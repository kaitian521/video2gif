package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dodotechhk.video2gif.EditState
import com.dodotechhk.video2gif.centerCropHalfExtents

/**
 * 裁剪框遮罩(P4 / 剪映式)。覆盖在**满帧播放**的视频上,用半透明压暗 + 边框标出
 * 中心裁剪保留区。区域来自 [centerCropHalfExtents](与导出 `cropEffect` 同一真值 → 所见即所得)。
 *
 * 切比例时只重绘此遮罩,**不重塑视频表面** → 不抖不闪。P5/P6 的缩放/旋转/拖动后续在此叠加。
 *
 * 必须与视频显示矩形**同尺寸、同位置**(调用方把二者放进同一个按源比例定的 Box)。
 */
@Composable
fun CropOverlay(
    state: EditState,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.5f,
    borderWidth: Dp = 2.dp,
) {
    val (halfW, halfH) = remember(state.aspect, state.displayWidth, state.displayHeight) {
        centerCropHalfExtents(state)
    }
    val scrim = Color.Black.copy(alpha = scrimAlpha)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cw = w * halfW
        val ch = h * halfH
        val left = (w - cw) / 2f
        val top = (h - ch) / 2f

        // 保留区之外压暗(上/下/左/右四条)。
        drawRect(scrim, Offset(0f, 0f), Size(w, top))
        drawRect(scrim, Offset(0f, top + ch), Size(w, h - top - ch))
        drawRect(scrim, Offset(0f, top), Size(left, ch))
        drawRect(scrim, Offset(left + cw, top), Size(w - left - cw, ch))

        // 保留区边框。
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(cw, ch),
            style = Stroke(width = borderWidth.toPx()),
        )
    }
}
