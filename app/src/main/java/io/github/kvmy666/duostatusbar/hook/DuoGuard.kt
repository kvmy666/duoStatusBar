package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * The safety valve for a failure that `try`/`catch` cannot reach.
 *
 * Everything in this module is guarded, but the first Phase 3 attempt proved the limit of that: Rive's
 * native renderer died with a SIGSEGV, and a native signal tears the process down before any Kotlin
 * `catch` runs. SystemUI then restarted, ran the same code, and looped. No in-process handler can fix
 * that, so the protection lives outside the process — in two `Settings.Global` entries, which is the
 * store that actually works here:
 *
 *     duo_statusbar_stage           0 = off (default), 1 = icons only, 2 = icons + Rive
 *     duo_statusbar_rive_attempts   written *before* Rive is created, cleared after it survives
 *
 * Control, no root and no app needed:
 *
 *     adb shell settings put global duo_statusbar_stage 1   # icons only  (no native code at all)
 *     adb shell settings put global duo_statusbar_stage 2   # icons + Rive
 *     adb shell settings put global duo_statusbar_stage 0   # off
 *     adb shell settings put global duo_statusbar_rive_attempts 0   # clear the crash breaker
 *
 * Why not a file: the first version used files under SystemUI's own data dir, and the device refused it
 * from every direction — the module (running as SystemUI's uid) may write there, but `su -c` cannot, so
 * *I* could not enable or disable the module from a shell; root is `u:r:ksu:s0` with SELinux enforcing
 * and `ls /data/user_de/0/com.android.systemui` is denied outright. A gate nobody can operate is not a
 * safety valve. Settings have neither problem: adb writes them with no permission, `Settings.Global`
 * reads are unrestricted, and SystemUI already holds `WRITE_SECURE_SETTINGS` (verified `granted=true` on
 * the device), so the module can write the counter back.
 *
 * Off is the default, so a freshly installed build does nothing at all until `stage` says otherwise.
 * And if Rive dies twice the counter says so and stage 2 is refused — one boot is enough to stop a loop.
 *
 * The stage can also come from the user's settings in the app (over the provider, because a normal app
 * cannot write global settings). Precedence is in [stage]: an explicit `duo_statusbar_stage` wins, then the
 * app's settings, then off — so adb always has the last word and the app cannot quietly re-enable something
 * that was switched off for diagnosis.
 */
internal class DuoGuard(private val context: Context) {

    /** The exact command that turns the full element on; logged so nobody has to guess. */
    val enableHint: String = "adb shell settings put global $KEY_STAGE $RIVE"

    /**
     * The stage that is actually in force, resolved in this order:
     *
     *   1. `Settings.Global` `duo_statusbar_stage` **when present** (0/1/2). This is the developer override
     *      and the kill switch: `adb shell settings put global duo_statusbar_stage 0` must always be able to
     *      switch the module off no matter what the app's settings say.
     *   2. otherwise the user's settings from the app (`enabled` + `useRive`).
     *   3. otherwise off.
     *
     * The app cannot write global settings (no `WRITE_SECURE_SETTINGS` for a normal app), which is why the
     * user path arrives over the provider instead — see `settings/DuoPrefs.kt`.
     */
    fun stage(): Int {
        globalStage()?.let { override ->
            L.i("stage $override (adb override)")
            return override
        }
        val app = DuoSettingsClient.read(context)
        val resolved = when {
            !app.enabled -> OFF
            app.engineMode == DuoPrefs.ENGINE_CANVAS || !app.useRive -> ICONS_ONLY
            else -> RIVE
        }
        L.i("stage $resolved (app settings rev ${app.revision}, enabled=${app.enabled}, engine=${app.engineMode})")
        return resolved
    }

    /**
     * The adb/developer override in force (0/1/2), or null when absent. Exposed for the diagnostic dump so
     * a report can tell "the module is off" from "an override is pinning it off" without adb access.
     */
    fun override(): Int? = globalStage()

    /** The adb/developer override, or null when it has not been set (absent means "ask the app"). */
    private fun globalStage(): Int? = try {
        Settings.Global.getInt(context.contentResolver, KEY_STAGE, ABSENT).let { value ->
            if (value == ABSENT) null else if (value in OFF..RIVE) value else null
        }
    } catch (t: Throwable) {
        L.w("stage override unreadable (${t.javaClass.simpleName}) - using app settings")
        null
    }

    /**
     * False once Rive has died often enough that we stop trying. Checked before every Rive creation,
     * which is what breaks the loop: the mark is written *before* the crash, so a death count that never
     * gets cleared makes the next boot stop instead of repeating it.
     */
    fun riveAllowed(): Boolean {
        // Once this process has already created a Rive view and is still alive to ask again, the risk
        // has been taken: a native fault would have killed the process with it. The count is per process
        // *by design* ([noteRiveAttempt]), so the later bars must not be blocked by the first bar's own
        // attempt. They were: the main bar raised the counter to MAX, and the keyguard bar and the shade
        // header then fell back to Canvas in the same process (measured on OxygenOS 16), so the lock
        // screen drew a different element than the home bar with no visible reason.
        val attempted = notedThisProcess.get()
        val attempts = attempts()
        val age = lastAttemptAgeMs()
        if (shouldAllowRive(attempts, attempted, age)) {
            // A full count that allowed through is a *stale* count, not a live crash loop, so clear it:
            // the breaker is a crash-loop guard, not a life sentence (the old code blocked forever).
            if (!attempted && attempts >= MAX_ATTEMPTS) {
                L.i("Rive attempts stale (${age / 1000}s old) - clearing the breaker and trying Rive again")
                clearRiveAttempts()
            }
            return true
        }
        L.w("Rive refused: $attempts failed attempts recorded - clear with: adb shell settings put global $KEY_ATTEMPTS 0")
        return false
    }

    /** How many Rive process deaths are recorded (the crash breaker's counter). */
    fun attempts(): Int = try {
        Settings.Global.getInt(context.contentResolver, KEY_ATTEMPTS, 0)
    } catch (_: Throwable) {
        0
    }

    /** How long ago the last attempt was recorded; [Long.MAX_VALUE] when there is no timestamp. */
    private fun lastAttemptAgeMs(): Long = try {
        val at = Settings.Global.getLong(context.contentResolver, KEY_ATTEMPT_AT, 0L)
        if (at <= 0L) Long.MAX_VALUE else System.currentTimeMillis() - at
    } catch (_: Throwable) {
        Long.MAX_VALUE
    }

    /**
     * Call immediately before handing the .riv to Rive: a crash after this point is counted.
     *
     * Counted once per process, not once per view. There are three status bars now (main, keyguard,
     * shade header) and each gets an element, so counting creations tripped the breaker at two and
     * dropped every bar to Canvas. A crash takes the whole process with it, so the risk has been taken
     * the moment the first view is created - the later ones add nothing to count.
     */
    fun noteRiveAttempt() {
        if (!notedThisProcess.compareAndSet(false, true)) return
        put(KEY_ATTEMPTS, attempts() + 1)
        putLong(KEY_ATTEMPT_AT, System.currentTimeMillis())
    }

    /** Call once the drawing has survived its first seconds: the counter only ever counts deaths. */
    fun clearRiveAttempts() {
        put(KEY_ATTEMPTS, 0)
        putLong(KEY_ATTEMPT_AT, 0L)
    }

    /** Set once per process; see [noteRiveAttempt]. */
    private val notedThisProcess = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Records that the module's code actually ran inside SystemUI, with the wall-clock time.
     *
     * Written as early as possible — before the stage gate — because the app's diagnostics can otherwise
     * not tell the two failure modes apart: "LSPosed never injected the module" and "the module ran but is
     * switched off". A normal app can read `Settings.Global`, so the settings screen can say which one it
     * is instead of showing a blank report.
     */
    fun noteLoaded() {
        putLong(KEY_LAST_LOAD, System.currentTimeMillis())
        noteHeartbeat()
    }

    /**
     * Updates the "the module is running right now" stamp. [noteLoaded] writes it once at load; the state
     * monitor keeps it fresh while the process lives. The app reads it to tell "the module is gone" (just
     * unscoped and System UI restarted) from "the module is here and healthy" — without it, a report taken
     * after unscoping shipped the *previous* run's dump and status and looked like it had loaded fine.
     */
    fun noteHeartbeat() {
        putLong(KEY_HEARTBEAT, System.currentTimeMillis())
    }

    private fun put(key: String, value: Int) {
        try {
            Settings.Global.putInt(context.contentResolver, key, value)
        } catch (t: Throwable) {
            // Losing the counter costs the breaker, not the status bar - so never propagate.
            L.w("guard write $key=$value: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun putLong(key: String, value: Long) {
        try {
            Settings.Global.putLong(context.contentResolver, key, value)
        } catch (t: Throwable) {
            L.w("guard write $key=$value: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "DuoSB"
        const val OFF = 0
        const val ICONS_ONLY = 1
        const val RIVE = 2
        const val KEY_STAGE = "duo_statusbar_stage"
        const val KEY_ATTEMPTS = "duo_statusbar_rive_attempts"

        /** Wall-clock ms of the last Rive attempt, so the breaker can recognise a stale count. */
        const val KEY_ATTEMPT_AT = "duo_statusbar_rive_attempt_at"

        /** Wall-clock ms of the last time the module ran inside SystemUI; 0 means it never has. */
        const val KEY_LAST_LOAD = "duo_statusbar_last_load"

        /** Wall-clock ms the module last proved it is alive; refreshed by the state monitor. */
        const val KEY_HEARTBEAT = "duo_statusbar_heartbeat"
        /** How many *processes* may die to Rive before it is given up on. */
        private const val MAX_ATTEMPTS = 2

        /**
         * How long a full attempt count stays armed. A crash loop retries inside this window and stays
         * blocked; a device that has been healthy longer than this re-arms Rive on its next start.
         */
        private const val CRASH_WINDOW_MS = 120_000L

        /** Sentinel for "the user never set an override": `getInt` cannot return null, so it needs one. */
        private const val ABSENT = -1

        /**
         * The pure decision behind [riveAllowed], so the boundary is unit-tested off the phone: Rive is
         * allowed when this process has already taken the risk, or the counter has room, or the count is
         * old enough to be stale rather than a live crash loop.
         */
        fun shouldAllowRive(attempts: Int, attemptedThisProcess: Boolean, attemptAgeMs: Long): Boolean {
            if (attemptedThisProcess) return true
            if (attempts < MAX_ATTEMPTS) return true
            return attemptAgeMs > CRASH_WINDOW_MS
        }
    }
}
