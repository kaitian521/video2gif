package com.dodotechhk.video2gif.ui

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用内媒体循环播放(作品播放弹窗 / 导出成功预览共用):
 * - 视频(mp4):ExoPlayer,REPEAT_MODE_ALL,静音(导出无音轨,保持一致)。
 * - 动图(GIF/WebP):ImageDecoder 解出 AnimatedImageDrawable 无限循环
 *   (API 28+;低版本回退静态首帧,minSdk 24)。
 */
@Composable
fun MediaPlayback(uri: String, isVideo: Boolean, modifier: Modifier = Modifier) {
    if (isVideo) VideoPlayback(uri, modifier) else AnimatedImagePlayback(uri, modifier)
}

@Composable
private fun VideoPlayback(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
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
        modifier = modifier,
    )
}

@Composable
private fun AnimatedImagePlayback(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(context.contentResolver, parsed)
                    )
                } else {
                    context.contentResolver.openInputStream(parsed)?.use {
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
        modifier = modifier,
    )
}
