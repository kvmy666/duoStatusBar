package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17A: the "module never loaded" explanation is decided by a pure function, so every likely cause
 * (no LSPosed, not enabled, System UI not scoped, scoped but stale, config unreadable) is proven here
 * instead of only on a phone. The report wording is asserted too: it must name the fix.
 */
class ModuleLoadDiagnosisTest {

    private fun scope(
        queried: Boolean = true,
        enabled: Boolean? = true,
        value: String? = "com.android.systemui"
    ) = RootLogs.ModuleScope(queried, enabled, value, row = "duostatusbar|$enabled|$value")

    @Test
    fun `a healthy or unrelated state needs no explanation`() {
        listOf(
            ModuleState.OK,
            ModuleState.NEEDS_RESTART,
            ModuleState.DISABLED,
            ModuleState.NOT_REPORTING,
            ModuleState.IOS_ACTIVE,
            ModuleState.IOS_UNSUPPORTED
        ).forEach { state ->
            val diagnosis = ModuleLoadDiagnosis.diagnose(state, true, scope(), true)
            assertEquals("$state must stay NOT_NEEDED", LoadCause.NOT_NEEDED, diagnosis.cause)
            assertNull(diagnosis.reportCause())
            assertNull(diagnosis.reportStep())
        }
    }

    @Test
    fun `no LSPosed manager means LSPosed is missing`() {
        val diagnosis = ModuleLoadDiagnosis.diagnose(ModuleState.NEVER_LOADED, false, null, false)
        assertEquals(LoadCause.LSPOSED_MISSING, diagnosis.cause)
        assertEquals(false, diagnosis.lsposedInstalled)
        assertTrue(diagnosis.reportCause()!!.contains("LSPosed"))
        assertTrue(diagnosis.reportStep()!!.contains("Install"))
    }

    @Test
    fun `module disabled in LSPosed`() {
        val diagnosis = ModuleLoadDiagnosis.diagnose(
            ModuleState.NEVER_LOADED, true, scope(enabled = false, value = null), false
        )
        assertEquals(LoadCause.NOT_ENABLED, diagnosis.cause)
        assertTrue(diagnosis.reportCause()!!.contains("not enabled"))
        assertTrue(diagnosis.reportStep()!!.contains("Enable Duo Status Bar"))
    }

    @Test
    fun `System UI absent from the scope`() {
        val diagnosis = ModuleLoadDiagnosis.diagnose(
            ModuleState.NEVER_LOADED, true, scope(value = "com.other.app"), false
        )
        assertEquals(LoadCause.NOT_SCOPED, diagnosis.cause)
        assertTrue(diagnosis.reportCause()!!.contains("not in"))
        assertTrue(diagnosis.reportStep()!!.contains("scope"))
    }

    @Test
    fun `System UI in the scope means the next step is a restart`() {
        val diagnosis = ModuleLoadDiagnosis.diagnose(
            ModuleState.NEVER_LOADED, true, scope(value = "com.android.systemui"), false
        )
        assertEquals(LoadCause.SCOPED_BUT_NOT_LOADED, diagnosis.cause)
        assertTrue(diagnosis.reportCause()!!.contains("did not load"))
        assertTrue(diagnosis.reportStep()!!.contains("Restart"))
    }

    @Test
    fun `the System UI package match ignores case and separators`() {
        listOf("COM.ANDROID.SYSTEMUI", " com.android.systemui ", "x,com.android.systemui,y").forEach {
            val diagnosis = ModuleLoadDiagnosis.diagnose(
                ModuleState.NEVER_LOADED, true, scope(value = it), false
            )
            assertEquals("scope '$it' must match", LoadCause.SCOPED_BUT_NOT_LOADED, diagnosis.cause)
        }
    }

    @Test
    fun `an unknown or unreadable scope cannot be guessed`() {
        listOf(
            scope(queried = false, enabled = null, value = null),
            scope(enabled = null, value = null),
            scope(enabled = true, value = null),
            scope(enabled = true, value = "unknown"),
            scope(enabled = true, value = "  ")
        ).forEach { scope ->
            val diagnosis = ModuleLoadDiagnosis.diagnose(ModuleState.NEVER_LOADED, true, scope, false)
            assertEquals(
                "scope=$scope must stay unknown",
                LoadCause.ENABLE_OR_SCOPE_UNKNOWN,
                diagnosis.cause
            )
            assertTrue(diagnosis.reportCause()!!.contains("not enabled"))
            assertTrue(diagnosis.reportStep()!!.contains("scope"))
        }
    }

    @Test
    fun `a missing LSPosed manager is unknown when the capture proves it was queried`() {
        // The enum rule stays driven by lsposedInstalled; this asserts the pass-through shape only.
        val diagnosis = ModuleLoadDiagnosis.diagnose(ModuleState.NEVER_LOADED, null, null, false)
        assertEquals(LoadCause.ENABLE_OR_SCOPE_UNKNOWN, diagnosis.cause)
    }

    @Test
    fun `the report capability flag is passed through unchanged`() {
        listOf(true, false).forEach { cannot ->
            val diagnosis = ModuleLoadDiagnosis.diagnose(
                ModuleState.NEVER_LOADED, true, scope(value = "com.android.systemui"), cannot
            )
            assertEquals(cannot, diagnosis.reportCannotReadFrameworkLogs)
        }
    }

    @Test
    fun `every real cause has a non blank cause and step that names the fix`() {
        val causes = listOf(
            LoadCause.LSPOSED_MISSING,
            LoadCause.NOT_ENABLED,
            LoadCause.NOT_SCOPED,
            LoadCause.SCOPED_BUT_NOT_LOADED,
            LoadCause.ENABLE_OR_SCOPE_UNKNOWN
        )
        causes.forEach { cause ->
            val diagnosis = LoadDiagnosis(cause, true, false)
            val text = diagnosis.reportCause()
            val step = diagnosis.reportStep()
            assertNotNull("$cause has no cause text", text)
            assertNotNull("$cause has no step text", step)
            assertTrue("$cause cause is blank", !text.isNullOrBlank())
            assertTrue("$cause step is blank", !step.isNullOrBlank())
            assertTrue("$cause step is not a fix: $step", step!!.length > 10)
            assertFalse("$cause cause leaks a format placeholder", text!!.contains("%"))
        }
    }

    @Test
    fun `unrelated unreadable sections do not claim the framework logs were unreadable`() {
        val capture = RootCapture(
            "route=root",
            listOf(
                CaptureSection("capture route", SectionState.PRESENT, "", "rootAttempted=true"),
                CaptureSection("LSPosed newest modules log", SectionState.PRESENT, "", "log"),
                CaptureSection("breadcrumb", SectionState.UNREADABLE, "unset"),
                CaptureSection("crash records", SectionState.PRESENT, "", "none")
            )
        )
        assertFalse(ModuleLoadDiagnosis.reportCannotReadFrameworkLogs(capture, null))
    }

    @Test
    fun `a skipped capture route means no framework logs`() {
        val capture = RootCapture(
            "route=skipped",
            listOf(CaptureSection("capture route", SectionState.SKIPPED, "no root or Shizuku"))
        )
        assertTrue(ModuleLoadDiagnosis.reportCannotReadFrameworkLogs(capture, null))
    }

    @Test
    fun `an unreadable LSPosed config means no framework logs`() {
        val capture = RootCapture(
            "route=root",
            listOf(
                CaptureSection("capture route", SectionState.PRESENT, "", "rootAttempted=true"),
                CaptureSection(
                    ModuleLoadDiagnosis.SCOPE_SECTION,
                    SectionState.UNREADABLE,
                    "permission denied"
                )
            )
        )
        assertTrue(ModuleLoadDiagnosis.reportCannotReadFrameworkLogs(capture, null))
    }

    @Test
    fun `a recorded capture failure means no framework logs`() {
        assertTrue(
            ModuleLoadDiagnosis.reportCannotReadFrameworkLogs(null, "no root or Shizuku")
        )
    }
}
