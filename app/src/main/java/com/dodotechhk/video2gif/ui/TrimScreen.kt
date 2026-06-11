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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.dodotechhk.video2gif.ClipConstraints
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.clampEndMs
import com.dodotechhk.video2gif.clampStartMs
import com.dodotechhk.video2gif.exceedsMaxClip
import com.dodotechhk.video2gif.isValidClip

/** 毫秒 → 一位小数的秒("12.3s"),给所有时间文案统一观感。 */
private fun fmtSec(ms: Long): String = "%.1fs".format(ms / 1000f)

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
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶栏:返回 | 标题 | Next(右上角)。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Trim",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    // 超 10s 在此拦下并提示,不进预览;否则放行。
                    if (exceedsMaxClip(state.clipStartMs, state.clipEndMs)) {
                        Toast.makeText(
                            context,
                            "Max ${ClipConstraints.MAX_CLIP_MS / 1000} seconds",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        onNext()
                    }
                },
                enabled = valid,
            ) { Text("Next") }
        }

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

        // 选区信息:居中,选段时长为主、起止为辅。
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${fmtSec(length)} selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${fmtSec(state.clipStartMs)} – ${fmtSec(state.clipEndMs)} · source ${fmtSec(state.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
                // 左右两个滑竿手柄为黑色;中间选中段轨道保持主题蓝。
                colors = SliderDefaults.colors(thumbColor = Color.Black),
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
                            color = Color.Black,
                            shape = RoundedCornerShape(1.dp),
                        ),
                )
            }
        }

        Text(
            "Min ${ClipConstraints.MIN_CLIP_MS / 1000f}s · max ${ClipConstraints.MAX_CLIP_MS / 1000}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!valid) {
            Text(
                "Invalid range",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
