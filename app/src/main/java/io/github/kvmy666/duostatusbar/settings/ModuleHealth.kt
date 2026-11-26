package io.github.kvmy666.duostatusbar.settings

import android.content.Context

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
    NOT_REPORTING
}

internal data class ModuleHealth(
    val state: ModuleState,
    val loadedAt: Long,
    val appUpdatedAt: Long,
    val mode: ModuleMode = ModuleMode.OFF
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
        // The module is effectively on when either orientation is on: an unsaved landscape copy reads
        // the portrait one, so reading both never reports "off" while the bar is drawing.
        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        val duoEnabled = portrait.enabled || landscape.enabled
        // iOS owns the bar when its switch is on, even if the Duo switch is off (the field reports).
        val iosEnabled = portrait.iosMode || landscape.iosMode
        return ModuleHealth(
            decide(
                loadedAt,
                updatedAt,
                statusBlank,
                heartbeat,
                System.currentTimeMillis(),
                settingsEnabled = duoEnabled,
                iosEnabled = iosEnabled
            ),
            loadedAt,
            updatedAt,
            modeFor(duoEnabled, iosEnabled)
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
     * [settingsEnabled] is the legacy name for the Duo switch ([duoEnabled]); [iosEnabled] is the iOS
     * switch. The switches are checked *after* the load/heartbeat conditions because a module that never
     * loaded is a setup problem, not a turned-off one: telling that user to "turn it on" would skip the
     * LSPosed steps they actually need. They are also checked after [ModuleState.NEEDS_RESTART] because
     * an APK update still needs the restart regardless of a switch — the running code is the old build
     * either way. iOS owning the bar is healthy even when the Duo switch is off; [ModuleState.DISABLED]
     * means neither mode is on.
     */
    fun decide(
        loadedAt: Long,
        appUpdatedAt: Long,
        statusBlank: Boolean,
        heartbeatAt: Long = 0L,
        nowMs: Long = 0L,
        settingsEnabled: Boolean = true,
        iosEnabled: Boolean = false,
        duoEnabled: Boolean = settingsEnabled
    ): ModuleState = when {
        loadedAt <= 0L -> ModuleState.NEVER_LOADED
        heartbeatAt <= 0L -> ModuleState.NEVER_LOADED
        nowMs > 0L && nowMs - heartbeatAt > DuoPrefs.HEARTBEAT_STALE_MS -> ModuleState.NEVER_LOADED
        // The APK changed after the module last loaded, so System UI is running the previous build.
        appUpdatedAt > loadedAt -> ModuleState.NEEDS_RESTART
        !duoEnabled && !iosEnabled -> ModuleState.DISABLED
        // iOS owns the bar: healthy even when the Duo switch is off (the old DISABLED field report).
        iosEnabled -> ModuleState.OK
        statusBlank -> ModuleState.NOT_REPORTING
        else -> ModuleState.OK
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
