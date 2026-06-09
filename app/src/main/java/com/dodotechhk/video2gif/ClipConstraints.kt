package com.dodotechhk.video2gif

/**
 * 截取区间约束(实施计划 P2 / 技术方案 §3)。
 *
 * **滑动时**只硬夹「最短 [MIN_CLIP_MS]」与「不越界 `[0, durationMs]`」;**10s 上限是软约束**,
 * 允许用户滑过 10s,只在点「下一步」时用 [exceedsMaxClip] 拦下并提示。
 * 这些是**纯函数**,便于单测(见 `ClipConstraintsTest`),UI 只负责把拖动事件转给它们。
 *
 * 注意:区间**只写进 [EditState]**(clipStartMs/clipEndMs),导出时才折进
 * `ClippingConfiguration`;为保证逐帧精确,导出必须走转码/effect-bake 路径,
 * 不走 trim optimization / edit-list(§3)。
 */
object ClipConstraints {
    /** 区间最小时长(ms),硬约束:滑动时即夹紧。 */
    const val MIN_CLIP_MS = 500L
    /** 区间最大时长(ms),软约束:滑动不夹,点「下一步」时提示。 */
    const val MAX_CLIP_MS = 10_000L
}

/** 默认区间结束点:从 0 开始,`end = min(duration, 上限)`。 */
fun defaultClipEndMs(durationMs: Long): Long =
    minOf(durationMs, ClipConstraints.MAX_CLIP_MS)

/**
 * start 拖到 [desiredStartMs](end 固定为 [endMs]):
 * 夹紧使 `end-start ≥ 500ms` 且 `start ≥ 0`。**不夹 10s 上限**。
 */
fun clampStartMs(desiredStartMs: Long, endMs: Long): Long {
    val hi = endMs - ClipConstraints.MIN_CLIP_MS
    return desiredStartMs.coerceIn(0L, maxOf(0L, hi))
}

/**
 * end 拖到 [desiredEndMs](start 固定为 [startMs]):
 * 夹紧使 `end-start ≥ 500ms` 且 `end ≤ duration`。**不夹 10s 上限**。
 */
fun clampEndMs(desiredEndMs: Long, startMs: Long, durationMs: Long): Long {
    val lo = startMs + ClipConstraints.MIN_CLIP_MS
    return desiredEndMs.coerceIn(minOf(lo, durationMs), durationMs)
}

/**
 * 区间结构是否合法(可进入下一步的前提):落在 `[0, duration]` 内,且长度 `≥ MIN_CLIP_MS`。
 * **不含 10s 上限**——上限由 [exceedsMaxClip] 在「下一步」单独提示,不阻断选择。
 */
fun isValidClip(startMs: Long, endMs: Long, durationMs: Long): Boolean {
    val length = endMs - startMs
    return startMs in 0..durationMs &&
        endMs in 0..durationMs &&
        length >= ClipConstraints.MIN_CLIP_MS
}

/** 区间长度是否超过 10s 上限(用于点「下一步」时提示,不用于夹紧滑动)。 */
fun exceedsMaxClip(startMs: Long, endMs: Long): Boolean =
    endMs - startMs > ClipConstraints.MAX_CLIP_MS
