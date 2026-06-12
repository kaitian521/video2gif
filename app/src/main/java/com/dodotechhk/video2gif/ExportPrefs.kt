package com.dodotechhk.video2gif

import android.content.Context

/**
 * 上次导出选择的持久化(SharedPreferences):格式/分辨率/帧率/清晰度。
 *
 * - [applyTo]:导入构造初始 [EditState] 后调用,把上次导出的选择覆盖到默认值上
 *   (首次无记录时保持 EditState 的出厂默认:WebP / 480p / 10fps / 高)。
 * - [save]:每次**发起导出**时调用(用户已确认的选择,无论导出成败都算「上次的选择」)。
 */
object ExportPrefs {

    private const val NAME = "export_prefs"
    private const val KEY_FORMAT = "format"
    private const val KEY_HEIGHT = "targetHeight"
    private const val KEY_FPS = "maxFps"
    private const val KEY_QUALITY = "quality"

    fun applyTo(context: Context, state: EditState): EditState {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        // enum 按 name 存取;记录损坏/版本变化解析不出时回落当前默认。
        val format = sp.getString(KEY_FORMAT, null)
            ?.let { name -> ExportFormat.values().firstOrNull { it.name == name } }
            ?: state.format
        val quality = sp.getString(KEY_QUALITY, null)
            ?.let { name -> ExportQuality.values().firstOrNull { it.name == name } }
            ?: state.quality
        return state.copy(
            format = format,
            quality = quality,
            targetHeight = sp.getInt(KEY_HEIGHT, state.targetHeight),
            maxFps = sp.getInt(KEY_FPS, state.maxFps),
        )
    }

    fun save(context: Context, state: EditState) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_FORMAT, state.format.name)
            .putString(KEY_QUALITY, state.quality.name)
            .putInt(KEY_HEIGHT, state.targetHeight)
            .putInt(KEY_FPS, state.maxFps)
            .apply()
    }
}
