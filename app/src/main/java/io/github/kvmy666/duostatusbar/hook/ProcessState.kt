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

    /**
     * Claims the one install for this process. MainHook calls this before it does any work; a second
     * class-loader load loses and returns without installing. A failed install calls [resetInstall]
     * so a later load can retry instead of leaving the process permanently half-installed.
     */
    fun claimInstall(): Boolean = installed.compareAndSet(false, true)

    /** Releases a claim whose install threw, so the next load is allowed to try again. */
    fun resetInstall() {
        installed.set(false)
    }
}
