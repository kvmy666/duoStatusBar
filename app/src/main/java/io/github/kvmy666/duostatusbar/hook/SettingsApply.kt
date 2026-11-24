package io.github.kvmy666.duostatusbar.hook

/**
 * The retry budget for a stage read that came back "off" only because the settings provider could not be
 * reached (C-11 / I-2).
 *
 * The first retry stays at [delaysMs]`[0]` so a healthy boot reads exactly as before; the later gaps grow
 * because the observed recovery on sweet was ~14 s after the first failure, while the old fixed 5 × 1.5 s
 * budget (7.5 s nominal) expired ~1.4 s too early. Kept free of Android types so the schedule is unit-tested.
 */
internal object StageRetry {

    private val delaysMs = longArrayOf(1_500L, 2_000L, 3_000L, 4_000L, 6_000L)

    /** How many times an unreachable read is retried before the loop settles. */
    val retryCount: Int get() = delaysMs.size

    /** Total wait covered by the schedule; must stay >= 15 s (I-2). */
    val budgetMs: Long get() = delaysMs.sum()

    /** The wait before retry [retry] (0-based), or null when the budget is exhausted. */
    fun delayFor(retry: Int): Long? = delaysMs.getOrNull(retry)
}

/**
 * What a settings apply should do, decided without touching Android so it can be unit-tested.
 *
 * The stage alone is not enough: an unreachable provider also reads as [DuoGuard.OFF], and tearing the
 * live element down for that was C-07. A bridge answer is authoritative, though - the app pushed the
 * settings it just wrote even if it cannot expose them over the provider - so only a failed read with no
 * bridge snapshot is "unreachable". A real "off" (the provider or the bridge answered, or the adb
 * `duo_statusbar_stage` override pins 0) must still tear down exactly as before.
 *
 * The two liveness inputs are deliberately separate: [hasElement] means a real Duo element exists and
 * alone feeds the D1 REFRESH/ENABLE branch (a live iOS owner must not make a reachable Duo read look
 * like REFRESH); [iosLive] means a live iOS v2 owner exists and is consulted only for the unreachable
 * keep decision, so an unreachable read cannot release a live iOS bar (C11).
 */
internal enum class SettingsApplyAction { ENABLE, REFRESH, KEEP_UNREACHABLE, TEARDOWN }

internal fun settingsApplyAction(
    stage: Int,
    stageFromAdbOverride: Boolean,
    providerUnreachable: Boolean,
    servedFromBridge: Boolean,
    hasElement: Boolean,
    iosLive: Boolean
): SettingsApplyAction =
    if (stage == DuoGuard.OFF) {
        // C10: the adb kill switch is authoritative, so it is never treated as "unreachable".
        val unreachable = providerUnreachable && !stageFromAdbOverride && !servedFromBridge
        if (unreachable && (hasElement || iosLive)) SettingsApplyAction.KEEP_UNREACHABLE else SettingsApplyAction.TEARDOWN
    } else if (hasElement) {
        SettingsApplyAction.REFRESH
    } else {
        SettingsApplyAction.ENABLE
    }
