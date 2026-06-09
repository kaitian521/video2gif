package com.dodotechhk.video2gif.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.VideoImporter
import kotlinx.coroutines.launch
import java.io.File

/** 导入页 UI 状态。 */
private sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Loaded(val state: EditState) : ImportUiState
    data class Rejected(val reason: String) : ImportUiState
}

/**
 * P1 导入页:相册选视频 → 校验时长 → 解析可读本地路径。
 * 用照片选择器 [ActivityResultContracts.PickVisualMedia](无需存储权限)。
 */
@Composable
fun ImportScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<ImportUiState>(ImportUiState.Idle) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui = ImportUiState.Loading
        scope.launch {
            ui = when (val result = VideoImporter.import(context, uri)) {
                is VideoImporter.Result.Success -> ImportUiState.Loaded(
                    EditState(
                        sourceUri = result.uri,
                        sourceLocalPath = result.localPath,
                        durationMs = result.durationMs,
                    )
                )

                is VideoImporter.Result.TooShort -> ImportUiState.Rejected(
                    "视频太短(${result.durationMs} ms),需 > ${VideoImporter.MIN_DURATION_MS} ms"
                )

                is VideoImporter.Result.Error -> ImportUiState.Rejected(result.message)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            enabled = ui !is ImportUiState.Loading,
        ) {
            Text("从相册选择视频")
        }

        Spacer(Modifier.height(24.dp))

        when (val state = ui) {
            ImportUiState.Idle ->
                Text("请选择一个 > ${VideoImporter.MIN_DURATION_MS}ms 的视频")

            ImportUiState.Loading ->
                CircularProgressIndicator()

            is ImportUiState.Rejected ->
                Text("✗ ${state.reason}", color = MaterialTheme.colorScheme.error)

            is ImportUiState.Loaded ->
                ImportedInfo(state.state)
        }
    }
}

@Composable
private fun ImportedInfo(state: EditState) {
    // 运行时复核 sourceLocalPath 真实可读(DoD:exists() && canRead() 为真)。
    val readable = remember(state.sourceLocalPath) {
        state.sourceLocalPath?.let { File(it).run { exists() && canRead() } } == true
    }
    Column(horizontalAlignment = Alignment.Start) {
        Text("✓ 导入成功,可进入下一步(P2)", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("时长:${state.durationMs} ms")
        Spacer(Modifier.height(8.dp))
        Text("sourceLocalPath:")
        Text(state.sourceLocalPath ?: "—")
        Spacer(Modifier.height(8.dp))
        Text("exists() && canRead():$readable")
    }
}
