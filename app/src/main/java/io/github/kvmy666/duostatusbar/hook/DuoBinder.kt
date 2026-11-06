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
 *
 * ## Why every write goes through [DuoBinding] and its lock
 *
 * Rive's renderer advances on the main thread while holding `File.lock`, and inside that it calls
 * `ViewModelInstance.pollChanges`, which **iterates the view model's property map**. `getNumberProperty`
 * and friends **insert into that map on first use**. So a write that runs on another thread — or a
 * re-entrant one — while `advance` iterates the map throws `ConcurrentModificationException` and takes the
 * host process down. That is a real crash seen on a Redmi/Afterlife Android 14 build:
 *
 *     ConcurrentModificationException
 *       ViewModelInstance.pollChanges
 *       RiveFileController.advance
 *       RiveArtboardRenderer.advance
 *
 * Taking the *same* `File.lock` around every write makes the write and the renderer's iteration mutually
 * exclusive, so the map can never be modified mid-iteration. [bind] captures the lock once, next to the
 * view model, so callers cannot forget it.
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
    const val PROPERTY_COUNT = 59

    private const val REVEAL_MS = "revealMs"
    private const val PERCENT_TEXT = "percentText"
    private const val NETWORK_TEXT = "networkText"
    private const val VISIBLE = "visible"
    private const val CHARGING = "charging"
    private const val ANIMATE_CHARGE = "animateCharge"
    private const val EVENT_TYPE = "eventType"
    private const val EVENT_VISIBLE = "eventVisible"

    /**
     * Binds a view model for writing. [lock] must be the Rive file's own lock (`RiveAnimationView.file.lock`)
     * so the writes serialize with the renderer's `advance`; when it is unavailable the view model itself is
     * used as the monitor, which still serializes writes with each other.
     */
    fun bind(vm: ViewModelInstance, lock: Any?): DuoBinding = DuoBinding(vm, lock ?: vm)

    /** Writes the whole snapshot. Returns how many properties failed to bind (0 is perfect). */
    internal fun write(vm: ViewModelInstance, v: DuoVisual): Int {
        var failures = 0
        val gradient = v.downloadGradient
        val gradientColors = gradient?.riveColors() ?: List(5) { v.downloadColor }
        val gradientPositions = gradient?.rivePositions() ?: listOf(0f, 1f, 1f, 1f, 1f)
        val gradientPoints =
            gradient?.points(-DOWNLOAD_RADIUS, -DOWNLOAD_RADIUS, DOWNLOAD_RADIUS, DOWNLOAD_RADIUS)
                ?: floatArrayOf(-DOWNLOAD_RADIUS, 0f, DOWNLOAD_RADIUS, 0f)

        val numbers = arrayOf(
            "trimLeftEnd" to v.trimLeftEnd,
            "trimRightEnd" to v.trimRightEnd,
            "leftArc" to v.leftArc,
            "rightArc" to v.rightArc,
            "trackOpacity" to v.trackOpacity,
            "percentFontSize" to v.percentFontSize,
            // Top of the percentage text box.  -60 is the original seat; more negative raises it.
            "percentY" to v.percentY,
            // Battery line (2 SIM) level-derived trim start.
            "batteryLineStart" to v.batteryLineStart,
            // Invert-center: middle occupant seat and scale.
            "middleY" to v.middleY,
            "middleScale" to v.middleScale,
            // Invert-center: charging bolt seat.
            "boltY" to v.boltY,
            // Which group this view draws. Both are 1 for the original element; a split layout
            // runs two views of the same file and each one hides the other's group.
            "ringOpacity" to v.ringOpacity,
            "indicatorsOpacity" to v.indicatorsOpacity,
            // Wi-Fi is a Rive blend layer (one axis); the cellular spheres are bound per sphere so the
            // dual-SIM split can drive a pair per line.
            "wifiLevel" to v.wifiLevel.toFloat(),
            "cell1Opacity" to v.cell1Opacity,
            "cell2Opacity" to v.cell2Opacity,
            "cell3Opacity" to v.cell3Opacity,
            "cell4Opacity" to v.cell4Opacity,
            // A strong bar's gray dot fills into this crescent. The scale grows with the opacity,
            // so a fill written as intermediate opacities is also a growing moon. 0 keeps the circle.
            "moon1Opacity" to v.moon1Opacity,
            "moon2Opacity" to v.moon2Opacity,
            "moon3Opacity" to v.moon3Opacity,
            "moon4Opacity" to v.moon4Opacity,
            "moon1Scale" to v.moon1Opacity * DotMoonMotion.DOT_SCALE,
            "moon2Scale" to v.moon2Opacity * DotMoonMotion.DOT_SCALE,
            "moon3Scale" to v.moon3Opacity * DotMoonMotion.DOT_SCALE,
            "moon4Scale" to v.moon4Opacity * DotMoonMotion.DOT_SCALE,
            // The one moon left in the middle of the row when the four dots are fully hidden.
            "centerMoonOpacity" to v.centerMoonOpacity,
            "centerMoonScale" to v.centerMoonOpacity * DotMoonMotion.DOT_SCALE,
            // The whole middle-slot hand-over is one Rive layer; this only says which occupant.
            "middleMode" to v.middleMode.toFloat(),
            // Download wash on the white ring. Zero opacity and zero lengths leave the battery stroke
            // exactly as it was. Offsets move only for the counter-clockwise direction.
            "downloadLeftEnd" to v.downloadLeftEnd,
            "downloadLeftOffset" to v.downloadLeftOffset,
            "downloadRightEnd" to v.downloadRightEnd,
            "downloadRightOffset" to v.downloadRightOffset,
            "downloadOpacity" to if (gradient == null) v.downloadOpacity else 0f,
            "downloadGradientOpacity" to if (gradient != null) v.downloadOpacity else 0f,
            "downloadGradientStop1" to gradientPositions[0],
            "downloadGradientStop2" to gradientPositions[1],
            "downloadGradientStop3" to gradientPositions[2],
            "downloadGradientStop4" to gradientPositions[3],
            "downloadGradientStop5" to gradientPositions[4],
            "downloadGradientStartX" to gradientPoints[0],
            "downloadGradientStartY" to gradientPoints[1],
            "downloadGradientEndX" to gradientPoints[2],
            "downloadGradientEndY" to gradientPoints[3],
            EVENT_TYPE to v.eventType.toFloat()
        )
        for ((name, value) in numbers) {
            if (!write(name) { vm.getNumberProperty(name).value = value }) failures++
        }

        val colors = arrayOf(
            "tint" to v.tint,
            "fgColor" to v.fgColor,
            "downloadColor" to v.downloadColor,
            "downloadGradientColor1" to gradientColors[0],
            "downloadGradientColor2" to gradientColors[1],
            "downloadGradientColor3" to gradientColors[2],
            "downloadGradientColor4" to gradientColors[3],
            "downloadGradientColor5" to gradientColors[4]
        )
        for ((name, value) in colors) {
            if (!write(name) { vm.getColorProperty(name).value = value }) failures++
        }

        if (!write(PERCENT_TEXT) { vm.getStringProperty(PERCENT_TEXT).value = v.percentText }) failures++
        // The middle slot's cellular label, shown only while Wi-Fi is off (FR-06).
        if (!write(NETWORK_TEXT) { vm.getStringProperty(NETWORK_TEXT).value = v.networkText }) failures++
        // False plays the departure, true brings the element back (screen off / on).
        if (!write(VISIBLE) { vm.getBooleanProperty(VISIBLE).value = v.visible }) failures++
        // Drives the Charge layer: the bolt's journey from the middle slot to the ring's gap.
        if (!write(CHARGING) { vm.getBooleanProperty(CHARGING).value = v.charging }) failures++
        // False makes the bolt appear/disappear instantly (the user's charging-animation switch).
        if (!write(ANIMATE_CHARGE) { vm.getBooleanProperty(ANIMATE_CHARGE).value = v.animateCharge }) failures++
        // Event icon type and visibility.
        if (!write(EVENT_VISIBLE) { vm.getBooleanProperty(EVENT_VISIBLE).value = v.eventVisible }) failures++
        return failures
    }

    /** Fires an arrival of [ms] milliseconds; 0 clears the request. See [REVEAL_CLEAR_MS]. */
    internal fun writeReveal(vm: ViewModelInstance, ms: Int): Boolean =
        write(REVEAL_MS) { vm.getNumberProperty(REVEAL_MS).value = ms.toFloat() }

    private inline fun write(name: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (t: Throwable) {
        L.w("bind $name: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private const val DOWNLOAD_RADIUS = 51.5f
}

/**
 * A view model paired with the lock its writes must take (see [DuoBinder]). Created by [DuoBinder.bind];
 * both surfaces hold one and write only through it.
 */
class DuoBinding internal constructor(
    private val vm: ViewModelInstance,
    private val lock: Any
) {
    /** Writes the whole snapshot under the Rive lock. Returns how many properties failed to bind. */
    fun apply(v: DuoVisual): Int = synchronized(lock) { DuoBinder.write(vm, v) }

    /** Fires an arrival of [ms] ms under the Rive lock; 0 clears the request. */
    fun requestReveal(ms: Int): Boolean = synchronized(lock) { DuoBinder.writeReveal(vm, ms) }
}
