package io.github.kvmy666.duostatusbar.hook

import java.util.concurrent.ConcurrentHashMap

/**
 * A one-shot log gate.
 *
 * Several facts are worth logging exactly once per process - the ROM adapter, the first icon hidden,
 * the first hide pass - because a repeated layout pass would otherwise turn the log into a wall of the
 * same line. This replaces the scatter of `HashSet`/`AtomicBoolean` guards with one small, thread-safe
 * gate: the first time a [key] is used the block runs, and every time after it is a no-op.
 */
internal class LogOnce {

    private val seen = ConcurrentHashMap.newKeySet<String>()

    /** Runs [block] the first time [key] is used; returns true if it ran, false if it was a repeat. */
    fun once(key: String, block: () -> Unit): Boolean {
        if (!seen.add(key)) return false
        block()
        return true
    }
}
