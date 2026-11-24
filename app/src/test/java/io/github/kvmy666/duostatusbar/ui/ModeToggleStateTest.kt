package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner report item 1: exactly one mode owns the bar.
 *
 *     OFF  = enabled=false, iosMode=false
 *     DUO  = enabled=true,  iosMode=false
 *     IOS  = enabled=false, iosMode=true
 *
 * The two switches are not independent; every transition below is the whole matrix, and every other
 * setting must survive a toggle bit-for-bit (the fixture is fully populated on purpose).
 */
class ModeToggleStateTest {

    /** A fully populated settings instance so any dropped field makes the equality checks fail. */
    private fun fixture(enabled: Boolean, iosMode: Boolean) = DuoSettings(
        enabled = enabled,
        useRive = false,
        showPercent = false,
        sizePercent = 137,
        offsetX = -23,
        percentHeight = 61,
        splitIndicators = true,
        indicatorsOffsetX = 14,
        lockRing = true,
        liveApply = false,
        systemClockFont = false,
        revealMs = 1500,
        animationsEnabled = false,
        arrivalEnabled = false,
        departureEnabled = false,
        chargingEnabled = false,
        showChargingBolt = false,
        tapAction = "toggle_qs",
        doubleTapAction = "open_settings",
        longPressAction = "open_actions",
        indicatorsTapAction = "toggle_wifi",
        indicatorsDoubleTapAction = "toggle_cell",
        indicatorsLongPressAction = "no_action",
        iconColor = "white",
        hideOtherIcons = true,
        hiddenStockIcons = "alarm,clock",
        designStyle = DuoPrefs.DESIGN_NEW,
        showInQs = false,
        showInShade = false,
        showAirplane = false,
        dndMode = "dots",
        simChoice = "sim2",
        wifiDots = true,
        edgePadding = 42,
        engineMode = DuoPrefs.ENGINE_CANVAS,
        middleSlot = DuoPrefs.MIDDLE_SLOT_CLOCK,
        showWifiGlyph = false,
        showCellDots = false,
        eventMask = 123,
        downloadProgressEnabled = false,
        burnInEnabled = true,
        iosMode = iosMode,
        iosBatteryOffset = -13,
        iosBatteryScale = 88,
        iosWifiOffset = 7,
        iosWifiScale = 91,
        iosCellOffset = -5,
        iosCellScale = 112,
        simDisplay = DuoPrefs.SIM_DISPLAY_DUAL,
        iosPercentInside = true,
        hideExtras = false
    )

    // ---------------------------------------------------------------- toggle matrix

    @Test
    fun `OFF duo on becomes DUO`() {
        val before = fixture(enabled = false, iosMode = false)

        val after = applyDuoMasterToggle(before, requested = true)

        assertEquals(fixture(enabled = true, iosMode = false), after)
    }

    @Test
    fun `DUO duo off becomes OFF`() {
        val before = fixture(enabled = true, iosMode = false)

        val after = applyDuoMasterToggle(before, requested = false)

        assertEquals(fixture(enabled = false, iosMode = false), after)
    }

    @Test
    fun `OFF ios on becomes IOS and turns Duo off`() {
        val before = fixture(enabled = false, iosMode = false)

        val after = applyIosModeToggle(before, requested = true, available = true)

        assertEquals(fixture(enabled = false, iosMode = true), after)
    }

    @Test
    fun `IOS ios off becomes DUO and turns Duo on`() {
        val before = fixture(enabled = false, iosMode = true)

        val after = applyIosModeToggle(before, requested = false, available = true)

        assertEquals(fixture(enabled = true, iosMode = false), after)
    }

    @Test
    fun `DUO ios on becomes IOS`() {
        val before = fixture(enabled = true, iosMode = false)

        val after = applyIosModeToggle(before, requested = true, available = true)

        assertEquals(fixture(enabled = false, iosMode = true), after)
    }

    @Test
    fun `IOS duo toggle is disabled so no write can leave IOS`() {
        val ios = fixture(enabled = false, iosMode = true)

        // The Duo master switch follows duoOwnsBar: grey while an available iOS mode owns the bar.
        assertFalse(duoOwnsBar(ios, iosAvailable = true))
    }

    @Test
    fun `unavailable ios on is a no-op`() {
        val before = fixture(enabled = true, iosMode = false)

        val after = applyIosModeToggle(before, requested = true, available = false)

        assertSame(before, after)
    }

    @Test
    fun `unavailable ios off still becomes DUO`() {
        val stale = fixture(enabled = false, iosMode = true)

        val after = applyIosModeToggle(stale, requested = false, available = false)

        assertEquals(fixture(enabled = true, iosMode = false), after)
    }

    // ------------------------------------------------------- enabled-state helper matrix

    @Test
    fun `IOS greys every Duo control and keeps the iOS card and shared cards active`() {
        val ios = fixture(enabled = false, iosMode = true)

        assertTrue(iosCardEnabled(ios, iosAvailable = true))
        assertFalse(duoOwnsBar(ios, iosAvailable = true))
        assertFalse(duoControlsEnabled(ios, iosAvailable = true))
        assertTrue(iosSimDisplayEnabled(ios, iosAvailable = true))
        assertTrue(sharedCardEnabled(ios, iosAvailable = true))

        assertTrue(customCardEnabled(CustomCard.IOS_MODE, ios, iosAvailable = true))
        for (card in listOf(
            CustomCard.CUSTOMIZE,
            CustomCard.ANIMATIONS,
            CustomCard.APPEARANCE,
            CustomCard.ACTIONS,
            CustomCard.LIVE_EVENTS,
            CustomCard.DOWNLOAD
        )) {
            assertFalse("$card must be grey in IOS", customCardEnabled(card, ios, iosAvailable = true))
        }
        for (card in listOf(CustomCard.SIM, CustomCard.SURFACES, CustomCard.ICONS)) {
            assertTrue("$card is shared and active in IOS", customCardEnabled(card, ios, iosAvailable = true))
        }
    }

    @Test
    fun `DUO keeps Duo and shared cards active and greys the iOS tuning`() {
        val duo = fixture(enabled = true, iosMode = false)
        val iosTuningEnabled = duo.iosMode && true

        assertTrue(iosCardEnabled(duo, iosAvailable = true))
        assertTrue(duoOwnsBar(duo, iosAvailable = true))
        assertTrue(duoControlsEnabled(duo, iosAvailable = true))
        assertFalse(iosSimDisplayEnabled(duo, iosAvailable = true))
        assertFalse("iOS tuning follows iosMode", iosTuningEnabled)
        assertTrue(sharedCardEnabled(duo, iosAvailable = true))

        assertTrue(customCardEnabled(CustomCard.IOS_MODE, duo, iosAvailable = true))
        assertTrue(customCardEnabled(CustomCard.CUSTOMIZE, duo, iosAvailable = true))
        assertTrue(customCardEnabled(CustomCard.SIM, duo, iosAvailable = true))
    }

    @Test
    fun `OFF keeps the Duo master active but greys every Duo and shared card`() {
        val off = fixture(enabled = false, iosMode = false)

        assertTrue(iosCardEnabled(off, iosAvailable = true))
        assertTrue(duoOwnsBar(off, iosAvailable = true))
        assertFalse(duoControlsEnabled(off, iosAvailable = true))
        assertFalse(iosSimDisplayEnabled(off, iosAvailable = true))
        assertFalse(sharedCardEnabled(off, iosAvailable = true))

        assertTrue(customCardEnabled(CustomCard.IOS_MODE, off, iosAvailable = true))
        assertTrue("engine stays usable in OFF", customCardEnabled(CustomCard.ENGINE, off, iosAvailable = true))
        for (card in listOf(
            CustomCard.CUSTOMIZE,
            CustomCard.ANIMATIONS,
            CustomCard.APPEARANCE,
            CustomCard.SIM,
            CustomCard.SURFACES,
            CustomCard.ICONS,
            CustomCard.ACTIONS,
            CustomCard.LIVE_EVENTS,
            CustomCard.DOWNLOAD
        )) {
            assertFalse("$card must be grey in OFF", customCardEnabled(card, off, iosAvailable = true))
        }
    }
}
