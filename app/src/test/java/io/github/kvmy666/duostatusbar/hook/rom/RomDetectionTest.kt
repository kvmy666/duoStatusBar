package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ROM choice is a pure function of build identity, so it is pinned here rather than discovered on a
 * phone. What matters is not the label but the *ids*: a wrong container list means nothing is injected while
 * the stock bar stays untouched, which looks like "the module does nothing" and is expensive to debug.
 */
class RomDetectionTest {

    @Test
    fun `oneplus is recognised as oxygenos and keeps the measured ids`() {
        val rom = RomDetection.forThisRom("OnePlus", "OnePlus", "CPH2747", "OnePlus/CPH2747/OP611FL1:16")
        assertEquals("oxygenos", rom.id)
        assertEquals("system_icons", rom.containerIds.first())
        assertEquals("battery", rom.batteryId)
        assertTrue("measured, not guessed", rom.notes.contains("measured"))
    }

    @Test
    fun `oppo maps to the unverified coloros adapter`() {
        val rom = RomDetection.forThisRom("OPPO", "OPPO", "CPH2451", "OPPO/CPH2451:14")
        assertEquals("coloros", rom.id)
        assertTrue(rom.label.contains("ColorOS"))
        assertTrue("must admit it is unverified", rom.notes.contains("unverified"))
    }

    @Test
    fun `samsung maps to the unverified one ui adapter`() {
        val rom = RomDetection.forThisRom(
            "samsung", "samsung", "dm1q", "samsung/dm1q/dm1q:14/UP1A/eng:user"
        )
        assertEquals("samsung", rom.id)
        assertTrue(rom.label.contains("One UI"))
        assertTrue("must admit it is unverified", rom.notes.contains("unverified"))
    }

    @Test
    fun `xiaomi maps to the unverified hyperos adapter`() {
        val rom = RomDetection.forThisRom("Xiaomi", "Redmi", "aurora", "Redmi/aurora:15")
        assertEquals("hyperos", rom.id)
        assertTrue("must admit it is unverified", rom.notes.contains("unverified"))
    }

    @Test
    fun `anything else falls back to the aosp baseline`() {
        val rom = RomDetection.forThisRom("Google", "google", "husky", "Google/husky:16")
        assertEquals("aosp", rom.id)
        assertEquals("system_icons", rom.containerIds.first())
    }

    @Test
    fun `every adapter probes the aosp id first and names the systemui package`() {
        val roms = listOf(
            RomDetection.forThisRom("OnePlus", "OnePlus", "p", "d"),
            RomDetection.forThisRom("OPPO", "OPPO", "p", "d"),
            RomDetection.forThisRom("samsung", "samsung", "p", "d"),
            RomDetection.forThisRom("Xiaomi", "Redmi", "p", "d"),
            RomDetection.forThisRom("Google", "google", "p", "d")
        )
        for (rom in roms) {
            assertEquals(
                "AOSP spelling first, so a stock ROM needs no special case",
                "system_icons",
                rom.containerIds.first()
            )
            assertEquals("com.android.systemui", rom.systemUiPackage)
            assertTrue("container list must not be empty", rom.containerIds.isNotEmpty())
        }
    }
}
