package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomResources
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * The optical scale for [designStyle]. The classic drawing is 112 px where the new one is 104 px, so
 * it is drawn at [DuoPrefs.OLD_OPTICAL_SCALE] to keep the same on-screen ring size. An unknown value
 * keeps the classic scale, matching [DuoPrefs.normalizeDesignStyle].
 */
internal fun opticalScaleFor(designStyle: String?): Float =
    if (DuoPrefs.normalizeDesignStyle(designStyle) == DuoPrefs.DESIGN_OLD) {
        DuoPrefs.OLD_OPTICAL_SCALE
    } else {
        1f
    }

/**
 * The element's size and position inside the icon strip (FR-03/17).
 *
 * The strip is only 61 px tall, and the drawing is `Fit.CONTAIN` (square), so sizing the view to the
 * strip's height capped the element at 61 px and the size setting did nothing above ~75 % - measured on
 * the device: 100 % and 140 % looked identical. The view is now a **square** that follows the setting
 * and is allowed to overflow the strip, capped only by the status bar window's own height.
 *
 * The slot width is captured **once**, at attach, before the stock icons are hidden. Re-measuring it
 * later reads the hidden battery's 0-width and falls back to the strip height, which changes the size
 * mid-session and forces a live relayout of the Rive `TextureView` - the native resize that used to
 * restart System UI and trip the Rive breaker. Pinning the base keeps the size stable for the whole run.
 */
internal class SlotGeometry(private val context: Context, private val rom: RomAdapter) {

    /** The size the element is actually drawn at; captured once at attach, not updated live. */
    var appliedSize = 0
        private set

    /** The slot width captured once, at attach, before the stock icons were hidden. */
    var slotBasePx = 0
        private set

    /** The main bar's final side in px; extras may never draw larger than this. */
    var mainSidePx = 0
        private set

    /** The drawing currently on screen; it owns the optical scale applied to every size. */
    var designStyle = DuoPrefs.DESIGN_OLD
        private set

    /** Pins the design style for this run. Called at attach and while no element is attached. */
    fun applyDesignStyle(style: String?) {
        designStyle = DuoPrefs.normalizeDesignStyle(style)
    }

    /** Pins the size and slot base for this run. Called once, at attach. */
    fun capture(sizePercent: Int, basePx: Int) {
        appliedSize = sizePercent
        slotBasePx = basePx
    }

    /**
     * Switches to a size that was already saved for another orientation. The slot base stays the one
     * captured at attach — re-measuring it after the stock icons are hidden reads 0 and would resize
     * from the wrong width.
     */
    fun applySize(sizePercent: Int) {
        appliedSize = sizePercent
    }

    /** Forgets the captured values (teardown). */
    fun reset() {
        appliedSize = 0
        slotBasePx = 0
        mainSidePx = 0
        designStyle = DuoPrefs.DESIGN_OLD
    }

    /** Records the main bar's actual box after applyLayout; extras are clamped to it. */
    fun noteMainSide(side: Int) {
        if (side > 0) mainSidePx = side
    }

    /**
     * Side for a QS/shade/keyguard copy. The bar-height cap alone is not enough: the QS/shade
     * container can be the full window height, so an extra could draw at 175 px while the main bar
     * is capped at 141 px. Reuse the main bar's final side exactly.
     */
    fun extraSidePx(container: ViewGroup, windowRoot: View?, base: Int = 0): Int {
        val side = sidePx(container, windowRoot, base)
        return if (mainSidePx > 0) side.coerceAtMost(mainSidePx) else side
    }

    /**
     * The element's side in px: the slot base scaled by the setting, capped at the status bar window's
     * own height so it can never grow past the bar.
     *
     * [base] is the per-slot value for the extra bars; the main bar relies on the captured [slotBasePx].
     */
    fun sidePx(container: ViewGroup, windowRoot: View?, base: Int = 0): Int {
        val measured = when {
            base > 0 -> base
            slotBasePx > 0 -> slotBasePx
            else -> measuredWidth(container)
        }
        val scaled = ((measured * appliedSize / 100f) * opticalScaleFor(designStyle))
            .toInt().coerceAtLeast(1)
        // At boot the vendor header can still be 0 high; the measured QS header is 63 and the shade
        // header 101, so the container's own height is the next honest cap.
        val height = (windowRoot?.height ?: 0).takeIf { it > 0 }
            ?: container.height.takeIf { it > 0 }
            ?: return scaled
        return scaled.coerceAtMost(height).coerceAtLeast(1)
    }

    /** The element's width in px, used for the Canvas fallback's layout params. */
    fun widthPx(container: ViewGroup): Int {
        val base = if (slotBasePx > 0) slotBasePx else measuredWidth(container)
        return ((base * appliedSize / 100f) * opticalScaleFor(designStyle))
            .toInt().coerceAtLeast(1)
    }

    /** The battery slot's column — 83 px measured on the target device — or the best available estimate. */
    fun measuredWidth(container: ViewGroup): Int {
        val measured = try {
            val id = RomResources.id(context, rom, rom.batteryId)
            if (id != 0) container.findViewById<View>(id)?.width ?: 0 else 0
        } catch (_: Throwable) {
            0
        }
        if (measured > 0) return measured
        if (container.height > 0) return container.height // square, like the reference element
        return (27.4f * context.resources.displayMetrics.density).toInt() // 83 px at density 3.025
    }

    /** How far to move the element so its centre matches its status bar window's centre. */
    fun windowCenterShiftY(container: ViewGroup, windowRoot: View?): Float {
        // The strip is its own bar (the shade header's icon area): nothing to centre against, and the
        // arithmetic below would move it to the window's top instead.
        if (windowRoot == null || windowRoot === container) return 0f
        val height = (windowRoot.height).takeIf { it > 0 } ?: return 0f
        val location = IntArray(2)
        try {
            container.getLocationInWindow(location)
        } catch (_: Throwable) {
            return 0f
        }
        val stripCenter = location[1] + container.height / 2f
        return height / 2f - stripCenter
    }
}

/**
 * Margin that leaves [paddingPercent] of [sidePx] in the icon strip.
 *
 * 100 keeps the view's full width, which is the slot between the screen edge and the icons Duo does
 * not replace. 0 returns that width to the strip. The result is zero or negative: a negative start
 * margin overlaps the previous icon without moving this view's end edge.
 */
internal fun edgeInsetPx(sidePx: Int, paddingPercent: Int): Int {
    if (sidePx <= 0) return 0
    val percent = paddingPercent.coerceIn(0, 100)
    return sidePx * percent / 100 - sidePx
}

/**
 * Horizontal translation that puts a [side] px view on the center of the status bar, then nudges it by
 * [offsetPx]. [barLeft] and [parentLeft] are window coordinates. Gravity pins the view to the parent's
 * left padding, so the translation is the whole distance from that edge to the centered seat.
 *
 * The status bar's center does not move when the clock gets wider. A translation written from it stays
 * put with no further layout pass.
 */
internal fun lockedTranslationX(
    barLeft: Int,
    barWidth: Int,
    parentLeft: Int,
    parentPaddingLeft: Int,
    side: Int,
    offsetPx: Float
): Float = barLeft + barWidth / 2f - side / 2f - parentLeft - parentPaddingLeft + offsetPx

/**
 * Vertical translation that puts the ring on the vertical center of the status bar.
 *
 * The view is taller than the ring: the extra artboard space sits above it, so the ring's center is
 * `height - side/2` from the top of the view. Gravity pins that top to the parent's top padding.
 */
internal fun lockedTranslationY(
    barTop: Int,
    barHeight: Int,
    parentTop: Int,
    parentPaddingTop: Int,
    side: Int
): Float {
    val height = RingGeometry.elementHeightPx(side)
    val ringFromTop = height - side / 2f
    return barTop + barHeight / 2f - ringFromTop - parentTop - parentPaddingTop
}

/**
 * Horizontal translation (in the host's physical pixel coordinates) that puts a [viewWidth] px view
 * with [endPadding] px reserved on its right at the physical right edge of a [containerWidth] px parent,
 * then nudges it by [offsetPx] (negative moves left, 0 is the right edge).
 *
 * Android [ViewGroup] coordinates are physical: `x` grows to the right in both LTR and RTL, and
 * `layoutDirection` only changes default child placement, not the origin. The host therefore exposes
 * no mirrored coordinate system, so the same formula is used for both directions and `rtl` does not
 * flip it. The mirrored form (`viewWidth + endPadding - offsetPx`) would only be correct for a host
 * whose coordinates were mirrored, which this one is not.
 */
internal fun normalSeatTranslationX(
    containerWidth: Int,
    viewWidth: Int,
    endPadding: Int,
    offsetPx: Int,
    rtl: Boolean
): Int = containerWidth - viewWidth - endPadding + offsetPx
