package io.github.kvmy666.duostatusbar.hook

/**
 * A hard backoff for a suppress/restore loop (IOS-LAG).
 *
 * The owner's dump showed the same restore fired hundreds of times: a layout pass sees a 0x0 box,
 * restores the stock icons, the next pass hides them again. This guard counts lifecycle events in a
 * fixed [windowMs] window opened by the first event (it does not slide with each event) and trips at
 * [maxEvents], after which [record] keeps returning true without counting until [reset]. The caller
 * stops retrying on its own and waits for a real change (a new attach/root, a settings change or a
 * rotation). Icon re-adds must **not** call [reset]: the ROM repopulates its icon container after
 * every hide pass, so a re-add is part of the loop, not a reason to retry it.
 *
 * Pure and allocation-light: no Android imports, no logging, safe on a layout pass.
 */
internal class ThrashGuard(
    private val windowMs: Long = 10_000L,
    private val maxEvents: Int = 8
) {
    private var windowStartedAt = 0L
    private var events = 0
    private var tripped = false

    /**
     * Records one event at [now] (uptime millis). The first event opens the window; a call at or after
     * [windowMs] starts a fresh window. Returns true once the guard is tripped.
     */
    fun record(now: Long): Boolean {
        if (tripped) return true
        if (events == 0 || now - windowStartedAt >= windowMs) {
            windowStartedAt = now
            events = 0
        }
        events++
        if (events >= maxEvents) {
            tripped = true
            return true
        }
        return false
    }

    fun isTripped(): Boolean = tripped

    fun count(): Int = events

    fun reset() {
        windowStartedAt = 0L
        events = 0
        tripped = false
    }

    fun describe(): String = "$events event(s) in ${windowMs / 1000}s" + if (tripped) " (tripped)" else ""
}
