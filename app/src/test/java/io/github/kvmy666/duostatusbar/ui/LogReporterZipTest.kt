package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
