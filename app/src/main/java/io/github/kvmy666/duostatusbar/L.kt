package io.github.kvmy666.duostatusbar

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * Minimal logger used everywhere in the module.
 *
 * Two sinks on purpose:
 *  - logcat (tag `DuoSB`)  → works on stock ROMs, `adb logcat -s DuoSB` while developing (FR-26).
 *  - XposedBridge.log       → the sink that always works: LSPosed routes it to
 *                             `/data/adb/lspd/log/modules_<boot>.log` (tag `LSPosedFramework`).
 *
 * **Use this, never `android.util.Log` directly.** Measured on the target device (OnePlus 15 /
 * OxygenOS 16): `android.util.Log` calls made from inside SystemUI do not reach logcat at all - the ROM
 * runs a filtered logd, and the tag is nowhere in the buffer even though the code ran. During Phase 3
 * verification that made a *working* module look completely dead: every stage "failed" because the only
 * sink the checks could read was empty. `tools/route-module-logs.py` keeps the sources on this path.
 *
 * The XposedBridge sink is wrapped because it only exists inside a hooked process - in the app itself it
 * throws and logging is logcat-only. Every method swallows its own errors: logging must never be the
 * reason SystemUI dies (FR-21).
 */
object L {

    const val TAG = "DuoSB"

    /**
     * One entry point for every level. The XposedBridge sink carries no level, so nothing is lost by
     * sending `w`/`d`/`v`/`e` through the same path - and keeping them visible is the whole point: a
     * warning that never arrives is worse than no warning, because it looks like silence.
     */
    private fun both(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        try {
            XposedBridge.log("$TAG | $msg")
        } catch (_: Throwable) {
        }
    }

    fun i(msg: String) = both(msg)

    fun w(msg: String) = both(msg)

    fun d(msg: String) = both(msg)

    fun v(msg: String) = both(msg)

    fun e(msg: String) = both(msg)

    fun e(where: String, t: Throwable?) {
        both("$where FAILED -> ${t?.javaClass?.name}: ${t?.message}")
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
