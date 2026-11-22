package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3A A6: one row per [DuoFeature].
 *
 * A feature with a dynamic registration must add exactly that registration when its setting is on and
 * remove it when the setting is off. The remaining features are render-only: their gate is the pure
 * [FeatureSet.has] predicate (plus the owner gate), so they cannot leave a process-wide hook behind.
 * That is deliberate, not an omission, and is what the second assertion pair documents.
 */
class FeatureRegistrationMatrixTest {

    /** Every optional field off, with Duo owning the bar, so one feature can be isolated. */
    private val base = ModuleSettings.DEFAULT.copy(
        enabled = true,
        showPercent = false,
        splitIndicators = false,
        lockRing = false,
        systemClockFont = false,
        animationsEnabled = false,
        arrivalEnabled = false,
        departureEnabled = false,
        chargingEnabled = false,
        showChargingBolt = false,
        tapAction = "no_action",
        doubleTapAction = "no_action",
        longPressAction = "no_action",
        indicatorsTapAction = "no_action",
        indicatorsDoubleTapAction = "no_action",
        indicatorsLongPressAction = "no_action",
        showInQs = false,
        showInShade = false,
        hideOtherIcons = false,
        hiddenStockIcons = "",
        wifiDots = false,
        showWifiGlyph = false,
        showCellDots = false,
        showAirplane = false,
        dndMode = DuoPrefs.DND_OFF,
        downloadProgressEnabled = false,
        burnInEnabled = false,
        eventMask = 0
    )

    private fun set(settings: ModuleSettings): FeatureSet =
        featureSetFor(settings, adbOverride = null, iosDrawingReady = true, otherOrientationEnabled = false)

    /** The settings with only [feature] turned on. `RING` has no switch and is always on. */
    private fun on(feature: DuoFeature): ModuleSettings = when (feature) {
        DuoFeature.RING -> base
        DuoFeature.PERCENT -> base.copy(showPercent = true)
        DuoFeature.SPLIT -> base.copy(splitIndicators = true)
        DuoFeature.LOCK_RING -> base.copy(lockRing = true)
        DuoFeature.CLOCK_FONT -> base.copy(systemClockFont = true)
        DuoFeature.ANIMATIONS -> base.copy(animationsEnabled = true)
        DuoFeature.ARRIVAL -> base.copy(arrivalEnabled = true)
        DuoFeature.DEPARTURE -> base.copy(departureEnabled = true)
        DuoFeature.CHARGING_MOTION -> base.copy(chargingEnabled = true)
        DuoFeature.CHARGING_BOLT -> base.copy(showChargingBolt = true)
        DuoFeature.RING_GESTURES -> base.copy(tapAction = "flashlight")
        DuoFeature.INDICATOR_GESTURES -> base.copy(indicatorsTapAction = "flashlight")
        DuoFeature.QS_SURFACE -> base.copy(showInQs = true)
        DuoFeature.SHADE_SURFACE -> base.copy(showInShade = true)
        DuoFeature.HIDE_OTHER -> base.copy(hideOtherIcons = true)
        DuoFeature.HIDDEN_KEYS -> base.copy(hiddenStockIcons = "alarm")
        DuoFeature.WIFI_DOTS -> base.copy(wifiDots = true)
        DuoFeature.WIFI_GLYPH -> base.copy(showWifiGlyph = true)
        DuoFeature.CELL_DOTS -> base.copy(showCellDots = true)
        DuoFeature.AIRPLANE -> base.copy(showAirplane = true)
        DuoFeature.DND -> base.copy(dndMode = DuoPrefs.DND_MIDDLE)
        DuoFeature.DOWNLOAD -> base.copy(downloadProgressEnabled = true)
        DuoFeature.BURN_IN -> base.copy(burnInEnabled = true)
        DuoFeature.EVENT_ALARM -> base.copy(eventMask = DuoPrefs.EVENT_ALARM)
        DuoFeature.EVENT_FLASHLIGHT -> base.copy(eventMask = DuoPrefs.EVENT_FLASHLIGHT)
        DuoFeature.EVENT_SCREENSHOT -> base.copy(eventMask = DuoPrefs.EVENT_SCREENSHOT)
        DuoFeature.EVENT_UNLOCK -> base.copy(eventMask = DuoPrefs.EVENT_UNLOCK)
        DuoFeature.EVENT_CALL -> base.copy(eventMask = DuoPrefs.EVENT_CALL)
    }

    /** The one dynamic registration a feature owns, or null when it only changes the drawing. */
    private fun dynamicFor(feature: DuoFeature): DuoRegistration? = when (feature) {
        DuoFeature.EVENT_FLASHLIGHT -> DuoRegistration.FLASHLIGHT
        DuoFeature.EVENT_SCREENSHOT -> DuoRegistration.SCREENSHOT
        DuoFeature.EVENT_UNLOCK -> DuoRegistration.UNLOCK
        DuoFeature.RING_GESTURES,
        DuoFeature.INDICATOR_GESTURES,
        DuoFeature.DOWNLOAD -> DuoRegistration.STATUS_TOUCH
        else -> null
    }

    @Test
    fun `every feature follows its own setting`() {
        for (feature in DuoFeature.entries) {
            assertFalse(
                "${feature.tag} must be absent from the isolated base",
                feature in set(base).duo && feature != DuoFeature.RING
            )
            assertTrue(
                "${feature.tag} must be active when its own setting is on",
                feature in set(on(feature)).duo
            )
        }
    }

    @Test
    fun `dynamic features add their registration and render-only features do not`() {
        val offRegistrations = duoRegistrationsFor(set(base))
        for (feature in DuoFeature.entries) {
            val dynamic = dynamicFor(feature)
            val onRegistrations = duoRegistrationsFor(set(on(feature)))
            if (dynamic != null) {
                assertTrue("${feature.tag} must register $dynamic", dynamic in onRegistrations)
                assertFalse(
                    "${feature.tag} must not register $dynamic while it is off",
                    dynamic in offRegistrations
                )
            } else {
                // Render-only / predicate-gated feature: it owns no process-wide registration, so
                // turning it on may not change the hook set at all.
                assertEquals(
                    "${feature.tag} must not add a dynamic registration",
                    offRegistrations,
                    onRegistrations
                )
            }
        }
    }

    @Test
    fun `turning a dynamic feature off live uninstalls its registration exactly once`() {
        for (feature in DuoFeature.entries) {
            val dynamic = dynamicFor(feature) ?: continue
            val uninstalls = HashMap<DuoRegistration, Int>()
            val registrations = RegistrationSet<DuoRegistration, DuoRegistration>(
                installer = { it },
                uninstaller = { uninstalls[it] = (uninstalls[it] ?: 0) + 1 }
            )
            registrations.apply(duoRegistrationsFor(set(on(feature))))
            assertTrue(dynamic in registrations.installed())

            registrations.apply(duoRegistrationsFor(set(base)))
            assertFalse(dynamic in registrations.installed())
            assertEquals("${feature.tag} must unhook $dynamic once", 1, uninstalls[dynamic])

            // A repeated apply of the same off state must not unhook it again.
            registrations.apply(duoRegistrationsFor(set(base)))
            assertEquals(1, uninstalls[dynamic])
        }
    }

    @Test
    fun `owner-level registrations exist for every live Duo bar`() {
        val registrations = duoRegistrationsFor(set(base))
        assertTrue(DuoRegistration.PANEL_EXPANSION in registrations)
        assertTrue(DuoRegistration.STATUS_LAYOUT in registrations)
        assertTrue(duoRegistrationsFor(set(base.copy(enabled = false))).isEmpty())
    }

    /**
     * R3 A4/A1/A6: the panel hook is shared, so an iOS bar carries it (and only it); NONE carries
     * nothing and Duo is unchanged.
     */
    @Test
    fun `the panel expansion trigger is shared but every other registration is Duo-only`() {
        val ios = set(base.copy(iosMode = true))
        assertEquals(BarOwner.IOS, ios.owner)
        assertEquals(setOf(DuoRegistration.PANEL_EXPANSION), duoRegistrationsFor(ios))

        val none = set(base.copy(enabled = false))
        assertEquals(BarOwner.NONE, none.owner)
        assertTrue(duoRegistrationsFor(none).isEmpty())

        val duo = duoRegistrationsFor(set(base))
        assertTrue(DuoRegistration.PANEL_EXPANSION in duo)
        assertTrue(DuoRegistration.STATUS_LAYOUT in duo)
        for (duoOnly in listOf(
            DuoRegistration.FLASHLIGHT,
            DuoRegistration.SCREENSHOT,
            DuoRegistration.UNLOCK,
            DuoRegistration.STATUS_TOUCH
        )) {
            assertFalse("$duoOnly is Duo-only", duoOnly in duoRegistrationsFor(ios))
        }
    }

    @Test
    fun `the shared shade layout listener is kept for both owners and dropped for NONE`() {
        assertTrue("Duo needs the shade listener", keepShadeLayoutListener(set(base)))
        assertTrue("iOS needs the shade listener", keepShadeLayoutListener(set(base.copy(iosMode = true))))
        assertFalse("NONE must detach it", keepShadeLayoutListener(set(base.copy(enabled = false))))
    }

    @Test
    fun `panel transition work follows the owner`() {
        val duo = set(base)
        val ios = set(base.copy(iosMode = true))
        val none = set(base.copy(enabled = false))

        assertEquals(setOf(PanelTransitionAction.DUO_PANEL_STATE), panelTransitionActions(duo))
        assertEquals(setOf(PanelTransitionAction.IOS_REFRESH), panelTransitionActions(ios))
        assertTrue(
            "the iOS panel branch may not run Duo state work",
            PanelTransitionAction.DUO_PANEL_STATE !in panelTransitionActions(ios)
        )
        assertTrue(
            "the Duo panel branch may not run iOS refresh work",
            PanelTransitionAction.IOS_REFRESH !in panelTransitionActions(duo)
        )
        assertTrue(panelTransitionActions(none).isEmpty())
    }
}
