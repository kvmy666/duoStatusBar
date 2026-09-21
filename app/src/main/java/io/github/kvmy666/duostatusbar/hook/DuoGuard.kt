package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import android.util.Log

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
 */
internal class DuoGuard(private val context: Context) {

    /** The exact command that turns the full element on; logged so nobody has to guess. */
    val enableHint: String = "adb shell settings put global $KEY_STAGE $RIVE"

    /** 0 = off, 1 = icons only, 2 = icons + Rive. Anything unreadable means off. */
    fun stage(): Int = try {
        val value = Settings.Global.getInt(context.contentResolver, KEY_STAGE, OFF)
        if (value in OFF..RIVE) {
            value
        } else {
            Log.w(TAG, "stage '$value' is not 0..2 - treating as off")
            OFF
        }
    } catch (t: Throwable) {
        Log.w(TAG, "stage unreadable (${t.javaClass.simpleName}) - treating as off")
        OFF
    }

    /**
     * False once Rive has died often enough that we stop trying. Checked before every Rive creation,
     * which is what breaks the loop: the mark is written *before* the crash, so a death count that never
     * gets cleared makes the next boot stop instead of repeating it.
     */
    fun riveAllowed(): Boolean {
        val attempts = attempts()
        if (attempts < MAX_ATTEMPTS) return true
        Log.w(TAG, "Rive refused: $attempts failed attempts recorded - clear with: adb shell settings put global $KEY_ATTEMPTS 0")
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
            Log.w(TAG, "guard write $key=$value: ${t.javaClass.simpleName}: ${t.message}")
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
    }
}
