package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
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

    private fun visual(level: Int, charging: Boolean = false, saver: Boolean = false, airplane: Boolean = false) =
        DuoMapping.visual(
            level = level,
            charging = charging,
            saver = saver,
            showPercent = true,
            wifiLevel = 3,
            cellLevel = 4,
            airplane = airplane
        )

    // ---------------------------------------------------------------------------- ring geometry

    @Test
    fun `ring is empty at zero percent`() {
        assertEquals(DuoMapping.LEFT_START, DuoMapping.trimLeft(0), 0.0001f)
        assertEquals(DuoMapping.RIGHT_START, DuoMapping.trimRight(0), 0.0001f)
    }

    @Test
    fun `left half fills completely at fifty percent`() {
        assertEquals(DuoMapping.LEFT_FULL, DuoMapping.trimLeft(50), 0.0001f)
        // The right half only starts moving above 50 %.
        assertEquals(DuoMapping.RIGHT_START, DuoMapping.trimRight(50), 0.0001f)
    }

    @Test
    fun `ring is full at one hundred percent`() {
        assertEquals(DuoMapping.LEFT_FULL, DuoMapping.trimLeft(100), 0.0001f)
        assertEquals(DuoMapping.RIGHT_FULL, DuoMapping.trimRight(100), 0.0001f)
    }

    @Test
    fun `halfway through the right half is halfway along its arc`() {
        val half = (DuoMapping.RIGHT_START + DuoMapping.RIGHT_FULL) / 2f
        assertEquals(half, DuoMapping.trimRight(75), 0.0001f)
    }

    @Test
    fun `out of range levels are clamped, not extrapolated`() {
        assertEquals(DuoMapping.LEFT_START, DuoMapping.trimLeft(-20), 0.0001f)
        assertEquals(DuoMapping.LEFT_FULL, DuoMapping.trimLeft(500), 0.0001f)
        assertEquals(DuoMapping.RIGHT_FULL, DuoMapping.trimRight(500), 0.0001f)
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
        assertEquals(33f, DuoMapping.percentFontSize("100"), 0.0001f)
        assertEquals(42f, DuoMapping.percentFontSize("42"), 0.0001f)
        assertEquals(33f, visual(100).percentFontSize, 0.0001f)
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
    fun `every field of the snapshot is populated for a full battery`() {
        val v = visual(100)
        assertEquals(DuoMapping.LEFT_FULL, v.trimLeftEnd, 0.0001f)
        assertEquals(DuoMapping.RIGHT_FULL, v.trimRightEnd, 0.0001f)
        assertTrue(v.trackOpacity > 0f)
        assertEquals(DuoMapping.WHITE, v.fgColor)
    }
}
