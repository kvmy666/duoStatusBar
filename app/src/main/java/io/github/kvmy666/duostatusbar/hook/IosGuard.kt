package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L

/**
 * The crash breaker for the iOS skin's Rive view, kept separate from [DuoGuard].
 *
 * Duo and iOS are two independent Rive consumers. Sharing the attempt counter would couple them: an
 * iOS native death counted twice would block Duo on the next boot, and the other way around. So this
 * class owns its own key pair:
 *
 *     duo_statusbar_ios_rive_attempts   written *before* the iOS Rive view is created
 *     duo_statusbar_ios_rive_attempt_at wall-clock ms of that attempt, for stale-count detection
 *
 * The semantics mirror [DuoGuard] exactly (2 attempts, a 120 s crash window, once per instance, stale
 * counts re-armed and cleared). The pure policy is reused from [DuoGuard.shouldAllowRive] so the two
 * breakers cannot drift apart, but the stored counters are never shared.
 *
 * The kill switch stays single: `duo_statusbar_stage 0` is read by [DuoGuard] before anything else and
 * kills both modes. There is deliberately no iOS stage key.
 */
internal class IosGuard(private val context: Context) {

    /**
     * False once the iOS Rive view has died often enough that we stop trying. Checked before every
     * iOS Rive creation; the mark is written *before* the crash, so a death count that never gets
     * cleared makes the next boot stop instead of repeating it.
     */
    fun riveAllowed(): Boolean {
        // Once this instance has already created an iOS Rive view and is still alive to ask again, the
        // risk has been taken: a native fault would have killed the process with it. The later bars
        // must not be blocked by the first bar's own attempt (M14).
        val attempted = notedThisProcess.get()
        val attempts = attempts()
        val age = lastAttemptAgeMs()
        if (DuoGuard.shouldAllowRive(attempts, attempted, age)) {
            // A full count that allowed through is a *stale* count, not a live crash loop, so clear it:
            // the breaker is a crash-loop guard, not a life sentence.
            if (!attempted && attempts >= MAX_ATTEMPTS) {
                L.i("iOS Rive attempts stale (${age / 1000}s old) - clearing the iOS breaker and trying Rive again")
                clearRiveAttempts()
            }
            return true
        }
        L.w("iOS Rive refused: $attempts failed attempts recorded - clear with: adb shell settings put global $KEY_ATTEMPTS 0")
        return false
    }

    /** How many iOS Rive process deaths are recorded (the crash breaker's counter). */
    fun attempts(): Int = try {
        Settings.Global.getInt(context.contentResolver, KEY_ATTEMPTS, 0)
    } catch (t: Throwable) {
        L.w("iOS guard read $KEY_ATTEMPTS: ${t.javaClass.simpleName}: ${t.message}")
        0
    }

    /** How long ago the last iOS attempt was recorded; [Long.MAX_VALUE] when there is no timestamp. */
    private fun lastAttemptAgeMs(): Long = try {
        val at = Settings.Global.getLong(context.contentResolver, KEY_ATTEMPT_AT, 0L)
        if (at <= 0L) Long.MAX_VALUE else System.currentTimeMillis() - at
    } catch (t: Throwable) {
        L.w("iOS guard read $KEY_ATTEMPT_AT: ${t.javaClass.simpleName}: ${t.message}")
        Long.MAX_VALUE
    }

    /**
     * Call immediately before handing `ios.riv` to Rive: a crash after this point is counted.
     *
     * Counted once per instance, not once per view, for the same reason as [DuoGuard.noteRiveAttempt]:
     * every status bar surface gets its own view, and a crash takes the whole process with it.
     */
    fun noteRiveAttempt() {
        if (!notedThisProcess.compareAndSet(false, true)) return
        put(KEY_ATTEMPTS, attempts() + 1)
        putLong(KEY_ATTEMPT_AT, System.currentTimeMillis())
    }

    /** Call once the iOS drawing has survived its first seconds: the counter only ever counts deaths. */
    fun clearRiveAttempts() {
        put(KEY_ATTEMPTS, 0)
        putLong(KEY_ATTEMPT_AT, 0L)
    }

    /** Set once per instance; see [noteRiveAttempt]. */
    private val notedThisProcess = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun put(key: String, value: Int) {
        try {
            Settings.Global.putInt(context.contentResolver, key, value)
        } catch (t: Throwable) {
            // Losing the counter costs the breaker, not the status bar - so never propagate.
            L.w("iOS guard write $key=$value: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun putLong(key: String, value: Long) {
        try {
            Settings.Global.putLong(context.contentResolver, key, value)
        } catch (t: Throwable) {
            L.w("iOS guard write $key=$value: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        const val KEY_ATTEMPTS = "duo_statusbar_ios_rive_attempts"

        /** Wall-clock ms of the last iOS Rive attempt, so the breaker can recognise a stale count. */
        const val KEY_ATTEMPT_AT = "duo_statusbar_ios_rive_attempt_at"

        /** How many *processes* may die to the iOS Rive view before it is given up on. */
        private const val MAX_ATTEMPTS = 2

        /**
         * How long a full attempt count stays armed. A crash loop retries inside this window and stays
         * blocked; a device that has been healthy longer than this re-arms Rive on its next start.
         */
        private const val CRASH_WINDOW_MS = 120_000L

        /**
         * Whether the iOS skin should be active at all. An adb override always wins: override 2 means
         * Duo draws and iOS is suppressed, because [DuoGuard.stage] returns the override before the
         * iOS-mode branch. W3 calls this before starting the iOS host.
         *
         * The drawing-ready parameter is the capability seam: a build without the scene refuses iOS
         * mode even when the saved setting says otherwise, and the tests can exercise both sides
         * without waiting for a real Rive runtime. This build ships the scene and both renderers.
         */
        fun active(
            iosMode: Boolean,
            adbOverride: Int?,
            drawingReady: Boolean = true
        ): Boolean = drawingReady && adbOverride == null && iosMode
    }
}
