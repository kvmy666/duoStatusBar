package io.github.kvmy666.duostatusbar.settings

import android.content.Context

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

    const val COL_ENABLED = "enabled"
    const val COL_USE_RIVE = "use_rive"
    const val COL_SHOW_PERCENT = "show_percent"
    const val COL_SIZE_PERCENT = "size_percent"
    const val COL_OFFSET_X = "offset_x"
    const val COL_REVISION = "revision"
    const val COL_TAP = "tap_action"
    const val COL_DOUBLE_TAP = "double_tap_action"
    const val COL_LONG_PRESS = "long_press_action"

    /** The column set the module expects; kept in one place so both sides cannot drift. */
    val COLUMNS = arrayOf(
        COL_ENABLED, COL_USE_RIVE, COL_SHOW_PERCENT, COL_SIZE_PERCENT, COL_OFFSET_X, COL_REVISION,
        COL_TAP, COL_DOUBLE_TAP, COL_LONG_PRESS
    )

    private const val PREFS = "duo_settings"
    private const val KEY_REVISION = "revision"
    private const val KEY_STATUS = "last_status"
    private const val KEY_HISTORY = "status_history"
    private const val HISTORY_LIMIT = 20

    fun read(context: Context): DuoSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DuoSettings(
            enabled = p.getBoolean(COL_ENABLED, false),
            useRive = p.getBoolean(COL_USE_RIVE, true),
            showPercent = p.getBoolean(COL_SHOW_PERCENT, true),
            sizePercent = p.getInt(COL_SIZE_PERCENT, 100),
            offsetX = p.getInt(COL_OFFSET_X, 0),
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

    // Same bounds the module clamps to, so the two sides cannot disagree about what a legal value is.
    const val MIN_SIZE = 60
    const val MAX_SIZE = 140
    const val MAX_OFFSET = 40
}
