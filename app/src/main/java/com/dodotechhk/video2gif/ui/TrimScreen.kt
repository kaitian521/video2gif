package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.dodotechhk.video2gif.ClipConstraints
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.clampEndMs
import com.dodotechhk.video2gif.clampStartMs
import com.dodotechhk.video2gif.exceedsMaxClip
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
    val context = LocalContext.current
    val length = state.clipEndMs - state.clipStartMs
    val valid = isValidClip(state.clipStartMs, state.clipEndMs, state.durationMs)

    // 当前播放位置(由预览回调更新),用于在两滑竿之间画播放进度细条。
    var positionMs by remember(state.sourceUri) { mutableStateOf(state.clipStartMs) }
    // 滑块松手后自增,触发预览从左滑竿重新播放。
    var restartTrigger by remember(state.sourceUri) { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("截取区间", style = MaterialTheme.typography.titleLarge)
        Text("源时长:${state.durationMs} ms", style = MaterialTheme.typography.bodyMedium)

        // 视频预览:**高度固定**,宽度按源视频显示比例自适应,无黑边。
        // 盒子与视频宽高比严格一致 → PlayerView 默认 FIT 不产生 letterbox。
        // 超宽视频(宽 > 可用宽度)时按可用宽度回退,避免横向溢出。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = state.sourceAspectRatio
            val previewHeight = 320.dp
            val w = minOf(previewHeight * ratio, maxWidth)
            val h = w / ratio
            VideoPreview(
                state = state,
                onPositionChange = { positionMs = it },
                onVideoDisplaySize = { vwPx, vhPx ->
                    // 用播放器真实尺寸校正源宽高比(MMR 方形像素假设可能不符);早校正,预览页直接用对。
                    val reported = vwPx.toFloat() / vhPx
                    if (kotlin.math.abs(reported - state.sourceAspectRatio) > 0.01f) {
                        onStateChange(state.copy(displayWidth = vwPx, displayHeight = vhPx))
                    }
                },
                restartSignal = restartTrigger,
                modifier = Modifier
                    .width(w)
                    .height(h),
            )
        }

        Text(
            "区间:${state.clipStartMs} … ${state.clipEndMs} ms(时长 $length ms)",
            style = MaterialTheme.typography.titleMedium,
        )

        // 滑块 + 播放进度细条。左右各 +15dp,避开手机侧边手势区(叠加在 Column 的 24dp 之上)。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
        ) {
            // 轨道两端各内缩一个滑块半径,使细条与滑竿落在同一坐标系。
            val thumbRadius = 10.dp
            val trackWidth = maxWidth

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
                // 松手即从左滑竿重新播放。
                onValueChangeFinished = { restartTrigger++ },
                valueRange = 0f..state.durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.durationMs > 0) {
                // 夹在两滑竿之间,确保细条始终落在选区内。
                val shownPos = positionMs.coerceIn(state.clipStartMs, state.clipEndMs)
                val fraction = (shownPos.toFloat() / state.durationMs).coerceIn(0f, 1f)
                val playheadX = thumbRadius + (trackWidth - thumbRadius * 2) * fraction
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = playheadX - 1.dp)
                        .width(2.dp)
                        .height(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }

        Text(
            "最短 ${ClipConstraints.MIN_CLIP_MS}ms;超过 ${ClipConstraints.MAX_CLIP_MS / 1000}s 可继续滑动,下一步会提示",
            style = MaterialTheme.typography.bodySmall,
        )

        if (!valid) {
            Text("区间不合法", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("重新选择") }
            Button(
                onClick = {
                    // 超 10s 在此拦下并提示,不进预览;否则放行。
                    if (exceedsMaxClip(state.clipStartMs, state.clipEndMs)) {
                        Toast.makeText(
                            context,
                            "最多选 ${ClipConstraints.MAX_CLIP_MS / 1000} 秒",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        onNext()
                    }
                },
                enabled = valid,
            ) { Text("下一步(P3 预览)") }
        }
    }
}
