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

    /**
     * How long the arrival request stays set before the host clears it back to 0.
     *
     * `revealMs` carries both the trigger and the duration: the state machine fires an arrival when it
     * reads a non-zero value, so leaving it set would re-fire the moment the arrival finished. Clearing
     * it early is what makes it fire once. It only has to outlive the first frame - the machine latches
     * the state on the transition - so this is short on purpose, and shorter than the shortest arrival
     * (500 ms) so a second arrival can still be requested.
     */
    const val REVEAL_CLEAR_MS = 120L

    /** The arrivals the file can play, in ms: the one timeline at five speeds. */
    val REVEAL_CHOICES = intArrayOf(500, 750, 1000, 1250, 1500)

    /** Number of properties a complete snapshot writes — used to report partial failures. */
    const val PROPERTY_COUNT = 19

    private const val REVEAL_MS = "revealMs"
    private const val PERCENT_TEXT = "percentText"
    private const val VISIBLE = "visible"

    /** Writes the whole snapshot. Returns how many properties failed to bind (0 is perfect). */
    fun apply(vm: ViewModelInstance, v: DuoVisual): Int {
        var failures = 0

        val numbers = arrayOf(
            "trimLeftEnd" to v.trimLeftEnd,
            "trimRightEnd" to v.trimRightEnd,
            "leftArc" to v.leftArc,
            "rightArc" to v.rightArc,
            "trackOpacity" to v.trackOpacity,
            "percentOpacity" to v.percentOpacity,
            "percentFontSize" to v.percentFontSize,
            "boltOpacity" to v.boltOpacity,
            "wifiOuterOpacity" to v.wifiOuterOpacity,
            "wifiMidOpacity" to v.wifiMidOpacity,
            // The whole middle-slot hand-over is one Rive layer; this only says which occupant.
            "middleMode" to v.middleMode.toFloat(),
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
        // False plays the departure, true brings the element back (screen off / on).
        if (!write(VISIBLE) { vm.getBooleanProperty(VISIBLE).value = v.visible }) failures++

        return failures
    }

    /** Fires an arrival of [ms] milliseconds; 0 clears the request. See [REVEAL_CLEAR_MS]. */
    fun requestReveal(vm: ViewModelInstance, ms: Int): Boolean =
        write(REVEAL_MS) { vm.getNumberProperty(REVEAL_MS).value = ms.toFloat() }

    private inline fun write(name: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (t: Throwable) {
        L.w("bind $name: ${t.javaClass.simpleName}: ${t.message}")
        false
    }
}
