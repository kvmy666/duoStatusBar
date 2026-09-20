package io.github.kvmy666.duostatusbar

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * Minimal logger used everywhere in the module.
 *
 * Two sinks on purpose:
 *  - logcat (tag `DuoSB`)  → what we pull with `adb logcat -s DuoSB` while developing (FR-26).
 *  - XposedBridge.log       → what the user can see inside LSPosed's own log when something
 *                             goes wrong on a ROM we do not own.
 *
 * Every method swallows its own errors: logging must never be the reason SystemUI dies (FR-21).
 */
object L {

    const val TAG = "DuoSB"

    fun i(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        try {
            XposedBridge.log("$TAG | $msg")
        } catch (_: Throwable) {
        }
    }

    fun e(where: String, t: Throwable?) {
        i("$where FAILED -> ${t?.javaClass?.name}: ${t?.message}")
    }

    /** Runs [block]; on any Throwable it logs and returns normally (fail-silent, FR-21). */
    inline fun guard(where: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            e(where, t)
        }
    }
}
