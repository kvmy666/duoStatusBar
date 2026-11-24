package io.github.kvmy666.duostatusbar.ui

/**
 * The restart handshake, kept pure so the rules are testable without a device.
 *
 * The safe-mode loop proved that the old app-side wait was too short and that the root fallback could
 * kill the fresh SystemUI it was supposed to wait for. These three rules close that gap: always wait for
 * a load stamp, coalesce repeated requests, and never kill a SystemUI younger than
 * [MIN_SYSTEMUI_AGE_S].
 */
internal object RestartPolicy {

    /** How long the app waits for the module to publish a new load stamp after a restart request. */
    const val RELOAD_WAIT_MS = 10_000L

    /** Repeated restart taps inside this window are one request; only the first may act. */
    const val RESTART_DEBOUNCE_MS = 10_000L

    /** The root route refuses to kill a SystemUI younger than this; it may still be installing. */
    const val MIN_SYSTEMUI_AGE_S = 15L

    /** Stamp polling granularity; small enough to notice a reload promptly, coarse enough to be cheap. */
    private const val POLL_MS = 250L

    /**
     * True when a request at [now] must be ignored because the previous one was at [lastAt].
     * A zero/negative [lastAt] means there was no previous request. A backwards clock also debounces.
     */
    fun shouldDebounce(now: Long, lastAt: Long): Boolean {
        if (lastAt <= 0L) return false
        return now - lastAt < RESTART_DEBOUNCE_MS
    }

    /** True when an age is known and below the guard; an unknown age (`null`) cannot block the route. */
    fun youngRefused(ageSeconds: Long?): Boolean =
        ageSeconds != null && ageSeconds < MIN_SYSTEMUI_AGE_S

    /**
     * Whether [stamp] is a reload after [before]. When [before] is unknown (0), any positive stamp counts:
     * the app must still wait rather than treating "never loaded" as "already restarted".
     */
    fun reloadedSince(before: Long, stamp: Long): Boolean =
        if (before <= 0L) stamp > 0L else stamp > before

    /**
     * Polls [readStamp] until it shows a reload after [before], or [timeoutMs] elapses.
     *
     * [now]/[sleep]/[readStamp] are injectable so a fake clock can exercise the wait without real time.
     * [sleep] is expected to advance what [now] reports; the loop always checks the stamp first, so a
     * zero timeout still performs one final look.
     */
    fun waitForReload(
        before: Long,
        readStamp: () -> Long,
        now: () -> Long,
        timeoutMs: Long = RELOAD_WAIT_MS,
        sleep: (Long) -> Unit
    ): Boolean {
        val deadline = now() + timeoutMs
        while (true) {
            if (reloadedSince(before, readStamp())) return true
            if (now() >= deadline) return false
            sleep(POLL_MS)
        }
    }
}
