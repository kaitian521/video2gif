package com.dodotechhk.video2gif.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.ExportPrefs
import com.dodotechhk.video2gif.R
import com.dodotechhk.video2gif.VideoImporter
import com.dodotechhk.video2gif.defaultClipEndMs
import com.dodotechhk.video2gif.ui.theme.HomeCardGradientEnd
import com.dodotechhk.video2gif.ui.theme.HomeCardGradientStart
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
        // 标题栏:app 名 + 设置入口(与作品页共用 HomeHeader)。
        HomeHeader(
            title = stringResource(R.string.home_header),
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        // 首页入口卡片:点击进相册选视频。金黄→品牌橙渐变 + 白字,比默认灰底更醒目。
        Card(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            enabled = status != ImportStatus.Loading,
            elevation = CardDefaults.elevatedCardElevation(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(HomeCardGradientStart, HomeCardGradientEnd)
                        )
                    )
                    .padding(vertical = 48.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.home_pick_video),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
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
