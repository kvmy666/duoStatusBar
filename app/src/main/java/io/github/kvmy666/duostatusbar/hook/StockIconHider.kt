package io.github.kvmy666.duostatusbar.hook

import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.L
import java.lang.ref.WeakReference

/**
 * Hides the stock status-bar views and puts them back exactly as they were (FR-08/21).
 *
 * Hiding is not destruction: a hidden view stays in the tree, it simply draws nothing and occupies
 * nothing (GONE **and** 0×0, never an overlay). Its original state is remembered first, so switching
 * the module off restores the stock bar without a restart.
 *
 * Two modes share this class:
 *  - [hideAllExcept] is the legacy "hide all other icons" switch and still hides every child.
 *  - [hideReplaced] is the per-icon path: the icons Duo replaces (battery/Wi-Fi/cellular) plus the
 *    exact keys the user selected. A battery is always hidden with [hideKeepLayout], because the
 *    overlay is positioned from its frame; a key that matches no view is a no-op, never a hide-all.
 */
internal class StockIconHider {

    private val hiddenOriginals = ArrayList<HiddenState>()
    private val logOnce = LogOnce()

    /** Every container the last full passes covered, with the child count seen, for [needsReapply]. */
    private val appliedContainers = java.util.IdentityHashMap<ViewGroup, Int>()

    /**
     * The exact views a pass hid, per container. A keyguard/unlock re-inflates the strip with new view
     * objects but the same child count; without this the remembered hidden views (now detached, still
     * GONE) would make [needsReapply] say false and the re-added stock icons would stay visible.
     */
    private val appliedViews = HashMap<ViewGroup, MutableList<WeakReference<View>>>()

    /**
     * Hides every child of [container] except [keep], remembering each one's original state.
     *
     * [keepLayout] is hidden with [hideKeepLayout] instead (INVISIBLE, layout untouched). The overlay path
     * uses it for the battery: the element is drawn outside this container and positioned from the
     * battery, so the battery must keep a real position to measure against - and that position has to
     * survive a rotation, which a GONE 0x0 view cannot.
     */
    fun hideAllExcept(
        container: ViewGroup,
        keep: View?,
        keepLayout: View? = null,
        alsoKeep: Collection<View> = emptyList()
    ) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === keep || alsoKeep.any { it === child }) continue
            if (child === keepLayout) hideKeepLayout(child) else hide(child)
        }
        noteApplied(container)
        logOnce.once("hide") {
            L.i("stock status-bar views removed (GONE + 0x0), not overlaid - FR-08")
        }
    }

    /**
     * FR-08b/A4: hides only the views Duo replaces — the battery, and the Wi-Fi/cellular icons — plus
     * the icons named by [selected] (the stable keys from [StockIconKey]).
     *
     * Walked recursively because the icons live inside the `statusIcons` container, not as direct
     * children of the strip; a container is never hidden itself, only the replaced/selected icons
     * inside it. A view whose slot, id and class cannot be read is left alone, so an unmeasured ROM
     * keeps the user's other icons rather than losing them. A key in [selected] that matches no view
     * is ignored: only the named views are hidden.
     *
     * A battery is always hidden with [hideKeepLayout] (INVISIBLE, layout kept) even when the caller
     * passed no [keepLayout], because its frame is the overlay anchor (A0 audit). Never GONE.
     */
    fun hideReplaced(
        container: ViewGroup,
        keep: View?,
        keepLayout: View? = null,
        alsoKeep: Collection<View> = emptyList(),
        selected: Set<String> = emptySet()
    ) {
        walk(container, keep, keepLayout, alsoKeep, selected, HashMap())
        noteApplied(container)
        logOnce.once("hide-replaced") {
            L.i("only the replaced battery/Wi-Fi/cellular and selected status views hidden; " +
                "other icons kept - FR-08b")
        }
    }

    /**
     * Hides [view] but keeps it laid out: `INVISIBLE`, not `GONE`/0x0. The stock view stops drawing but
     * keeps its real position, which the overlay path needs as its anchor. Remembered like any other hide,
     * so switching off restores it exactly.
     */
    fun hideKeepLayout(view: View) {
        if (view.visibility == View.INVISIBLE) return
        rememberOriginal(view)
        view.visibility = View.INVISIBLE
        markApplied(view, View.INVISIBLE)
    }

    /**
     * R3 A6 (plain Duo): true when a full hide pass is still needed.
     *
     * A layout tick on the shade fires per frame; walking the whole tree each time was the measured
     * cost. The ROM only re-shows its icons by re-inflating/re-adding them (container or child count
     * changes) or by making a remembered hidden view visible again; when neither happened the previous
     * hide is still in force. The settle pass always runs the full walk, so nothing is left hidden or
     * shown late.
     */
    fun needsReapply(container: ViewGroup): Boolean {
        val childCount = appliedContainers[container] ?: return true
        if (container.childCount != childCount) return true
        if (hiddenOriginals.any { it.appliedVisibility != it.view.visibility }) return true
        val refs = appliedViews[container] ?: return true
        return refs.any { ref ->
            val view = ref.get()
            view == null || !isWithin(view, container)
        }
    }

    /** Remembers that a full pass just ran for [container], with the views it left hidden. */
    private fun noteApplied(container: ViewGroup) {
        appliedContainers[container] = container.childCount
        val refs = ArrayList<WeakReference<View>>()
        for (state in hiddenOriginals) {
            if (isWithin(state.view, container)) refs.add(WeakReference(state.view))
        }
        appliedViews[container] = refs
    }

    /** True when [view] is still in [container]'s tree (a re-inflated strip detaches the old views). */
    private fun isWithin(view: View, container: ViewGroup): Boolean {
        var parent: View? = view
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent as? View
        }
        return false
    }

    /**
     * Records that a pass covered [container] even when it hid icons in a nested strip (the QS
     * cluster's `statusIcons`); the caller only needs [needsReapply] to stay false when nothing
     * re-appeared.
     */
    fun notePass(container: ViewGroup) {
        noteApplied(container)
    }

    /**
     * Item 8-runtime: the iOS replacement host's hide pass.
     *
     * iOS replaces the icons *inside* the stock views, so this pass never hides a view in [preserve]
     * (the adopted/replaceable icons, computed by the caller as the inventory entries whose key is not
     * selected). It hides exactly a view whose stable [StockIconKey] is in [selected], and with the
     * legacy [hideOthers] flag it also hides the non-replaced icons (alarm, sound, Bluetooth…). A
     * selected battery is `GONE` + 0×0 like any other hidden icon; the `hideKeepLayout` rule is only for
     * a battery hidden by the legacy "all" switch, where an overlay may still need its frame.
     *
     * Occurrence counting is shared with [StockIconKey.inventory], so `id:mobile_combo#2` names the same
     * SIM here as in the settings list.
     */
    fun hideForIos(
        container: ViewGroup,
        preserve: Collection<View>,
        selected: Set<String>,
        hideOthers: Boolean
    ) {
        walkForIos(container, preserve, selected, hideOthers, HashMap())
        noteApplied(container)
        logOnce.once("hide-ios") {
            L.i("iOS stock icons: selected/hide-others applied - $selected")
        }
    }

    /** True when [view] is one of the icons Duo replaces; see [hideReplaced]. */
    fun isReplaced(view: View): Boolean = StockIconKey.kindOf(view).replaced

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
        markApplied(view, View.GONE)
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
        appliedContainers.clear()
        appliedViews.clear()
    }

    /**
     * One depth-first pass. Occurrence counting is shared with [StockIconKey.inventory], so the key
     * `id:mobile_combo#2` means the same SIM in the settings list and in this hide pass.
     */
    private fun walk(
        parent: View,
        keep: View?,
        keepLayout: View?,
        alsoKeep: Collection<View>,
        selected: Set<String>,
        counts: MutableMap<String, Int>
    ) {
        if (parent !is ViewGroup) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child === keep || alsoKeep.any { it === child }) continue
            if (StockIconKey.isIgnored(child)) continue
            val kind = StockIconKey.kindOf(child)
            if (kind == StockIconKind.IGNORED) {
                // A container (or an unreadable wrapper): walk into it, never hide it.
                walk(child, keep, keepLayout, alsoKeep, selected, counts)
                continue
            }
            val base = StockIconKey.baseKey(child)
            val occurrence = (counts[base] ?: 0) + 1
            counts[base] = occurrence
            val key = StockIconKey.key(base, occurrence)
            if (!kind.replaced && key !in selected) continue
            if (kind == StockIconKind.BATTERY || child === keepLayout) {
                hideKeepLayout(child)
            } else {
                hide(child)
            }
        }
    }

    /**
     * The iOS pass: walk every icon (counted in the same order as [StockIconKey.inventory]) but hide
     * only the selected keys and, with [hideOthers], the non-replaced icons. A [preserve] view is
     * counted and then skipped, so its occurrence suffix still names it correctly.
     */
    private fun walkForIos(
        parent: View,
        preserve: Collection<View>,
        selected: Set<String>,
        hideOthers: Boolean,
        counts: MutableMap<String, Int>
    ) {
        if (parent !is ViewGroup) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (StockIconKey.isIgnored(child)) continue
            val kind = StockIconKey.kindOf(child)
            if (kind == StockIconKind.IGNORED) {
                walkForIos(child, preserve, selected, hideOthers, counts)
                continue
            }
            val base = StockIconKey.baseKey(child)
            val occurrence = (counts[base] ?: 0) + 1
            counts[base] = occurrence
            val key = StockIconKey.key(base, occurrence)
            if (preserve.any { it === child }) continue
            val hideSelected = key in selected
            val hideNonReplaced = hideOthers && !kind.replaced
            if (!hideSelected && !hideNonReplaced) continue
            if (kind == StockIconKind.BATTERY && !hideSelected) {
                hideKeepLayout(child)
            } else {
                hide(child)
            }
        }
    }

    private fun rememberOriginal(view: View) {
        if (hiddenOriginals.any { it.view === view }) return
        val lp = view.layoutParams
        hiddenOriginals.add(HiddenState(view, view.visibility, lp?.width ?: 0, lp?.height ?: 0))
    }

    /** The visibility the last hide pass left [view] in, so a re-show is detectable without a walk. */
    private fun markApplied(view: View, visibility: Int) {
        hiddenOriginals.firstOrNull { it.view === view }?.appliedVisibility = visibility
    }

    private class HiddenState(
        val view: View,
        val visibility: Int,
        val width: Int,
        val height: Int
    ) {
        var appliedVisibility: Int = visibility
    }
}
