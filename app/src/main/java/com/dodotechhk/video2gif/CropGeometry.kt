package com.dodotechhk.video2gif

/**
 * 目标比例(实施计划 P4)。[ratio] = 宽/高;`null` 表示「原始」(用源比例,不裁)。
 */
enum class AspectRatio(val label: String, val ratio: Float?) {
    Original("原始", null),
    Square("1:1", 1f),
    ThreeFour("3:4", 3f / 4f),
    FourThree("4:3", 4f / 3f),
    SixteenNine("16:9", 16f / 9f),
    NineSixteen("9:16", 9f / 16f),
}

/**
 * 中心裁剪的 NDC 半宽/半高(实施计划 P4–P5 / 技术方案 §4)。
 *
 * **纯函数**,P5 旋转会在此再叠加内接矩形;当前含**比例 + 缩放**。返回 `(halfW, halfH)`,
 * 二者 `∈ (0, 1]`。Crop 在 NDC `[-1,1]` 上按比例**逐轴**缩窗口:裁后像素比 =
 * `(halfW·srcW)/(halfH·srcH)`,令其等于目标比例即得下式。
 *
 * 1) 比例。记 `srcAR = 源宽/源高`、`targetAR = 目标宽/高`,`r = targetAR / srcAR`:
 *    - `r ≤ 1`(目标相对更"瘦" → 裁宽):`halfW = r`,`halfH = 1`;
 *    - `r > 1`(目标相对更"宽" → 裁高):`halfW = 1`,`halfH = 1/r`。
 *    「原始」比例 `r = 1` → `(1, 1)`,不裁。
 * 2) 缩放。放大 `s = max(1, scale)` → 窗口逐轴再除以 `s`(`halfW/s, halfH/s`),
 *    比例不变(同除一个数)、窗口更小 = 放大取景。`s ≥ 1` 保证不越界、不露黑。
 */
fun centerCropHalfExtents(state: EditState): Pair<Float, Float> {
    val srcAR = state.sourceAspectRatio
    val targetAR = state.aspect.ratio ?: srcAR
    val r = targetAR / srcAR
    val (aspectHalfW, aspectHalfH) = if (r <= 1f) r to 1f else 1f to (1f / r)
    val s = state.scale.coerceAtLeast(1f)
    return (aspectHalfW / s).coerceIn(0f, 1f) to (aspectHalfH / s).coerceIn(0f, 1f)
}

/**
 * 裁剪窗口中心(NDC,实施计划 P6 拖动)。把 [EditState.offsetX]/[EditState.offsetY] 夹紧到
 * 窗口完全落在内容 `[-1,1]` 内(`|c| ≤ 1 - half`)—— 任何比例 × 缩放 × 偏移组合都不越界、
 * 不露黑边。预览平移(graphicsLayer)与导出 [cropEffect] 共用此真值(WYSIWYG)。
 */
fun clampedCropCenter(state: EditState): Pair<Float, Float> {
    val (halfW, halfH) = centerCropHalfExtents(state)
    return state.offsetX.coerceIn(halfW - 1f, 1f - halfW) to
        state.offsetY.coerceIn(halfH - 1f, 1f - halfH)
}

/**
 * 比例/缩放变化后把偏移夹回合法域**并回写 state**(实施计划 P6 步骤 4):
 * 否则 raw 偏移停在界外,反向拖动要先"空拖"回界内才生效(死区)。
 */
fun EditState.withClampedOffsets(): EditState {
    val (cx, cy) = clampedCropCenter(this)
    return copy(offsetX = cx, offsetY = cy)
}
