package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings

/**
 * The user's settings, and the contract that carries them into System UI (FR-03/16/17).
 *
 * Why a provider rather than the obvious `Settings.Global`: this app is a normal app, so it cannot write
 * global settings (`WRITE_SECURE_SETTINGS` is a privileged permission it will never hold). The module, on
 * the other hand, runs *inside* System UI and can read anything — so the app owns the values and publishes
 * them, and the module reads them:
 *
 *     app  --writes--> SharedPreferences --publishes--> exported ContentProvider
 *                                                              |
 *                                          module (System UI) reads via ContentResolver
 *
 * The provider is exported without a permission on purpose: System UI is a different uid and holds no
 * signature permission of ours, so requiring one would make the channel unusable. What it exposes is
 * non-sensitive — whether the element is on, which renderer, its size and offset. Nothing here is a secret,
 * and a rogue reader can only learn that the module is installed.
 *
 * `duo_statusbar_stage` in `Settings.Global` remains the **developer override and kill switch**, and it wins
 * when it is present (see the module's `DuoGuard`): `adb shell settings put global duo_statusbar_stage 0`
 * must always be able to switch the module off regardless of what this app says.
 */
data class DuoSettings(
    /** FR-03: the master switch. */
    val enabled: Boolean = false,
    /** Prefer Rive; false means "always use the Canvas drawing" (no native code in the path). */
    val useRive: Boolean = true,
    /** FR-16: show the battery percentage in the ring's top gap. */
    val showPercent: Boolean = true,
    /** FR-03/17: element size as a percentage of the measured slot. */
    val sizePercent: Int = 100,
    /** FR-17: horizontal nudge inside the slot, in dp, from the drag editor. */
    val offsetX: Int = 0,
    /**
     * Whether a size/position change reaches the running status bar immediately. Off means the value
     * is saved but only applied on the next start (the Restart System UI button), which is the safe
     * path: resizing the Rive view while System UI runs is what used to take it down.
     */
    val liveApply: Boolean = true,
    /**
     * Whether the status-bar clock is redrawn in the phone's own system font. The module never hides
     * the clock; this only swaps its typeface so it matches the element's digits.
     */
    val systemClockFont: Boolean = true,
    /**
     * FR-25: how long the arrival takes, in ms. One Rive timeline played at five speeds, so this is a
     * choice from [DuoPrefs.REVEAL_CHOICES] rather than a free number.
     */
    val revealMs: Int = 1000,
    /**
     * The Animations section (FR-25). `animationsEnabled` is the master switch; when it is off none of
     * the decorative motion plays. The three switches below pick which of them are allowed when the
     * master is on. Off means the change is instant, not that the feature stops working.
     */
    val animationsEnabled: Boolean = true,
    val arrivalEnabled: Boolean = true,
    val departureEnabled: Boolean = true,
    val chargingEnabled: Boolean = true,
    /**
     * FR-05/18: what a gesture on the element asks Auto Expand to do. Its action keys, or `no_action`.
     * The defaults mean the element consumes no touches at all — no gestures, no conflicts.
     */
    val tapAction: String = "no_action",
    val doubleTapAction: String = "no_action",
    val longPressAction: String = "no_action"
)

object DuoPrefs {

    const val AUTHORITY = "io.github.kvmy666.duostatusbar.settings"
    const val ACTION_SETTINGS_CHANGED = "io.github.kvmy666.duostatusbar.SETTINGS_CHANGED"

    /**
     * Sent by the app when the user taps "Restart System UI". The module lives inside System UI, so it is
     * the only side that can restart it: it kills its own process and Android brings System UI straight
     * back. Needed because the size only takes effect on a fresh start (resizing it live is what used to
     * take System UI down).
     */
    const val ACTION_RESTART_SYSTEMUI = "io.github.kvmy666.duostatusbar.RESTART_SYSTEMUI"

    const val COL_ENABLED = "enabled"
    const val COL_USE_RIVE = "use_rive"
    const val COL_SHOW_PERCENT = "show_percent"
    const val COL_SIZE_PERCENT = "size_percent"
    const val COL_OFFSET_X = "offset_x"
    const val COL_LIVE_APPLY = "live_apply"
    const val COL_CLOCK_FONT = "clock_font"
    const val COL_REVISION = "revision"
    const val COL_TAP = "tap_action"
    const val COL_DOUBLE_TAP = "double_tap_action"
    const val COL_LONG_PRESS = "long_press_action"
    const val COL_REVEAL_MS = "reveal_ms"
    const val COL_ANIMATIONS = "animations_enabled"
    const val COL_ARRIVAL = "arrival_enabled"
    const val COL_DEPARTURE = "departure_enabled"
    const val COL_CHARGING = "charging_enabled"

    /** The column set the module expects; kept in one place so both sides cannot drift. */
    val COLUMNS = arrayOf(
        COL_ENABLED, COL_USE_RIVE, COL_SHOW_PERCENT, COL_SIZE_PERCENT, COL_OFFSET_X,
        COL_LIVE_APPLY, COL_CLOCK_FONT, COL_REVISION,
        COL_TAP, COL_DOUBLE_TAP, COL_LONG_PRESS, COL_REVEAL_MS,
        COL_ANIMATIONS, COL_ARRIVAL, COL_DEPARTURE, COL_CHARGING
    )

    private const val PREFS = "duo_settings"
    private const val KEY_REVISION = "revision"
    private const val KEY_STATUS = "last_status"
    private const val KEY_HISTORY = "status_history"
    private const val KEY_DUMP = "last_dump"
    private const val HISTORY_LIMIT = 20

    fun read(context: Context): DuoSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DuoSettings(
            enabled = p.getBoolean(COL_ENABLED, false),
            useRive = p.getBoolean(COL_USE_RIVE, true),
            showPercent = p.getBoolean(COL_SHOW_PERCENT, true),
            sizePercent = p.getInt(COL_SIZE_PERCENT, 100),
            offsetX = p.getInt(COL_OFFSET_X, 0),
            liveApply = p.getBoolean(COL_LIVE_APPLY, true),
            systemClockFont = p.getBoolean(COL_CLOCK_FONT, true),
            revealMs = nearestReveal(p.getInt(COL_REVEAL_MS, DEFAULT_REVEAL_MS)),
            animationsEnabled = p.getBoolean(COL_ANIMATIONS, true),
            arrivalEnabled = p.getBoolean(COL_ARRIVAL, true),
            departureEnabled = p.getBoolean(COL_DEPARTURE, true),
            chargingEnabled = p.getBoolean(COL_CHARGING, true),
            tapAction = p.getString(COL_TAP, "no_action") ?: "no_action",
            doubleTapAction = p.getString(COL_DOUBLE_TAP, "no_action") ?: "no_action",
            longPressAction = p.getString(COL_LONG_PRESS, "no_action") ?: "no_action"
        )
    }

    /** Writes the settings and bumps the revision the module compares against. Returns the new revision. */
    fun write(context: Context, settings: DuoSettings): Long {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = revision(context) + 1
        p.edit()
            .putBoolean(COL_ENABLED, settings.enabled)
            .putBoolean(COL_USE_RIVE, settings.useRive)
            .putBoolean(COL_SHOW_PERCENT, settings.showPercent)
            .putInt(COL_SIZE_PERCENT, settings.sizePercent.coerceIn(MIN_SIZE, MAX_SIZE))
            .putInt(COL_OFFSET_X, settings.offsetX.coerceIn(-MAX_OFFSET, MAX_OFFSET))
            .putBoolean(COL_LIVE_APPLY, settings.liveApply)
            .putBoolean(COL_CLOCK_FONT, settings.systemClockFont)
            .putInt(COL_REVEAL_MS, nearestReveal(settings.revealMs))
            .putBoolean(COL_ANIMATIONS, settings.animationsEnabled)
            .putBoolean(COL_ARRIVAL, settings.arrivalEnabled)
            .putBoolean(COL_DEPARTURE, settings.departureEnabled)
            .putBoolean(COL_CHARGING, settings.chargingEnabled)
            .putString(COL_TAP, settings.tapAction)
            .putString(COL_DOUBLE_TAP, settings.doubleTapAction)
            .putString(COL_LONG_PRESS, settings.longPressAction)
            .putLong(KEY_REVISION, next)
            .apply()
        return next
    }

    fun revision(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_REVISION, 0L)

    /** What the module last reported about itself, for the diagnostics screen. */
    fun status(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUS, "") ?: ""

    fun writeStatus(context: Context, status: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // A cheap rolling history: appended only when it changes, so a burst of identical reports (the
        // element is re-checked on every layout change) does not turn into a wall of the same line.
        val previous = p.getString(KEY_STATUS, "")
        if (previous == status) return
        val history = (statusHistory(context) + "${System.currentTimeMillis()} $status")
            .takeLast(HISTORY_LIMIT)
        p.edit()
            .putString(KEY_STATUS, status)
            .putStringSet(KEY_HISTORY, history.toSet())
            .apply()
    }

    /**
     * The last few module self-reports, oldest first. Kept because a bug report with the *sequence* of what
     * the module thought it was doing is worth far more than the final state.
     */
    fun statusHistory(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_HISTORY, emptySet())
            .orEmpty()
            .sorted()

    /**
     * The debug diagnostic dump the module last sent (build identity, id probes, view tree, readers).
     * Empty in release builds, where the module never sends one.
     */
    fun dump(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DUMP, "") ?: ""

    fun writeDump(context: Context, dump: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DUMP, dump)
            .apply()
    }

    /**
     * When the module last ran inside SystemUI (wall clock ms), or 0 if it never has.
     *
     * Read straight from `Settings.Global`, which a normal app may read without a permission. It is the
     * one fact that separates "LSPosed never injected the module" from "the module is switched off", and
     * the About screen says which one it is instead of showing a blank report.
     */
    fun moduleLoadTime(context: Context): Long = try {
        Settings.Global.getLong(context.contentResolver, "duo_statusbar_last_load", 0L)
    } catch (_: Throwable) {
        0L
    }

    // Same bounds the module clamps to, so the two sides cannot disagree about what a legal value is.
    // Raised to 200 so the element can grow to the status bar's own height (the module caps the drawn
    // side at the window height, so 200 % is the ceiling that is actually reachable).
    /**
     * FR-25: the arrivals the Rive file can play. The file holds one timeline at five speeds, so a value
     * between two of these is snapped to the nearest rather than silently ignored.
     */
    val REVEAL_CHOICES = intArrayOf(500, 750, 1000, 1250, 1500)
    const val DEFAULT_REVEAL_MS = 1000
    const val MIN_REVEAL_MS = 500
    const val MAX_REVEAL_MS = 1500

    /** Snaps a requested arrival to the nearest the file can actually play. */
    fun nearestReveal(ms: Int): Int =
        REVEAL_CHOICES.minByOrNull { kotlin.math.abs(it - ms) } ?: DEFAULT_REVEAL_MS

    const val MIN_SIZE = 60
    const val MAX_SIZE = 200
    const val MAX_OFFSET = 40
}
