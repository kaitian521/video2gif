package com.dodotechhk.video2gif.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dodotechhk.video2gif.ExportFormat
import com.dodotechhk.video2gif.R
import com.dodotechhk.video2gif.works.ExportRecord
import com.dodotechhk.video2gif.works.WorksDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creation 作品页(P15):列出历史导出产物(Room 索引,产物本体在相册)。
 * 点开 → 系统查看器;分享 → 系统分享面板;删除 → 二次确认后删相册文件 + 删记录。
 * 外部删除的产物在加载时校验清理(记录不残留)。
 */
@Composable
fun CreationScreen(
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { WorksDatabase.get(context).dao() }
    val records by dao.observeAll().collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<ExportRecord?>(null) }
    var playing by remember { mutableStateOf<ExportRecord?>(null) }

    // 外部删除清理:产物在相册被删后,索引记录同步移除。
    LaunchedEffect(records) {
        withContext(Dispatchers.IO) {
            records.forEach { rec ->
                val exists = runCatching {
                    context.contentResolver.query(
                        Uri.parse(rec.uri), arrayOf("_id"), null, null, null,
                    )?.use { it.count > 0 } ?: false
                }.getOrDefault(false)
                if (!exists) dao.deleteById(rec.id)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // 标题栏:与导入页共用 HomeHeader。
        HomeHeader(title = stringResource(R.string.tab_creation), onSettings = onSettings)

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.creation_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(records, key = { it.id }) { rec ->
                    WorkItem(
                        record = rec,
                        onOpen = { playing = rec },
                        onShare = { shareRecord(context, rec) },
                        onDelete = { pendingDelete = rec },
                    )
                }
            }
        }
    }

    // 应用内播放弹窗:mp4 走 ExoPlayer,GIF/WebP 走 ImageDecoder 动图;
    // 底部直接提供分享/删除,不必关弹窗回网格找按钮。
    playing?.let { rec ->
        PlaybackDialog(
            record = rec,
            onShare = { shareRecord(context, rec) },
            onDelete = { playing = null; pendingDelete = rec },
            onDismiss = { playing = null },
        )
    }

    // 删除二次确认:删相册文件 + 删索引。
    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.creation_delete_title)) },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    scope.launch(Dispatchers.IO) {
                        runCatching { context.contentResolver.delete(Uri.parse(rec.uri), null, null) }
                        dao.deleteById(rec.id)
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun mimeOf(rec: ExportRecord): String =
    runCatching { ExportFormat.valueOf(rec.format).mimeType }.getOrDefault("image/*")

/** 时长 → "m:ss"(四舍五入到秒)。 */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms + 500) / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** 字节数 → "1.2 MB" / "350 KB"。 */
private fun formatBytes(bytes: Long): String =
    if (bytes >= 1 shl 20) "%.1f MB".format(bytes / 1048576f)
    else "%d KB".format((bytes + 512) / 1024)

/**
 * 作品播放弹窗:[MediaPlayback] 循环播放,底部一排分享/删除操作
 * (删除走外部的二次确认流程)。
 */
@Composable
private fun PlaybackDialog(
    record: ExportRecord,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isVideo = runCatching { ExportFormat.valueOf(record.format).isVideo }.getOrDefault(false)
    val ratio =
        if (record.width > 0 && record.height > 0) record.width.toFloat() / record.height else 1f

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaPlayback(record.uri, isVideo, Modifier.fillMaxSize())
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    onClick = onShare,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.share))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

private fun shareRecord(context: android.content.Context, rec: ExportRecord) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(rec)
            putExtra(Intent.EXTRA_STREAM, Uri.parse(rec.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_title, rec.format))
        )
    }
}

/** 作品卡片:缩略图 + 格式/日期 + 分享/删除。 */
@Composable
private fun WorkItem(
    record: ExportRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    // 相册缩略图(API 29+;低版本占位灰)。
    val thumb by produceState<ImageBitmap?>(null, record.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver
                        .loadThumbnail(Uri.parse(record.uri), android.util.Size(512, 512), null)
                        .asImageBitmap()
                } else null
            }.getOrNull()
        }
    }
    // 文件大小(MediaStore OpenableColumns.SIZE;查不到则不显示)。
    val sizeBytes by produceState<Long?>(null, record.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(
                    Uri.parse(record.uri), arrayOf(OpenableColumns.SIZE), null, null, null,
                )?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 格式角标。
            Text(
                record.format.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            // 时长 · 文件大小角标(大小异步加载,查到前只显示时长)。
            Text(
                listOfNotNull(
                    formatDuration(record.durationMs),
                    sizeBytes?.let { formatBytes(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                DateFormat.format("MM-dd HH:mm", record.createdAt).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
