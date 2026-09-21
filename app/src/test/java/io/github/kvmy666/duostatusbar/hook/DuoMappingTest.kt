package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state → visual mapping, tested here rather than on a phone.
 *
 * This is the half of the element that fails *silently*: a wrong trim fraction or a wrong colour looks
 * like a working status bar until someone notices the battery is 40 % wrong. It is pure maths (no
 * Android types), so it is cheap to pin down: every expectation below is derived from the geometry in
 * `docs/DESIGN-duo.md` and the colour rules in FR-15/FR-25.
 */
class DuoMappingTest {

    private fun visual(
        level: Int,
        charging: Boolean = false,
        saver: Boolean = false,
        airplane: Boolean = false,
        dnd: Boolean = false
    ) = DuoMapping.visual(
        level = level,
        charging = charging,
        saver = saver,
        showPercent = true,
        wifiLevel = 3,
        cellLevel = 4,
        airplane = airplane,
        dnd = dnd
    )

    // ---------------------------------------------------------------------------- ring geometry

    // The percent-on case the reference screenshots show: the gap is open, so there are two halves.
    private val half = DuoMapping.halfArc(charging = false)

    @Test
    fun `ring is empty at zero percent`() {
        assertEquals(0f, DuoMapping.trimLeft(0, charging = false, closed = false), 0.0001f)
        assertEquals(0f, DuoMapping.trimRight(0, charging = false, closed = false), 0.0001f)
    }

    @Test
    fun `left half fills completely at fifty percent`() {
        assertEquals(half, DuoMapping.trimLeft(50, charging = false, closed = false), 0.0001f)
        // The right half only starts moving above 50 %.
        assertEquals(0f, DuoMapping.trimRight(50, charging = false, closed = false), 0.0001f)
    }

    @Test
    fun `ring is full at one hundred percent`() {
        assertEquals(half, DuoMapping.trimLeft(100, charging = false, closed = false), 0.0001f)
        assertEquals(half, DuoMapping.trimRight(100, charging = false, closed = false), 0.0001f)
    }

    @Test
    fun `halfway through the right half is halfway along its arc`() {
        assertEquals(half / 2f, DuoMapping.trimRight(75, charging = false, closed = false), 0.0001f)
    }

    @Test
    fun `out of range levels are clamped, not extrapolated`() {
        assertEquals(0f, DuoMapping.trimLeft(-20, charging = false, closed = false), 0.0001f)
        assertEquals(half, DuoMapping.trimLeft(500, charging = false, closed = false), 0.0001f)
        assertEquals(half, DuoMapping.trimRight(500, charging = false, closed = false), 0.0001f)
    }

    // ---------------------------------------------------------------------------------- top gap

    @Test
    fun `closing the gap gives the left half the whole ring and empties the right`() {
        assertTrue(DuoMapping.gapClosed(charging = false, showPercent = false))
        // No pair of windows meeting at 12 o'clock, so no two round caps overlap there: the left
        // half covers the whole drawn arc on its own and the right half is zero. Two windows that
        // met would put the left body over the right body (user-reported bug).
        assertEquals(DuoMapping.DRAWN_ARC, DuoMapping.leftArc(charging = false, showPercent = false), 0.0001f)
        assertEquals(0f, DuoMapping.rightArc(charging = false, showPercent = false), 0.0001f)
        assertEquals(DuoMapping.DRAWN_ARC / 2f, DuoMapping.trimLeft(50, charging = false, closed = true), 0.0001f)
        assertEquals(0f, DuoMapping.trimRight(50, charging = false, closed = true), 0.0001f)
        assertEquals(DuoMapping.DRAWN_ARC, DuoMapping.trimLeft(100, charging = false, closed = true), 0.0001f)
    }

    @Test
    fun `the digits keep the gap open and the bolt narrows it`() {
        assertEquals(half, DuoMapping.leftArc(charging = false, showPercent = true), 0.0001f)
        assertEquals(half, DuoMapping.rightArc(charging = false, showPercent = true), 0.0001f)
        assertFalse(DuoMapping.gapClosed(charging = true, showPercent = true))
        // A narrower gap means each half is longer.
        assertTrue(DuoMapping.halfArc(charging = true) > half)
        assertEquals(
            half + (DuoMapping.GAP_PERCENT_DEG - DuoMapping.GAP_CHARGING_DEG) / 720f,
            DuoMapping.leftArc(charging = true, showPercent = true),
            0.0001f
        )
    }

    // ------------------------------------------------------------------------------- battery tint

    @Test
    fun `charging is green and beats every other rule`() {
        assertEquals(DuoMapping.GREEN_CHARGING, DuoMapping.tint(5, charging = true, saver = true))
        assertEquals(DuoMapping.GREEN_CHARGING, DuoMapping.tint(100, charging = true, saver = false))
    }

    @Test
    fun `saver is yellow`() {
        assertEquals(DuoMapping.YELLOW_SAVER, DuoMapping.tint(80, charging = false, saver = true))
    }

    @Test
    fun `below twenty percent is red`() {
        assertEquals(DuoMapping.RED_CRITICAL, DuoMapping.tint(19, charging = false, saver = false))
        assertEquals(DuoMapping.WHITE, DuoMapping.tint(20, charging = false, saver = false))
    }

    // ------------------------------------------------------------------------------------ wifi

    @Test
    fun `wifi layers light up as the signal improves`() {
        assertEquals(Triple(0.3f, 0.3f, 0.3f), DuoMapping.wifiOpacities(0))
        assertEquals(Triple(0.3f, 0.3f, 1f), DuoMapping.wifiOpacities(1))
        assertEquals(Triple(0.3f, 1f, 1f), DuoMapping.wifiOpacities(2))
        assertEquals(Triple(1f, 1f, 1f), DuoMapping.wifiOpacities(3))
    }

    @Test
    fun `wifi never exceeds three layers`() {
        assertEquals(Triple(1f, 1f, 1f), DuoMapping.wifiOpacities(99))
    }

    // -------------------------------------------------------------------------------- cellular

    @Test
    fun `cellular shows one sphere per bar`() {
        assertEquals(listOf(1f, 0.3f, 0.3f, 0.3f), DuoMapping.cellOpacities(1))
        assertEquals(listOf(1f, 1f, 1f, 1f), DuoMapping.cellOpacities(4))
        assertEquals(listOf(0.3f, 0.3f, 0.3f, 0.3f), DuoMapping.cellOpacities(0))
    }

    // ------------------------------------------------------------------------------ the snapshot

    @Test
    fun `charging hides the percentage and shows the bolt`() {
        val v = visual(42, charging = true)
        assertEquals("", v.percentText.trim())
        assertEquals(0f, v.percentOpacity, 0.0001f)
        assertEquals(1f, v.boltOpacity, 0.0001f)
    }

    @Test
    fun `a normal reading shows the number and no bolt`() {
        val v = visual(42)
        assertEquals("42", v.percentText)
        assertEquals(1f, v.percentOpacity, 0.0001f)
        assertEquals(0f, v.boltOpacity, 0.0001f)
    }

    @Test
    fun `three digits shrink so they still fit the gap`() {
        assertEquals(26f, DuoMapping.percentFontSize("100"), 0.0001f)
        assertEquals(32f, DuoMapping.percentFontSize("42"), 0.0001f)
        assertEquals(26f, visual(100).percentFontSize, 0.0001f)
    }

    @Test
    fun `airplane mode blanks both radios and raises the glyph`() {
        val v = visual(70, airplane = true)
        assertEquals(1f, v.airplaneOpacity, 0.0001f)
        assertEquals(0f, v.wifiOuterOpacity, 0.0001f)
        assertEquals(0f, v.wifiMidOpacity, 0.0001f)
        assertEquals(0f, v.wifiDotOpacity, 0.0001f)
        assertTrue(v.airplaneState)
        v.let { assertEquals(listOf(0.3f, 0.3f, 0.3f, 0.3f), listOf(it.cell1Opacity, it.cell2Opacity, it.cell3Opacity, it.cell4Opacity)) }
    }

    @Test
    fun `dnd shows the moon and clears the middle slot`() {
        val v = visual(70, dnd = true)
        assertEquals(1f, v.dndOpacity, 0.0001f)
        assertEquals(0f, v.wifiOuterOpacity, 0.0001f)
        assertEquals(0f, v.wifiMidOpacity, 0.0001f)
        assertEquals(0f, v.wifiDotOpacity, 0.0001f)
        // Cellular is not the middle slot: the spheres stay lit while DND is on.
        assertEquals(listOf(1f, 1f, 1f, 1f), listOf(v.cell1Opacity, v.cell2Opacity, v.cell3Opacity, v.cell4Opacity))
    }

    @Test
    fun `dnd is off by default`() {
        assertEquals(0f, visual(70).dndOpacity, 0.0001f)
    }

    @Test
    fun `airplane beats dnd for the middle slot`() {
        val v = visual(70, airplane = true, dnd = true)
        assertEquals(1f, v.airplaneOpacity, 0.0001f)
        assertEquals(0f, v.dndOpacity, 0.0001f)
    }

    @Test
    fun `every field of the snapshot is populated for a full battery`() {
        val v = visual(100)
        // The trim ends are arc lengths now, so a full battery fills both halves completely.
        assertEquals(DuoMapping.halfArc(charging = false), v.trimLeftEnd, 0.0001f)
        assertEquals(DuoMapping.halfArc(charging = false), v.trimRightEnd, 0.0001f)
        assertEquals(DuoMapping.halfArc(charging = false), v.leftArc, 0.0001f)
        assertEquals(DuoMapping.halfArc(charging = false), v.rightArc, 0.0001f)
        assertTrue(v.trackOpacity > 0f)
        assertEquals(DuoMapping.WHITE, v.fgColor)
    }
}
