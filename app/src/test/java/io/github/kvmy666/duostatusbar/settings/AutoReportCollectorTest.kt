package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A collector read that throws must become a recorded failure, never a silent `null` that reads as
 * "nothing to report". The label and the exception class travel to ProblemDetector as `collector|capture`.
 */
class AutoReportCollectorTest {

    @Test
    fun `a failed health read is recorded as health with its class`() {
        val failures = mutableListOf<String>()
        val health: ModuleHealth? = AutoReportManager.collectorRead("health", failures) {
            throw IllegalStateException("boom")
        }
        assertNull(health)
        assertEquals(listOf("health:IllegalStateException"), failures)
    }

    @Test
    fun `a failed module log read is recorded as module-log with its class`() {
        val failures = mutableListOf<String>()
        val log: String? = AutoReportManager.collectorRead("module-log", failures) {
            throw IllegalArgumentException("broken")
        }
        assertNull(log)
        assertEquals(listOf("module-log:IllegalArgumentException"), failures)
    }

    @Test
    fun `a recorded read failure queues a collector capture problem`() {
        val failures = mutableListOf("IllegalStateException: capture")
        AutoReportManager.collectorRead("health", failures) {
            throw IllegalStateException("boom")
        }
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleEnabled = true,
                moduleState = ModuleState.OK,
                captureFailure = failures.joinToString("; "),
                capture = RootCapture(
                    "test",
                    listOf(
                        CaptureSection(
                            "app-side facts (no privilege)",
                            SectionState.PRESENT,
                            "",
                            "MODEL=test"
                        )
                    )
                )
            )
        )
        val collector = problems.firstOrNull { it.type == "collector" && it.location == "capture" }
        assertTrue("collector problem missing: $problems", collector != null)
        assertTrue(
            "failure class missing from detail: ${collector!!.detail}",
            collector.detail.contains("health:IllegalStateException")
        )
    }
}
