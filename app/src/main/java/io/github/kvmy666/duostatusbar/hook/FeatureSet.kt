package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * Which host owns the status bar. `NONE` means no drawing host is active (the kill switch or the user
 * master switch is off), so only the shared registrations stay.
 */
internal enum class BarOwner(val tag: String) {
    NONE("none"),
    DUO("duo"),
    IOS("ios")
}

/** The renderer the Duo host would use. iOS and the off states have no Duo renderer. */
internal enum class DuoRenderer(val tag: String) {
    NONE("none"),
    RIVE("rive"),
    CANVAS("canvas")
}

/**
 * Every optional, user-visible feature in [ModuleSettings].
 *
 * The [tag] is the short stable name used in the boot line and in tests. It deliberately does not
 * change with the enum constant order.
 */
internal enum class DuoFeature(val tag: String) {
    RING("ring"),
    PERCENT("percent"),
    SPLIT("split"),
    LOCK_RING("lock_ring"),
    CLOCK_FONT("clock_font"),
    ANIMATIONS("animations"),
    ARRIVAL("arrival"),
    DEPARTURE("departure"),
    CHARGING_MOTION("charging_motion"),
    CHARGING_BOLT("charging_bolt"),
    RING_GESTURES("ring_gestures"),
    INDICATOR_GESTURES("indicator_gestures"),
    QS_SURFACE("qs_surface"),
    SHADE_SURFACE("shade_surface"),
    HIDE_OTHER("hide_other"),
    HIDDEN_KEYS("hidden_keys"),
    WIFI_DOTS("wifi_dots"),
    WIFI_GLYPH("wifi_glyph"),
    CELL_DOTS("cell_dots"),
    AIRPLANE("airplane"),
    DND("dnd"),
    DOWNLOAD("download"),
    BURN_IN("burn_in"),
    EVENT_ALARM("event_alarm"),
    EVENT_FLASHLIGHT("event_flashlight"),
    EVENT_SCREENSHOT("event_screenshot"),
    EVENT_UNLOCK("event_unlock"),
    EVENT_CALL("event_call")
}

/**
 * Registrations that are infrastructure for both drawing modes and therefore stay registered
 * whichever host owns the bar (and even while the kill switch pins the module off, so it can come
 * back without a reboot).
 */
internal enum class SharedHook(val tag: String) {
    WINDOW("window"),
    APP("app"),
    SETTINGS("settings"),
    ORIENTATION("orientation"),
    ICON_ADD("icon_add"),
    SHADE_HEADER("shade_header"),
    VENDOR_SHADE("vendor_shade"),
    BAR_TINT("bar_tint"),
    SHADE_LAYOUT("shade_layout")
}

/**
 * The resolved feature model for one settings snapshot. It is deliberately data-only so the runtime
 * can decide what to register without re-reading settings inside every hook.
 */
internal data class FeatureSet(
    val owner: BarOwner,
    val renderer: DuoRenderer,
    val duo: Set<DuoFeature>,
    val shared: Set<SharedHook>
) {
    /** True only while the Duo host owns the bar; every Duo entry point checks this. */
    val duoRuntime: Boolean get() = owner == BarOwner.DUO

    /** True only while the iOS host owns the bar; iOS code stays inert otherwise. */
    val iosRuntime: Boolean get() = owner == BarOwner.IOS

    fun has(feature: DuoFeature): Boolean = feature in duo

    /**
     * The same shared registrations with no owner and no Duo feature.
     *
     * A stop assigns this snapshot before it diffs registrations, so the diff reads the state the
     * runtime is left in instead of the DUO set that triggered the teardown (B).
     */
    fun stopped(): FeatureSet =
        copy(owner = BarOwner.NONE, renderer = DuoRenderer.NONE, duo = emptySet())
}

/**
 * True only when the Duo host should be created.
 *
 * An off orientation (owner NONE, including when the other orientation has Duo on) keeps no host: the
 * next enable for the on orientation attaches it, so a vendor shade callback can never reach Duo work
 * while Duo is off (A).
 */
internal fun shouldCreateDuoHost(features: FeatureSet): Boolean = features.duoRuntime

/**
 * The one shared shade layout listener belongs to either live owner. It is removed only when nothing
 * owns the bar, so switching Duo -> iOS keeps it installed (D).
 */
internal fun keepShadeLayoutListener(features: FeatureSet): Boolean =
    features.owner != BarOwner.NONE

/** Shared registrations are mode-independent; see [SharedHook]. */
private val ALL_SHARED: Set<SharedHook> = SharedHook.entries.toSet()

/**
 * Resolves the owner, renderer and active features from one settings snapshot.
 *
 * Precedence matches the runtime gates:
 *  1. the adb override `0` kills both hosts (shared registrations only);
 *  2. an adb override `1`/`2` forces Duo and suppresses iOS, with the renderer taken from the stage;
 *  3. iOS mode when the drawing is actually available now makes iOS own the bar and turns every Duo
 *     feature off;
 *  4. an explicit off (neither switch on) leaves nothing owning the bar;
 *  5. otherwise Duo owns it with the engine-derived renderer. That includes `iosMode` asked for on a
 *     device where iOS cannot run: Duo must keep the bar, never nothing (D13/D14).
 *
 * [iosDrawingReady] is the "iOS is available now" argument: the family/engine/battery seam plus any
 * recorded route failure ([IosAvailability.effective]).
 *
 * [otherOrientationEnabled] is accepted so the caller can keep the shared registrations (and the boot
 * line) stable when the other orientation has Duo on. It never changes the owner: the current settings
 * snapshot is the only owner decision.
 */
internal fun featureSetFor(
    settings: ModuleSettings?,
    adbOverride: Int?,
    iosDrawingReady: Boolean,
    @Suppress("UNUSED_PARAMETER") otherOrientationEnabled: Boolean
): FeatureSet {
    val resolved = settings ?: ModuleSettings.DEFAULT

    if (adbOverride == DuoGuard.OFF) {
        return FeatureSet(BarOwner.NONE, DuoRenderer.NONE, emptySet(), ALL_SHARED)
    }

    if (adbOverride != null) {
        // An explicit stage wins over both master switches: 1 is the Canvas bar, 2 is Rive. iOS is
        // suppressed because DuoGuard.stage() returns the override before the iOS branch.
        val renderer = if (adbOverride == DuoGuard.RIVE) DuoRenderer.RIVE else DuoRenderer.CANVAS
        return FeatureSet(BarOwner.DUO, renderer, duoFeatures(resolved), ALL_SHARED)
    }

    if (IosGuard.active(resolved.iosMode, null, iosDrawingReady)) {
        // iOS owns the bar: no Duo feature may be registered, so its set is empty by construction.
        return FeatureSet(BarOwner.IOS, DuoRenderer.NONE, emptySet(), ALL_SHARED)
    }

    if (!resolved.enabled && !resolved.iosMode) {
        // Both switches are off: nothing owns the bar. `iosMode` alone is deliberately not "off": an
        // unavailable iOS mode must fall through to Duo instead of leaving the bar ownerless.
        return FeatureSet(BarOwner.NONE, DuoRenderer.NONE, emptySet(), ALL_SHARED)
    }

    val renderer =
        if (resolved.engineMode == DuoPrefs.ENGINE_CANVAS || !resolved.useRive) DuoRenderer.CANVAS
        else DuoRenderer.RIVE
    return FeatureSet(BarOwner.DUO, renderer, duoFeatures(resolved), ALL_SHARED)
}

/**
 * One flag per optional feature, using exactly the fields in [ModuleSettings].
 *
 * `RING` has no switch in [ModuleSettings]: the ring is the base drawing whenever Duo owns the bar, so
 * it is always present. `DND` is on for either drawn mode ("middle" or "dots"), not only the moon in
 * the ring. Events whose bit is set in `eventMask` are active; the screenshot and flashlight bits have
 * no state-monitor receiver of their own.
 */
private fun duoFeatures(settings: ModuleSettings): Set<DuoFeature> = buildSet {
    add(DuoFeature.RING)
    if (settings.showPercent) add(DuoFeature.PERCENT)
    if (settings.splitIndicators) add(DuoFeature.SPLIT)
    if (settings.lockRing) add(DuoFeature.LOCK_RING)
    if (settings.systemClockFont) add(DuoFeature.CLOCK_FONT)
    if (settings.animationsEnabled) add(DuoFeature.ANIMATIONS)
    if (settings.arrivalEnabled) add(DuoFeature.ARRIVAL)
    if (settings.departureEnabled) add(DuoFeature.DEPARTURE)
    if (settings.chargingEnabled) add(DuoFeature.CHARGING_MOTION)
    if (settings.showChargingBolt) add(DuoFeature.CHARGING_BOLT)
    if (settings.hasRingGesture()) add(DuoFeature.RING_GESTURES)
    if (settings.hasIndicatorGesture()) add(DuoFeature.INDICATOR_GESTURES)
    if (settings.showInQs) add(DuoFeature.QS_SURFACE)
    if (settings.showInShade) add(DuoFeature.SHADE_SURFACE)
    if (settings.hideOtherIcons) add(DuoFeature.HIDE_OTHER)
    if (DuoPrefs.parseHiddenStockIcons(settings.hiddenStockIcons).isNotEmpty()) {
        add(DuoFeature.HIDDEN_KEYS)
    }
    if (settings.wifiDots) add(DuoFeature.WIFI_DOTS)
    if (settings.showWifiGlyph) add(DuoFeature.WIFI_GLYPH)
    if (settings.showCellDots) add(DuoFeature.CELL_DOTS)
    if (settings.showAirplane) add(DuoFeature.AIRPLANE)
    if (settings.dndMode != DuoPrefs.DND_OFF) add(DuoFeature.DND)
    if (settings.downloadProgressEnabled) add(DuoFeature.DOWNLOAD)
    if (settings.burnInEnabled) add(DuoFeature.BURN_IN)
    if (DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_ALARM)) add(DuoFeature.EVENT_ALARM)
    if (DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_FLASHLIGHT)) {
        add(DuoFeature.EVENT_FLASHLIGHT)
    }
    if (DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_SCREENSHOT)) {
        add(DuoFeature.EVENT_SCREENSHOT)
    }
    if (DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_UNLOCK)) add(DuoFeature.EVENT_UNLOCK)
    if (DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_CALL)) add(DuoFeature.EVENT_CALL)
}

private const val NO_ACTION = "no_action"

private fun ModuleSettings.hasRingGesture(): Boolean =
    tapAction != NO_ACTION || doubleTapAction != NO_ACTION || longPressAction != NO_ACTION

private fun ModuleSettings.hasIndicatorGesture(): Boolean =
    indicatorsTapAction != NO_ACTION ||
        indicatorsDoubleTapAction != NO_ACTION ||
        indicatorsLongPressAction != NO_ACTION

// ---------------------------------------------------------------- state-monitor receivers

// Spelled out rather than referencing Android classes so the model stays unit-testable without a
// device, and so the log line and tests pin the exact contract. The values mirror the constants used
// by DuoStateMonitor.start().
private const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"
private const val ACTION_POWER_SAVE = "android.os.action.POWER_SAVE_MODE_CHANGED"
private const val ACTION_AIRPLANE_MODE = "android.intent.action.AIRPLANE_MODE"
private const val ACTION_INTERRUPTION_FILTER = "android.app.action.INTERRUPTION_FILTER_CHANGED"
private const val ACTION_RINGER_MODE = "android.media.RINGER_MODE_CHANGED"
private const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
private const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"
private const val ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT"
private const val ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE"
private const val ACTION_CONFIGURATION_CHANGED = "android.intent.action.CONFIGURATION_CHANGED"
private const val ACTION_TIME_CHANGED = "android.intent.action.TIME_SET"
private const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
private const val ACTION_LOCALE_CHANGED = "android.intent.action.LOCALE_CHANGED"
private const val ACTION_TIME_TICK = "android.intent.action.TIME_TICK"
private const val ACTION_NEXT_ALARM = "android.app.action.NEXT_ALARM_CLOCK_CHANGED"
private const val ACTION_WIFI_RSSI = "android.net.wifi.RSSI_CHANGED"
private const val ACTION_WIFI_STATE = "android.net.wifi.WIFI_STATE_CHANGED"
private const val ACTION_CONNECTIVITY = "android.net.conn.CONNECTIVITY_CHANGE"
private const val ACTION_SERVICE_STATE = "android.intent.action.SERVICE_STATE"
private const val ACTION_ANY_DATA_STATE = "android.intent.action.ANY_DATA_STATE"

/** Same contract as [DownloadProgress.ACTION]; pinned here so the model has no Android dependency. */
internal const val ACTION_DOWNLOAD = "io.github.kvmy666.duostatusbar.action.DOWNLOAD"

/**
 * The [DuoStateMonitor] receiver actions required by [features], or an empty list when Duo does not own
 * the bar.
 *
 * Baseline (always while Duo owns the bar): battery/power save, screen on/off, configuration, and the
 * three time/locale changes. `TIME_TICK` is added while the clock feature or burn-in needs it: this
 * model has no separate clock-visibility switch, so `CLOCK_FONT` is the clock feature here. If the
 * runtime draws the clock middle slot even with the system-font switch off, the caller must keep
 * `TIME_TICK` for that slot (documented in the report rather than guessed at here).
 *
 * Feature-specific actions:
 *  - `DOWNLOAD` -> the Punch Hole download broadcast;
 *  - `EVENT_ALARM` -> next-alarm changes; `EVENT_CALL` -> phone state; `EVENT_UNLOCK` -> user present;
 *  - `AIRPLANE` -> airplane changes, also needed while `WIFI_DOTS` turns the spheres into Wi-Fi;
 *  - `DND` -> interruption filter and ringer mode;
 *  - Wi-Fi (`WIFI_GLYPH`/`WIFI_DOTS`) -> RSSI, Wi-Fi state and connectivity;
 *  - `CELL_DOTS` -> connectivity plus service/data state.
 *
 * `EVENT_FLASHLIGHT` and `EVENT_SCREENSHOT` add no receiver here: the flashlight rides the system
 * state and the screenshot watcher is a separate observer, not a broadcast action.
 *
 * The result is sorted so it is deterministic in tests and logs.
 */
internal fun monitorActions(features: FeatureSet): List<String> {
    if (features.owner != BarOwner.DUO) return emptyList()
    val duo = features.duo
    val actions = LinkedHashSet<String>()

    actions += ACTION_BATTERY_CHANGED
    actions += ACTION_POWER_SAVE
    actions += ACTION_SCREEN_OFF
    actions += ACTION_SCREEN_ON
    actions += ACTION_CONFIGURATION_CHANGED
    actions += ACTION_TIME_CHANGED
    actions += ACTION_TIMEZONE_CHANGED
    actions += ACTION_LOCALE_CHANGED

    if (DuoFeature.CLOCK_FONT in duo || DuoFeature.BURN_IN in duo) actions += ACTION_TIME_TICK
    if (DuoFeature.DOWNLOAD in duo) actions += ACTION_DOWNLOAD
    if (DuoFeature.EVENT_ALARM in duo) actions += ACTION_NEXT_ALARM
    if (DuoFeature.EVENT_CALL in duo) actions += ACTION_PHONE_STATE
    if (DuoFeature.EVENT_UNLOCK in duo) actions += ACTION_USER_PRESENT

    if (DuoFeature.AIRPLANE in duo || DuoFeature.WIFI_DOTS in duo) actions += ACTION_AIRPLANE_MODE
    if (DuoFeature.DND in duo) {
        actions += ACTION_INTERRUPTION_FILTER
        actions += ACTION_RINGER_MODE
    }

    val wifi = DuoFeature.WIFI_DOTS in duo || DuoFeature.WIFI_GLYPH in duo
    val cell = DuoFeature.CELL_DOTS in duo
    if (wifi) {
        actions += ACTION_WIFI_RSSI
        actions += ACTION_WIFI_STATE
    }
    if (wifi || cell) actions += ACTION_CONNECTIVITY
    if (cell) {
        actions += ACTION_SERVICE_STATE
        actions += ACTION_ANY_DATA_STATE
    }

    return actions.sorted()
}

/**
 * The receiver actions the real monitor must register for [features].
 *
 * This is [monitorActions] plus the one caveat documented there: the middle slot may draw the clock
 * even when the system-font switch (`CLOCK_FONT`) is off, and that clock still needs the minute tick.
 * [clockSlotShowsClock] supplies that runtime fact; it can only add the tick to a non-empty Duo set,
 * so iOS/NONE still register nothing.
 */
internal fun monitorActionsForRuntime(features: FeatureSet, clockSlotShowsClock: Boolean): List<String> {
    val actions = monitorActions(features)
    if (actions.isEmpty() || !clockSlotShowsClock) return actions
    return (actions + ACTION_TIME_TICK).distinct().sorted()
}

/**
 * The app-visible status line. It carries exactly the boot line's owner/renderer/stage/override/rev/
 * active/shared fields, so the diagnostics screen shows the same facts as the log (E).
 */
internal fun featureStatus(
    features: FeatureSet,
    stage: Int,
    revision: Long,
    `override`: Int?
): String = featureLogLine(features, stage, revision, `override`)

/**
 * The single boot line that lists what is registered. Deterministic: owner/renderer/stage/override/rev
 * in a fixed order, then the active and shared tags sorted.
 *
 * Example:
 * `features: owner=duo renderer=rive stage=2 override=none rev=7 active=percent,ring shared=app,window`
 */
internal fun featureLogLine(
    features: FeatureSet,
    stage: Int,
    revision: Long,
    `override`: Int?
): String {
    val active = features.duo.map { it.tag }.sorted().joinToString(",")
    val shared = features.shared.map { it.tag }.sorted().joinToString(",")
    val overrideText = `override`?.toString() ?: "none"
    return "features: owner=${features.owner.tag} renderer=${features.renderer.tag} stage=$stage " +
        "override=$overrideText rev=$revision active=$active shared=$shared"
}
