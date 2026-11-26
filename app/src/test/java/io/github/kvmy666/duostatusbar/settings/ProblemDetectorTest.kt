package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The detector is the only place that decides "this is a problem worth a report". It reads the module's
 * own status/dump/fallback, ModuleHealth and the shared capture; each rule has a stable location code.
 */
class ProblemDetectorTest {

    private fun capture(vararg sections: CaptureSection) =
        RootCapture("test", sections.toList())

    private fun facts() = CaptureSection(
        "app-side facts (no privilege)",
        SectionState.PRESENT,
        "",
        "MODEL=test"
    )

    @Test
    fun `no problem produces nothing`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = "process=com.android.systemui pid=1 · owner=duo · stage=1 renderer=Rive attached=true",
                dump = "ios v2 runtimeMisses: family=x wifi=0 cell=0 label=0 hidden=0",
                moduleState = ModuleState.OK,
                moduleEnabled = true,
                iosActive = false,
                capture = capture(facts())
            )
        )
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `an enabled module that never loaded is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(moduleState = ModuleState.NEVER_LOADED, moduleEnabled = true, capture = capture(facts()))
        )
        assertEquals(listOf("module|module-load"), problems.map { "${it.type}|${it.location}" })
    }

    @Test
    fun `an enabled module whose state is unreadable is a report problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = "process=com.android.systemui pid=1 · owner=duo",
                moduleState = null,
                moduleEnabled = true,
                capture = capture(facts())
            )
        )
        assertEquals(listOf("module|report"), problems.map { "${it.type}|${it.location}" })
    }

    @Test
    fun `a rive fallback is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                fallback = "Rive did not bind on 24069PC21G (reason=asset)",
                capture = capture(facts())
            )
        )
        assertTrue(problems.any { it.type == "fallback" && it.location == "rive" })
    }

    @Test
    fun `a rive bind failure in the dump is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(dump = "rive bind: reason=surface detail=no-surface", capture = capture(facts()))
        )
        assertTrue(problems.any { it.type == "fallback" && it.location == "rive" })
    }

    @Test
    fun `an ios miss names the piece that missed`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = "ios v2 miss family=x piece=battery tried=StatBatteryMeterView",
                iosActive = true,
                capture = capture(facts())
            )
        )
        assertTrue(problems.any { it.type == "ios_miss" && it.location == "battery" })
    }

    @Test
    fun `ios misses are ignored when ios does not own the bar`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = "ios v2 miss family=x piece=wifi tried=IosWifi",
                iosActive = false,
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "ios_miss" })
    }

    @Test
    fun `an attach failure is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(status = "attached=false stage=0", capture = capture(facts()))
        )
        assertTrue(problems.any { it.type == "attach" && it.location == "attach" })
    }

    @Test
    fun `a not attached dump is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(dump = "status bar window: not attached", capture = capture(facts()))
        )
        assertTrue(problems.any { it.type == "attach" && it.location == "attach" })
    }

    // R16-C: an update pending is one state, not a load failure; the old build's evidence is stale.
    @Test
    fun `needs restart is not a module load problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleState = ModuleState.NEEDS_RESTART,
                moduleEnabled = true,
                updatePending = true,
                capture = capture(facts())
            )
        )
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `a stale dump does not trigger attach while an update is pending`() {
        val staleDump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.1.1 (code 111, debug=false)\n" +
            "status bar window: attached=false\n" +
            "module: attached=false stage=0\n"
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = staleDump,
                moduleState = ModuleState.NEEDS_RESTART,
                moduleEnabled = true,
                updatePending = true,
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "attach" })
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `the same stale dump without updatePending still detects attach`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = "module: attached=false stage=0",
                moduleEnabled = true,
                moduleState = ModuleState.OK,
                updatePending = false,
                capture = capture(facts())
            )
        )
        assertTrue(problems.any { it.type == "attach" && it.location == "attach" })
    }

    @Test
    fun `safe mode this boot is a crash problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection("safe mode", SectionState.PRESENT, "", "counter=4\nsafeMode=entered"),
                    facts()
                )
            )
        )
        assertTrue(problems.any { it.type == "crash" && it.location == "safe-mode" })
    }

    @Test
    fun `a systemui crash record is a crash problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection(
                        "crash records",
                        SectionState.PRESENT,
                        "",
                        "process: com.android.systemui\nsignal 11"
                    ),
                    facts()
                )
            )
        )
        assertTrue(problems.any { it.type == "crash" && it.location == "systemui" })
    }

    @Test
    fun `a crash detail never says records`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection(
                        "crash records",
                        SectionState.PRESENT,
                        "",
                        "process: com.android.systemui\nsignal 11"
                    ),
                    facts()
                )
            )
        )
        val crash = problems.first { it.type == "crash" }
        assertEquals("deaths=unknown", crash.detail)
        problems.forEach { problem ->
            assertFalse(
                "a missing counter is not a record count: ${problem.detail}",
                problem.detail.contains("records")
            )
            assertFalse("type must stay a stable code: ${problem.type}", problem.type.contains("records"))
            assertFalse("location must stay a stable code: ${problem.location}", problem.location.contains("records"))
        }
    }

    @Test
    fun `systemui deaths above zero are a crash problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection("systemui deaths", SectionState.PRESENT, "", "deaths=2\nsafeMode=no"),
                    facts()
                )
            )
        )
        assertTrue(problems.any { it.type == "crash" && it.location == "systemui" })
    }

    @Test
    fun `a collector that cannot read anything reports cannot read`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection("capture route", SectionState.UNREADABLE, "no route"),
                    CaptureSection("logcat (DuoSB)", SectionState.UNREADABLE, "permission denied")
                )
            )
        )
        val collector = problems.firstOrNull { it.type == "collector" }
        assertTrue(collector != null && collector.location == "capture")
        assertTrue(collector!!.detail.contains("permission denied") || collector.detail.contains("no route"))
    }

    @Test
    fun `a capture exception is a collector problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(captureFailure = "IllegalStateException: boom", capture = capture(facts()))
        )
        assertTrue(problems.any { it.type == "collector" && it.location == "capture" })
    }

    @Test
    fun `a panel hook fallback names the panel location`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleLog = "panel hook unavailable sdk=37 panelFallback=attach-order",
                capture = capture(facts())
            )
        )
        assertTrue(problems.any { it.type == "hook" && it.location == "panel" })
    }

    @Test
    fun `an install failure is a hook problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(moduleLog = "DuoHook install failed: NoSuchMethod", capture = capture(facts()))
        )
        assertTrue(problems.any { it.type == "hook" })
    }

    @Test
    fun `every problem type has a stable fingerprint`() {
        val problem = DetectedProblem("attach", "attach")
        assertEquals(
            "attach|attach|2.1.2|CPH2747",
            ProblemDetector.fingerprint(problem, "2.1.2", "CPH2747")
        )
        assertEquals(
            "attach|attach|2.1.2|CPH2747",
            ProblemDetector.fingerprint(problem, "2.1.2", "CPH2747")
        )
    }

    @Test
    fun `a reporter failure keeps the stage and the error class`() {
        val problem = ProblemDetector.reporterFailure("upload", "IOException")
        assertEquals("reporter", problem.type)
        assertEquals("upload:IOException", problem.location)
        assertEquals(
            "reporter|zip:ZipException|2.1.2|M",
            ProblemDetector.fingerprint(ProblemDetector.reporterFailure("zip", "ZipException"), "2.1.2", "M")
        )
    }

    @Test
    fun `duplicate detections collapse to one problem per location`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = "ios v2 miss family=x piece=wifi tried=a\nios v2 miss family=x piece=wifi tried=b",
                iosActive = true,
                capture = capture(facts())
            )
        )
        assertEquals(1, problems.count { it.type == "ios_miss" && it.location == "wifi" })
    }
}
