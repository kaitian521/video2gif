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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.AspectRatio
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.MediaStoreSaver
import com.dodotechhk.video2gif.centerCropHalfExtents
import com.dodotechhk.video2gif.clampedCropCenter
import com.dodotechhk.video2gif.withClampedOffsets
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
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val outAR = state.outputAspectRatio
            val (vw, vh) = if (outAR <= 9f / 15f) {
                maxHeight * outAR to maxHeight
            } else {
                maxWidth to maxWidth / outAR
            }
            // 源比例 cover 外框:源更宽 → 定高、左右溢出;源更高 → 定宽、上下溢出。
            val srcAR = state.sourceAspectRatio
            val (videoW, videoH) = if (srcAR >= outAR) vh * srcAR to vh else vw to vw / srcAR
            // 手势换算用的内容像素尺寸(随比例/布局变;rememberUpdatedState 防 pointerInput 闭包读旧值)。
            val density = LocalDensity.current
            val contentPx by rememberUpdatedState(
                with(density) { videoW.toPx() } to with(density) { videoH.toPx() },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // P5/P6 手势:单指拖动移位 + 双指缩放,统一走 transform。
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val s = (currentState.scale * zoom).coerceIn(1f, MAX_SCALE)
                            val scaled = currentState.copy(scale = s)
                            val (wPx, hPx) = contentPx
                            val (halfW, halfH) = centerCropHalfExtents(scaled)
                            // 跟手 1:1:内容全宽 wPx·s px ↔ NDC 跨度 2,拖内容 = 窗口反向移动;
                            // NDC y 朝上、屏幕 y 朝下 → y 取反。从**已夹紧**的当前中心起算并
                            // 夹紧后写回(比例/缩放变过也不留空拖死区,实施计划 P6 步骤 4)。
                            val (cx0, cy0) = clampedCropCenter(scaled)
                            val cx = (cx0 - pan.x * 2f / (wPx * s)).coerceIn(halfW - 1f, 1f - halfW)
                            val cy = (cy0 + pan.y * 2f / (hPx * s)).coerceIn(halfH - 1f, 1f - halfH)
                            onChange(scaled.copy(offsetX = cx, offsetY = cy))
                        }
                    }
                    // 双击复位:缩放 + 位置一起归零。
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            onChange(currentState.copy(scale = 1f, offsetX = 0f, offsetY = 0f))
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 满帧播放:预览不裁不移(aspect=Original、scale=1、offset=0);视觉裁切 =
                // 源比例 cover 外框 + clipToBounds,缩放/平移由 graphicsLayer 表现,导出才真裁。
                Box(
                    modifier = Modifier
                        .width(vw)
                        .height(vh)
                        .clipToBounds()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    // requiredWidth/Height 允许超出父约束(Compose 自动居中溢出部分)。
                    VideoPreview(
                        state = state.copy(
                            aspect = AspectRatio.Original,
                            scale = 1f,
                            offsetX = 0f,
                            offsetY = 0f,
                        ),
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
                                val s = state.scale
                                scaleX = s
                                scaleY = s
                                // 把窗口中心 (cx,cy) 平移到外框中心:NDC → px 乘内容半宽/半高×s,
                                // x 反向(内容左移露出右部),y 再取反回屏幕系。与导出 cropEffect
                                // 共用 clampedCropCenter 真值。
                                val (cx, cy) = clampedCropCenter(state)
                                translationX = -cx * size.width / 2f * s
                                translationY = cy * size.height / 2f * s
                            },
                    )
                    // 白色预览框:作为视频之后的兄弟节点画在最上层,贴外框内缘。
                    // 外框 == 裁切窗口 == 导出保留区,框线所在即导出边界。
                    Box(Modifier.matchParentSize().border(2.dp, Color.White))
                }
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
                    // 切比例后把偏移夹回新窗口的合法域(消除空拖死区)。
                    onClick = { onStateChange(state.copy(aspect = aspect).withClampedOffsets()) },
                    label = { Text(aspect.label) },
                )
            }
        }

        Text(
            "比例:${state.aspect.label} · 缩放:${"%.1f".format(state.scale)}×" +
                "(拖动移位,双指缩放,双击复位)",
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
