package io.github.kvmy666.duostatusbar.hook

/** iOS battery appearance derived from Android state. */
internal enum class IosBatteryState {
    NORMAL,
    LOW,
    SAVER,
    CHARGING,
    CHARGING_SAVER,
    PLUGGED_NOT_CHARGING
}

/** One SIM draws four bars; two SIMs draw the data SIM as bars and the other as dots. */
internal enum class IosCellMode { SINGLE, DUAL }

/**
 * Which row of a dual-SIM glyph this view draws.
 *
 * [BOTH] is one view drawing the stacked data-bars + other-dots block; the host uses it for the
 * two-SIM cluster. [OTHER] is kept because the artboard still supports a single row per role, while
 * the runtime single-SIM path uses [DATA].
 */
internal enum class IosCellRole { DATA, OTHER, BOTH }

/**
 * Everything an iOS status-bar glyph needs, derived from system state.
 *
 * Pure data on purpose, like [DuoVisual]: this is the half that can go wrong silently, so it stays free
 * of Android types and is unit-tested (`IosMappingTest`) instead of being debugged on a phone. One
 * snapshot is shared by every occupant; [part] selects which glyph it describes.
 */
internal data class IosVisual(
    val part: IosPart,
    val visible: Boolean = true,
    val tint: Int = 0,
    /** Battery charge 0..1. */
    val batteryLevel: Float = 0f,
    val batteryState: IosBatteryState = IosBatteryState.NORMAL,
    /** Fill colour: the dynamic tint for NORMAL, a fixed state colour otherwise. */
    val batteryFillColor: Int = 0,
    val boltOpacity: Float = 0f,
    /** Wi-Fi level 0..3. */
    val wifiLevel: Float = 0f,
    val cellMode: IosCellMode = IosCellMode.SINGLE,
    val cellRole: IosCellRole = IosCellRole.DATA,
    /** Dual-SIM data row, 0..4 bars. */
    val cellBars: Float = 0f,
    /** Dual-SIM other row, 0..4 dots. */
    val cellDots: Float = 0f
)
