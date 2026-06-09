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
 * 中心裁剪的 NDC 半宽/半高(实施计划 P4 / 技术方案 §4)。
 *
 * **纯函数**,P5 会在此叠加 scale/旋转的内接矩形;P4 只含比例。返回 `(halfW, halfH)`,
 * 二者 `∈ (0, 1]`。Crop 在 NDC `[-1,1]` 上按比例**逐轴**缩窗口:裁后像素比 =
 * `(halfW·srcW)/(halfH·srcH)`,令其等于目标比例即得下式。
 *
 * 记 `srcAR = 源宽/源高`、`targetAR = 目标宽/高`,`r = targetAR / srcAR`:
 * - `r ≤ 1`(目标相对更"瘦" → 裁宽):`halfW = r`,`halfH = 1`;
 * - `r > 1`(目标相对更"宽" → 裁高):`halfW = 1`,`halfH = 1/r`。
 *
 * 「原始」比例时 `targetAR = srcAR` → `r = 1` → `(1, 1)`,不裁。
 */
fun centerCropHalfExtents(state: EditState): Pair<Float, Float> {
    val srcAR = state.sourceAspectRatio
    val targetAR = state.aspect.ratio ?: srcAR
    val r = targetAR / srcAR
    return if (r <= 1f) r to 1f else 1f to (1f / r)
}
