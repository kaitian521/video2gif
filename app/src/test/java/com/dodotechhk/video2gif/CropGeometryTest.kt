package com.dodotechhk.video2gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P4 中心裁剪几何纯函数单测:验证「各比例半宽/半高 ∈ (0,1] 且裁后比例 == 目标比例」。
 * P5 会在此表上叠加 scale/旋转(内接矩形)继续扩展。
 */
class CropGeometryTest {

    /** 用源显示尺寸 + 目标比例构造 state。 */
    private fun state(w: Int, h: Int, aspect: AspectRatio) =
        EditState(displayWidth = w, displayHeight = h, aspect = aspect)

    @Test
    fun original_does_not_crop() {
        val (halfW, halfH) = centerCropHalfExtents(state(1920, 1080, AspectRatio.Original))
        assertEquals(1f, halfW, 1e-4f)
        assertEquals(1f, halfH, 1e-4f)
    }

    @Test
    fun square_on_landscape_crops_width_only() {
        // 16:9 源 → 1:1:裁宽,halfH=1,halfW=targetAR/srcAR=1/(16/9)=0.5625
        val (halfW, halfH) = centerCropHalfExtents(state(1920, 1080, AspectRatio.Square))
        assertEquals(1080f / 1920f, halfW, 1e-4f)
        assertEquals(1f, halfH, 1e-4f)
    }

    @Test
    fun square_on_portrait_crops_height_only() {
        // 9:16 源 → 1:1:裁高,halfW=1,halfH=srcAR/targetAR=(9/16)/1=0.5625
        val (halfW, halfH) = centerCropHalfExtents(state(1080, 1920, AspectRatio.Square))
        assertEquals(1f, halfW, 1e-4f)
        assertEquals(1080f / 1920f, halfH, 1e-4f)
    }

    @Test
    fun cropped_ratio_equals_target_and_in_bounds() {
        // 对每个源 × 每个目标比例:裁后像素比 == 目标比例,且半宽/半高 ∈ (0,1]。
        val sources = listOf(1920 to 1080, 1080 to 1920, 1280 to 1280, 640 to 480)
        for ((w, h) in sources) {
            for (aspect in AspectRatio.values()) {
                val st = state(w, h, aspect)
                val (halfW, halfH) = centerCropHalfExtents(st)
                assertTrue("halfW 越界 $halfW", halfW > 0f && halfW <= 1f + 1e-4f)
                assertTrue("halfH 越界 $halfH", halfH > 0f && halfH <= 1f + 1e-4f)
                // 至少一轴贴满(中心裁剪不会两轴同时内缩)。
                assertTrue("无轴贴满: $halfW,$halfH", halfW >= 1f - 1e-4f || halfH >= 1f - 1e-4f)

                val targetAR = aspect.ratio ?: st.sourceAspectRatio
                val croppedAR = (halfW * w) / (halfH * h)
                assertEquals("裁后比例 != 目标 ($w×$h, ${aspect.label})", targetAR, croppedAR, 1e-3f)
            }
        }
    }
}
