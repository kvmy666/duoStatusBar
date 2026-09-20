package io.github.kvmy666.duostatusbar.probe

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.atomic.AtomicBoolean

/**
 * P-02 — dumps the live status-bar view hierarchy.
 *
 * OEM-agnostic on purpose: instead of guessing resource ids, we watch `WindowManagerImpl.addView`
 * and pick the view that is added as the STATUS_BAR window, then walk its children. This is the
 * same detection strategy the sibling Auto Expand module uses, and it is what told us (in Phase 0)
 * which container actually holds the battery/Wi-Fi/signal views on OxygenOS 16.
 *
 * Log-only: nothing is intercepted, nothing is modified.
 */
internal object ViewProbe {

    private const val TYPE_STATUS_BAR = 2000
    private const val MAX_DEPTH = 6
    private const val FLUSH_AT = 4000

    private val done = AtomicBoolean(false)

    fun install(lp: XC_LoadPackage.LoadPackageParam) {
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                L.guard("P-02 after addView") {
                    val view = param.args.getOrNull(0) as? View ?: return
                    if (done.get()) return
                    val layoutParams = view.layoutParams ?: return
                    if (XposedHelpers.getIntField(layoutParams, "type") != TYPE_STATUS_BAR) return
                    if (!done.compareAndSet(false, true)) return
                    L.i("P-02 STATUS_BAR window view: ${view.javaClass.name}")
                    val buffer = StringBuilder()
                    walk(view, 0, buffer)
                    if (buffer.isNotEmpty()) L.i(buffer.toString())
                    L.i("P-02 hierarchy dump complete")
                }
            }
        }
        L.guard("P-02 hook addView(view,params)") {
            XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerImpl", lp.classLoader, "addView",
                View::class.java, ViewGroup.LayoutParams::class.java, callback
            )
        }
        L.guard("P-02 hook addView(view,params,display)") {
            XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerImpl", lp.classLoader, "addView",
                View::class.java, ViewGroup.LayoutParams::class.java, android.view.Display::class.java, callback
            )
        }
    }

    private fun walk(view: View, depth: Int, out: StringBuilder) {
        if (depth > MAX_DEPTH) return

        val id = try {
            if (view.id == View.NO_ID) "-" else view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            "#${view.id}"
        }
        // getBoundsOnScreen is a hidden API; getLocationOnScreen is public and enough for the report.
        val location = IntArray(2)
        try {
            view.getLocationOnScreen(location)
        } catch (_: Throwable) {
        }
        out.append("\nP-02 ")
            .append("  ".repeat(depth))
            .append(view.javaClass.name)
            .append(" id=").append(id)
            .append(" vis=").append(view.visibility)
            .append(' ').append(view.width).append('x').append(view.height)
            .append(" @").append(location[0]).append(',').append(location[1])

        if (out.length > FLUSH_AT) {
            L.i(out.toString())
            out.setLength(0)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), depth + 1, out)
        }
    }
}
