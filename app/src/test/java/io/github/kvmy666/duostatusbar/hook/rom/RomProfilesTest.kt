package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-01: a measured profile can be added from data, but it must never override an unverified guess
 * or a device it does not match. That rule is what keeps a data edit from silently changing behaviour
 * on a phone we have not read, so it is pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RomProfilesTest {

    private val json = """
        {
          "version": 1,
          "profiles": [
            {
              "id": "oxygenos",
              "label": "OxygenOS / OnePlus",
              "systemUiPackage": "com.android.systemui",
              "containerIds": ["system_icons", "status_bar_end_side_content"],
              "batteryId": "battery",
              "clockId": "clock",
              "match": ["oneplus", "oxygen"],
              "measured": true,
              "notes": "measured on OxygenOS 16"
            },
            {
              "id": "broken",
              "containerIds": [],
              "match": ["nobody"],
              "measured": true,
              "notes": "must be skipped"
            }
          ]
        }
    """.trimIndent()

    /** The measured HyperOS 3 APK path, so the asset profile can prove its family (R18). */
    private val hyperOsMarkers = RomMarkers.of(
        systemUiSourceDir = "/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk"
    )

    private fun measuredHyperOs() = RomAdapter(
        id = "hyperos",
        label = "HyperOS / MIUI (measured)",
        systemUiPackage = "com.android.systemui",
        containerIds = listOf("hyperos_status_icons"),
        batteryId = "battery",
        clockId = "clock",
        notes = "measured on 24129PN74G",
        match = listOf("xiaomi", "redmi", "poco"),
        measured = true,
        apiMin = 37,
        apiMax = 37
    )

    @Test
    fun `parses a profile and skips an entry with no container ids`() {
        val profiles = RomProfiles.parse(json)
        assertEquals(1, profiles.size)
        val p = profiles.single()
        assertEquals("oxygenos", p.id)
        assertEquals(listOf("system_icons", "status_bar_end_side_content"), p.containerIds)
        assertTrue(p.measured)
        assertEquals(listOf("oneplus", "oxygen"), p.match)
    }

    @Test
    fun `parses aliases and canonicalises a legacy id`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "oneui",
                  "containerIds": ["system_icons"],
                  "aliases": ["oneui-legacy"]
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals("samsung", p.id)
        assertEquals(listOf("oneui-legacy"), p.aliases)
    }

    @Test
    fun `a malformed body yields no profiles instead of throwing`() {
        assertEquals(emptyList<RomAdapter>(), RomProfiles.parse("{not json"))
        assertEquals(emptyList<RomAdapter>(), RomProfiles.parse(""))
    }

    @Test
    fun `a measured profile overrides the unverified built-in for its match`() {
        val hyper = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016",
            listOf(measuredHyperOs()), 37, hyperOsMarkers
        )
        assertEquals("hyperos_status_icons", hyper.containerIds.first())
        assertTrue(hyper.measured)
    }

    @Test
    fun `an unmeasured profile never overrides the built-in`() {
        val hyper = RomDetection.forThisRom(
            "Xiaomi", "24129PN74G", "dada", "CP2A.260605.016",
            listOf(measuredHyperOs().copy(measured = false)), 37, hyperOsMarkers
        )
        assertEquals("system_icons", hyper.containerIds.first())
        assertFalse("the unmeasured profile's ids are not used", hyper.containerIds.contains("hyperos_status_icons"))
    }

    @Test
    fun `a profile that does not match leaves the built-in choice alone`() {
        val google = RomDetection.forThisRom(
            "Google", "google", "husky", "Google/husky:16",
            listOf(measuredHyperOs()), 37, hyperOsMarkers
        )
        assertEquals("aosp", google.id)
    }

    @Test
    fun `parses the optional shade fields`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "oxygenos",
                  "containerIds": ["system_icons"],
                  "shadeContainerIds": ["quick_qs_status_icons", "shade_header_system_icons"],
                  "shadeBatteryId": "batteryRemainingIcon",
                  "shadeStatusIconIds": ["statusIcons"],
                  "shadeMeasured": true
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals(
            listOf("quick_qs_status_icons", "shade_header_system_icons"),
            p.shadeContainerIds
        )
        assertEquals("batteryRemainingIcon", p.shadeBatteryId)
        assertEquals(listOf("statusIcons"), p.shadeStatusIconIds)
        assertTrue(p.shadeMeasured)
    }

    @Test
    fun `parses hideContainerIds`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "honor",
                  "containerIds": ["system_icon_area"],
                  "hideContainerIds": ["signal_cluster_container"]
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals(listOf("signal_cluster_container"), p.hideContainerIds)
    }

    @Test
    fun `shipped asset keeps the honor and coloros signal cluster ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = RomProfiles.load(context)
        val honor = profiles.first { it.id == "honor" }
        val coloros = profiles.first { it.id == "coloros" }
        assertEquals(listOf("signal_cluster_container"), honor.hideContainerIds)
        assertEquals(listOf("signal_cluster"), coloros.hideContainerIds)
        assertFalse("the honor map is still unverified", honor.measured)
        assertFalse("the coloros map is still unverified", coloros.measured)
    }

    @Test
    fun `parses dualSimCompositeIds`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "motorola",
                  "containerIds": ["system_icons"],
                  "dualSimCompositeIds": ["mobile_combo_dual"]
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals(listOf("mobile_combo_dual"), p.dualSimCompositeIds)
    }

    @Test
    fun `shipped asset keeps the motorola dual-SIM composite`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = RomProfiles.load(context)
        val motorola = profiles.first { it.id == "motorola" }
        assertEquals(listOf("mobile_combo_dual"), motorola.dualSimCompositeIds)
        assertFalse("the motorola map is still unverified", motorola.measured)
    }

    @Test
    fun `missing or blank shade fields default to empty`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "aosp",
                  "containerIds": ["system_icons"],
                  "shadeContainerIds": [],
                  "shadeBatteryId": "  "
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals(emptyList<String>(), p.shadeContainerIds)
        assertEquals("", p.shadeBatteryId)
        assertEquals(emptyList<String>(), p.shadeStatusIconIds)
        assertFalse(p.shadeMeasured)
    }


    @Test
    fun `parses optional capability keys`() {
        val json = """
            {
              "profiles": [
                 {
                   "id": "oxygenos",
                   "containerIds": ["system_icons"],
                   "coverHook": false,
                   "panelExpansionKind": "listener",
                   "panelHookMeasured": true,
                   "panelOwnerAvailable": true
                 }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertFalse(p.coverHook)
        assertEquals("listener", p.panelExpansionKind)
        assertTrue(p.panelHookMeasured)
        assertTrue(p.panelOwnerAvailable)
    }

    @Test
    fun `unknown panelExpansionKind becomes unknown`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "oxygenos",
                  "containerIds": ["system_icons"],
                  "panelExpansionKind": "something_else"
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals("unknown", p.panelExpansionKind)
    }

    @Test
    fun `missing capability keys preserve defaults`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "aosp",
                  "containerIds": ["system_icons"]
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertTrue(p.coverHook)
        assertEquals("panel_type_5arg", p.panelExpansionKind)
        assertFalse(p.panelHookMeasured)
        assertFalse(p.panelOwnerAvailable)
    }

    @Test
    fun `shipped asset keeps oxygenos shade map`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = RomProfiles.load(context)
        val rom = RomDetection.forThisRom("OnePlus", "OnePlus", "CPH2747", "OnePlus/CPH2747:16", profiles)
        assertEquals("oxygenos", rom.id)
        assertEquals(listOf("quick_qs_status_icons", "shade_header_system_icons"), rom.shadeContainerIds)
        assertEquals("batteryRemainingIcon", rom.shadeBatteryId)
        assertTrue(rom.shadeMeasured)
        assertTrue(rom.panelOwnerAvailable)
        // The shipped oxygenos profile is measured in the asset, but no marker proves the family on
        // brand alone, so the selected adapter is the code fallback and is not measured (R18).
        assertFalse("brand/SDK cannot prove OxygenOS", rom.measured)
    }

    @Test
    fun `shipped asset keeps the seven unmeasured maps out of runtime selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = RomProfiles.load(context)
        assertEquals(9, profiles.size)
        assertEquals(setOf("oxygenos", "hyperos"), profiles.filter { it.measured }.map { it.id }.toSet())
        assertEquals(
            setOf("aosp", "coloros", "samsung", "vivo", "honor", "motorola", "nothing"),
            profiles.filter { !it.measured }.map { it.id }.toSet()
        )
        val samsung = RomDetection.forThisRom("samsung", "samsung", "SM-S928", "One UI 8", profiles)
        assertEquals("samsung", samsung.id)
        assertFalse(samsung.measured)
    }

    @Test
    fun `shipped asset uses only canonical family ids with aliases`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = RomProfiles.load(context)
        val ids = profiles.map { it.id }.toSet()
        assertEquals(
            setOf("oxygenos", "hyperos", "aosp", "coloros", "samsung", "vivo", "honor", "motorola", "nothing"),
            ids
        )
        assertTrue("every id is a known family", ids.all { it in RomDetection.FAMILY_IDS })
        val aliasIds = setOf("oneui", "originos", "magicos", "myos", "nothingos")
        assertTrue("no legacy alias id survives as a profile id", ids.none { it in aliasIds })
        assertEquals(listOf("oneui"), profiles.first { it.id == "samsung" }.aliases)
        assertEquals(listOf("originos"), profiles.first { it.id == "vivo" }.aliases)
        assertEquals(listOf("magicos"), profiles.first { it.id == "honor" }.aliases)
        assertEquals(listOf("myos"), profiles.first { it.id == "motorola" }.aliases)
        assertEquals(listOf("nothingos"), profiles.first { it.id == "nothing" }.aliases)
    }

    @Test
    fun `parses apiMin and apiMax from json`() {
        val json = """
            {
              "profiles": [
                {
                  "id": "oxygenos",
                  "containerIds": ["system_icons"],
                  "apiMin": 36,
                  "apiMax": 36
                }
              ]
            }
        """.trimIndent()
        val p = RomProfiles.parse(json).single()
        assertEquals(36, p.apiMin)
        assertEquals(36, p.apiMax)
    }

}
