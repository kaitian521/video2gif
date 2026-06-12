package com.dodotechhk.video2gif

/**
 * 清晰度五档(实施计划 P8/P9 / 技术方案 §10.2,2026-06 标定)。
 *
 * **只管画质,不管帧率**(帧率独立为 [EditState.maxFps],避免两个控件打架):
 * - mp4 码率 = [k] × 输出宽 × 输出高 × maxFps;
 * - GIF 颜色数/抖动、WebP q 的五档映射在 [FormatConverter]。
 *
 * 档位定位:低 = 体积优先(可感知的妥协)、中 = 均衡、高 = 质量优先、
 * 超高 = 逐帧调色板(体积显著增大)、Max = 不计体积的质量天花板。
 *
 * k 取值刻意把两端拉开(0.04 → 2.0,跨 50×;相邻档 2.2–3.3×),
 * 让五档在体积/画质上都有肉眼可分的台阶;超出编码器能力的高码率由
 * DefaultEncoderFactory 的 fallback 自动夹紧,不会导出失败。
 */
enum class ExportQuality(val labelRes: Int, val k: Float) {
    Low(R.string.quality_low, 0.04f),
    Medium(R.string.quality_medium, 0.12f),
    High(R.string.quality_high, 0.4f),
    ExtraHigh(R.string.quality_extra_high, 0.9f),
    Max(R.string.quality_max, 2.0f),
}
