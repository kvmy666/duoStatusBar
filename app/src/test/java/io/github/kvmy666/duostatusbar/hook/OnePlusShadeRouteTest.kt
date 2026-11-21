package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The vendor shade cluster is handed to exactly one owner: iOS wins once it attached, otherwise the
 * exact Duo path it had before is kept, and a null Duo host is only a failure when iOS did not take it.
 */
class OnePlusShadeRouteTest {

    @Test
    fun `ios attach wins with or without a duo host`() {
        assertEquals(ShadeAttachRoute.IOS, shadeAttachRoute(iosAttached = true, hasDuoHost = true))
        assertEquals(ShadeAttachRoute.IOS, shadeAttachRoute(iosAttached = true, hasDuoHost = false))
    }

    @Test
    fun `duo is the fallback and none is left over`() {
        assertEquals(ShadeAttachRoute.DUO, shadeAttachRoute(iosAttached = false, hasDuoHost = true))
        assertEquals(ShadeAttachRoute.NONE, shadeAttachRoute(iosAttached = false, hasDuoHost = false))
    }

    @Test
    fun `getter names use the OEM get prefix`() {
        assertEquals("getQsStatusIcon", getterFor("qsStatusIcon"))
        assertEquals("getBatteryIcon", getterFor("batteryIcon"))
        assertEquals("getCluster", getterFor("cluster"))
    }
}
