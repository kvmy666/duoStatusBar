package io.github.kvmy666.duostatusbar.hook.integration

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The hand-off to Auto Expand (FR-05/18): **this module draws, that module acts.**
 *
 * The contract is copied from the sibling module's own code (`ActionDispatcher.kt`), not invented:
 *
 *     action   io.github.kvmy666.autoexpand.ZONE_PRIVILEGED_ACTION
 *     extra    zone_action_key = a ZoneAction key, e.g. "toggle_flashlight"
 *     target   setPackage("com.android.systemui") — its privileged receiver lives inside System UI
 *
 * The last line is what makes this work so cleanly: Auto Expand's receiver *is* in this process (System UI),
 * so a request is a same-process broadcast, and it is delivered with the identical shape the module's own
 * dispatcher uses (`ActionDispatcher.sendPrivileged`). Nothing about its internals is duplicated here — the
 * keys are its vocabulary, the handling is its code.
 *
 * If Auto Expand is not installed, nothing needs to happen: no receiver, no action, no error. And the keys
 * default to `no_action`, so with stock settings the element consumes **no touches at all** and cannot
 * interfere with Auto Expand's own gesture zones (FR-18).
 */
internal object AutoExpand {

    const val PACKAGE = "io.github.kvmy666.autoexpand"
    const val ACTION_PRIVILEGED = "io.github.kvmy666.autoexpand.ZONE_PRIVILEGED_ACTION"
    const val EXTRA_ACTION_KEY = "zone_action_key"

    /** Auto Expand's own key for "do nothing" (`ZoneAction.NoAction.toKey()`). */
    const val NO_ACTION = "no_action"

    private const val SYSTEMUI = "com.android.systemui"
    private const val TAG = "DuoSB"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(PACKAGE, 0)
        true
    } catch (_: Throwable) {
        false
    }

    /** Asks Auto Expand to run [key]. Returns false when there is nothing to ask for. */
    fun request(context: Context, key: String): Boolean {
        if (key.isBlank() || key == NO_ACTION) return false
        return try {
            context.sendBroadcast(
                Intent(ACTION_PRIVILEGED)
                    .setPackage(SYSTEMUI)
                    .putExtra(EXTRA_ACTION_KEY, key)
            )
            Log.i(TAG, "asked Auto Expand for '$key'")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Auto Expand request '$key' failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }
}
