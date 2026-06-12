package com.dodotechhk.video2gif.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.ExportPrefs
import com.dodotechhk.video2gif.R
import com.dodotechhk.video2gif.VideoImporter
import com.dodotechhk.video2gif.defaultClipEndMs
import kotlinx.coroutines.launch

/** 导入页 UI 状态(成功后通过回调离开本页,故无 Loaded 态)。 */
private sealed interface ImportStatus {
    data object Idle : ImportStatus
    data object Loading : ImportStatus
    data class Rejected(val reason: String) : ImportStatus
}

/**
 * P1 导入页:相册选视频 → 校验时长 → 解析可读本地路径。
 * 成功后构造初始 [EditState](含默认区间 [0, min(duration, 10s)])并回调 [onImported]。
 */
@Composable
fun ImportScreen(
    onImported: (EditState) -> Unit,
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ImportStatus>(ImportStatus.Idle) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = ImportStatus.Loading
        scope.launch {
            when (val result = VideoImporter.import(context, uri)) {
                is VideoImporter.Result.Success -> {
                    status = ImportStatus.Idle
                    onImported(
                        // 导出相关字段(格式/分辨率/帧率/清晰度)套用上次导出的选择。
                        ExportPrefs.applyTo(
                            context,
                            EditState(
                                sourceUri = result.uri,
                                sourceLocalPath = result.localPath,
                                durationMs = result.durationMs,
                                displayWidth = result.displayWidth,
                                displayHeight = result.displayHeight,
                                clipStartMs = 0L,
                                clipEndMs = defaultClipEndMs(result.durationMs),
                            ),
                        )
                    )
                }

                is VideoImporter.Result.TooShort -> status = ImportStatus.Rejected(
                    context.getString(
                        R.string.import_too_short,
                        result.durationMs,
                        VideoImporter.MIN_DURATION_MS,
                    )
                )

                is VideoImporter.Result.Error -> status = ImportStatus.Rejected(result.message)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 标题栏:显示 app 名(string 资源,与 launcher 一致)。
        Text(
            stringResource(R.string.home_header),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
        // 设置入口:右上角齿轮 → 设置页(隐私协议/版本号/联系我们)。
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        // 首页入口卡片:点击进相册选视频。
        Card(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            enabled = status != ImportStatus.Loading,
            elevation = CardDefaults.elevatedCardElevation(),
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.home_pick_video),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        when (val s = status) {
            ImportStatus.Idle -> {}

            ImportStatus.Loading ->
                CircularProgressIndicator()

            is ImportStatus.Rejected ->
                Text("✗ ${s.reason}", color = MaterialTheme.colorScheme.error)
        }
        }
    }
}
