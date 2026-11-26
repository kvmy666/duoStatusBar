package io.github.kvmy666.duostatusbar.settings

import android.content.Context

/** An unsent, new fingerprint waiting for a ZIP. [count] coalesces repeats detected before sending. */
internal data class PendingProblem(val fingerprint: String, val count: Int = 1)

/**
 * Everything the automatic reporter remembers, in one value so a pulse can be reasoned about and tested
 * without touching SharedPreferences.
 */
internal data class AutoReportState(
    val consent: ConsentState = ConsentState.UNANSWERED,
    val sent: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val pending: List<PendingProblem> = emptyList(),
    val day: Long = 0L,
    val sendsToday: Int = 0,
    val overflow: Int = 0
)

/** Persistence boundary: the real store is a dedicated prefs file; tests use an in-memory one. */
internal interface AutoReportStateStore {
    fun load(): AutoReportState
    fun save(state: AutoReportState)
}

/**
 * Pure state transitions: sighting, dedup, counters, the daily send cap and the bounded queue. Every
 * bound is explicit; overflow is counted, never silently dropped.
 */
internal object AutoReportLedger {

    const val DAY_MS = 24L * 60L * 60L * 1000L
    const val MAX_SENDS_PER_DAY = 3
    const val MAX_QUEUE = 50
    const val MAX_SENT = 200

    fun dayOf(nowMs: Long): Long = Math.floorDiv(nowMs, DAY_MS)

    /** A new day resets only the cap; queued work is never thrown away. */
    fun rollDay(state: AutoReportState, day: Long): AutoReportState =
        if (state.day == day) state else state.copy(day = day, sendsToday = 0)

    fun sight(
        state: AutoReportState,
        problem: DetectedProblem,
        version: String,
        model: String
    ): AutoReportState {
        val fingerprint = ProblemDetector.fingerprint(problem, version, model)
        if (state.sent.contains(fingerprint)) {
            // Already delivered once: only bump the counter carried in the next summary.
            val next = (state.counts[fingerprint] ?: 0) + 1
            val counts = LinkedHashMap(state.counts)
            counts[fingerprint] = next
            var overflow = state.overflow
            while (counts.size > MAX_QUEUE) {
                val oldest = counts.keys.first()
                counts.remove(oldest)
                overflow += 1
            }
            return state.copy(counts = counts, overflow = overflow)
        }

        val existing = state.pending.indexOfFirst { it.fingerprint == fingerprint }
        val pending = if (existing >= 0) {
            state.pending.toMutableList().also {
                it[existing] = it[existing].copy(count = it[existing].count + 1)
            }
        } else {
            state.pending + PendingProblem(fingerprint)
        }
        val bounded = boundedQueue(pending, state.overflow)
        return state.copy(pending = bounded.first, overflow = bounded.second)
    }

    fun canSend(state: AutoReportState, maxPerDay: Int = MAX_SENDS_PER_DAY): Boolean =
        state.sendsToday < maxPerDay

    fun noteAttempt(state: AutoReportState): AutoReportState =
        state.copy(sendsToday = state.sendsToday + 1)

    /** A delivered ZIP contains the new fingerprints plus the known counts and overflow in its caption. */
    fun delivered(state: AutoReportState, fingerprints: List<String>): AutoReportState {
        val sent = (state.sent + fingerprints).distinct().takeLast(MAX_SENT)
        return state.copy(
            sent = sent,
            pending = state.pending.filterNot { it.fingerprint in fingerprints },
            counts = emptyMap(),
            overflow = 0
        )
    }

    /** A delivered summary carries every known count and the overflow marker. */
    fun summaryDelivered(state: AutoReportState): AutoReportState =
        state.copy(counts = emptyMap(), overflow = 0)

    fun queueReporterFailure(
        state: AutoReportState,
        stage: String,
        errorClass: String,
        version: String,
        model: String
    ): AutoReportState = sight(state, ProblemDetector.reporterFailure(stage, errorClass), version, model)

    private fun boundedQueue(
        pending: List<PendingProblem>,
        overflow: Int
    ): Pair<List<PendingProblem>, Int> {
        if (pending.size <= MAX_QUEUE) return pending to overflow
        val dropped = pending.size - MAX_QUEUE
        return pending.drop(dropped) to (overflow + dropped)
    }
}

/** Consent and pulse state in its own file, never in the user's settings store. */
internal object AutoReportStore {

    const val PREFS = "duo_auto_report"
    const val KEY_FIRST_PULSE_DONE = "first_pulse_done"

    private const val KEY_CONSENT = "consent"
    private const val KEY_SENT = "sent"
    private const val KEY_COUNTS = "counts"
    private const val KEY_PENDING = "pending"
    private const val KEY_DAY = "day"
    private const val KEY_SENDS = "sends_today"
    private const val KEY_OVERFLOW = "overflow"

    fun open(context: Context): AutoReportStateStore = PrefsStore(context.applicationContext)

    fun consent(context: Context): ConsentState = open(context).load().consent

    fun writeConsent(context: Context, state: ConsentState) {
        val store = open(context)
        store.save(store.load().copy(consent = state))
    }

    fun firstPulseDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_PULSE_DONE, false)

    fun markFirstPulseDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_FIRST_PULSE_DONE, true).apply()
    }

    /** Consent (or a fresh toggle-on) asks for the one immediate pulse again. */
    fun markFirstPulsePending(context: Context) {
        prefs(context).edit().putBoolean(KEY_FIRST_PULSE_DONE, false).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The real store: one dedicated prefs file, line-encoded so every field is bounded and no value can
     * collide with a separator. Malformed lines are ignored rather than making the reporter fail.
     */
    private class PrefsStore(private val context: Context) : AutoReportStateStore {

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun load(): AutoReportState {
            val p = prefs()
            val consent = when (p.getString(KEY_CONSENT, null)) {
                "yes" -> ConsentState.YES
                "no" -> ConsentState.NO
                else -> ConsentState.UNANSWERED
            }
            return AutoReportState(
                consent = consent,
                sent = p.getString(KEY_SENT, "").orEmpty()
                    .lineSequence().map { it.trim() }.filter { it.isNotBlank() }
                    .toList().takeLast(AutoReportLedger.MAX_SENT),
                counts = decodeCounts(p.getString(KEY_COUNTS, "").orEmpty()),
                pending = decodePending(p.getString(KEY_PENDING, "").orEmpty()),
                day = p.getLong(KEY_DAY, 0L),
                sendsToday = p.getInt(KEY_SENDS, 0).coerceAtLeast(0),
                overflow = p.getInt(KEY_OVERFLOW, 0).coerceAtLeast(0)
            )
        }

        override fun save(state: AutoReportState) {
            val editor = prefs().edit()
                .putString(KEY_SENT, state.sent.joinToString("\n"))
                .putString(
                    KEY_COUNTS,
                    state.counts.entries.joinToString("\n") { "${it.key}\t${it.value}" }
                )
                .putString(
                    KEY_PENDING,
                    state.pending.joinToString("\n") { "${it.fingerprint}\t${it.count}" }
                )
                .putLong(KEY_DAY, state.day)
                .putInt(KEY_SENDS, state.sendsToday)
                .putInt(KEY_OVERFLOW, state.overflow)
            when (state.consent) {
                ConsentState.YES -> editor.putString(KEY_CONSENT, "yes")
                ConsentState.NO -> editor.putString(KEY_CONSENT, "no")
                ConsentState.UNANSWERED -> editor.remove(KEY_CONSENT)
            }
            editor.apply()
        }

        private fun decodeCounts(raw: String): Map<String, Int> {
            val out = LinkedHashMap<String, Int>()
            raw.lineSequence().forEach { line ->
                val at = line.lastIndexOf('\t')
                if (at <= 0) return@forEach
                val count = line.substring(at + 1).toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                out[line.substring(0, at)] = count
                while (out.size > AutoReportLedger.MAX_QUEUE) out.remove(out.keys.first())
            }
            return out
        }

        private fun decodePending(raw: String): List<PendingProblem> {
            val out = ArrayList<PendingProblem>()
            raw.lineSequence().forEach { line ->
                val at = line.lastIndexOf('\t')
                if (at <= 0) return@forEach
                val count = line.substring(at + 1).toIntOrNull()?.takeIf { it > 0 } ?: 1
                out += PendingProblem(line.substring(0, at), count)
                if (out.size > AutoReportLedger.MAX_QUEUE) out.removeAt(0)
            }
            return out
        }
    }
}
