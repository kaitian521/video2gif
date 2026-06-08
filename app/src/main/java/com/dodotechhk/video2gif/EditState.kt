package com.dodotechhk.video2gif

/**
 * 编辑状态:单一数据源,预览与导出都从它派生。
 *
 * P0 先建空壳;后续阶段往里加字段(届时改回 `data class`):
 * - P1 `sourceUri` / `sourceLocalPath` / `durationMs`
 * - P2 `clipStartMs` / `clipEndMs`
 * - P3 `targetHeight`
 * - P4 `aspect`
 * - P5 `scaleX` / `scaleY` / `rotation`
 * - P6 `offsetX` / `offsetY`
 * - P7 `speed`(时间轴变换,不进 [buildVideoEffects])
 */
class EditState
