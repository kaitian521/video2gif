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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.AspectRatio
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.ExportFormat
import com.dodotechhk.video2gif.ExportPrefs
import com.dodotechhk.video2gif.ExportQuality
import com.dodotechhk.video2gif.FormatConverter
import com.dodotechhk.video2gif.MediaStoreSaver
import com.dodotechhk.video2gif.centerCropHalfExtents
import com.dodotechhk.video2gif.clampedCropCenter
import com.dodotechhk.video2gif.withClampedOffsets
import com.dodotechhk.video2gif.VideoExporter
import com.dodotechhk.video2gif.ui.theme.ChipSelected
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/** 最大放大倍数(取景窗口最多缩到 1/MAX_SCALE)。 */
private const val MAX_SCALE = 8f

/**
 * 选中态强调色 chips(比例 / 导出面板各选项组共用):默认 secondaryContainer 在暗黑模式下
 * 发暗、选中不明显,改用强调橙 + 深色文字(明暗主题都鲜明);导出中禁用时选中项保留半透橙。
 */
@Composable
private fun accentChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = ChipSelected,
    selectedLabelColor = Color.Black,
    disabledSelectedContainerColor = ChipSelected.copy(alpha = 0.5f),
)

/** 可选输出分辨率(目标高度,px;宽按比例派生)。技术方案 §导出参数。 */
private val RESOLUTION_HEIGHTS = listOf(240, 360, 480, 540, 720, 1080)

/** 可选最大输出帧率(mp4 setFrameRate 上限 / GIF·WebP fps 滤镜)。 */
private val MAX_FPS_OPTIONS = listOf(10, 15, 20, 25, 30)

/** P7 变速范围(>0,下限远离 0 避免输出时长/体积爆炸,§5.4)。 */
private const val SPEED_MIN = 0.5f
private const val SPEED_MAX = 2f

/** 速度显示:最多两位小数,去尾零(1×、1.5×、0.55×)。 */
private fun formatSpeed(speed: Float): String =
    "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}×"

/**
 * P3 预览页:独立于截取页,承载效果管线预览(`buildVideoEffects` → ExoPlayer.setVideoEffects)。
 * 循环播放当前选区 `[clipStartMs, clipEndMs]`。
 *
 * 当前仅 `Presentation`(P3 骨架);P4–P6 的比例/缩放/旋转/拖动控件后续叠加到此页,
 * 通过 [onStateChange] 回写 [EditState] 触发 `applyEffects`。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    // 导出 harness 状态:验证 targetHeight 真实输出尺寸 + 产物相册可见 + 进度/取消(P8)。
    var exportStatus by remember(state.sourceUri) { mutableStateOf("") }
    var exporting by remember(state.sourceUri) { mutableStateOf(false) }
    var exportSession by remember(state.sourceUri) {
        mutableStateOf<VideoExporter.ExportSession?>(null)
    }
    // P9 二次转码会话(GIF/WebP 阶段);取消按钮按当前阶段中止对应会话。
    var convertSession by remember(state.sourceUri) {
        mutableStateOf<FormatConverter.ConvertSession?>(null)
    }
    // 点「导出」弹出底部面板,所有导出选项集中其中。
    var showExportSheet by remember(state.sourceUri) { mutableStateOf(false) }
    // P10:最近一次导出成功的产物(相册 Uri + 格式),供「分享」;新一次导出开始时清空。
    var savedResult by remember(state.sourceUri) {
        mutableStateOf<Pair<android.net.Uri, ExportFormat>?>(null)
    }
    // 导出成功对话框(面板收起后弹出,提供分享入口)。
    var showSuccessDialog by remember(state.sourceUri) { mutableStateOf(false) }

    // 结果提示:成功/失败都 Toast(面板已收起或被关掉时也能看到)。
    val toast: (String) -> Unit = { msg ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    // 把产物落相册并收尾(成功/失败都结束导出态)。
    val finishWithSave: (File, ExportFormat, String) -> Unit = { file, format, detail ->
        scope.launch {
            val uri = MediaStoreSaver.save(context, file, format)
            // 中间产物/已复制完的 cache 文件都清掉(P10:不留残留)。
            file.delete()
            exporting = false
            // 终态:收起面板,Toast 报结果(成功产物仍可重开面板分享)。
            showExportSheet = false
            if (uri != null) {
                savedResult = uri to format
                val dir = if (format.isVideo) "Movies" else "Pictures"
                exportStatus = "Saved to gallery ($dir/Video2gif, ${format.label}): $detail"
                showSuccessDialog = true
            } else {
                exportStatus = "Export OK ($detail), but saving to gallery failed"
                toast("Export failed: saving to gallery failed")
            }
        }
    }

    // P10 分享:把相册里的产物经系统分享面板发出去(MediaStore uri 可直接授权读)。
    val shareSaved = shareSaved@{
        val (uri, format) = savedResult ?: return@shareSaved
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share ${format.label}"))
    }

    // 启动一次导出(P8+P9):Transformer 先出中间 mp4;mp4 直存,GIF/WebP 再跑 ffmpeg 转码。
    // 导出与面板可见性解耦(关面板不中断导出)。
    val startExport = startExport@{
        if (exporting) return@startExport
        exporting = true
        savedResult = null // 新一轮导出,旧产物不再供分享(相册里仍在)。
        ExportPrefs.save(context, state) // 记住本次选择,下次导入作为默认。
        val format = state.format
        val twoPhase = !format.isVideo
        val phase1 = if (twoPhase) "1/2 encoding mp4" else "Exporting mp4"
        exportStatus = "$phase1… (keep app open)"
        val mp4File = File(context.cacheDir, "export_intermediate.mp4")
        exportSession = VideoExporter.export(
            context, state, mp4File,
            onProgress = { p -> exportStatus = "$phase1 $p%… (keep app open)" },
        ) { result ->
            exportSession = null
            when (result) {
                is VideoExporter.Result.Success -> {
                    val size = "${result.width}×${result.height}, ${result.durationMs} ms"
                    if (!twoPhase) {
                        exportStatus = "Export OK: $size, saving to gallery…"
                        finishWithSave(mp4File, ExportFormat.Mp4, size)
                    } else {
                        // P9:对中间 mp4 跑 ffmpeg(fps+调色板/libwebp_anim,不 scale)。
                        // ffmpeg-kit 回调在后台线程,经 scope.launch 切回主线程更新 UI。
                        exportStatus = "2/2 converting to ${format.label}… (keep app open)"
                        val outFile = File(context.cacheDir, "export_out.${format.extension}")
                        convertSession = FormatConverter.convert(
                            mp4File, outFile, format, state.quality,
                            fps = state.maxFps,
                            expectedDurationMs = result.durationMs,
                            onProgress = { p ->
                                scope.launch { exportStatus = "2/2 converting to ${format.label} $p%… (keep app open)" }
                            },
                        ) { convResult ->
                            scope.launch {
                                convertSession = null
                                mp4File.delete() // 中间 mp4 用完即清。
                                when (convResult) {
                                    is FormatConverter.Result.Success -> {
                                        exportStatus = "Conversion OK: $size, saving to gallery…"
                                        finishWithSave(outFile, format, size)
                                    }

                                    is FormatConverter.Result.Error -> {
                                        exporting = false
                                        showExportSheet = false
                                        exportStatus = "Conversion failed: ${convResult.message} (leftovers cleaned)"
                                        toast("Export failed: ${format.label} conversion error")
                                    }

                                    FormatConverter.Result.Cancelled -> {
                                        exporting = false
                                        exportStatus = "Cancelled (output cleaned)"
                                    }
                                }
                            }
                        }
                    }
                }

                is VideoExporter.Result.Error -> {
                    exporting = false
                    showExportSheet = false
                    exportStatus = "Export failed: ${result.message} (leftovers cleaned)"
                    toast("Export failed: ${result.message}")
                }

                VideoExporter.Result.Cancelled -> {
                    exporting = false
                    exportStatus = "Cancelled (output cleaned)"
                }
            }
        }
    }

    // 手势用最新 state/回调(pointerInput 闭包内避免读到旧值)。
    val currentState by rememberUpdatedState(state)
    val onChange by rememberUpdatedState(onStateChange)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶栏:返回(图标,与截取页一致)| 标题。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Preview",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

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
                }
                // 白色预览框:画在播放器**外缘**——盒子比裁切窗口大一圈(2dp),边线落在窗口外侧,
                // 不再覆盖视频边缘像素。与裁切窗口同心(同在居中的手势 Box 内)。
                Box(
                    Modifier
                        .width(vw + 4.dp)
                        .height(vh + 4.dp)
                        .border(2.dp, Color.White),
                )
            }
            // 手势提示:半透明胶囊,叠在预览底部居中,不挡操作(无点击)。
            Text(
                "Pinch to zoom · Drag to move",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
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
                    colors = accentChipColors(),
                )
            }
        }

        // P7:变速滑竿(0.5×–2×,连续无步进)。时间轴变换:
        // 预览播放器倍速 / 导出 setSpeed,同一 state.speed。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val speedStyle = MaterialTheme.typography.bodyMedium
            Text("Speed", style = speedStyle)
            Slider(
                value = state.speed,
                onValueChange = { onStateChange(state.copy(speed = it)) },
                valueRange = SPEED_MIN..SPEED_MAX,
                modifier = Modifier.weight(1f),
            )
            // 数值定宽(按最宽样本 "9.99×" 实测):文字随值变宽会挤压 weight 滑竿的
            // 轨道宽度,thumb 位置跟着重算 → 抖。定宽后轨道宽度恒定。
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val valueWidth = remember(textMeasurer, speedStyle, density) {
                with(density) { textMeasurer.measure("9.99×", speedStyle).size.width.toDp() }
            }
            Text(
                formatSpeed(state.speed),
                style = speedStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.width(valueWidth),
            )
        }

        // 底部操作:打开导出面板(返回已上移到顶栏图标)。导出选项全部收进 ModalBottomSheet。
        // 通栏按钮:距屏幕左右各 40dp(Column 16dp + 此处 24dp)。
        Button(
            onClick = { showExportSheet = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(if (exporting) "Exporting…" else "Export")
        }
    }

    // 导出面板:清晰度三档 + 开始导出 + 进度/取消,集中在底部弹片。
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            // 打开即完全展开,跳过半展开档。
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Export Options", style = MaterialTheme.typography.titleLarge)

                // 统一的「标题在上、选项在下」分组:标题用 titleSmall 突出层次。
                @Composable
                fun OptionSection(title: String, chips: @Composable () -> Unit) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) { chips() }
                    }
                }

                // P9 格式:默认 GIF;mp4 直出,GIF/WebP 由 ffmpeg 对中间 mp4 转码。
                OptionSection("Format") {
                    ExportFormat.values().forEach { f ->
                        FilterChip(
                            selected = state.format == f,
                            enabled = !exporting,
                            onClick = { onStateChange(state.copy(format = f)) },
                            label = { Text(f.label) },
                            colors = accentChipColors(),
                        )
                    }
                }

                // 分辨率(目标高度,px;宽按比例派生)= 像素尺寸唯一真值(Presentation.createForHeight)。
                OptionSection("Resolution") {
                    RESOLUTION_HEIGHTS.forEach { h ->
                        FilterChip(
                            selected = state.targetHeight == h,
                            enabled = !exporting,
                            onClick = { onStateChange(state.copy(targetHeight = h)) },
                            label = { Text("${h}p") },
                            colors = accentChipColors(),
                        )
                    }
                }

                // 最大帧率五档:mp4 setFrameRate 上限 / GIF·WebP fps 滤镜(与清晰度解耦)。
                OptionSection("Frame rate") {
                    MAX_FPS_OPTIONS.forEach { fps ->
                        FilterChip(
                            selected = state.maxFps == fps,
                            enabled = !exporting,
                            onClick = { onStateChange(state.copy(maxFps = fps)) },
                            label = { Text("$fps") },
                            colors = accentChipColors(),
                        )
                    }
                }

                // 清晰度五档(mp4 码率 k×W×H×maxFps / GIF 颜色抖动 / WebP q;§10.2 已标定)。
                OptionSection("Quality") {
                    ExportQuality.values().forEach { q ->
                        FilterChip(
                            selected = state.quality == q,
                            enabled = !exporting,
                            onClick = { onStateChange(state.copy(quality = q)) },
                            label = { Text(q.label) },
                            colors = accentChipColors(),
                        )
                    }
                }

                if (exportStatus.isNotEmpty()) {
                    Text(exportStatus, style = MaterialTheme.typography.bodyMedium)
                }

                // 主按钮通栏:距屏幕左右各 40dp(面板 24dp + 此处 16dp)。
                Button(
                    enabled = !exporting,
                    onClick = { startExport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(if (exporting) "Exporting…" else "Start Export")
                }
                if (exporting) {
                    // 按当前阶段取消:阶段 1 中止 Transformer,阶段 2 中止 ffmpeg。
                    OutlinedButton(
                        onClick = {
                            exportSession?.cancel()
                            convertSession?.cancel()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) { Text("Cancel") }
                }
                // P10:导出成功后可直接分享相册里的产物。
                if (!exporting && savedResult != null) {
                    OutlinedButton(
                        onClick = { shareSaved() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) { Text("Share") }
                }
            }
        }
    }

    // 导出成功对话框:标题右侧关闭按钮,正文提示已存相册,主操作为分享。
    if (showSuccessDialog) {
        val savedDir = if (savedResult?.second?.isVideo == true) "Movies" else "Pictures"
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Export Success", modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSuccessDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            },
            text = { Text("Saved to gallery ($savedDir/Video2gif). You can share it now.") },
            confirmButton = {
                Button(onClick = {
                    showSuccessDialog = false
                    shareSaved()
                }) { Text("Share") }
            },
        )
    }
}
