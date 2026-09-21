package io.github.kvmy666.duostatusbar.hook

import app.rive.runtime.kotlin.core.ViewModelInstance
import io.github.kvmy666.duostatusbar.L

/**
 * The one place that knows how a [DuoVisual] reaches the drawing.
 *
 * Both surfaces use it — the SystemUI view ([DuoRiveView]) and the in-app controls and preview — so the
 * property names exist exactly once. If a name ever changes in `rive/duo/scene.rml`, it changes here and
 * both follow. The alternative (two copies of these strings) is how a preview and a status bar end up
 * quietly disagreeing about what the same `.riv` is showing.
 *
 * Every write is individually guarded: one renamed or missing property produces a warning naming it and
 * the rest of the snapshot still lands, so a partially-wrong file degrades instead of going blank.
 */
object DuoBinder {

    private const val TAG = "DuoSB"

    /** How long the reveal timeline runs; the host clears the request just after it finishes. */
    const val REVEAL_MS = 500L

    /** Number of properties a complete snapshot writes — used to report partial failures. */
    const val PROPERTY_COUNT = 21

    private const val REVEAL_REQUEST = "revealRequest"
    private const val AIRPLANE_STATE = "airplaneState"
    private const val PERCENT_TEXT = "percentText"

    /** Writes the whole snapshot. Returns how many properties failed to bind (0 is perfect). */
    fun apply(vm: ViewModelInstance, v: DuoVisual): Int {
        var failures = 0

        val numbers = arrayOf(
            "trimLeftEnd" to v.trimLeftEnd,
            "trimRightEnd" to v.trimRightEnd,
            "gapLeft" to v.gapLeft,
            "gapRight" to v.gapRight,
            "trackOpacity" to v.trackOpacity,
            "percentOpacity" to v.percentOpacity,
            "percentFontSize" to v.percentFontSize,
            "boltOpacity" to v.boltOpacity,
            "airplaneOpacity" to v.airplaneOpacity,
            "dndOpacity" to v.dndOpacity,
            "wifiOuterOpacity" to v.wifiOuterOpacity,
            "wifiMidOpacity" to v.wifiMidOpacity,
            "wifiDotOpacity" to v.wifiDotOpacity,
            "cell1Opacity" to v.cell1Opacity,
            "cell2Opacity" to v.cell2Opacity,
            "cell3Opacity" to v.cell3Opacity,
            "cell4Opacity" to v.cell4Opacity
        )
        for ((name, value) in numbers) {
            if (!write(name) { vm.getNumberProperty(name).value = value }) failures++
        }

        val colors = arrayOf("tint" to v.tint, "fgColor" to v.fgColor)
        for ((name, value) in colors) {
            if (!write(name) { vm.getColorProperty(name).value = value }) failures++
        }

        if (!write(PERCENT_TEXT) { vm.getStringProperty(PERCENT_TEXT).value = v.percentText }) failures++
        // Drives the state machine layer that plays the airplane morph.
        if (!write(AIRPLANE_STATE) { vm.getBooleanProperty(AIRPLANE_STATE).value = v.airplaneState }) failures++

        return failures
    }

    /** Arms or clears the reveal. The state machine fires on the false -> true edge. */
    fun requestReveal(vm: ViewModelInstance, on: Boolean): Boolean =
        write(REVEAL_REQUEST) { vm.getBooleanProperty(REVEAL_REQUEST).value = on }

    private inline fun write(name: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (t: Throwable) {
        L.w("bind $name: ${t.javaClass.simpleName}: ${t.message}")
        false
    }
}
