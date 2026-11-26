package io.github.kvmy666.duostatusbar.hook

import android.os.BatteryManager

/** The user's SIM display choice: automatic, force one row, or force the stacked dual block. */
internal enum class IosSimDisplay { AUTO, SINGLE, DUAL }

/**
 * The live iOS state -> [IosVisual] mapping: the SIM display choice and the battery glyph.
 *
 * Pure and unit-tested (`IosMappingTest`), like [SignalMapping]: the wire from the system readers to the
 * drawing is where a wrong threshold or colour looks like a working bar until someone notices it. The
 * only Android reference is the `BatteryManager` status constant; no resources, no logging.
 *
 * The battery state itself always comes from Duo's readers (IOS-INV §3.5), so the iOS glyph agrees with
 * the system.
 */
internal object IosMapping {

    /**
     * The stored `simDisplay` string; anything unrecognised is [IosSimDisplay.AUTO], so a stale or
     * missing provider value cannot change the automatic decision.
     */
    fun simDisplay(value: String): IosSimDisplay = when (value.trim().lowercase()) {
        "single" -> IosSimDisplay.SINGLE
        "dual" -> IosSimDisplay.DUAL
        else -> IosSimDisplay.AUTO
    }

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
        tint: Int,
        percentInside: Boolean = false
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
            visible = true,
            tint = tint,
            batteryLevel = clampedLevel / 100f,
            batteryState = state,
            batteryFillColor = fill,
            boltOpacity = bolt,
            percentInside = percentInside
        )
    }
}
