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

    private fun measuredSamsung() = RomAdapter(
        id = "samsung",
        label = "Samsung One UI (measured)",
        systemUiPackage = "com.android.systemui",
        containerIds = listOf("samsung_status_icons"),
        batteryId = "battery",
        clockId = "clock",
        notes = "measured on SM-S938B",
        match = listOf("samsung"),
        measured = true
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
    fun `a malformed body yields no profiles instead of throwing`() {
        assertEquals(emptyList<RomAdapter>(), RomProfiles.parse("{not json"))
        assertEquals(emptyList<RomAdapter>(), RomProfiles.parse(""))
    }

    @Test
    fun `a measured profile overrides the unverified built-in for its match`() {
        val samsung = RomDetection.forThisRom(
            "samsung", "samsung", "dm1q", "samsung/dm1q:14", listOf(measuredSamsung())
        )
        assertEquals("samsung_status_icons", samsung.containerIds.first())
        assertTrue(samsung.measured)
    }

    @Test
    fun `an unmeasured profile never overrides the built-in`() {
        val samsung = RomDetection.forThisRom(
            "samsung", "samsung", "dm1q", "samsung/dm1q:14",
            listOf(measuredSamsung().copy(measured = false))
        )
        assertEquals("system_icons", samsung.containerIds.first())
        assertFalse(samsung.measured)
    }

    @Test
    fun `a profile that does not match leaves the built-in choice alone`() {
        val google = RomDetection.forThisRom(
            "Google", "google", "husky", "Google/husky:16", listOf(measuredSamsung())
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
    }

    @Test
    fun `shipped asset keeps the six unmeasured maps out of runtime selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profiles = RomProfiles.load(context)
        assertEquals(9, profiles.size)
        assertEquals(setOf("oxygenos", "hyperos"), profiles.filter { it.measured }.map { it.id }.toSet())
        assertEquals(
            setOf("aosp", "coloros", "oneui", "originos", "magicos", "myos", "nothingos"),
            profiles.filter { !it.measured }.map { it.id }.toSet()
        )
        val samsung = RomDetection.forThisRom("samsung", "samsung", "SM-S928", "One UI 8", profiles)
        assertEquals("samsung", samsung.id)
        assertFalse(samsung.measured)
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
