package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
