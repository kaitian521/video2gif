package com.dodotechhk.video2gif.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dodotechhk.video2gif.EditState

/** 顶层页面。导入页之外的两步:截取(P2)与预览(P3)各自独立。 */
private enum class Screen { Trim, Preview }

/**
 * 顶层导航:导入页 → 截取页(P2)→ 预览页(P3)。
 * 持有唯一的 [EditState],各页从它派生。
 */
@Composable
fun Video2gifApp(modifier: Modifier = Modifier) {
    var editState by remember { mutableStateOf<EditState?>(null) }
    var screen by remember { mutableStateOf(Screen.Trim) }

    when (val current = editState) {
        null -> ImportScreen(
            onImported = { editState = it; screen = Screen.Trim },
            modifier = modifier,
        )

        else -> when (screen) {
            Screen.Trim -> {
                // 系统返回键:截取页 → 回到导入页。
                BackHandler { editState = null }
                TrimScreen(
                    state = current,
                    onStateChange = { editState = it },
                    onBack = { editState = null },
                    onNext = { screen = Screen.Preview },
                    modifier = modifier,
                )
            }

            Screen.Preview -> {
                // 系统返回键:预览页 → 回到截取页(而非退到后台)。
                BackHandler { screen = Screen.Trim }
                PreviewScreen(
                    state = current,
                    onStateChange = { editState = it },
                    onBack = { screen = Screen.Trim },
                    modifier = modifier,
                )
            }
        }
    }
}
