package io.github.kvmy666.duostatusbar.hook

import android.os.BatteryManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stb06ChargingVisualsTest {

    @Test
    fun showsChargingBelowFull() {
        assertTrue(shouldShowCharging(99, true, BatteryManager.BATTERY_STATUS_CHARGING))
        assertTrue(shouldShowCharging(99, false, BatteryManager.BATTERY_STATUS_CHARGING))
        assertTrue(shouldShowCharging(99, true, BatteryManager.BATTERY_STATUS_FULL))
    }

    @Test
    fun stopsChargingVisualsAtFull() {
        assertFalse(shouldShowCharging(100, true, BatteryManager.BATTERY_STATUS_CHARGING))
        assertFalse(shouldShowCharging(100, true, BatteryManager.BATTERY_STATUS_FULL))
        assertFalse(shouldShowCharging(100, false, BatteryManager.BATTERY_STATUS_CHARGING))
        assertFalse(shouldShowCharging(100, false, BatteryManager.BATTERY_STATUS_FULL))
    }

    @Test
    fun resumesWhenDroppingBelowFullWhilePlugged() {
        assertTrue(shouldShowCharging(99, true, BatteryManager.BATTERY_STATUS_FULL))
    }
}
