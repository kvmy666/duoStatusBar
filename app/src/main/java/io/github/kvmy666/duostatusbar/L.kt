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

    /** How many of the most recent lines the module keeps to hand to the app. Bounded, so no leak. */
    private const val MAX_RECENT_LINES = 600

    /** Byte bound for the recent log (2 MB of UTF-8), whichever hits first with the line bound. */
    private const val MAX_RECENT_BYTES = 2 * 1024 * 1024

    private val ringLock = Any()
    private val ring = ArrayDeque<String>(MAX_RECENT_LINES)

    /** Tracked UTF-8 byte size of the joined recent log (lines plus their separators). */
    private var recentBytes = 0

    /**
     * One entry point for every level. The XposedBridge sink carries no level, so nothing is lost by
     * sending `w`/`d`/`v`/`e` through the same path - and keeping them visible is the whole point: a
     * warning that never arrives is worse than no warning, because it looks like silence.
     */
    private fun both(msg: String) {
        record(msg)
        sinks(msg)
    }

    /** Writes to both sinks without keeping the line (used for the bulky diagnostic dump). */
    fun diag(msg: String) = sinks(msg)

    private fun sinks(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        try {
            XposedBridge.log("$TAG | $msg")
        } catch (_: Throwable) {
        }
    }

    /**
     * Keeps the last [MAX_RECENT] lines in memory so the module can push its own log to the app through
     * the settings channel. This is what makes a report self-contained on a device where the app cannot
     * get root: reading LSPosed's log file needs `su`, and on a locked-bootloader root `su` is often not
     * runnable from the app at all ("Cannot run program su: error=2"). The module already has every line;
     * it just hands them over. Never throws, never blocks on a lock for long.
     */
    private fun record(msg: String) {
        try {
            val line = System.currentTimeMillis().toString() + " " + msg
            val lineBytes = line.toByteArray(Charsets.UTF_8).size
            synchronized(ringLock) {
                val wasEmpty = ring.isEmpty()
                ring.addLast(line)
                recentBytes += lineBytes + if (wasEmpty) 0 else 1
                while (ring.isNotEmpty() && (ring.size > MAX_RECENT_LINES || recentBytes > MAX_RECENT_BYTES)) {
                    val removed = ring.removeFirst()
                    val removedBytes = removed.toByteArray(Charsets.UTF_8).size
                    if (ring.isEmpty()) {
                        recentBytes -= removedBytes
                    } else {
                        recentBytes -= removedBytes + 1
                    }
                    if (recentBytes < 0) recentBytes = 0
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** Test-only: clears the recent ring so retention tests start from a known empty state. */
    internal fun clearRecentForTest() {
        try {
            synchronized(ringLock) {
                ring.clear()
                recentBytes = 0
            }
        } catch (_: Throwable) {
        }
    }

    /** The recent module log, oldest first, as one text block. Empty when nothing was logged yet. */
    fun recentText(): String = try {
        synchronized(ringLock) { ring.joinToString("\n") }
    } catch (_: Throwable) {
        ""
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
