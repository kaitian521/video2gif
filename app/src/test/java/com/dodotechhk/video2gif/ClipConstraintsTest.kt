package com.dodotechhk.video2gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2 区间约束纯函数单测:验证「区间正确夹在 [500ms, 10s]」(无需真机)。 */
class ClipConstraintsTest {

    @Test
    fun default_end_caps_at_max_else_duration() {
        assertEquals(10_000L, defaultClipEndMs(30_000L)) // 长视频封顶 10s
        assertEquals(3_000L, defaultClipEndMs(3_000L))   // 短视频取全长
    }

    @Test
    fun clamp_end_enforces_min_length() {
        // start=0,end 拖到 100 → 不足 500ms,夹到 500
        assertEquals(500L, clampEndMs(desiredEndMs = 100L, startMs = 0L, durationMs = 30_000L))
    }

    @Test
    fun clamp_end_enforces_max_length() {
        // start=2000,end 拖到 29000 → 超 10s,夹到 12000
        assertEquals(12_000L, clampEndMs(desiredEndMs = 29_000L, startMs = 2_000L, durationMs = 30_000L))
    }

    @Test
    fun clamp_end_caps_at_duration() {
        // start=0,end 拖出总时长 → 夹到 duration
        assertEquals(8_000L, clampEndMs(desiredEndMs = 50_000L, startMs = 0L, durationMs = 8_000L))
    }

    @Test
    fun clamp_start_enforces_min_length() {
        // end=5000,start 拖到 4900 → 不足 500ms,夹到 4500
        assertEquals(4_500L, clampStartMs(desiredStartMs = 4_900L, endMs = 5_000L))
    }

    @Test
    fun clamp_start_enforces_max_length() {
        // end=15000,start 拖到 0 → 超 10s,夹到 5000
        assertEquals(5_000L, clampStartMs(desiredStartMs = 0L, endMs = 15_000L))
    }

    @Test
    fun clamp_start_not_negative() {
        // end=3000,start 拖到负数 → 夹到 0
        assertEquals(0L, clampStartMs(desiredStartMs = -1_000L, endMs = 3_000L))
    }

    @Test
    fun valid_clip_boundaries() {
        assertTrue(isValidClip(0L, 500L, 30_000L))      // 恰好下限
        assertTrue(isValidClip(0L, 10_000L, 30_000L))   // 恰好上限
        assertFalse(isValidClip(0L, 499L, 30_000L))     // 过短
        assertFalse(isValidClip(0L, 10_001L, 30_000L))  // 过长
        assertFalse(isValidClip(0L, 5_000L, 4_000L))    // end 超出总时长
    }
}
