package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L

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
            app.useRive -> RIVE
            else -> ICONS_ONLY
        }
        L.i("stage $resolved (app settings rev ${app.revision}, enabled=${app.enabled}, rive=${app.useRive})")
        return resolved
    }

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
        val attempts = attempts()
        if (attempts < MAX_ATTEMPTS) return true
        L.w("Rive refused: $attempts failed attempts recorded - clear with: adb shell settings put global $KEY_ATTEMPTS 0")
        return false
    }

    fun attempts(): Int = try {
        Settings.Global.getInt(context.contentResolver, KEY_ATTEMPTS, 0)
    } catch (_: Throwable) {
        0
    }

    /** Call immediately before handing the .riv to Rive: a crash after this point is counted. */
    fun noteRiveAttempt() {
        put(KEY_ATTEMPTS, attempts() + 1)
    }

    /** Call once the drawing has survived its first seconds: the counter only ever counts deaths. */
    fun clearRiveAttempts() {
        put(KEY_ATTEMPTS, 0)
    }

    private fun put(key: String, value: Int) {
        try {
            Settings.Global.putInt(context.contentResolver, key, value)
        } catch (t: Throwable) {
            // Losing the counter costs the breaker, not the status bar - so never propagate.
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
        private const val MAX_ATTEMPTS = 2

        /** Sentinel for "the user never set an override": `getInt` cannot return null, so it needs one. */
        private const val ABSENT = -1
    }
}
