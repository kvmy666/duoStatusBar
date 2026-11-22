package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure feature model behind the master switches (R3A A6). Every optional feature is resolved from
 * the settings snapshot without a Context, so the runtime can register hooks/receivers only for what is
 * actually on.
 */
class FeatureSetTest {

    private val defaults = ModuleSettings.DEFAULT
    private val duoOn = defaults.copy(enabled = true)

    private fun set(
        settings: ModuleSettings?,
        override: Int? = null,
        iosReady: Boolean = true,
        otherOrientation: Boolean = false
    ): FeatureSet = featureSetFor(settings, override, iosReady, otherOrientation)

    private fun active(settings: ModuleSettings): Set<DuoFeature> = set(settings).duo

    /** A setting that only switches [feature], used to prove "on -> present, off -> absent". */
    private data class Gate(
        val feature: DuoFeature,
        val on: ModuleSettings,
        val off: ModuleSettings
    )

    private fun gates(): List<Gate> = listOf(
        Gate(
            DuoFeature.PERCENT,
            duoOn.copy(showPercent = true),
            duoOn.copy(showPercent = false)
        ),
        Gate(
            DuoFeature.SPLIT,
            duoOn.copy(splitIndicators = true),
            duoOn.copy(splitIndicators = false)
        ),
        Gate(
            DuoFeature.LOCK_RING,
            duoOn.copy(lockRing = true),
            duoOn.copy(lockRing = false)
        ),
        Gate(
            DuoFeature.CLOCK_FONT,
            duoOn.copy(systemClockFont = true),
            duoOn.copy(systemClockFont = false)
        ),
        Gate(
            DuoFeature.ANIMATIONS,
            duoOn.copy(animationsEnabled = true),
            duoOn.copy(animationsEnabled = false)
        ),
        Gate(
            DuoFeature.ARRIVAL,
            duoOn.copy(arrivalEnabled = true),
            duoOn.copy(arrivalEnabled = false)
        ),
        Gate(
            DuoFeature.DEPARTURE,
            duoOn.copy(departureEnabled = true),
            duoOn.copy(departureEnabled = false)
        ),
        Gate(
            DuoFeature.CHARGING_MOTION,
            duoOn.copy(chargingEnabled = true),
            duoOn.copy(chargingEnabled = false)
        ),
        Gate(
            DuoFeature.CHARGING_BOLT,
            duoOn.copy(showChargingBolt = true),
            duoOn.copy(showChargingBolt = false)
        ),
        Gate(
            DuoFeature.RING_GESTURES,
            duoOn.copy(tapAction = "flashlight"),
            duoOn.copy(tapAction = "no_action")
        ),
        Gate(
            DuoFeature.RING_GESTURES,
            duoOn.copy(longPressAction = "screenshot"),
            duoOn.copy(longPressAction = "no_action")
        ),
        Gate(
            DuoFeature.INDICATOR_GESTURES,
            duoOn.copy(indicatorsTapAction = "flashlight"),
            duoOn.copy(indicatorsTapAction = "no_action")
        ),
        Gate(
            DuoFeature.INDICATOR_GESTURES,
            duoOn.copy(indicatorsLongPressAction = "screenshot"),
            duoOn.copy(indicatorsLongPressAction = "no_action")
        ),
        Gate(
            DuoFeature.QS_SURFACE,
            duoOn.copy(showInQs = true),
            duoOn.copy(showInQs = false)
        ),
        Gate(
            DuoFeature.SHADE_SURFACE,
            duoOn.copy(showInShade = true),
            duoOn.copy(showInShade = false)
        ),
        Gate(
            DuoFeature.HIDE_OTHER,
            duoOn.copy(hideOtherIcons = true),
            duoOn.copy(hideOtherIcons = false)
        ),
        Gate(
            DuoFeature.HIDDEN_KEYS,
            duoOn.copy(hiddenStockIcons = "alarm"),
            duoOn.copy(hiddenStockIcons = "")
        ),
        Gate(
            DuoFeature.WIFI_DOTS,
            duoOn.copy(wifiDots = true),
            duoOn.copy(wifiDots = false)
        ),
        Gate(
            DuoFeature.WIFI_GLYPH,
            duoOn.copy(showWifiGlyph = true),
            duoOn.copy(showWifiGlyph = false)
        ),
        Gate(
            DuoFeature.CELL_DOTS,
            duoOn.copy(showCellDots = true),
            duoOn.copy(showCellDots = false)
        ),
        Gate(
            DuoFeature.AIRPLANE,
            duoOn.copy(showAirplane = true),
            duoOn.copy(showAirplane = false)
        ),
        Gate(
            DuoFeature.DND,
            duoOn.copy(dndMode = DuoPrefs.DND_MIDDLE),
            duoOn.copy(dndMode = DuoPrefs.DND_OFF)
        ),
        Gate(
            DuoFeature.DND,
            duoOn.copy(dndMode = DuoPrefs.DND_DOTS),
            duoOn.copy(dndMode = DuoPrefs.DND_OFF)
        ),
        Gate(
            DuoFeature.DOWNLOAD,
            duoOn.copy(downloadProgressEnabled = true),
            duoOn.copy(downloadProgressEnabled = false)
        ),
        Gate(
            DuoFeature.BURN_IN,
            duoOn.copy(burnInEnabled = true),
            duoOn.copy(burnInEnabled = false)
        ),
        eventGate(DuoFeature.EVENT_ALARM, DuoPrefs.EVENT_ALARM),
        eventGate(DuoFeature.EVENT_FLASHLIGHT, DuoPrefs.EVENT_FLASHLIGHT),
        eventGate(DuoFeature.EVENT_SCREENSHOT, DuoPrefs.EVENT_SCREENSHOT),
        eventGate(DuoFeature.EVENT_UNLOCK, DuoPrefs.EVENT_UNLOCK),
        eventGate(DuoFeature.EVENT_CALL, DuoPrefs.EVENT_CALL)
    )

    private fun eventGate(feature: DuoFeature, bit: Int): Gate = Gate(
        feature,
        duoOn.copy(eventMask = DuoPrefs.EVENT_ALL),
        duoOn.copy(eventMask = DuoPrefs.EVENT_ALL and bit.inv())
    )

    @Test
    fun `every DuoFeature is covered by a setting gate`() {
        val covered = gates().map { it.feature }.toSet() + DuoFeature.RING
        assertEquals(DuoFeature.entries.toSet(), covered)

        for (gate in gates()) {
            assertTrue("${gate.feature} should be active when its setting is on", gate.feature in active(gate.on))
            assertFalse("${gate.feature} should be absent when its setting is off", gate.feature in active(gate.off))
        }
    }

    @Test
    fun `the ring is always part of the Duo drawing because it has no switch`() {
        assertTrue(DuoFeature.RING in active(duoOn))
        assertTrue(DuoFeature.RING in active(duoOn.copy(showPercent = false, showInQs = false)))
    }

    @Test
    fun `the Duo master switch drives the whole set`() {
        val off = set(defaults)
        assertEquals(BarOwner.NONE, off.owner)
        assertTrue(off.duo.isEmpty())
        assertFalse(off.duoRuntime)

        val on = set(duoOn)
        assertEquals(BarOwner.DUO, on.owner)
        assertTrue(on.duoRuntime)
        assertTrue(on.duo.isNotEmpty())
    }

    @Test
    fun `iOS mode suppresses every Duo feature`() {
        val ios = set(allOn().copy(iosMode = true))
        assertEquals(BarOwner.IOS, ios.owner)
        assertTrue(ios.iosRuntime)
        assertTrue("no Duo feature may run while iOS owns the bar", ios.duo.isEmpty())
        assertEquals(DuoRenderer.NONE, ios.renderer)

        // The same holds for each individual gate: turning one Duo setting on cannot leak into iOS.
        for (gate in gates()) {
            assertEquals(BarOwner.IOS, set(gate.on.copy(iosMode = true)).owner)
            assertTrue(set(gate.on.copy(iosMode = true)).duo.isEmpty())
        }
    }

    @Test
    fun `an iOS mode without the drawing stays on Duo`() {
        val duo = set(allOn().copy(iosMode = true), iosReady = false)
        assertEquals(BarOwner.DUO, duo.owner)
        assertFalse(duo.iosRuntime)
        assertEquals(DuoFeature.entries.toSet(), duo.duo)
    }

    @Test
    fun `an adb override suppresses iOS and there is no iOS feature set`() {
        for (override in listOf(DuoGuard.ICONS_ONLY, DuoGuard.RIVE)) {
            val forced = set(allOn().copy(iosMode = true), override = override)
            assertEquals(BarOwner.DUO, forced.owner)
            assertFalse(forced.iosRuntime)
            assertEquals(DuoFeature.entries.toSet(), forced.duo)
        }
    }

    @Test
    fun `override zero is none with shared registrations only`() {
        val killed = set(allOn(), override = DuoGuard.OFF)
        assertEquals(BarOwner.NONE, killed.owner)
        assertEquals(DuoRenderer.NONE, killed.renderer)
        assertTrue(killed.duo.isEmpty())
        assertEquals(SharedHook.entries.toSet(), killed.shared)
    }

    @Test
    fun `override one is the Canvas Duo bar`() {
        val iconsOnly = set(allOn(), override = DuoGuard.ICONS_ONLY)
        assertEquals(BarOwner.DUO, iconsOnly.owner)
        assertEquals(DuoRenderer.CANVAS, iconsOnly.renderer)
        assertEquals(DuoFeature.entries.toSet(), iconsOnly.duo)
    }

    @Test
    fun `override two is the Rive Duo bar`() {
        val rive = set(allOn(), override = DuoGuard.RIVE)
        assertEquals(BarOwner.DUO, rive.owner)
        assertEquals(DuoRenderer.RIVE, rive.renderer)
        assertEquals(DuoFeature.entries.toSet(), rive.duo)
    }

    @Test
    fun `the default settings resolve to none`() {
        val featureSet = set(defaults)
        assertEquals(BarOwner.NONE, featureSet.owner)
        assertEquals(DuoRenderer.NONE, featureSet.renderer)
        assertTrue(featureSet.duo.isEmpty())
    }

    @Test
    fun `defaults when enabled activate exactly the default-on features`() {
        val expected = setOf(
            DuoFeature.RING,
            DuoFeature.PERCENT,
            DuoFeature.CLOCK_FONT,
            DuoFeature.ANIMATIONS,
            DuoFeature.ARRIVAL,
            DuoFeature.DEPARTURE,
            DuoFeature.CHARGING_MOTION,
            DuoFeature.CHARGING_BOLT,
            DuoFeature.QS_SURFACE,
            DuoFeature.SHADE_SURFACE,
            DuoFeature.WIFI_GLYPH,
            DuoFeature.CELL_DOTS,
            DuoFeature.AIRPLANE,
            DuoFeature.DND,
            DuoFeature.DOWNLOAD,
            DuoFeature.EVENT_ALARM,
            DuoFeature.EVENT_FLASHLIGHT,
            DuoFeature.EVENT_SCREENSHOT,
            DuoFeature.EVENT_UNLOCK,
            DuoFeature.EVENT_CALL
        )
        assertEquals(expected, active(defaults.copy(enabled = true)))
    }

    @Test
    fun `the renderer follows engine and useRive`() {
        fun renderer(engine: String, useRive: Boolean): DuoRenderer =
            set(duoOn.copy(engineMode = engine, useRive = useRive)).renderer

        assertEquals(DuoRenderer.CANVAS, renderer(DuoPrefs.ENGINE_CANVAS, useRive = true))
        assertEquals(DuoRenderer.CANVAS, renderer(DuoPrefs.ENGINE_AUTO, useRive = false))
        assertEquals(DuoRenderer.RIVE, renderer(DuoPrefs.ENGINE_AUTO, useRive = true))
        assertEquals(DuoRenderer.RIVE, renderer(DuoPrefs.ENGINE_RIVE, useRive = true))
        assertEquals(DuoRenderer.CANVAS, renderer(DuoPrefs.ENGINE_RIVE, useRive = false))
    }

    @Test
    fun `other orientation never changes the owner`() {
        for (settings in listOf(defaults, duoOn, allOn(), allOn().copy(iosMode = true))) {
            for (other in listOf(false, true)) {
                assertEquals(set(settings, otherOrientation = false).owner, set(settings, otherOrientation = other).owner)
                assertEquals(set(settings, otherOrientation = false).duo, set(settings, otherOrientation = other).duo)
            }
        }
    }

    @Test
    fun `all-on has every feature exactly once`() {
        val featureSet = set(allOn())
        assertEquals(DuoFeature.entries.toSet(), featureSet.duo)
        assertEquals(DuoFeature.entries.size, featureSet.duo.map { it.tag }.distinct().size)
        assertEquals(SharedHook.entries.toSet(), featureSet.shared)
        assertEquals(SharedHook.entries.size, featureSet.shared.map { it.tag }.distinct().size)
    }

    private fun allOn(): ModuleSettings = duoOn.copy(
        showPercent = true,
        splitIndicators = true,
        lockRing = true,
        systemClockFont = true,
        animationsEnabled = true,
        arrivalEnabled = true,
        departureEnabled = true,
        chargingEnabled = true,
        showChargingBolt = true,
        tapAction = "flashlight",
        doubleTapAction = "no_action",
        longPressAction = "no_action",
        indicatorsTapAction = "flashlight",
        indicatorsDoubleTapAction = "no_action",
        indicatorsLongPressAction = "no_action",
        showInQs = true,
        showInShade = true,
        hideOtherIcons = true,
        hiddenStockIcons = "alarm",
        wifiDots = true,
        showWifiGlyph = true,
        showCellDots = true,
        showAirplane = true,
        dndMode = DuoPrefs.DND_MIDDLE,
        downloadProgressEnabled = true,
        burnInEnabled = true,
        eventMask = DuoPrefs.EVENT_ALL
    )
}
