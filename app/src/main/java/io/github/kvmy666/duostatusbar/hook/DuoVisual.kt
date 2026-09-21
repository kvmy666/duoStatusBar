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
    val trackOpacity: Float,
    val percentText: String,
    val percentOpacity: Float,
    val percentFontSize: Float,
    val boltOpacity: Float,
    val airplaneOpacity: Float,
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

    // Ring geometry, straight out of docs/DESIGN-duo.md. The drawn arc is split by the top gap:
    // left segment 0.6631 -> 0.9010 covers 0-50 %, right segment 0.0990 -> 0.3369 covers 50-100 %.
    const val LEFT_START = 0.6631f
    const val LEFT_FULL = 0.9010f
    const val RIGHT_START = 0.0990f
    const val RIGHT_FULL = 0.3369f
    private const val LEFT_SPAN = LEFT_FULL - LEFT_START
    private const val RIGHT_SPAN = RIGHT_FULL - RIGHT_START

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

    /** Left half of the ring, 0 % -> 50 %. */
    fun trimLeft(level: Int): Float =
        LEFT_START + LEFT_SPAN * (level.coerceIn(0, 50) / 50f)

    /** Right half of the ring, 50 % -> 100 %. Stays at its start (invisible) below 50 %. */
    fun trimRight(level: Int): Float =
        RIGHT_START + RIGHT_SPAN * ((level.coerceIn(50, 100) - 50) / 50f)

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
        fgColor: Int = WHITE
    ): DuoVisual {
        val (outer, middle, dot) = wifiOpacities(if (airplane) 0 else wifiLevel)
        val cells = cellOpacities(if (airplane) 0 else cellLevel)
        val percent = if (showPercent && !charging) level.toString() else ""
        return DuoVisual(
            trimLeftEnd = trimLeft(level),
            trimRightEnd = trimRight(level),
            trackOpacity = 0.22f,
            percentText = percent.ifEmpty { " " },
            percentOpacity = if (percent.isEmpty()) 0f else 1f,
            percentFontSize = percentFontSize(percent.ifEmpty { "50" }),
            boltOpacity = if (charging) 1f else 0f,
            airplaneOpacity = if (airplane) 1f else 0f,
            wifiOuterOpacity = if (airplane) 0f else outer,
            wifiMidOpacity = if (airplane) 0f else middle,
            wifiDotOpacity = if (airplane) 0f else dot,
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
