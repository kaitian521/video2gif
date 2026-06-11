package com.dodotechhk.video2gif

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * mp4 → GIF / WebP 二次转码(P9 / 技术方案 §5.2、§8)。
 *
 * 对 Transformer 导出的**最终 mp4**跑一条 ffmpeg-kit 命令:
 * - **不做 scale**:分辨率真值在 mp4(`Presentation` 已锁定),FFmpeg 只做 fps + 编码;
 * - GIF:`fps + palettegen/paletteuse + dither` 两遍调色板法(单命令 filter_complex);
 * - WebP:`fps + libwebp_anim`(动图编码器,fork AAR 已确认内置);
 * - `executeWithArgumentsAsync`:**参数数组、不带 `ffmpeg` 前缀**,自带后台线程;
 * - 进度:statistics 回调的已处理时间 ÷ 预期输出时长。
 *
 * 三档参数(§10.2,2026-06 标定;**fps 不在档内**,由 [EditState.maxFps] 独立控制):
 *
 * | 档 | max_colors | dither       | webp q |
 * |----|------------|--------------|--------|
 * | 低 | 64         | none(非 off)| 50     |
 * | 中 | 256        | bayer        | 75     |
 * | 高 | 256        | sierra2_4a   | 90     |
 */
object FormatConverter {

    sealed interface Result {
        data class Success(val outPath: String) : Result
        data class Error(val message: String) : Result
        /** 用户取消;产物已清理。 */
        data object Cancelled : Result
    }

    /** 进行中的转码会话;[cancel] 中止 ffmpeg 并清理产物(幂等,实际清理在会话回调里)。 */
    class ConvertSession internal constructor(private val session: FFmpegSession) {
        fun cancel() = FFmpegKit.cancel(session.sessionId)
    }

    private data class GifTier(val maxColors: Int, val dither: String)

    private fun gifTier(quality: ExportQuality) = when (quality) {
        ExportQuality.Low -> GifTier(64, "none")
        ExportQuality.Medium -> GifTier(256, "bayer")
        ExportQuality.High -> GifTier(256, "sierra2_4a")
    }

    private fun webpQ(quality: ExportQuality) = when (quality) {
        ExportQuality.Low -> 50
        ExportQuality.Medium -> 75
        ExportQuality.High -> 90
    }

    /**
     * 把 [mp4File] 转成 [format](GIF/WebP;mp4 不需要转码,调用方不该传进来)。
     * 回调线程为 ffmpeg-kit 的回调线程,**调用方负责切回主线程**更新 UI。
     *
     * @param fps 输出帧率([EditState.maxFps],用户五档选择)。
     * @param expectedDurationMs 预期输出时长(= 选取时长 ÷ speed),用于 statistics 进度换算。
     */
    fun convert(
        mp4File: File,
        outFile: File,
        format: ExportFormat,
        quality: ExportQuality,
        fps: Int,
        expectedDurationMs: Long,
        onProgress: (Int) -> Unit = {},
        onResult: (Result) -> Unit,
    ): ConvertSession {
        require(!format.isVideo) { "mp4 is direct output, no conversion needed" }

        val args = when (format) {
            ExportFormat.Gif -> {
                val t = gifTier(quality)
                arrayOf(
                    "-i", mp4File.absolutePath,
                    "-filter_complex",
                    "fps=$fps,split[s0][s1];" +
                        "[s0]palettegen=max_colors=${t.maxColors}:stats_mode=diff[p];" +
                        "[s1][p]paletteuse=dither=${t.dither}",
                    "-y", outFile.absolutePath,
                )
            }

            ExportFormat.WebP -> {
                arrayOf(
                    "-i", mp4File.absolutePath,
                    "-vf", "fps=$fps",
                    "-c:v", "libwebp_anim",
                    "-lossless", "0",
                    "-q:v", "${webpQ(quality)}",
                    "-loop", "0",
                    "-an",
                    "-y", outFile.absolutePath,
                )
            }

            ExportFormat.Mp4 -> error("unreachable")
        }

        val session = FFmpegKit.executeWithArgumentsAsync(
            args,
            { completed ->
                when {
                    ReturnCode.isSuccess(completed.returnCode) ->
                        onResult(Result.Success(outFile.absolutePath))

                    ReturnCode.isCancel(completed.returnCode) -> {
                        outFile.delete()
                        onResult(Result.Cancelled)
                    }

                    else -> {
                        outFile.delete()
                        onResult(
                            Result.Error(
                                "ffmpeg rc=${completed.returnCode}:" +
                                    (completed.failStackTrace ?: completed.output ?: "unknown error")
                                        .take(200)
                            )
                        )
                    }
                }
            },
            /* logCallback = */ null,
            /* statisticsCallback = */ { stats ->
                if (expectedDurationMs > 0) {
                    val p = (stats.time.toDouble() / expectedDurationMs * 100).toInt()
                    if (p in 0..100) onProgress(p)
                }
            },
        )
        return ConvertSession(session)
    }
}
