package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the SIM card enablement matrix.
 *
 * The SIM card is shared: the iOS SIM display row follows the iOS drawing, while the Duo cellular
 * line and the Wi-Fi dots follow the Duo master switch. The card itself stays reachable whenever
 * either drawing owns the bar, so the iOS display can be chosen with the Duo master switch off.
 */
class SimCardTest {

    @Test
    fun `sim card is shared while either drawing owns the bar`() {
        assertTrue(
            "the Duo master switch keeps the SIM card usable",
            sharedCardEnabled(DuoSettings(enabled = true, iosMode = false), iosAvailable = true)
        )
        assertTrue(
            "an available iOS mode keeps the SIM card usable even with the Duo switch off",
            sharedCardEnabled(DuoSettings(enabled = false, iosMode = true), iosAvailable = true)
        )
        assertTrue(
            "both drawings on still leaves one owner, so the card stays usable",
            sharedCardEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = true)
        )
        assertFalse(
            "with both drawings off the SIM card is disabled",
            sharedCardEnabled(DuoSettings(enabled = false, iosMode = false), iosAvailable = true)
        )
        assertEquals(
            "customCardEnabled(SIM) must use the shared predicate",
            sharedCardEnabled(DuoSettings(enabled = false, iosMode = true), iosAvailable = true),
            customCardEnabled(
                CustomCard.SIM,
                DuoSettings(enabled = false, iosMode = true),
                iosAvailable = true
            )
        )
    }

    @Test
    fun `ios sim display follows the iOS drawing only`() {
        assertTrue(
            "iOS owning the bar enables the SIM display regardless of the Duo switch",
            iosSimDisplayEnabled(DuoSettings(enabled = false, iosMode = true), iosAvailable = true)
        )
        assertTrue(
            "the Duo master switch does not control the SIM display",
            iosSimDisplayEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = true)
        )
        assertFalse(
            "an off iOS mode leaves the SIM display disabled",
            iosSimDisplayEnabled(DuoSettings(enabled = true, iosMode = false), iosAvailable = true)
        )
        assertFalse(
            "an unavailable iOS mode cannot own the bar, so the SIM display stays disabled",
            iosSimDisplayEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = false)
        )
    }

    @Test
    fun `duo sim rows mirror the Duo master switch and stop only while iOS owns the bar`() {
        assertTrue(
            "the master switch enables the Duo rows",
            duoSimRowsEnabled(DuoSettings(enabled = true, iosMode = false), iosAvailable = true)
        )
        assertFalse(
            "the off master switch disables the Duo rows",
            duoSimRowsEnabled(DuoSettings(enabled = false, iosMode = false), iosAvailable = true)
        )
        assertFalse(
            "an available iOS mode disables the Duo rows",
            duoSimRowsEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = true)
        )
        assertTrue(
            "a stale unavailable iOS mode leaves the Duo rows usable",
            duoSimRowsEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = false)
        )
        for (enabled in listOf(true, false)) {
            for (iosMode in listOf(true, false)) {
                for (available in listOf(true, false)) {
                    val settings = DuoSettings(enabled = enabled, iosMode = iosMode)
                    assertEquals(
                        "duoSimRowsEnabled must mirror duoControlsEnabled " +
                            "(enabled=$enabled, iosMode=$iosMode, available=$available)",
                        duoControlsEnabled(settings, available),
                        duoSimRowsEnabled(settings, available)
                    )
                }
            }
        }
    }
}
