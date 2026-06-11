package com.dodotechhk.video2gif

import android.net.Uri

/**
 * 编辑状态:单一数据源,预览与导出都从它派生。
 *
 * 已有字段:
 * - P1 `sourceUri` / `sourceLocalPath` / `durationMs`
 * - P2 `clipStartMs` / `clipEndMs`
 * - P3 `targetHeight`
 * - P4 `aspect`
 * - P5 `scale`(旋转 pending)
 * - P6 `offsetX` / `offsetY`
 * - P7 `speed`(时间轴变换,不进 [buildVideoEffects])
 *
 * 后续阶段继续往里加:
 * - P5 余项 `rotation`(pending)
 */
data class EditState(
    /** 相册选中的原始 `content://` Uri(仅用于预览/读取,**绝不**直接拼进 ffmpeg)。 */
    val sourceUri: Uri? = null,
    /** 确认可读的本地路径(原文件绝对路径,或复制到 cache 的副本);ffmpeg/导出只认它。 */
    val sourceLocalPath: String? = null,
    /** 源视频总时长(ms)。 */
    val durationMs: Long = 0L,
    /** 源视频**显示**宽(px,已应用旋转);读不到为 0,用 [sourceAspectRatio] 兜底。 */
    val displayWidth: Int = 0,
    /** 源视频**显示**高(px,已应用旋转);读不到为 0。 */
    val displayHeight: Int = 0,
    /** 截取区间起点(ms),默认从 0。仅存于此,导出时才折进 ClippingConfiguration(§3)。 */
    val clipStartMs: Long = 0L,
    /** 截取区间终点(ms)。约束见 [ClipConstraints]。 */
    val clipEndMs: Long = 0L,
    /**
     * 预览/导出的目标高度(px),宽度由内容比例派生。取偶数以满足 H.264 编码器对偶数维度的要求;
     * 派生宽度的偶数对齐由导出端 encoder 兜底。详见 [buildVideoEffects] 的 `Presentation`(P3)。
     */
    val targetHeight: Int = 720,
    /** 目标比例(P4 中心裁剪);默认「原始」= 不裁。详见 [centerCropHalfExtents]。 */
    val aspect: AspectRatio = AspectRatio.Original,
    /** P5 缩放:取景窗口放大倍数(≥1,1=不放大)。放大 → 裁剪窗口相对更小(halfW/s, halfH/s)。 */
    val scale: Float = 1f,
    /**
     * P6 拖动:裁剪窗口中心的 NDC 偏移(GL 约定,y 朝上;0 = 居中)。
     * 使用前一律经 [clampedCropCenter] 夹紧(|c| ≤ 1 - half),保证窗口不出内容、不露黑边。
     */
    val offsetX: Float = 0f,
    /** 同 [offsetX],竖直方向(正值 = 窗口偏向内容上部)。 */
    val offsetY: Float = 0f,
    /**
     * P7 变速:播放/导出速度倍率(>0,UI 滑竿限定 [0.5, 2],0.05 步进)。
     * **时间轴变换,绝不进 [buildVideoEffects]**(会改帧时间戳,效果链不支持):
     * 预览走播放器倍速 `setPlaybackSpeed`,导出走 `EditedMediaItem.Builder#setSpeed`(§5.4)。
     * 输出时长 = 选取时长 ÷ speed;成品最小时长语义(÷speed ≥ 500ms)待 §10.6 定稿。
     */
    val speed: Float = 1f,
    /** P8 清晰度档位:决定 mp4 码率(k×W×H×maxFps)/GIF 颜色抖动/WebP q;**不影响像素几何与帧率**。 */
    val quality: ExportQuality = ExportQuality.Medium,
    /**
     * 最大输出帧率(UI 五档 10/15/20/25/30):mp4 `setFrameRate` 上限(speed>1 防爆高,§5.4),
     * GIF/WebP 的 `fps` 滤镜直接用它。默认 15(GIF 工具的均衡值)。
     */
    val maxFps: Int = 15,
    /** P9 导出格式:默认 GIF;mp4 直出,GIF/WebP 对中间 mp4 二次转码([FormatConverter])。 */
    val format: ExportFormat = ExportFormat.Gif,
) {
    /** 源视频显示宽高比(宽/高);读不到尺寸时回退 16:9。供截取页按比例定宽用。 */
    val sourceAspectRatio: Float
        get() = if (displayWidth > 0 && displayHeight > 0) {
            displayWidth.toFloat() / displayHeight
        } else {
            16f / 9f
        }

    /** 经比例裁剪后的输出宽高比(宽/高):选了比例用之,否则用源比例。供预览页按比例定框用。 */
    val outputAspectRatio: Float
        get() = aspect.ratio ?: sourceAspectRatio
}
