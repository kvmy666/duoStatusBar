package io.github.kvmy666.duostatusbar.probe

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PHASE 0 EVIDENCE COLLECTOR — read-only. It logs; it never changes SystemUI behaviour, and every
 * step is guarded so a failure produces a log line instead of a broken status bar (FR-21).
 *
 *   P-01  which classes we need actually exist + their real method names (FR-26)  -> ProbeReport
 *   P-02  the live status-bar view hierarchy                                       -> ViewProbe
 *   P-03  can the SystemUI process load our native Rive library?                   -> ProbeReport
 *   P-04  the triggers we need: screen on/off, unlock, rotation                    -> here
 *   P-05  status bar metrics (height, density, orientation)                        -> ProbeReport
 *
 * Read back with:  adb logcat -s DuoSB
 */
class ProbeHook(private val lp: XC_LoadPackage.LoadPackageParam) {

    private val reported = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var app: Application? = null

    fun install() {
        L.guard("P-00 install") {
            L.i("================ DUO STATUS BAR / PHASE 0 PROBES ================")
            L.i("P-00 uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}")
            L.i("P-00 classLoader=${lp.classLoader}")
            // P-01 needs neither the application context nor the view hierarchy, so it runs
            // synchronously: a timer could be lost (and the log buffer rotates fast here).
            L.guard("P-01 inventory") { ProbeReport.inventory(lp) }
            hookApplicationOnCreate()
            handler.postDelayed({ report() }, 8_000L)
        }
    }

    // ------------------------------------------------------------------ P-04 application context

    private fun hookApplicationOnCreate() {
        L.guard("P-04 hook Application.onCreate") {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lp.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        L.guard("P-04 after Application.onCreate") {
                            val ctx = param.thisObject as Application
                            app = ctx
                            L.i("P-04 Application ready: ${ctx.packageName}")
                            registerLifecycleReceiver(ctx)
                            ViewProbe.install(lp)
                        }
                    }
                }
            )
        }
    }

    private fun registerLifecycleReceiver(ctx: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val extra =
                    if (action == Intent.ACTION_CONFIGURATION_CHANGED)
                        " orientation=${c?.resources?.configuration?.orientation}"
                    else ""
                L.i("P-04 EVENT $action$extra")
            }
        }
        ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        L.i("P-04 lifecycle receiver registered (screen on/off, user present, rotation)")
    }

    // ------------------------------------------------------------------------- final report

    private fun report() {
        if (!reported.compareAndSet(false, true)) return
        L.guard("P-03 native library") { ProbeReport.nativeLibrary(app) }
        L.guard("P-05 metrics") { ProbeReport.metrics(app) }
        L.i("================ PHASE 0 PROBES DONE ================")
    }
}
