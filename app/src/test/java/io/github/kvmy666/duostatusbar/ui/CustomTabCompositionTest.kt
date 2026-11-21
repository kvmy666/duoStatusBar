package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Custom tab composition that [CustomizationPage] actually renders.
 *
 * The iOS card was previously only added to the dead `showCustomization` branch of
 * [DuoSettingsScreen], so the owner could not reach it. The page now iterates [CUSTOM_CARD_ORDER],
 * and this test fails if that list ever drops or reorders the iOS card — or if the enabled rule stops
 * letting an available mode own the bar while still leaving its own card (and the engine picker) usable.
 */
class CustomTabCompositionTest {

    @Test
    fun `custom tab renders the iOS card first`() {
        val expected = listOf(
            CustomCard.IOS_MODE,
            CustomCard.ENGINE,
            CustomCard.CUSTOMIZE,
            CustomCard.ANIMATIONS,
            CustomCard.APPEARANCE,
            CustomCard.SIM,
            CustomCard.SURFACES,
            CustomCard.ICONS,
            CustomCard.ACTIONS,
            CustomCard.LIVE_EVENTS,
            CustomCard.DOWNLOAD
        )

        assertEquals(
            "CustomizationPage must compose exactly this card order",
            expected,
            CUSTOM_CARD_ORDER
        )
        assertEquals(
            "the iOS card must be first so the mode is always reachable",
            CustomCard.IOS_MODE,
            CUSTOM_CARD_ORDER.first()
        )
    }

    @Test
    fun `available iOS mode keeps the shared cards enabled and disables the Duo-only cards`() {
        val settings = DuoSettings(enabled = true, iosMode = true)
        val shared = setOf(
            CustomCard.IOS_MODE,
            CustomCard.SIM,
            CustomCard.SURFACES,
            CustomCard.ICONS
        )

        assertTrue(
            "the iOS card must stay enabled so the mode can always be switched off",
            iosCardEnabled(settings, iosAvailable = true)
        )
        assertTrue(
            "SIM must stay usable so the iOS SIM display can still be chosen",
            customCardEnabled(CustomCard.SIM, settings, iosAvailable = true)
        )
        assertTrue(
            "Surfaces apply to both drawings, so the card must stay usable",
            customCardEnabled(CustomCard.SURFACES, settings, iosAvailable = true)
        )
        assertTrue(
            "the icon hide list applies to both drawings, so the card must stay usable",
            customCardEnabled(CustomCard.ICONS, settings, iosAvailable = true)
        )
        CUSTOM_CARD_ORDER.filter { it !in shared }.forEach { card ->
            assertFalse(
                "Duo-only card $card must be disabled while an available iOS mode owns the bar",
                customCardEnabled(card, settings, iosAvailable = true)
            )
        }
    }

    @Test
    fun `off path leaves every card identical to the master switch and keeps the engine usable`() {
        for (master in listOf(true, false)) {
            val settings = DuoSettings(enabled = master, iosMode = false)

            assertTrue(
                "the iOS card must stay enabled so the mode can always be switched on or off",
                iosCardEnabled(settings, iosAvailable = true)
            )
            assertTrue(
                "the engine picker must stay usable even when the Duo master switch is off (master=$master)",
                customCardEnabled(CustomCard.ENGINE, settings, iosAvailable = true)
            )
            CUSTOM_CARD_ORDER
                .filter { it != CustomCard.IOS_MODE && it != CustomCard.ENGINE }
                .forEach { card ->
                    assertEquals(
                        "card $card must follow the master switch when iOS mode is off (master=$master)",
                        master,
                        customCardEnabled(card, settings, iosAvailable = true)
                    )
                }
        }
    }

    @Test
    fun `a stale iOS mode without a drawing cannot lock the Duo cards`() {
        for (master in listOf(true, false)) {
            val settings = DuoSettings(enabled = master, iosMode = true)

            assertTrue(
                "the iOS card must stay enabled even when the drawing is unavailable",
                iosCardEnabled(settings, iosAvailable = false)
            )
            assertTrue(
                "the engine picker must stay enabled when the iOS mode is unavailable (master=$master)",
                customCardEnabled(CustomCard.ENGINE, settings, iosAvailable = false)
            )
            CUSTOM_CARD_ORDER
                .filter { it != CustomCard.IOS_MODE && it != CustomCard.ENGINE }
                .forEach { card ->
                    assertEquals(
                        "an unavailable iOS mode must not disable $card; it follows the master switch (master=$master)",
                        master,
                        customCardEnabled(card, settings, iosAvailable = false)
                    )
                }
        }
    }

    @Test
    fun `a stale unavailable ios mode does not own the bar`() {
        assertTrue(
            "an available iOS mode must own the bar",
            iosOwnsBar(DuoSettings(iosMode = true), iosAvailable = true)
        )
        assertFalse(
            "iOS mode off must never own the bar",
            iosOwnsBar(DuoSettings(iosMode = false), iosAvailable = true)
        )
        assertFalse(
            "a stale iOS mode without an available drawing must not own the bar",
            iosOwnsBar(DuoSettings(iosMode = true), iosAvailable = false)
        )
    }

    @Test
    fun `ios card is enabled to switch on or, already on, to switch off`() {
        assertTrue(
            "an available drawing enables switching the mode on",
            iosCardEnabled(DuoSettings(iosMode = false), iosAvailable = true)
        )
        assertTrue(
            "an available drawing keeps an already-on mode switchable",
            iosCardEnabled(DuoSettings(iosMode = true), iosAvailable = true)
        )
        assertTrue(
            "an already-on mode must stay switchable even when the drawing is unavailable",
            iosCardEnabled(DuoSettings(iosMode = true), iosAvailable = false)
        )
        assertFalse(
            "without a drawing an off mode cannot be switched on",
            iosCardEnabled(DuoSettings(iosMode = false), iosAvailable = false)
        )
    }

    @Test
    fun `duo owns the bar unless an available iOS mode does`() {
        assertFalse(
            "an available iOS mode means Duo does not own the bar",
            duoOwnsBar(DuoSettings(iosMode = true), iosAvailable = true)
        )
        assertTrue(
            "an off iOS mode leaves the bar to Duo",
            duoOwnsBar(DuoSettings(iosMode = false), iosAvailable = true)
        )
        assertTrue(
            "an off iOS mode without a drawing leaves the bar to Duo",
            duoOwnsBar(DuoSettings(iosMode = false), iosAvailable = false)
        )
        assertTrue(
            "a stale iOS mode without a drawing leaves the bar to Duo",
            duoOwnsBar(DuoSettings(iosMode = true), iosAvailable = false)
        )
    }
}
