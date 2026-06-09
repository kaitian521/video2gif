package com.dodotechhk.video2gif.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.buildVideoEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 截取页内嵌视频预览。
 *
 * - ExoPlayer + PlayerView;**prepare 前**先 `setVideoEffects(buildVideoEffects)`
 *   接通效果管线骨架(P3 硬约束,当前为空列表 = 直通)。
 * - 循环播放当前选区 `[clipStartMs, clipEndMs]`(近似:seek 到 start、到 end 回跳;
 *   选区随滑块实时变化,无需重建播放器)。
 * - 静音(导出无音轨,保持一致)。
 */
@Composable
fun VideoPreview(
    state: EditState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 仅当源切换时重建播放器;改选区不重建。
    val player = remember(state.sourceUri) {
        ExoPlayer.Builder(context).build().apply {
            setVideoEffects(buildVideoEffects(state)) // 硬约束:prepare 前至少调一次
            state.sourceUri?.let { setMediaItem(MediaItem.fromUri(it)) }
            volume = 0f
            playWhenReady = true
            seekTo(state.clipStartMs)
            prepare()
        }
    }

    // 用 rememberUpdatedState 让循环始终读到最新选区。
    val startMs by rememberUpdatedState(state.clipStartMs)
    val endMs by rememberUpdatedState(state.clipEndMs)

    LaunchedEffect(player) {
        while (isActive) {
            val pos = player.currentPosition
            if (player.playbackState == Player.STATE_ENDED || pos >= endMs) {
                player.seekTo(startMs)
                player.play()
            }
            delay(50)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { useController = false } },
        update = { it.player = player },
        modifier = modifier,
    )
}
