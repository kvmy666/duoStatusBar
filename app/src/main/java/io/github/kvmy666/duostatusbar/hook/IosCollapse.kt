package io.github.kvmy666.duostatusbar.hook

import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.L

/**
 * Removes one stock slot from the bar while iOS draws the single dual-SIM block (IOS-R2 item 2).
 *
 * A dual-SIM bar has two stock mobile views. iOS draws both rows as one stacked glyph in the data SIM's
 * slot, so the other slot must occupy nothing. This is the only place the replacement host writes a
 * stock view's layout *for a drawing slot*; the user hide path also writes `GONE` + 0x0 for the icons
 * the user selected. The write here is exact: the visibility and layout params are captured before the
 * write and put back bit-for-bit on [restore]. It is only ever constructed for a measurable, visible
 * sibling of the data view; anything else falls back to the approved [IosSkin] suppression, which keeps
 * the slot (C1/C2/M15).
 */
internal class IosCollapse(private val view: View) {

    private val logOnce = LogOnce()

    private var applied = false
    private var originalVisibility = View.VISIBLE
    private var originalWidth = 0
    private var originalHeight = 0

    /** The view whose slot this removes. */
    val target: View get() = view

    /** True while the view is `GONE` + 0×0 because of this collapse. */
    val isApplied: Boolean get() = applied

    /**
     * Captures the exact state, then sets `GONE` + 0×0. Returns false (and touches nothing) when the
     * view was not visible/laid out or has no layout params.
     */
    fun apply(): Boolean {
        if (applied) return true
        var params: ViewGroup.LayoutParams? = null
        var captured = false
        return try {
            if (view.visibility != View.VISIBLE) return false
            if (contentArea(view) <= 0) return false
            params = view.layoutParams ?: return false
            originalVisibility = view.visibility
            originalWidth = params.width
            originalHeight = params.height
            captured = true
            view.visibility = View.GONE
            params.width = 0
            params.height = 0
            view.layoutParams = params
            applied = true
            true
        } catch (t: Throwable) {
            fail("ios-collapse-apply", t)
            // A half-applied collapse must not leave the slot gone without a restorable snapshot.
            applied = false
            if (captured) {
                val restoreParams = params
                try {
                    // Put the size back before the visibility: a throw after the mutation must not
                    // leave a permanent 0x0 VISIBLE (or GONE) slot with no way to restore it.
                    if (restoreParams != null) {
                        restoreParams.width = originalWidth
                        restoreParams.height = originalHeight
                        view.layoutParams = restoreParams
                    }
                    view.visibility = originalVisibility
                } catch (_: Throwable) {
                    try {
                        view.visibility = originalVisibility
                    } catch (_: Throwable) {
                    }
                }
            }
            false
        }
    }

    /**
     * Puts back the exact captured visibility and layout params. Idempotent.
     *
     * `applied` is cleared only once every write succeeded, so a failed restore stays retryable: the
     * slot is still collapsed, and a later [IosReplaceHost.dropRecord]/teardown can try again instead
     * of leaving it gone with no second chance.
     */
    fun restore() {
        if (!applied) return
        try {
            view.visibility = originalVisibility
            view.layoutParams?.let { params ->
                params.width = originalWidth
                params.height = originalHeight
                view.layoutParams = params
            }
            applied = false
        } catch (t: Throwable) {
            fail("ios-collapse-restore", t)
        }
    }

    /**
     * The measurable area of [view] or any visible descendant, mirroring `contentArea` in
     * [IosReplaceHost]. The measured OPLUS mobile outer is 0×0 while its `mobile_group` child is
     * 60×63, so the outer's own bounds say nothing about whether the slot has content.
     */
    private fun contentArea(view: View): Int {
        if (view.visibility != View.VISIBLE) return 0
        var best = if (view.width > 0 && view.height > 0) {
            view.width.toLong() * view.height
        } else {
            0L
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                best = maxOf(best, contentArea(view.getChildAt(i)).toLong())
            }
        }
        return best.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun fail(key: String, t: Throwable) {
        logOnce.once(key) {
            L.w("iOS collapse $key: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
