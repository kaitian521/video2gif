package com.dodotechhk.video2gif.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.widget.ImageView
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
        // 标题栏:与导入页一致(左标题 + 右设置齿轮)。
        Box(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.tab_creation),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
            }
        }

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

    // 应用内播放弹窗:mp4 走 ExoPlayer,GIF/WebP 走 ImageDecoder 动图。
    playing?.let { rec ->
        PlaybackDialog(record = rec, onDismiss = { playing = null })
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

/**
 * 作品播放弹窗:按格式选播放器循环播放。
 * - Mp4:ExoPlayer(REPEAT_MODE_ALL,静音与导出无音轨一致)。
 * - Gif/WebP:ImageDecoder 解出 AnimatedImageDrawable 无限循环(API 28+;
 *   低版本回退静态首帧,minSdk 24)。
 */
@Composable
private fun PlaybackDialog(record: ExportRecord, onDismiss: () -> Unit) {
    val isVideo = runCatching { ExportFormat.valueOf(record.format).isVideo }.getOrDefault(false)
    val ratio =
        if (record.width > 0 && record.height > 0) record.width.toFloat() / record.height else 1f

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
                contentAlignment = Alignment.Center,
            ) {
                if (isVideo) VideoPlayback(record) else AnimatedImagePlayback(record)
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
    }
}

/** mp4 播放:循环、静音、无控制条,随弹窗关闭释放。 */
@Composable
private fun VideoPlayback(record: ExportRecord) {
    val context = LocalContext.current
    val player = remember(record.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(record.uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

/** GIF/WebP 播放:AnimatedImageDrawable 无限循环;API < 28 显示静态首帧。 */
@Composable
private fun AnimatedImagePlayback(record: ExportRecord) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, record.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(record.uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(context.contentResolver, uri)
                    )
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapDrawable(context.resources, BitmapFactory.decodeStream(it))
                    }
                }
            }.getOrNull()
        }
    }
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        },
        update = { view ->
            view.setImageDrawable(drawable)
            val d = drawable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start()
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
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
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
