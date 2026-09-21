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
    val boltOpacity: Float,
    val airplaneOpacity: Float,
    /** The Do Not Disturb crescent in the middle slot; 0 unless DND is on (FR-06). */
    val dndOpacity: Float,
    val wifiOuterOpacity: Float,
    val wifiMidOpacity: Float,
    val wifiDotOpacity: Float,
    val cell1Opacity: Float,
    val cell2Opacity: Float,
    val cell3Opacity: Float,
    val cell4Opacity: Float,
    val tint: Int,
    val fgColor: Int,
    val airplaneState: Boolean
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
            airplaneOpacity = at(airplaneOpacity, other.airplaneOpacity),
            dndOpacity = at(dndOpacity, other.dndOpacity),
            wifiOuterOpacity = at(wifiOuterOpacity, other.wifiOuterOpacity),
            wifiMidOpacity = at(wifiMidOpacity, other.wifiMidOpacity),
            wifiDotOpacity = at(wifiDotOpacity, other.wifiDotOpacity),
            cell1Opacity = at(cell1Opacity, other.cell1Opacity),
            cell2Opacity = at(cell2Opacity, other.cell2Opacity),
            cell3Opacity = at(cell3Opacity, other.cell3Opacity),
            cell4Opacity = at(cell4Opacity, other.cell4Opacity),
            percentText = if (past) other.percentText else percentText,
            tint = if (past) other.tint else tint,
            fgColor = if (past) other.fgColor else fgColor,
            airplaneState = if (past) other.airplaneState else airplaneState
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
        middleBlend: Float = 1f
    ): DuoVisual {
        // The middle slot holds exactly one occupant (FR-06/FR-16): airplane wins, then DND, else Wi-Fi.
        // Note `wifiOpacities(0)` is not "hidden" - level 0 is the dimmed "no network" state - so a
        // taken slot is zeroed explicitly here rather than by feeding 0 into the level ramp.
        //
        // [middleBlend] crossfades the hand-over: 0 keeps the Wi-Fi, 1 shows the alternate occupant,
        // and the monitor tweens it so the slot fades instead of cutting (user feedback). The Wi-Fi
        // also gets its arcs collapsed by the state machine's Airplane layer at the same time.
        val alternate = if (airplane || dnd) 1f else 0f
        val wifiFade = 1f - middleBlend.coerceIn(0f, 1f) * alternate
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
            boltOpacity = if (charging) 1f else 0f,
            airplaneOpacity = if (airplane) middleBlend.coerceIn(0f, 1f) else 0f,
            dndOpacity = if (dnd && !airplane) middleBlend.coerceIn(0f, 1f) else 0f,
            wifiOuterOpacity = outer * wifiFade,
            wifiMidOpacity = middle * wifiFade,
            wifiDotOpacity = dot * wifiFade,
            cell1Opacity = cells[0],
            cell2Opacity = cells[1],
            cell3Opacity = cells[2],
            cell4Opacity = cells[3],
            tint = tint(level, charging, saver),
            fgColor = fgColor,
            airplaneState = airplane
        )
    }
}
