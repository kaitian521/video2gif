package com.dodotechhk.video2gif.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dodotechhk.video2gif.EditState

/**
 * 顶层导航:导入页 ↔ 截取页。
 * 持有唯一的 [EditState];P3 起预览页会接到「下一步」。
 */
@Composable
fun Video2gifApp(modifier: Modifier = Modifier) {
    var editState by remember { mutableStateOf<EditState?>(null) }

    when (val current = editState) {
        null -> ImportScreen(
            onImported = { editState = it },
            modifier = modifier,
        )

        else -> TrimScreen(
            state = current,
            onStateChange = { editState = it },
            onBack = { editState = null },
            onNext = { /* P3 预览:待实现 */ },
            modifier = modifier,
        )
    }
}
