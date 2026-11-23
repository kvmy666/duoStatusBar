package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 P4: the pure delivery route matrix and the zero-double-ownership invariant.
 *
 * V2 is the system-native resource engine + battery hook; it needs iOS mode, a covered family, and
 * **both** process-wide hooks installed. Every other iOS family is the pre-R7 host; iOS off is NONE.
 */
class IosRouteTest {

    private fun rom(id: String) = RomAdapter(
        id = id,
        label = id,
        systemUiPackage = "com.android.systemui",
        containerIds = emptyList(),
        batteryId = "battery",
        notes = "test"
    )

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
    fun `a covered family missing either hook falls back to the host`() {
        for (id in listOf("oxygenos", "samsung")) {
            assertEquals(id, IosRoute.HOST, iosRoute(true, id, engineInstalled = false, batteryInstalled = true))
            assertEquals(id, IosRoute.HOST, iosRoute(true, id, engineInstalled = true, batteryInstalled = false))
            assertEquals(id, IosRoute.HOST, iosRoute(true, id, engineInstalled = false, batteryInstalled = false))
        }
    }

    @Test
    fun `host families stay host even when both hooks are installed`() {
        val hosts = listOf("hyperos", "nothing", "motorola", "vivo", "aosp", "coloros", "honor")
        for (id in hosts) {
            assertEquals(id, IosRoute.HOST, iosRoute(true, id, engineInstalled = true, batteryInstalled = true))
        }
    }

    @Test
    fun `integration-ish oxygenos adapter resolves v2 only with both hooks`() {
        val oxygenos = rom("oxygenos")
        assertEquals(IosRoute.V2, iosRoute(true, oxygenos.id, true, true))
        assertEquals(IosRoute.HOST, iosRoute(true, oxygenos.id, false, true))
        assertEquals(IosRoute.HOST, iosRoute(true, oxygenos.id, true, false))
        // The same adapter with iOS off is not an iOS route at all.
        assertEquals(IosRoute.NONE, iosRoute(false, oxygenos.id, true, true))
    }

    @Test
    fun `v2 never leaves a pre-r7 host attached`() {
        assertTrue(iosV2HasNoHosts(IosRoute.V2, iosMainHostPresent = false, extraHostCount = 0))
        assertFalse(iosV2HasNoHosts(IosRoute.V2, iosMainHostPresent = true, extraHostCount = 0))
        assertFalse(iosV2HasNoHosts(IosRoute.V2, iosMainHostPresent = false, extraHostCount = 1))
        // Host and NONE routes are not constrained by this invariant.
        assertTrue(iosV2HasNoHosts(IosRoute.HOST, iosMainHostPresent = true, extraHostCount = 2))
        assertTrue(iosV2HasNoHosts(IosRoute.HOST, iosMainHostPresent = false, extraHostCount = 0))
        assertTrue(iosV2HasNoHosts(IosRoute.NONE, iosMainHostPresent = false, extraHostCount = 0))
    }

    @Test
    fun `stopping the v2 owners keeps the selected route`() {
        // R7 P5: the HOST fall-through stops the v2 owners but must stay HOST; only the explicit
        // deactivate path writes NONE. A route rewrite here used to leave HOST reports saying NONE.
        assertEquals(IosRoute.HOST, routeAfterStoppingV2Owners(IosRoute.HOST))
        assertEquals(IosRoute.V2, routeAfterStoppingV2Owners(IosRoute.V2))
        assertEquals(IosRoute.NONE, routeAfterStoppingV2Owners(IosRoute.NONE))
    }
}
