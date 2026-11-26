package io.github.kvmy666.duostatusbar.hook

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide one-shot flags for the SystemUI process.
 *
 * LSPosed can call `handleLoadPackage` twice in one System UI process (once for
 * `com.android.systemui`, once for `system`), and every hook must go in exactly once, so this state
 * cannot live on a single [DuoHook] instance.
 */
internal object ProcessState {
    /**
     * Per-process, not per-instance: LSPosed can call `handleLoadPackage` twice in one System UI
     * process (once for `com.android.systemui`, once for `system`), and every hook must go in once.
     */
    val installed = AtomicBoolean(false)

    /** Set by whichever of the Application bootstrap paths runs first, so start runs once. */
    val started = AtomicBoolean(false)

    /** RED STUB: MainHook owns the install claim through this seam. */
    fun claimInstall(): Boolean = true

    /** RED STUB: a failed install releases the claim so a later class-loader load can retry. */
    fun resetInstall() {
    }
}
