package io.github.kvmy666.duostatusbar.hook

import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.L

/**
 * Hides the stock status-bar views and puts them back exactly as they were (FR-08/21).
 *
 * Hiding is not destruction: a hidden view stays in the tree, it simply draws nothing and occupies
 * nothing (GONE **and** 0×0, never an overlay). Its original state is remembered first, so switching
 * the module off restores the stock bar without a restart.
 */
internal class StockIconHider {

    private val hiddenOriginals = ArrayList<HiddenState>()
    private val logOnce = LogOnce()

    /** Hides every child of [container] except [keep], remembering each one's original state. */
    fun hideAllExcept(container: ViewGroup, keep: View?) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === keep) continue
            hide(child)
        }
        logOnce.once("hide") {
            L.i("stock status-bar views removed (GONE + 0x0), not overlaid - FR-08")
        }
    }

    /**
     * FR-08b: hides only the views Duo replaces — the battery, and the Wi-Fi/cellular icons — and leaves
     * everything else (silent, vibrate, alarm, …) visible.
     *
     * Walked recursively because the icons live inside the `statusIcons` container, not as direct
     * children of the strip; a container is never hidden itself, only the replaced icons inside it. A
     * view whose slot cannot be read is left alone unless it is plainly the battery, so an unmeasured
     * ROM keeps the user's other icons rather than losing them.
     */
    fun hideReplaced(container: ViewGroup, keep: View?) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === keep) continue
            when {
                isReplaced(child) -> hide(child)
                child is ViewGroup -> hideReplaced(child, keep)
            }
        }
        logOnce.once("hide-replaced") {
            L.i("only the replaced battery/Wi-Fi/cellular views hidden; other icons kept - FR-08b")
        }
    }

    /** True when [view] is one of the icons Duo replaces; see [hideReplaced]. */
    fun isReplaced(view: View): Boolean {
        val slot = slotOf(view)
        if (slot != null) {
            val s = slot.lowercase()
            return s == "battery" || s.startsWith("wifi") || s.startsWith("mobile")
        }
        // No readable slot: a battery view is still replaced, anything else is left for the user.
        return view.javaClass.simpleName.contains("battery", ignoreCase = true)
    }

    /** `StatusBarIconView.getSlot()`, or the OEM `getSlotTag()`; null when neither exists. */
    private fun slotOf(view: View): String? = try {
        view.javaClass.getMethod("getSlot").invoke(view) as? String
    } catch (_: Throwable) {
        try {
            view.javaClass.getMethod("getSlotTag").invoke(view) as? String
        } catch (_: Throwable) {
            null
        }
    }

    /** Hides one view (and its descendants) and remembers what it looked like. */
    fun hide(view: View) {
        val lp = view.layoutParams
        val alreadyHidden = view.visibility == View.GONE && lp != null && lp.width == 0 && lp.height == 0
        // Re-applying layoutParams on every layout pass is what turns "hide again" into a feedback loop:
        // setting them requests another layout, which fires the listener that calls back in here. Once a
        // view is already gone, touching nothing ends the loop (measured: render was being called at
        // frame rate, thousands of times a second).
        if (!alreadyHidden) {
            rememberOriginal(view)
            view.visibility = View.GONE
            lp?.let {
                it.width = 0
                it.height = 0
                view.layoutParams = it
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) hide(view.getChildAt(i))
        }
    }

    /** Puts every hidden view back as it was. Used when the element is switched off or torn down. */
    fun restore() {
        for (state in hiddenOriginals) {
            try {
                state.view.visibility = state.visibility
                state.view.layoutParams?.let { lp ->
                    lp.width = state.width
                    lp.height = state.height
                    state.view.layoutParams = lp
                }
            } catch (t: Throwable) {
                L.w("restore: ${t.message}")
            }
        }
        hiddenOriginals.clear()
    }

    private fun rememberOriginal(view: View) {
        if (hiddenOriginals.any { it.view === view }) return
        val lp = view.layoutParams
        hiddenOriginals.add(HiddenState(view, view.visibility, lp?.width ?: 0, lp?.height ?: 0))
    }

    private data class HiddenState(val view: View, val visibility: Int, val width: Int, val height: Int)
}
