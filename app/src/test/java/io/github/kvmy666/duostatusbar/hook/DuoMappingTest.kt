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

    @Test
    fun `the ring takes the bar's foreground colour when no battery rule applies`() {
        // FR-15b: on a light bar the whole element is black, including the ring's default colour; the
        // charging / saver / low-battery rules still win over it.
        val black = DuoMapping.visual(
            level = 80, charging = false, saver = false, showPercent = true,
            wifiLevel = 3, cellLevel = 4, airplane = false, fgColor = DuoMapping.BLACK
        )
        assertEquals(DuoMapping.BLACK, black.fgColor)
        assertEquals(DuoMapping.BLACK, black.tint)
        val charging = DuoMapping.visual(
            level = 80, charging = true, saver = false, showPercent = true,
            wifiLevel = 3, cellLevel = 4, airplane = false, fgColor = DuoMapping.BLACK
        )
        assertEquals(DuoMapping.GREEN_CHARGING, charging.tint)
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
    fun `airplane mode blanks the cellular ramps and takes the middle slot`() {
        val v = visual(70, airplane = true)
        assertEquals(DuoMapping.MIDDLE_AIRPLANE, v.middleMode)
        // The glyphs are the state machine's job now, so the host only reports the level: the arcs stay
        // lit here and the Rive MiddleSlot layer retracts them as it morphs.
        v.let { assertEquals(listOf(0.3f, 0.3f, 0.3f, 0.3f), listOf(it.cell1Opacity, it.cell2Opacity, it.cell3Opacity, it.cell4Opacity)) }
    }

    @Test
    fun `dnd takes the middle slot and leaves the spheres lit`() {
        val v = visual(70, dnd = true)
        assertEquals(DuoMapping.MIDDLE_DND, v.middleMode)
        // Cellular is not the middle slot: the spheres stay lit while DND is on.
        assertEquals(listOf(1f, 1f, 1f, 1f), listOf(v.cell1Opacity, v.cell2Opacity, v.cell3Opacity, v.cell4Opacity))
    }

    @Test
    fun `the middle slot shows the Wi-Fi by default`() {
        assertEquals(DuoMapping.MIDDLE_WIFI, visual(70).middleMode)
    }

    @Test
    fun `the slot holds exactly one occupant, and airplane wins`() {
        // FR-06/FR-16: one number, one occupant - which is why the state machine can be a star.
        assertEquals(DuoMapping.MIDDLE_AIRPLANE, DuoMapping.middleMode(airplane = true, dnd = true))
        assertEquals(DuoMapping.MIDDLE_DND, DuoMapping.middleMode(airplane = false, dnd = true))
        assertEquals(DuoMapping.MIDDLE_WIFI, DuoMapping.middleMode(airplane = false, dnd = false))
    }

    // --------------------------------------------------------------------------- network generation

    @Test
    fun `the cellular generation maps the common radio types`() {
        assertEquals("5G", DuoMapping.networkGeneration(20))   // NR
        assertEquals("4G", DuoMapping.networkGeneration(13))   // LTE
        assertEquals("4G", DuoMapping.networkGeneration(19))   // LTE_CA
        assertEquals("3G", DuoMapping.networkGeneration(3))    // UMTS
        assertEquals("3G", DuoMapping.networkGeneration(15))   // HSPAP
        assertEquals("2G", DuoMapping.networkGeneration(1))    // GPRS
        assertEquals("2G", DuoMapping.networkGeneration(16))   // GSM
    }

    @Test
    fun `an NR connection reads as 5G even when the data type is LTE`() {
        // 5G NSA: the data network type is LTE, but the radio reports NR connected. Reading only the
        // data type is what made a 5G phone show "4G" (user-reported bug).
        assertEquals("5G", DuoMapping.networkGeneration(13, nrConnected = true))
        assertEquals("5G", DuoMapping.networkGeneration(20, nrConnected = true))
        // Not NR connected: the data type is all we have.
        assertEquals("4G", DuoMapping.networkGeneration(13, nrConnected = false))
    }

    @Test
    fun `an unknown radio type shows no label`() {
        // UNKNOWN and IWLAN (Wi-Fi calling) are not a generation worth naming.
        assertEquals("", DuoMapping.networkGeneration(0))
        assertEquals("", DuoMapping.networkGeneration(18))
    }

    @Test
    fun `with wifi off the slot shows the cellular generation`() {
        assertEquals(
            DuoMapping.MIDDLE_NETWORK,
            DuoMapping.middleMode(airplane = false, dnd = false, wifiOn = false, hasNetwork = true)
        )
        // No service either: the slot empties rather than showing a stale label.
        assertEquals(
            DuoMapping.MIDDLE_OFF,
            DuoMapping.middleMode(airplane = false, dnd = false, wifiOn = false, hasNetwork = false)
        )
    }

    @Test
    fun `wifi on but not connected shows the cellular generation`() {
        // The radio is on but there is no Wi-Fi network: the phone is on mobile data, so the slot must
        // name the generation rather than show a dead Wi-Fi glyph (user-reported bug).
        assertEquals(
            DuoMapping.MIDDLE_NETWORK,
            DuoMapping.middleMode(
                airplane = false, dnd = false, wifiOn = true, hasNetwork = true, wifiConnected = false
            )
        )
        // Connected Wi-Fi still wins the slot.
        assertEquals(
            DuoMapping.MIDDLE_WIFI,
            DuoMapping.middleMode(
                airplane = false, dnd = false, wifiOn = true, hasNetwork = true, wifiConnected = true
            )
        )
    }

    @Test
    fun `airplane and dnd beat the network label`() {
        assertEquals(
            DuoMapping.MIDDLE_AIRPLANE,
            DuoMapping.middleMode(airplane = true, dnd = false, wifiOn = false, hasNetwork = true)
        )
        assertEquals(
            DuoMapping.MIDDLE_DND,
            DuoMapping.middleMode(airplane = false, dnd = true, wifiOn = false, hasNetwork = true)
        )
    }

    @Test
    fun `the network label is only carried while the slot holds it`() {
        val shown = DuoMapping.visual(
            level = 70, charging = false, saver = false, showPercent = true,
            wifiLevel = 0, cellLevel = 4, airplane = false, wifiOn = false, networkText = "5G"
        )
        assertEquals(DuoMapping.MIDDLE_NETWORK, shown.middleMode)
        assertEquals("5G", shown.networkText)
        // Wi-Fi on: the slot holds the glyph, so the label is blank even if one was handed in.
        val hidden = DuoMapping.visual(
            level = 70, charging = false, saver = false, showPercent = true,
            wifiLevel = 3, cellLevel = 4, airplane = false, wifiOn = true, networkText = "5G"
        )
        assertEquals(DuoMapping.MIDDLE_WIFI, hidden.middleMode)
        assertEquals("", hidden.networkText)
    }

    @Test
    fun `a demo can morph between two snapshots without leaving either state`() {
        val off = visual(72, dnd = false)
        val on = visual(72, airplane = true)
        // The ends are the states themselves, not an approximation of them.
        assertEquals(off, off.lerp(on, 0f))
        assertEquals(on, off.lerp(on, 1f))
        // Half-way is actually half-way for the things that can be half-way...
        val mid = off.lerp(on, 0.5f)
        assertEquals((off.wifiOuterOpacity + on.wifiOuterOpacity) / 2f, mid.wifiOuterOpacity, 0.0001f)
        assertEquals((off.trimLeftEnd + on.trimLeftEnd) / 2f, mid.trimLeftEnd, 0.0001f)
        // ...and the things that cannot are one state or the other, never a third thing.
        assertTrue(mid.middleMode == off.middleMode || mid.middleMode == on.middleMode)
        assertTrue(mid.tint == off.tint || mid.tint == on.tint)
        assertEquals(off.percentText, off.lerp(on, 0.49f).percentText)
        assertEquals(on.percentText, off.lerp(on, 0.51f).percentText)
        // Out-of-range t is clamped rather than extrapolated.
        assertEquals(off, off.lerp(on, -1f))
        assertEquals(on, off.lerp(on, 2f))
    }

    @Test
    fun `airplane beats dnd for the middle slot`() {
        assertEquals(DuoMapping.MIDDLE_AIRPLANE, visual(70, airplane = true, dnd = true).middleMode)
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
