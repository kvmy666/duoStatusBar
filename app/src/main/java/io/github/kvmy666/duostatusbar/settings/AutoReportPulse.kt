package io.github.kvmy666.duostatusbar.settings

import java.io.File

/** What a pulse found and what it did. The log line is the measured cost of one pulse. */
internal enum class PulseAction { SKIPPED_CONSENT, NOTHING, ZIP_SENT, SUMMARY_SENT, CAPPED, FAILED }

internal data class PulseResult(
    val action: PulseAction,
    val problems: Int = 0,
    val sends: Int = 0,
    val zipBytes: Long = 0L,
    val elapsedMs: Long = 0L,
    val pending: Int = 0,
    /** How many capture sections the pulse read; measured by the cost test. */
    val sections: Int = 0,
    val detail: String = ""
) {
    /** The one line the worker logs, so a device run can report the real pulse cost. */
    fun logLine(): String = "auto report pulse: ms=$elapsedMs bytes=$zipBytes problems=$problems sends=$sends"
}

/** Version/device identity used for fingerprints and the report caption. No serial, IMEI or account. */
internal data class AutoReportIdentity(
    val version: String,
    val manufacturer: String,
    val model: String,
    val rom: String
)

/** The evidence one collection produced. [problems] already went through [ProblemDetector]. */
internal data class AutoReportCollected(
    val report: String,
    val logs: String,
    val status: String,
    val dump: String,
    val sections: Int,
    val problems: List<DetectedProblem>,
    /** When the dump was written, so the ZIP labels a stale one with its time. */
    val dumpAt: Long = 0L,
    /** False when the snapshot is a dead run's leftovers; the ZIP must not embed them. */
    val evidenceLive: Boolean = true
)

internal data class ZipArtifact(val file: File?, val bytes: Long)

internal sealed interface SendOutcome {
    object Sent : SendOutcome
    data class Failed(val errorClass: String) : SendOutcome
}

internal interface AutoReportSender {
    fun sendZip(artifact: ZipArtifact, caption: String): SendOutcome
    fun sendSummary(text: String, caption: String): SendOutcome
}

/**
 * Builds the caption/summary with only version, device model, ROM id, fingerprints and counts. A
 * fingerprint is `type|location|version|model`; no serial, IMEI, SSID, carrier, phone or account can
 * appear here.
 */
internal object AutoReportCaption {

    private const val MAX_ITEMS = 10

    fun zipCaption(
        identity: AutoReportIdentity,
        newFingerprints: List<String>,
        counts: Map<String, Int>,
        overflow: Int
    ): String = buildString {
        append(identityText(identity))
        if (newFingerprints.isNotEmpty()) {
            append("\nnew: ").append(bounded(newFingerprints))
        }
        if (counts.isNotEmpty()) {
            append("\nknown: ").append(bounded(counts.entries.map { "${it.key}=${it.value}" }))
        }
        if (overflow > 0) append("\nqueue_overflow=").append(overflow)
    }

    fun summary(identity: AutoReportIdentity, counts: Map<String, Int>, overflow: Int): String = buildString {
        appendLine("Duo Status Bar automatic problem summary")
        appendLine(identityText(identity))
        if (counts.isNotEmpty()) {
            appendLine("known counts:")
            counts.entries.take(MAX_ITEMS).forEach { appendLine("  ${it.key}=${it.value}") }
            if (counts.size > MAX_ITEMS) appendLine("  (+${counts.size - MAX_ITEMS} more)")
        }
        if (overflow > 0) appendLine("queue_overflow=$overflow")
    }.trimEnd()

    private fun identityText(identity: AutoReportIdentity): String = buildString {
        append("Duo Status Bar ").append(identity.version)
        append(" · ").append(identity.manufacturer).append(' ').append(identity.model)
        append(" · ROM ").append(identity.rom)
    }

    private fun bounded(items: List<String>): String = buildString {
        append(items.take(MAX_ITEMS).joinToString(", "))
        if (items.size > MAX_ITEMS) append(" (+${items.size - MAX_ITEMS} more)")
    }
}

/**
 * The block appended to a ZIP report body naming the pending fingerprints that caused this send.
 * Only new/unsent entries are listed: an already-sent count is not a trigger for this report.
 */
internal fun whySentSection(pending: List<PendingProblem>): String = buildString {
    appendLine("=== why sent ===")
    appendLine("fingerprints that triggered this report:")
    pending.forEach { appendLine("- ${it.fingerprint} (count=${it.count})") }
}.trimEnd()

/**
 * One automatic-report pulse: consent gate, detection ledger, one ZIP for new problems, one compact
 * summary for counter-only pulses, the daily cap and reporter-failure retry.
 *
 * Nothing is collected when consent is off, and nothing is sent when there is no problem. Every failure
 * of the reporter itself is persisted as a `reporter` fingerprint, so it is retried on the next pulse
 * instead of reading as "no problems".
 */
internal class AutoReportPulse(
    private val store: AutoReportStateStore,
    private val clock: () -> Long,
    private val elapsedClock: () -> Long,
    private val identity: AutoReportIdentity,
    private val collector: () -> AutoReportCollected,
    private val zipper: (AutoReportCollected) -> ZipArtifact,
    private val sender: AutoReportSender,
    private val maxPerDay: Int = AutoReportLedger.MAX_SENDS_PER_DAY
) {

    fun run(): PulseResult {
        val start = elapsedClock()
        val initial = store.load()
        if (!ConsentGate.enabled(initial.consent)) {
            return finish(initial, PulseAction.SKIPPED_CONSENT, start)
        }
        var state = AutoReportLedger.rollDay(
            initial,
            AutoReportLedger.dayOf(clock())
        )

        val collected = try {
            collector()
        } catch (t: Throwable) {
            state = queueFailure(state, "collect", t.javaClass.simpleName)
            store.save(state)
            return finish(state, PulseAction.FAILED, start, detail = "collect:${t.javaClass.simpleName}")
        }

        val problems = collected.problems
        problems.forEach {
            state = AutoReportLedger.sight(state, it, identity.version, identity.model)
        }

        var sends = 0
        var zipBytes = 0L
        var action = PulseAction.NOTHING
        var detail = ""

        if (state.pending.isNotEmpty()) {
            if (!AutoReportLedger.canSend(state, maxPerDay)) {
                action = PulseAction.CAPPED
                detail = "cap=$maxPerDay"
            } else {
                var zipError: String? = null
                val artifact = try {
                    val enriched = collected.copy(
                        report = collected.report.trimEnd() + "\n\n" +
                            whySentSection(state.pending)
                    )
                    zipper(enriched)
                } catch (t: Throwable) {
                    zipError = t.javaClass.simpleName
                    null
                }
                if (artifact == null) {
                    state = queueFailure(state, "zip", zipError ?: "unavailable")
                    action = PulseAction.FAILED
                    detail = "zip:${zipError ?: "unavailable"}"
                } else {
                    val fingerprints = state.pending.map { it.fingerprint }
                    val caption = AutoReportCaption.zipCaption(
                        identity,
                        fingerprints,
                        state.counts,
                        state.overflow
                    )
                    val outcome = try {
                        sender.sendZip(artifact, caption)
                    } catch (t: Throwable) {
                        SendOutcome.Failed(t.javaClass.simpleName)
                    } finally {
                        artifact.file?.delete()
                    }
                    when (outcome) {
                        SendOutcome.Sent -> {
                            // Only a delivered upload uses the daily cap; a failed one can retry.
                            state = AutoReportLedger.noteAttempt(
                                AutoReportLedger.delivered(state, fingerprints)
                            )
                            sends = 1
                            zipBytes = artifact.bytes
                            action = PulseAction.ZIP_SENT
                        }
                        is SendOutcome.Failed -> {
                            state = queueFailure(state, "upload", outcome.errorClass)
                            action = PulseAction.FAILED
                            detail = "upload:${outcome.errorClass}"
                        }
                    }
                }
            }
        } else if (state.counts.isNotEmpty()) {
            if (!AutoReportLedger.canSend(state, maxPerDay)) {
                action = PulseAction.CAPPED
                detail = "cap=$maxPerDay"
            } else {
                val caption = AutoReportCaption.zipCaption(
                    identity,
                    emptyList(),
                    state.counts,
                    state.overflow
                )
                val text = AutoReportCaption.summary(identity, state.counts, state.overflow)
                val outcome = try {
                    sender.sendSummary(text, caption)
                } catch (t: Throwable) {
                    SendOutcome.Failed(t.javaClass.simpleName)
                }
                when (outcome) {
                    SendOutcome.Sent -> {
                        // Only a delivered summary uses the daily cap; a failed one can retry.
                        state = AutoReportLedger.noteAttempt(
                            AutoReportLedger.summaryDelivered(state)
                        )
                        sends = 1
                        action = PulseAction.SUMMARY_SENT
                    }
                    is SendOutcome.Failed -> {
                        state = queueFailure(state, "upload", outcome.errorClass)
                        action = PulseAction.FAILED
                        detail = "upload:${outcome.errorClass}"
                    }
                }
            }
        }

        store.save(state)
        return finish(state, action, start, problems = problems.size, sends = sends,
            zipBytes = zipBytes, sections = collected.sections, detail = detail)
    }

    private fun queueFailure(state: AutoReportState, stage: String, errorClass: String): AutoReportState =
        AutoReportLedger.queueReporterFailure(
            state,
            stage,
            errorClass,
            identity.version,
            identity.model
        )

    private fun finish(
        state: AutoReportState,
        action: PulseAction,
        start: Long,
        problems: Int = 0,
        sends: Int = 0,
        zipBytes: Long = 0L,
        sections: Int = 0,
        detail: String = ""
    ): PulseResult = PulseResult(
        action = action,
        problems = problems,
        sends = sends,
        zipBytes = zipBytes,
        elapsedMs = (elapsedClock() - start).coerceAtLeast(0L),
        pending = state.pending.size,
        sections = sections,
        detail = detail
    )
}
