package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.ClipConstraints
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.clampEndMs
import com.dodotechhk.video2gif.clampStartMs
import com.dodotechhk.video2gif.isValidClip

/**
 * P2 截取页:内嵌视频预览 + 双滑块选 `[clipStartMs, clipEndMs]`,长度恒夹在 [500ms, 10s]。
 * 区间只写回 [EditState](通过 [onStateChange]),不在此做任何裁剪。
 */
@Composable
fun TrimScreen(
    state: EditState,
    onStateChange: (EditState) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val length = state.clipEndMs - state.clipStartMs
    val valid = isValidClip(state.clipStartMs, state.clipEndMs, state.durationMs)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("截取区间", style = MaterialTheme.typography.titleLarge)
        Text("源时长:${state.durationMs} ms", style = MaterialTheme.typography.bodyMedium)

        // 视频预览:循环播放当前选区。占据中间可用空间。
        VideoPreview(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Text(
            "区间:${state.clipStartMs} … ${state.clipEndMs} ms(时长 $length ms)",
            style = MaterialTheme.typography.titleMedium,
        )

        RangeSlider(
            value = state.clipStartMs.toFloat()..state.clipEndMs.toFloat(),
            onValueChange = { range ->
                val newStart = range.start.toLong()
                val newEnd = range.endInclusive.toLong()
                // 检测哪个手柄被拖动,另一端固定,交给纯函数夹紧。
                val (start, end) = if (newStart != state.clipStartMs) {
                    clampStartMs(newStart, state.clipEndMs) to state.clipEndMs
                } else {
                    state.clipStartMs to clampEndMs(newEnd, state.clipStartMs, state.durationMs)
                }
                onStateChange(state.copy(clipStartMs = start, clipEndMs = end))
            },
            valueRange = 0f..state.durationMs.toFloat(),
            // 左右各 +15dp,避开手机侧边手势区(叠加在 Column 的 24dp 之上)。
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
        )

        Text(
            "约束:${ClipConstraints.MIN_CLIP_MS}ms ≤ 时长 ≤ ${ClipConstraints.MAX_CLIP_MS}ms",
            style = MaterialTheme.typography.bodySmall,
        )

        if (!valid) {
            Text("区间不合法", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("重新选择") }
            Button(onClick = onNext, enabled = valid) { Text("下一步(P3 预览)") }
        }
    }
}
