package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.ui.dumpVersion

/** How the module is doing, as the app can tell from stores it can read without root. */
internal enum class ModuleState {
    /** Loaded and reporting. */
    OK,

    /** LSPosed has not injected the module into System UI (never loaded). */
    NEVER_LOADED,

    /** The app APK was updated after the module last loaded: System UI still runs the old code. */
    NEEDS_RESTART,

    /**
     * The module loaded and heartbeats, but neither Duo nor iOS is switched on: nothing is broken,
     * the element is simply not wanted.
     */
    DISABLED,

    /** The module loaded but never reported: the settings channel is broken (the One UI 8 case). */
    NOT_REPORTING,

    /** A fresh capability report says the real owner is iOS: Duo is off while the iOS bar draws. */
    IOS_ACTIVE,

    /**
     * The user asked for iOS, the module is loaded, and its fresh report says iOS is not deliverable
     * on this UI. Duo keeps the bar; this is a limitation, not a broken module.
     */
    IOS_UNSUPPORTED
}

internal data class ModuleHealth(
    val state: ModuleState,
    val loadedAt: Long,
    val appUpdatedAt: Long,
    val mode: ModuleMode = ModuleMode.OFF,
    /**
     * The build the running module last wrote in its diagnostic dump. Wording only: the load/update
     * timestamps decide [ModuleState.NEEDS_RESTART], so a missing dump cannot hide the restart.
     */
    val runningVersion: String? = null
)

/** Which host owns the bar, as the report labels it. */
internal enum class ModuleMode(val label: String) {
    DUO("Duo active"),
    IOS("iOS active"),
    OFF("off")
}

/**
 * Diagnoses the module from stores the app can always read, even when the settings provider is
 * invisible to System UI:
 *
 *  - `Settings.Global.duo_statusbar_last_load`, which the module stamps *before* the gate; and
 *  - the app's own APK `lastUpdateTime`.
 *
 * That distinguishes the two failures a "no report yet" state hides: the app was updated and System UI
 * was never restarted (the running module is the old code), versus the module loaded but its provider
 * call is rejected (`Unknown authority …`, measured on One UI 8) so it can never report.
 *
 * The user's own switch (read from the settings store, both orientations) is a third input, so a loaded
 * module the user turned off reports [ModuleState.DISABLED] instead of looking broken.
 */
internal object ModuleHealthCheck {

    fun of(context: Context): ModuleHealth {
        val loadedAt = DuoPrefs.moduleLoadTime(context)
        val statusBlank = DuoPrefs.status(context).isBlank()
        val updatedAt = appUpdatedAt(context)
        val heartbeat = DuoPrefs.heartbeat(context)
        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        // The module is effectively on when either orientation is on: an unsaved landscape copy reads
        // the portrait one, so reading both never reports "off" while the bar is drawing.
        val duoEnabled = portrait.enabled || landscape.enabled
        // iOS owns the bar when its switch is on, even if the Duo switch is off (the field reports).
        val iosEnabled = portrait.iosMode || landscape.iosMode
        // Only a report from the current module session is trusted; a missing/stale one is UNKNOWN.
        val capability = IosCapability.read(context, loadedAt)
        // The build the loaded module is. A load newer than the APK update means it is this build; only
        // a pending update (APK newer than the load) still runs the dump's build.
        val runningVersion = runningVersion(
            loadedAt,
            updatedAt,
            dumpVersion(DuoPrefs.dump(context)),
            BuildConfig.VERSION_NAME
        )
        return ModuleHealth(
            decide(
                loadedAt,
                updatedAt,
                statusBlank,
                heartbeat,
                System.currentTimeMillis(),
                settingsEnabled = duoEnabled,
                iosMode = iosEnabled,
                capability = capability
            ),
            loadedAt,
            updatedAt,
            modeFor(duoEnabled, iosEnabled),
            runningVersion
        )
    }

    /**
     * Pure decision, so the rule is unit-tested rather than only exercised on a phone.
     *
     * [heartbeatAt] is the module's "alive right now" stamp and [nowMs] the clock. The module is only
     * trusted when that stamp is present and fresh: a heartbeat of 0 means it never ran (or the running
     * build predates the heartbeat), and an old one means it was unscoped and System UI restarted without
     * it. Both are [ModuleState.NEVER_LOADED], so a report taken then cannot ship the previous run's
     * dump/status as if the module were healthy (measured: unscope, restart, send -> full but stale).
     *
     * An installed APK newer than [loadedAt] is checked **before** the heartbeat: the running process
     * still executes the previous build, which cannot heartbeat the new code, so its silent heartbeat
     * is not a load failure. The only correct advice there is [ModuleState.NEEDS_RESTART]; calling it
     * NEVER_LOADED sent a 2.1.2 app report claiming the 2.1.1 module was never injected.
     *
     * [settingsEnabled] is the legacy name for the Duo switch ([duoEnabled]); [iosMode] is the iOS
     * switch. The switches are checked *after* the load/heartbeat conditions because a module that never
     * loaded is a setup problem, not a turned-off one: telling that user to "turn it on" would skip the
     * LSPosed steps they actually need. They are also checked after [ModuleState.NEEDS_RESTART] because
     * an APK update still needs the restart regardless of a switch — the running code is the old build
     * either way. iOS owning the bar is healthy even when the Duo switch is off; [ModuleState.DISABLED]
     * means neither mode is on.
     *
     * [iosMode] and [capability] come from the same session as [loadedAt]. A valid report whose owner is
     * iOS **and whose live route is [IosCapability.ROUTE_V2]** means iOS is actually drawing, so the
     * state is [ModuleState.IOS_ACTIVE] even though `enabled` is false. An owner tag alone is not enough:
     * the module can briefly report `owner=ios` with `route=none` while refusing an activation and
     * falling back to Duo, and a transient report must not claim iOS is active. A valid report that says
     * iOS is not supported while the user asked for it is [ModuleState.IOS_UNSUPPORTED] and outranks the
     * owner tag; Duo still owns the bar, so it is never [ModuleState.DISABLED]. An UNKNOWN report (null)
     * changes nothing: iOS mode is not turned into "off" and no owner is claimed.
     */
    fun decide(
        loadedAt: Long,
        appUpdatedAt: Long,
        statusBlank: Boolean,
        heartbeatAt: Long = 0L,
        nowMs: Long = 0L,
        settingsEnabled: Boolean = true,
        iosMode: Boolean = false,
        capability: IosCapability? = null,
        /** R15's name for [iosMode]; either switch on means iOS was requested. */
        iosEnabled: Boolean = false
    ): ModuleState {
        // The two names are the same switch; accept either so the R15 mode tests and the capability
        // tests exercise one rule.
        val iosRequested = iosMode || iosEnabled
        return when {
            loadedAt <= 0L -> ModuleState.NEVER_LOADED
            // The APK changed after the module last loaded, so System UI is running the previous build.
            // This outranks the heartbeat checks: the old build cannot write the new heartbeat.
            appUpdatedAt > loadedAt -> ModuleState.NEEDS_RESTART
            heartbeatAt <= 0L -> ModuleState.NEVER_LOADED
            nowMs > 0L && nowMs - heartbeatAt > DuoPrefs.HEARTBEAT_STALE_MS -> ModuleState.NEVER_LOADED
            // A refusal outranks the owner tag: the fallback report can still carry owner=ios.
            iosRequested && capability != null && !capability.supported -> ModuleState.IOS_UNSUPPORTED
            // Active means the live route, not just the owner tag (route=none is a transient/fallback).
            capability != null &&
                capability.owner == IosCapability.OWNER_IOS &&
                capability.route == IosCapability.ROUTE_V2 -> ModuleState.IOS_ACTIVE
            !settingsEnabled && !iosRequested -> ModuleState.DISABLED
            statusBlank -> ModuleState.NOT_REPORTING
            else -> ModuleState.OK
        }
    }

    /**
     * The build the *loaded* module is, as far as the two stamps prove it.
     *
     * [loadedAt] is written by the module that System UI started; [appUpdatedAt] is when this APK was
     * installed. A load after the update means the old process is gone, so the loaded code is this APK —
     * even when the stored dump is old and still says `version: 2.1.1` (the field case). Only while the
     * APK is newer than the load is the running build the previous one, and then the dump names it.
     * Equal or missing stamps prove nothing. The dump never decides this: a stale dump made a current
     * module look old in the app's own report.
     */
    fun runningVersion(
        loadedAt: Long,
        appUpdatedAt: Long,
        dumpVersion: String?,
        appVersion: String
    ): String? = when {
        loadedAt <= 0L -> null
        appUpdatedAt > loadedAt -> dumpVersion?.trim()?.takeIf { it.isNotEmpty() }
        loadedAt > appUpdatedAt -> appVersion.trim().takeIf { it.isNotEmpty() }
        else -> null
    }

    /** The configured owner: iOS takes precedence over Duo, matching the runtime feature gate. */
    fun modeFor(duoEnabled: Boolean, iosEnabled: Boolean): ModuleMode = when {
        iosEnabled -> ModuleMode.IOS
        duoEnabled -> ModuleMode.DUO
        else -> ModuleMode.OFF
    }

    @Suppress("DEPRECATION")
    private fun appUpdatedAt(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (_: Throwable) {
        0L
    }
}
