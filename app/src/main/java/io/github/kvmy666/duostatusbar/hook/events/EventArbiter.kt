package io.github.kvmy666.duostatusbar.hook.events

/**
 * Pure arbiter for live events. Owns a single active event, applies newest-wins preemption and
 * coalescing, and expires transient events. No Android, no logging.
 */
class EventArbiter(
    private val isEnabled: (LiveEventType) -> Boolean = { true },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var _active: LiveEvent? = null

    /** The currently active event, or `null` when nothing is live. */
    val active: LiveEvent? get() = _active

    /**
     * Offer a new event. Disabled types are ignored and the current active event is left unchanged.
     * Otherwise newest wins: the incoming event always replaces the current one, including when it is
     * the same type (coalesce / reset timer).
     */
    fun offer(
        type: LiveEventType,
        source: String = "",
        durationMs: Long? = type.defaultDurationMs,
        now: Long = clock(),
    ): LiveEvent? {
        if (!isEnabled(type)) return null
        val event = LiveEvent(
            type = type,
            source = source,
            startedAtMillis = now,
            durationMs = durationMs,
        )
        _active = event
        return _active
    }

    /**
     * Clear the active event. With [type] null, clears unconditionally; otherwise clears only when the
     * active event's type matches.
     */
    fun end(type: LiveEventType? = null) {
        val current = _active
        if (current == null) return
        if (type == null || current.type == type) {
            _active = null
        }
    }

    /** Drop expired transient events and return the (possibly cleared) active event. */
    fun tick(now: Long = clock()): LiveEvent? {
        val current = _active
        if (current != null && current.durationMs != null) {
            val endsAt = current.endsAtMillis
            if (endsAt != null && now >= endsAt) {
                _active = null
            }
        }
        return _active
    }

    /** Clear any active event. */
    fun clear() {
        _active = null
    }
}
