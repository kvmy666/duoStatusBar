package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3A A6: the two master switches own the process. While iOS owns the bar there is no Duo host, no
 * Duo monitor and no Duo-only registration; the only hook it carries is the shared panel-expansion
 * trigger. While Duo owns it (or nothing does) no iOS host or receiver exists. The live switch is the
 * same diff applied twice.
 *
 * The runtime is modelled by [FakeRuntime] but every decision it makes comes from the production
 * [ownerActions]/[duoRegistrationsFor]/[panelTransitionActions] diff, so a regression in the model
 * fails here.
 */
class MasterSwitchTest {

    private val duoOn = ModuleSettings.DEFAULT.copy(enabled = true)
    private val iosOn = duoOn.copy(iosMode = true)

    private fun features(settings: ModuleSettings, override: Int? = null, iosReady: Boolean = true) =
        featureSetFor(settings, override, iosReady, otherOrientationEnabled = false)

    @Test
    fun `iOS owner has no Duo feature and only the shared panel trigger`() {
        val ios = features(iosOn)
        assertEquals(BarOwner.IOS, ios.owner)
        assertTrue(ios.iosRuntime)
        assertFalse(ios.duoRuntime)
        assertTrue("no Duo feature may survive iOS", ios.duo.isEmpty())
        assertEquals(
            "iOS carries the shared panel trigger and nothing else",
            setOf(DuoRegistration.PANEL_EXPANSION),
            duoRegistrationsFor(ios)
        )
        assertEquals(DuoRenderer.NONE, ios.renderer)
        assertEquals(SharedHook.entries.toSet(), ios.shared)
    }

    @Test
    fun `Duo off has no iOS host or receiver and no Duo registration`() {
        val off = features(ModuleSettings.DEFAULT)
        assertEquals(BarOwner.NONE, off.owner)
        assertFalse(off.duoRuntime)
        assertFalse(off.iosRuntime)
        assertTrue(duoRegistrationsFor(off).isEmpty())
        assertEquals(SharedHook.entries.toSet(), off.shared)

        val runtime = FakeRuntime()
        runtime.apply(off)
        assertFalse("an off master switch must not create an iOS host", runtime.iosHost)
        assertFalse("an off master switch must not register an iOS receiver", runtime.iosReceiver)
        assertFalse(runtime.duoHost)
        assertFalse(runtime.duoMonitor)
        assertTrue(runtime.registrations.installed().isEmpty())
    }

    @Test
    fun `the shared registrations are exactly the shared hook set in every mode`() {
        for (settings in listOf(ModuleSettings.DEFAULT, duoOn, iosOn)) {
            assertEquals(SharedHook.entries.toSet(), features(settings).shared)
        }
    }

    @Test
    fun `a live Duo to iOS to Duo switch leaves nothing of the other mode`() {
        val duo = features(duoOn)
        val ios = features(iosOn)
        val runtime = FakeRuntime()

        runtime.apply(duo)
        assertTrue(runtime.duoHost)
        assertTrue(runtime.duoMonitor)
        assertFalse(runtime.iosHost)
        assertFalse(runtime.iosReceiver)
        assertTrue(duoRegistrationsFor(duo).isNotEmpty())
        assertEquals(duoRegistrationsFor(duo), runtime.registrations.installed())

        runtime.apply(ios)
        assertFalse("iOS must tear the Duo host down", runtime.duoHost)
        assertFalse("iOS must stop the Duo monitor", runtime.duoMonitor)
        assertTrue(runtime.iosHost)
        assertTrue(runtime.iosReceiver)
        assertEquals(
            "only the shared panel trigger may remain",
            setOf(DuoRegistration.PANEL_EXPANSION),
            runtime.registrations.installed()
        )

        runtime.apply(duo)
        assertTrue(runtime.duoHost)
        assertTrue(runtime.duoMonitor)
        assertFalse("returning to Duo must tear iOS down", runtime.iosHost)
        assertFalse(runtime.iosReceiver)
        assertEquals(duoRegistrationsFor(duo), runtime.registrations.installed())
    }

    @Test
    fun `an adb override keeps iOS inert while forcing Duo`() {
        for (override in listOf(DuoGuard.ICONS_ONLY, DuoGuard.RIVE)) {
            val forced = features(iosOn, override)
            assertEquals(BarOwner.DUO, forced.owner)
            assertFalse(forced.iosRuntime)
            assertTrue(forced.duo.isNotEmpty())
            assertTrue(DuoRegistration.PANEL_EXPANSION in duoRegistrationsFor(forced))
        }
    }

    @Test
    fun `the Duo host exists only while Duo owns the bar`() {
        assertTrue(shouldCreateDuoHost(features(duoOn)))
        assertFalse(shouldCreateDuoHost(features(ModuleSettings.DEFAULT)))
        assertFalse(shouldCreateDuoHost(features(iosOn)))
        // A: the off orientation keeps no host even when the other orientation has Duo on; the next
        // enable for the on orientation creates and attaches it.
        assertFalse(
            shouldCreateDuoHost(
                featureSetFor(
                    ModuleSettings.DEFAULT,
                    adbOverride = null,
                    iosDrawingReady = true,
                    otherOrientationEnabled = true
                )
            )
        )
    }

    @Test
    fun `a teardown diffs the stopped set and leaves owner NONE`() {
        val duo = features(duoOn)
        val runtime = FakeRuntime()
        runtime.apply(duo)
        assertTrue(DuoRegistration.STATUS_LAYOUT in runtime.registrations.installed())

        runtime.teardown()

        assertEquals(BarOwner.NONE, runtime.current.owner)
        assertFalse(runtime.duoHost)
        assertFalse(runtime.duoMonitor)
        assertTrue("no Duo registration may survive a teardown", runtime.registrations.installed().isEmpty())
        assertEquals("the shared hook set is preserved", duo.shared, runtime.current.shared)
    }

    @Test
    fun `an iOS panel transition refreshes iOS state and never the Duo host`() {
        val runtime = FakeRuntime()
        runtime.apply(features(duoOn))
        assertTrue(runtime.duoHost)

        runtime.apply(features(iosOn))
        assertFalse(runtime.duoHost)
        assertFalse(runtime.duoMonitor)
        assertEquals(setOf(DuoRegistration.PANEL_EXPANSION), runtime.registrations.installed())

        runtime.panelTransition()
        assertEquals("only the iOS refresh branch may run", 1, runtime.iosRefreshes)
        assertEquals("the Duo panel branch may not run", 0, runtime.duoPanelTransitions)
        assertFalse("the iOS branch must not create or revive a Duo host", runtime.duoHost)
        assertFalse("the iOS branch must not restart the Duo monitor", runtime.duoMonitor)
        assertEquals(
            "the shared trigger survives an iOS panel transition",
            setOf(DuoRegistration.PANEL_EXPANSION),
            runtime.registrations.installed()
        )
    }

    /**
     * A tiny process model. No Android and no Xposed: the owner actions come from [ownerActions], the
     * registration diff from [RegistrationSet] and the panel branch from [panelTransitionActions],
     * exactly the objects the real [DuoHook] uses.
     */
    private class FakeRuntime {
        var current: FeatureSet =
            FeatureSet(BarOwner.NONE, DuoRenderer.NONE, emptySet(), SharedHook.entries.toSet())

        var duoHost = false
        var duoMonitor = false
        var iosHost = false
        var iosReceiver = false

        /** Edge-triggered panel work actually run, split by branch, so the iOS path can be asserted. */
        var iosRefreshes = 0
        var duoPanelTransitions = 0

        val registrations = RegistrationSet<DuoRegistration, DuoRegistration>(
            installer = { it },
            uninstaller = { }
        )

        /** Models one real panel/aggregate edge through the production owner decision. */
        fun panelTransition() {
            val actions = panelTransitionActions(current)
            if (PanelTransitionAction.IOS_REFRESH in actions) iosRefreshes++
            if (PanelTransitionAction.DUO_PANEL_STATE in actions) duoPanelTransitions++
        }

        fun apply(next: FeatureSet) {
            val actions = ownerActions(current, next)
            if (OwnerAction.STOP_DUO_MONITOR in actions) duoMonitor = false
            if (OwnerAction.TEARDOWN_DUO in actions) {
                duoHost = false
                duoMonitor = false
            }
            if (OwnerAction.ATTACH_DUO in actions) {
                duoHost = true
                duoMonitor = true
            }
            if (OwnerAction.TEARDOWN_IOS in actions) {
                iosHost = false
                iosReceiver = false
            }
            if (OwnerAction.ATTACH_IOS in actions) {
                iosHost = true
                iosReceiver = true
            }
            registrations.apply(duoRegistrationsFor(next))
            current = next
        }

        /**
         * Mirrors `DuoHook.stopDuoRuntime(next)`: the stopped set is assigned before the registration
         * diff, so a teardown removes the Duo hooks instead of re-installing them (B).
         */
        fun teardown() {
            val next = current.stopped()
            duoMonitor = false
            duoHost = false
            registrations.apply(duoRegistrationsFor(next))
            current = next
        }
    }
}
