package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-09's demo timing, pinned down because a loop that never quite reaches either end would show the
 * user a state the setting does not actually produce.
 */
class DuoSettingPreviewTest {

    @Test
    fun `the demo starts and ends at the off state`() {
        assertEquals(0f, demoPhase(0f), 0.0001f)
        assertEquals(0f, demoPhase(0.999f), 0.0001f)
    }

    @Test
    fun `the demo reaches the on state and holds it`() {
        assertEquals(1f, demoPhase(0.5f), 0.0001f)
        assertEquals(1f, demoPhase(0.55f), 0.0001f)
    }

    @Test
    fun `the demo never overshoots either state`() {
        for (step in 0..200) {
            val t = step / 200f
            assertTrue("t=$t", demoPhase(t) in 0f..1f)
        }
    }

    @Test
    fun `the demo moves monotonically out and back`() {
        // Rising half: the phase must not dip back towards off on the way to on.
        var previous = -1f
        for (step in 0..50) {
            val phase = demoPhase(0.2f + 0.2f * step / 50f)
            assertTrue("phase dipped: $previous -> $phase", phase >= previous - 0.0001f)
            previous = phase
        }
    }

    @Test
    fun `locked position preview puts zero in the middle and reaches both edges`() {
        assertEquals(0f, positionPreviewOffset(0f, 360f, 120f, centered = true), 0.0001f)
        assertEquals(-120f, positionPreviewOffset(-360f, 360f, 120f, centered = true), 0.0001f)
        assertEquals(120f, positionPreviewOffset(360f, 360f, 120f, centered = true), 0.0001f)
    }

    @Test
    fun `normal position preview is right anchored at zero`() {
        // 0 is the right edge, not the centre.
        assertEquals(120f, positionPreviewOffset(0f, 360f, 120f, centered = false), 0.0001f)
        assertEquals(-120f, positionPreviewOffset(-360f, 360f, 120f, centered = false), 0.0001f)
        assertEquals(120f, positionPreviewOffset(360f, 360f, 120f, centered = false), 0.0001f)
        assertTrue(positionPreviewOffset(0f, 360f, 120f, centered = false) != 0f)
    }

    @Test
    fun `strip position preview is right anchored in normal mode`() {
        // Unlocked (normal) parts are right-anchored: 0 is the right edge, negatives move left.
        assertEquals(150f, positionPreviewOffset(0f, 673f, 150f, centered = false), 0.0001f)
        assertEquals(150f, positionPreviewOffset(673f, 673f, 150f, centered = false), 0.0001f)
        assertEquals(-150f, positionPreviewOffset(-673f, 673f, 150f, centered = false), 0.0001f)
    }

    @Test
    fun `position preview clamps at either limit`() {
        // centered mode clamps at both edges
        assertEquals(120f, positionPreviewOffset(360f, 360f, 120f, centered = true), 0.0001f)
        assertEquals(-120f, positionPreviewOffset(-360f, 360f, 120f, centered = true), 0.0001f)
        assertEquals(120f, positionPreviewOffset(999f, 360f, 120f, centered = true), 0.0001f)
        assertEquals(-120f, positionPreviewOffset(-999f, 360f, 120f, centered = true), 0.0001f)
        // normal mode: positives clamp at the right edge
        assertEquals(120f, positionPreviewOffset(360f, 360f, 120f, centered = false), 0.0001f)
        assertEquals(-120f, positionPreviewOffset(-360f, 360f, 120f, centered = false), 0.0001f)
        assertEquals(120f, positionPreviewOffset(999f, 360f, 120f, centered = false), 0.0001f)
        assertEquals(-120f, positionPreviewOffset(-999f, 360f, 120f, centered = false), 0.0001f)
    }

    @Test
    fun `position preview has a safe zero for empty and negative ranges`() {
        assertEquals(0f, positionPreviewOffset(0f, 360f, 120f, centered = true), 0.0001f)
        assertEquals(0f, positionPreviewOffset(50f, 0f, 120f, centered = true), 0.0001f)
        assertEquals(0f, positionPreviewOffset(50f, -10f, 120f, centered = true), 0.0001f)
        assertEquals(0f, positionPreviewOffset(50f, 360f, 0f, centered = true), 0.0001f)
        assertEquals(0f, positionPreviewOffset(50f, 360f, -1f, centered = true), 0.0001f)
    }

    @Test
    fun `position preview keeps the knob under the finger`() {
        val limit = 360f
        val travel = 120f
        val dragScale = limit / travel
        for (drag in listOf(-360f, -180f, -1f, 0f, 1f, 180f, 360f)) {
            assertEquals(drag, positionPreviewOffset(drag, limit, travel, centered = true) * dragScale, 0.0001f)
        }
    }

    @Test
    fun `locked position range follows the screen instead of a fixed two hundred dp cap`() {
        assertEquals(337, positionOffsetLimit(673, centered = true))
    }

    @Test
    fun `position press steps one dp and stops at either limit`() {
        assertEquals(11, stepPositionOffset(10, direction = 1, limitDp = 20))
        assertEquals(9, stepPositionOffset(10, direction = -1, limitDp = 20))
        assertEquals(20, stepPositionOffset(20, direction = 1, limitDp = 20))
        assertEquals(-20, stepPositionOffset(-20, direction = -1, limitDp = 20))
    }
}
