package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogReporterZipTest {

    /**
     * The ZIP name is stamped to the second and [writeReportZip] refuses to overwrite, so tests that
     * land in the same second would collide. Clear this class's own reports first.
     */
    @Before
    fun clearOldReports() {
        val dir = RuntimeEnvironment.getApplication().getExternalFilesDir("diagnostics")
        dir?.listFiles { f -> f.isFile && f.name.startsWith("duo-report-") }?.forEach { it.delete() }
    }

    @Test
    fun writeReportZipCreatesZipWithExpectedEntries() {
        val context = RuntimeEnvironment.getApplication()
        val file = writeReportZip(
            context,
            report = "diag-body",
            logs = "logcat-body",
            status = "status-body",
            dump = "dump-body"
        )
        assertNotNull(file)
        assertTrue(file!!.exists())
        ZipFile(file).use { zf ->
            assertNotNull(zf.getEntry("manifest.json"))
            assertNotNull(zf.getEntry("diagnostics.txt"))
            assertNotNull(zf.getEntry("module-log.txt"))
            assertNotNull(zf.getEntry("logcat.txt"))
        }
    }

    @Test
    fun `stale diagnostic dump is labelled in the zip`() {
        val context = RuntimeEnvironment.getApplication()
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 1.4.2 (code 23, debug=false)\n" +
            "build:\n  x=1"
        val file = writeReportZip(
            context,
            report = "diag",
            logs = "logs",
            status = "status",
            dump = dump
        )
        assertNotNull(file)
        ZipFile(file).use { zf ->
            val text = zf.getInputStream(zf.getEntry("diagnostics-dump.txt"))
                .bufferedReader().readText()
            assertTrue(text.startsWith("[stale diagnostic dump from 1.4.2"))
            assertTrue("the original evidence must survive", text.contains("version: 1.4.2"))
        }
    }

    @Test
    fun `evidenceLive false drops the stored status history and dump`() {
        val context = RuntimeEnvironment.getApplication()
        val status = "process=com.android.systemui pid=7525 · owner=ios · stage=2"
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: ${BuildConfig.VERSION_NAME} (code 1, debug=false)\n" +
            "DUMP-MARKER\n"
        val report = buildDiagnostics(
            context = context,
            settings = DuoSettings(),
            status = status,
            history = listOf("HISTORY-MARKER"),
            dump = dump,
            moduleLoadAt = 0L,
            dumpAt = 123L,
            evidenceLive = false
        )
        assertFalse("stored status must be gone: $report", report.contains("owner=ios"))
        assertFalse(
            "stored status must be gone: $report",
            report.contains("process=com.android.systemui")
        )
        assertFalse("stored history must be gone: $report", report.contains("HISTORY-MARKER"))
        assertFalse("stored dump must be gone: $report", report.contains("DUMP-MARKER"))
        assertTrue(
            "the report must say there is no live module report: $report",
            report.contains("no report yet")
        )
    }

    @Test
    fun `an evidence dead zip carries no stored status or dump`() {
        val context = RuntimeEnvironment.getApplication()
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: ${BuildConfig.VERSION_NAME} (code 1, debug=false)\n" +
            "DUMP-MARKER\n"
        val file = writeReportZip(
            context,
            report = "diag",
            logs = "logs",
            status = "STATUS-MARKER",
            dump = dump,
            evidenceLive = false
        )
        assertNotNull(file)
        ZipFile(file!!).use { zf ->
            assertNull("a dead dump must not be embedded", zf.getEntry("diagnostics-dump.txt"))
            val moduleLog = zf.getInputStream(zf.getEntry("module-log.txt"))
                .bufferedReader().readText()
            assertFalse("stored dump must be gone: $moduleLog", moduleLog.contains("DUMP-MARKER"))
            assertFalse("stored status must be gone: $moduleLog", moduleLog.contains("STATUS-MARKER"))
        }
    }

    @Test
    fun `the shared evidence gate keeps same-build status and dump out of the zip`() {
        val context = RuntimeEnvironment.getApplication()
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: ${BuildConfig.VERSION_NAME} (code 1, debug=false)\n" +
            "DUMP-MARKER\n"
        val evidence = reportEvidence(
            status = "STATUS-MARKER",
            dump = dump,
            dumpAt = 123L,
            evidenceLive = false
        )
        assertEquals("", evidence.status)
        assertEquals("", evidence.dump)
        assertEquals(0L, evidence.dumpAt)

        val file = writeReportZip(
            context,
            report = "diag",
            logs = "logs",
            status = evidence.status,
            dump = evidence.dump,
            dumpAt = evidence.dumpAt,
            evidenceLive = false
        )
        assertNotNull(file)
        ZipFile(file!!).use { zf ->
            assertNull("a dead dump must not be embedded", zf.getEntry("diagnostics-dump.txt"))
            val moduleLog = zf.getInputStream(zf.getEntry("module-log.txt"))
                .bufferedReader().readText()
            assertFalse("stored dump must be gone: $moduleLog", moduleLog.contains("DUMP-MARKER"))
            assertFalse("stored status must be gone: $moduleLog", moduleLog.contains("STATUS-MARKER"))
        }
    }

    @Test
    fun `a blank dump ignores a stored dump time and adds no dump entry`() {
        val context = RuntimeEnvironment.getApplication()
        DuoPrefs.writeDump(context, "")
        assertTrue("setup must leave a stored stamp", DuoPrefs.dumpAt(context) > 0L)
        val evidence = reportEvidence("STATUS-MARKER", "", 0L, evidenceLive = true)

        assertEquals("a blank dump has no dump time", 0L, evidenceDumpTime(context, evidence, true))

        val file = writeReportZip(
            context,
            report = "diag",
            logs = "logs",
            status = evidence.status,
            dump = evidence.dump,
            dumpAt = evidence.dumpAt
        )
        assertNotNull(file)
        ZipFile(file!!).use { zf ->
            assertNull("a blank dump must not add a dump entry", zf.getEntry("diagnostics-dump.txt"))
        }
    }

    @Test
    fun `current diagnostic dump is unchanged in the zip`() {
        val context = RuntimeEnvironment.getApplication()
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: ${BuildConfig.VERSION_NAME} (code 1, debug=false)\n" +
            "build:\n  x=1"
        val file = writeReportZip(
            context,
            report = "diag",
            logs = "logs",
            status = "status",
            dump = dump
        )
        assertNotNull(file)
        ZipFile(file).use { zf ->
            val text = zf.getInputStream(zf.getEntry("diagnostics-dump.txt"))
                .bufferedReader().readText()
            assertFalse(text.startsWith("[stale"))
            assertEquals(dump, text)
        }
    }
}
