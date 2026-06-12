package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.ExportFormat
import com.dodotechhk.video2gif.ExportQuality
import com.dodotechhk.video2gif.R

/** 可选输出分辨率(目标高度,px;宽按比例派生)。技术方案 §导出参数。 */
private val RESOLUTION_HEIGHTS = listOf(240, 360, 480, 540, 720, 1080)

/** 可选最大输出帧率(mp4 setFrameRate 上限 / GIF·WebP fps 滤镜)。 */
private val MAX_FPS_OPTIONS = listOf(10, 15, 20, 25, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewExportSheet(
    state: EditState,
    exporting: Boolean,
    exportStatus: String,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onStateChange: (EditState) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShareSaved: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 打开即完全展开,跳过半展开档。
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.export_options),
                style = MaterialTheme.typography.titleLarge,
            )

            // P9 格式:默认 GIF;mp4 直出,GIF/WebP 由 ffmpeg 对中间 mp4 转码。
            OptionSection(stringResource(R.string.export_format)) {
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
            OptionSection(stringResource(R.string.export_resolution)) {
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
            OptionSection(stringResource(R.string.export_frame_rate)) {
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
            OptionSection(stringResource(R.string.export_quality)) {
                ExportQuality.values().forEach { q ->
                    FilterChip(
                        selected = state.quality == q,
                        enabled = !exporting,
                        onClick = { onStateChange(state.copy(quality = q)) },
                        label = { Text(stringResource(q.labelRes)) },
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
                onClick = onStartExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (exporting) R.string.exporting else R.string.start_export
                    )
                )
            }
            if (exporting) {
                // 按当前阶段取消:阶段 1 中止 Transformer,阶段 2 中止 ffmpeg。
                OutlinedButton(
                    onClick = onCancelExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.cancel)) }
            }
            // P10:导出成功后可直接分享相册里的产物。
            if (!exporting && canShare) {
                OutlinedButton(
                    onClick = onShareSaved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.share)) }
            }
        }
    }
}

/**
 * 统一的「标题在上、选项在下」分组:标题用 titleSmall 突出层次。
 * 选项行可横滚时,两端加渐隐遮罩(右端附小箭头)提示还有更多选项。
 */
@Composable
private fun OptionSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        val scroll = rememberScrollState()
        val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { chips() }
            if (scroll.canScrollBackward) {
                Box(
                    Modifier
                        .matchParentSize()
                        .wrapContentWidth(Alignment.Start)
                        .width(28.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(sheetColor, Color.Transparent)
                            )
                        )
                )
            }
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .matchParentSize()
                        .wrapContentWidth(Alignment.End)
                        .width(40.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, sheetColor)
                            )
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
