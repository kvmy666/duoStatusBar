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
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
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

    /** The status-bar touch hook is installed once per process. */
    private val touchHooked = AtomicBoolean(false)

    /** The debug diagnostic dump is written to the log once per process, not on every settings change. */
    private val diagnosticsLogged = AtomicBoolean(false)
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

    /**
     * The debounced "settings changed" apply. A drag on the app's size/position slider broadcasts on
     * every tick; running the whole re-read for each one puts a burst of binder calls on System UI's
     * main thread. Waiting for the burst to settle keeps the bar responsive and, more importantly,
     * keeps the watchdog from restarting System UI.
     */
    private val settingsApply = Runnable {
        val ctx = app ?: return@Runnable
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

    fun install() {
        L.guard("DuoHook install") {
            L.i("=== Duo Status Bar ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) ===")
            hookApplication()
        }
    }

    /** The single host for this process, created on first use. */
    private fun ensureHost(ctx: Context): DuoIconHost {
        host?.let { return it }
        return DuoIconHost(ctx).also { host = it }
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
                            // Heartbeat before the gate: it is how the app tells "LSPosed never injected
                            // the module" apart from "the module ran but is switched off". Two signals:
                            // a Settings.Global stamp, and a provider report. The provider needs no
                            // permission, so the About screen cannot show a false "never" on a ROM that
                            // denies SystemUI WRITE_SECURE_SETTINGS (the Global write then fails silently).
                            L.guard("DuoHook heartbeat") {
                                guard.noteLoaded()
                                DuoSettingsClient.report(ctx, "loaded · stage=$stage")
                                // A fresh load clears any previous fallback alert; a fallback during this
                                // run is reported from DuoIconHost when it happens.
                                DuoSettingsClient.reportFallback(ctx, "")
                            }
                            if (stage == DuoGuard.OFF) {
                                L.i("gated off - nothing hooked. Enable with: ${guard.enableHint}, then restart SystemUI")
                                return@guard
                            }
                            L.i("application ready: ${ctx.packageName} (stage $stage)")
                            ensureHost(ctx)
                            hookWindowManagerAddView()
                            hookShadeHeader()
                            hookStatusIconContainer()
                            hookBarAppearance()
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
                    // The size only takes effect on a fresh start, so the app asks for one here. This
                    // module is the only side that can do it: it lives inside System UI, and killing its
                    // own process makes Android bring System UI straight back.
                    if (intent?.action == DuoPrefs.ACTION_RESTART_SYSTEMUI) {
                        L.i("restart requested by the app - restarting System UI")
                        handler.postDelayed({
                            try {
                                android.os.Process.killProcess(android.os.Process.myPid())
                            } catch (t: Throwable) {
                                L.w("restart failed: ${t.javaClass.simpleName}: ${t.message}")
                            }
                        }, RESTART_DELAY_MS)
                        return
                    }
                    // Coalesce a burst of changes (a slider drag broadcasts on every tick) into one
                    // apply. Each apply does synchronous provider reads on System UI's main thread, so
                    // without this a drag could block it and get System UI restarted by the watchdog.
                    handler.removeCallbacks(settingsApply)
                    handler.postDelayed(settingsApply, SETTINGS_DEBOUNCE_MS)
                }
            }
            ctx.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(DuoSettingsClient.ACTION_SETTINGS_CHANGED)
                    addAction(DuoPrefs.ACTION_RESTART_SYSTEMUI)
                },
                Context.RECEIVER_EXPORTED
            )
            L.i("listening for app settings changes")
        }
    }

    /** Tells the app what the module is actually doing, so diagnostics shows facts, not intentions. */
    private fun report(ctx: Context, stage: Int, settings: ModuleSettings?) {
        L.guard("DuoHook status report") {
            val element = host?.duo
            val renderer = element?.rendererName ?: "none"
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
            reportDiagnostics(ctx, stage, settings, element)
        }
    }

    /**
     * Sends the full diagnostic dump to the app (so its "Save status to a file" button ships it) and
     * writes it to the log once per process. Always on — not debug-only — so any release user can pull a
     * complete bug report without a special build.
     */
    private fun reportDiagnostics(ctx: Context, stage: Int, settings: ModuleSettings?, element: DuoElement?) {
        val dump = Diag.collect(ctx, statusBarRoot, stage, settings, element)
        DuoSettingsClient.reportDump(ctx, dump)
        if (diagnosticsLogged.compareAndSet(false, true)) {
            L.i("--- diagnostic dump (debug build) ---")
            Diag.log(dump)
            L.i("--- end diagnostic dump ---")
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
                val attached = ensureHost(ctx).attach(root)
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
                    hookStatusBarTouch(root)
                    L.i("Duo attached on attempt $attempt")
                    report(ctx, DuoGuard(ctx).stage(), null)
                } else {
                    scheduleAttach(attempt + 1)
                }
            }
        }, if (attempt == 0) FIRST_DELAY_MS else RETRY_MS)
    }

    /**
     * FR-05/18: drives the element's own tap gestures from the status bar's touch stream.
     *
     * The status bar consumes touches before they reach the injected element view, so its OnTouch
     * listener never fired and the element's configured actions did nothing - only Auto Expand's edge
     * zones reacted. This hooks `dispatchTouchEvent` on the bar (the same layer Auto Expand uses) and
     * lets [DuoIconHost.handleElementTouch] claim only the touches that land on the element. Auto
     * Expand's zones yield to those touches, so one tap means one action.
     *
     * The runtime class is checked for a *declared* override: hooking an inherited
     * `ViewGroup.dispatchTouchEvent` would intercept every touch in the process.
     */
    private fun hookStatusBarTouch(root: View) {
        if (!touchHooked.compareAndSet(false, true)) return
        L.guard("DuoHook status bar touch") {
            // `dispatchTouchEvent` is inherited from ViewGroup, so this hooks the base method and the
            // identity guard below keeps it to the status-bar window only (hooking without the guard
            // would run for every touch in the process).
            val method = try {
                XposedHelpers.findMethodExact(
                    root.javaClass, "dispatchTouchEvent", android.view.MotionEvent::class.java
                )
            } catch (t: Throwable) {
                L.w("no dispatchTouchEvent on ${root.javaClass.simpleName}: ${t.message}")
                return@guard
            }
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.thisObject !== root) return
                    try {
                        val event = param.args.getOrNull(0) as? android.view.MotionEvent ?: return
                        host?.handleElementTouch(event)
                    } catch (t: Throwable) {
                        L.w("element touch: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            })
            L.i("status bar touch hook installed (${root.javaClass.simpleName}) - FR-05/18")
        }
    }

    private fun attachLayoutListener(root: View) {
        L.guard("DuoHook layout listener") {
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                L.guard("DuoHook onLayout") {
                    // Hide pass only. Do NOT re-read system state here: the status bar re-lays out on
                    // every clock tick, and refreshing Wi-Fi/cell/etc plus re-rendering Rive on each one
                    // kept SystemUI awake at ~1 Hz — the battery drain. State is already broadcast-driven.
                    host?.reapplyHiding()
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
    /**
     * FR-03b: hides icon views as the ROM adds them.
     *
     * A hiding pass on a layout change is not enough on the shade header - the ROM repopulates its
     * `StatusIconContainer` afterwards, so the icons came back after every pass. `StatusIconContainer`
     * is the one container all three bars use for their icons, so hooking its `addView` catches every
     * icon in every bar at the moment it arrives.
     */
    private fun hookStatusIconContainer() {
        L.guard("DuoHook icon container") {
            val cls = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.StatusIconContainer", lp.classLoader
            )
            XposedBridge.hookAllMethods(cls, "addView", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook icon added") {
                        val child = param.args.firstOrNull() as? View ?: return@guard
                        host?.onStatusIconAdded(child)
                    }
                }
            })
        }
    }

    /**
     * Captures the colour SystemUI tints its own icons, so the Duo element can match the bar (black on a
     * light bar, white on a dark one) - the "chameleon" behaviour across apps.
     *
     * Both entry points are best-effort: `onDarkChanged(ArrayList, float, int)` is the AOSP one and
     * `setIconColor(int, boolean)` the spellings OEM builds added around it. When neither exists the
     * element falls back to the system day/night setting (see [BarTint]).
     */
    private fun hookBarAppearance() {
        L.guard("DuoHook bar appearance") {
            val cls = XposedHelpers.findClass(
                "com.android.systemui.statusbar.StatusBarIconView", lp.classLoader
            )
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        // Both methods carry exactly one int: `onDarkChanged`'s tint and `setIconColor`'s
                        // colour. The first int in the argument list is that value either way.
                        for (arg in param.args) {
                            if (arg is Int) {
                                if (BarTint.update(arg)) monitor?.onBarAppearanceChanged()
                                break
                            }
                        }
                    } catch (t: Throwable) {
                        L.w("bar tint: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            }
            XposedBridge.hookAllMethods(cls, "onDarkChanged", callback)
            XposedBridge.hookAllMethods(cls, "setIconColor", callback)
            L.i("bar appearance hook installed (StatusBarIconView) - FR-15b")
        }
    }

    private fun hookShadeHeader() {        // The controller that owns the header is the reliable hand-over: it is handed the header view
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

        /** Give the restart broadcast a moment to finish before the process goes. */
        const val RESTART_DELAY_MS = 300L

        /** How long a burst of settings changes is allowed to settle before one apply runs. */
        const val SETTINGS_DEBOUNCE_MS = 250L
        const val RETRY_MS = 2_000L
        const val MAX_ATTEMPTS = 6
    }
}
