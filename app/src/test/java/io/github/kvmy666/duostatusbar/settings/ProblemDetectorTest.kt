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

    private fun lsposedScope(
        enabled: String = "1",
        scope: String = "com.android.systemui"
    ) = CaptureSection(
        "LSPosed config (enabled / scope)",
        SectionState.PRESENT,
        "",
        "sqlite=/system/bin/sqlite3\n" +
            "module|enabled|scope\n" +
            "io.github.kvmy666.duostatusbar|$enabled|$scope"
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
    fun `an enabled module that never loaded is a problem when LSPosed proves it`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleState = ModuleState.NEVER_LOADED,
                moduleEnabled = true,
                duoEnabled = true,
                capture = capture(lsposedScope(), facts())
            )
        )
        assertEquals(listOf("module|module-load"), problems.map { "${it.type}|${it.location}" })
    }

    @Test
    fun `a never-loaded module without an LSPosed proof is not a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleState = ModuleState.NEVER_LOADED,
                moduleEnabled = true,
                duoEnabled = true,
                capture = capture(
                    CaptureSection(
                        "LSPosed config (enabled / scope)",
                        SectionState.PRESENT,
                        "",
                        "enabled=unknown scope=unknown (modules_config.db not queried)"
                    ),
                    facts()
                )
            )
        )
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `a never-loaded module is not a problem when Duo is off`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleState = ModuleState.NEVER_LOADED,
                moduleEnabled = true,
                duoEnabled = false,
                capture = capture(lsposedScope(), facts())
            )
        )
        assertTrue(problems.isEmpty())
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
    fun `a rive bind failure in the current status is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = "process=com.android.systemui pid=1 · owner=duo · stage=2 · " +
                    "renderer=none · attached=false · riveBind=bind",
                capture = capture(facts())
            )
        )
        assertTrue(problems.any { it.type == "fallback" && it.location == "rive" })
    }

    @Test
    fun `a stale rive bind line in the dump history is not a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = "owner=duo stage=2 renderer=Rive attached=true",
                dump = "history:\n  1795698346608 stage=2 · renderer=none · rive bind: reason=bind",
                moduleState = ModuleState.OK,
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "fallback" })
    }

    @Test
    fun `an ios miss is never a trigger`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = "ios v2 miss family=x piece=battery tried=StatBatteryMeterView",
                iosActive = true,
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "ios_miss" })
    }

    @Test
    fun `an attach retry exhaustion in the current status is a problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = "process=com.android.systemui pid=1 · owner=duo · stage=2 · " +
                    "attach failed after 6 attempts - see the diagnostic dump",
                moduleEnabled = true,
                duoEnabled = true,
                moduleState = ModuleState.OK,
                capture = capture(facts())
            )
        )
        assertTrue(problems.any { it.type == "attach" && it.location == "attach" })
    }

    @Test
    fun `an attached=false history line is not an attach trigger`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                dump = "history:\n  1795698344536 stage=0 · renderer=none · attached=false\n" +
                    "status bar window: not attached\nstep=attach:failed",
                moduleEnabled = true,
                moduleState = ModuleState.OK,
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "attach" })
    }

    @Test
    fun `an attach marker does not fire while an update is pending`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = "process=com.android.systemui pid=1 · owner=duo · " +
                    "attach failed after 6 attempts",
                moduleState = ModuleState.NEEDS_RESTART,
                moduleEnabled = true,
                duoEnabled = true,
                updatePending = true,
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "attach" })
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
    fun `safe mode may be written as yes or true`() {
        listOf("safeMode=yes", "safeMode=true").forEach { marker ->
            val problems = ProblemDetector.detect(
                ProblemInputs(
                    capture = capture(
                        CaptureSection("safe mode", SectionState.PRESENT, "", marker),
                        facts()
                    )
                )
            )
            assertTrue("$marker must be safe mode", problems.any { it.type == "crash" && it.location == "safe-mode" })
        }
    }

    @Test
    fun `a crash counter above zero without safe mode is not a crash`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection("safe mode", SectionState.PRESENT, "", "counter=1\nsafeMode=no"),
                    CaptureSection(
                        "systemui deaths",
                        SectionState.PRESENT,
                        "",
                        "deaths=1\nsafeMode=no\nsafeModeCounter=1\ncrashRecords=0"
                    ),
                    facts()
                )
            )
        )
        assertTrue(problems.none { it.type == "crash" })
    }

    @Test
    fun `an old systemui crash record is not a current crash trigger`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                capture = capture(
                    CaptureSection(
                        "crash records",
                        SectionState.PRESENT,
                        "",
                        "2026-11-24 16:50:39 system_app_crash (text, 5068 bytes)\nprocess: com.android.systemui"
                    ),
                    CaptureSection("systemui deaths", SectionState.PRESENT, "", "deaths=records\nsafeMode=no"),
                    facts()
                )
            )
        )
        assertTrue(problems.none { it.type == "crash" })
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
    fun `a panel fallback is recorded but never a hook trigger`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(
                moduleLog = "panel hook unavailable sdk=37 panelFallback=attach-order",
                capture = capture(facts())
            )
        )
        assertTrue(problems.none { it.type == "hook" })
    }

    @Test
    fun `an install failure is a hook problem`() {
        val problems = ProblemDetector.detect(
            ProblemInputs(moduleLog = "DuoHook install failed: NoSuchMethod", capture = capture(facts()))
        )
        assertTrue(problems.any { it.type == "hook" && it.location == "hook" })
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
                moduleLog = "DuoHook install failed: one\nDuoHook install failed: two",
                capture = capture(facts())
            )
        )
        assertEquals(1, problems.count { it.type == "hook" && it.location == "hook" })
    }

    @Test
    fun `every fingerprint has a short human reason`() {
        listOf(
            "attach|attach|2.1.2|M",
            "fallback|rive|2.1.2|M",
            "hook|hook|2.1.2|M",
            "hook|panel|2.1.2|M",
            "module|module-load|2.1.2|M",
            "module|report|2.1.2|M",
            "crash|safe-mode|2.1.2|M",
            "crash|systemui|2.1.2|M",
            "ios_miss|wifi|2.1.2|M",
            "collector|capture|2.1.2|M",
            "reporter|upload:IOException|2.1.2|M"
        ).forEach { fingerprint ->
            val reason = ProblemDetector.humanReason(fingerprint)
            assertTrue("$fingerprint needs a reason", reason.isNotBlank())
            assertFalse("$fingerprint must stay ASCII", reason.any { it.code > 127 })
            assertFalse("$fingerprint must be one line", reason.contains('\n'))
        }
        assertEquals(
            "Duo owns the bar but did not attach after its retries",
            ProblemDetector.humanReason("attach|attach|2.1.3|SM-S938U1")
        )
    }
}
