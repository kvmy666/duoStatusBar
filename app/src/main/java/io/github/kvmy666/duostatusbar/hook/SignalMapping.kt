package io.github.kvmy666.duostatusbar.hook

/**
 * The Wi-Fi/cellular level mapping and the middle-slot occupant choice.
 *
 * Pure and unit-tested (`DuoMappingTest`), so a change here is caught off-device.
 */
/** One active subscription's slot, id and already-mapped four-dot level. */
data class SimSignal(val slot: Int, val subscriptionId: Int, val level: Int)

/** One active subscription's raw platform signal and the four-dot result Duo draws. */
data class CellSample(
    val slot: Int,
    val subscriptionId: Int,
    /** `SignalStrength.level` before any Duo mapping; -1 when the read failed. */
    val raw: Int,
    /** `CellSignalStrength.getNumSignalStrengthLevels()`, a count, never a raw value. */
    val count: Int,
    /** Highest legal raw value on this source, derived from [raw] and [count]. */
    val maxIndex: Int,
    val rat: String,
    val dbm: Int,
    val metric: String,
    val dots: Int,
    /** `per-sub`, `unavailable`; the source of this sample. */
    val src: String,
    val airplane: Boolean
)

/** The subscription the drawn cellular level came from, and whether it fell back. */
data class CellSelection(
    val dots: Int,
    val slot: Int,
    val subscriptionId: Int,
    /** `per-sub`, `default`, `stale` or `fallback`. */
    val src: String,
    val fallback: Boolean
)

/**
 * One refresh's cellular reads: a sample per active subscription plus the default line.
 *
 * The default line is the platform's aggregate `SignalStrength` (the one `TelephonyManager` returns
 * without a subscription), which is what `auto` uses. Both are read from the same snapshot so the
 * report cannot show levels from two different moments.
 */
data class CellReading(
    val samples: List<CellSample>,
    val count: Int,
    val defaultRaw: Int,
    val defaultMaxIndex: Int,
    val defaultDots: Int,
    val defaultSub: Int,
    val defaultSrc: String,
    val airplane: Boolean
) {
    val slots: List<Int> get() = samples.map { it.slot }
    val subs: List<Int> get() = samples.map { it.subscriptionId }
}

object SignalMapping {

    const val SRC_PER_SUB = "per-sub"
    const val SRC_DEFAULT = "default"
    const val SRC_STALE = "stale"
    const val SRC_UNAVAILABLE = "unavailable"
    const val SRC_FALLBACK = "fallback"

    /**
     * Maps the phone's Wi-Fi bar count onto Duo's three levels, so the ring agrees with the stock icon.
     *
     * The stock icon has four bars (1..4); Duo draws two arcs plus the dot (1..3). The user's mapping,
     * measured against the real icon: 1 and 2 bars -> 1, 3 bars -> 2, 4 bars -> 3. 0 is "no signal".
     */
    fun wifiBars(stockLevel: Int): Int = when {
        stockLevel <= 0 -> 0
        stockLevel <= 2 -> 1
        stockLevel == 3 -> 2
        else -> 3
    }

    /**
     * The stock bar count the reader feeds to [wifiBars].
     *
     * `WifiManager.calculateSignalLevel` uses AOSP's five-step scale and reports one bar fewer than
     * the OxygenOS icon (measured: stock 3 bars read as 2). The shift is pinned separately from the
     * platform call so its effect is visible in tests and in the `wifi sample` line.
     */
    fun stockWifiBars(calc5: Int): Int =
        if (calc5 <= 0) 0 else (calc5 + 1).coerceIn(1, 4)

    /**
     * Wi-Fi layers, bottom-up: nothing connected dims everything, then the dot, the middle arc and
     * finally the outer arc appear as the signal improves (FR-25).
     */
    fun wifiOpacities(level: Int): Triple<Float, Float, Float> = when (level.coerceIn(0, 3)) {
        0 -> Triple(0.3f, 0.3f, 0.3f)
        1 -> Triple(0.3f, 0.3f, 1f)
        2 -> Triple(0.3f, 1f, 1f)
        else -> Triple(1f, 1f, 1f)
    }

    /**
     * Maps a raw `SignalStrength.level` to the four spheres Duo draws.
     *
     * [stockLevels] is `CellSignalStrength.getNumSignalStrengthLevels()`, a **count**, while the
     * platform's raw value is an **index**. On a 5-level source the highest raw is 4; on a 4-level
     * source it is 3. The count is therefore reduced to the highest legal index before scaling, so a
     * full 4-level source lights four dots instead of three. If the caller's raw has already reached
     * the count (a bar count on 0..count, not an index), the count is the safe top instead.
     */
    /**
     * Maps a raw `SignalStrength.level` to the four spheres Duo draws.
     *
     * [hasService] distinguishes "no service" from a weak-but-present bar: a platform level of 0 is
     * the first bar when the radio is registered, not the absence of service. Only an unregistered
     * radio (or an explicit no-service/airplane state) yields 0. The level is first converted to a
     * bar count (raw + 1, capped at [stockLevels]) and then mapped through [cellDotsForBars].
     */
    fun cellDots(rawLevel: Int, stockLevels: Int, hasService: Boolean = true): Int {
        if (!hasService || rawLevel < 0) return 0
        val count = stockLevels.coerceAtLeast(1)
        val bars = if (rawLevel >= count) count else rawLevel + 1
        return cellDotsForBars(bars.coerceIn(0, count), count)
    }

    /** Backwards-compatible two-argument form; assumes the radio is in service. */
    fun cellDots(rawLevel: Int, stockLevels: Int): Int =
        cellDots(rawLevel, stockLevels, true)

    /**
     * Pure mapping from a bar count (0..[levels]) onto Duo's four dots.
     *
     * Owner's 5-bar table: bars 0..5 -> [0, 1, 1, 2, 3, 4] (bar 1 and bar 2 share dot 1; there is
     * no fifth dot). On any other level count the 4-level-or-other fallback maps bars to dots with
     * nearest rounding: bar 1 always lights at least one dot so a present-but-weak signal is never
     * drawn as "no service".
     */
    fun cellDotsForBars(bars: Int, levels: Int): Int {
        val lvl = levels.coerceAtLeast(1)
        val b = bars.coerceIn(0, lvl)
        if (b <= 0) return 0
        if (lvl == 5) {
            // Owner's table for bars 0..5.
            return listOf(0, 1, 1, 2, 3, 4)[b]
        }
        val raw = b - 1
        val maxIndex = (lvl - 1).coerceAtLeast(1)
        if (raw <= 0) return 1
        return ((4 * raw + maxIndex / 2) / maxIndex).coerceIn(1, 4)
    }

    /** Highest legal raw for [stockLevels]' count, given the raw value actually observed. */
    fun cellMaxIndex(rawLevel: Int, stockLevels: Int): Int {
        val count = stockLevels.coerceAtLeast(1)
        return if (rawLevel >= count) count else maxOf(count - 1, 1)
    }

    /**
     * Round-to-nearest mapping from a raw index onto Duo's four dots.
     *
     * The top of the scale always lands on 4 (`(4 * maxIndex + maxIndex / 2) / maxIndex`), so a full
     * stock bar can never read as 1 or 3. Zero means no signal; every non-zero level lights at least
     * one dot. `maxIndex` is clamped to 1 so a broken profile cannot divide by zero.
     */
    fun cellDotsIndexed(rawLevel: Int, maxIndex: Int): Int {
        if (rawLevel <= 0) return 0
        val top = maxIndex.coerceAtLeast(1)
        return ((4 * rawLevel + top / 2) / top).coerceIn(1, 4)
    }

    /**
     * Chooses the dots for the user's SIM setting. `sim1`/`sim2` are *slot* selections, not list
     * positions: a phone can have only slot 2 active, and the active-subscription list would then be
     * one entry. A missing slot keeps [defaultLevel] (the platform's default data line) instead of
     * silently showing the other SIM's bars.
     */
    fun selectedCellLevel(choice: String, signals: List<SimSignal>, defaultLevel: Int): Int =
        when (choice) {
            "sim1" -> signals.firstOrNull { it.slot == 0 }?.level ?: defaultLevel
            "sim2" -> signals.firstOrNull { it.slot == 1 }?.level ?: defaultLevel
            else -> defaultLevel
        }

    /**
     * The SIM-setting selection with its provenance. Unlike [selectedCellLevel] this keeps the slot,
     * subscription id and source, so a report can show which line produced the drawn dots.
     *
     * A missing slot falls back to [defaultDots] with `slot=-1/sub=-1`. An unreadable slot keeps its
     * identity but is marked `fallback`, never silently reported as a per-subscription read.
     */
    fun selectCell(
        choice: String,
        samples: List<CellSample>,
        defaultDots: Int,
        defaultSub: Int,
        defaultSrc: String = SRC_DEFAULT
    ): CellSelection {
        val wantedSlot = when (choice) {
            "sim1" -> 0
            "sim2" -> 1
            else -> null
        }
        if (wantedSlot == null) {
            return CellSelection(defaultDots, -1, defaultSub, defaultSrc, fallback = false)
        }
        val sample = samples.firstOrNull { it.slot == wantedSlot }
            ?: return CellSelection(defaultDots, -1, -1, SRC_FALLBACK, fallback = true)
        if (sample.src == SRC_UNAVAILABLE) {
            return CellSelection(
                defaultDots,
                sample.slot,
                sample.subscriptionId,
                SRC_FALLBACK,
                fallback = true
            )
        }
        return CellSelection(sample.dots, sample.slot, sample.subscriptionId, sample.src, fallback = false)
    }

    /** One bounded diagnostic line per active subscription, emitted after the legacy line. */
    fun cellSampleLine(sample: CellSample): String =
        "cell sample sub=${sample.subscriptionId} slot=${sample.slot} raw=${sample.raw}" +
            " count=${sample.count} maxIndex=${sample.maxIndex} rat=${sample.rat}" +
            " dbm=${sample.dbm} metric=${sample.metric} dots=${sample.dots}" +
            " src=${sample.src} airplane=${sample.airplane}"

    /** One selection line per refresh; [choice] is the user's `auto`/`sim1`/`sim2` setting. */
    fun cellPickLine(choice: String, selection: CellSelection, defaultSub: Int): String =
        "cell pick sim=${choice.ifEmpty { "auto" }} slot=${selection.slot}" +
            " sub=${selection.subscriptionId} dots=${selection.dots} src=${selection.src}" +
            " fallback=${selection.fallback} defaultSub=$defaultSub"

    /** Four cellular spheres; `level` 0..4 spheres lit. */
    fun cellOpacities(level: Int): List<Float> {
        val n = level.coerceIn(0, 4)
        return (1..4).map { if (it <= n) 1f else 0.3f }
    }

    /** Four spheres, fully gone. Used when they have nothing left to report. */
    private val HIDDEN_SPHERES = listOf(0f, 0f, 0f, 0f)

    /**
     * What the four spheres under the ring should show.
     *
     * With [wifiDots] off, they are the cellular bars, dimmed to empty while airplane mode is on.
     * With it on they stay cellular whenever airplane mode is off. Airplane mode hands them to
     * Wi-Fi while that radio is on, and removes them when Wi-Fi is off too.
     */
    fun sphereOpacities(
        cellLevel: Int,
        wifiLevel: Int,
        airplane: Boolean,
        wifiOn: Boolean,
        wifiDots: Boolean
    ): List<Float> = when {
        !wifiDots || !airplane -> cellOpacities(if (airplane) 0 else cellLevel)
        wifiOn -> cellOpacities(wifiAsSpheres(wifiLevel))
        else -> HIDDEN_SPHERES
    }

    /**
     * Duo's Wi-Fi level (0..3) back onto four spheres.
     *
     * The glyph compresses the stock four-bar icon (1–2 bars → 1, 3 → 2, 4 → 3). The spheres have
     * a place for each stock bar, so each compressed step lands on the stronger bar it stands for.
     */
    private fun wifiAsSpheres(wifiLevel: Int): Int = when (wifiLevel.coerceIn(0, 3)) {
        0 -> 0
        1 -> 2
        2 -> 3
        else -> 4
    }

    /** The middle slot's occupants, in the order the state machine expects (see `scene.rml`). */
    const val MIDDLE_OFF = 0
    const val MIDDLE_WIFI = 1
    const val MIDDLE_AIRPLANE = 2
    const val MIDDLE_DND = 3

    /** The cellular generation shown when Wi-Fi is off (FR-06): "5G"/"4G"/"3G"/"2G". */
    const val MIDDLE_NETWORK = 4

    /**
     * FR-06/FR-16: airplane wins the slot, then DND, then a **connected** Wi-Fi, then the cellular
     * generation. [wifiConnected] is false when the radio is on but there is no network through it, in
     * which case the phone is on mobile data and the generation is the honest thing to show.
     *
     * [showAirplane] and [showDnd] are the user's choices. A status that is off is skipped, so the
     * slot stays on Wi-Fi or the generation. Wi-Fi and the generation are always eligible.
     */
    fun middleMode(
        airplane: Boolean,
        dnd: Boolean,
        wifiOn: Boolean = true,
        hasNetwork: Boolean = false,
        wifiConnected: Boolean = true,
        showAirplane: Boolean = true,
        showDnd: Boolean = true
    ): Int = when {
        showAirplane && airplane -> MIDDLE_AIRPLANE
        showDnd && dnd -> MIDDLE_DND
        wifiOn && wifiConnected -> MIDDLE_WIFI
        hasNetwork -> MIDDLE_NETWORK
        // Wi-Fi on but not connected, and no generation to name: keep the dim glyph rather than empty.
        wifiOn -> MIDDLE_WIFI
        else -> MIDDLE_OFF
    }

    /**
     * The cellular generation label for a `TelephonyManager.NETWORK_TYPE_*` value.
     *
     * Android-free on purpose: the type constants are spelled out as literals so this stays in the
     * pure, unit-tested half. Unknown or Wi-Fi-calling types map to empty, which leaves the slot off
     * rather than showing a label that means nothing.
     *
     * [dataEnabled] is the user's mobile-data switch. Voice service stays registered after that switch
     * is turned off, so the radio type would still read 5G/4G; the slot names the data path, and with
     * data off there is nothing to name.
     */
    fun networkGeneration(type: Int, nrConnected: Boolean = false, dataEnabled: Boolean = true): String {
        if (!dataEnabled) return ""
        // The phone's radio reports NR as connected. On 5G NSA the data network type is still LTE, so
        // this is the only honest 5G signal - it is what the stock OxygenOS bar reads (see
        // `OplusMobileSignalExImpl`: `ServiceState.getNrState()` 2 or 3 means 5G).
        if (nrConnected) return "5G"
        return when (type) {
            // NETWORK_TYPE_NR
            20 -> "5G"
            // NETWORK_TYPE_LTE, NETWORK_TYPE_LTE_CA
            13, 19 -> "4G"
            // UMTS, EVDO_0/A, HSDPA, HSUPA, HSPA, EVDO_B, EHRPD, HSPAP, TD_SCDMA
            3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
            // GPRS, EDGE, CDMA, 1xRTT, IDEN, GSM
            1, 2, 4, 7, 11, 16 -> "2G"
            else -> ""
        }
    }
}
