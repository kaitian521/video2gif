package com.dodotechhk.video2gif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.R
import com.dodotechhk.video2gif.ui.theme.ChipSelected
import com.dodotechhk.video2gif.ui.theme.HomeBottomBarBackgroundDark
import com.dodotechhk.video2gif.ui.theme.HomeBottomBarBackgroundLight

/** 顶层页面。导入页之外的两步:截取(P2)与预览(P3)各自独立。 */
private enum class Screen { Trim, Preview }

/** 首页底部 Tab:Home(导入)/ Creation(作品页)。 */
private enum class HomeTab { Home, Creation }

/**
 * 顶层导航:导入页 → 截取页(P2)→ 预览页(P3)。
 * 持有唯一的 [EditState],各页从它派生。
 */
@Composable
fun Video2gifApp(modifier: Modifier = Modifier) {
    var editState by remember { mutableStateOf<EditState?>(null) }
    var screen by remember { mutableStateOf(Screen.Trim) }
    var homeTab by remember { mutableStateOf(HomeTab.Home) }
    // 设置页:从首页两个 Tab(导入页/作品页)的标题栏齿轮进入,覆盖显示。
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false }, modifier = modifier)
        return
    }

    when (val current = editState) {
        // 首页:底部双 Tab(Home 导入 / Creation 作品页)。
        null -> Scaffold(
            modifier = modifier,
            // 外层(MainActivity)Scaffold 已消费系统栏 inset,这里不再重复,
            // 否则标题栏/底部 Tab 会被多垫一个状态栏/导航栏的高度。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                HomeBottomBar(selected = homeTab, onSelect = { homeTab = it })
            },
        ) { innerPadding ->
            when (homeTab) {
                HomeTab.Home -> ImportScreen(
                    onImported = { editState = it; screen = Screen.Trim },
                    onSettings = { showSettings = true },
                    modifier = Modifier.padding(innerPadding),
                )

                HomeTab.Creation -> CreationScreen(
                    onSettings = { showSettings = true },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        else -> when (screen) {
            Screen.Trim -> {
                // 系统返回键:截取页 → 回到导入页。
                BackHandler { editState = null }
                TrimScreen(
                    state = current,
                    onStateChange = { editState = it },
                    onBack = { editState = null },
                    // 进预览页一律清空效果(旋转/比例/缩放/偏移/变速/文字),纯新预览。
                    onNext = {
                        editState = current.resetEffects()
                        screen = Screen.Preview
                    },
                    modifier = modifier,
                )
            }

            Screen.Preview -> {
                // 预览页 → 截取页:全部效果参数重置(返回即放弃本次预览的编辑)。
                val backToTrim = {
                    editState = editState?.resetEffects()
                    screen = Screen.Trim
                }
                // 系统返回键:预览页 → 回到截取页(而非退到后台)。
                BackHandler { backToTrim() }
                PreviewScreen(
                    state = current,
                    onStateChange = { editState = it },
                    onBack = backToTrim,
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * 首页悬浮胶囊式底部 Tab 栏:选中项展开为「图标 + 文字」的橙色胶囊,
 * 未选中只显示图标;切换带宽度/颜色动画。强调橙与全局 [ChipSelected] 一致。
 */
@Composable
private fun HomeBottomBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val barColor =
        if (isSystemInDarkTheme()) HomeBottomBarBackgroundDark else HomeBottomBarBackgroundLight

    Box(
        // 导航栏 inset 已由外层 Scaffold 消费,这里只留视觉边距。
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = barColor,
            shadowElevation = 6.dp,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(6.dp),
            ) {
                HomeBottomBarItem(
                    selected = selected == HomeTab.Home,
                    onClick = { onSelect(HomeTab.Home) },
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    label = stringResource(R.string.tab_home),
                )
                HomeBottomBarItem(
                    selected = selected == HomeTab.Creation,
                    onClick = { onSelect(HomeTab.Creation) },
                    selectedIcon = Icons.Filled.PlayArrow,
                    unselectedIcon = Icons.Outlined.PlayArrow,
                    label = stringResource(R.string.tab_creation),
                )
            }
        }
    }
}

@Composable
private fun HomeBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
) {
    val pillColor by animateColorAsState(
        if (selected) ChipSelected.copy(alpha = 0.18f) else Color.Transparent,
        label = "tabPill",
    )
    val contentColor by animateColorAsState(
        if (selected) ChipSelected else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(pillColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
