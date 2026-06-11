package com.dodotechhk.video2gif

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.math.roundToInt

/** 导出进度轮询间隔(ms)。 */
private const val PROGRESS_POLL_MS = 200L

/**
 * mp4 导出(实施计划 P8)。Transformer 一遍出**无音轨 mp4**,与预览共用同一条
 * [buildVideoEffects] → WYSIWYG(第一个对拍验收点)。
 *
 * 覆盖 P8 步骤 1–7:`ClippingConfiguration`(选区)+ `setRemoveAudio` + 变速 `setSpeed`(§5.4)
 * + `setFrameRate(maxOutputFps)` + 视觉效果链 + H264 + 三档码率(`k×W×H×fps`,经
 * `DefaultEncoderFactory`)+ `getProgress` 轮询 + 失败/取消清理产物。
 * `experimentalSetTrimOptimizationEnabled(false)` 显式锁定逐帧精确前提(§3),不依赖默认值。
 * 三档数值(k/fps)待 P11(§10.2/§10.6)真机标定。
 */
object VideoExporter {

    sealed interface Result {
        /**
         * 导出成功;[width]/[height] 为读回的**编码**尺寸,[rotation] 为旋转元数据,
         * [durationMs] 为读回的输出时长(验变速:≈ 选取时长 ÷ speed)。
         */
        data class Success(
            val outPath: String,
            val width: Int,
            val height: Int,
            val rotation: Int,
            val durationMs: Long,
        ) : Result

        data class Error(val message: String) : Result

        /** 用户取消;产物已清理。 */
        data object Cancelled : Result
    }

    /** 进行中的导出会话;[cancel] 中止 Transformer 并清理产物(幂等)。 */
    class ExportSession internal constructor(private val onCancel: () -> Unit) {
        fun cancel() = onCancel()
    }

    /**
     * 一键导出。**必须在带 Looper 的线程调用**(主线程即可);[onProgress](0–100)与
     * [onResult] 都在同一 Looper 触发,可直接更新 UI。
     */
    fun export(
        context: Context,
        state: EditState,
        outFile: File,
        onProgress: (Int) -> Unit = {},
        onResult: (Result) -> Unit,
    ): ExportSession {
        val src = state.sourceLocalPath
        if (src == null) {
            onResult(Result.Error("无本地源路径(sourceLocalPath 为空)"))
            return ExportSession {}
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(src)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(state.clipStartMs)
                    .setEndPositionMs(state.clipEndMs)
                    .build()
            )
            .build()

        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            // 与预览共用同一条视觉效果链 → WYSIWYG。
            .setEffects(Effects(emptyList(), buildVideoEffects(state)))
            // 最大输出帧率:speed > 1 时帧时间戳被压缩,不设上限输出帧率会爆高(§5.4)。
            .setFrameRate(state.quality.maxOutputFps)
            .apply {
                // P7 变速:时间轴变换走 setSpeed(§5.4),绝不进 buildVideoEffects;
                // 设了 setSpeed 后效果链里不允许再有改时间戳的 effect(当前没有)。
                // 1× 不设置,保持默认路径与此前完全一致。
                if (state.speed > 0f && state.speed != 1f) {
                    setSpeed(constantSpeedProvider(state.speed))
                }
            }
            .build()

        // 三档码率 = k × 输出宽 × 输出高 × maxOutputFps(实施计划 P8 步骤 3)。
        // 输出尺寸:高 = targetHeight,宽按裁后比例派生;编码器对齐(偶数等)由 encoder 兜底,
        // 码率估算不需要精确到对齐后的值。
        val outH = state.targetHeight
        val outW = (outH * state.outputAspectRatio).roundToInt()
        val bitrate = (state.quality.k * outW * outH * state.quality.maxOutputFps).roundToInt()

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()
        var finished = false
        lateinit var transformer: Transformer
        // 进度轮询(P8 步骤 5):Transformer 无推送式进度,只能 getProgress 轮询。
        val pollProgress = object : Runnable {
            override fun run() {
                if (finished) return
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress)
                }
                handler.postDelayed(this, PROGRESS_POLL_MS)
            }
        }

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            // 显式关 trim 优化:逐帧精确必须走转码路径(§3),不依赖默认值。
            .experimentalSetTrimOptimizationEnabled(false)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(bitrate).build()
                    )
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    handler.removeCallbacks(pollProgress)
                    val meta = readMeta(outFile.absolutePath)
                    onResult(
                        Result.Success(
                            outFile.absolutePath, meta.width, meta.height, meta.rotation,
                            meta.durationMs,
                        )
                    )
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    finished = true
                    handler.removeCallbacks(pollProgress)
                    outFile.delete() // 失败清理残留(P8 步骤 6)。
                    onResult(Result.Error(exportException.message ?: "导出失败"))
                }
            })
            .build()

        // HDR 策略(§10.5 定稿):统一 tone map 到 SDR。输出格式(GIF 256 色/WebP 8-bit/H264 8-bit)
        // 均承载不了 HDR;OpenGL 模式 API 29+ 广泛可用,SDR 源为 no-op,故无条件设置、不做检测分支。
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(edited).build()
        )
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        transformer.start(composition, outFile.absolutePath)
        handler.post(pollProgress)

        return ExportSession {
            if (!finished) {
                finished = true
                handler.removeCallbacks(pollProgress)
                transformer.cancel()
                outFile.delete() // 取消清理残留(P8 步骤 6 / P10 DoD)。
                onResult(Result.Cancelled)
            }
        }
    }

    /** 恒定速度 SpeedProvider(技术方案 §5.4):速度恒为 [speed],无后续变化点。 */
    private fun constantSpeedProvider(speed: Float): SpeedProvider = object : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private data class Meta(val width: Int, val height: Int, val rotation: Int, val durationMs: Long)

    /** 读回导出文件的编码宽/高/旋转/时长;读不到回退 0。 */
    private fun readMeta(path: String): Meta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            fun meta(key: Int) = retriever.extractMetadata(key)
            Meta(
                width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            Meta(0, 0, 0, 0L)
        } finally {
            retriever.release()
        }
    }
}
