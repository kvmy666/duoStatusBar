package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.hook.rom.RomFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the R7 P1 name tables and level mapping to the measured evidence:
 * `.pm/evidence/r7/build/A-oxygenos-names.md` (OxygenOS) and `B-other-roms-names.md` (the rest).
 */
class IosResourceNamesTest {

    // ------------------------------------------------------------------------------ level tables

    @Test
    fun `wifi mapping follows the stock four-bar table`() {
        // Owner table (SignalMapping.wifiDotsForStockBars): stock 0..4 -> iOS 0,1,1,2,3.
        assertEquals(listOf(0, 1, 1, 2, 3), (0..4).map { IosResourceNames.wifiDots(it, 4) })
        assertEquals(0, IosResourceNames.wifiDots(0, 4))
        assertEquals(0, IosResourceNames.wifiDots(1, 0))
        assertEquals(0, IosResourceNames.wifiDots(-1, 4))
    }

    @Test
    fun `dormant three-level tables use a ceiling (data only)`() {
        // Only the dormant HyperOS/vivo tables pass top = 3; engineFamily is false for both.
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { IosResourceNames.wifiDots(it, 3) })
        // A value above the source top stays on the strongest arc.
        assertEquals(3, IosResourceNames.wifiDots(9, 3))
    }

    @Test
    fun `cell mapping keeps every non-zero level lit`() {
        assertEquals(listOf(1, 2, 3, 4, 4), (0..4).map { IosResourceNames.cellDots(it, 4) })
        assertEquals(listOf(1, 2, 3, 4, 4, 4), (0..5).map { IosResourceNames.cellDots(it, 5) })
        assertEquals(0, IosResourceNames.cellDots(-1, 4))
        assertEquals(0, IosResourceNames.cellDots(3, 0))
    }

    // ----------------------------------------------------------------------------- OxygenOS map

    @Test
    fun `OxygenOS maps the measured no-service resources to level zero`() {
        val oplus = IosResourceNames.specs("oxygenos")
        val noService = oplus.first { it.name == "stat_signal_noservice_lte" }
        val softNoService = oplus.first { it.name == "stat_signal_soft_noservice" }
        assertEquals(IosResourceKind.CELL, noService.kind)
        assertEquals(0, noService.value)
        assertEquals(IosResourceKind.CELL, softNoService.kind)
        assertEquals(0, softNoService.value)
        // The null/no-SIM glyph is explicitly NOT a no-service mapping.
        assertTrue(oplus.none { it.name == "stat_signal_signal_null_lte" })
    }

    @Test
    fun `OxygenOS maps activity arrows only as hidden extras`() {
        val oplus = IosResourceNames.specs("oxygenos")
        // R8: activity arrows are HIDDEN leaves, never glyph entries.
        assertTrue(oplus.filter { it.kind != IosResourceKind.HIDDEN }.none { it.name.contains("activity") })
        assertTrue(oplus.none { it.name.contains("ic_no_internet") })
        assertTrue(oplus.none { it.name.contains("mobiledata") })
        assertTrue(oplus.none { it.name == "stat_signal_soft_roma_lte" })
    }

    @Test
    fun `OxygenOS maps exactly the 19 measured hide-extras names`() {
        val expected = setOf(
            "stat_signal_wifi_6",
            "stat_signal_wifi_6e",
            "stat_signal_wifi_7",
            "stat_signal_wifi_double",
            "stat_signal_wifi_h_passpoint",
            "stat_signal_wifi_passpoint",
            "stat_signal_wifi_r_passpoint",
            "stat_signal_activity_wifi_none",
            "stat_signal_activity_wifi_in",
            "stat_signal_activity_wifi_out",
            "stat_signal_activity_wifi_inout",
            "stat_signal_activity_default_public",
            "stat_signal_activity_in_public",
            "stat_signal_activity_out_public",
            "stat_signal_activity_inout_public",
            "stat_signal_activity_soft_default_public",
            "stat_signal_activity_soft_in_public",
            "stat_signal_activity_soft_out_public",
            "stat_signal_activity_soft_inout_public"
        )
        val hidden = IosResourceNames.hiddenSpecs("oxygenos")
        assertEquals(19, hidden.size)
        assertEquals(expected, hidden.map { it.name }.toSet())
        assertEquals(19, hidden.map { it.name }.toSet().size) // no duplicates
        assertTrue(hidden.all { it.kind == IosResourceKind.HIDDEN && it.value == 0 })
        // The live Oplus family (ColorOS) shares the same hide-extras entries; every other family
        // has none.
        for (family in RomFamilies.CANONICAL_IDS.filter { it != "oxygenos" && it != "coloros" }) {
            assertTrue("$family has hide-extras", IosResourceNames.hiddenSpecs(family).isEmpty())
        }
    }

    @Test
    fun `coloros mirrors the OxygenOS resource table including the hidden specs`() {
        // R17D: ColorOS is live on the measured Oplus table, so it must be the same 113 specs and the
        // same 19 hidden leaves as OxygenOS (dropping one would silently hide a stock icon).
        val oxygen = IosResourceNames.specs("oxygenos")
        val coloros = IosResourceNames.specs("coloros")
        assertEquals(113, coloros.size)
        assertEquals(oxygen, coloros)
        assertEquals(19, IosResourceNames.hiddenSpecs("coloros").size)
        assertEquals(
            IosResourceNames.hiddenSpecs("oxygenos"),
            IosResourceNames.hiddenSpecs("coloros")
        )
    }

    // -------------------------------------------------------------------------------- all maps

    @Test
    fun `every label is a supported iOS label`() {
        for (family in RomFamilies.CANONICAL_IDS) {
            for (spec in IosResourceNames.specs(family).filter { it.kind == IosResourceKind.LABEL }) {
                assertTrue(
                    "$family ${spec.name} has label '${spec.label}'",
                    IosGeometry.DataLabel.supports(spec.label)
                )
            }
        }
    }

    @Test
    fun `no family repeats a named package-entry pair`() {
        for (family in RomFamilies.CANONICAL_IDS) {
            val seen = HashSet<Pair<String, String>>()
            for (spec in IosResourceNames.specs(family)) {
                if (spec.name.isEmpty()) continue // measured raw framework ids carry no name
                val key = spec.packageName to spec.name
                assertTrue("$family repeats $key", seen.add(key))
            }
        }
    }

    @Test
    fun `samsung keeps its captive wifi indication stock`() {
        val samsung = IosResourceNames.specs("samsung")
        assertTrue(samsung.none { it.name.startsWith("stat_sys_wifi_captive_") })
        // The normal Wi-Fi bars are still mapped.
        assertNotNull(samsung.firstOrNull { it.name == "stat_sys_wifi_signal_4" })
    }

    @Test
    fun `samsung maps the measured One UI extra signal families`() {
        val samsung = IosResourceNames.specs("samsung")
        // reverse/decoded/samsung/res/values/public.xml (One UI 16-11, SDK 36).
        assertEquals(2, samsung.first { it.name == "stat_sys_wifi6_signal_3" }.value)
        assertEquals(3, samsung.first { it.name == "stat_sys_wifi_signal_wifi_calling_4" }.value)
        assertEquals(0, samsung.first { it.name == "stat_sys_wifi7_signal_0" }.value)
        val nullVzw = samsung.first { it.name == "stat_sys_signal_null_vzw" }
        assertEquals(IosResourceKind.CELL, nullVzw.kind)
        assertEquals(0, nullVzw.value)
        assertEquals("LTE", samsung.first { it.name == "ic_lte_mobiledata" }.label)
        assertEquals("5G", samsung.first { it.name == "stat_sys_data_type_at_signal_area_5ga" }.label)
    }

    @Test
    fun `a spec without an explicit package resolves through the rom package`() {
        // R7 P5: the default is blank so the engine can use `rom.systemUiPackage` (m4).
        val specs = IosResourceNames.specs("oxygenos")
        assertTrue(specs.all { it.packageName.isEmpty() })
        assertEquals("", IosResourceSpec.DEFAULT_PACKAGE)
        // The measured Motorola framework ids are the only explicit package.
        assertTrue(IosResourceNames.specs("motorola").any { it.packageName == "android" })
    }

    @Test
    fun `live and dormant tables keep their exact measured counts`() {
        // Exact counts keep a future edit from silently dropping a measured entry.
        assertEquals(113, IosResourceNames.specs("oxygenos").size) // 94 + 19 R8 hide-extras
        assertEquals(113, IosResourceNames.specs("coloros").size) // the same live Oplus table (R17D)
        // 108 + 25 Wi-Fi (5 families) + 4 no-service + 41 labels (the two *_5g_plus_mobiledata_default*
        // entries in the R17C list are included, so the sum is 178, not the brief's 176).
        assertEquals(178, IosResourceNames.specs("samsung").size)
        assertEquals(38, IosResourceNames.specs("hyperos").size)
        assertEquals(25, IosResourceNames.specs("nothing").size)
        assertEquals(21, IosResourceNames.specs("motorola").size)
        assertEquals(11, IosResourceNames.specs("vivo").size)
        // Host-only / unsupported families have no resource map.
        assertTrue(IosResourceNames.specs("aosp").isEmpty())
        assertTrue(IosResourceNames.specs("honor").isEmpty())
        assertTrue(IosResourceNames.specs("unknown").isEmpty())
        for (id in listOf("hyperos", "nothing", "motorola", "vivo")) {
            assertFalse("$id dormant", IosV2Profile.engineFamily(id))
            assertNull("$id dormant", IosV2Profile.batteryOwner(id))
        }
    }

    @Test
    fun `measured tables keep their data and only oxygenos-samsung are live`() {
        val samsung = IosResourceNames.specs("samsung")
        assertEquals("LTE", samsung.first { it.name == "stat_sys_data_connected_lte" }.label)
        assertEquals("5G", samsung.first { it.name == "stat_sys_data_connected_5g" }.label)
        assertEquals("5G+", samsung.first { it.name == "stat_sys_data_connected_5g_plus_connected" }.label)
        assertNotNull(samsung.firstOrNull { it.name == "stat_sys_signal_5level_5" })
        assertEquals(0, samsung.first { it.name == "stat_sys_signal_null" }.value)

        val hyperos = IosResourceNames.specs("hyperos")
        assertTrue(hyperos.none { it.kind == IosResourceKind.LABEL }) // text drawable, not a resource
        assertEquals(3, hyperos.first { it.name == "stat_sys_wifi_signal_3" }.value)
        assertEquals(3, hyperos.first { it.name == "stat_sys_signal_2_no_voice" }.value)

        val nothing = IosResourceNames.specs("nothing")
        assertTrue(nothing.none { it.kind == IosResourceKind.CELL }) // NTSignalDrawable
        assertEquals("G", nothing.first { it.name == "nt_ic_g_mobiledata" }.label)
        assertEquals("5G", nothing.first { it.name == "nt_ic_5g_mobiledata" }.label)

        val motorola = IosResourceNames.specs("motorola")
        val rawWifi = motorola.filter { it.rawId != 0 }
        assertEquals(5, rawWifi.size)
        assertEquals("android", rawWifi.first().packageName)
        assertEquals(0x01080965, rawWifi.minOf { it.rawId })

        val vivo = IosResourceNames.specs("vivo")
        assertEquals(3, vivo.first { it.name == "vivo_wifi_signal_3" }.value)
        assertEquals(0, vivo.first { it.name == "vivo_wifi_unavailable" }.value)
        assertEquals(4, vivo.first { it.name == "vivo_signal_strength_4" }.value)

        for (id in listOf("hyperos", "nothing", "motorola", "vivo")) {
            assertFalse("$id dormant", IosV2Profile.engineFamily(id))
            assertNull("$id dormant", IosV2Profile.batteryOwner(id))
        }
    }
}
