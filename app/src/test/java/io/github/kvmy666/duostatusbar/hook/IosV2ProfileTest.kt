package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.hook.rom.RomFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4: one owner per piece.
 *
 * The engine covers OxygenOS + Samsung, but the battery owner, the stacked-SIM binders, the
 * hide-extras binders and the data-type placement are separate, measured pieces. A foreign owner
 * (Samsung with only the Oplus hook) must never make a family look installed.
 */
class IosV2ProfileTest {

    @Test
    fun `battery owner is the measured class family per v2 family`() {
        assertEquals(IosBatteryOwner.OPLUS, IosV2Profile.batteryOwner("oxygenos"))
        assertEquals(IosBatteryOwner.AOSP, IosV2Profile.batteryOwner("samsung"))
        for (id in RomFamilies.CANONICAL_IDS.filter { it != "oxygenos" && it != "samsung" }) {
            assertNull(id, IosV2Profile.batteryOwner(id))
        }
    }

    @Test
    fun `stacked and extras are owned only on the measured OxygenOS family`() {
        assertTrue(IosV2Profile.stackedFamily("oxygenos"))
        assertTrue(IosV2Profile.extrasFamily("oxygenos"))
        for (id in RomFamilies.CANONICAL_IDS.filter { it != "oxygenos" }) {
            assertFalse(id, IosV2Profile.stackedFamily(id))
            assertFalse(id, IosV2Profile.extrasFamily(id))
        }
    }

    @Test
    fun `batteryOwnerInstalled accepts only the family's own hook`() {
        assertTrue(IosV2Profile.batteryOwnerInstalled("oxygenos", oplusHooked = true, aospHooked = false))
        assertFalse(IosV2Profile.batteryOwnerInstalled("oxygenos", oplusHooked = false, aospHooked = true))

        assertTrue(IosV2Profile.batteryOwnerInstalled("samsung", oplusHooked = false, aospHooked = true))
        assertFalse(IosV2Profile.batteryOwnerInstalled("samsung", oplusHooked = true, aospHooked = false))

        // A non-family has no owner at all, whatever was hooked.
        assertFalse(IosV2Profile.batteryOwnerInstalled("hyperos", true, true))
    }

    @Test
    fun `a Samsung with only the Oplus hook is not installed`() {
        // The old any-class semantics (`installed = oplus || aosp`) would have said true here; the
        // per-owner rule says the Samsung battery is still stock.
        assertFalse(IosV2Profile.batteryOwnerInstalled("samsung", oplusHooked = true, aospHooked = false))
        // And the reverse must not make OxygenOS look installed.
        assertFalse(IosV2Profile.batteryOwnerInstalled("oxygenos", oplusHooked = false, aospHooked = true))
    }

    @Test
    fun `unavailable battery line keeps the frozen OxygenOS string and names the others`() {
        // Byte-identical to the 2.1.1 line on OxygenOS.
        assertEquals(
            "v2 missing class=IosBatteryV2 - stock bar kept",
            IosV2Profile.unavailableBatteryLine("oxygenos")
        )
        // Every other covered family names its own missing owner.
        assertEquals(
            "v2 missing battery owner=aosp - stock bar kept",
            IosV2Profile.unavailableBatteryLine("samsung")
        )
    }

    @Test
    fun `existing family booleans are unchanged`() {
        assertEquals(
            listOf("oxygenos", "samsung"),
            RomFamilies.CANONICAL_IDS.filter { IosV2Profile.engineFamily(it) }
        )
        for (id in RomFamilies.CANONICAL_IDS) {
            assertEquals(id, IosV2Profile.engineFamily(id), IosV2Profile.batteryFamily(id))
        }
        assertEquals(
            listOf("oxygenos"),
            RomFamilies.CANONICAL_IDS.filter { IosV2Profile.typePlacementFamily(it) }
        )
        assertEquals(
            listOf("oxygenos"),
            RomFamilies.CANONICAL_IDS.filter { IosV2Profile.orderFamily(it) }
        )
    }
}
