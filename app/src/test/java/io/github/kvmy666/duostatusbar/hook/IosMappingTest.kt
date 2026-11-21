package io.github.kvmy666.duostatusbar.hook

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The iOS state -> visual mapping, tested here rather than on a phone.
 *
 * Every boundary the plan names is pinned: the low threshold, saver-overrides-low, charging vs
 * plugged-not-charging, the percentage tri-state, Wi-Fi validation (M6), and the dual-SIM mapping by
 * subscription id (never by view order).
 */
class IosMappingTest {

    private val tint = 0xFF123456.toInt()

    private fun wifiSample(
        dots: Int = 3,
        on: Boolean = true,
        active: Boolean = true,
        validated: Boolean = true
    ) = WifiSample(
        rssi = -55,
        calc5 = 4,
        stockBars = 4,
        dots = dots,
        on = on,
        active = active,
        validated = validated,
        source = "active"
    )

    private fun sig(slot: Int, sub: Int, level: Int) = SimSignal(slot, sub, level)

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
        assertEquals(IosPart.BATTERY, v.part)
        assertEquals(0.64f, v.batteryLevel, 0.0001f)
        assertEquals(IosBatteryState.NORMAL, v.batteryState)
        assertEquals(tint, v.batteryFillColor)
        assertEquals(tint, v.tint)
        assertEquals(0f, v.boltOpacity, 0.0001f)
        assertTrue(v.visible)
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

    // ---------------------------------------------------------------------------- wifi

    @Test
    fun `wifi off is hidden`() {
        val v = IosMapping.wifi(wifiSample(on = false), tint)
        assertEquals(IosPart.WIFI, v.part)
        assertFalse(v.visible)
        assertEquals(0f, v.wifiLevel, 0.0001f)
        assertEquals(tint, v.tint)
    }

    @Test
    fun `wifi active but not validated is hidden`() {
        val v = IosMapping.wifi(wifiSample(validated = false), tint)
        assertFalse(v.visible)
        assertEquals(0f, v.wifiLevel, 0.0001f)
    }

    @Test
    fun `wifi not active is hidden`() {
        val v = IosMapping.wifi(wifiSample(active = false), tint)
        assertFalse(v.visible)
        assertEquals(0f, v.wifiLevel, 0.0001f)
    }

    @Test
    fun `validated wifi keeps its level`() {
        val v = IosMapping.wifi(wifiSample(dots = 2), tint)
        assertTrue(v.visible)
        assertEquals(2f, v.wifiLevel, 0.0001f)
    }

    @Test
    fun `wifi level is clamped to zero through three`() {
        assertEquals(3f, IosMapping.wifi(wifiSample(dots = 9), tint).wifiLevel, 0.0001f)
        assertEquals(0f, IosMapping.wifi(wifiSample(dots = -3), tint).wifiLevel, 0.0001f)
    }

    // ---------------------------------------------------------------------------- cellular plan

    private fun slot(subId: Int, visible: Boolean = true) = CellSlot(subId, visible)

    private fun plan(
        signals: List<SimSignal>,
        defaultSubId: Int = 0,
        slots: List<CellSlot>
    ) = IosMapping.cellPlan(signals, defaultSubId, slots)

    private fun single(value: IosCellPlan): IosCellPlan.Single {
        assertTrue("expected Single, was $value", value is IosCellPlan.Single)
        return value as IosCellPlan.Single
    }

    private fun dual(value: IosCellPlan): IosCellPlan.Dual {
        assertTrue("expected Dual, was $value", value is IosCellPlan.Dual)
        return value as IosCellPlan.Dual
    }

    private val twoSims = listOf(
        sig(slot = 0, sub = 11, level = 3),
        sig(slot = 1, sub = 22, level = 1)
    )

    @Test
    fun `empty signals leave one visible slot as empty bars and two slots as none`() {
        val one = single(plan(emptyList(), slots = listOf(slot(7))))
        assertEquals(0, one.slot)
        assertEquals(7, one.subId)
        assertEquals(0f, one.level, 0.0001f)

        assertEquals(IosCellPlan.None, plan(emptyList(), slots = listOf(slot(7), slot(8))))
        assertEquals(IosCellPlan.None, plan(emptyList(), slots = listOf(slot(7, visible = false))))
    }

    @Test
    fun `one sim maps to the visible slot with its own level`() {
        val p = single(
            plan(
                listOf(sig(slot = 0, sub = 7, level = 3)),
                slots = listOf(slot(7), slot(9))
            )
        )
        assertEquals(0, p.slot)
        assertEquals(7, p.subId)
        assertEquals(3f, p.level, 0.0001f)
    }

    @Test
    fun `one sim over two slots ignores an unreadable other slot`() {
        val p = single(
            plan(
                listOf(sig(slot = 0, sub = 7, level = 2)),
                slots = listOf(slot(7), slot(0))
            )
        )
        assertEquals(0, p.slot)
        assertEquals(7, p.subId)
        assertEquals(2f, p.level, 0.0001f)

        // The single unreadable slot is the current fallback.
        val fallback = single(plan(listOf(sig(0, 7, 2)), slots = listOf(slot(0))))
        assertEquals(0, fallback.slot)
        assertEquals(0, fallback.subId)
        assertEquals(2f, fallback.level, 0.0001f)
    }

    @Test
    fun `one sim with no matching slot is none`() {
        assertEquals(
            IosCellPlan.None,
            plan(listOf(sig(0, 7, 2)), slots = listOf(slot(8), slot(9)))
        )
    }

    @Test
    fun `two visible slots with the same readable id are none`() {
        // Drawing over the first match would leave the second stock icon sitting beside the glyph.
        assertEquals(
            IosCellPlan.None,
            plan(listOf(sig(0, 7, 2)), slots = listOf(slot(7), slot(7)))
        )
    }

    @Test
    fun `exactly one visible matching slot is single`() {
        val p = single(
            plan(
                listOf(sig(0, 7, 2)),
                slots = listOf(slot(7), slot(0))
            )
        )
        assertEquals(0, p.slot)
        assertEquals(7, p.subId)
        assertEquals(2f, p.level, 0.0001f)
    }

    @Test
    fun `two sims choose the data by default sub and fall back to the first slot`() {
        val byDefault = dual(plan(twoSims, defaultSubId = 22, slots = listOf(slot(11), slot(22))))
        assertEquals(1, byDefault.dataSlot)
        assertEquals(0, byDefault.otherSlot)
        assertEquals(22, byDefault.dataSubId)
        assertEquals(11, byDefault.otherSubId)
        assertEquals(1f, byDefault.bars, 0.0001f)
        assertEquals(3f, byDefault.dots, 0.0001f)

        val fallback = dual(plan(twoSims, defaultSubId = 99, slots = listOf(slot(11), slot(22))))
        assertEquals(0, fallback.dataSlot)
        assertEquals(1, fallback.otherSlot)
        assertEquals(11, fallback.dataSubId)
        assertEquals(22, fallback.otherSubId)
        assertEquals(3f, fallback.bars, 0.0001f)
        assertEquals(1f, fallback.dots, 0.0001f)
    }

    @Test
    fun `two sims are ordered by slot regardless of signal order`() {
        val swapped = listOf(twoSims[1], twoSims[0])
        val p = dual(plan(swapped, defaultSubId = 99, slots = listOf(slot(11), slot(22))))
        assertEquals(0, p.dataSlot)
        assertEquals(1, p.otherSlot)
        assertEquals(11, p.dataSubId)
        assertEquals(22, p.otherSubId)
        assertEquals(3f, p.bars, 0.0001f)
        assertEquals(1f, p.dots, 0.0001f)
    }

    @Test
    fun `two sim levels are clamped to zero through four`() {
        val p = dual(
            plan(
                listOf(sig(0, 11, 9), sig(1, 22, -2)),
                defaultSubId = 11,
                slots = listOf(slot(11), slot(22))
            )
        )
        assertEquals(4f, p.bars, 0.0001f)
        assertEquals(0f, p.dots, 0.0001f)
    }

    @Test
    fun `duplicate or unreadable subscription ids are none`() {
        assertEquals(
            IosCellPlan.None,
            plan(
                listOf(sig(0, 11, 3), sig(1, 11, 2)),
                slots = listOf(slot(11), slot(22))
            )
        )
        assertEquals(
            IosCellPlan.None,
            plan(
                listOf(sig(0, 11, 3), sig(1, 0, 2)),
                slots = listOf(slot(11), slot(0))
            )
        )
    }

    @Test
    fun `three active subscriptions are none`() {
        assertEquals(
            IosCellPlan.None,
            plan(
                listOf(sig(0, 11, 3), sig(1, 22, 2), sig(2, 33, 1)),
                slots = listOf(slot(11), slot(22), slot(33))
            )
        )
    }

    @Test
    fun `two sims with one slot gone draw a single over the visible slot`() {
        val otherVisible = single(
            plan(twoSims, defaultSubId = 22, slots = listOf(slot(11), slot(22, visible = false)))
        )
        assertEquals(0, otherVisible.slot)
        assertEquals(11, otherVisible.subId)
        assertEquals(3f, otherVisible.level, 0.0001f)

        val dataVisible = single(
            plan(twoSims, defaultSubId = 22, slots = listOf(slot(11, visible = false), slot(22)))
        )
        assertEquals(1, dataVisible.slot)
        assertEquals(22, dataVisible.subId)
        assertEquals(1f, dataVisible.level, 0.0001f)
    }

    @Test
    fun `two sims with both slots gone are none`() {
        assertEquals(
            IosCellPlan.None,
            plan(twoSims, slots = listOf(slot(11, visible = false), slot(22, visible = false)))
        )
    }

    @Test
    fun `one two one sim sequence keeps the same slots`() {
        val slots = listOf(slot(11), slot(22))

        val first = single(plan(listOf(sig(0, 11, 2)), slots = slots))
        assertEquals(0, first.slot)
        assertEquals(11, first.subId)
        assertEquals(2f, first.level, 0.0001f)

        val both = dual(plan(twoSims, defaultSubId = 99, slots = slots))
        assertEquals(0, both.dataSlot)
        assertEquals(1, both.otherSlot)
        assertEquals(3f, both.bars, 0.0001f)
        assertEquals(1f, both.dots, 0.0001f)

        val again = single(plan(listOf(sig(0, 11, 4)), slots = slots))
        assertEquals(0, again.slot)
        assertEquals(11, again.subId)
        assertEquals(4f, again.level, 0.0001f)
    }

    @Test
    fun `cell visual is single data or dual both and none is null`() {
        val data = IosMapping.cellVisual(IosCellPlan.Single(0, 11, 3f), tint)!!
        assertEquals(IosPart.CELL, data.part)
        assertEquals(IosCellMode.SINGLE, data.cellMode)
        assertEquals(IosCellRole.DATA, data.cellRole)
        assertEquals(3f, data.cellBars, 0.0001f)
        assertEquals(0f, data.cellDots, 0.0001f)
        assertEquals(tint, data.tint)

        val both = IosMapping.cellVisual(IosCellPlan.Dual(0, 1, 11, 22, 3f, 2f), tint)!!
        assertEquals(IosCellMode.DUAL, both.cellMode)
        assertEquals(IosCellRole.BOTH, both.cellRole)
        assertEquals(3f, both.cellBars, 0.0001f)
        assertEquals(2f, both.cellDots, 0.0001f)

        assertNull(IosMapping.cellVisual(IosCellPlan.None, tint))
    }
}
