package com.dodotechhk.video2gif

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * 最小导出 harness(实施计划 P3 收尾 / P8 雏形)。
 *
 * 目的:在 P4–P6 之前就能用**同一条 [buildVideoEffects]** 一键导出 mp4,验证
 * `targetHeight` 的真实输出尺寸(PlayerView 会缩放显示,肉眼看不出),并为后续像素能力做对拍。
 *
 * 当前覆盖:`ClippingConfiguration`(选区)+ `setRemoveAudio` + 视觉效果链 + H264
 * + 变速 `setSpeed`(P7,§5.4)。**码率、帧率(`setFrameRate(maxOutputFps)`)、进度、
 * 取消清理留到 P8/P11**(见实施计划)。`experimentalSetTrimOptimizationEnabled(false)`
 * 显式锁定逐帧精确前提(§3),不依赖默认值。
 */
object VideoExporter {

    sealed interface Result {
        /** 导出成功;[width]/[height] 为读回的**编码**尺寸,[rotation] 为旋转元数据。 */
        data class Success(
            val outPath: String,
            val width: Int,
            val height: Int,
            val rotation: Int,
        ) : Result

        data class Error(val message: String) : Result
    }

    /**
     * 一键导出。**必须在带 Looper 的线程调用**(主线程即可);回调在同一 Looper 触发。
     * 回调线程 = 调用线程,故从 Compose 主线程调用时 [onResult] 也在主线程,可直接更新 UI。
     */
    fun export(
        context: Context,
        state: EditState,
        outFile: File,
        onResult: (Result) -> Unit,
    ) {
        val src = state.sourceLocalPath
        if (src == null) {
            onResult(Result.Error("无本地源路径(sourceLocalPath 为空)"))
            return
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
            .apply {
                // P7 变速:时间轴变换走 setSpeed(§5.4),绝不进 buildVideoEffects;
                // 设了 setSpeed 后效果链里不允许再有改时间戳的 effect(当前没有)。
                // 1× 不设置,保持默认路径与此前完全一致。
                if (state.speed > 0f && state.speed != 1f) {
                    setSpeed(constantSpeedProvider(state.speed))
                }
            }
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            // 显式关 trim 优化:逐帧精确必须走转码路径(§3),不依赖默认值。
            .experimentalSetTrimOptimizationEnabled(false)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val (w, h, rot) = readSize(outFile.absolutePath)
                    onResult(Result.Success(outFile.absolutePath, w, h, rot))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    onResult(Result.Error(exportException.message ?: "导出失败"))
                }
            })
            .build()

        transformer.start(edited, outFile.absolutePath)
    }

    /** 恒定速度 SpeedProvider(技术方案 §5.4):速度恒为 [speed],无后续变化点。 */
    private fun constantSpeedProvider(speed: Float): SpeedProvider = object : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    /** 读回导出文件的编码宽/高/旋转;读不到回退 0。 */
    private fun readSize(path: String): Triple<Int, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val w = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rot = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            Triple(w, h, rot)
        } catch (e: Exception) {
            Triple(0, 0, 0)
        } finally {
            retriever.release()
        }
    }
}
