package io.github.kvmy666.duostatusbar.hook

import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference

/**
 * The anti-thrash state of one iOS surface, shared by every host instance that serves it.
 *
 * The shade loop reported in IOS-R2 created a fresh [IosReplaceHost] per layout pass, and an
 * instance-owned [ThrashGuard] was therefore reset before it could ever trip. This registry keys the
 * two guards and the trip state by the surface name instead: a new host object on the same surface
 * inherits the counters and the trip exactly as the live owner would have.
 *
 * A trip is cleared only by [reset], which the hosts call for a real change (a settings change or a
 * genuinely different window root). Icon churn never resets it.
 */
internal object IosSurfaceThrash {

    /** How close two lifecycle events must be to count as churn rather than a normal open/close. */
    private const val RAPID_MS = 1_500L

    /** How long a trip keeps blocking the same window before one probe is allowed through. */
    private const val BACKOFF_MS = 10_000L

    private class State {
        val lifecycle = ThrashGuard()
        val reassert = ThrashGuard()
        var tripped = false
        var root: WeakReference<View>? = null
        var trippedAt: Long = 0L

        /** When this surface was last attached, or null before the first attach/reset. */
        var lastAttachAt: Long? = null

        /** When the current attachment started, or null while detached. */
        var attachedAt: Long? = null
    }

    private val states = HashMap<String, State>()

    /** The attach/teardown guard for [surface], created on first use. */
    @Synchronized
    fun lifecycle(surface: String): ThrashGuard = state(surface).lifecycle

    /** The drawable re-assert guard for [surface], created on first use. */
    @Synchronized
    fun reassert(surface: String): ThrashGuard = state(surface).reassert

    /**
     * Records one attach/teardown timestamp and returns true only when that event is *rapid*:
     * an attach whose previous attach was under [RAPID_MS] ago, or a teardown of an attachment that
     * lived under [RAPID_MS].
     *
     * A normal shade open/close is spaced by more than this, so its events are not counted as churn.
     * The per-surface timestamps are updated on every event, even when the event is not rapid, and
     * [reset] clears both.
     */
    @Synchronized
    fun lifecycleEvent(surface: String, attach: Boolean, now: Long): Boolean {
        val state = state(surface)
        return if (attach) {
            val previous = state.lastAttachAt
            val rapid = previous != null && now - previous < RAPID_MS
            state.lastAttachAt = now
            state.attachedAt = now
            rapid
        } else {
            val since = state.attachedAt
            val rapid = since != null && now - since < RAPID_MS
            state.attachedAt = null
            rapid
        }
    }

    @Synchronized
    fun isTripped(surface: String): Boolean = states[surface]?.tripped == true

    /** Records a trip, the window root it belongs to and when it happened. */
    @Synchronized
    fun trip(surface: String, root: View?, now: Long = SystemClock.uptimeMillis()) {
        val state = state(surface)
        state.tripped = true
        state.root = root?.let { WeakReference(it) }
        state.trippedAt = now
    }

    /** The window root of the last trip, or null when unknown/collected. */
    @Synchronized
    fun rootOf(surface: String): View? = states[surface]?.root?.get()

    /**
     * Whether [surface] must still back off for [root].
     *
     * A trip is scoped to the window it happened in and expires after [cooldownMs]: a collected trip
     * root, a different window, or an elapsed cooldown resets the state and allows one probe, so a
     * transient churn cannot latch the surface off for the rest of the process.
     */
    @Synchronized
    fun shouldBlock(
        surface: String,
        root: View?,
        now: Long,
        cooldownMs: Long = BACKOFF_MS
    ): Boolean {
        val state = states[surface] ?: return false
        if (!state.tripped) return false
        val tripRoot = state.root?.get()
        if (tripRoot == null) {
            // The window we tripped on is gone (or collected): allow one probe on the current one.
            resetLocked(state)
            return false
        }
        if (root?.rootView === tripRoot && now - state.trippedAt < cooldownMs) return true
        resetLocked(state)
        return false
    }

    /** Clears both guards and the trip for a real change. */
    @Synchronized
    fun reset(surface: String) {
        val state = states[surface] ?: return
        resetLocked(state)
    }

    private fun resetLocked(state: State) {
        state.lifecycle.reset()
        state.reassert.reset()
        state.tripped = false
        state.root = null
        state.trippedAt = 0L
        state.lastAttachAt = null
        state.attachedAt = null
    }

    @Synchronized
    fun clearForTest() {
        states.clear()
    }

    private fun state(surface: String): State =
        states.getOrPut(surface) { State() }
}
