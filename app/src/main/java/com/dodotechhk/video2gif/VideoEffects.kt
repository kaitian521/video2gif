package com.dodotechhk.video2gif

import androidx.media3.common.Effect

/**
 * 像素变换的唯一真值:预览(ExoPlayer.setVideoEffects)与导出(Transformer)共用同一条
 * 视觉效果链,从而保证 WYSIWYG(技术方案 §4)。
 *
 * P0 先返回空列表;从 P3 起依次加入 `Presentation` → `Crop` → `ScaleAndRotate` 等。
 *
 * 注意:**变速不进这里**(它改帧时间戳),单独走 [EditState] 的 speed —— 预览用播放器倍速、
 * 导出用 `EditedMediaItem.Builder#setSpeed`(技术方案 §5.4 / 实施计划 P7)。
 */
fun buildVideoEffects(state: EditState): List<Effect> = emptyList()
