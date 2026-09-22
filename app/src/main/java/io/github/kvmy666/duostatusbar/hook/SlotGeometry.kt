package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter

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

    /** Pins the size and slot base for this run. Called once, at attach. */
    fun capture(sizePercent: Int, basePx: Int) {
        appliedSize = sizePercent
        slotBasePx = basePx
    }

    /** Forgets the captured values (teardown). */
    fun reset() {
        appliedSize = 0
        slotBasePx = 0
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
        val scaled = measured * appliedSize / 100
        val height = (windowRoot?.height ?: 0).takeIf { it > 0 } ?: return scaled
        return scaled.coerceAtMost(height)
    }

    /** The element's width in px, used for the Canvas fallback's layout params. */
    fun widthPx(container: ViewGroup): Int {
        val base = if (slotBasePx > 0) slotBasePx else measuredWidth(container)
        return (base * appliedSize / 100).coerceAtLeast(1)
    }

    /** The battery slot's column — 83 px measured on the target device — or the best available estimate. */
    fun measuredWidth(container: ViewGroup): Int {
        val measured = try {
            val id = context.resources.getIdentifier(rom.batteryId, "id", rom.systemUiPackage)
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
