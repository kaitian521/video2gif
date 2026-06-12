package com.dodotechhk.video2gif.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 快手橙(Kwai 品牌橙)主题色 ----

/** 主品牌橙(浅色主题 primary):按钮、选中态等全局主强调色。 */
val KwaiOrange = Color(0xFFFF7E00)
val KwaiOnOrange = Color(0xFFFFFFFF)
val KwaiOrangeContainer = Color(0xFFFFDCC2)
val KwaiOnOrangeContainer = Color(0xFF2E1500)

/** 暗色主题 primary:稍亮的橙,深底上保持品牌感与对比度。 */
val KwaiOrangeBright = Color(0xFFFFA040)
val KwaiOnOrangeDark = Color(0xFF3F2200)
val KwaiOrangeContainerDark = Color(0xFF6A3C00)
val KwaiOnOrangeContainerDark = Color(0xFFFFDCC2)

/** 次要色:暖棕灰,与橙主色同一暖色温。 */
val WarmSecondary = Color(0xFF765847)
val WarmSecondaryDark = Color(0xFFE6BEA8)

/** 第三色:暖红,用于少量点缀。 */
val WarmTertiary = Color(0xFF9C4146)
val WarmTertiaryDark = Color(0xFFFFB3B0)

/** 截取页滑竿手柄强调色:与品牌橙一致,深色/浅色主题下都清晰可辨。 */
val TrimHandle = KwaiOrange

/**
 * 选中态强调色(预览页比例 chips / 导出面板选项 chips):与 [TrimHandle] 同一品牌橙。
 * 默认 secondaryContainer 在暗黑模式下发暗、选中不明显;中明度橙明暗主题都鲜明。
 */
val ChipSelected = KwaiOrange
