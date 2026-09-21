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
    /** Top-gap edges (fractions of the circle from 12 o'clock): the ring closes when they meet. */
    val gapLeft: Float,
    val gapRight: Float,
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
)

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

    /** Fraction from 12 o'clock to the top gap's right edge (the left edge mirrors it). */
    fun gapRight(charging: Boolean, showPercent: Boolean): Float = when {
        charging -> GAP_CHARGING_DEG / 720f
        showPercent -> GAP_PERCENT_DEG / 720f
        else -> 0f
    }

    fun gapLeft(gapRight: Float): Float = 1f - gapRight

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

    /** Left half of the ring, 0 % -> 50 %: bottom-left endpoint up to the gap's left edge. */
    fun trimLeft(level: Int, gapLeft: Float): Float =
        LEFT_START + (gapLeft - LEFT_START) * (level.coerceIn(0, 50) / 50f)

    /** Right half of the ring, 50 % -> 100 %: gap's right edge down to the bottom-right endpoint. */
    fun trimRight(level: Int, gapRight: Float): Float =
        gapRight + (RIGHT_FULL - gapRight) * ((level.coerceIn(50, 100) - 50) / 50f)

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

    /** Font size shrinks for 3 digits so `100` still fits the gap (iOST does the same). */
    fun percentFontSize(text: String): Float = if (text.length >= 3) 33f else 42f

    fun visual(
        level: Int,
        charging: Boolean,
        saver: Boolean,
        showPercent: Boolean,
        wifiLevel: Int,
        cellLevel: Int,
        airplane: Boolean,
        dnd: Boolean = false,
        fgColor: Int = WHITE
    ): DuoVisual {
        // The middle slot holds exactly one occupant (FR-06/FR-16): airplane wins, then DND, else Wi-Fi.
        // Note `wifiOpacities(0)` is not "hidden" - level 0 is the dimmed "no network" state - so a
        // taken slot is zeroed explicitly here rather than by feeding 0 into the level ramp.
        val middleTaken = airplane || dnd
        val (outer, middle, dot) = wifiOpacities(wifiLevel)
        val cells = cellOpacities(if (airplane) 0 else cellLevel)
        val percent = if (showPercent && !charging) level.toString() else ""
        val gRight = gapRight(charging, showPercent)
        val gLeft = gapLeft(gRight)
        return DuoVisual(
            trimLeftEnd = trimLeft(level, gLeft),
            trimRightEnd = trimRight(level, gRight),
            gapLeft = gLeft,
            gapRight = gRight,
            trackOpacity = 0.22f,
            percentText = percent.ifEmpty { " " },
            percentOpacity = if (percent.isEmpty()) 0f else 1f,
            percentFontSize = percentFontSize(percent.ifEmpty { "50" }),
            boltOpacity = if (charging) 1f else 0f,
            airplaneOpacity = if (airplane) 1f else 0f,
            dndOpacity = if (dnd && !airplane) 1f else 0f,
            wifiOuterOpacity = if (middleTaken) 0f else outer,
            wifiMidOpacity = if (middleTaken) 0f else middle,
            wifiDotOpacity = if (middleTaken) 0f else dot,
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
