package com.homeflix.tv.ui

import com.homeflix.tv.presentation.ui.coverScale
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverScaleTest {
    /** Size the picture is drawn at, given the TextureView's default stretch. */
    private fun drawn(viewW: Float, viewH: Float, aspect: Float): Pair<Float, Float> {
        val (sx, sy) = coverScale(viewW, viewH, aspect, zoom = 1f)
        return viewW * sx to viewH * sy
    }

    @Test
    fun keepsAspect_andCoversTheView() {
        // 16:9 trailer in a wide billboard (960x408 dp): fills width, crops top/bottom.
        val (w, h) = drawn(960f, 408f, 16f / 9f)
        assertEquals(16f / 9f, w / h, 0.001f)
        assertEquals(960f, w, 0.01f)
        assertEquals(true, h >= 408f)

        // 2.39:1 scope clip in a 16:9 tile: fills height, crops the sides.
        val (w2, h2) = drawn(356f, 200f, 2.39f)
        assertEquals(2.39f, w2 / h2, 0.001f)
        assertEquals(200f, h2, 0.01f)
        assertEquals(true, w2 >= 356f)

        // Exact match: untouched.
        val (w3, h3) = drawn(1600f, 900f, 16f / 9f)
        assertEquals(1600f, w3, 0.01f); assertEquals(900f, h3, 0.01f)
    }

    @Test
    fun slightZoom_isUniform() {
        val (sx, sy) = coverScale(1600f, 900f, 16f / 9f) // default 2% zoom
        assertEquals(1.02f, sx, 0.0001f); assertEquals(1.02f, sy, 0.0001f)
    }
}
