package com.dodotechhk.video2gif

import android.net.Uri

/**
 * 编辑状态:单一数据源,预览与导出都从它派生。
 *
 * 已有字段:
 * - P1 `sourceUri` / `sourceLocalPath` / `durationMs`
 * - P2 `clipStartMs` / `clipEndMs`
 *
 * 后续阶段继续往里加:
 * - P3 `targetHeight`
 * - P4 `aspect`
 * - P5 `scaleX` / `scaleY` / `rotation`
 * - P6 `offsetX` / `offsetY`
 * - P7 `speed`(时间轴变换,不进 [buildVideoEffects])
 */
data class EditState(
    /** 相册选中的原始 `content://` Uri(仅用于预览/读取,**绝不**直接拼进 ffmpeg)。 */
    val sourceUri: Uri? = null,
    /** 确认可读的本地路径(原文件绝对路径,或复制到 cache 的副本);ffmpeg/导出只认它。 */
    val sourceLocalPath: String? = null,
    /** 源视频总时长(ms)。 */
    val durationMs: Long = 0L,
    /** 截取区间起点(ms),默认从 0。仅存于此,导出时才折进 ClippingConfiguration(§3)。 */
    val clipStartMs: Long = 0L,
    /** 截取区间终点(ms)。约束见 [ClipConstraints]。 */
    val clipEndMs: Long = 0L,
)
