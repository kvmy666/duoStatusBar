package io.github.kvmy666.duostatusbar.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.Breadcrumb
import io.github.kvmy666.duostatusbar.hook.events.flashlightStateFromDispatch
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.hook.rom.RomMarkers
import io.github.kvmy666.duostatusbar.hook.rom.RomResolver
import io.github.kvmy666.duostatusbar.hook.rom.RomResources
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import io.github.kvmy666.duostatusbar.ui.RestartPolicy
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

    /**
     * Defense in depth for a direct [install] call: MainHook owns the per-process claim
     * ([ProcessState.claimInstall]), so this only stops the same host instance from running its install
     * body twice. A failed install leaves it set; MainHook's claim reset is what allows a retry with a
     * fresh host, which is the path that matters.
     */
    private val installEntered = AtomicBoolean(false)

    /**
     * R3 A6: the one cached feature snapshot every Duo/iOS entry point checks. It is recomputed in
     * [start], [enable], [settingsApply] and after an attach, never inside a hot hook.
     */
    private var features: FeatureSet =
        FeatureSet(BarOwner.NONE, DuoRenderer.NONE, emptySet(), SharedHook.entries.toSet())

    /** The settings revision whose [featureLogLine] was already written, so boot/apply log once. */
    private var loggedFeatureRevision: Long = Long.MIN_VALUE
    private var loggedFeatureOwner: BarOwner? = null

    /** The Duo-only registrations that are currently installed; diffed on every feature change. */
    private val registrations = RegistrationSet<DuoRegistration, () -> Unit>(
        installer = { installDuoRegistration(it) },
        uninstaller = { remove -> remove() }
    )

    /**
     * The boot hook set (shade header, vendor QS cluster, icon container, tint, orientation) is installed
     * once per process. A live enable re-enters [enable] and must not double every callback.
     */
    private val hooksInstalled = AtomicBoolean(false)

    /** The one pending post-shade-layout pass, so overlapping layout passes do not stack settles. */
    private var shadeSettle: Runnable? = null

    /** R3 A6: the header attach is attempted at most once per settle, not on every layout tick. */
    private var shadeSettleHeaderAttempted = false

    /** Panel expansion state from SystemUI; authoritative for main/extras visibility. */
    private var qsExpanded = false
    private var notificationExpanded = false
    private var shadedOrQsExpanded = false

    /** True only after the per-panel callback installed. Without it, keep attach fallback ownership. */
    @Volatile
    private var panelHookInstalled = false

    /** Last logged panel owner, so owner changes are logged once instead of every panel event. */
    private var lastPanelOwner: String? = null

    /** The shade window that already owns the one layout listener. */
    private var shadeLayoutWindow: View? = null

    /** The one shared shade layout listener, kept so it can be removed when no owner remains (D). */
    private var shadeLayoutListener: View.OnLayoutChangeListener? = null

    /**
     * The status-bar layout listener is installed once per root. A live disable/re-enable must not
     * stack a second listener on the same view: every extra callback runs the hide pass on every
     * status-bar layout, which is the same 1 Hz battery drain the single listener already avoids.
     */
    private var layoutListenerRoot: View? = null
    private var layoutListener: View.OnLayoutChangeListener? = null

    /**
     * A context to read the settings and register the settings receiver with. It is the [Application]
     * once one exists, and the status-bar window's own context on a ROM where the module is injected
     * after the Application was created (see [start]).
     */
    private var app: Context? = null
    private var host: DuoIconHost? = null

    /**
     * R7 P4: how iOS mode is delivered right now. [IosRoute.V2] is the system-native resource engine
     * + battery hook; [IosRoute.NONE] is iOS off or an unsupported family (the stock bar is kept).
     */
    @Volatile
    private var iosRoute: IosRoute = IosRoute.NONE

    /** R3 A6: the same bound for the plain-Duo extra bars, which had no throttle at all. */
    private var lastDuoExtraAttachAt = 0L

    /** The orientation whose settings are currently applied, so a config change can switch sets. */
    private var appliedOrientation = Configuration.ORIENTATION_UNDEFINED

    /** The next [settingsApply] came from a rotation, so the saved size for that orientation is drawn. */
    private val orientationPending = AtomicBoolean(false)
    private var monitor: DuoStateMonitor? = null
    private var statusBarRoot: View? = null

    /** How many unreachable-provider re-reads have been scheduled since the last successful read. */
    private var settingsRereadAttempt = 0

    /** One-shot logging gates, so a repeated apply does not flood the report with the same cause. */
    private val logOnce = LogOnce()

    /** Wall-clock ms of the last restart broadcast accepted by this process, for per-process debounce. */
    @Volatile private var lastRestartRequestAt = 0L

    /**
     * The keyguard/shade window (FR-03b). On the lock screen the `StatusBar` window still draws our
     * element, but the keyguard's own status bar is a *second* bar in this window and sits on top of
     * it, so its stock icons have to be hidden too or the lock screen shows both.
     */
    private var shadeRoot: View? = null

    /** The pulled-down shade's header, handed over by [hookShadeHeader]. */
    private var shadeHeader: View? = null

    /** Sends status and the one-shot diagnostic dump to the app. */
    private val reporter = HookReporter(
        { statusBarRoot },
        { host?.duo },
        // The shade/keyguard window and the shade header are separate windows the status-bar dump never
        // reaches; their tree is what shows where the shade element actually sits (Issue #1).
        { listOf("shade window" to shadeRoot, "shade header" to shadeHeader) }
    )

    /**
     * The ROM adapter, used only to resolve resource ids against SystemUI's package.
     *
     * A plain field, not `lazy`: the SystemUI markers need a context to read the installed APK/class, and
     * a lazy initializer that happened to run before `app` existed would freeze the UNKNOWN result for
     * the whole process. It starts as the UNKNOWN resolution (old brand behaviour) and [start] reassigns
     * it as soon as a context exists. The first use happens inside hooks, after [start].
     */
    private var rom: RomAdapter = RomDetection.forThisRom(
        Build.MANUFACTURER.orEmpty(),
        Build.BRAND.orEmpty(),
        Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty(),
        Build.VERSION.SDK_INT,
        RomMarkers.UNKNOWN
    )

    /**
     * The debounced "settings changed" apply. A drag on the app's size/position slider broadcasts on
     * every tick; running the whole re-read for each one puts a burst of binder calls on System UI's
     * main thread. Waiting for the burst to settle keeps the bar responsive and, more importantly,
     * keeps the watchdog from restarting System UI.
     */
    private val settingsApply = Runnable {
        val ctx = app ?: return@Runnable
        L.guard("DuoHook settings changed") {
            val applySavedSize = orientationPending.getAndSet(false)
            val guard = DuoGuard(ctx)
            val stage = guard.stage()
            val override = guard.override()
            // One fresh snapshot per apply: the owner decision, the feature diff and the monitor
            // registration all come from the same settings revision, never from different reads.
            val snapshot = DuoSettingsClient.read(ctx)
            // R6/D13/D14: an explicit iOS-off is a turn-off even when no iOS owner is live (for
            // example after an activation failure fell back to Duo), so it clears the refusal and a
            // later request can try the route again.
            if (!snapshot.iosMode) IosAvailability.routeFailed = false
            val next = featureSetFor(snapshot, override, IosAvailability.effective(), false)

            if (next.iosRuntime) {
                commitFeatures(stage, snapshot, override, next)
                logOnce.once("settings-ios-on") {
                    L.i("settings apply: iOS mode on - Duo element suppressed")
                }
                // Mutual exclusivity (rule 7, A6): no Duo host, monitor, hook or listener survives.
                stopDuoRuntime()
                installHooksOnce()
                val iosSettings = snapshot
                val previousRoute = iosRoute
                iosRoute = resolveIosRoute(next)
                reportIosCapability(ctx, iosSettings.revision)
                if (iosRoute == IosRoute.V2) {
                    if (activateIosV2(ctx, iosSettings, synchronous = previousRoute != IosRoute.V2)) {
                        IosBatteryV2.refresh()
                        applyDuoRegistrations()
                        settingsRereadAttempt = 0
                        reportIosCapability(ctx, iosSettings.revision)
                        reporter.report(ctx, stage, iosSettings, features.owner)
                        return@guard
                    }
                    // Activation left no live map. Name the missing piece, record the failure, then
                    // fall back to Duo: an unavailable route must never leave the bar ownerless.
                    logIosV2Unavailable(next)
                    stopIosV2Owners()
                    iosRoute = IosRoute.NONE
                    IosAvailability.routeFailed = true
                    IosRouteDiagnostics.update(iosRoute, rom.id)
                    applyDuoRegistrations()
                    settingsRereadAttempt = 0
                    reportIosCapability(ctx, iosSettings.revision)
                    reporter.report(ctx, stage, iosSettings, features.owner)
                    // Recompute with effective=false -> Duo and attach the host on this same apply.
                    enable(ctx, attempt = 0)
                    return@guard
                }
                // iOS on but v2 cannot take the bar (uncovered family or missing hook): release any
                // stale v2 owner, record the failure and fall back to Duo rather than nothing.
                logIosV2Unavailable(next)
                stopIosV2Owners()
                iosRoute = IosRoute.NONE
                IosAvailability.routeFailed = true
                IosRouteDiagnostics.update(iosRoute, rom.id)
                applyDuoRegistrations()
                settingsRereadAttempt = 0
                reportIosCapability(ctx, iosSettings.revision)
                reporter.report(ctx, stage, iosSettings, features.owner)
                enable(ctx, attempt = 0)
                return@guard
            }
            // C11: decide the action before touching the live iOS attachment. An unreachable provider
            // reads as DEFAULT (owner NONE), and the old order tore iOS down for that - exactly the
            // "unreachable looks like off" regression. A live iOS owner counts as an element here, so
            // an unreachable read cannot silently release the resource map/battery.
            val iosLive = IosIconOrder.isActive ||
                IosResourceEngine.isActive || IosBatteryV2.isActive ||
                IosMobileTypePlacement.isActive
            val action = settingsApplyAction(
                stage,
                override != null,
                DuoSettingsClient.providerUnreachable,
                DuoSettingsClient.servedFromBridge,
                // BUG D: only a real Duo element counts. A live iOS host made this true, so switching
                // iOS off for Duo read as REFRESH and the never-recreated Duo host stayed absent.
                host?.duo != null,
                iosLive
            )
            if (next.owner == BarOwner.NONE && action == SettingsApplyAction.KEEP_UNREACHABLE) {
                // The unreachable read is ModuleSettings.DEFAULT; applying it would visually reset the
                // live element, so keep the current settings until a read succeeds. Do not consume a
                // pending rotation apply either - the next successful read still has to draw it.
                if (applySavedSize) orientationPending.set(true)
                scheduleSettingsReread()
                reporter.report(ctx, stage, null, features.owner)
                return@guard
            }
            // Leaving iOS mode restores its stock icons before the Duo path runs. When no iOS host was
            // ever created this is a no-op, so the existing path stays byte-identical. `isActive` also
            // covers a boot where the order was activated before any host could attach. A reachable
            // off/other-owner answer still tears down exactly as before.
            if (iosLive && !keepIosOnUnreachable(next.owner, action)) {
                iosTeardown(next)
            }
            commitFeatures(stage, snapshot, override, next)
            if (!next.duoRuntime) {
                settingsRereadAttempt = 0
                logOnce.once("settings-off-teardown") {
                    L.i("settings apply: stage OFF from the settings source - tearing down (stock icons restored)")
                }
                stopDuoRuntime()
                applyDuoRegistrations()
                reporter.report(ctx, stage, snapshot, features.owner)
                return@guard
            }
            settingsRereadAttempt = 0
            val settings = host?.refreshSettings(applySavedSize) ?: snapshot
            // refreshSettings reads the provider itself; trust its owner when it differs from the
            // snapshot above (a live toggle can land between the two reads).
            val refreshed = featureSetFor(settings, override, IosAvailability.effective(), false)
            commitFeatures(stage, settings, override, refreshed)
            if (!refreshed.duoRuntime) {
                stopDuoRuntime()
                applyDuoRegistrations()
                reporter.report(ctx, stage, settings, features.owner)
                return@guard
            }
            // BUG D: the iosTeardown above removed the Duo host. A switch back to Duo (or a REFRESH
            // with no host at all) must attach one instead of poking a null monitor.
            if (host == null) {
                if (iosLive) {
                    logOnce.once("mode-ios-duo-reattach") {
                        L.i("mode ios -> duo: attaching the Duo host")
                    }
                }
                enable(ctx, attempt = 0)
                reporter.report(ctx, stage, settings, features.owner)
                return@guard
            }
            when (action) {
                // B: a teardown must diff the stopped set, not the DUO set that triggered it, or it
                // re-installs the very hooks whose host has just been torn down.
                SettingsApplyAction.TEARDOWN -> stopDuoRuntime(features.stopped())
                // Switched on live: install the shared boot hook set first, then attach. enable()
                // installs them behind its one-shot guard and schedules the attach itself.
                SettingsApplyAction.ENABLE -> enable(ctx, attempt = 0)
                SettingsApplyAction.REFRESH -> {
                    // A design/engine/live-size change is applied in-process: the host replaces its
                    // element (fresh view, fresh art) instead of restarting System UI. Do this before
                    // the monitor refresh so the new element receives the current state.
                    val liveHost = host
                    if (liveHost != null && liveHost.hasPendingRebuild()) {
                        val root = liveStatusBarRoot()
                        if (root == null) {
                            // Never tear the working element down for a rebuild that has no window to
                            // draw into. The pending flag stays set; the next apply/attach retries.
                            L.w("live settings change with no attached status-bar window - will retry")
                            scheduleAttach(attempt = 0)
                            reporter.report(ctx, stage, settings, features.owner)
                            return@guard
                        }
                        if (!liveHost.rebuildForLiveSettings(root)) {
                            L.w("live settings rebuild failed - retrying the attach (no restart)")
                            scheduleAttach(attempt = 0)
                            reporter.report(ctx, stage, settings, features.owner)
                            return@guard
                        }
                        // The rebuild dropped every extra bar with the old element; rebuild them from
                        // the current windows exactly as the attach path does.
                        lastDuoExtraAttachAt = 0L
                        shadeRoot?.let { attachExtraBars(it) }
                        host?.setMainBarVisible(!shadedOrQsExpanded)
                        if (qsExpanded || notificationExpanded) {
                            host?.setPanelOwner(if (qsExpanded) "qs header" else "shade header")
                        }
                    }
                    monitor?.apply(features)
                    monitor?.refresh()
                    applyDuoRegistrations()
                }
                SettingsApplyAction.KEEP_UNREACHABLE -> Unit
            }
            reporter.report(ctx, stage, settings, features.owner)
        }
    }

    /**
     * Re-reads the settings after an unreachable provider looked like "off" (C-07 / I-2).
     *
     * The element is deliberately kept: an unreachable provider is not a user decision, and tearing the
     * live element down would blank a working bar. The budget is bounded but never ends in a teardown -
     * once exhausted, the loop waits for the next real settings change (or the next boot read).
     */
    private fun scheduleSettingsReread() {
        if (settingsRereadAttempt >= StageRetry.retryCount) {
            logOnce.once("settings-reread-exhausted") {
                L.w(
                    "settings provider still unreachable after ${StageRetry.budgetMs} ms - " +
                        "keeping the element, waiting for the next settings change"
                )
            }
            return
        }
        val delay = StageRetry.delayFor(settingsRereadAttempt) ?: return
        settingsRereadAttempt++
        logOnce.once("settings-reread-keep") {
            L.w("stage reads OFF but the settings provider is unreachable - keeping the element and re-reading in $delay ms")
        }
        handler.removeCallbacks(settingsApply)
        handler.postDelayed(settingsApply, delay)
    }

    /**
     * Writes the app-visible "the module loaded" stamp as early as a context is available.
     *
     * The app's restart handshake waits for this stamp to change; writing it at the top of install/start
     * means a fresh SystemUI is detectable while the heavy install is still running, which is exactly the
     * window in which the old app-side timeout expired and root killed the new process.
     */
    private fun noteLoadStamp(ctx: Context) {
        L.guard("DuoHook load stamp") {
            DuoGuard(ctx).noteLoaded()
            logOnce.once("load-stamp") { L.i("module load stamp written (install/start)") }
        }
    }

    fun install() {
        L.guard("DuoHook install") {
            // MainHook owns the per-process install claim. This is only a same-instance guard, so a
            // direct call cannot run the install body twice; it deliberately does not touch
            // ProcessState.installed (a second class-loader load is MainHook's duplicate skip).
            if (!installEntered.compareAndSet(false, true)) {
                L.i("install already ran on this host - skipping duplicate call")
                return@guard
            }
            // Earliest point the app can see: before any hook, Rive or ROM work below.
            val loadContext = AppContextResolver.resolveAppContext()
            if (loadContext != null) {
                Breadcrumb.bind(loadContext)
                noteLoadStamp(loadContext)
            }
            L.i("=== Duo Status Bar ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) ===")
            Breadcrumb.step("install:ios-engine")
            IosResourceEngine.install(lp.classLoader)
            Breadcrumb.step("install:battery")
            // R4: the battery owner and the Oplus placement binder are per-family pieces. A skipped
            // family logs one `unsupported` line (never on OxygenOS, whose log is frozen) and keeps
            // the stock piece; the resource engine stays process-wide as before.
            if (IosV2Profile.batteryFamily(rom.id)) {
                IosBatteryV2.install(lp.classLoader, rom.id)
            } else {
                logIosPieceUnsupported("battery")
            }
            if (IosV2Profile.typePlacementFamily(rom.id)) {
                IosMobileTypePlacement.install(lp.classLoader)
            } else {
                logIosPieceUnsupported("placement")
            }
            IosDiagnostics.register(IosRouteDiagnostics)
            // The window hook goes in *now*, not from Application.onCreate. On HyperOS 16 and One UI the
            // Application is created before LSPosed injects the module, so an Application.onCreate hook
            // fires too late (or never) and the status-bar window - already added - was never seen. The
            // users' logs show exactly that: "MainHook loaded" then silence, and the element never drew.
            // Watching addView from the first instant of the process catches the window on every ROM.
            Breadcrumb.step("install:window")
            hookWindowManagerAddView()
            L.i("install step: window-hook")
            Breadcrumb.step("install:app")
            hookApplication()
            L.i("install step: app-hook")
            // If the Application already exists (injected late), start now; otherwise Application.onCreate
            // calls [start] the moment it fires, and [bootstrap] retries in case neither is ready yet.
            Breadcrumb.step("install:adopt")
            adoptExistingWindows()
            L.i("install step: adopt-existing")
            Breadcrumb.step("install:start")
            startWithCurrentApplication()
            L.i("install step: start-existing")
            Breadcrumb.step("install:bootstrap")
            bootstrap()
            L.i("install step: bootstrap")
            Breadcrumb.step("install:done")
            // R6/D13/D14: now that the process-wide engine/battery hooks have had their one install
            // attempt, record whether iOS is deliverable on this family. `start()` may already have set
            // it (it runs above when the Application exists); recomputing here covers the late case.
            IosAvailability.supported = iosDeliverable()
            AppContextResolver.resolveAppContext()?.let { reportIosCapability(it) }
            L.i("install step: done")
        }
    }

    /** The single host for this process, created on first use. */
    private fun ensureHost(ctx: Context): DuoIconHost {
        host?.let { return it }
        return DuoIconHost(ctx).also { host = it }
    }

    /**
     * Caches [next] and writes the deterministic boot line once per settings revision.
     *
     * The line is the single source of truth in a dump for "what did the module actually keep
     * registered": owner, renderer, stage, override, revision, active Duo features and shared hooks.
     */
    private fun commitFeatures(
        stage: Int,
        settings: ModuleSettings?,
        override: Int?,
        next: FeatureSet
    ) {
        // Capture the previous owner before assigning, so exactly one mode line describes the switch.
        val previous = features
        features = next
        if (previous.owner != next.owner) {
            val teardown = ownerActions(previous, next)
                .map { it.name }
                .sorted()
                .joinToString(",")
                .ifEmpty { "none" }
            L.i("mode ${previous.owner.tag} -> ${next.owner.tag} (teardown: $teardown)")
        }
        // R6/D13/D14: the app's owner/support verdict follows every committed feature set. The
        // reporter de-duplicates (ignoring `at`), so a repeated apply writes nothing.
        app?.let { reportIosCapability(it, settings?.revision) }
        val revision = settings?.revision ?: -1L
        if (revision == loggedFeatureRevision && next.owner == loggedFeatureOwner) return
        loggedFeatureRevision = revision
        loggedFeatureOwner = next.owner
        L.i(featureLogLine(next, stage, revision, override))
    }

    /** Re-resolves the cached features from the last settings snapshot and the current override. */
    private fun refreshFeatures(stage: Int, override: Int?, settings: ModuleSettings?) {
        commitFeatures(
            stage,
            settings,
            override,
            featureSetFor(settings, override, IosAvailability.effective(), false)
        )
    }

    /**
     * Stops everything Duo-only. Called on every owner change away from Duo (iOS, NONE, unreachable
     * disable): monitor, dynamic registrations (event hooks, touch, layout listener) and the host
     * itself, in that order, so no callback can reach a torn-down host.
     *
     * [next] is the set the runtime is left in, assigned before the registration diff. A teardown passes
     * [FeatureSet.stopped] instead of the Duo set that triggered it, so the diff removes the Duo hooks
     * rather than keeping them installed with no host (B).
     *
     * The shared panel-expansion registration is not Duo work: when [next] is already iOS the diff
     * keeps it installed, and a transition to NONE removes it. The shared shade layout listener is kept
     * for either owner and removed only when nothing remains (D).
     */
    private fun stopDuoRuntime(next: FeatureSet = features) {
        features = next
        monitor?.stop()
        monitor = null
        applyDuoRegistrations()
        host?.teardown()
        host = null
        attaching.set(false)
        // `panelHookInstalled` is owned by hookShadeExpansion and its unhook, not by this teardown:
        // the shared panel hook survives a Duo -> iOS switch, so clearing it here would make the Duo
        // coverage branch skip setPanelOwner(null) after iOS -> Duo. The unhook clears it for NONE.
        lastPanelOwner = null
        qsExpanded = false
        notificationExpanded = false
        shadedOrQsExpanded = false
        if (!keepShadeLayoutListener(next)) detachShadeLayoutListener()
    }

    /**
     * Installs or removes exactly the dynamic registrations the cached features ask for. The shared
     * panel-expansion hook is part of this set, so it is installed while either Duo or iOS owns the bar
     * and removed for NONE.
     */
    private fun applyDuoRegistrations() {
        val diff = registrations.apply(duoRegistrationsFor(features))
        if (diff.isEmpty) return
        val added = diff.added.map { it.tag }.sorted().joinToString(",")
        val removed = diff.removed.map { it.tag }.sorted().joinToString(",")
        val active = registrations.installed().map { it.tag }.sorted().joinToString(",")
        L.i("duo registrations added=[$added] removed=[$removed] active=[$active]")
    }

    /** Opens exactly one Duo registration; the returned lambda removes everything it installed. */
    private fun installDuoRegistration(key: DuoRegistration): (() -> Unit)? = when (key) {
        DuoRegistration.FLASHLIGHT -> hookFlashlight()
        DuoRegistration.SCREENSHOT -> hookScreenshot()
        DuoRegistration.UNLOCK -> hookKeyguardUnlock()
        DuoRegistration.PANEL_EXPANSION -> hookShadeExpansion()
        DuoRegistration.STATUS_TOUCH -> statusBarRoot?.let { hookStatusBarTouch(it) }
        DuoRegistration.STATUS_LAYOUT -> hookStatusLayout()
    }

    /** Runs an installer, logging and returning null when the ROM lacks that class/method. */
    private fun installGuarded(where: String, block: () -> (() -> Unit)?): (() -> Unit)? = try {
        block()
    } catch (t: Throwable) {
        L.w("$where: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    // ----------------------------------------------------------------------------- v2 routing

    /**
     * R6/D13/D14: whether the family and both process-wide hooks make the v2 route deliverable.
     *
     * Uses the family's own battery owner ([IosBatteryV2.installedFor]); a foreign hook never counts.
     */
    private fun iosDeliverable(): Boolean =
        IosV2Profile.engineFamily(rom.id) &&
            IosResourceEngine.installed &&
            IosBatteryV2.installedFor(rom.id)

    /**
     * Writes the live capability report with the real feature owner and route.
     *
     * [revision] defaults to the last successful settings read, matching the boot-line revision when
     * the caller does not already have the snapshot.
     */
    private fun reportIosCapability(
        ctx: Context,
        revision: Long? = null,
        featureSet: FeatureSet = features
    ) {
        val rev = revision ?: DuoSettingsClient.lastRead()?.revision ?: -1L
        IosCapabilityReporter.report(ctx, featureSet, rom.id, iosRoute, rev)
    }

    /**
     * The one route decision per settings apply (R7 P4). Pure [iosRoute] plus the process-wide hook
     * flags; the diagnostics line is refreshed from here so a dump always names the live route.
     */
    private fun resolveIosRoute(next: FeatureSet): IosRoute {
        val route = io.github.kvmy666.duostatusbar.hook.iosRoute(
            next.iosRuntime,
            rom.id,
            IosResourceEngine.installed,
            // R4: the family's own battery owner, never "any battery class was hooked".
            IosBatteryV2.installedFor(rom.id)
        )
        IosRouteDiagnostics.update(route, rom.id)
        return route
    }

    /**
     * Starts the system-native path: the resource map, the battery owner and the OxygenOS placement.
     *
     * Requires [iosRoute] to already be [IosRoute.V2] (set by [resolveIosRoute]); anything thrown
     * deactivates and returns false so the caller can fall back to the host with stock restored.
     */
    private fun activateIosV2(ctx: Context, settings: ModuleSettings, synchronous: Boolean): Boolean {
        if (iosRoute != IosRoute.V2) return false
        return try {
            val engineReady = IosResourceEngine.activate(ctx, rom, settings, synchronous)
            if (!engineReady) {
                // R7 P5: no map (async without a previous snapshot, or an empty build) means the caller
                // must keep the HOST owner; cancel any scheduled build and touch no view.
                stopIosV2Owners()
                return false
            }
            // R9: the live layout owns the per-icon scale; publish the snapshot before any owner
            // installs/reapplies so a bind and a slider tick use the same numbers.
            // R11: the host clock is the one size owner; configure it before the first slot apply
            // so a bind and a slider tick already read the same cap.
            IosSlotLayout.configure(RomResources.id(ctx, rom, rom.clockId), rom.systemUiPackage)
            IosSlotLayout.activate(settings)
            IosBatteryV2.activate(
                ctx,
                rom,
                settings.iosPercentInside,
                IosMapping.FALLBACK_LOW_THRESHOLD,
                settings
            )
            IosMobileTypePlacement.activate(
                if (IosV2Profile.typePlacementFamily(rom.id)) rom.id else null,
                rom.systemUiPackage
            )
            // R8 owners are installed only on the iOS path; a Duo user pays nothing for them. The boot
            // activate is synchronous (before first inflation), so these hooks are in place before the
            // first bind; a late V2 switch installs them here and the next bind reconciles.
            // R4: the stacked-SIM and hide-extras binder hooks are measured Oplus classes only, so they
            // are installed only on a family that owns them (never on Samsung).
            val stackedSupported = IosV2Profile.stackedFamily(rom.id)
            val extrasSupported = IosV2Profile.extrasFamily(rom.id)
            if (stackedSupported) IosStackedSim.install(lp.classLoader)
            if (extrasSupported) IosExtras.install(lp.classLoader, rom.systemUiPackage)
            // R8: the order path is enabled only on OxygenOS, where the swap is measured. Samsung
            // exposes the same class names but the permutation is UNVERIFIED there; enabling it could
            // desynchronise slot/view indices, so every other family keeps stock order (restore is a
            // no-op when inactive).
            if (IosV2Profile.orderFamily(rom.id)) {
                // Repeat the idempotent install in case a previous teardown released the hooks.
                IosIconOrder.install(lp.classLoader)
                IosIconOrder.activate()
            } else {
                IosIconOrder.restore()
            }
            // R4: activate only when the family owns the piece and (for stacked) the binder installed.
            if (stackedSupported && IosStackedSim.installed) {
                // A repeat apply must not reset the swap counter, so a live owner takes the settings path.
                if (IosStackedSim.isActive) {
                    IosStackedSim.applySettings(settings)
                } else {
                    IosStackedSim.activate(ctx, settings)
                }
            } else {
                IosStackedSim.deactivate()
                if (!stackedSupported) logIosPieceUnsupported("stacked")
            }
            if (extrasSupported) {
                IosExtras.apply(settings.hideExtras)
            } else {
                IosExtras.deactivate()
                logIosPieceUnsupported("extras")
            }
            IosDiagnostics.register(IosR8Report)
            iosRoute = IosRoute.V2
            // R6/D13/D14: a successful activation clears any earlier route refusal.
            IosAvailability.routeFailed = false
            IosRouteDiagnostics.update(iosRoute, rom.id)
            logOnce.once("ios-v2-active") { L.i("iOS v2 active: family=${rom.id}") }
            true
        } catch (t: Throwable) {
            L.w("iOS v2 activate failed: ${t.javaClass.simpleName}")
            deactivateIosV2()
            false
        }
    }

    /**
     * Stops the v2 owners and restores the stock drawables, without touching the route. The caller
     * decides the route.
     */
    private fun stopIosV2Owners() {
        // R9: put every stock LayoutParams/clip flag/scale type back before the drawables are restored.
        runCatching { IosSlotLayout.restoreAll() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.layout", it) }
        runCatching { IosStackedSim.deactivate() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.stacked", it) }
        runCatching { IosExtras.deactivate() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.extras", it) }
        // Restore the stock order, but do not release: the order hooks are the process-wide owner and
        // release belongs to the final iOS teardown.
        runCatching { IosIconOrder.restore() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.order", it) }
        runCatching { IosDiagnostics.unregister(IosR8Report) }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.r8report", it) }
        runCatching { IosMobileTypePlacement.deactivate() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.placement", it) }
        runCatching { IosBatteryV2.deactivate() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.battery", it) }
        runCatching { IosResourceEngine.deactivate() }
            .onFailure { IosV2Log.failureOnce("duohook.stopIosV2.engine", it) }
    }

    /**
     * Leaves iOS mode: stops the v2 owners and records the NONE route.
     *
     * [releaseOrder] also releases the process-wide order hooks; only the final iOS teardown may do
     * that, so an activation failure keeps them installed for the next attempt.
     */
    private fun deactivateIosV2(releaseOrder: Boolean = false) {
        stopIosV2Owners()
        if (releaseOrder) {
            runCatching { IosIconOrder.release() }
                .onFailure { IosV2Log.failureOnce("duohook.deactivate.orderRelease", it) }
        }
        iosRoute = IosRoute.NONE
        // R6/D13/D14: turning iOS off (or a fresh activation, which sets it true again on failure)
        // clears the route refusal, so the next request is not permanently refused.
        IosAvailability.routeFailed = false
        IosRouteDiagnostics.update(iosRoute, rom.id)
    }

    /**
     * One line naming why v2 could not take the bar, so a stock bar in iOS mode is diagnosable.
     * Engine-level failures log their specific class/method/resource from inside the engine.
     */
    private fun logIosV2Unavailable(next: FeatureSet) {
        if (!features.iosRuntime && !next.iosRuntime) return
        when {
            !IosV2Profile.engineFamily(rom.id) ->
                IosV2Log.branchOnce("ios.route", "v2 missing family=${rom.id} - stock bar kept")
            !IosResourceEngine.installed ->
                IosV2Log.branchOnce("ios.route", "v2 missing class=IosResourceEngine - stock bar kept")
            !IosBatteryV2.installedFor(rom.id) ->
                // R4: OxygenOS keeps the 2.1.1 string; another family names its own missing owner.
                IosV2Log.branchOnce("ios.route", IosV2Profile.unavailableBatteryLine(rom.id))
            else ->
                IosV2Log.branchOnce(
                    "ios.route",
                    "v2 activation refused - stock bar kept (see engine failure lines)"
                )
        }
    }

    /**
     * R4: one `unsupported` line per skipped owner piece, once per process. Never on OxygenOS: every
     * gate is true there and its log is frozen, so a skip must not add a line.
     */
    private fun logIosPieceUnsupported(piece: String) {
        if (rom.id == "oxygenos") return
        IosV2Log.branchOnce(
            "gate.$piece",
            "unsupported family=${rom.id} - $piece kept stock"
        )
    }

    /** Repaints the live iOS battery owner; v2 has no host and the resource map is already published. */
    private fun iosRefreshAll() {
        IosBatteryV2.refresh()
    }

    /**
     * Leaves iOS mode: stops the v2 owners and restores every replaced stock drawing.
     *
     * [next] is the owner the apply is moving to, so the shared shade listener is removed only when
     * nothing owns the bar afterwards (D).
     */
    private fun iosTeardown(next: FeatureSet = features) {
        deactivateIosV2(releaseOrder = true)
        if (!keepShadeLayoutListener(next)) detachShadeLayoutListener()
        // R6/D13/D14: the app must see the route go back to none and the failure flag cleared. Report
        // the destination owner so the write matches the state the apply is moving to (the commit that
        // follows is then a no-op for the de-dupe).
        app?.let { reportIosCapability(it, featureSet = next) }
    }

    /** Installs the boot hook set once per process. Shared by the Duo and iOS enable paths. */
    private fun installHooksOnce() {
        if (!hooksInstalled.compareAndSet(false, true)) return
        hookShadeHeader()
        // OnePlus keeps the visible QS cluster outside the generic ShadeHeaderController surface.
        // These vendor hooks hand that cluster over directly; hookShadeHeader above stays the
        // fallback for every other ROM and for a build where the vendor classes change.
        OnePlusShadeHooks(lp, { host }).install()
        // Flashlight/screenshot/unlock/panel-expansion are Duo-only and installed by the feature diff
        // (A6), never here: an iOS boot must not register them at all.
        hookStatusIconContainer()
        hookBarAppearance()
        // The settings receiver is already registered in start(), before this gate, so a restart
        // request still arrives when the module reads as off. Orientation is installed here, once
        // we know a rotation might need to attach the other set.
        hookOrientation()
    }

    /**
     * Bootstraps from the Application that is already alive, if there is one.
     *
     * This is the other half of the early-attach fix: when the module is injected after the Application
     * was created, `Application.onCreate` never runs again, so the heartbeat, the settings receiver and
     * the extra hooks would never be installed. `AndroidAppHelper.currentApplication()` is the Xposed
     * API for exactly this case; it is null when a normal onCreate is still coming.
     */
    private fun startWithCurrentApplication() {
        L.guard("DuoHook bootstrap") {
            val ctx = AppContextResolver.resolveAppContext()
            if (ctx != null) {
                L.i("Application already created at injection - starting now")
                start(ctx)
            }
        }
    }

    /**
     * The Application may only be created a moment after the module loads, so a failed resolve is retried
     * briefly instead of giving up - the alternative is the "MainHook loaded" then silence that made the
     * app report the module as never injected.
     */
    private fun bootstrap(attempt: Int = 0) {
        if (ProcessState.started.get()) return
        val ctx = AppContextResolver.resolveAppContext() ?: statusBarRoot?.context
        if (ctx != null) {
            start(ctx)
            return
        }
        if (attempt >= MAX_BOOTSTRAP_ATTEMPTS) {
            L.w("no Application context after $MAX_BOOTSTRAP_ATTEMPTS attempts - waiting for the status-bar window")
            return
        }
        handler.postDelayed({
            L.guard("DuoHook bootstrap retry") { bootstrap(attempt + 1) }
        }, BOOTSTRAP_RETRY_MS)
    }

    /**
     * Adopts a window that was added before the module was injected.
     *
     * When injection is late, `WindowManagerImpl.addView` already fired and the addView hook will never see
     * the window, so it is read back from `WindowManagerGlobal.mViews` - the process's own list of added
     * windows - by its layout-params type. Both windows matter: the status bar draws the element, and the
     * shade/keyguard window carries the lock-screen bar and the shade header (FR-03b).
     */
    private fun adoptExistingWindows() {
        if (statusBarRoot == null) {
            L.guard("DuoHook existing window") {
                val found = AppContextResolver.findExistingWindow(AppContextResolver.TYPE_STATUS_BAR)
                    ?: return@guard
                statusBarRoot = found
                L.i("existing status bar window found: ${found.javaClass.name}")
                scheduleAttach(attempt = 0)
                // Now that a SystemUI-owned context exists, the bootstrap can succeed even when the
                // ActivityThread lookups did not.
                bootstrap()
            }
        }
        L.guard("DuoHook existing shade window") {
            val found = AppContextResolver.findExistingWindow(AppContextResolver.TYPE_NOTIFICATION_SHADE)
            if (found != null) adoptShadeWindow(found)
        }
    }

    /**
     * The status-bar window that is actually on screen right now.
     *
     * [statusBarRoot] is captured once and never cleared, so after the ROM recreates the status-bar
     * window it can name a detached tree: a live rebuild would then inject into a window nobody sees.
     * Keep the cached root only while it is attached; otherwise adopt a live `TYPE_STATUS_BAR` window
     * if one exists.
     */
    private fun liveStatusBarRoot(): View? {
        val cached = statusBarRoot
        if (cached != null && cached.isAttachedToWindow) return cached
        val found = AppContextResolver.findExistingWindow(AppContextResolver.TYPE_STATUS_BAR)
        if (found != null && found.isAttachedToWindow) {
            if (found !== cached) {
                statusBarRoot = found
                L.i("existing status bar window adopted: ${found.javaClass.name}")
            }
            return found
        }
        return null
    }

    /**
     * Captures the shade/keyguard window.
     *
     * Its layout listener is the trigger for [attachExtraBars]. When this window is captured after the
     * main bar attached, that listener was installed before the window was known and would never run:
     * attach the extra bars and add the listener now instead of waiting for a restart.
     */
    private fun adoptShadeWindow(view: View) {
        if (shadeRoot != null) return
        shadeRoot = view
        L.i("keyguard/shade window found: ${view.javaClass.name} (FR-03b)")
        if (monitor != null) {
            attachExtraBars(view)
            addShadeLayoutListener(view)
        }
    }

    /**
     * Everything that needs a live context. Runs once per process, from whichever of
     * [startWithCurrentApplication] or `Application.onCreate` happens first.
     */
    private fun start(ctx: Context) {
        if (!ProcessState.started.compareAndSet(false, true)) return
        Breadcrumb.bind(ctx)
        Breadcrumb.step("start")
        // First statement, before RomDetection: the new process must be detectable immediately.
        noteLoadStamp(ctx)
        app = ctx
        // Now that a context exists the SystemUI package can be inspected. Only a successful read that
        // proves a non-MIUI SystemUI may switch a Xiaomi-brand device to AOSP; an unreadable one keeps
        // the old HyperOS/brand behaviour (see [RomMarkers]). One resolver so this cannot drift from the
        // icon host and the diagnostic dump (R15).
        rom = RomResolver.resolve(ctx)
        // R6/D13/D14: the family is known now, so the iOS owner decision can stop trusting a saved ON.
        // A new process has not attempted a route yet, so it starts from "not failed".
        IosAvailability.routeFailed = false
        IosAvailability.supported = iosDeliverable()
        L.guard("DuoHook start") {
            // Report even when the module is gated off: the app must still learn the family and the
            // support verdict, otherwise an iOS request on an unsupported UI looks like DISABLED.
            reportIosCapability(ctx)
            // The settings/restart receiver goes in *before* the gate. It is the app's only way to reach a
            // module that read itself as "off": "Restart System UI" and a live enable both arrive here.
            // Registering it after the gate (as before) left the button dead on every ROM where the stage
            // resolved to 0 - exactly the ColorOS/realme report, where the provider was briefly unreachable
            // at boot and the module then never listened again. The receiver changes nothing while the
            // module is off; it only reacts to our own custom actions.
            hookSettingsChanges(ctx)
            enable(ctx, attempt = 0)
        }
    }

    /**
     * Resolves the stage and, when the module is on, hooks and attaches. The stage is read - and, when the
     * provider could not be reached, re-read - because "unreadable" is not "the user turned it off": on
     * ColorOS/realme the first read at boot can fail while the app process is still coming up, and
     * treating that as "off" is what made the element silently never appear (stage 0, no report).
     */
    private fun enable(ctx: Context, attempt: Int) {
        L.guard("DuoHook enable #$attempt") {
            val guard = DuoGuard(ctx)
            val stage = guard.stage()
            val override = guard.override()
            val settings = DuoSettingsClient.read(ctx)
            refreshFeatures(stage, override, settings)
            if (attempt == 0) {
                // Heartbeat before the gate: it is how the app tells "LSPosed never injected the module"
                // apart from "the module ran but is switched off". Two signals: a Settings.Global stamp,
                // and a provider report. The provider needs no permission, so the About screen cannot show
                // a false "never" on a ROM that denies SystemUI WRITE_SECURE_SETTINGS.
                L.guard("DuoHook heartbeat") {
                    guard.noteLoaded()
                    // E: the app-visible line carries the same owner/renderer/stage/override/rev/
                    // active/shared fields as the L boot line, not a reduced fragment.
                    DuoSettingsClient.report(ctx, hookStatusPrefix(features.owner.tag) +
                        "loaded · " +
                        featureStatus(features, stage, settings.revision, override))
                    DuoSettingsClient.reportFallback(ctx, "")
                }
            }
            if (!features.duoRuntime && !features.iosRuntime) {
                // Retry only while the provider itself was unreachable; a real "off" answer is final.
                // An unreachable read looks like "off", so it must not be treated as the other
                // orientation being off too — that would skip the hooks and never retry.
                if (DuoSettingsClient.providerUnreachable && attempt < StageRetry.retryCount) {
                    val delay = StageRetry.delayFor(attempt)
                    if (delay != null) {
                        L.w("settings provider unreachable (attempt ${attempt + 1}/${StageRetry.retryCount}) - retrying")
                        handler.postDelayed({
                            L.guard("DuoHook enable retry") { enable(ctx, attempt + 1) }
                        }, delay)
                        return@guard
                    }
                }
                // Off in *both* orientations leaves the process untouched, the same as a single off switch.
                // Off in only this orientation still installs the hooks: rotating has to be able to attach
                // the other set without a System UI restart.
                if (!DuoSettingsClient.otherOrientationEnabled(ctx)) {
                    L.i("gated off - nothing hooked. Enable with: ${guard.enableHint}, then restart SystemUI")
                    return@guard
                }
            }
            L.i("application ready: ${ctx.packageName} (stage $stage)")
            if (features.iosRuntime) L.i("ios mode on - Duo element suppressed")
            // Shared boot hooks are installed for both owners; the Duo/iOS-only work is branched.
            installHooksOnce()
            if (features.iosRuntime) {
                // R7 P4: the native route is activated synchronously on the boot path so the very
                // first inflation already sees our map; it installs no host or attach timer.
                iosRoute = resolveIosRoute(features)
                reportIosCapability(ctx, settings.revision)
                if (iosRoute == IosRoute.V2 && activateIosV2(ctx, settings, synchronous = true)) {
                    applyDuoRegistrations()
                    reportIosCapability(ctx, settings.revision)
                    return@guard
                }
                // iOS on but v2 cannot take the bar. Name the missing piece once, record the
                // failure, then fall through to the Duo path with a recomputed feature set so the
                // bar keeps an owner instead of staying stock with nothing drawn (D13/D14).
                logIosV2Unavailable(features)
                stopIosV2Owners()
                iosRoute = IosRoute.NONE
                IosAvailability.routeFailed = true
                IosRouteDiagnostics.update(iosRoute, rom.id)
                val fallback =
                    featureSetFor(settings, override, false, otherOrientationEnabled = false)
                // Diagnostics-only: the `features:` line must name the Duo stage that is actually
                // attached, not the stale iOS stage read before the failure. The attach decision
                // below still uses `features` (unchanged).
                commitFeatures(DuoGuard.stageOf(fallback), settings, override, fallback)
                applyDuoRegistrations()
                reportIosCapability(ctx, settings.revision)
            }
            // A: the Duo host is created only when Duo owns the bar. An off orientation keeps no host:
            // the next enable for the on orientation attaches it, so a vendor shade callback can never
            // reach Duo work while Duo is off.
            if (shouldCreateDuoHost(features)) ensureHost(ctx)
            // The window may already have been captured by the early addView hook; attach now.
            if (!features.duoRuntime) {
                L.i("this orientation is off - waiting to apply the other orientation")
            } else {
                statusBarRoot?.let { scheduleAttach(attempt = 0) }
            }
            applyDuoRegistrations()
        }
    }

    private fun hookApplication() {
        L.guard("DuoHook hook Application") {
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook Application.onCreate") {
                        start(param.thisObject as Application)
                    }
                }
            }
            // The base class covers every ROM whose Application calls super.onCreate(). The concrete
            // SystemUI Applications are hooked too, for GSI/Vector ROMs (issue #9, PR #8) where the
            // framework can bypass the base `android.app.Application.onCreate` lifecycle. Each name is
            // optional - a ROM that does not have it is simply skipped - and `start` runs once regardless
            // of how many fire (it is guarded by a process-wide flag).
            for (name in listOf(
                "android.app.Application",
                "com.android.systemui.SystemUIApplication",
                "com.android.systemui.MiuiSystemUIApplication"
            )) {
                try {
                    XposedHelpers.findAndHookMethod(name, lp.classLoader, "onCreate", callback)
                    L.i("Application.onCreate hook installed on $name")
                } catch (_: Throwable) {
                    // Not present on this ROM.
                }
            }
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
                        val reason = intent.getStringExtra(DuoPrefs.EXTRA_RESTART_REASON) ?: "unknown"
                        val now = System.currentTimeMillis()
                        val last = lastRestartRequestAt
                        if (RestartPolicy.shouldDebounce(now, last)) {
                            // A second broadcast inside the window is the same request: killing twice in
                            // quick succession is what let a replacement SystemUI be hit mid-install.
                            L.w("restart ignored (debounced, ${now - last} ms since last request)")
                            return
                        }
                        lastRestartRequestAt = now
                        L.i("restart requested by the app (reason=$reason) - restarting System UI")
                        handler.postDelayed({
                            try {
                                android.os.Process.killProcess(android.os.Process.myPid())
                            } catch (t: Throwable) {
                                L.w("restart failed: ${t.javaClass.simpleName}: ${t.message}")
                            }
                        }, RESTART_DELAY_MS)
                        return
                    }
                    if (intent?.action == DuoPrefs.ACTION_DIAGNOSTICS_REQUEST) {
                        // The app is building a bug report: capture the bar now (the shade may be open),
                        // so the dump reflects the reported state instead of the boot-time one.
                        val ctxNow = app
                        if (ctxNow != null) {
                            reporter.reportDiagnostics(
                                ctxNow,
                                DuoGuard(ctxNow).stage(),
                                DuoSettingsClient.read(ctxNow),
                                host?.duo
                            )
                            L.i("diagnostic dump refreshed on app request")
                        }
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
                    addAction(DuoPrefs.ACTION_DIAGNOSTICS_REQUEST)
                },
                // All three actions restart System UI or read its tree, so they are exported only to
                // senders holding our signature permission - the app holds it, nothing else can.
                SettingsBridge.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED
            )
            L.i("listening for app settings changes")
        }
    }

    /**
     * Rotation swaps which stored set is drawn.
     *
     * The status-bar window on this ROM does not get a configuration callback whose orientation
     * actually changes, so watching only that left the previous set on screen until some other
     * settings write happened to re-read. The display's rotation is what the phone really did.
     */
    private fun hookOrientation() {
        val ctx = app ?: return
        appliedOrientation = orientationFrom(ctx)
        L.guard("DuoHook orientation") {
            ctx.registerComponentCallbacks(object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    noteCurrentOrientation(ctx)
                }

                override fun onLowMemory() {}
            })
            val displays = ctx.getSystemService(DisplayManager::class.java)
            displays?.registerDisplayListener(object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}

                override fun onDisplayRemoved(displayId: Int) {}

                override fun onDisplayChanged(displayId: Int) {
                    noteCurrentOrientation(ctx)
                }
            }, handler)
            L.i("orientation listener installed")
        }
    }

    /**
     * The orientation to draw. Resources and the display can update a moment apart; a display that
     * has already left the set we are drawing wins, otherwise the configuration does, which is the
     * same value a later settings read uses.
     */
    private fun noteCurrentOrientation(ctx: Context) {
        val configured = ctx.resources.configuration.orientation
        val fromDisplay = orientationFromRotation(displayRotation(ctx))
        if (fromDisplay != Configuration.ORIENTATION_UNDEFINED && fromDisplay != appliedOrientation) {
            noteOrientation(fromDisplay)
            return
        }
        noteOrientation(configured)
    }

    /** What is on screen right now, for the initial value so the first layout does not re-apply. */
    private fun orientationFrom(ctx: Context): Int {
        val fromDisplay = orientationFromRotation(displayRotation(ctx))
        if (fromDisplay != Configuration.ORIENTATION_UNDEFINED) return fromDisplay
        return ctx.resources.configuration.orientation
    }

    private fun displayRotation(ctx: Context): Int? = try {
        ctx.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation
    } catch (_: Throwable) {
        null
    }

    private fun orientationFromRotation(rotation: Int?): Int = when (rotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> Configuration.ORIENTATION_LANDSCAPE
        Surface.ROTATION_0, Surface.ROTATION_180 -> Configuration.ORIENTATION_PORTRAIT
        else -> Configuration.ORIENTATION_UNDEFINED
    }

    /** Applies [next] when it is a real orientation and not the one already drawn. */
    private fun noteOrientation(next: Int) {
        if (next != Configuration.ORIENTATION_PORTRAIT && next != Configuration.ORIENTATION_LANDSCAPE) return
        if (next == appliedOrientation) return
        appliedOrientation = next
        DuoSettingsClient.useOrientation(next)
        orientationPending.set(true)
        L.i(
            "orientation is now " +
                "${if (next == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}" +
                " - applying that set"
        )
        handler.removeCallbacks(settingsApply)
        handler.post(settingsApply)
    }

    private fun hookWindowManagerAddView() {
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                L.guard("DuoHook addView") {
                    val view = param.args.getOrNull(0) as? View ?: return
                    val layoutParams = view.layoutParams ?: return
                    when (XposedHelpers.getIntField(layoutParams, "type")) {
                        AppContextResolver.TYPE_STATUS_BAR -> {
                            // A recreated window replaces a detached one; only an attached root that
                            // is still the live bar is left alone.
                            val existing = statusBarRoot
                            if (existing != null && existing.isAttachedToWindow) return
                            statusBarRoot = view
                            L.i("status bar window found: ${view.javaClass.name}")
                            scheduleAttach(attempt = 0)
                        }
                        AppContextResolver.TYPE_NOTIFICATION_SHADE -> adoptShadeWindow(view)
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
            // A ROM the module could not attach on must still leave evidence: send the same diagnostic
            // dump as a successful attach, so a report names the ids/probes that were tried. Without
            // this, the Samsung One UI user's report was just "loaded · stage=2" with nothing to act on.
            val ctx = app ?: statusBarRoot?.context
            if (ctx != null) {
                L.guard("DuoHook attach failed report") {
                    DuoSettingsClient.report(ctx, hookStatusPrefix(features.owner.tag) +
                        "attach failed after $MAX_ATTEMPTS attempts - see the diagnostic dump")
                    reporter.reportDiagnostics(ctx, DuoGuard(ctx).stage(), null, host?.duo)
                }
            }
            return
        }
        handler.postDelayed({
            L.guard("DuoHook attach #$attempt") {
                val root = liveStatusBarRoot() ?: return@guard
                // Prefer the Application, but fall back to the bar window's own context: on a ROM where
                // the module is injected late the Application may never have been captured.
                val ctx = app ?: root.context
                if (features.iosRuntime) {
                    // v2 has no host to attach; the native owners are already in place.
                    return@guard
                }
                // A6: nothing attaches while no owner is named (off, kill switch, unreachable).
                if (!features.duoRuntime) return@guard
                val attached = ensureHost(ctx).attach(root)
                if (attached) {
                    // attach() re-reads the settings; take the same snapshot for the monitor and the
                    // Duo-only registrations so a live toggle during attach cannot leave a stale set.
                    val appliedSettings =
                        DuoSettingsClient.lastRead() ?: DuoSettingsClient.read(ctx)
                    val guard = DuoGuard(ctx)
                    refreshFeatures(guard.stage(), guard.override(), appliedSettings)
                    if (monitor == null) {
                        monitor = DuoStateMonitor(ctx, host!!).also { it.start(features) }
                    } else {
                        monitor?.apply(features)
                    }
                    // Installs the Duo-only layout listener and touch hook through the same diff.
                    applyDuoRegistrations()
                    if (!attaching.compareAndSet(false, true)) return@guard
                    // The keyguard and the shade header may already be on screen (the element attaches
                    // at boot, they come later, but a re-attach after rotation can land either way).
                    val shadeWindow = shadeRoot
                    ShadeProbe.logOnce(
                        "shadeProbe:scheduleAttach:shadeRoot:${shadeWindow != null}",
                        "shade window=${ShadeProbe.describe(shadeWindow)}"
                    )
                    shadeWindow?.let { shade -> attachExtraBars(shade) }
                    host?.setMainBarVisible(!shadedOrQsExpanded)
                    if (qsExpanded || notificationExpanded) {
                        host?.setPanelOwner(if (qsExpanded) "qs header" else "shade header")
                    }
                    L.i("Duo attached on attempt $attempt")
                    reporter.report(ctx, guard.stage(), null, features.owner)
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
    private fun hookStatusBarTouch(root: View): (() -> Unit)? = installGuarded("DuoHook status bar touch") {
        // `dispatchTouchEvent` is inherited from ViewGroup, so this hooks the base method and the
        // identity guard below keeps it to the status-bar window only (hooking without the guard
        // would run for every touch in the process).
        val method = try {
            XposedHelpers.findMethodExact(
                root.javaClass, "dispatchTouchEvent", android.view.MotionEvent::class.java
            )
        } catch (t: Throwable) {
            L.w("no dispatchTouchEvent on ${root.javaClass.simpleName}: ${t.message}")
            return@installGuarded null
        }
        val unhook = XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.thisObject !== root) return
                // A6: a touch hook that outlives its owner must be inert before teardown completes.
                if (!features.duoRuntime) return
                try {
                    val event = param.args.getOrNull(0) as? android.view.MotionEvent ?: return
                    host?.handleElementTouch(event)
                } catch (t: Throwable) {
                    L.w("element touch: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        })
        L.i("status bar touch hook installed (${root.javaClass.simpleName}) - FR-05/18")
        return@installGuarded { unhook.unhook() }
    }

    /**
     * Installs the main-bar layout listener as a removable Duo-only registration.
     *
     * The listener is what re-applies hiding after a layout pass; it has no business running while
     * iOS owns the bar. [attachLayoutListener] owns the actual listener identity, so removing the
     * exact instance it returned cannot strip a newer one.
     */
    private fun hookStatusLayout(): (() -> Unit)? {
        val root = statusBarRoot ?: return null
        attachLayoutListener(root)
        val listener = layoutListener ?: return null
        return {
            if (layoutListener === listener) {
                runCatching { root.removeOnLayoutChangeListener(listener) }
                layoutListenerRoot = null
                layoutListener = null
            }
        }
    }

    private fun attachLayoutListener(root: View) {
        L.guard("DuoHook layout listener") {
            if (layoutListenerRoot === root && layoutListener != null) return@guard
            layoutListenerRoot?.let { old ->
                layoutListener?.let { listener ->
                    runCatching { old.removeOnLayoutChangeListener(listener) }
                }
            }
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                L.guard("DuoHook onLayout") {
                    // Hide pass only. Do NOT re-read system state here: the status bar re-lays out on
                    // every clock tick, and refreshing Wi-Fi/cell/etc plus re-rendering Rive on each one
                    // kept SystemUI awake at ~1 Hz — the battery drain. State is already broadcast-driven.
                    // Orientation is an int compare and only posts work when it actually changed.
                    // The display listener covers a rotation that has not reached this configuration yet.
                    app?.let { noteOrientation(it.resources.configuration.orientation) }
                    // R3 A6: the full walk only when the hide state may have been undone.
                    host?.reapplyHidingIfNeeded()
                }
            }
            layoutListenerRoot = root
            layoutListener = listener
            root.addOnLayoutChangeListener(listener)
            // The keyguard's bar is inflated into the shade window when the lock screen appears, and the
            // shade window is the one that changes then - so its layout pass is the trigger for the
            // second hiding pass (FR-03b).
            shadeRoot?.let { addShadeLayoutListener(it) }
        }
    }

    /**
     * SystemUI's own panel state. This is the missing piece: the container attach order is not the
     * same as QS-vs-shade vs collapsed, so deriving visibility from which slot attached last left
     * both copies visible during the transition (and hid the shade copy). The ROM already reports:
     *
     *  - NotificationShadeWindowControllerImpl.onShadeOrQsExpanded(Boolean): true at the start of
     *    any QS/shade expansion, false when both are closed.
     *  - ShadeExpansionStateManager.onPanelExpansionChanged(... PanelType): QS_PANEL and
     *    NOTIFICATION_PANEL separately.
     *
     * Both hooks are after-only and never modify an argument or return value.
     */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else handler.post { block() }
    }

    private fun hookShadeExpansion(): (() -> Unit)? {
        val handles = ArrayList<XC_MethodHook.Unhook>()
        L.guard("DuoHook shade expansion") {
            val coverageCallback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val expanded = param.args.getOrNull(0) as? Boolean ?: return
                    onMain {
                        // R3 A4/A1/A6: the panel hook is shared. The aggregate boolean is tracked for
                        // both owners; only a real edge does work, so a per-frame drag stays cheap.
                        val changed = shadedOrQsExpanded != expanded
                        shadedOrQsExpanded = expanded
                        if (changed &&
                            PanelTransitionAction.IOS_REFRESH in panelTransitionActions(features) &&
                            iosRoute == IosRoute.V2
                        ) {
                            // iOS branch only: repaint the live v2 surfaces. No Duo host access.
                            iosRefreshAll()
                        }
                        // A6: the Duo part of this shared registration must not touch the torn-down
                        // Duo host while iOS (or nothing) owns the bar.
                        if (!features.duoRuntime) return@onMain
                        try {
                            if (changed) {
                                // Entering QS/shade: hold the main copy until the incoming copy is
                                // opaque. Leaving: onPanelCollapsed() owns the order (slide, latch,
                                // opaque reveal), so no 267–283 ms blackout can open.
                                if (expanded) host?.setMainBarVisible(false) else host?.onPanelCollapsed()
                                L.i("shade/QS covered -> $expanded")
                            }
                            // Only the per-panel hook knows whether the content is the QS
                            // or the notification surface. Do not lock ownership to null
                            // when that hook was unavailable.
                            if (!expanded && panelHookInstalled) {
                                host?.setPanelOwner(null)
                            }
                        } catch (t: Throwable) {
                            L.w("shade/QS coverage: ${t.javaClass.simpleName}: ${t.message}")
                        }
                    }
                }
            }
            try {
                val cls = XposedHelpers.findClass(
                    "com.android.systemui.shade.NotificationShadeWindowControllerImpl",
                    lp.classLoader
                )
                try {
                    handles += XposedHelpers.findAndHookMethod(
                        cls, "onShadeOrQsExpanded", java.lang.Boolean::class.java,
                        coverageCallback
                    )
                    DuoLog.event(
                        ui = rom.id, surface = "shade", stage = "hook",
                        clazz = "NotificationShadeWindowControllerImpl",
                        hook = "onShadeOrQsExpanded", result = "ok"
                    )
                    L.i("shade/QS coverage hook installed")
                } catch (t: Throwable) {
                    DuoLog.event(
                        ui = rom.id, surface = "shade", stage = "hook",
                        clazz = "NotificationShadeWindowControllerImpl",
                        hook = "onShadeOrQsExpanded", result = "fail",
                        reason = DuoLog.HOOK_INSTALL_FAILED,
                        detail = "${t.javaClass.simpleName}: ${t.message}"
                    )
                    L.w("shade/QS coverage hook install failed: ${t.javaClass.simpleName}: ${t.message}")
                }
            } catch (t: Throwable) {
                DuoLog.event(
                    ui = rom.id, surface = "shade", stage = "hook",
                    clazz = "NotificationShadeWindowControllerImpl",
                    hook = "onShadeOrQsExpanded", result = "fail",
                    reason = DuoLog.HOOK_ABSENT,
                    detail = "${t.javaClass.simpleName}: ${t.message}"
                )
                L.w("shade/QS coverage hook absent: ${t.javaClass.simpleName}: ${t.message}")
            }

            var phase = "manager"
            try {
                val cls = XposedHelpers.findClass(
                    "com.android.systemui.shade.ShadeExpansionStateManager",
                    lp.classLoader
                )
                phase = "panelType"
                val panelType = XposedHelpers.findClass(
                    "com.android.systemui.shade.PanelType", lp.classLoader
                )
                phase = "method"
                handles += XposedHelpers.findAndHookMethod(
                    cls, "onPanelExpansionChanged",
                    Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, String::class.java, panelType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val expanded = param.args.getOrNull(1) as? Boolean ?: return
                            val type = param.args.getOrNull(4)?.toString().orEmpty()
                            onMain {
                                val panelOwner = ShadeExpansionClassifier.ownerOf(type)
                                val wasPanelExpanded = when (panelOwner) {
                                    ShadeOwner.QS -> qsExpanded
                                    ShadeOwner.NOTIFICATION -> notificationExpanded
                                    ShadeOwner.NONE -> null
                                }
                                val wasCovered = shadedOrQsExpanded
                                // Track the panel/aggregate booleans for both owners; the iOS branch
                                // below consumes only the edge, the Duo body consumes the owner.
                                when (panelOwner) {
                                    ShadeOwner.QS -> qsExpanded = expanded
                                    ShadeOwner.NOTIFICATION -> notificationExpanded = expanded
                                    ShadeOwner.NONE -> { }
                                }
                                // Aggregate (when present): a false event means both parts
                                // are actually collapsed; true is covered above.
                                if (type.contains("SHADE_PANEL") && !expanded) {
                                    qsExpanded = false
                                    notificationExpanded = false
                                }
                                val covered = qsExpanded || notificationExpanded
                                shadedOrQsExpanded = covered
                                val changed = (wasPanelExpanded != null &&
                                    wasPanelExpanded != expanded) || wasCovered != covered
                                if (changed &&
                                    PanelTransitionAction.IOS_REFRESH in panelTransitionActions(features) &&
                                    iosRoute == IosRoute.V2
                                ) {
                                    // iOS branch only (R3 A4/A1): repaint the live v2 surfaces on a real
                                    // panel edge. No Duo host or monitor work is reached here.
                                    iosRefreshAll()
                                }
                                // A6: this callback is shared; the Duo part runs only while Duo owns
                                // the bar and never touches a torn-down host.
                                if (!features.duoRuntime) return@onMain
                                try {
                                    if (covered != wasCovered) {
                                        if (covered) host?.setMainBarVisible(false)
                                        else host?.onPanelCollapsed()
                                    }
                                    val owner = when {
                                        // In full shade QS_PANEL can stay expanded; the
                                        // notification surface is the visible one then.
                                        notificationExpanded -> "shade header"
                                        qsExpanded -> "qs header"
                                        else -> null
                                    }
                                    host?.setPanelOwner(owner)
                                    val ownerKey = when (owner) {
                                        "shade header" -> "notification"
                                        "qs header" -> "qs"
                                        else -> "collapsed"
                                    }
                                    if (ownerKey != lastPanelOwner) {
                                        lastPanelOwner = ownerKey
                                        DuoLog.event(
                                            ui = rom.id, surface = "shade", stage = "owner",
                                            hook = "onPanelExpansionChanged", result = "ok",
                                            detail = ownerKey
                                        )
                                    }
                                } catch (t: Throwable) {
                                    L.w("panel expansion: ${t.javaClass.simpleName}: ${t.message}")
                                }
                            }
                        }
                    }
                )
                panelHookInstalled = true
                DuoLog.event(
                    ui = rom.id, surface = "shade", stage = "hook",
                    clazz = "ShadeExpansionStateManager",
                    hook = "onPanelExpansionChanged", result = "ok"
                )
                L.i("shade panel expansion hook installed")
            } catch (t: Throwable) {
                DuoLog.event(
                    ui = rom.id, surface = "shade", stage = "hook",
                    clazz = "ShadeExpansionStateManager",
                    hook = "onPanelExpansionChanged", result = "fail",
                    reason = DuoLog.PANEL_HOOK_ABSENT,
                    detail = "${t.javaClass.simpleName}: ${t.message}"
                )
                L.w("shade panel expansion hook unavailable: ${t.javaClass.simpleName}: ${t.message}")
                L.w("shade panel expansion lookup failed at phase=$phase sdk=${Build.VERSION.SDK_INT} panelFallback=attach-order - panel owner falls back to attach order: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        if (handles.isEmpty()) return null
        return {
            for (unhook in handles) {
                try {
                    unhook.unhook()
                } catch (_: Throwable) {
                }
            }
            panelHookInstalled = false
            lastPanelOwner = null
            qsExpanded = false
            notificationExpanded = false
            shadedOrQsExpanded = false
        }
    }

    /**
     * Removes the one shared shade layout listener and its pending settle when no owner remains (D).
     * Idempotent; the next attach/layout installs it again.
     */
    private fun detachShadeLayoutListener() {
        shadeLayoutWindow?.let { window ->
            shadeLayoutListener?.let { listener ->
                runCatching { window.removeOnLayoutChangeListener(listener) }
            }
        }
        shadeLayoutWindow = null
        shadeLayoutListener = null
        shadeSettle?.let { handler.removeCallbacks(it) }
        shadeSettle = null
        shadeSettleHeaderAttempted = false
    }

    /**
     * The shade window's layout pass: re-runs the hide pass and attaches the lock-screen/QS bars. The
     * ROM re-shows its icons once more after the drag settles, so one debounced pass follows; it is
     * stored so a burst of layout passes replaces it instead of stacking a second settle burst.
     */
    private fun addShadeLayoutListener(shade: View) {
        if (shadeLayoutWindow === shade && shadeLayoutListener != null) return
        // A new window replaces the old listener, so exactly one is ever installed.
        detachShadeLayoutListener()
        shadeLayoutWindow = shade
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            L.guard("DuoHook onShadeLayout") {
                // A6: this listener is shared (both modes need it), but each mode's work is separate.
                val duo = features.duoRuntime
                val ios = features.iosRuntime
                if (!duo && !ios) return@guard
                attachExtraBars(shade)
                if (duo) {
                    // R3 A1/A6: every Duo call below is gated on a live host. While iOS owns the bar
                    // the settings apply drops the Duo host, so a torn-down instance is never touched.
                    // The full walk only runs when the hide state may have been undone.
                    host?.reapplyHidingIfNeeded()
                    // Exactly one copy may be visible at any stage. The owner is updated by the last
                    // successful QS/shade attach; showing it here also hides the other copy on every
                    // layout, so entering/exiting QS cannot leave both on screen.
                    host?.showSurfaceOwner()
                }
                if (shadeSettle == null) shadeSettleHeaderAttempted = false
                shadeSettle?.let { handler.removeCallbacks(it) }
                val settle = Runnable {
                    shadeSettle = null
                    L.guard("DuoHook shade settle") {
                        if (!shadeSettleHeaderAttempted) {
                            shadeSettleHeaderAttempted = true
                            if (duo) shadeHeader?.let { host?.attachShadeHeader(it, shadeRoot) }
                        }
                        if (duo) {
                            // The settle is the one full hide pass after the transition; the per-layout
                            // callback above is only a cheap re-show check.
                            host?.reapplyHiding()
                            host?.showSurfaceOwner()
                            // Snapshot the header's layout while the shade is open. The app-only dump
                            // happens after the shade closed, by which point these bounds are gone
                            // (Issue #1: the element jumps when Quick Settings expands).
                            if (host != null) {
                                shadeHeader?.let {
                                    reporter.captureExtras(Diag.treeText("shade header (as last laid out)", it))
                                }
                            }
                        }
                    }
                }
                shadeSettle = settle
                handler.postDelayed(settle, SHADE_SETTLE_MS)
            }
        }
        shadeLayoutListener = listener
        shade.addOnLayoutChangeListener(listener)
    }

    /**
     * FR-03b: the two extra bars that carry their own icon strip, both inside the shade window. The ids
     * are read out of the device's SystemUI (`reverse/SystemUI-device.apk`): the lock screen's
     * `KeyguardStatusBarView` uses `system_icons`, and the pulled-down shade's header
     * (`combined_qs_header`) uses `shade_header_system_icons`. Both draw their own stock icons, which is
     * why the lock screen and the shade looked untouched until they were handled.
     */
    private fun attachExtraBars(shade: View) {
        if (!features.duoRuntime) return
        // R3 A6: the shade fires a layout pass per frame. The Duo extra bars had no throttle, so every
        // pass re-resolved strips and re-ran `attachExtra`/`attachShadeHeader`. One attempt per 500 ms
        // is enough for a transition; the settle below still completes the pass.
        val now = SystemClock.uptimeMillis()
        if (now - lastDuoExtraAttachAt < DUO_EXTRA_ATTACH_MIN_MS) return
        lastDuoExtraAttachAt = now
        val host = host
        if (host == null) {
            ShadeProbe.logOnce("shadeProbe:attachExtraBars:noHost", "attachExtraBars host=null (main bar not ready)")
            return
        }
        ShadeProbe.logOnce(
            "shadeProbe:attachExtraBars:window",
            "attachExtraBars window=${ShadeProbe.describe(shade)} attached=${shade.isAttachedToWindow}"
        )
        host.attachExtra("keyguard bar", shade, rom.keyguardContainerId)
        // The shade header is not in this window; it arrives through hookShadeHeader. It is built early,
        // often before the main bar has attached, so this is also where a failed attempt is retried.
        val header = shadeHeader
        ShadeProbe.logOnce(
            "shadeProbe:attachExtraBars:header:${header != null}",
            "attachExtraBars header=${if (header == null) "absent" else ShadeProbe.describe(header)}"
        )
        if (header == null) {
            // The vendor header controller may not have run yet (live enable-from-off); the visible QS
            // cluster is still resolvable from the shade window, so never stop at "no header".
            val result = host.attachShadeWindow(shade)
            ShadeProbe.logOnce("shadeProbe:attachShadeWindow:$result", "attachShadeWindow result=$result")
            return
        }
        val result = host.attachShadeHeader(header, shade)
        ShadeProbe.logOnce("shadeProbe:attachShadeHeader:$result", "attachShadeHeader result=$result")
        // R10: vivo keeps a GONE/INVISIBLE `combined_qs_header` in the tree, so a present header is not
        // proof of a usable one. Only a dead header falls back to the window; a usable header that is
        // simply not laid out yet keeps the existing bounded retry (no second attach path).
        if (!result &&
            ShadeRoute.shouldTryWindow(
                header.isAttachedToWindow, header.visibility, header.isShown
            )
        ) {
            val windowResult = host.attachShadeWindow(shade)
            ShadeProbe.logOnce(
                "shadeProbe:attachShadeWindow:fallback:$windowResult",
                "attachShadeWindow fallback (dead header) result=$windowResult"
            )
        }
    }

    /**
     * FR-03b: hides icon views as the ROM adds them.
     *
     * A hiding pass on a layout change is not enough on the shade header - the ROM repopulates its
     * `StatusIconContainer` afterwards, so the icons came back after every pass. `StatusIconContainer`
     * is the one container all three bars use for their icons, so hooking the method that adds an
     * icon catches every icon in every bar at the moment it arrives.
     *
     * R8: `XposedBridge.hookAllMethods` is declared-only and the vendor containers declare
     * `onViewAdded`/`onViewRemoved`, not `addView`. The targets come from
     * [IconContainerHooks.declaredTargets] and the reported count is the real unhook count, so a
     * container that declares neither keeps the layout pass as its documented fallback.
     */
    private fun hookStatusIconContainer() {
        L.guard("DuoHook icon container") {
            val callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    L.guard("DuoHook icon added") {
                        // R8: only an add is an arrival; `onViewRemoved` must not masquerade as one.
                        val added = param.method.name == "onViewAdded"
                        // R3 A3: the container is the object the arrival ran on. Registration is
                        // additive; the existing host notifications below stay exactly as they were.
                        val container = param.thisObject as? ViewGroup
                        // R8: the order owner is notified on every route; it is inert unless it is
                        // installed and active, so V2 (which now owns the order) is covered too.
                        if (added && container != null) {
                            IosIconOrder.onIconAdded(container)
                        }
                        if (!added) return@guard
                        val child = param.args.firstOrNull() as? View ?: return@guard
                        // A6: feed only the owner that is live. The other mode's fields are null while
                        // it is off, but the explicit gate keeps a slow async callback from reaching a
                        // host created for the other mode during a live switch.
                        if (features.duoRuntime) host?.onStatusIconAdded(child)
                    }
                }
            }
            var classes = 0
            var methods = 0
            for (name in IconContainerHooks.CLASS_NAMES) {
                try {
                    val cls = XposedHelpers.findClass(name, lp.classLoader)
                    classes++
                    val targets = IconContainerHooks.declaredTargets(cls)
                    if (targets.isEmpty()) {
                        logOnce.once("iconContainer:noTargets:$name") {
                            L.w("$name: no declared onViewAdded/onViewRemoved")
                        }
                        continue
                    }
                    for (method in targets) {
                        methods += XposedBridge.hookAllMethods(cls, method, callback).size
                    }
                } catch (_: Throwable) {
                }
            }
            L.i("status icon container hooks installed: classes=$classes methods=$methods")
            if (methods == 0) {
                logOnce.once("iconContainer:noMethods") {
                    L.w("status icon container hooks: no declared onViewAdded/onViewRemoved - layout pass remains the fallback")
                }
            }
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
                        // `onDarkChanged` carries a float `darkIntensity` and an int `tint`; some OEM
                        // builds pass the intensity as an int too. [BarTint.pickTint] keeps only the
                        // int(s) that can be a resolved colour, so an intensity is never read as one.
                        val color = BarTint.pickTint(param.args)
                        // The shared work (remember the bar colour) always runs; only the owning
                        // mode's redraw is branched.
                        if (color != 0 && BarTint.update(color)) {
                            if (features.duoRuntime) monitor?.onBarAppearanceChanged()
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

    private fun hookFlashlight(): (() -> Unit)? = installGuarded("DuoHook flashlight") {
        val cls = XposedHelpers.findClass(
            "com.android.systemui.statusbar.policy.FlashlightControllerImpl",
            lp.classLoader
        )
        val unhook = XposedHelpers.findAndHookMethod(
            cls, "dispatchListeners",
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!features.duoRuntime) return
                    // dispatchListeners(int what, boolean value): type 1 is onFlashlightChanged;
                    // 2 is availability, 6 is night-light, and 0 is an error. Treating every
                    // callback as the torch state made availability updates light the icon while
                    // the torch was off.
                    val what = param.args.getOrNull(0) as? Int ?: return
                    val state = flashlightStateFromDispatch(
                        what,
                        param.args.getOrNull(1) as? Boolean ?: false
                    ) ?: return
                    monitor?.onFlashlightChanged(state)
                }
            }
        )
        L.i("flashlight event hook installed")
        return@installGuarded { unhook.unhook() }
    }

    private fun hookScreenshot(): (() -> Unit)? = installGuarded("DuoHook screenshot") {
        // AOSP/OnePlus route every screenshot request (hardware key, QS tile, assistant, long screenshot)
        // through TakeScreenshotExecutorImpl.executeScreenshotsAsync. Hooking the async executor catches
        // the request before the capture; it is the same surface on the measured OxygenOS SystemUI.
        val cls = XposedHelpers.findClass(
            "com.android.systemui.screenshot.TakeScreenshotExecutorImpl",
            lp.classLoader
        )
        val hooks = XposedBridge.hookAllMethods(cls, "executeScreenshotsAsync", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!features.duoRuntime) return
                monitor?.onScreenshot()
            }
        })
        if (hooks.isEmpty()) return@installGuarded null
        L.i("screenshot event hook installed")
        return@installGuarded {
            for (unhook in hooks) {
                try {
                    unhook.unhook()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun hookKeyguardUnlock(): (() -> Unit)? = installGuarded("DuoHook keyguard unlock") {
        // ACTION_USER_PRESENT is not delivered to this receiver on OxygenOS, so hook the measured
        // keyguard manager instead. notifyKeyguardAuthenticated(true) is the successful PIN/biometric
        // path; onKeyguardFadedAway is the completion fallback.
        val cls = XposedHelpers.findClass(
            "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager",
            lp.classLoader
        )
        val handles = ArrayList<XC_MethodHook.Unhook>()
        try {
            handles += XposedHelpers.findAndHookMethod(
                cls,
                "notifyKeyguardAuthenticated",
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!features.duoRuntime) return
                        if (param.args.firstOrNull() == true) {
                            L.i("keyguard authenticated -> unlock event")
                            monitor?.onUnlock()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            L.w("keyguard authenticated hook unavailable: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            handles += XposedHelpers.findAndHookMethod(
                cls, "onKeyguardFadedAway", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!features.duoRuntime) return
                        L.i("keyguard faded away -> unlock fallback")
                        monitor?.onUnlock()
                    }
                }
            )
        } catch (t: Throwable) {
            L.w("keyguard faded-away hook unavailable: ${t.javaClass.simpleName}: ${t.message}")
        }
        if (handles.isEmpty()) return@installGuarded null
        L.i("keyguard unlock hook installed")
        return@installGuarded {
            for (unhook in handles) {
                try {
                    unhook.unhook()
                } catch (_: Throwable) {
                }
            }
        }
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
            val cls = try {
                XposedHelpers.findClass(
                    "com.android.systemui.shade.ShadeHeaderController", lp.classLoader
                )
            } catch (t: Throwable) {
                ShadeProbe.log("ShadeHeaderController not found: ${t.javaClass.simpleName}: ${t.message}")
                throw t
            }
            ShadeProbe.log("ShadeHeaderController found: ${cls.name}")
            try {
                XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        L.guard("DuoHook shade header ctor") {
                            val view = param.args.firstOrNull() as? View
                            if (view == null) {
                                ShadeProbe.log("ShadeHeaderController ctor fired without a View arg " +
                                    "(args=${param.args?.size ?: 0})")
                                return@guard
                            }
                            ShadeProbe.log("ShadeHeaderController ctor fired header=${ShadeProbe.describe(view)}")
                            ShadeProbe.tree("header (ctor)", view)
                            L.i("shade header view: ${view.javaClass.name}")
                            shadeHeader = view
                            if (features.duoRuntime) {
                                view.post {
                                    shadeHeader?.let { host?.attachShadeHeader(it, shadeRoot) }
                                }
                            }
                        }
                    }
                })
                ShadeProbe.log("ShadeHeaderController constructor hook installed")
            } catch (t: Throwable) {
                ShadeProbe.log("ShadeHeaderController constructor hook failed: ${t.javaClass.simpleName}: ${t.message}")
                throw t
            }
        }
        L.guard("DuoHook shade header") {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.view.ViewStub", lp.classLoader, "inflate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            L.guard("DuoHook stub inflate") {
                                val view = param.result as? View ?: return@guard
                                ShadeProbe.logOnce(
                                    "shadeProbe:inflate:${ShadeProbe.key(view)}",
                                    "ViewStub.inflate fired root=${ShadeProbe.describe(view)}"
                                )
                                val ctx = app ?: return@guard
                                val id = RomResources.id(ctx, rom, "shade_header_system_icons")
                                if (id == 0 || view.findViewById<View>(id) == null) return@guard
                                ShadeProbe.log("shade header inflated from a stub: ${ShadeProbe.describe(view)}")
                                ShadeProbe.tree("header (stub)", view)
                                L.i("shade header inflated from a stub: ${view.javaClass.name}")
                                shadeHeader = view
                                if (features.duoRuntime) {
                                    view.post {
                                        shadeHeader?.let { host?.attachShadeHeader(it, shadeRoot) }
                                    }
                                }
                            }
                        }
                    }
                )
                ShadeProbe.log("ViewStub.inflate hook installed")
            } catch (t: Throwable) {
                ShadeProbe.log("ViewStub.inflate hook failed: ${t.javaClass.simpleName}: ${t.message}")
                throw t
            }
        }
    }

    private companion object {
        const val FIRST_DELAY_MS = 2_500L

        /** How long a late injection waits for the Application before it relies on the bar's context. */
        const val BOOTSTRAP_RETRY_MS = 500L
        const val MAX_BOOTSTRAP_ATTEMPTS = 20

        /** After a shade drag settles, the ROM re-shows its icon views once more. */
        const val SHADE_SETTLE_MS = 400L

        /** Give the restart broadcast a moment to finish before the process goes. */
        const val RESTART_DELAY_MS = 300L

        /** How long a burst of settings changes is allowed to settle before one apply runs. */
        const val SETTINGS_DEBOUNCE_MS = 250L
        const val RETRY_MS = 2_000L
        const val MAX_ATTEMPTS = 6

        /** R3 A6: the plain-Duo extra bars use the same cadence. */
        const val DUO_EXTRA_ATTACH_MIN_MS = 500L
    }
}

/**
 * FIX 1 / C11: a settings apply that only failed because the provider was unreachable must not restore
 * the stock iOS icons. The snapshot then still says [BarOwner.NONE] (the default), so the owner alone
 * cannot tell "unreachable" apart from "the user switched iOS off"; the decision action can.
 *
 * Pure, so the order in `settingsApply` is pinned without an Android/SystemUI seam.
 */
internal fun keepIosOnUnreachable(owner: BarOwner, action: SettingsApplyAction): Boolean =
    owner == BarOwner.NONE && action == SettingsApplyAction.KEEP_UNREACHABLE

/**
 * R7 P4: the one additive dump line that names the live delivery route.
 *
 * It is a separate [IosDiagnosticSource] so the existing engine/battery lines and their parsers are
 * untouched; the line appears even before the first activation (`route=none family=<rom>`).
 */
internal object IosRouteDiagnostics : IosDiagnosticSource {
    @Volatile
    private var route: IosRoute = IosRoute.NONE

    @Volatile
    private var family: String = "none"

    fun update(route: IosRoute, family: String) {
        this.route = route
        this.family = family.ifBlank { "none" }
    }

    override fun diagnosticLines(): List<String> =
        listOf("ios v2 route=${route.name.lowercase()} family=$family")

    internal fun resetForTest() = update(IosRoute.NONE, "none")
}
