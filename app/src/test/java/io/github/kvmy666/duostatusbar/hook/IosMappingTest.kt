package io.github.kvmy666.duostatusbar.hook

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live iOS state -> visual mapping, tested here rather than on a phone.
 *
 * Every boundary the mapping names is pinned: the low threshold, saver-overrides-low, charging vs
 * plugged-not-charging, the percentage tri-state, and the SIM display string.
 */
class IosMappingTest {

    private val tint = 0xFF123456.toInt()

    // ---------------------------------------------------------------------------- low threshold

    @Test
    fun `missing low threshold falls back to twenty`() {
        val r = IosMapping.lowThreshold(null)
        assertEquals(20, r.value)
        assertTrue(r.fallback)
    }

    @Test
    fun `valid low threshold is kept without fallback`() {
        val r = IosMapping.lowThreshold(15)
        assertEquals(15, r.value)
        assertFalse(r.fallback)
    }

    @Test
    fun `negative low threshold falls back`() {
        val r = IosMapping.lowThreshold(-1)
        assertEquals(20, r.value)
        assertTrue(r.fallback)
    }

    @Test
    fun `above one hundred falls back`() {
        val r = IosMapping.lowThreshold(101)
        assertEquals(20, r.value)
        assertTrue(r.fallback)
    }

    @Test
    fun `zero and one hundred are valid thresholds`() {
        assertEquals(0, IosMapping.lowThreshold(0).value)
        assertFalse(IosMapping.lowThreshold(0).fallback)
        assertEquals(100, IosMapping.lowThreshold(100).value)
        assertFalse(IosMapping.lowThreshold(100).fallback)
    }

    // ---------------------------------------------------------------------------- battery state

    private fun state(
        level: Int,
        threshold: Int = 20,
        saver: Boolean = false,
        plugged: Boolean = false,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING
    ) = IosMapping.batteryState(level, threshold, saver, plugged, status)

    @Test
    fun `level at the threshold is low`() {
        assertEquals(IosBatteryState.LOW, state(level = 20, threshold = 20))
    }

    @Test
    fun `one above the threshold is normal`() {
        assertEquals(IosBatteryState.NORMAL, state(level = 21, threshold = 20))
    }

    @Test
    fun `zero is low`() {
        assertEquals(IosBatteryState.LOW, state(level = 0))
    }

    @Test
    fun `saver overrides low`() {
        assertEquals(IosBatteryState.SAVER, state(level = 5, saver = true))
    }

    @Test
    fun `charging without saver is charging`() {
        assertEquals(
            IosBatteryState.CHARGING,
            state(level = 50, plugged = true, status = BatteryManager.BATTERY_STATUS_CHARGING)
        )
    }

    @Test
    fun `charging with saver is yellow charging`() {
        assertEquals(
            IosBatteryState.CHARGING_SAVER,
            state(level = 50, saver = true, plugged = true, status = BatteryManager.BATTERY_STATUS_CHARGING)
        )
    }

    @Test
    fun `plugged not charging outranks saver`() {
        assertEquals(
            IosBatteryState.PLUGGED_NOT_CHARGING,
            state(
                level = 80,
                saver = true,
                plugged = true,
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING
            )
        )
    }

    @Test
    fun `unplugged not charging is not grey`() {
        assertEquals(
            IosBatteryState.LOW,
            state(level = 10, plugged = false, status = BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        )
        assertEquals(
            IosBatteryState.NORMAL,
            state(level = 80, plugged = false, status = BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        )
    }

    // ---------------------------------------------------------------------------- battery visual

    private fun battery(
        level: Int,
        threshold: Int = 20,
        saver: Boolean = false,
        plugged: Boolean = false,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING
    ) = IosMapping.battery(
        level = level,
        lowThreshold = threshold,
        saver = saver,
        plugged = plugged,
        status = status,
        tint = tint
    )

    @Test
    fun `normal battery takes the dynamic tint`() {
        val v = battery(level = 64)
        assertEquals(0.64f, v.batteryLevel, 0.0001f)
        assertEquals(IosBatteryState.NORMAL, v.batteryState)
        assertEquals(tint, v.batteryFillColor)
        assertEquals(tint, v.tint)
        assertEquals(0f, v.boltOpacity, 0.0001f)
        assertTrue(v.visible)
        assertFalse(v.percentInside)
    }

    @Test
    fun `percent inside is copied into the visual`() {
        val v = IosMapping.battery(
            level = 69,
            lowThreshold = 20,
            saver = false,
            plugged = false,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            tint = tint,
            percentInside = true
        )
        assertTrue(v.percentInside)
        assertEquals(0.69f, v.batteryLevel, 0.0001f)
    }

    @Test
    fun `low battery is red`() {
        assertEquals(IosMapping.COLOR_LOW, battery(level = 12).batteryFillColor)
    }

    @Test
    fun `saver battery is yellow`() {
        assertEquals(IosMapping.COLOR_SAVER, battery(level = 70, saver = true).batteryFillColor)
    }

    @Test
    fun `charging battery is green with a bolt`() {
        val v = battery(level = 50, plugged = true, status = BatteryManager.BATTERY_STATUS_CHARGING)
        assertEquals(IosMapping.COLOR_CHARGING, v.batteryFillColor)
        assertEquals(1f, v.boltOpacity, 0.0001f)
    }

    @Test
    fun `charging with saver is yellow with a bolt`() {
        val v = battery(
            level = 50,
            saver = true,
            plugged = true,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )
        assertEquals(IosMapping.COLOR_SAVER, v.batteryFillColor)
        assertEquals(1f, v.boltOpacity, 0.0001f)
    }

    @Test
    fun `plugged not charging is grey with a bolt even with saver`() {
        val v = battery(
            level = 90,
            saver = true,
            plugged = true,
            status = BatteryManager.BATTERY_STATUS_NOT_CHARGING
        )
        assertEquals(IosMapping.COLOR_PLUGGED_NOT_CHARGING, v.batteryFillColor)
        assertEquals(1f, v.boltOpacity, 0.0001f)
    }

    @Test
    fun `unplugged not charging keeps the level colour and no bolt`() {
        val low = battery(level = 10, status = BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        assertEquals(IosMapping.COLOR_LOW, low.batteryFillColor)
        assertEquals(0f, low.boltOpacity, 0.0001f)
    }

    @Test
    fun `battery level is clamped`() {
        val high = battery(level = 150)
        assertEquals(1f, high.batteryLevel, 0.0001f)
        val low = battery(level = -5)
        assertEquals(0f, low.batteryLevel, 0.0001f)
    }

    // ---------------------------------------------------------------------------- sim display

    @Test
    fun `sim display string is auto for anything but single or dual`() {
        assertEquals(IosSimDisplay.SINGLE, IosMapping.simDisplay("single"))
        assertEquals(IosSimDisplay.SINGLE, IosMapping.simDisplay(" SINGLE "))
        assertEquals(IosSimDisplay.DUAL, IosMapping.simDisplay("dual"))
        assertEquals(IosSimDisplay.AUTO, IosMapping.simDisplay("auto"))
        assertEquals(IosSimDisplay.AUTO, IosMapping.simDisplay("bogus"))
        assertEquals(IosSimDisplay.AUTO, IosMapping.simDisplay(""))
    }
}
