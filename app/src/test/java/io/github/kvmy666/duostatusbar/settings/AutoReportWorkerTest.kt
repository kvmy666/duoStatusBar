package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A thrown pulse is retried through WorkManager backoff, its failure is persisted for the next pulse,
 * and the first-pulse stamp stays pending so the retry is not pushed to the 24 h periodic run.
 */
class AutoReportWorkerTest {

    @Test
    fun `a successful pulse marks the first pulse done`() {
        var markings = 0
        var persisted = 0
        val outcome = finishPulse(
            failure = null,
            markFirstPulseDone = { markings++ },
            persistFailure = { persisted++ },
            log = { }
        )
        assertEquals(PulseAttempt.SUCCESS, outcome)
        assertEquals(1, markings)
        assertEquals(0, persisted)
    }

    @Test
    fun `a failed pulse is retried, persisted and never marked done`() {
        var markings = 0
        val persisted = mutableListOf<Throwable>()
        val failure = IllegalStateException("pulse")
        val outcome = finishPulse(
            failure = failure,
            markFirstPulseDone = { markings++ },
            persistFailure = { persisted += it },
            log = { }
        )
        assertEquals(PulseAttempt.RETRY, outcome)
        assertEquals("a failed pulse must be retried, not hidden", 0, markings)
        assertEquals(listOf(failure), persisted)
    }

    @Test
    fun `a persistence failure is logged and the pulse still retries`() {
        var markings = 0
        val logs = mutableListOf<String>()
        val outcome = finishPulse(
            failure = IllegalStateException("pulse"),
            markFirstPulseDone = { markings++ },
            persistFailure = { throw IllegalArgumentException("store") },
            log = { logs += it }
        )
        assertEquals(PulseAttempt.RETRY, outcome)
        assertEquals(0, markings)
        assertTrue(
            "the nested persistence failure must be logged: $logs",
            logs.any { it.contains("IllegalArgumentException") }
        )
        assertTrue(
            "the pulse failure must still be logged: $logs",
            logs.any { it.contains("IllegalStateException") }
        )
    }
}
