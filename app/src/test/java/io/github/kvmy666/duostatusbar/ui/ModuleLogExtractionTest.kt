package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.AutoReportManager
import io.github.kvmy666.duostatusbar.settings.ProblemDetector
import io.github.kvmy666.duostatusbar.settings.ProblemInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * The app process cannot see the module's log buffer. A report therefore has to take the module log out
 * of the dump the module pushed (it carries the marker), and only fall back to the app's own ring — with
 * an explicit label — when the dump has no such section.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleLogExtractionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val marker = "=== module log (from the module, no root needed) ==="

    @Before
    fun clearOldReports() {
        val dir = context.getExternalFilesDir("diagnostics")
        dir?.listFiles { f -> f.isFile && f.name.startsWith("duo-report-") }?.forEach { it.delete() }
        L.clearRecentForTest()
    }

    private fun readEntry(file: File, name: String): String =
        ZipFile(file).use { zf ->
            val entry = zf.getEntry(name)
            assertNotNull(name, entry)
            zf.getInputStream(entry).bufferedReader().readText()
        }

    private fun write(dump: String): File {
        val file = writeReportZip(
            context,
            report = "diagnostics body",
            logs = "capture body",
            status = "status body",
            dump = dump
        )
        assertNotNull("zip was not written", file)
        return file!!
    }

    @Test
    fun `module log entry comes from the dump section not the app ring`() {
        L.i("app-ring-only-line")
        val dump = "header\n$marker\nmodule-one\nmodule-two\n"
        val text = readEntry(write(dump), "module-log.txt")
        assertTrue("module lines missing: $text", text.contains("module-one"))
        assertTrue("module lines missing: $text", text.contains("module-two"))
        assertFalse("app ring leaked into module-log.txt: $text", text.contains("app-ring-only-line"))
    }

    @Test
    fun `a dump without the marker labels the app ring fallback`() {
        L.i("app-ring-only-line")
        val text = readEntry(write("header without a module section"), "module-log.txt")
        assertTrue(
            "fallback must be labelled: $text",
            text.startsWith("[app-process log; module log not present]")
        )
        assertTrue("fallback must still carry the app ring: $text", text.contains("app-ring-only-line"))
    }

    @Test
    fun `the last module section wins and is trimmed`() {
        val dump = "first\n$marker\nstale\nsecond\n$marker\nmodule-one\nmodule-two\n"
        assertEquals("module-one\nmodule-two", moduleLogFromDump(dump))
    }

    @Test
    fun `a dump without a module section yields null`() {
        assertNull(moduleLogFromDump("header only"))
        assertNull(moduleLogFromDump(""))
    }

    @Test
    fun `a blank module section yields null`() {
        assertNull(moduleLogFromDump("header\n$marker\n   \n"))
    }

    @Test
    fun `the auto collector detector sees the module log from the dump`() {
        val dump = "header\n$marker\nDuoHook install failed: wifi\n"
        val log = AutoReportManager.detectorModuleLog(dump, "app-ring-only")
        assertEquals("DuoHook install failed: wifi", log)
        val problems = ProblemDetector.detect(ProblemInputs(moduleLog = log, iosActive = true))
        assertTrue(
            "detector missed the module log from the dump: $problems",
            problems.any { it.type == "hook" && it.location == "hook" }
        )
    }
}
