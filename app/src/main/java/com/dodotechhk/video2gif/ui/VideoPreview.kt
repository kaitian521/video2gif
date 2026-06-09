package com.dodotechhk.video2gif.ui

import android.view.LayoutInflater
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
import com.dodotechhk.video2gif.R
import com.dodotechhk.video2gif.buildVideoEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 截取页 / 预览页内嵌视频预览。
 *
 * - ExoPlayer + PlayerView(布局 `R.layout.video_preview`):`surface_type=texture_view`
 *   避免外框尺寸变化时的 SurfaceView 黑闪;`resize_mode=fill` 避免切比例瞬间「框≠内容比例」
 *   被 letterbox 露黑边(稳态外框比例 == 输出比例,fill 即等比无拉伸)。
 * - **prepare 前**先 `setVideoEffects(buildVideoEffects)` 接通效果管线(P3 硬约束)。
 * - 循环播放当前选区 `[clipStartMs, clipEndMs]`(近似:seek 到 start、到 end 回跳;
 *   选区随滑块实时变化,无需重建播放器)。
 * - 静音(导出无音轨,保持一致)。
 *
 * 外框 w×h 由调用方按 [EditState.outputAspectRatio] 给定。
 */
@Composable
fun VideoPreview(
    state: EditState,
    onPositionChange: (Long) -> Unit = {},
    restartSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 仅当源切换时重建播放器;改选区/效果不重建。
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

    // 效果相关 state 变化时重建效果链(P4–P6 手势走这里)。
    // 用各效果字段做 key:任一变化即 re-apply,prepare 后再次 setVideoEffects 合法。
    LaunchedEffect(player, state.targetHeight, state.aspect) {
        player.setVideoEffects(buildVideoEffects(state))
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
                onPositionChange(startMs)
            } else {
                onPositionChange(pos)
            }
            delay(50)
        }
    }

    // 滑块改动并松手后,从左滑竿(clipStart)重新播放。
    LaunchedEffect(restartSignal) {
        if (restartSignal > 0) {
            player.seekTo(startMs)
            player.play()
            onPositionChange(startMs)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.video_preview, null) as PlayerView
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
