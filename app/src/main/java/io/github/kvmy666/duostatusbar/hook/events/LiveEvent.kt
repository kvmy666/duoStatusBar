package io.github.kvmy666.duostatusbar.hook.events

/**
 * Pure event contract for the status bar's "live" icons (alarm, flashlight, screenshot, ...).
 *
 * No Android, no hooks, no UI: this file only describes what an event is and how its slot is named.
 * Source adapters, presentation and Rive/Canvas rendering build on top of it.
 */

/**
 * A kind of live event. Priority is higher = more important. [defaultDurationMs] is `null` when the
 * event lasts as long as its source is active, or a number of milliseconds for a transient event.
 */
/**
 * `FlashlightControllerImpl.dispatchListeners(int what, boolean value)` multiplexes several events
 * through one callback. Only type 1 is the torch state; 2 is availability, 6 is night-light, and 0 is
 * an error. Returns null for every non-torch callback so the caller leaves the event state alone.
 */
fun flashlightStateFromDispatch(what: Int, value: Boolean): Boolean? =
    if (what == 1) value else null

enum class LiveEventType(val priority: Int, val defaultDurationMs: Long?, val riveId: Int) {
    ALARM(100, 1200L, 1),
    FLASHLIGHT(90, null, 7),
    SCREENSHOT(80, 1000L, 2),
    MIC(70, null, 3),
    LOCATION(60, null, 4),
    VOLUME(20, 1200L, 5),
    UNLOCK(10, 1800L, 6),
    CALL(95, 2500L, 8);

    /** Convenience: whether this event is transient by default. */
    val isTransientByDefault: Boolean get() = defaultDurationMs != null
}

/**
 * One live event instance. [durationMs] is `null` while the source stays active; otherwise the event
 * expires [durationMs] milliseconds after [startedAtMillis].
 */
data class LiveEvent(
    val type: LiveEventType,
    val source: String = "",
    val startedAtMillis: Long,
    val durationMs: Long? = type.defaultDurationMs,
) {
    /** Absolute expiry instant, or `null` when the event is tied to its source. */
    val endsAtMillis: Long? = durationMs?.let { startedAtMillis + it }

    /** Rive id for this event's type; 0 means no event. */
    val riveId: Int get() = type.riveId
}

/**
 * A named slot that an event occupies in the Duo element. The animation belongs to [event] (the event
 * type); [iconKey] only selects which artwork is drawn inside that animation, so an icon pack can
 * provide several variants without duplicating the animation.
 */
data class EventIconSlot(
    val event: LiveEventType,
    val iconKey: String = "default",
)
