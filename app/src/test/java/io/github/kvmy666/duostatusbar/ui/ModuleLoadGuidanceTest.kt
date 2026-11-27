package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import io.github.kvmy666.duostatusbar.settings.LoadCause
import io.github.kvmy666.duostatusbar.settings.ModuleHealth
import io.github.kvmy666.duostatusbar.settings.ModuleLoadDiagnosis
import io.github.kvmy666.duostatusbar.settings.ModuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R17A: the health card explains a never-loaded module with real resources, not invented Compose
 * English. The strings are resolved from `res/values`, so a renamed or missing id fails the build
 * gate instead of showing a blank line, and the cause -> resource mapping is asserted directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleLoadGuidanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val causeIds = listOf(
        LoadCause.LSPOSED_MISSING,
        LoadCause.NOT_ENABLED,
        LoadCause.NOT_SCOPED,
        LoadCause.SCOPED_BUT_NOT_LOADED,
        LoadCause.ENABLE_OR_SCOPE_UNKNOWN
    )

    @Test
    fun `every new health guidance string resolves to non blank text`() {
        listOf(
            R.string.module_health_cause_lsposed_missing,
            R.string.module_health_fix_lsposed_missing,
            R.string.module_health_cause_not_enabled,
            R.string.module_health_fix_not_enabled,
            R.string.module_health_cause_not_scoped,
            R.string.module_health_fix_not_scoped,
            R.string.module_health_cause_scoped_not_loaded,
            R.string.module_health_fix_scoped_not_loaded,
            R.string.module_health_cause_unknown_scope,
            R.string.module_health_fix_unknown_scope,
            R.string.module_health_report_no_framework_logs
        ).forEach { id ->
            assertTrue("string resource $id is blank", context.getString(id).isNotBlank())
        }
    }

    @Test
    fun `the plain English guidance carries no format placeholders`() {
        listOf(
            R.string.module_health_cause_lsposed_missing,
            R.string.module_health_fix_lsposed_missing,
            R.string.module_health_cause_not_enabled,
            R.string.module_health_fix_not_enabled,
            R.string.module_health_cause_not_scoped,
            R.string.module_health_fix_not_scoped,
            R.string.module_health_cause_scoped_not_loaded,
            R.string.module_health_fix_scoped_not_loaded,
            R.string.module_health_cause_unknown_scope,
            R.string.module_health_fix_unknown_scope,
            R.string.module_health_report_no_framework_logs
        ).forEach { id ->
            assertFalse("string resource $id needs unexpected formatting", context.getString(id).contains("%"))
        }
    }

    @Test
    fun `the three setup failures read as plain English`() {
        assertEquals(
            "LSPosed not detected",
            context.getString(R.string.module_health_cause_lsposed_missing)
        )
        assertTrue(
            context.getString(R.string.module_health_fix_lsposed_missing).contains("Install LSPosed")
        )
        assertEquals(
            "Duo is not enabled in LSPosed",
            context.getString(R.string.module_health_cause_not_enabled)
        )
        assertTrue(
            context.getString(R.string.module_health_fix_not_enabled).contains("restart System UI")
        )
        assertEquals(
            "System UI is not in Duo's scope",
            context.getString(R.string.module_health_cause_not_scoped)
        )
        assertTrue(
            context.getString(R.string.module_health_fix_not_scoped).contains("scope")
        )
    }

    @Test
    fun `every real cause maps to a title and a fix resource`() {
        causeIds.forEach { cause ->
            val title = causeTitleRes(cause)
            val fix = causeFixRes(cause)
            assertTrue("$cause has no title resource", title != null)
            assertTrue("$cause has no fix resource", fix != null)
            assertTrue(context.getString(title!!).isNotBlank())
            assertTrue(context.getString(fix!!).isNotBlank())
        }
        assertNull(causeTitleRes(LoadCause.NOT_NEEDED))
        assertNull(causeFixRes(LoadCause.NOT_NEEDED))
        assertNull(causeTitleRes(null))
        assertNull(causeFixRes(null))
    }

    private val neverLoaded = ModuleHealth(ModuleState.NEVER_LOADED, 0L, 0L)

    private fun scopeCapture(enabled: String, scope: String): RootCapture = RootCapture(
        "route=no privilege",
        listOf(
            CaptureSection("capture route", SectionState.PRESENT, "", "rootAttempted=false"),
            CaptureSection(
                ModuleLoadDiagnosis.SCOPE_SECTION,
                SectionState.PRESENT,
                "",
                "sqlite=ok\nmodule|enabled|scope\nduostatusbar|$enabled|$scope"
            )
        )
    )

    @Test
    fun `the health-card diagnosis reads the capture scope and proves LSPosed was queried`() {
        // The LSPosed manager is not installed in Robolectric, but a queried config is proof enough:
        // the module is scoped to System UI, so the next step is a restart.
        val scoped = ModuleLoadDiagnosis.of(context, neverLoaded, scopeCapture("1", "com.android.systemui"))
        assertEquals(LoadCause.SCOPED_BUT_NOT_LOADED, scoped.cause)
        assertFalse(scoped.reportCannotReadFrameworkLogs)

        val unscoped = ModuleLoadDiagnosis.of(context, neverLoaded, scopeCapture("1", "com.other.app"))
        assertEquals(LoadCause.NOT_SCOPED, unscoped.cause)

        val disabled = ModuleLoadDiagnosis.of(context, neverLoaded, scopeCapture("0", "com.android.systemui"))
        assertEquals(LoadCause.NOT_ENABLED, disabled.cause)
    }

    @Test
    fun `a failed or skipped capture means the report could not read framework logs`() {
        val failed = RootCapture(
            "route=no privilege",
            listOf(
                CaptureSection(
                    "capture route",
                    SectionState.PRESENT,
                    "",
                    "rootAttempted=false shizukuAttempted=false\ncaptureFailure=no root or Shizuku"
                )
            )
        )
        assertTrue(ModuleLoadDiagnosis.of(context, neverLoaded, failed).reportCannotReadFrameworkLogs)

        val skipped = RootCapture(
            "route=no privilege",
            listOf(CaptureSection("LSPosed config (enabled / scope)", SectionState.SKIPPED, "no route"))
        )
        assertTrue(ModuleLoadDiagnosis.of(context, neverLoaded, skipped).reportCannotReadFrameworkLogs)
    }
}
