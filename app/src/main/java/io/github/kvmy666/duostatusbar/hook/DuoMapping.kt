package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * The single entry point the drawing and the monitor have always used, kept as a thin facade so every
 * existing caller and test keeps compiling unchanged.
 *
 * The maths itself now lives next to what it belongs to: [RingGeometry] (arc/trim/gap), [SignalMapping]
 * (Wi-Fi/cellular levels and the middle-slot occupant) and [Colors] (palette and battery tint).
 */
object DuoMapping {

    // Ring geometry - see RingGeometry.
    const val LEFT_START = RingGeometry.LEFT_START
    const val RIGHT_FULL = RingGeometry.RIGHT_FULL
    const val GAP_PERCENT_DEG = RingGeometry.GAP_PERCENT_DEG
    const val GAP_CHARGING_DEG = RingGeometry.GAP_CHARGING_DEG
    val RIGHT_START = RingGeometry.RIGHT_START
    val LEFT_FULL = RingGeometry.LEFT_FULL
    const val DRAWN_ARC = RingGeometry.DRAWN_ARC

    fun gapClosed(charging: Boolean, showPercent: Boolean): Boolean =
        RingGeometry.gapClosed(charging, showPercent)

    fun halfArc(charging: Boolean): Float = RingGeometry.halfArc(charging)

    fun leftArc(charging: Boolean, showPercent: Boolean): Float =
        RingGeometry.leftArc(charging, showPercent)

    fun rightArc(charging: Boolean, showPercent: Boolean): Float =
        RingGeometry.rightArc(charging, showPercent)

    fun trimLeft(level: Int, charging: Boolean, closed: Boolean): Float =
        RingGeometry.trimLeft(level, charging, closed)

    fun trimRight(level: Int, charging: Boolean, closed: Boolean): Float =
        RingGeometry.trimRight(level, charging, closed)

    fun percentFontSize(text: String): Float = RingGeometry.percentFontSize(text)

    // Palette - see Colors.
    const val GREEN_CHARGING = Colors.GREEN_CHARGING
    const val YELLOW_SAVER = Colors.YELLOW_SAVER
    const val RED_CRITICAL = Colors.RED_CRITICAL
    const val WHITE = Colors.WHITE
    const val BLACK = Colors.BLACK

    fun tint(level: Int, charging: Boolean, saver: Boolean, base: Int = WHITE): Int =
        Colors.tint(level, charging, saver, base)

    // Signal levels and the middle slot - see SignalMapping.
    const val MIDDLE_OFF = SignalMapping.MIDDLE_OFF
    const val MIDDLE_WIFI = SignalMapping.MIDDLE_WIFI
    const val MIDDLE_AIRPLANE = SignalMapping.MIDDLE_AIRPLANE
    const val MIDDLE_DND = SignalMapping.MIDDLE_DND
    const val MIDDLE_NETWORK = SignalMapping.MIDDLE_NETWORK

    fun wifiBars(stockLevel: Int): Int = SignalMapping.wifiBars(stockLevel)

    fun wifiOpacities(level: Int): Triple<Float, Float, Float> = SignalMapping.wifiOpacities(level)

    fun cellOpacities(level: Int): List<Float> = SignalMapping.cellOpacities(level)

    fun middleMode(
        airplane: Boolean,
        dnd: Boolean,
        wifiOn: Boolean = true,
        hasNetwork: Boolean = false,
        wifiConnected: Boolean = true,
        showAirplane: Boolean = true,
        showDnd: Boolean = true
    ): Int = SignalMapping.middleMode(
        airplane, dnd, wifiOn, hasNetwork, wifiConnected, showAirplane, showDnd
    )

    fun networkGeneration(type: Int, nrConnected: Boolean = false, dataEnabled: Boolean = true): String =
        SignalMapping.networkGeneration(type, nrConnected, dataEnabled)

    fun visual(
        level: Int,
        charging: Boolean,
        saver: Boolean,
        showPercent: Boolean,
        wifiLevel: Int,
        cellLevel: Int,
        airplane: Boolean,
        dnd: Boolean = false,
        fgColor: Int = WHITE,
        visible: Boolean = true,
        /** Whether the Wi-Fi radio is on. Off hands the middle slot to the cellular generation. */
        wifiOn: Boolean = true,
        /**
         * Whether Wi-Fi is the network actually carrying data. False (connected but no internet, or
         * radio off) hands the slot to the cellular generation, so the element follows the active path
         * rather than showing a Wi-Fi glyph while the user is on mobile data.
         */
        wifiConnected: Boolean = wifiLevel > 0,
        /** The cellular generation, e.g. "5G"; ignored unless the slot is actually holding it. */
        networkText: String = "",
        /** Whether the charging journey plays; false shows the bolt instantly. */
        animateCharge: Boolean = true,
        /**
         * Whether the charging bolt is drawn at all. False removes the bolt (and lets the percentage
         * take the top gap again) while the ring still tints green for the charging battery.
         */
        showCharging: Boolean = true,
        /** Whether Airplane mode may take the middle slot. Off leaves that place to the network. */
        showAirplane: Boolean = true,
        /** Whether Do Not Disturb may take the middle slot. Off leaves that place to the network. */
        showDnd: Boolean = true,
        /**
         * When true and [dnd] is on, each strong signal bar fills its gray dot into the white
         * crescent. A weak bar stays the gray circle. The middle slot stays with Wi-Fi, airplane,
         * or the generation. If the row is hidden entirely, one moon remains in the middle of it.
         */
        dndDots: Boolean = false,
        /**
         * Where the percentage sits: 0 is the original top-gap seat, 100 is fully raised so a center
         * punch-hole does not cut through the digits. The ring does not move.
         */
        percentHeight: Int = 100,
        /**
         * When true, airplane mode turns the four spheres into Wi-Fi strength, and they disappear
         * if Wi-Fi is off too. Out of airplane mode they stay the cellular bars.
         */
        wifiDots: Boolean = false,
        /**
         * Download progress painted on the white ring. [DownloadWash.IDLE] (the default) draws
         * nothing, so a build with no download looks exactly as it did before.
         */
        download: DownloadWash = DownloadWash.IDLE,
        /**
         * Download progress to draw in the battery-percentage seat, or null to leave that seat to
         * the battery. A number opens the gap when the battery percentage is off, and replaces the
         * battery number when it is on. Only used while [downloadActive]; an active download beats
         * charging, so a visible charging bolt does not take the seat back.
         */
        downloadPercent: Int? = null,
        /**
         * Whether a download currently owns the ring. When true it overrides the charging visual:
         * no bolt, no charge animation, no charging tint, and the download number/wash take their
         * places instead. False is the ordinary battery ring, so a completed download returns to
         * whatever the battery was doing.
         */
        downloadActive: Boolean = false,
        /** User's choice of what occupies the middle slot; "auto" keeps the existing behaviour. */
        middleSlot: String = DuoPrefs.MIDDLE_SLOT_AUTO,
        /** The clock text shown when [middleSlot] is "clock"; empty means the slot is off. */
        clockText: String = "",
        /** Whether the Wi-Fi glyph inside the ring is drawn at all. */
        showWifiGlyph: Boolean = true,
        /** Whether the four cellular dots inside the ring are drawn at all. */
        showCellDots: Boolean = true,
        /** Rive event type id (0 = none). */
        eventType: Int = 0,
        /** Whether a live event is currently visible. */
        eventVisible: Boolean = false
    ): DuoVisual {
        // The middle slot holds exactly one occupant (FR-06/FR-16): airplane wins, then DND, then
        // Wi-Fi; with Wi-Fi off the slot shows the cellular generation instead. The hand-over itself
        // - the arcs collapsing, the dot fading, the new occupant growing out of it - is the
        // MiddleSlot layer's job, so all the host says is which occupant it should be.
        // Wi-Fi only owns the slot when it is the active data path: a connected-but-internet-less AP
        // still leaves the phone on mobile data, so the slot shows the cellular generation instead of a
        // Wi-Fi glyph while the user is plainly on 4G/5G (user-reported).
        // Dots mode and the middle moon are different places for the same status. If both are
        // asked for, the dots win and the middle stays on the connection.
        val mode = SignalMapping.middleMode(
            airplane, dnd, wifiOn, networkText.isNotEmpty(), wifiConnected,
            showAirplane, showDnd && !dndDots
        )
        val chosenMode = when (DuoPrefs.normalizeMiddleSlot(middleSlot)) {
            DuoPrefs.MIDDLE_SLOT_WIFI -> if (wifiOn) MIDDLE_WIFI else MIDDLE_OFF
            DuoPrefs.MIDDLE_SLOT_CELL -> if (networkText.isNotEmpty()) MIDDLE_NETWORK else MIDDLE_OFF
            DuoPrefs.MIDDLE_SLOT_PERCENT -> mode
            DuoPrefs.MIDDLE_SLOT_CLOCK -> if (clockText.isNotEmpty()) MIDDLE_NETWORK else MIDDLE_OFF
            DuoPrefs.MIDDLE_SLOT_CHARGING -> if (charging) MIDDLE_NETWORK else MIDDLE_OFF
            else -> mode
        }
        val chosenText = when (DuoPrefs.normalizeMiddleSlot(middleSlot)) {
            DuoPrefs.MIDDLE_SLOT_PERCENT -> if (chosenMode == SignalMapping.MIDDLE_NETWORK) networkText else ""
            DuoPrefs.MIDDLE_SLOT_CLOCK -> clockText
            DuoPrefs.MIDDLE_SLOT_CHARGING -> if (charging) level.toString() else ""
            DuoPrefs.MIDDLE_SLOT_CELL, DuoPrefs.MIDDLE_SLOT_AUTO -> if (chosenMode == SignalMapping.MIDDLE_NETWORK) networkText else ""
            else -> ""
        }
        val invertCentre = DuoPrefs.normalizeMiddleSlot(middleSlot) == DuoPrefs.MIDDLE_SLOT_PERCENT
        val (outerRaw, middleRaw, dotRaw) = SignalMapping.wifiOpacities(wifiLevel)
    val (outer, middle, dot) = if (showWifiGlyph) {
        Triple(outerRaw, middleRaw, dotRaw)
    } else {
        Triple(0f, 0f, 0f)
    }
        val cells = SignalMapping.sphereOpacities(cellLevel, wifiLevel, airplane, wifiOn, wifiDots)
        val (circlesRaw, crescentsRaw, centerMoonRaw) = signalDots(cells, dnd && dndDots)
    val circles = if (showCellDots) circlesRaw else listOf(0f, 0f, 0f, 0f)
    val crescents = if (showCellDots) crescentsRaw else listOf(0f, 0f, 0f, 0f)
    val centerMoon = if (showCellDots) centerMoonRaw else 0f
        // The download owns the ring while it is shown: the charging bolt, its tint and its animation
        // step aside, and the download wash/digits take their places. With no active download this is
        // the ordinary battery ring, so a completed download returns to the charging/battery state.
        val activeDownload = if (downloadActive) download else DownloadWash.IDLE
        val chargingVisual = charging && showCharging && !downloadActive
        val downloadDigits = if (downloadActive) downloadPercent else null
        val percentSeat = (showPercent || invertCentre) || downloadDigits != null
        val percent = when {
            downloadDigits != null -> downloadDigits.toString()
            (showPercent || invertCentre) && !chargingVisual -> level.toString()
            else -> ""
        }
        val closed = RingGeometry.gapClosed(chargingVisual, percentSeat)
        return DuoVisual(
            trimLeftEnd = RingGeometry.trimLeft(level, chargingVisual, closed),
            trimRightEnd = RingGeometry.trimRight(level, chargingVisual, closed),
            leftArc = RingGeometry.leftArc(chargingVisual, percentSeat),
            rightArc = RingGeometry.rightArc(chargingVisual, percentSeat),
            trackOpacity = 0.22f,
            percentText = percent.ifEmpty { " " },
            percentOpacity = if (percent.isEmpty()) 0f else 1f,
            percentFontSize = RingGeometry.percentFontSize(percent.ifEmpty { "50" }),
            percentY = if (invertCentre) RingGeometry.INVERTED_PERCENT_TOP_Y
                       else RingGeometry.percentTopY(percentHeight),
            batteryLineStart = RingGeometry.batteryLineStart(level),
            middleY = if (invertCentre) RingGeometry.INVERTED_MIDDLE_Y else 0f,
            middleScale = if (invertCentre) RingGeometry.INVERTED_MIDDLE_SCALE else 1f,
            boltY = if (invertCentre) 49.5f else 0f,
            // Only the slot's actual occupant carries text: a hidden one never shows a stale label.
            networkText = chosenText,
            boltOpacity = if (chargingVisual) 1f else 0f,
            wifiOuterOpacity = outer,
            wifiMidOpacity = middle,
            cell1Opacity = circles[0],
            cell2Opacity = circles[1],
            cell3Opacity = circles[2],
            cell4Opacity = circles[3],
            moon1Opacity = crescents[0],
            moon2Opacity = crescents[1],
            moon3Opacity = crescents[2],
            moon4Opacity = crescents[3],
            centerMoonOpacity = centerMoon,
            tint = Colors.tint(level, charging && !downloadActive, saver, fgColor),
            fgColor = fgColor,
            middleMode = chosenMode,
            wifiLevel = wifiLevel,
            cellLevel = cellLevel,
            charging = chargingVisual,
            downloadLeftEnd = activeDownload.leftEnd,
            downloadLeftOffset = activeDownload.leftOffset,
            downloadRightEnd = activeDownload.rightEnd,
            downloadRightOffset = activeDownload.rightOffset,
            downloadOpacity = activeDownload.opacity,
            downloadColor = activeDownload.color,
            downloadGradient = activeDownload.gradient,
            visible = visible,
            animateCharge = animateCharge && showCharging && !downloadActive,
            eventType = eventType,
            eventVisible = eventVisible
        )
    }

    /**
     * The four signal dots, plus the lone moon that stands in when that row is hidden.
     *
     * With the moon-dots mode off, the circles keep the sphere opacities and no crescent is drawn.
     * With it on, a fully lit bar becomes the white crescent and its circle is gone; a dim bar stays
     * the gray circle. The views ease between those. A fully hidden row (every sphere at 0) draws
     * none of the four and leaves one crescent in the middle of them.
     */
    private fun signalDots(
        cells: List<Float>,
        moonsOn: Boolean
    ): Triple<List<Float>, List<Float>, Float> {
        val none = listOf(0f, 0f, 0f, 0f)
        if (!moonsOn) return Triple(cells, none, 0f)
        if (cells.all { it == 0f }) return Triple(none, none, 1f)
        return Triple(
            cells.map { if (it >= 1f) 0f else it },
            cells.map { if (it >= 1f) 1f else 0f },
            0f
        )
    }
}
