package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.AspectRatio
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.MediaStoreSaver
import com.dodotechhk.video2gif.VideoExporter
import kotlinx.coroutines.launch
import java.io.File

/** 最大放大倍数(取景窗口最多缩到 1/MAX_SCALE)。 */
private const val MAX_SCALE = 8f

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

    // 手势用最新 state/回调(pointerInput 闭包内避免读到旧值)。
    val currentState by rememberUpdatedState(state)
    val onChange by rememberUpdatedState(onStateChange)

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

        // 预览:无取景框,裁切窗口(外框)**严格等于所选比例**。
        // 尺寸规则:outAR ≤ 9/15 → 定高(占满可用高),宽自适应;否则定宽(占满可用宽),高自适应。
        // 无黑边的实现:PlayerView 始终保持**源比例**并 cover 外框,溢出由 clipToBounds 裁掉
        // (中心裁切)。不能依赖 resize_mode:media3 1.10.1 启用 setVideoEffects 后
        // Player 不上报 videoSize(b/292111083),AspectRatioFrameLayout 拿到 0 恒不调整;
        // 且效果管线会把画面 SCALE_TO_FIT(letterbox)进 surface —— 只有让 surface 比例
        // 恒等于内容比例,letterbox 才恒为 no-op。可见区域 == 导出 cropEffect 的中心
        // 裁剪窗口(同一真值,WYSIWYG)。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // 双指缩放:放大视频;夹在 [1, MAX_SCALE]。
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val s = (currentState.scale * zoom).coerceIn(1f, MAX_SCALE)
                        onChange(currentState.copy(scale = s))
                    }
                }
                // 双击复位缩放。
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onChange(currentState.copy(scale = 1f)) })
                },
            contentAlignment = Alignment.Center,
        ) {
            val outAR = state.outputAspectRatio
            val (vw, vh) = if (outAR <= 9f / 15f) {
                maxHeight * outAR to maxHeight
            } else {
                maxWidth to maxWidth / outAR
            }

            // 满帧播放:预览不裁不缩(aspect=Original、scale=1);视觉裁切 = 源比例 cover 外框
            // + clipToBounds,缩放由 graphicsLayer 表现,导出才真裁。
            Box(
                modifier = Modifier
                    .width(vw)
                    .height(vh)
                    .clipToBounds()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                // 源比例 cover 外框:源更宽 → 定高、左右溢出;源更高 → 定宽、上下溢出。
                // requiredWidth/Height 允许超出父约束(Compose 自动居中溢出部分)。
                val srcAR = state.sourceAspectRatio
                val (videoW, videoH) = if (srcAR >= outAR) vh * srcAR to vh else vw to vw / srcAR
                VideoPreview(
                    state = state.copy(aspect = AspectRatio.Original, scale = 1f),
                    onVideoDisplaySize = { vwPx, vhPx ->
                        // 用播放器真实尺寸校正源宽高比,让预览几何与导出 Crop 同源(否则框≠导出)。
                        // 注:1.10.1 开 setVideoEffects 后此回调不触发(b/292111083),
                        // 实际依赖导入时 MMR 读到的 displayWidth/Height;留作版本升级后的兜底。
                        val reported = vwPx.toFloat() / vhPx
                        if (kotlin.math.abs(reported - currentState.sourceAspectRatio) > 0.01f) {
                            onChange(currentState.copy(displayWidth = vwPx, displayHeight = vhPx))
                        }
                    },
                    modifier = Modifier
                        .requiredWidth(videoW)
                        .requiredHeight(videoH)
                        .graphicsLayer {
                            scaleX = state.scale
                            scaleY = state.scale
                        },
                )
                // 白色预览框:作为视频之后的兄弟节点画在最上层,贴外框内缘。
                // 外框 == 裁切窗口 == 导出保留区,框线所在即导出边界。
                Box(Modifier.matchParentSize().border(2.dp, Color.White))
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
            "比例:${state.aspect.label} · 缩放:${"%.1f".format(state.scale)}×(双指缩放,双击复位)",
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
