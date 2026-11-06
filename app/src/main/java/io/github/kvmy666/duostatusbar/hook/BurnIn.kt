package io.github.kvmy666.duostatusbar.hook

/** One wall-clock hour. The burn-in seat alternates on each boundary, with no timer of its own. */
internal const val BURN_IN_HOUR_MS = 3_600_000L

/**
 * How far the main element moves for burn-in protection, in physical pixels.
 *
 * On odd wall-clock hours the element sits one pixel left of its normal seat; on even hours it is
 * where it always was. Parity is read from the wall clock itself, so no alarm, wake lock or render
 * loop is needed: the existing minute tick only has to redraw when the parity changes.
 *
 * OxygenOS may already move status-bar items itself on some builds. If the ring visibly moves twice
 * as far there, turn the toggle off; that interaction is not measured here and stays unverified.
 */
internal fun burnInShiftPx(enabled: Boolean, epochHour: Long): Float =
    if (enabled && (epochHour and 1L) == 1L) -1f else 0f
