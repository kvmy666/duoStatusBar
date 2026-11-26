package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W4: iOS mode greys every Duo control. The OFF path must stay identical to the master switch on its
 * own, so a false [DuoSettings.iosMode] can never disable a control the user could use before.
 */
class IosSettingsUiTest {

    @Test
    fun `duo controls follow the master switch and only an available iOS mode`() {
        // The drawing is available in this build, so an ON owns the bar and stops every Duo control.
        assertFalse(duoControlsEnabled(DuoSettings(enabled = true, iosMode = true)))
        assertFalse(duoControlsEnabled(DuoSettings(enabled = false, iosMode = true)))
        // The explicit seam still proves the same matrix.
        assertFalse(duoControlsEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = true))
        assertTrue(duoControlsEnabled(DuoSettings(enabled = true, iosMode = false), iosAvailable = true))
        assertFalse(duoControlsEnabled(DuoSettings(enabled = false, iosMode = false), iosAvailable = true))
    }

    @Test
    fun `off path is exactly the master switch`() {
        for (enabled in listOf(true, false)) {
            assertEquals(
                enabled,
                duoControlsEnabled(DuoSettings(enabled = enabled, iosMode = false))
            )
        }
    }

    @Test
    fun `switching on is refused while the drawing is unavailable`() {
        val before = DuoSettings(enabled = true, iosMode = false)

        val result = applyIosModeToggle(before, requested = true, available = false)

        assertSame(before, result)
        assertFalse(result.iosMode)
    }

    @Test
    fun `available mode sets and clears iosMode normally`() {
        val off = DuoSettings(iosMode = false)
        val on = DuoSettings(iosMode = true)

        assertTrue(applyIosModeToggle(off, requested = true, available = true).iosMode)
        assertFalse(applyIosModeToggle(on, requested = false, available = true).iosMode)
    }

    @Test
    fun `unknown availability preserves today's usable switch`() {
        assertTrue(iosModeAvailable())
        assertTrue(iosModeAvailable(null))
        assertTrue(iosModeAvailable(true))
        assertFalse(iosModeAvailable(false))
    }

    @Test
    fun `unsupported disables the switch unless the mode is already on`() {
        assertFalse(
            "an unsupported, off mode cannot be switched on",
            iosCardEnabled(DuoSettings(iosMode = false), iosAvailable = false)
        )
        assertTrue(
            "a stale on mode stays switchable so it can be turned off",
            iosCardEnabled(DuoSettings(iosMode = true), iosAvailable = false)
        )
    }

    @Test
    fun `requested on while unsupported never turns Duo off`() {
        val before = DuoSettings(enabled = true, iosMode = false)

        val result = applyIosModeToggle(before, requested = true, available = false)

        assertSame(before, result)
        assertTrue("the unavailable route must leave Duo enabled", result.enabled)
    }

    @Test
    fun `a stale unsupported ios mode can be turned off and hands the bar back`() {
        val stale = DuoSettings(enabled = false, iosMode = true)

        val result = applyIosModeToggle(stale, requested = false, available = false)

        assertFalse(result.iosMode)
        assertTrue(result.enabled)
    }

    @Test
    fun `an unavailable ios mode owns nothing and enables nothing`() {
        val stale = DuoSettings(enabled = true, iosMode = true)

        assertFalse(iosOwnsBar(stale, iosAvailable = false))
        assertTrue(duoControlsEnabled(stale, iosAvailable = false))
        assertFalse(iosTuningEnabled(stale, available = false))
    }

    @Test
    fun `a saved ios mode can always be switched off`() {
        val stale = DuoSettings(enabled = true, iosMode = true)

        val result = applyIosModeToggle(stale, requested = false)

        assertFalse(result.iosMode)
    }

    @Test
    fun `switching off is allowed even while unavailable`() {
        val stale = DuoSettings(enabled = true, iosMode = true)

        val result = applyIosModeToggle(stale, requested = false, available = false)

        assertFalse(result.iosMode)
    }
}
