package com.dodotechhk.video2gif

/**
 * 清晰度三档(实施计划 P8/P9 / 技术方案 §10.2,2026-06 标定)。
 *
 * **只管画质,不管帧率**(帧率独立为 [EditState.maxFps],避免两个控件打架):
 * - mp4 码率 = [k] × 输出宽 × 输出高 × maxFps;
 * - GIF 颜色数/抖动、WebP q 的三档映射在 [FormatConverter]。
 *
 * 档位定位:低 = 体积优先(可感知的妥协)、中 = 均衡、高 = 质量优先。
 */
enum class ExportQuality(val label: String, val k: Float) {
    Low("低", 0.03f),
    Medium("中", 0.08f),
    High("高", 0.2f),
}
