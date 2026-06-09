package com.dodotechhk.video2gif

/**
 * 截取区间约束(实施计划 P2 / 技术方案 §3)。
 *
 * 区间长度恒夹在 `[MIN_CLIP_MS, MAX_CLIP_MS]`,且落在 `[0, durationMs]` 内。
 * 这些是**纯函数**,便于单测(见 `ClipConstraintsTest`),UI 只负责把拖动事件转给它们。
 *
 * 注意:区间**只写进 [EditState]**(clipStartMs/clipEndMs),导出时才折进
 * `ClippingConfiguration`;为保证逐帧精确,导出必须走转码/effect-bake 路径,
 * 不走 trim optimization / edit-list(§3)。
 */
object ClipConstraints {
    /** 区间最小时长(ms)。 */
    const val MIN_CLIP_MS = 500L
    /** 区间最大时长(ms)。 */
    const val MAX_CLIP_MS = 10_000L
}

/** 默认区间结束点:从 0 开始,`end = min(duration, 上限)`。 */
fun defaultClipEndMs(durationMs: Long): Long =
    minOf(durationMs, ClipConstraints.MAX_CLIP_MS)

/**
 * start 拖到 [desiredStartMs](end 固定为 [endMs]):
 * 夹紧使 `500ms ≤ end-start ≤ 10s` 且 `start ≥ 0`。
 */
fun clampStartMs(desiredStartMs: Long, endMs: Long): Long {
    val lo = maxOf(0L, endMs - ClipConstraints.MAX_CLIP_MS)
    val hi = endMs - ClipConstraints.MIN_CLIP_MS
    return desiredStartMs.coerceIn(lo, maxOf(lo, hi))
}

/**
 * end 拖到 [desiredEndMs](start 固定为 [startMs]):
 * 夹紧使 `500ms ≤ end-start ≤ 10s` 且 `end ≤ duration`。
 */
fun clampEndMs(desiredEndMs: Long, startMs: Long, durationMs: Long): Long {
    val lo = startMs + ClipConstraints.MIN_CLIP_MS
    val hi = minOf(durationMs, startMs + ClipConstraints.MAX_CLIP_MS)
    return desiredEndMs.coerceIn(minOf(lo, hi), maxOf(lo, hi))
}

/** 区间是否合法:落在 `[0, duration]` 内,且长度在 `[MIN_CLIP_MS, MAX_CLIP_MS]`。 */
fun isValidClip(startMs: Long, endMs: Long, durationMs: Long): Boolean {
    val length = endMs - startMs
    return startMs in 0..durationMs &&
        endMs in 0..durationMs &&
        length in ClipConstraints.MIN_CLIP_MS..ClipConstraints.MAX_CLIP_MS
}
