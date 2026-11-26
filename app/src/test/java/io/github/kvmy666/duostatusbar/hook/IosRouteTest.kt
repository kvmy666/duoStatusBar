package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.hook.rom.RomFamilies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure delivery route matrix.
 *
 * V2 is the only iOS route: it needs iOS mode, a covered family, and **both** process-wide hooks
 * installed. Every other iOS family keeps the stock bar and resolves to NONE; iOS off is NONE too.
 */
class IosRouteTest {

    @Test
    fun `iOS off is none regardless of family and hooks`() {
        assertEquals(IosRoute.NONE, iosRoute(false, "oxygenos", true, true))
        assertEquals(IosRoute.NONE, iosRoute(false, "hyperos", false, false))
    }

    @Test
    fun `both covered families go v2 with both hooks installed`() {
        for (id in listOf("oxygenos", "samsung")) {
            assertEquals(id, IosRoute.V2, iosRoute(true, id, engineInstalled = true, batteryInstalled = true))
        }
    }

    @Test
    fun `a covered family missing either hook keeps the stock bar`() {
        for (id in listOf("oxygenos", "samsung")) {
            assertEquals(id, IosRoute.NONE, iosRoute(true, id, engineInstalled = false, batteryInstalled = true))
            assertEquals(id, IosRoute.NONE, iosRoute(true, id, engineInstalled = true, batteryInstalled = false))
            assertEquals(id, IosRoute.NONE, iosRoute(true, id, engineInstalled = false, batteryInstalled = false))
        }
    }

    @Test
    fun `uncovered families keep the stock bar even when both hooks are installed`() {
        val uncovered = listOf("hyperos", "nothing", "motorola", "vivo", "aosp", "coloros", "honor")
        for (id in uncovered) {
            assertEquals(id, IosRoute.NONE, iosRoute(true, id, engineInstalled = true, batteryInstalled = true))
        }
    }

    @Test
    fun `the icon order is owned only on the measured oxygenos family`() {
        // Samsung exposes the same class names but the order permutation is UNVERIFIED there; enabling
        // it could desynchronise slot/view indices, so every other family keeps stock order.
        assertTrue(IosV2Profile.orderFamily("oxygenos"))
        for (id in RomFamilies.CANONICAL_IDS.filter { it != "oxygenos" }) {
            assertFalse(id, IosV2Profile.orderFamily(id))
        }
    }
}
