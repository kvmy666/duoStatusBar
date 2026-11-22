package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.SystemClock

/**
 * One process-wide snapshot of the Wi-Fi and cellular state for every iOS surface (R3 A4).
 *
 * Each surface used to read its own values: the home bar, the keyguard bar and the shade header all
 * called the platform readers separately, so they could show different levels in the same moment
 * (owner report: the cell showed 1/4 while a fresh read said 3/4). A short TTL makes every surface see
 * the same sample without turning the reads into polling; [invalidate] forces the next read after a
 * broadcast or a panel transition. The reads themselves are still the proven [WifiReader]/[CellReader]
 * paths.
 */
internal object IosReadings {

    /** Long enough to cover the surfaces of one refresh burst, short enough not to hide a real change. */
    const val TTL_MS = 750L

    private class WifiCache(val at: Long, val sample: WifiSample)

    private class CellCache(val at: Long, val reading: CellReading)

    private var generation = 0L
    private var cachedWifi: WifiCache? = null
    private var cachedCell: CellCache? = null

    private var now: () -> Long = { SystemClock.uptimeMillis() }

    private var wifiReader: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
        { context, level, on, active, validated ->
            WifiReader.sample(context, level, on, active, validated)
        }

    private var cellReader: (Context) -> CellReading =
        { context -> CellReader.read(context, airplane = false) }

    /** Drops both snapshots so the next surface reads the current platform state. */
    fun invalidate() {
        generation++
        cachedWifi = null
        cachedCell = null
    }

    /** The shared Wi-Fi sample, read at most once per [TTL_MS]. */
    fun wifi(
        context: Context,
        currentLevel: Int,
        currentOn: Boolean,
        currentActive: Boolean,
        currentValidated: Boolean
    ): WifiSample {
        val at = now()
        cachedWifi?.let { cache ->
            val age = at - cache.at
            if (age in 0 until TTL_MS) return cache.sample
        }
        val sample = wifiReader(context, currentLevel, currentOn, currentActive, currentValidated)
        cachedWifi = WifiCache(at, sample)
        return sample
    }

    /** The shared cellular reading, read at most once per [TTL_MS]. */
    fun cell(context: Context): CellReading {
        val at = now()
        cachedCell?.let { cache ->
            val age = at - cache.at
            if (age in 0 until TTL_MS) return cache.reading
        }
        val reading = cellReader(context)
        cachedCell = CellCache(at, reading)
        return reading
    }

    /** Test seam: how many invalidations have happened. */
    internal fun generationForTest(): Long = generation

    /** Test seam: a clean cache with deterministic readers (and clock). */
    internal fun resetForTest(
        wifi: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
            { context, level, on, active, validated ->
                WifiReader.sample(context, level, on, active, validated)
            },
        cell: (Context) -> CellReading = { context -> CellReader.read(context, airplane = false) },
        clock: () -> Long = { SystemClock.uptimeMillis() }
    ) {
        generation = 0
        cachedWifi = null
        cachedCell = null
        wifiReader = wifi
        cellReader = cell
        now = clock
    }
}
