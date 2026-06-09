package com.dodotechhk.video2gif

import androidx.media3.common.Effect
import androidx.media3.effect.Presentation

/**
 * 像素变换的唯一真值:预览(ExoPlayer.setVideoEffects)与导出(Transformer)共用同一条
 * 视觉效果链,从而保证 WYSIWYG(技术方案 §4)。
 *
 * 链路顺序(从 P3 起逐阶段补全):`ScaleAndRotate`(P5)→ `Crop`(P4/P6)→ `Presentation`(P3)。
 * `Presentation` 放在最后,把内容定到目标输出高度(宽由比例派生)。
 *
 * 注意:**变速不进这里**(它改帧时间戳),单独走 [EditState] 的 speed —— 预览用播放器倍速、
 * 导出用 `EditedMediaItem.Builder#setSpeed`(技术方案 §5.4 / 实施计划 P7)。
 */
fun buildVideoEffects(state: EditState): List<Effect> = listOf(
    // P3:仅 Presentation,把输出定到 targetHeight(宽按内容比例派生)。
    // 计划里的 copyWithUnsetSideRoundedTo(2) 在 1.10.1 未确认存在,故不用;
    // 偶数维度由偶数 targetHeight + 导出端 encoder 兜底。
    Presentation.createForHeight(state.targetHeight),
)
