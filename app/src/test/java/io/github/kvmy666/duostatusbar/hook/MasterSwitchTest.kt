package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3A A6: the two master switches own the process. While iOS owns the bar there is no Duo feature and
 * no Duo-only registration; the only hook it carries is the shared panel-expansion trigger. While Duo
 * owns it (or nothing does) no iOS runtime exists.
 *
 * The registration diff is the production [RegistrationSet] and the owner decision is
 * [duoRegistrationsFor], so a regression there fails here.
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
    fun `Duo off has no runtime owner and no Duo registration`() {
        val off = features(ModuleSettings.DEFAULT)
        assertEquals(BarOwner.NONE, off.owner)
        assertFalse(off.duoRuntime)
        assertFalse(off.iosRuntime)
        assertTrue(duoRegistrationsFor(off).isEmpty())
        assertEquals(SharedHook.entries.toSet(), off.shared)
    }

    @Test
    fun `the shared registrations are exactly the shared hook set in every mode`() {
        for (settings in listOf(ModuleSettings.DEFAULT, duoOn, iosOn)) {
            assertEquals(SharedHook.entries.toSet(), features(settings).shared)
        }
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
        runtime.current = duo
        runtime.registrations.apply(duoRegistrationsFor(duo))
        assertTrue(DuoRegistration.STATUS_LAYOUT in runtime.registrations.installed())

        runtime.teardown()

        assertEquals(BarOwner.NONE, runtime.current.owner)
        assertTrue("no Duo registration may survive a teardown", runtime.registrations.installed().isEmpty())
        assertEquals("the shared hook set is preserved", duo.shared, runtime.current.shared)
    }

    /**
     * A tiny process model for the teardown path only. No Android and no Xposed: the stopped set comes
     * from [FeatureSet.stopped] and the registration diff from [RegistrationSet].
     */
    private class FakeRuntime {
        var current: FeatureSet =
            FeatureSet(BarOwner.NONE, DuoRenderer.NONE, emptySet(), SharedHook.entries.toSet())

        val registrations = RegistrationSet<DuoRegistration, DuoRegistration>(
            installer = { it },
            uninstaller = { }
        )

        /**
         * Mirrors `DuoHook.stopDuoRuntime(next)`: the stopped set is assigned before the registration
         * diff, so a teardown removes the Duo hooks instead of re-installing them (B).
         */
        fun teardown() {
            val next = current.stopped()
            registrations.apply(duoRegistrationsFor(next))
            current = next
        }
    }
}
