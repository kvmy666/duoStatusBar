package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
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
import org.robolectric.Shadows.shadowOf
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

    // R16-A2 integration: the path the manual report screen now takes. A module loaded after this APK
    // is the app build, so its stale 2.1.1 dump is labelled AND the per-piece block uses live status.
    @Test
    fun `buildDiagnostics labels a stale dump with time and prints the live mode`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Robolectric reports 0 (unknown) for lastUpdateTime; a known install stamp is what proves the
        // loaded module started after this APK was installed.
        val updatedAt = 1_000L
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
            .lastUpdateTime = updatedAt
        Settings.Global.putLong(
            context.contentResolver, "duo_statusbar_last_load", updatedAt + 10_000L
        )
        Settings.Global.putLong(
            context.contentResolver, "duo_statusbar_heartbeat", System.currentTimeMillis()
        )
        val status = "process=com.android.systemui pid=25754 · owner=ios · stage=0 · " +
            "renderer=none · attached=false"
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.1.1 (code 111, debug=false)\n" +
            "=== module log (from the module, no root needed) ===\n" +
            "1795696331873 status -> app: stage=0 · renderer=none · attached=false\n"
        val settings = DuoSettings(enabled = false, iosMode = true)
        DuoPrefs.write(context, settings, DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, settings, DuoOrientation.LANDSCAPE)
        DuoPrefs.writeStatus(context, status)
        DuoPrefs.writeDump(context, dump)

        val report = buildDiagnostics(
            context = context,
            settings = settings,
            status = status,
            history = emptyList(),
            dump = dump,
            moduleLoadAt = 0L,
            dumpAt = 1795696331873L,
            captureText = ""
        )

        assertTrue(report, report.contains("mode: OK (iOS active)"))
        assertTrue(report, report.contains("per-piece facts not derived from it"))
        assertTrue(report, report.contains("dump: STALE diagnostic dump from 2.1.1 written "))
        assertFalse(report, report.contains("NEVER_LOADED"))
        assertFalse(report, report.contains("missing:"))
    }

    @Test
    fun `the full report puts the user message after the completeness header`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val report = buildFullReport(
            context = context,
            problem = "the ring does not draw",
            settings = DuoSettings(),
            status = "",
            history = emptyList(),
            dump = "",
            moduleLoadAt = 0L,
            logs = "capture body"
        )
        val diagnostics = report.substringBefore("===== device capture =====")
        val title = diagnostics.indexOf("Duo Status Bar diagnostics")
        val completeness = diagnostics.indexOf("report completeness:")
        val message = diagnostics.indexOf("user message:")
        val settings = diagnostics.indexOf("settings:")
        assertTrue("title missing: $diagnostics", title >= 0)
        assertTrue(
            "completeness must follow the title: $diagnostics",
            completeness > title
        )
        assertTrue(
            "user message must follow the completeness header: $diagnostics",
            message > completeness
        )
        assertTrue("settings must follow the user message: $diagnostics", settings > message)
        assertTrue("message body missing", diagnostics.contains("the ring does not draw"))
    }

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
    fun `manifest carries the optional completeness map`() {
        val dest = File(tmp.root, "completeness.zip")
        DuoReport.writeZip(
            dest,
            inputs().copy(
                completeness = mapOf(
                    "module status" to "present reason=reported",
                    "crash records" to "unreadable reason=Permission denied"
                )
            )
        )
        ZipFile(dest).use { zf ->
            val m = JSONObject(
                zf.getInputStream(zf.getEntry("manifest.json")).bufferedReader().readText()
            )
            val completeness = m.getJSONObject("completeness")
            assertEquals("present reason=reported", completeness.getString("module status"))
            assertEquals(
                "unreadable reason=Permission denied",
                completeness.getString("crash records")
            )
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

    @Test
    fun labelStaleDumpPrefixesMismatchedVersion() {
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 1.4.2 (code 23, debug=false)\n" +
            "build:\n  x=1"
        val labelled = labelStaleDump(dump, "2.0.2")
        assertTrue(labelled.startsWith("[stale diagnostic dump from 1.4.2"))
        assertTrue("the dump itself must be kept", labelled.contains(dump))
    }

    // R16-A2: the dump carries the module-log epochs; the last one is when the module wrote the dump.
    @Test
    fun dumpWrittenAtUsesTheLastEpochInTheModuleLogSection() {
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.1.1 (code 111, debug=false)\n" +
            "=== module log (from the module, no root needed) ===\n" +
            "1795654045014 MainHook loaded\n" +
            "1795696331873 status -> app: owner=ios\n"
        assertEquals(1795696331873L, dumpWrittenAt(dump))
    }

    @Test
    fun dumpWrittenAtIgnoresEpochsOutsideTheModuleLogSection() {
        val dump = "1795696331873 a dump header that happens to start with an epoch\n" +
            "=== module log (from the module, no root needed) ===\n" +
            "1795654045014 MainHook loaded\n"
        assertEquals(1795654045014L, dumpWrittenAt(dump))
        assertNull(dumpWrittenAt("1795696331873 but no module log section\n"))
    }

    // R16-A2: the label must name both builds and when the stale dump was written.
    @Test
    fun labelStaleDumpWithTimeNamesTheBuildAndTheWriteTime() {
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.1.1 (code 111, debug=false)\n" +
            "=== module log (from the module, no root needed) ===\n" +
            "1795696331873 status -> app\n"
        // The label is local time, as a user reads it; the epoch is 2026-11-26. Pin the expected text
        // with the same formatter so the test is not tied to the machine's timezone.
        val written = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(1795696331873L))
        val labelled = labelStaleDump(dump, "2.1.3", dumpAt = 0L, now = 1795696400000L)
        assertTrue("the date must be the epoch's day: $labelled", written.startsWith("2026-11-26"))
        assertTrue(
            "stale label must name build and time: $labelled",
            labelled.startsWith(
                "[stale diagnostic dump from 2.1.1 written $written; app is 2.1.3; " +
                    "per-piece facts not derived from it]"
            )
        )
        assertTrue("the dump itself must be kept", labelled.contains(dump))
    }

    @Test
    fun labelStaleDumpKeepsTheOldTwoArgumentWording() {
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.1.1 (code 111, debug=false)\n"
        assertEquals(
            "[stale diagnostic dump from 2.1.1; app is 2.1.3; do not read it as this run]\n$dump",
            labelStaleDump(dump, "2.1.3")
        )
    }

    @Test
    fun labelStaleDumpLeavesMatchingVersionUnchanged() {
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.0.2 (code 30, debug=false)\n" +
            "build:\n  x=1"
        assertEquals(dump, labelStaleDump(dump, "2.0.2"))
    }

    @Test
    fun labelStaleDumpLeavesDumpWithoutVersionLineUnchanged() {
        val dump = "Duo Status Bar diagnostic dump\nbuild:\n  x=1"
        assertEquals(dump, labelStaleDump(dump, "2.0.2"))
    }

    @Test
    fun labelStaleDumpLeavesBlankUnchanged() {
        assertEquals("", labelStaleDump("", "2.0.2"))
    }
}
