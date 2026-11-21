package io.github.kvmy666.duostatusbar.hook

import android.os.BatteryManager

/** One stock mobile slot in strip order: its readable subscription id and whether it is drawable. */
internal data class CellSlot(val subId: Int, val visible: Boolean)

/**
 * The cellular drawing decision for one strip.
 *
 * Pure and view-free: [None] leaves every stock mobile icon alone, [Single] draws one SIM over one
 * slot, and [Dual] draws the stacked block over both slots with one element.
 */
internal sealed class IosCellPlan {
    object None : IosCellPlan()
    data class Single(val slot: Int, val subId: Int, val level: Float) : IosCellPlan()
    data class Dual(
        val dataSlot: Int,
        val otherSlot: Int,
        val dataSubId: Int,
        val otherSubId: Int,
        val bars: Float,
        val dots: Float
    ) : IosCellPlan()
}

/**
 * The state -> [IosVisual] mapping for the iOS status-bar mode.
 *
 * Pure and unit-tested (`IosMappingTest`), like [SignalMapping]: the wire from the system readers to the
 * drawing is where a wrong threshold or colour looks like a working bar until someone notices it. The
 * only Android reference is the `BatteryManager` status constant; no resources, no logging.
 *
 * The stock view supplies slot, bounds, visibility and the SIM id; the state itself always comes from
 * Duo's readers (IOS-INV §3.5), so the iOS glyph agrees with the system.
 */
internal object IosMapping {

    /** Apple's published-in-practice low threshold; the fallback when the system value is unreadable. */
    const val FALLBACK_LOW_THRESHOLD = 20

    const val COLOR_LOW = 0xFFFF3B30.toInt()
    const val COLOR_SAVER = 0xFFFFCC00.toInt()
    const val COLOR_CHARGING = 0xFF34C759.toInt()
    const val COLOR_PLUGGED_NOT_CHARGING = 0xFF8E8E93.toInt()

    /** A resolved low-battery threshold and whether [FALLBACK_LOW_THRESHOLD] supplied it. */
    internal data class LowThreshold(val value: Int, val fallback: Boolean)

    /**
     * The system's own `config_lowBatteryWarningLevel`, validated.
     *
     * [raw] is what the runtime read; a null, negative or above-100 value is not a threshold and maps to
     * [FALLBACK_LOW_THRESHOLD] with `fallback=true`, so a report can say the value was guessed.
     */
    fun lowThreshold(raw: Int?): LowThreshold =
        if (raw == null || raw !in 0..100) {
            LowThreshold(FALLBACK_LOW_THRESHOLD, fallback = true)
        } else {
            LowThreshold(raw, fallback = false)
        }

    /**
     * The iOS battery state. Order matters:
     * plugged-but-not-charging outranks saver (a charge limit draws grey + bolt), charging + saver is
     * yellow + bolt, saver overrides low, and low is `level <= threshold`.
     */
    fun batteryState(
        level: Int,
        lowThreshold: Int,
        saver: Boolean,
        plugged: Boolean,
        status: Int
    ): IosBatteryState = when {
        plugged && status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ->
            IosBatteryState.PLUGGED_NOT_CHARGING
        plugged && saver -> IosBatteryState.CHARGING_SAVER
        plugged -> IosBatteryState.CHARGING
        saver -> IosBatteryState.SAVER
        level <= lowThreshold -> IosBatteryState.LOW
        else -> IosBatteryState.NORMAL
    }

    /** The battery snapshot: level, state, fill colour and bolt. */
    fun battery(
        level: Int,
        lowThreshold: Int,
        saver: Boolean,
        plugged: Boolean,
        status: Int,
        tint: Int
    ): IosVisual {
        val state = batteryState(level, lowThreshold, saver, plugged, status)
        val fill = when (state) {
            IosBatteryState.NORMAL -> tint
            IosBatteryState.LOW -> COLOR_LOW
            IosBatteryState.SAVER, IosBatteryState.CHARGING_SAVER -> COLOR_SAVER
            IosBatteryState.CHARGING -> COLOR_CHARGING
            IosBatteryState.PLUGGED_NOT_CHARGING -> COLOR_PLUGGED_NOT_CHARGING
        }
        val bolt = when (state) {
            IosBatteryState.CHARGING,
            IosBatteryState.CHARGING_SAVER,
            IosBatteryState.PLUGGED_NOT_CHARGING -> 1f
            else -> 0f
        }
        val clampedLevel = level.coerceIn(0, 100)
        return IosVisual(
            part = IosPart.BATTERY,
            visible = true,
            tint = tint,
            batteryLevel = clampedLevel / 100f,
            batteryState = state,
            batteryFillColor = fill,
            boltOpacity = bolt
        )
    }

    /**
     * The Wi-Fi snapshot. Visible only when the radio is on and the active network is validated (M6);
     * a connected-but-unvalidated network is hidden rather than drawn as a working link.
     */
    fun wifi(sample: WifiSample, tint: Int): IosVisual {
        val visible = sample.on && sample.active && sample.validated
        return IosVisual(
            part = IosPart.WIFI,
            visible = visible,
            tint = tint,
            wifiLevel = if (visible) sample.dots.coerceIn(0, 3).toFloat() else 0f
        )
    }

    /**
     * The cellular drawing plan for one strip.
     *
     * The stock views expose only their own SIM id, so a plan is only produced when the slots and the
     * active subscriptions agree exactly; any doubt resolves to [IosCellPlan.None] and the host leaves
     * both stock icons in place (rule 5). [slots] is in strip order and [signals] is ordered by slot.
     */
    fun cellPlan(
        signals: List<SimSignal>,
        defaultSubId: Int,
        slots: List<CellSlot>
    ): IosCellPlan {
        if (slots.isEmpty()) return IosCellPlan.None
        if (signals.isEmpty()) {
            // No active subscription: the single-slot empty-bars row is preserved; a two-slot cluster
            // with no reading is a doubt and stays stock.
            return if (slots.size == 1 && slots[0].visible) {
                IosCellPlan.Single(0, slots[0].subId, 0f)
            } else {
                IosCellPlan.None
            }
        }
        if (signals.any { it.subscriptionId <= 0 }) return IosCellPlan.None
        val active = signals.sortedBy { it.slot }
        if (active.map { it.subscriptionId }.distinct().size != active.size) return IosCellPlan.None

        return when (active.size) {
            1 -> singlePlan(active[0], slots)
            2 -> dualPlan(active, defaultSubId, slots)
            else -> IosCellPlan.None
        }
    }

    private fun singlePlan(only: SimSignal, slots: List<CellSlot>): IosCellPlan {
        val matches = slots.withIndex().filter { it.value.visible && it.value.subId == only.subscriptionId }
        // More than one visible slot claiming the same subscription id is a doubt, not a pick: drawing
        // over the first would leave the second stock icon sitting next to the glyph.
        if (matches.size > 1) return IosCellPlan.None
        if (matches.size == 1) {
            return IosCellPlan.Single(matches[0].index, only.subscriptionId, level(only.level))
        }
        // One unreadable slot is the current fallback: draw the active SIM over it rather than blanking
        // the only slot the bar has.
        if (slots.size == 1 && slots[0].visible && slots[0].subId <= 0) {
            return IosCellPlan.Single(0, 0, level(only.level))
        }
        return IosCellPlan.None
    }

    private fun dualPlan(
        active: List<SimSignal>,
        defaultSubId: Int,
        slots: List<CellSlot>
    ): IosCellPlan {
        if (slots.size != 2) return IosCellPlan.None
        val first = slots[0]
        val second = slots[1]
        if (first.subId <= 0 || second.subId <= 0 || first.subId == second.subId) {
            return IosCellPlan.None
        }
        val activeIds = active.mapTo(HashSet()) { it.subscriptionId }
        if (activeIds != setOf(first.subId, second.subId)) return IosCellPlan.None

        val data = active.firstOrNull { it.subscriptionId == defaultSubId } ?: active.first()
        val other = active.first { it.subscriptionId != data.subscriptionId }
        val dataSlot = slots.indexOfFirst { it.subId == data.subscriptionId }
        val otherSlot = slots.indexOfFirst { it.subId == other.subscriptionId }
        if (dataSlot < 0 || otherSlot < 0) return IosCellPlan.None

        if (first.visible && second.visible) {
            return IosCellPlan.Dual(
                dataSlot = dataSlot,
                otherSlot = otherSlot,
                dataSubId = data.subscriptionId,
                otherSubId = other.subscriptionId,
                bars = level(data.level),
                dots = level(other.level)
            )
        }
        val visible = slots.withIndex().filter { it.value.visible }
        if (visible.size != 1) return IosCellPlan.None
        val index = visible[0].index
        val subscription = when (visible[0].value.subId) {
            data.subscriptionId -> data
            other.subscriptionId -> other
            else -> return IosCellPlan.None
        }
        return IosCellPlan.Single(index, subscription.subscriptionId, level(subscription.level))
    }

    private fun level(raw: Int): Float = raw.coerceIn(0, 4).toFloat()

    /** The visual for a plan. [IosCellPlan.None] draws nothing, so the stock icons stay. */
    fun cellVisual(plan: IosCellPlan, tint: Int): IosVisual? = when (plan) {
        IosCellPlan.None -> null
        is IosCellPlan.Single -> IosVisual(
            part = IosPart.CELL,
            visible = true,
            tint = tint,
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = plan.level,
            cellDots = 0f
        )
        is IosCellPlan.Dual -> IosVisual(
            part = IosPart.CELL,
            visible = true,
            tint = tint,
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellBars = plan.bars,
            cellDots = plan.dots
        )
    }
}
