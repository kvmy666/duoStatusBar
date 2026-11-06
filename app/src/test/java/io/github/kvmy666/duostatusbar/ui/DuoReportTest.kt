package io.github.kvmy666.duostatusbar.ui

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoReportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun inputs(screenshot: ByteArray? = null) = DuoReportInputs(
        generatedAt = "2026-01-01T00:00:00Z",
        appVersion = "1.0.0",
        buildType = "debug",
        device = mapOf("model" to "Pixel", "sdk" to "35"),
        adapter = mapOf("id" to "aosp", "measured" to "true"),
        diagnostics = "diag-body",
        moduleLog = "module-body",
        logcat = "logcat-body",
        screenshotPng = screenshot
    )

    @Test
    fun writesRequiredEntriesWithoutScreenshot() {
        val dest = File(tmp.root, "report.zip")
        assertFalse(dest.exists())
        DuoReport.writeZip(dest, inputs())
        assertTrue(dest.exists())
        ZipFile(dest).use { zf ->
            assertNotNull(zf.getEntry("manifest.json"))
            assertNotNull(zf.getEntry("diagnostics.txt"))
            assertNotNull(zf.getEntry("module-log.txt"))
            assertNotNull(zf.getEntry("logcat.txt"))
            assertNull(zf.getEntry("screenshot.png"))
        }
    }

    @Test
    fun manifestHasSchemaAndKeys() {
        val dest = File(tmp.root, "manifest.zip")
        DuoReport.writeZip(dest, inputs())
        ZipFile(dest).use { zf ->
            val text = zf.getInputStream(zf.getEntry("manifest.json")).bufferedReader().readText()
            val m = JSONObject(text)
            assertEquals("duo-report/1", m.getString("schema"))
            assertEquals("2026-01-01T00:00:00Z", m.getString("generatedAt"))
            assertEquals("1.0.0", m.getString("appVersion"))
            assertEquals("debug", m.getString("buildType"))
            assertTrue(m.has("device"))
            assertTrue(m.has("adapter"))
            assertEquals("Pixel", m.getJSONObject("device").getString("model"))
            assertEquals("aosp", m.getJSONObject("adapter").getString("id"))
            val entries = m.getJSONArray("entries")
            assertTrue(entries.length() >= 3)
        }
    }

    @Test
    fun textEntryContentsRoundTrip() {
        val dest = File(tmp.root, "contents.zip")
        DuoReport.writeZip(dest, inputs())
        ZipFile(dest).use { zf ->
            fun read(name: String) = zf.getInputStream(zf.getEntry(name)).bufferedReader().readText()
            assertEquals("diag-body", read("diagnostics.txt"))
            assertEquals("module-body", read("module-log.txt"))
            assertEquals("logcat-body", read("logcat.txt"))
        }
    }

    @Test
    fun includesScreenshotWhenProvided() {
        val png = byteArrayOf(1, 2, 3, 4, 5)
        val dest = File(tmp.root, "shot.zip")
        DuoReport.writeZip(dest, inputs(png))
        ZipFile(dest).use { zf ->
            val e = zf.getEntry("screenshot.png")
            assertNotNull(e)
            val bytes = zf.getInputStream(e).readBytes()
            assertArrayEquals(png, bytes)
            val m = JSONObject(zf.getInputStream(zf.getEntry("manifest.json")).bufferedReader().readText())
            // entries array should mention screenshot
            val entries = m.getJSONArray("entries")
            var found = false
            for (i in 0 until entries.length()) if (entries.getString(i) == "screenshot.png") found = true
            assertTrue(found)
        }
    }

    @Test
    fun createsParentDirectories() {
        val dest = File(tmp.root, "nested/sub/dir/report.zip")
        DuoReport.writeZip(dest, inputs())
        assertTrue(dest.exists())
    }

    @Test
    fun refusesToOverwriteExistingDestination() {
        val dest = File(tmp.root, "existing.zip")
        dest.writeText("old")
        try {
            DuoReport.writeZip(dest, inputs())
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun pruneKeepsNewestN() {
        val dir = tmp.newFolder("prune")
        val f1 = File(dir, "duo-report-1.zip").apply { writeText("a"); setLastModified(1000L) }
        val f2 = File(dir, "duo-report-2.zip").apply { writeText("b"); setLastModified(2000L) }
        val f3 = File(dir, "duo-report-3.zip").apply { writeText("c"); setLastModified(3000L) }
        val other = File(dir, "other.txt").apply { writeText("x"); setLastModified(500L) }

        DuoReport.prune(dir, "duo-report-", 2)

        assertFalse(f1.exists())
        assertTrue(f2.exists())
        assertTrue(f3.exists())
        assertTrue(other.exists())
    }

    @Test
    fun pruneNoOpOnMissingDir() {
        val missing = File(tmp.root, "does-not-exist")
        DuoReport.prune(missing, "x", 1) // no throw
    }

    @Test
    fun pruneKeepZeroDeletesAllMatching() {
        val dir = tmp.newFolder("prune2")
        val f1 = File(dir, "r-1.zip").apply { writeText("a"); setLastModified(1000L) }
        val f2 = File(dir, "r-2.zip").apply { writeText("b"); setLastModified(2000L) }
        DuoReport.prune(dir, "r-", 0)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
    }
}
