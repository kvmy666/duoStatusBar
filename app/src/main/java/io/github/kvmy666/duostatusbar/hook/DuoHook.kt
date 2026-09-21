package io.github.kvmy666.duostatusbar.hook

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 3 entry point for the SystemUI process.
 *
 * Finds the status bar the same OEM-agnostic way the Phase 0 probe did — by watching
 * `WindowManagerImpl.addView` for the window whose layout params say `TYPE_STATUS_BAR` — then hands
 * that view to [DuoIconHost] and starts [DuoStateMonitor].
 *
 * The status bar is inflated a few seconds into boot, so the attach is retried on a short schedule;
 * if it never succeeds nothing is hidden and the stock status bar is untouched (FR-21).
 */
class DuoHook(private val lp: XC_LoadPackage.LoadPackageParam) {

    private val handler = Handler(Looper.getMainLooper())
    private val attaching = AtomicBoolean(false)
    private var app: Application? = null
    private var host: DuoIconHost? = null
    private var monitor: DuoStateMonitor? = null
    private var statusBarRoot: View? = null

    fun install() {
        L.guard("DuoHook install") {
            L.i("=== Duo Status Bar: SystemUI integration ===")
            hookApplication()
        }
    }

    private fun hookApplication() {
        L.guard("DuoHook hook Application") {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lp.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        L.guard("DuoHook Application.onCreate") {
                            val ctx = param.thisObject as Application
                            app = ctx
                            // The gate is read before anything is hooked: while the module is off it
                            // leaves no trace in this process at all, so a fresh install cannot affect
                            // the status bar until someone asks it to.
                            val guard = DuoGuard(ctx)
                            val stage = guard.stage()
                            if (stage == DuoGuard.OFF) {
                                L.i("gated off - nothing hooked. Enable with: ${guard.enableHint}, then restart SystemUI")
                                return@guard
                            }
                            L.i("application ready: ${ctx.packageName} (stage $stage)")
                            host = DuoIconHost(ctx)
                            hookWindowManagerAddView()
                        }
                    }
                }
            )
        }
    }

    private fun hookWindowManagerAddView() {
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                L.guard("DuoHook addView") {
                    val view = param.args.getOrNull(0) as? View ?: return
                    if (statusBarRoot != null) return
                    val layoutParams = view.layoutParams ?: return
                    if (XposedHelpers.getIntField(layoutParams, "type") != TYPE_STATUS_BAR) return
                    statusBarRoot = view
                    L.i("status bar window found: ${view.javaClass.name}")
                    scheduleAttach(attempt = 0)
                }
            }
        }
        L.guard("DuoHook hook addView") {
            XposedHelpers.findAndHookMethod(
                "android.view.WindowManagerImpl", lp.classLoader, "addView",
                View::class.java, ViewGroup.LayoutParams::class.java, callback
            )
        }
    }

    /** The bar inflates its children over a few seconds; retry until the icon strip exists. */
    private fun scheduleAttach(attempt: Int) {
        if (attempt > MAX_ATTEMPTS) {
            L.i("giving up after $MAX_ATTEMPTS attempts - status bar left untouched")
            return
        }
        handler.postDelayed({
            L.guard("DuoHook attach #$attempt") {
                val root = statusBarRoot ?: return@guard
                val ctx = app ?: return@guard
                if (host == null) host = DuoIconHost(ctx)
                val attached = host?.attach(root) ?: false
                if (attached) {
                    if (monitor == null) {
                        monitor = DuoStateMonitor(ctx, host!!).also { it.start() }
                        // Keep the state fresh without polling: a cheap re-read on every layout pass.
                        attachLayoutListener(root)
                    }
                    if (!attaching.compareAndSet(false, true)) return@guard
                    L.i("Duo attached on attempt $attempt")
                } else {
                    scheduleAttach(attempt + 1)
                }
            }
        }, if (attempt == 0) FIRST_DELAY_MS else RETRY_MS)
    }

    private fun attachLayoutListener(root: View) {
        L.guard("DuoHook layout listener") {
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                L.guard("DuoHook onLayout") {
                    host?.reapplyHiding()
                    monitor?.refresh()
                }
            }
        }
    }

    private companion object {
        const val TYPE_STATUS_BAR = 2000
        const val FIRST_DELAY_MS = 2_500L
        const val RETRY_MS = 2_000L
        const val MAX_ATTEMPTS = 6
    }
}
