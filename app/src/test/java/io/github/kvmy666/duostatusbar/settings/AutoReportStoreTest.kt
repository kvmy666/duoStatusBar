package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic reporter's ledger: consent gate, "one fingerprint sent once", counters, the daily cap,
 * the bounded queue with explicit overflow, and the day rollover that releases queued work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoReportStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val identity = AutoReportIdentity("2.1.2", "OnePlus", "CPH2747", "oxygenos")
    private val problem = DetectedProblem("attach", "attach")

    private fun sighted(state: AutoReportState) =
        AutoReportLedger.sight(state, problem, identity.version, identity.model)

    private fun fp() = ProblemDetector.fingerprint(problem, identity.version, identity.model)

    @Test
    fun `default consent is unanswered and therefore off`() {
        val store = AutoReportStore.open(context)
        assertEquals(ConsentState.UNANSWERED, store.load().consent)
        assertFalse(ConsentGate.enabled(store.load().consent))
        assertFalse(AutoReportStore.firstPulseDone(context))
    }

    @Test
    fun `consent round trips through the dedicated prefs file`() {
        AutoReportStore.writeConsent(context, ConsentState.YES)
        assertEquals(ConsentState.YES, AutoReportStore.consent(context))
        AutoReportStore.writeConsent(context, ConsentState.NO)
        assertEquals(ConsentState.NO, AutoReportStore.consent(context))
        // The answer, not a settings default, decides the dialog: it is never asked again.
        assertFalse(ConsentGate.shouldShow(AutoReportStore.consent(context)))
    }

    @Test
    fun `pulse state round trips including sent counters and the queue`() {
        val store = AutoReportStore.open(context)
        val state = AutoReportState(
            consent = ConsentState.YES,
            sent = listOf(fp()),
            counts = mapOf(fp() to 4),
            pending = listOf(PendingProblem("module|module-load|2.1.2|CPH2747", 2)),
            day = 7L,
            sendsToday = 1,
            overflow = 3
        )
        store.save(state)
        assertEquals(state, store.load())
    }

    @Test
    fun `first pulse completion survives a store reopen`() {
        AutoReportStore.markFirstPulsePending(context)
        assertFalse(AutoReportStore.firstPulseDone(context))
        AutoReportStore.markFirstPulseDone(context)
        assertTrue(AutoReportStore.firstPulseDone(context))
        AutoReportStore.markFirstPulsePending(context)
        assertFalse(AutoReportStore.firstPulseDone(context))
    }

    @Test
    fun `a fingerprint is sent once then only counted`() {
        var state = sighted(AutoReportState())
        assertEquals(1, state.pending.size)
        assertEquals(fp(), state.pending.first().fingerprint)

        state = AutoReportLedger.delivered(state, listOf(fp()))
        assertTrue(state.sent.contains(fp()))
        assertTrue(state.pending.isEmpty())

        state = sighted(state)
        assertTrue("a sent fingerprint must not be queued again", state.pending.isEmpty())
        assertEquals(1, state.counts[fp()])
    }

    @Test
    fun `repeats increment the counter`() {
        var state = sighted(AutoReportState())
        state = AutoReportLedger.delivered(state, listOf(fp()))
        state = sighted(state)
        state = sighted(state)
        state = sighted(state)
        assertEquals(3, state.counts[fp()])
    }

    @Test
    fun `repeats before sending coalesce into one queued item`() {
        var state = sighted(AutoReportState())
        state = sighted(state)
        state = sighted(state)
        assertEquals(1, state.pending.size)
        assertEquals(3, state.pending.first().count)
    }

    @Test
    fun `the daily cap blocks sends and keeps the work queued`() {
        var state = sighted(AutoReportState(day = 3L))
        repeat(AutoReportLedger.MAX_SENDS_PER_DAY) { state = AutoReportLedger.noteAttempt(state) }
        assertFalse(AutoReportLedger.canSend(state))
        assertEquals(1, state.pending.size)
        // A new day releases it.
        state = AutoReportLedger.rollDay(state, 4L)
        assertTrue(AutoReportLedger.canSend(state))
        assertEquals(1, state.pending.size)
        assertEquals(0, state.sendsToday)
    }

    @Test
    fun `the queue is bounded and overflow is counted not dropped silently`() {
        var state = AutoReportState()
        repeat(AutoReportLedger.MAX_QUEUE + 7) { index ->
            state = AutoReportLedger.sight(
                state,
                DetectedProblem("hook", "piece-$index"),
                identity.version,
                identity.model
            )
        }
        assertEquals(AutoReportLedger.MAX_QUEUE, state.pending.size)
        assertEquals(7, state.overflow)
        // The newest survive: an old first piece is gone, the last piece is kept.
        assertFalse(state.pending.any { it.fingerprint.startsWith("hook|piece-0|") })
        assertTrue(state.pending.any { it.fingerprint.startsWith("hook|piece-56|") })
    }

    @Test
    fun `the sent list is bounded`() {
        var state = AutoReportState()
        repeat(AutoReportLedger.MAX_SENT + 5) { index ->
            state = AutoReportLedger.delivered(state, listOf("type|loc|1|m$index"))
        }
        assertEquals(AutoReportLedger.MAX_SENT, state.sent.size)
        assertTrue(state.sent.last().endsWith("m204"))
    }

    @Test
    fun `a reporter failure becomes a queued problem with the stage and class`() {
        val state = AutoReportLedger.queueReporterFailure(
            AutoReportState(),
            "upload",
            "IOException",
            identity.version,
            identity.model
        )
        assertEquals(1, state.pending.size)
        assertTrue(state.pending.first().fingerprint.startsWith("reporter|upload:IOException|"))
    }
}
