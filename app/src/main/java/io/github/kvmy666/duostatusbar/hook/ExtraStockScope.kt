package io.github.kvmy666.duostatusbar.hook

import android.view.View
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter

/**
 * Resolves the stock signal clusters a ROM keeps outside the icon strip (R11/R14).
 *
 * Honor's `status_bar.xml` puts `signal_cluster_container` under `system_icon_area_fixed`, a sibling of
 * `hw_system_icons`; ColorOS includes `SignalClusterView signal_cluster` inside `notification_icon_area`,
 * outside the `system_icons` include. The strip hide pass therefore never reaches them and the stock
 * signal icons stay visible over the Duo element. Only visibility is changed here: these are shared
 * parents, so they are hidden as a unit and never sized or fitted.
 */
internal object ExtraStockScope {

    /**
     * The views named by [RomAdapter.hideContainerIds] that are safe to hide.
     *
     * A missing id (0) or a missing view is skipped, a `GONE` container is already hidden, and a target
     * that is [exclude] or one of its ancestors is left alone so the main strip/element is never hidden
     * with it.
     */
    fun targets(
        root: View,
        rom: RomAdapter,
        id: (String) -> Int,
        exclude: View? = null
    ): List<View> {
        val out = ArrayList<View>()
        for (name in rom.hideContainerIds) {
            val resId = id(name)
            if (resId == 0) continue
            val view = root.findViewById<View>(resId) ?: continue
            if (view === exclude || isAncestorOf(view, exclude)) continue
            if (view.visibility == View.GONE) continue
            out.add(view)
        }
        return out
    }

    /** True when [candidate] is an ancestor of [view]; hiding it would hide the strip/element too. */
    private fun isAncestorOf(candidate: View, view: View?): Boolean {
        var parent = view?.parent
        while (parent != null) {
            if (parent === candidate) return true
            parent = parent.parent
        }
        return false
    }
}
