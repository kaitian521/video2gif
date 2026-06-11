package com.dodotechhk.video2gif

/**
 * 清晰度三档(实施计划 P8 / 技术方案 §10.2)。
 *
 * mp4 码率 = [k] × 输出宽 × 输出高 × [maxOutputFps](实施计划 P8 步骤 3);
 * [maxOutputFps] 同时作为 `EditedMediaItem.setFrameRate` 的最大输出帧率上限
 * (speed > 1 时防止输出帧率爆高,§5.4)。GIF 的 fps/colors/dither 档位表留 P9。
 *
 * 三档具体数值(k / fps,GIF 还有 colors)待 P11(§10.2)真机实测标定;当前为计划初值。
 */
enum class ExportQuality(val label: String, val k: Float, val maxOutputFps: Int) {
    Low("低", 0.05f, 30),
    Medium("中", 0.1f, 30),
    High("高", 0.2f, 30),
}
