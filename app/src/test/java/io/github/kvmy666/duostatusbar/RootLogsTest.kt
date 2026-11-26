package io.github.kvmy666.duostatusbar

import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * FR-03/NFR-06: a root shell that is named but does not exist is why locked-bootloader reports came
 * back empty. The discovery must always yield something executable-looking, or the legacy `"su"`
 * lookup as the last resort — and the grant/parse rules must be decidable without a device.
 */
class RootLogsTest {

    @Test
    fun `the discovered root shell is non-blank and exists when it is an absolute path`() {
        val su = RootLogs.findSuBinary()
        assertTrue("must always name a command", su.isNotBlank())
        assertTrue("a chosen path must be real, 'su' is the PATH fallback", su == "su" || File(su).exists())
    }

    @Test
    fun `an existing absolute path is preferred over the PATH fallback`() {
        val picked = RootLogs.firstExisting(listOf("/a/su", "/b/su", "su")) { it == "/b/su" }
        assertEquals("/b/su", picked)
    }

    @Test
    fun `the first existing absolute path in order wins`() {
        val picked = RootLogs.firstExisting(listOf("/a/su", "/b/su", "su")) { it == "/a/su" || it == "/b/su" }
        assertEquals("/a/su", picked)
    }

    @Test
    fun `no existing absolute path falls back to plain su`() {
        assertEquals("su", RootLogs.firstExisting(listOf("/a/su", "su")) { false })
    }

    @Test
    fun `the su sentinel is never treated as an absolute candidate`() {
        // Even if something claims "su" exists as a file, it must stay the PATH fallback, not a path.
        assertEquals("su", RootLogs.firstExisting(listOf("su")) { true })
    }

    @Test
    fun `looksRooted accepts the id output of a root shell`() {
        assertTrue(RootLogs.looksRooted("uid=0(root) gid=0(root) context=u:r:magisk:s0"))
    }

    @Test
    fun `looksRooted accepts a bare root uid`() {
        assertTrue(RootLogs.looksRooted("0"))
    }

    @Test
    fun `looksRooted rejects a shell uid`() {
        assertFalse(RootLogs.looksRooted("uid=2000(shell) gid=2000(shell)"))
    }

    @Test
    fun `looksRooted rejects a capture failure`() {
        assertFalse(RootLogs.looksRooted("root log collection failed: IOException: Cannot run program \"su\""))
    }

    @Test
    fun `section extracts only its own block`() {
        val text = "=== root framework ===\nmagisk\nksu\n=== kernel modules ===\nkernelsu 1\n"
        assertEquals("magisk\nksu", RootLogs.section(text, "root framework"))
        assertEquals("kernelsu 1", RootLogs.section(text, "kernel modules"))
    }

    @Test
    fun `section of a missing block is empty`() {
        assertEquals("", RootLogs.section("=== a ===\n1\n", "b"))
    }

    @Test
    fun `the LSPosed tail keeps only DuoSB lines`() {
        val script = RootLogs.rootScript()
        // The unfiltered tail of the newest LSPosed log must be gone, so an unrelated
        // module's line cannot leak into the report.
        assertFalse("unfiltered LSPosed tail must be removed", script.contains("tail -n 120"))
        // DuoSB lines must still be retained through the filtered grep.
        assertTrue("DuoSB lines must still be retained", script.contains("DuoSB"))
    }

    @Test
    fun `rootRestartFailure names the su binary and the errno`() {
        val t = IOException(
            "Cannot run program \"/system/bin/su\": error=13, Permission denied"
        )
        val reason = RootLogs.rootRestartFailure(t, "/system/bin/su")
        assertTrue(reason.contains("/system/bin/su"))
        assertTrue(reason.contains("IOException"))
        assertTrue(reason.contains("error=13"))
        assertTrue(reason.contains("Permission denied"))
    }

    @Test
    fun `restartTimeoutReason names the su binary and the timeout`() {
        val reason = RootLogs.restartTimeoutReason("/system/bin/su", 25L)
        assertTrue(reason.contains("su"))
        assertTrue(reason.contains("/system/bin/su"))
        assertTrue(reason.contains("25s"))
    }

    @Test
    fun `restartExitReason names the su binary and the exit code without output`() {
        assertEquals("su /system/bin/su: exit 1", RootLogs.restartExitReason("/system/bin/su", 1, "   "))
    }

    @Test
    fun `restartExitReason appends the first non-blank output line`() {
        val reason = RootLogs.restartExitReason("su", 1, "\n  permission denied  \nmore")
        assertEquals("su su: exit 1: permission denied", reason)
    }

    @Test
    fun `restart script never runs pkill or killall`() {
        val script = RootLogs.restartScript()
        // Blind matchers can hit the replacement SystemUI Android starts after the kill.
        assertFalse("pkill must be gone", script.contains("pkill"))
        assertFalse("killall must be gone", script.contains("killall"))
    }

    @Test
    fun `restart script reads the current pid and its age then kills only that pid`() {
        val script = RootLogs.restartScript()
        assertTrue(script.contains("pidof com.android.systemui"))
        assertTrue(script.contains("/proc/\$PID/stat"))
        assertTrue(script.contains("/proc/uptime"))
        assertTrue(script.contains("CLK_TCK"))
        assertTrue(script.contains("kill -9 \"\$PID\""))
        assertTrue("the age guard must exit before the kill", script.indexOf("exit 3") < script.indexOf("kill -9"))
    }

    @Test
    fun `restart script refuses a too-young SystemUI with exit 3 and the shared message`() {
        val script = RootLogs.restartScript()
        assertTrue(script.contains("exit 3"))
        assertTrue(script.contains(RootLogs.YOUNG_REFUSAL_PREFIX))
        assertTrue(script.contains("< 15s"))
        assertEquals("refused: systemui age 10s < 15s", RootLogs.youngRefusalMessage(10L))
        assertEquals(null, RootLogs.youngRefusalMessage(15L))
    }

    @Test
    fun `exit three or the marker marks a young refusal, an ordinary failure does not`() {
        assertTrue(RootLogs.isYoungRefusal(RootLogs.EXIT_YOUNG, ""))
        assertTrue(RootLogs.isYoungRefusal(1, "refused: systemui age 4s < 15s"))
        assertFalse(RootLogs.isYoungRefusal(1, "su: permission denied"))
        assertFalse(RootLogs.isYoungRefusal(0, ""))
    }

    /** Reads the settings screen from the test working directory (the app module) or its parents. */
    private fun settingsScreenSource(): String {
        val rel = "src/main/java/io/github/kvmy666/duostatusbar/ui/DuoSettingsScreen.kt"
        val file = listOf(File(rel), File("app/$rel"), File("../$rel")).firstOrNull { it.isFile }
            ?: throw AssertionError("DuoSettingsScreen.kt not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun `the Shizuku restart route uses the guarded script and no blind kill`() {
        val source = settingsScreenSource()
        // Drop line comments so the check reads code, not prose that names the old matchers.
        val code = source.lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        assertTrue("the Shizuku route must run RootLogs.restartScript()", code.contains("RootLogs.restartScript()"))
        assertFalse("pkill must be gone from the app restart path", code.contains("pkill"))
        assertFalse("killall must be gone from the app restart path", code.contains("killall"))
    }

    @Test
    fun `blockedYoung is off unless the outcome says so`() {
        assertFalse(RestartOutcome(true, "").blockedYoung)
        assertFalse(RestartOutcome(false, "su: denied").blockedYoung)
        assertTrue(RestartOutcome(false, "refused: systemui age 3s < 15s", blockedYoung = true).blockedYoung)
    }

    // --- R15 C1: root/Shizuku diagnostics and privacy capture. ---

    @Test
    fun `every privileged section is kept with an explicit skip reason when nothing elevated ran`() {
        val sections = RootLogs.privilegedSections(
            "",
            "no root: root access was not granted; Shizuku is not running",
            null
        )
        val titles = sections.map { it.title }
        listOf(
            "logcat (DuoSB)",
            "LSPosed log files",
            "safe mode",
            "LSPosed config (enabled / scope)",
            "framework manager",
            "module.prop",
            "/data/adb layout",
            "root framework",
            "kernel modules",
            "installed module package",
            "crash records",
            "tombstones",
            "logcat crash buffer",
            "breadcrumb",
            "build props"
        ).forEach { assertTrue("missing section $it", it in titles) }
        sections.forEach {
            assertEquals("${it.title} must be skipped", SectionState.SKIPPED, it.state)
            assertTrue("${it.title} needs a reason", it.reason.isNotBlank())
            assertTrue(
                "${it.title} reason must name the route: ${it.reason}",
                it.reason.contains("no root") || it.reason.contains("Shizuku")
            )
        }
    }

    @Test
    fun `a section with output is present and a denied command is unreadable`() {
        val evidence = """
            === logcat (DuoSB) ===
            DuoSB | hello
            === crash records ===
            -- dropbox system_app_crash --
            /system/bin/sh: dumpsys: Permission denied
        """.trimIndent()
        val sections = RootLogs.privilegedSections(evidence, "", null)
        val logcat = sections.first { it.title == "logcat (DuoSB)" }
        assertEquals(SectionState.PRESENT, logcat.state)
        assertEquals("DuoSB | hello", logcat.text)
        val crash = sections.first { it.title == "crash records" }
        assertEquals(SectionState.UNREADABLE, crash.state)
        assertTrue("reason must quote the denial: ${crash.reason}", crash.reason.contains("Permission denied"))
        val tombstones = sections.first { it.title == "tombstones" }
        assertEquals(SectionState.EMPTY, tombstones.state)
        assertTrue(tombstones.reason.isNotBlank())
    }

    @Test
    fun `a timed out root capture makes every privileged section unreadable`() {
        val reason = "root log collection failed: root shell (/system/bin/su) timed out after 25s"
        val sections = RootLogs.privilegedSections("", "", reason)
        assertTrue(sections.isNotEmpty())
        sections.forEach {
            assertEquals("${it.title} must not pretend to be empty", SectionState.UNREADABLE, it.state)
            assertTrue("${it.title} must quote the timeout", it.reason.contains("timed out"))
        }
    }

    @Test
    fun `log file discovery keeps known names newest first and drops unrelated files`() {
        val listing = """
            dir=/data/adb/lspd/log
            file=/data/adb/lspd/log/modules_2026-11-24_10-00-00.log
            file=/data/adb/lspd/log/modules_2026-11-25_10-00-00.log
            file=/data/adb/lspd/log/verbose_2026-11-23_09-00-00.log
            file=/data/adb/lspd/log/lspd.log
            file=/data/adb/lspd/log/notes.txt
        """.trimIndent()
        val ordered = RootLogs.discoverLogFiles(listing)
        assertEquals(4, ordered.size)
        assertTrue("newest modules log must be first: $ordered", ordered.first().contains("2026-11-25"))
        assertTrue("the plain lspd.log must sort last: $ordered", ordered.last().endsWith("/lspd.log"))
        assertFalse("unrelated files must be dropped", ordered.any { it.endsWith("notes.txt") })
    }

    @Test
    fun `safe mode counter and matching lines come from the real LSPosed wording`() {
        val text = """
            [ W/LSPosedService-SafeMode ] System UI crashed 1 time(s), still below the threshold
            [ W/LSPosedService-SafeMode ] System UI crashed 2 time(s), still below the threshold
            [ E/LSPosedService-SafeMode ] System UI crashed too many times, stop all modules and enter safe mode ...
            unrelated line
        """.trimIndent()
        val report = RootLogs.parseSafeMode(text)
        assertEquals(2, report.counter)
        assertTrue(report.entered)
        assertEquals(3, report.lines.size)
        assertTrue(report.lines.last().contains("stop all modules"))
        val none = RootLogs.parseSafeMode("nothing here")
        assertEquals(null, none.counter)
        assertFalse(none.entered)
        assertTrue(none.lines.isEmpty())
    }

    @Test
    fun `crash records keep only systemui lines`() {
        val text = """
            Process: com.android.systemui
            Process: com.example.other
            at com.android.systemui.statusbar.Foo(Unknown Source)
        """.trimIndent()
        val filtered = RootLogs.filterSystemUiCrashes(text)
        assertTrue(filtered.contains("Process: com.android.systemui"))
        assertTrue(filtered.contains("statusbar.Foo"))
        assertFalse("another app's crash must be dropped", filtered.contains("com.example.other"))
    }

    @Test
    fun `both SystemUI data-dir breadcrumb files are read`() {
        assertTrue(
            "user_de path missing",
            RootLogs.BREADCRUMB_FILES.contains("/data/user_de/0/com.android.systemui/files/duo-breadcrumb.txt")
        )
        assertTrue(
            "data path missing",
            RootLogs.BREADCRUMB_FILES.contains("/data/data/com.android.systemui/files/duo-breadcrumb.txt")
        )
    }

    @Test
    fun `missing routes say exactly why and an unreadable death count is not zero`() {
        assertTrue(RootLogs.rootUnavailableReason(false, true).contains("no root"))
        assertTrue(RootLogs.rootUnavailableReason(true, false).contains("no root shell"))
        assertTrue(RootLogs.shizukuUnavailableReason(false, true).contains("not running"))
        assertTrue(RootLogs.shizukuUnavailableReason(true, false).contains("not granted"))
        assertEquals("deaths=3", RootLogs.deathSummary(3, false, null))
        assertEquals("deaths=records", RootLogs.deathSummary(null, true, null))
        assertTrue(
            "an unreadable capture must not report zero deaths",
            RootLogs.deathSummary(null, false, "root shell timed out").startsWith("deaths=unreadable:")
        )
        assertEquals("deaths=0", RootLogs.deathSummary(null, false, null))
    }

    @Test
    fun `the expanded script discovers framework logs and crash records`() {
        val script = RootLogs.rootScript()
        assertTrue(script.contains("ls -la /data/adb"))
        assertTrue(script.contains("dumpsys dropbox --print system_app_crash"))
        assertTrue(script.contains("dumpsys dropbox --print system_app_native_crash"))
        assertTrue(script.contains("dumpsys dropbox --print SYSTEM_TOMBSTONE"))
        assertTrue(script.contains("/data/tombstones"))
        assertTrue(script.contains("logcat -d -b crash -t 500"))
        assertTrue(script.contains("modules_config.db"))
        assertTrue(script.contains("org.lsposed.manager"))
        assertTrue(script.contains("module.prop"))
        assertTrue(script.contains("settings get global duo_statusbar_breadcrumb"))
        assertTrue(script.contains("/data/user_de/0/com.android.systemui/files/duo-breadcrumb.txt"))
    }

    @Test
    fun `section bounding keeps the budget and marks the cut`() {
        val long = "x".repeat(20)
        val out = RootLogs.bounded(long, 10)
        assertTrue(out.startsWith("x".repeat(10)))
        assertTrue("the cut must be visible", out.contains("truncated"))
        assertEquals("short", RootLogs.bounded("short", 10))
    }

    @Test
    fun `uptime is parsed from the proc line`() {
        assertEquals(12345L, RootLogs.parseUptime("12345.67 999.00"))
        assertEquals(null, RootLogs.parseUptime(""))
        assertEquals(null, RootLogs.parseUptime("not a number"))
    }
}
