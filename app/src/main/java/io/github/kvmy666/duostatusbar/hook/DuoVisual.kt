package io.github.kvmy666.duostatusbar.hook


/**
 * Everything the drawing needs, derived from system state.
 *
 * Pure data + pure maths on purpose: this is the half that can go wrong silently, so it is kept
 * free of Android types and unit-tested (`DuoMappingTest`) instead of being debugged on a phone.
 */
data class DuoVisual(
    val trimLeftEnd: Float,
    val trimRightEnd: Float,
    /** The two halves' track arc lengths (trim fractions); the right one is 0 when the gap closes. */
    val leftArc: Float,
    val rightArc: Float,
    val trackOpacity: Float,
    val percentText: String,
    val percentOpacity: Float,
    val percentFontSize: Float,
    /**
     * The cellular generation shown in the middle slot when Wi-Fi is off — "5G"/"4G"/"3G"/"2G", or
     * empty when there is no service. Empty unless the slot is actually holding it, so a hidden
     * occupant never carries stale text (FR-06).
     */
    val networkText: String,
    val boltOpacity: Float,
    val wifiOuterOpacity: Float,
    val wifiMidOpacity: Float,
    val cell1Opacity: Float,
    val cell2Opacity: Float,
    val cell3Opacity: Float,
    val cell4Opacity: Float,
    val tint: Int,
    val fgColor: Int,
    /**
     * Which occupant the middle slot holds: 0 off, 1 Wi-Fi, 2 airplane, 3 DND (FR-06/FR-16).
     *
     * The slot's whole hand-over - the arcs collapsing, the dot fading, the plane or crescent growing
     * out of it - is one Rive state machine layer, so this is the only thing the host says about it.
     */
    val middleMode: Int,
    /**
     * The two signal axes the Rive blend layers read: Wi-Fi 0-3, cellular 0-4. One number each rather
     * than six opacities, because Rive blends between pose animations and gets the per-sphere cascade
     * for free as the eased axis sweeps past each sphere's threshold.
     */
    val wifiLevel: Int,
    val cellLevel: Int,
    /**
     * Charging drives the bolt's journey - it is born in the middle slot and travels up into the ring's
     * gap - so that whole sequence is one Rive layer, and this is the only thing the host says about it.
     */
    val charging: Boolean,
    /** False plays the departure (screen off); true brings the element back. */
    val visible: Boolean = true,
    /**
     * Whether the charging *journey* plays. False shows the bolt instantly, which is the user's
     * "charging animation" switch (FR-25).
     */
    val animateCharge: Boolean = true
) {
    /**
     * Interpolates between two snapshots, for the FR-09 setting demos that morph one setting between
     * its off and on states.
     *
     * Every drawn quantity is a Float, so those interpolate; the things that cannot be half-way
     * (colours, the text, the boolean that drives the state machine) snap at the mid-point. Pure and
     * unit-tested, because a demo that quietly shows the wrong thing is worse than no demo.
     */
    fun lerp(other: DuoVisual, t: Float): DuoVisual {
        val f = t.coerceIn(0f, 1f)
        fun at(a: Float, b: Float) = a + (b - a) * f
        val past = f >= 0.5f
        return copy(
            trimLeftEnd = at(trimLeftEnd, other.trimLeftEnd),
            trimRightEnd = at(trimRightEnd, other.trimRightEnd),
            leftArc = at(leftArc, other.leftArc),
            rightArc = at(rightArc, other.rightArc),
            trackOpacity = at(trackOpacity, other.trackOpacity),
            percentOpacity = at(percentOpacity, other.percentOpacity),
            percentFontSize = at(percentFontSize, other.percentFontSize),
            boltOpacity = at(boltOpacity, other.boltOpacity),
            wifiOuterOpacity = at(wifiOuterOpacity, other.wifiOuterOpacity),
            wifiMidOpacity = at(wifiMidOpacity, other.wifiMidOpacity),
            cell1Opacity = at(cell1Opacity, other.cell1Opacity),
            cell2Opacity = at(cell2Opacity, other.cell2Opacity),
            cell3Opacity = at(cell3Opacity, other.cell3Opacity),
            cell4Opacity = at(cell4Opacity, other.cell4Opacity),
            percentText = if (past) other.percentText else percentText,
            networkText = if (past) other.networkText else networkText,
            tint = if (past) other.tint else tint,
            fgColor = if (past) other.fgColor else fgColor,
            // The middle slot's hand-over is a Rive layer, not a tween: this only picks which occupant.
            middleMode = if (past) other.middleMode else middleMode,
            wifiLevel = if (past) other.wifiLevel else wifiLevel,
            cellLevel = if (past) other.cellLevel else cellLevel,
            charging = if (past) other.charging else charging,
            visible = if (past) other.visible else visible,
            animateCharge = if (past) other.animateCharge else animateCharge
        )
    }
}

object DuoMapping {

    // Ring geometry, straight out of docs/DESIGN-duo.md. The fill runs from the bottom-left endpoint
    // clockwise: left segment covers 0-50 %, right segment covers 50-100 %, and the top gap between
    // them holds the number (or the bolt). The gap is a *variable*: 71.3 deg with the digits, 55.6 deg
    // with the bolt, and 0 (the ring closes) when the number is off - DESIGN §4.
    const val LEFT_START = 0.6631f
    const val RIGHT_FULL = 0.3369f
    const val GAP_PERCENT_DEG = 71.3f
    const val GAP_CHARGING_DEG = 55.6f

    /** The percent-on gap edges; the "closed" case is 0 and 1. */
    val RIGHT_START = GAP_PERCENT_DEG / 720f
    val LEFT_FULL = 1f - RIGHT_START

    /**
     * The two halves' arc lengths, as trim fractions. Each half is one trimmed window anchored by
     * `offset` and sized by `end`, so these are *lengths*, not positions.
     *
     * With the gap open each half covers [LEFT_START]..[LEFT_FULL] / [RIGHT_START]..[RIGHT_FULL].
     * With it closed the left half grows past 12 o'clock to cover the whole [DRAWN_ARC] on its own
     * and the right half goes to zero - there is deliberately no pair of windows meeting at 12
     * o'clock, because each would end in a round cap and the two caps would overlap: the left body
     * painted over the right body (user-reported bug), and the track's two 0.22 caps stacked to 0.46
     * and showed as a bright blob.
     */
    /** The whole drawn arc once the gap is closed: bottom-left endpoint, through 12 o'clock, round. */
    const val DRAWN_ARC = (1f - LEFT_START) + RIGHT_FULL

    /** The top gap is closed exactly when neither the digits nor the bolt occupy it. */
    fun gapClosed(charging: Boolean, showPercent: Boolean): Boolean = !charging && !showPercent

    /** One half's arc length for the current gap: the bolt's narrower gap makes the halves longer. */
    fun halfArc(charging: Boolean): Float =
        1f - (if (charging) GAP_CHARGING_DEG else GAP_PERCENT_DEG) / 720f - LEFT_START

    /** Left half's arc length: half the drawn arc, or all of it once the gap closes. */
    fun leftArc(charging: Boolean, showPercent: Boolean): Float =
        if (gapClosed(charging, showPercent)) DRAWN_ARC else halfArc(charging)

    /** Right half's arc length: half the drawn arc, or nothing once the gap closes. */
    fun rightArc(charging: Boolean, showPercent: Boolean): Float =
        if (gapClosed(charging, showPercent)) 0f else halfArc(charging)

    const val GREEN_CHARGING = 0xFF34C759.toInt()
    const val YELLOW_SAVER = 0xFFF2B900.toInt()
    const val RED_CRITICAL = 0xFFFF3B30.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** Battery colour, FR-15. Charging wins over everything, then saver, then the <20 % warning. */
    fun tint(level: Int, charging: Boolean, saver: Boolean): Int = when {
        charging -> GREEN_CHARGING
        saver -> YELLOW_SAVER
        level < 20 -> RED_CRITICAL
        else -> WHITE
    }

    /**
     * Left half's filled arc: 0 % -> 50 % of [halfArc] normally. With the gap closed it owns the
     * whole [DRAWN_ARC] instead, so 0 % -> 100 % of that.
     */
    fun trimLeft(level: Int, charging: Boolean, closed: Boolean): Float =
        if (closed) DRAWN_ARC * (level.coerceIn(0, 100) / 100f)
        else halfArc(charging) * (level.coerceIn(0, 50) / 50f)

    /** Right half's filled arc: 50 % -> 100 % of [halfArc], and nothing while the gap is closed. */
    fun trimRight(level: Int, charging: Boolean, closed: Boolean): Float =
        if (closed) 0f else halfArc(charging) * ((level.coerceIn(50, 100) - 50) / 50f)

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

    /** Four cellular spheres; `level` 0..4 spheres lit. */
    fun cellOpacities(level: Int): List<Float> {
        val n = level.coerceIn(0, 4)
        return (1..4).map { if (it <= n) 1f else 0.3f }
    }

    /**
     * Font size, measured against the reference: the digits' cap height is 0.218 of the ring diameter
     * (`tools/measure-element.py` on `duo-reference-02`: 43 px digits / 197 px ring), so ~22.5 units in
     * the 103-unit ring - a font of 32 for Montserrat (cap height ~0.7 em). Three digits shrink further
     * so `100` still fits the gap.
     */
    fun percentFontSize(text: String): Float = if (text.length >= 3) 26f else 32f

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
        animateCharge: Boolean = true
    ): DuoVisual {
        // The middle slot holds exactly one occupant (FR-06/FR-16): airplane wins, then DND, then
        // Wi-Fi; with Wi-Fi off the slot shows the cellular generation instead. The hand-over itself
        // - the arcs collapsing, the dot fading, the new occupant growing out of it - is the
        // MiddleSlot layer's job, so all the host says is which occupant it should be.
        // Wi-Fi only owns the slot when it is the active data path: a connected-but-internet-less AP
        // still leaves the phone on mobile data, so the slot shows the cellular generation instead of a
        // Wi-Fi glyph while the user is plainly on 4G/5G (user-reported).
        val mode = middleMode(airplane, dnd, wifiOn, networkText.isNotEmpty(), wifiConnected)
        val (outer, middle, dot) = wifiOpacities(wifiLevel)
        val cells = cellOpacities(if (airplane) 0 else cellLevel)
        val percent = if (showPercent && !charging) level.toString() else ""
        val closed = gapClosed(charging, showPercent)
        return DuoVisual(
            trimLeftEnd = trimLeft(level, charging, closed),
            trimRightEnd = trimRight(level, charging, closed),
            leftArc = leftArc(charging, showPercent),
            rightArc = rightArc(charging, showPercent),
            trackOpacity = 0.22f,
            percentText = percent.ifEmpty { " " },
            percentOpacity = if (percent.isEmpty()) 0f else 1f,
            percentFontSize = percentFontSize(percent.ifEmpty { "50" }),
            // Only the slot's actual occupant carries text: a hidden one never shows a stale label.
            networkText = if (mode == MIDDLE_NETWORK) networkText else "",
            boltOpacity = if (charging) 1f else 0f,
            wifiOuterOpacity = outer,
            wifiMidOpacity = middle,
            cell1Opacity = cells[0],
            cell2Opacity = cells[1],
            cell3Opacity = cells[2],
            cell4Opacity = cells[3],
            tint = tint(level, charging, saver),
            fgColor = fgColor,
            middleMode = mode,
            wifiLevel = wifiLevel,
            cellLevel = cellLevel,
            charging = charging,
            visible = visible,
            animateCharge = animateCharge
        )
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
     */
    fun middleMode(
        airplane: Boolean,
        dnd: Boolean,
        wifiOn: Boolean = true,
        hasNetwork: Boolean = false,
        wifiConnected: Boolean = true
    ): Int = when {
        airplane -> MIDDLE_AIRPLANE
        dnd -> MIDDLE_DND
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
     */
    fun networkGeneration(type: Int, nrConnected: Boolean = false): String {
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
