package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L

/**
 * The iOS mode gate and its legacy attempt counter.
 *
 * The iOS Rive renderer is gone, so the crash breaker that used to guard it no longer exists. What
 * remains is the read-only attempt counter (`duo_statusbar_ios_rive_attempts`) that the diagnostics
 * still report, and [active] for the iOS-host gate.
 *
 * The kill switch stays single: `duo_statusbar_stage 0` is read by [DuoGuard] before anything else and
 * kills both modes. There is deliberately no iOS stage key.
 */
internal class IosGuard(private val context: Context) {

    /** How many iOS Rive process deaths were recorded by the old breaker (diagnostics only). */
    fun attempts(): Int = try {
        Settings.Global.getInt(context.contentResolver, KEY_ATTEMPTS, 0)
    } catch (t: Throwable) {
        L.w("iOS guard read $KEY_ATTEMPTS: ${t.javaClass.simpleName}: ${t.message}")
        0
    }

    companion object {
        const val KEY_ATTEMPTS = "duo_statusbar_ios_rive_attempts"

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
