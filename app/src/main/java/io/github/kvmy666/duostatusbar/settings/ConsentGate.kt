package io.github.kvmy666.duostatusbar.settings

/** The user's answer to the automatic-report consent dialog. Absent means "not answered yet". */
internal enum class ConsentState { UNANSWERED, YES, NO }

/**
 * Pure consent/countdown rules, so "off before an answer", "never re-ask" and the five-second disabled
 * window are pinned off the phone.
 */
internal object ConsentGate {

    /** Both dialog buttons stay disabled for this many whole seconds. */
    const val COUNTDOWN_SECONDS = 5

    /** The dialog is shown only while the question is unanswered. */
    fun shouldShow(state: ConsentState): Boolean = state == ConsentState.UNANSWERED

    /** Automatic reports may run only after an explicit yes. */
    fun enabled(state: ConsentState): Boolean = state == ConsentState.YES

    /** Whole seconds still to wait after [elapsedSeconds]; never negative. */
    fun remaining(elapsedSeconds: Int): Int =
        (COUNTDOWN_SECONDS - elapsedSeconds).coerceIn(0, COUNTDOWN_SECONDS)

    /** The buttons become usable exactly when the countdown reaches zero. */
    fun buttonsEnabled(elapsedSeconds: Int): Boolean = remaining(elapsedSeconds) == 0
}
