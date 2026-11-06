package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `xiaomi maps to the measured hyperos adapter`() {
        val rom = RomDetection.forThisRom("Xiaomi", "24129PN74G", "dada", "CP2A.260605.016")
        assertEquals("hyperos", rom.id)
        assertEquals("system_icons", rom.containerIds.first())
        assertTrue("measured on HyperOS 3, not guessed", rom.notes.contains("measured"))
        assertTrue("the wider strip is a fallback", rom.containerIds.contains("system_icon_area"))
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

    @Test
    fun `shade fields default empty so old constructor calls keep compiling`() {
        val rom = RomAdapter(
            id = "legacy",
            label = "Legacy",
            systemUiPackage = "com.android.systemui",
            containerIds = listOf("system_icons"),
            batteryId = "battery",
            notes = "old call site without shade fields"
        )
        assertEquals(emptyList<String>(), rom.shadeContainerIds)
        assertEquals("", rom.shadeBatteryId)
        assertEquals(emptyList<String>(), rom.shadeStatusIconIds)
        assertFalse(rom.shadeMeasured)
        assertFalse(rom.panelOwnerAvailable)
    }

    @Test
    fun `vivo maps to the static originos adapter`() {
        val rom = RomDetection.forThisRom("vivo", "vivo", "V2309A", "OriginOS 6")
        assertEquals("vivo", rom.id)
        assertEquals(listOf("system_icons", "system_icon_area", "statusIcons"), rom.containerIds)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertEquals("static APK OriginOS 6; runtime unverified", rom.notes)
        assertFalse("status and shade were not read off a device", rom.shadeMeasured)
        assertEquals("vivo", RomDetection.forThisRom("iQOO", "iQOO", "p", "d").id)
    }

    @Test
    fun `honor maps to the static magic os adapter`() {
        val rom = RomDetection.forThisRom("HONOR", "HONOR", "BVL-N49", "MagicOS 10")
        assertEquals("honor", rom.id)
        assertEquals(listOf("system_icons", "hw_system_icons", "system_icon_area"), rom.containerIds)
        assertEquals(
            listOf("hw_system_icons", "system_icon_area", "statusIcons"),
            rom.shadeContainerIds
        )
        assertEquals("battery", rom.shadeBatteryId)
        assertEquals("static APK MagicOS 11 old; runtime unverified", rom.notes)
        assertFalse(rom.shadeMeasured)
    }

    @Test
    fun `motorola maps to the static myos adapter`() {
        val rom = RomDetection.forThisRom("motorola", "moto", "XT2301", "MyOS 15")
        assertEquals("motorola", rom.id)
        assertEquals(listOf("system_icons", "statusIcons"), rom.containerIds)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertFalse(rom.shadeMeasured)
        assertFalse("must admit the runtime is unverified", rom.notes.contains("measured"))
    }

    @Test
    fun `nothing maps to the static nothing os adapter`() {
        val rom = RomDetection.forThisRom("Nothing", "Nothing", "A065", "Nothing OS 4.0")
        assertEquals("nothing", rom.id)
        assertEquals(listOf("system_icons", "statusIcons"), rom.containerIds)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertFalse(rom.shadeMeasured)
    }

    @Test
    fun `existing adapters carry their static shade maps and keep their selection`() {
        val oxygen = RomDetection.forThisRom("OnePlus", "OnePlus", "p", "d")
        assertEquals("oxygenos", oxygen.id)
        assertEquals(
            listOf("quick_qs_status_icons", "shade_header_system_icons"),
            oxygen.shadeContainerIds
        )
        assertTrue("OnePlus shade was measured on the device", oxygen.shadeMeasured)

        val hyper = RomDetection.forThisRom("Xiaomi", "24129PN74G", "dada", "CP2A.260605.016")
        assertEquals("hyperos", hyper.id)
        assertEquals(listOf("shade_header_system_icons", "system_icons"), hyper.shadeContainerIds)
        assertFalse("HyperOS shade has no device dump yet", hyper.shadeMeasured)

        val samsung = RomDetection.forThisRom("samsung", "samsung", "p", "d")
        assertEquals("samsung", samsung.id)
        assertEquals(listOf("shade_header_system_icons"), samsung.shadeContainerIds)
        assertFalse(samsung.shadeMeasured)

        val color = RomDetection.forThisRom("OPPO", "OPPO", "p", "d")
        assertEquals("coloros", color.id)
        assertEquals(
            listOf("quick_qs_status_icons", "quick_status_bar_header_system_icons", "system_icons"),
            color.shadeContainerIds
        )
        assertFalse(color.shadeMeasured)
    }

    @Test
    fun `every factory carries the audited capability values`() {
        val cases = mapOf(
            "oxygenos" to Triple(true, "panel_type_5arg", true),
            "hyperos" to Triple(true, "fzz_3arg", false),
            "samsung" to Triple(true, "listener", false),
            "vivo" to Triple(true, "zfz_3arg", false),
            "motorola" to Triple(true, "listener", false),
            "nothing" to Triple(true, "fzz_3arg", false),
            "coloros" to Triple(false, "none", false),
            "honor" to Triple(false, "none", false),
            "aosp" to Triple(true, "panel_type_5arg", false)
        )
        val inputs = listOf(
            "OnePlus" to "OnePlus", "Xiaomi" to "Redmi", "samsung" to "samsung",
            "vivo" to "vivo", "motorola" to "moto", "Nothing" to "Nothing",
            "OPPO" to "OPPO", "HONOR" to "HONOR", "Google" to "google"
        )
        // map each to expected id
        val expectedIds = listOf("oxygenos","hyperos","samsung","vivo","motorola","nothing","coloros","honor","aosp")
        for ((i, input) in inputs.withIndex()) {
            val rom = RomDetection.forThisRom(input.first, input.second, "p", "d")
            assertEquals(expectedIds[i], rom.id)
            val (cover, kind, measured) = cases[rom.id]!!
            assertEquals(cover, rom.coverHook)
            assertEquals(kind, rom.panelExpansionKind)
            assertEquals(measured, rom.panelHookMeasured)
            assertEquals(rom.id == "oxygenos", rom.panelOwnerAvailable)
        }
    }

}
