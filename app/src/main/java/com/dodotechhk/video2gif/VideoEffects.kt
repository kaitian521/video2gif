package com.dodotechhk.video2gif

import androidx.media3.common.Effect
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import kotlin.math.roundToInt

/** P13 文字缩放范围(相对基准布局;上限另由窗口尺寸动态夹紧,不溢出)。 */
const val TEXT_SCALE_MIN = 0.3f
const val TEXT_SCALE_MAX = 4f

/**
 * 像素变换的唯一真值:预览(ExoPlayer.setVideoEffects)与导出(Transformer)共用同一条
 * 视觉效果链,从而保证 WYSIWYG(技术方案 §4)。
 *
 * 链路顺序:`Crop`(P4/P6)→ `Presentation`(P3)→ `OverlayEffect` 文字(P13)。
 * 文字放在 Presentation **之后**:此时帧 = 最终输出分辨率,bitmap 按输出尺寸生成、
 * 锚点坐标直接对应 `textPosX/Y`(裁切/平移/缩放都不影响字幕——字幕跟成品走)。
 *
 * 注意:**变速不进这里**(它改帧时间戳),单独走 [EditState] 的 speed —— 预览用播放器倍速、
 * 导出用 `EditedMediaItem.Builder#setSpeed`(技术方案 §5.4 / 实施计划 P7)。
 */
fun buildVideoEffects(state: EditState): List<Effect> = buildList {
    // P4:比例中心裁剪(P5 起 ScaleAndRotate 会插到 cropEffect 之前)。
    add(cropEffect(state))
    // P3:Presentation 把输出定到 targetHeight(宽按裁后比例派生)。
    // 计划里的 copyWithUnsetSideRoundedTo(2) 在 1.10.1 未确认存在,故不用;
    // 偶数维度由偶数 targetHeight + 导出端 encoder 兜底。
    add(Presentation.createForHeight(state.targetHeight))
    // P13:文字贴字(无文字时不加 effect,保持原链路)。
    textOverlayEffect(state)?.let { add(it) }
}

/**
 * P13 文字贴字 effect(多条):与预览共用 [TextOverlayRenderer] 生成的 bitmap(同源 → WYSIWYG)。
 * 每条 bitmap 按**最终输出分辨率**生成;位置经夹紧保证整框不出画面(不溢出)。
 */
fun textOverlayEffect(state: EditState): OverlayEffect? {
    if (state.texts.isEmpty()) return null
    val outH = state.targetHeight
    val outW = (outH * state.outputAspectRatio).roundToInt()
    val overlays: List<TextureOverlay> = state.texts.mapNotNull { item ->
        val bitmap = TextOverlayRenderer.renderScaled(
            content = item.content,
            fillColor = item.fillColor,
            strokeColor = item.strokeColor,
            bold = item.bold,
            winWPx = outW,
            winHPx = outH,
            scale = item.scale.coerceIn(TEXT_SCALE_MIN, TEXT_SCALE_MAX),
        ) ?: return@mapNotNull null

        // 夹紧:文字中心使整框落在画面内(导出端兜底;预览手势已夹过)。
        val halfW = bitmap.width / (2f * outW)
        val halfH = bitmap.height / (2f * outH)
        val px = if (halfW >= 0.5f) 0.5f else item.posX.coerceIn(halfW, 1f - halfW)
        val py = if (halfH >= 0.5f) 0.5f else item.posY.coerceIn(halfH, 1f - halfH)

        // 锚点:背景帧 NDC(x 右 y 上),(px,py) 是「x 右 y 下」的 0..1 → 转换;overlay 锚自身中心。
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(2f * px - 1f, 1f - 2f * py)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
        BitmapOverlay.createStaticBitmapOverlay(bitmap, settings)
    }
    if (overlays.isEmpty()) return null
    return OverlayEffect(overlays)
}

/**
 * 比例裁剪 + 拖动平移(实施计划 P4/P6 / 技术方案 §4)。NDC `[-1,1]` 上,
 * 窗口中心来自 [clampedCropCenter](偏移已夹紧,不越界),半宽/半高来自 [centerCropHalfExtents]。
 */
fun cropEffect(state: EditState): Crop {
    val (halfW, halfH) = centerCropHalfExtents(state)
    val (cx, cy) = clampedCropCenter(state)
    return Crop(cx - halfW, cx + halfW, cy - halfH, cy + halfH)
}
