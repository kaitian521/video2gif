package com.dodotechhk.video2gif.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 固定取景框遮罩(P4–P5 / 剪映式)。在画布中央画一个 [frameWidth]×[frameHeight] 的取景框,
 * 框外压暗 + 白色边框。
 *
 * **取景框尺寸由调用方传入**(与定视频基准尺寸 vw0/vh0 用的同一个 fw/fh)——二者必须同源,
 * 否则白框与视频填满区域不一致,会在框内留缝(历史 bug)。
 */
@Composable
fun CropFrameOverlay(
    frameWidth: Dp,
    frameHeight: Dp,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.5f,
    borderWidth: Dp = 2.dp,
) {
    val scrim = Color.Black.copy(alpha = scrimAlpha)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val fw = frameWidth.toPx().coerceAtMost(w)
        val fh = frameHeight.toPx().coerceAtMost(h)
        val left = (w - fw) / 2f
        val top = (h - fh) / 2f

        // 取景框之外压暗(上/下/左/右四条)。
        drawRect(scrim, Offset(0f, 0f), Size(w, top))
        drawRect(scrim, Offset(0f, top + fh), Size(w, h - top - fh))
        drawRect(scrim, Offset(0f, top), Size(left, fh))
        drawRect(scrim, Offset(left + fw, top), Size(w - left - fw, fh))

        // 取景框边框。
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(fw, fh),
            style = Stroke(width = borderWidth.toPx()),
        )
    }
}
