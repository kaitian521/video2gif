package com.dodotechhk.video2gif.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KwaiOrangeBright,
    onPrimary = KwaiOnOrangeDark,
    primaryContainer = KwaiOrangeContainerDark,
    onPrimaryContainer = KwaiOnOrangeContainerDark,
    secondary = WarmSecondaryDark,
    tertiary = WarmTertiaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary = KwaiOrange,
    onPrimary = KwaiOnOrange,
    primaryContainer = KwaiOrangeContainer,
    onPrimaryContainer = KwaiOnOrangeContainer,
    secondary = WarmSecondary,
    tertiary = WarmTertiary,
)

@Composable
fun Video2gifTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 关闭动态取色:品牌主题(快手橙)优先,Android 12+ 也不被壁纸色覆盖,
    // 否则按钮/选中态在不同设备上颜色不一致。
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
