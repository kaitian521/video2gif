package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.AspectRatio
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.MediaStoreSaver
import com.dodotechhk.video2gif.VideoExporter
import kotlinx.coroutines.launch
import java.io.File

/**
 * P3 预览页:独立于截取页,承载效果管线预览(`buildVideoEffects` → ExoPlayer.setVideoEffects)。
 * 循环播放当前选区 `[clipStartMs, clipEndMs]`。
 *
 * 当前仅 `Presentation`(P3 骨架);P4–P6 的比例/缩放/旋转/拖动控件后续叠加到此页,
 * 通过 [onStateChange] 回写 [EditState] 触发 `applyEffects`。
 */
@Composable
fun PreviewScreen(
    state: EditState,
    onStateChange: (EditState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val length = state.clipEndMs - state.clipStartMs
    // 最小导出 harness 状态:验证 targetHeight 真实输出尺寸 + 产物相册可见。
    var exportStatus by remember(state.sourceUri) { mutableStateOf("") }
    var exporting by remember(state.sourceUri) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("预览", style = MaterialTheme.typography.titleLarge)
        Text(
            "区间:${state.clipStartMs} … ${state.clipEndMs} ms(时长 $length ms)",
            style = MaterialTheme.typography.bodyMedium,
        )

        // 预览:视频按**源比例**满帧播放(表面尺寸不随选比例变化 → 不抖不闪),
        // 上面叠 CropOverlay 标出裁剪保留区。真正裁剪在导出时由 cropEffect 生效(所见即所得)。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val ar = state.sourceAspectRatio
            // 源比例 FIT 进可用区,得到视频显示矩形。
            val (vw, vh) = if (maxWidth / ar <= maxHeight) {
                maxWidth to maxWidth / ar
            } else {
                maxHeight * ar to maxHeight
            }
            Box(
                modifier = Modifier
                    .width(vw)
                    .height(vh),
            ) {
                // 满帧播放:用 aspect=Original 让预览不裁(导出仍用带 aspect 的 state)。
                VideoPreview(
                    state = state.copy(aspect = AspectRatio.Original),
                    modifier = Modifier.fillMaxSize(),
                )
                CropOverlay(state = state, modifier = Modifier.fillMaxSize())
            }
        }

        // P4:比例选择(原始 / 1:1 / 3:4 / 4:3 / 16:9 / 9:16),即时生效、无黑边。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AspectRatio.values().forEach { aspect ->
                FilterChip(
                    selected = state.aspect == aspect,
                    onClick = { onStateChange(state.copy(aspect = aspect)) },
                    label = { Text(aspect.label) },
                )
            }
        }

        Text(
            "比例:${state.aspect.label} · 目标高度:${state.targetHeight}px",
            style = MaterialTheme.typography.bodySmall,
        )

        // P3 收尾:最小导出 harness,导出后读回编码尺寸,验证 targetHeight 真实生效。
        if (exportStatus.isNotEmpty()) {
            Text(exportStatus, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回截取") }
            Button(
                enabled = !exporting,
                onClick = {
                    exporting = true
                    exportStatus = "导出中…"
                    val outFile = File(context.cacheDir, "harness_export.mp4")
                    VideoExporter.export(context, state, outFile) { result ->
                        when (result) {
                            is VideoExporter.Result.Success -> {
                                val size = "${result.width}×${result.height}" +
                                    "(rotation=${result.rotation},期望高=${state.targetHeight})"
                                exportStatus = "导出 OK:$size,存相册中…"
                                // 发布到系统相册(IO 线程),回报结果。
                                scope.launch {
                                    val uri = MediaStoreSaver.saveVideo(context, outFile)
                                    exporting = false
                                    exportStatus = if (uri != null) {
                                        "已存到相册(Movies/Video2gif):$size"
                                    } else {
                                        "导出 OK:$size,但存相册失败"
                                    }
                                }
                            }

                            is VideoExporter.Result.Error -> {
                                exporting = false
                                exportStatus = "导出失败:${result.message}"
                            }
                        }
                    }
                },
            ) { Text(if (exporting) "导出中…" else "导出测试(验尺寸)") }
        }
    }
}
