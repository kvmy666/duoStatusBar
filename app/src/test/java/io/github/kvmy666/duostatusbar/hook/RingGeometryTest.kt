package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The raised-percentage geometry must stay behaviour-preserving at its default (FR-16, PR #11).
 *
 * The element view became taller than it is wide so the digits can clear a punch-hole, but the default
 * seat has to land in exactly the same place as the old square view, or every existing user's
 * percentage moves on update. These are the numbers that keep that promise, pinned off the phone.
 */
class RingGeometryTest {

    @Test
    fun `default percentage seat is the original one`() {
        // Pre-PR: the group node sat at y 61.5 and the text top at -60, so the seat was 1.5.
        // Post-PR the node is at y 77.5; the default percentHeight (100) must land at the same 1.5.
        assertEquals(
            61.5f + RingGeometry.PERCENT_TOP_Y,
            77.5f + RingGeometry.percentTopY(100),
            0.001f
        )
    }

    @Test
    fun `height zero is the code's original relative seat and 100 lifts by one lift`() {
        assertEquals(RingGeometry.PERCENT_TOP_Y, RingGeometry.percentTopY(0), 0.001f)
        assertEquals(
            RingGeometry.PERCENT_TOP_Y - RingGeometry.PERCENT_LIFT,
            RingGeometry.percentTopY(100),
            0.001f
        )
    }

    @Test
    fun `the artboard only grew by the lift`() {
        assertEquals(
            RingGeometry.ARTBOARD_SIZE + RingGeometry.PERCENT_LIFT,
            RingGeometry.ARTBOARD_HEIGHT,
            0.001f
        )
    }

    @Test
    fun `element height keeps the ring's width and adds the lift`() {
        assertEquals(136, RingGeometry.elementHeightPx(120))
        assertTrue(RingGeometry.elementHeightPx(64) > 64)
        assertEquals(1, RingGeometry.elementHeightPx(0))
    }

    @Test
    fun `the anchor shift is negative and uniform, so the ring stays put`() {
        assertEquals(-RingGeometry.PERCENT_LIFT / 2f, RingGeometry.ringAnchorShiftY(120), 0.001f)
        assertEquals(
            2f * RingGeometry.ringAnchorShiftY(120),
            RingGeometry.ringAnchorShiftY(240),
            0.001f
        )
    }

    @Test
    fun `out-of-range percentage heights are clamped, not trusted`() {
        assertEquals(RingGeometry.percentTopY(0), RingGeometry.percentTopY(-50), 0.001f)
        assertEquals(RingGeometry.percentTopY(100), RingGeometry.percentTopY(9_999), 0.001f)
    }

    @Test
    fun `battery line start at the key levels`() {
        assertEquals(RingGeometry.BATTERY_LINE_END, RingGeometry.batteryLineStart(0), 0.0001f)
        assertEquals(
            RingGeometry.BATTERY_LINE_END -
                (RingGeometry.BATTERY_LINE_END - RingGeometry.BATTERY_LINE_START) * 19 / 100f,
            RingGeometry.batteryLineStart(19), 0.0001f
        )
        assertEquals(
            RingGeometry.BATTERY_LINE_END -
                (RingGeometry.BATTERY_LINE_END - RingGeometry.BATTERY_LINE_START) * 49 / 100f,
            RingGeometry.batteryLineStart(49), 0.0001f
        )
        assertEquals(
            RingGeometry.BATTERY_LINE_END -
                (RingGeometry.BATTERY_LINE_END - RingGeometry.BATTERY_LINE_START) * 80 / 100f,
            RingGeometry.batteryLineStart(80), 0.0001f
        )
        assertEquals(RingGeometry.BATTERY_LINE_START, RingGeometry.batteryLineStart(100), 0.0001f)
    }

    @Test
    fun `battery line start is monotonic decreasing and clamped`() {
        val levels = intArrayOf(0, 19, 49, 80, 100)
        var prev = Float.POSITIVE_INFINITY
        for (l in levels) {
            val s = RingGeometry.batteryLineStart(l)
            assertTrue(s <= prev)
            prev = s
        }
        assertEquals(RingGeometry.BATTERY_LINE_END, RingGeometry.batteryLineStart(-10), 0.0001f)
        assertEquals(RingGeometry.BATTERY_LINE_END, RingGeometry.batteryLineStart(-100), 0.0001f)
        assertEquals(RingGeometry.BATTERY_LINE_START, RingGeometry.batteryLineStart(101), 0.0001f)
        assertEquals(RingGeometry.BATTERY_LINE_START, RingGeometry.batteryLineStart(1000), 0.0001f)
    }
}
