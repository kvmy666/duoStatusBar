package io.github.kvmy666.duostatusbar.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
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

    /**
     * The keyguard/shade window (FR-03b). On the lock screen the `StatusBar` window still draws our
     * element, but the keyguard's own status bar is a *second* bar in this window and sits on top of
     * it, so its stock icons have to be hidden too or the lock screen shows both.
     */
    private var shadeRoot: View? = null

    /** The pulled-down shade's header, handed over by [hookShadeHeader]. */
    private var shadeHeader: View? = null

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
            hookShadeHeader()
            hookSettingsChanges(ctx)
                        }
                    }
                }
            )
        }
    }

    /**
     * The app's half of the channel. When the settings screen writes something it broadcasts, and this
     * re-reads and applies it live: size and offset change without re-injecting anything, switching off puts
     * the stock icons back exactly as they were, and switching on re-attaches.
     */
    private fun hookSettingsChanges(ctx: Context) {
        L.guard("DuoHook settings receiver") {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    L.guard("DuoHook settings changed") {
                        val stage = DuoGuard(ctx).stage()
                        val settings = host?.refreshSettings()
                        when {
                            stage == DuoGuard.OFF -> host?.teardown()
                            host?.duo == null -> scheduleAttach(attempt = 0)
                            else -> monitor?.refresh()
                        }
                        report(ctx, stage, settings)
                    }
                }
            }
            ctx.registerReceiver(
                receiver,
                IntentFilter(DuoSettingsClient.ACTION_SETTINGS_CHANGED),
                Context.RECEIVER_EXPORTED
            )
            L.i("listening for app settings changes")
        }
    }

    /** Tells the app what the module is actually doing, so diagnostics shows facts, not intentions. */
    private fun report(ctx: Context, stage: Int, settings: ModuleSettings?) {
        L.guard("DuoHook status report") {
            val element = host?.duo
            val renderer = when (element) {
                is DuoRiveView -> "Rive"
                is DuoCanvasView -> "Canvas"
                else -> "none"
            }
            val status = buildString {
                append("stage=").append(stage)
                append(" · renderer=").append(renderer)
                append(" · attached=").append(element != null)
                settings?.let {
                    append(" · size=").append(it.sizePercent).append('%')
                    append(" · offset=").append(it.offsetX).append("dp")
                    append(" · percent=").append(it.showPercent)
                    append(" · rev=").append(it.revision)
                }
                append(" · riveAttempts=").append(DuoGuard(ctx).attempts())
            }
            L.i("status -> app: $status")
            DuoSettingsClient.report(ctx, status)
        }
    }

    private fun hookWindowManagerAddView() {
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                L.guard("DuoHook addView") {
                    val view = param.args.getOrNull(0) as? View ?: return
                    val layoutParams = view.layoutParams ?: return
                    when (XposedHelpers.getIntField(layoutParams, "type")) {
                        TYPE_STATUS_BAR -> {
                            if (statusBarRoot != null) return
                            statusBarRoot = view
                            L.i("status bar window found: ${view.javaClass.name}")
                            scheduleAttach(attempt = 0)
                        }
                        TYPE_NOTIFICATION_SHADE -> {
                            if (shadeRoot != null) return
                            shadeRoot = view
                            L.i("keyguard/shade window found: ${view.javaClass.name} (FR-03b)")
                        }
                    }
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
                    // The keyguard and the shade header may already be on screen (the element attaches
                    // at boot, they come later, but a re-attach after rotation can land either way).
                    shadeRoot?.let { shade -> attachExtraBars(shade) }
                    L.i("Duo attached on attempt $attempt")
                    report(ctx, DuoGuard(ctx).stage(), null)
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
            // The keyguard's bar is inflated into the shade window when the lock screen appears, and the
            // shade window is the one that changes then - so its layout pass is the trigger for the
            // second hiding pass (FR-03b).
            val shade = shadeRoot
            shade?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                L.guard("DuoHook onShadeLayout") {
                    attachExtraBars(shade)
                    // Opening the shade re-shows the main bar's icon views, so the hide pass has to run
                    // again here - the status bar's own layout pass does not fire for a shade drag. The
                    // ROM also re-shows them *after* the drag settles, so this runs again a moment later.
                    host?.reapplyHiding()
                    handler.postDelayed({
                        L.guard("DuoHook shade settle") {
                            host?.reapplyHiding()
                            shadeHeader?.let { host?.attachShadeHeader(it) }
                        }
                    }, SHADE_SETTLE_MS)
                }
            }
        }
    }

    /**
     * FR-03b: the two extra bars that carry their own icon strip, both inside the shade window. The ids
     * are read out of the device's SystemUI (`reverse/SystemUI-device.apk`): the lock screen's
     * `KeyguardStatusBarView` uses `system_icons`, and the pulled-down shade's header
     * (`combined_qs_header`) uses `shade_header_system_icons`. Both draw their own stock icons, which is
     * why the lock screen and the shade looked untouched until they were handled.
     */
    private fun attachExtraBars(shade: View) {
        val host = host ?: return
        host.attachExtra("keyguard bar", shade, "system_icons")
        // The shade header is not in this window; it arrives through hookShadeHeader. It is built early,
        // often before the main bar has attached, so this is also where a failed attempt is retried.
        shadeHeader?.let { host.attachShadeHeader(it) }
    }

    /**
     * FR-03b: the pulled-down shade's header.
     *
     * Decompiled from the device's SystemUI rather than guessed:
     * `com.android.systemui.qs.dagger.OplusQSModuleEx.providesShadeHeaderView` takes the shade window,
     * finds the `qs_header_stub` ViewStub inside it, sets it to `R.layout.combined_qs_header` and
     * inflates it - returning the header view. So the header *is* in the shade window's tree, but only
     * after the stub inflates, which is why searching for it at boot found nothing. This takes the
     * returned view, which is the only moment it is handed over directly.
     */
    private fun hookShadeHeader() {
        // The controller that owns the header is the reliable hand-over: it is handed the header view
        // directly, whatever inflated it.
        L.guard("DuoHook shade header controller") {
            val cls = XposedHelpers.findClass(
                "com.android.systemui.shade.ShadeHeaderController", lp.classLoader
            )
            XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook shade header ctor") {
                        val view = param.args.firstOrNull() as? View ?: return@guard
                        L.i("shade header view: ${view.javaClass.name}")
                        shadeHeader = view
                        view.post { shadeHeader?.let { host?.attachShadeHeader(it) } }
                    }
                }
            })
        }
        L.guard("DuoHook shade header") {
            XposedHelpers.findAndHookMethod(
                "android.view.ViewStub", lp.classLoader, "inflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        L.guard("DuoHook stub inflate") {
                            val view = param.result as? View ?: return@guard
                            val ctx = app ?: return@guard
                            val id = ctx.resources.getIdentifier(
                                "shade_header_system_icons", "id", "com.android.systemui"
                            )
                            if (id == 0 || view.findViewById<View>(id) == null) return@guard
                            L.i("shade header inflated from a stub: ${view.javaClass.name}")
                            shadeHeader = view
                            view.post { shadeHeader?.let { host?.attachShadeHeader(it) } }
                        }
                    }
                }
            )
        }
    }

    private companion object {
        const val TYPE_STATUS_BAR = 2000

        /** `WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE`, which is @hide. */
        const val TYPE_NOTIFICATION_SHADE = 2040
        const val FIRST_DELAY_MS = 2_500L

        /** After a shade drag settles, the ROM re-shows its icon views once more. */
        const val SHADE_SETTLE_MS = 400L
        const val RETRY_MS = 2_000L
        const val MAX_ATTEMPTS = 6
    }
}
