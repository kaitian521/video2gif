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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

        // 效果管线预览:循环播放选区。占据中间可用空间。
        VideoPreview(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Text(
            "目标高度:${state.targetHeight}px(P4 起加入比例/缩放/旋转/拖动)",
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
