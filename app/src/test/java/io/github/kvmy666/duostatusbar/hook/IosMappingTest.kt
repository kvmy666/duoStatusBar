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
        slots: List<CellSlot>,
        display: IosSimDisplay = IosSimDisplay.AUTO
    ) = IosMapping.cellPlan(signals, defaultSubId, slots, display)

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
    fun `two readable signals with one unreadable slot stay stock but one active subscription still draws`() {
        // Safest mixed-sub edge: the dual plan needs both slots readable, so both stock icons stay.
        assertEquals(
            IosCellPlan.None,
            plan(twoSims, slots = listOf(slot(11), slot(0)))
        )

        // The same slot list with one active subscription still maps the readable slot and draws it.
        val p = single(plan(listOf(sig(0, 11, 3)), slots = listOf(slot(11), slot(0))))
        assertEquals(0, p.slot)
        assertEquals(11, p.subId)
        assertEquals(3f, p.level, 0.0001f)
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
    fun `sim display string is auto for anything but single or dual`() {
        assertEquals(IosSimDisplay.SINGLE, IosMapping.simDisplay("single"))
        assertEquals(IosSimDisplay.SINGLE, IosMapping.simDisplay(" SINGLE "))
        assertEquals(IosSimDisplay.DUAL, IosMapping.simDisplay("dual"))
        assertEquals(IosSimDisplay.AUTO, IosMapping.simDisplay("auto"))
        assertEquals(IosSimDisplay.AUTO, IosMapping.simDisplay("bogus"))
        assertEquals(IosSimDisplay.AUTO, IosMapping.simDisplay(""))
    }

    @Test
    fun `auto with two active sims draws the stacked dual block`() {
        val p = dual(plan(twoSims, defaultSubId = 22, slots = listOf(slot(11), slot(22))))
        assertEquals(1, p.dataSlot)
        assertEquals(0, p.otherSlot)
    }

    @Test
    fun `forced single with two active sims picks the data slot and hides the other`() {
        val p = single(
            plan(
                twoSims,
                defaultSubId = 22,
                slots = listOf(slot(11), slot(22)),
                display = IosSimDisplay.SINGLE
            )
        )
        assertEquals(1, p.slot)
        assertEquals(22, p.subId)
        assertEquals(1f, p.level, 0.0001f)
        assertEquals(0, p.hiddenSlot)
    }

    @Test
    fun `forced single with one visible slot draws the visible data slot`() {
        // The other SIM's slot is OEM-hidden, so there is no icon to hide: the cellular glyph must
        // still draw on the visible data slot instead of disappearing with the stock row.
        val sims = listOf(sig(slot = 0, sub = 1, level = 3), sig(slot = 1, sub = 2, level = 2))
        val p = single(
            plan(
                sims,
                defaultSubId = 1,
                slots = listOf(CellSlot(1, true), CellSlot(2, false)),
                display = IosSimDisplay.SINGLE
            )
        )
        assertEquals(0, p.slot)
        assertEquals(1, p.subId)
        assertEquals(3f, p.level, 0.0001f)
        assertNull("the hidden slot needs no hide", p.hiddenSlot)
    }

    @Test
    fun `forced single with the data slot OEM hidden is none`() {
        // Drawing nothing is the only safe plan when the data SIM's own slot is invisible.
        val sims = listOf(sig(slot = 0, sub = 1, level = 3), sig(slot = 1, sub = 2, level = 2))
        assertEquals(
            IosCellPlan.None,
            plan(
                sims,
                defaultSubId = 2,
                slots = listOf(CellSlot(1, true), CellSlot(2, false)),
                display = IosSimDisplay.SINGLE
            )
        )
    }

    @Test
    fun `forced single falls back to the first active sim when the default is unknown`() {
        val p = single(
            plan(
                twoSims,
                defaultSubId = 99,
                slots = listOf(slot(11), slot(22)),
                display = IosSimDisplay.SINGLE
            )
        )
        assertEquals(0, p.slot)
        assertEquals(11, p.subId)
        assertEquals(1, p.hiddenSlot)
    }

    @Test
    fun `forced single with an unmappable data sim is none`() {
        assertEquals(
            IosCellPlan.None,
            plan(
                twoSims,
                defaultSubId = 22,
                slots = listOf(slot(11), slot(33)),
                display = IosSimDisplay.SINGLE
            )
        )
    }

    @Test
    fun `forced dual with two active sims keeps the stacked block`() {
        val p = dual(
            plan(
                twoSims,
                defaultSubId = 11,
                slots = listOf(slot(11), slot(22)),
                display = IosSimDisplay.DUAL
            )
        )
        assertEquals(0, p.dataSlot)
        assertEquals(1, p.otherSlot)
    }

    @Test
    fun `forced dual with one active sim falls back to a single row`() {
        val p = single(
            plan(
                listOf(sig(0, 11, 2)),
                slots = listOf(slot(11), slot(22)),
                display = IosSimDisplay.DUAL
            )
        )
        assertEquals(0, p.slot)
        assertEquals(11, p.subId)
        assertNull(p.hiddenSlot)
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

    // ------------------------------------------------------------------- unavailable per-SIM samples

    private fun cellSample(
        slot: Int,
        subId: Int,
        raw: Int,
        dots: Int,
        src: String = SignalMapping.SRC_PER_SUB
    ) = CellSample(
        slot = slot,
        subscriptionId = subId,
        raw = raw,
        count = 5,
        maxIndex = SignalMapping.cellMaxIndex(raw, 5),
        rat = "Lte",
        dbm = -95,
        metric = "rsrp",
        dots = dots,
        src = src,
        airplane = false
    )

    @Test
    fun `an unavailable sample vetoes the plan instead of drawing its fallback`() {
        // sub 11 failed, so its one-dot level is only the caller's fallback; sub 22 read fine.
        val samples = listOf(
            cellSample(0, 11, raw = -1, dots = 1, src = SignalMapping.SRC_UNAVAILABLE),
            cellSample(1, 22, raw = 3, dots = 3)
        )
        val signals = samples.map { SimSignal(it.slot, it.subscriptionId, it.dots) }
        val slots = listOf(slot(11), slot(22))

        assertEquals(
            IosCellPlan.None,
            IosMapping.cellPlan(signals, defaultSubId = 11, slots = slots, samples = samples)
        )
        // Forced single draws the data SIM (11), which is the unavailable one: stay stock.
        assertEquals(
            IosCellPlan.None,
            IosMapping.cellPlan(
                signals,
                defaultSubId = 11,
                slots = slots,
                display = IosSimDisplay.SINGLE,
                samples = samples
            )
        )
    }

    @Test
    fun `an unavailable single sim stays stock`() {
        val samples = listOf(
            cellSample(0, 7, raw = -1, dots = 1, src = SignalMapping.SRC_UNAVAILABLE)
        )
        val signals = samples.map { SimSignal(it.slot, it.subscriptionId, it.dots) }
        assertEquals(
            IosCellPlan.None,
            IosMapping.cellPlan(signals, defaultSubId = 7, slots = listOf(slot(7)), samples = samples)
        )
    }

    @Test
    fun `an unreadable slot with an unavailable sample stays stock`() {
        // The stock slot reports sub id 0, so the plan falls back to the slot itself. The sample is
        // the per-sub evidence behind that slot; an unavailable one must veto the fallback (sub id 0
        // must not be looked up as if it were a real subscription).
        val samples = listOf(
            cellSample(0, 7, raw = -1, dots = 0, src = SignalMapping.SRC_UNAVAILABLE)
        )
        val signals = listOf(SimSignal(slot = 0, subscriptionId = 7, level = 0))
        assertEquals(
            IosCellPlan.None,
            IosMapping.cellPlan(
                signals,
                defaultSubId = 7,
                slots = listOf(CellSlot(subId = 0, visible = true)),
                samples = samples
            )
        )
    }

    @Test
    fun `an unreadable slot with an available sample keeps its plan`() {
        val samples = listOf(cellSample(0, 7, raw = 2, dots = 2))
        val signals = listOf(SimSignal(slot = 0, subscriptionId = 7, level = 2))
        val p = single(
            IosMapping.cellPlan(
                signals,
                defaultSubId = 7,
                slots = listOf(CellSlot(subId = 0, visible = true)),
                samples = samples
            )
        )
        assertEquals(0, p.slot)
        assertEquals(0, p.subId)
        // R4A item 2: the plan draws the sample's iOS level (raw 2 of 5 -> 3 dots), not its Duo dots.
        assertEquals(3f, p.level, 0.0001f)
    }

    @Test
    fun `a dual plan is vetoed when either subscription sample is unavailable`() {
        val slots = listOf(slot(11), slot(22))
        for (failedSub in listOf(11, 22)) {
            val samples = listOf(
                cellSample(
                    0,
                    11,
                    raw = if (failedSub == 11) -1 else 2,
                    dots = if (failedSub == 11) 0 else 2,
                    src = if (failedSub == 11) {
                        SignalMapping.SRC_UNAVAILABLE
                    } else {
                        SignalMapping.SRC_PER_SUB
                    }
                ),
                cellSample(
                    1,
                    22,
                    raw = if (failedSub == 22) -1 else 3,
                    dots = if (failedSub == 22) 0 else 3,
                    src = if (failedSub == 22) {
                        SignalMapping.SRC_UNAVAILABLE
                    } else {
                        SignalMapping.SRC_PER_SUB
                    }
                )
            )
            val signals = samples.map { SimSignal(it.slot, it.subscriptionId, it.dots) }
            assertEquals(
                "sub $failedSub unavailable must stay stock",
                IosCellPlan.None,
                IosMapping.cellPlan(signals, defaultSubId = 11, slots = slots, samples = samples)
            )
        }
    }

    @Test
    fun `a complete per-subscription reading produces the expected plan`() {
        val samples = listOf(
            cellSample(0, 11, raw = 2, dots = 2),
            cellSample(1, 22, raw = 3, dots = 3)
        )
        val signals = samples.map { SimSignal(it.slot, it.subscriptionId, it.dots) }
        val p = dual(
            IosMapping.cellPlan(
                signals,
                defaultSubId = 22,
                slots = listOf(slot(11), slot(22)),
                samples = samples
            )
        )
        assertEquals(1, p.dataSlot)
        assertEquals(0, p.otherSlot)
        assertEquals(22, p.dataSubId)
        assertEquals(11, p.otherSubId)
        // R4A item 2: each row uses its own sample. Data (sub 22, raw 3/5) is full; other (raw 2/5) is 3.
        assertEquals(4f, p.bars, 0.0001f)
        assertEquals(3f, p.dots, 0.0001f)

        val one = single(
            IosMapping.cellPlan(
                signals.take(1),
                defaultSubId = 11,
                slots = listOf(slot(11)),
                samples = samples.take(1)
            )
        )
        assertEquals(11, one.subId)
        // R4A item 2: raw 2 of 5 maps to 3 iOS dots (it used to draw the Duo 2).
        assertEquals(3f, one.level, 0.0001f)
    }

    // ---------------------------------------------------------------------------- Wi-Fi slot state

    @Test
    fun `wifi slot chooses wifi first then mobile data then none`() {
        assertEquals(IosWifiSlot.Wifi, IosMapping.wifiSlotState(true, true, "5G"))
        assertEquals(IosWifiSlot.Wifi, IosMapping.wifiSlotState(true, false, ""))
        assertEquals(IosWifiSlot.DataType("5G"), IosMapping.wifiSlotState(false, true, "5G"))
        assertEquals(IosWifiSlot.None, IosMapping.wifiSlotState(false, false, "5G"))
        assertEquals(IosWifiSlot.None, IosMapping.wifiSlotState(false, true, ""))
    }
}
