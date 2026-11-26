package io.github.kvmy666.duostatusbar.hook

/**
 * The iOS mode gate.
 *
 * The iOS Rive renderer is gone, so the crash breaker that used to guard it no longer exists. The
 * legacy attempt global is no longer read; the dump's `iosAttempts=0` / `guardAttempts=0` tokens stay
 * frozen for compatibility and are always zero.
 *
 * The kill switch stays single: `duo_statusbar_stage 0` is read by [DuoGuard] before anything else and
 * kills both modes. There is deliberately no iOS stage key.
 */
internal object IosGuard {

    /**
     * Whether the iOS skin should be active at all. An adb override always wins: override 2 means
     * Duo draws and iOS is suppressed, because [DuoGuard.stage] returns the override before the
     * iOS-mode branch. W3 calls this before starting the iOS host.
     *
     * The drawing-ready parameter is the capability seam: a build without the engine refuses iOS
     * mode even when the saved setting says otherwise, and the tests can exercise both sides. This
     * build ships the real v2 engine.
     */
    fun active(
        iosMode: Boolean,
        adbOverride: Int?,
        drawingReady: Boolean = true
    ): Boolean = drawingReady && adbOverride == null && iosMode
}
