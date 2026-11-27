package io.github.kvmy666.duostatusbar

import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId

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
    fun `the LSPosed scan filters to DuoSB without GNU alternation`() {
        val script = RootLogs.rootScript()
        // The unfiltered tail of the newest LSPosed log must be gone, so an unrelated
        // module's line cannot leak into the report.
        assertFalse("unfiltered LSPosed tail must be removed", script.contains("tail -n 120"))
        // Android toybox grep does not understand GNU `\|`; only `-E` alternation works.
        assertTrue(
            "toybox grep needs -E alternation",
            script.contains("grep -a -i -E 'DuoSB|duostatusbar'")
        )
        assertFalse("GNU \\| alternation is inert on toybox", script.contains("\\|"))
    }

    @Test
    fun `every per-file LSPosed tail is filtered to DuoSB`() {
        val script = RootLogs.rootScript()
        assertTrue("the per-file tail must report its match count", script.contains("matches="))
        val perFileTails = script.lines().filter {
            it.contains("tail -n 100") && it.contains("${'$'}f")
        }
        assertTrue("per-file tails are missing: $perFileTails", perFileTails.isNotEmpty())
        perFileTails.forEach { line ->
            assertTrue(
                "an unfiltered per-file tail can leak another module's log: $line",
                line.contains("grep -a -i -E 'DuoSB|duostatusbar'")
            )
        }
        assertFalse(
            "the raw per-file tail must be gone",
            script.contains("""tail -n 100 "${'$'}f" 2>&1""")
        )
    }

    @Test
    fun `the newest modules log scan is newest first and stops at the first DuoSB file`() {
        val script = RootLogs.rootScript()
        val newest = script.substringAfter("=== LSPosed newest modules log ===")
            .substringBefore("=== LSPosed log files ===")
        assertTrue("the scan must list modules_*.log", script.contains("modules_*.log"))
        assertTrue("the candidate count must be printed", script.contains("candidates="))
        assertTrue(
            "an empty candidate list must still print candidates=0",
            newest.contains("awk 'NF{n++} END{print n+0}'")
        )
        assertFalse(
            "grep -c exits 1 on zero matches and can abort a strict shell",
            newest.contains("grep -c .")
        )
        assertTrue("the scan must report what it listed", script.contains("scanned="))
        assertTrue("an empty scan must be explicit", script.contains("matchingDuoSB=0"))
        assertTrue("the scan must stop at the first matching file", script.contains("break"))
        assertTrue(
            "the scan must loop over the candidate list, not a hard-coded slice",
            script.contains("for f in ${'$'}CANDIDATES")
        )
        assertTrue("the candidate cap is 40, not the old 12", newest.contains("head -n 40"))
        assertFalse("the old 12-file cap must be gone", newest.contains("head -n 12"))
        assertTrue("the newest file wins", script.contains("ls -t"))
        assertTrue("matching output is bounded", script.contains("tail -n 400"))
        assertTrue(
            "toybox grep needs -E alternation",
            script.contains("grep -a -i -E 'DuoSB|duostatusbar'")
        )
        assertFalse("GNU \\| alternation is inert on toybox", script.contains("\\|"))
    }

    @Test
    fun `the newest modules log section is explicit when no file matches`() {
        val script = RootLogs.rootScript()
        val newest = script.substringAfter("=== LSPosed newest modules log ===")
            .substringBefore("=== LSPosed log files ===")
        val lines = newest.lines().map { it.trim() }
        val candidates = lines.indexOfFirst { it.startsWith("echo \"candidates=") }
        val scanned = lines.indexOfFirst { it.startsWith("echo \"scanned=") }
        val file = lines.indexOfFirst { it == "echo \"file=${'$'}FOUND\"" }
        val banner = lines.indexOfFirst { it == "echo '-- matching DuoSB --'" }
        val elseLine = lines.indexOfFirst { it == "else" }
        val noMatch = lines.indexOfFirst { it == "echo 'matchingDuoSB=0'" }
        assertTrue("candidates=N must be printed unconditionally", candidates >= 0)
        assertTrue("scanned=<files> must be printed unconditionally", scanned >= 0)
        assertTrue("file= must print even when empty", file >= 0)
        assertTrue("the banner must be guarded by a chosen file", banner > file)
        assertTrue("the no-match branch must follow the banner guard", elseLine > banner)
        assertTrue("the no-match case must be explicit", noMatch > elseLine)
        assertEquals(
            "only one banner may exist in the section",
            1,
            lines.count { it == "echo '-- matching DuoSB --'" }
        )
        assertTrue(
            "the empty file= line is printed before the no-match marker",
            file < noMatch
        )

        val body = """
            candidates=1
            scanned=/data/adb/lspd/log/modules_2026-11-27T00:00:49.760578.log
            file=
            matchingDuoSB=0
        """.trimIndent()
        val section = RootLogs.section(
            "=== LSPosed newest modules log ===\n$body\n=== safe mode ===",
            "LSPosed newest modules log"
        )
        assertTrue("candidates= must survive rendering: $section", section.contains("candidates=1"))
        assertTrue("scanned= must survive rendering: $section", section.contains("scanned="))
        assertTrue("matchingDuoSB=0 must be explicit: $section", section.contains("matchingDuoSB=0"))
        assertTrue("file= must stay, empty: $section", section.lines().any { it.trim() == "file=" })
        assertFalse(
            "a no-match section must never carry a bare banner",
            section.contains("-- matching DuoSB --")
        )
    }

    @Test
    fun `the newest log selector skips a newer file without the module line`() {
        val newest = "/data/adb/lspd/log/modules_2026-11-27T00:00:49.760578.log"
        val older = "/data/adb/lspd/log/modules_2026-11-24T21:46:27.000000.log"
        val texts = mapOf(
            newest to
                "[ 2026-11-27T00:00:50.000  1: 2: 3 I/LSPosedFramework ] another module loaded",
            older to
                "[ 2026-11-24T21:46:27.855 10266: 7525: 7525 I/LSPosedFramework ] " +
                "DuoSB | MainHook loaded into com.android.systemui"
        )
        assertEquals(
            "the newest file without a DuoSB line must not win",
            older,
            RootLogs.newestLogWithDuoB(listOf(newest, older)) { texts.getValue(it) }
        )
    }

    @Test
    fun `the newest log selector matches either module name ignoring case`() {
        val first = "/data/adb/lspd/log/modules_1.log"
        val second = "/data/adb/lspd/log/modules_2.log"
        assertEquals(
            first,
            RootLogs.newestLogWithDuoB(listOf(first, second)) {
                "io.github.kvmy666.duostatusbar loaded"
            }
        )
        assertEquals(
            first,
            RootLogs.newestLogWithDuoB(listOf(first, second)) { "duosb | lower case" }
        )
        assertEquals(
            "all empty logs must not select a file",
            null,
            RootLogs.newestLogWithDuoB(listOf(first, second)) { "another module" }
        )
    }

    @Test
    fun `an unreadable log is skipped by the newest log selector`() {
        val newest = "/data/adb/lspd/log/modules_new.log"
        val older = "/data/adb/lspd/log/modules_old.log"
        val picked = RootLogs.newestLogWithDuoB(listOf(newest, older)) {
            if (it == newest) throw IOException("permission denied") else "DuoSB | MainHook loaded"
        }
        assertEquals("an unreadable log is not evidence", older, picked)
    }

    @Test
    fun `the after restart report cannot leak KernelSU logs or unrelated modules`() {
        val script = RootLogs.rootScript()
        assertFalse("KernelSU shell log directory leaked", script.contains("/data/adb/ksu/log"))
        assertFalse("KernelSU sulog leaked", script.contains("sulog"))
        assertFalse("KernelSU logcat.log leaked", script.contains("logcat.log"))
        assertFalse("generic adb log sweep returned", script.contains("/data/adb/*/log"))
        assertFalse("nested generic adb log sweep returned", script.contains("/data/adb/*/*/log"))

        val moduleProp = script.substringAfter("echo '=== module.prop ==='")
            .substringBefore("echo '=== /data/adb layout ==='")
        assertTrue(
            "the module.prop block must be in the script: $moduleProp",
            moduleProp.contains("grep -a -m1 '^id='")
        )
        assertTrue("only framework ids may pass the case filter", moduleProp.contains("*) continue ;;"))
        assertFalse("the whole module.prop must never be echoed", moduleProp.contains("cat \"\$f\""))

        val afterRestart = """
            dir=/data/adb/ksu/log
            file=/data/adb/ksu/log/sulog-2026-11-27.log
            file=/data/adb/ksu/log/logcat.log
            dir=/data/adb/lspd/log
            file=/data/adb/lspd/log/modules_2026-11-27T00:00:49.760578.log
        """.trimIndent()
        assertEquals(
            "only the allow-listed LSPosed log survives",
            listOf("/data/adb/lspd/log/modules_2026-11-27T00:00:49.760578.log"),
            RootLogs.discoverLogFiles(afterRestart)
        )
        listOf("AppleFonts", "com.hevy-morphe", "hybrid_mount", "bindhosts", "tricky_store")
            .forEach {
                assertFalse(
                    "$it is a user module, not framework evidence",
                    RootLogs.isFrameworkModuleId(it)
                )
            }
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
            "LSPosed newest modules log",
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
            dir=/data/adb/ksu/log
            file=/data/adb/ksu/log/logcat.log
            file=/data/adb/ksu/log/sulog-2026-11-27.log
        """.trimIndent()
        val ordered = RootLogs.discoverLogFiles(listing)
        assertEquals(4, ordered.size)
        assertTrue("newest modules log must be first: $ordered", ordered.first().contains("2026-11-25"))
        assertTrue("the plain lspd.log must sort last: $ordered", ordered.last().endsWith("/lspd.log"))
        assertFalse("unrelated files must be dropped", ordered.any { it.endsWith("notes.txt") })
        assertFalse("KernelSU logs must never be read", ordered.any { it.contains("/ksu/") })
    }

    @Test
    fun `safe mode counter and matching lines come from the real LSPosed wording`() {
        val text = """
            safeLogFiles=1
            [ 2026-11-24T21:46:45.219        0:  4269:  7347 W/LSPosedService-SafeMode ] System UI crashed 1 time(s), still below the threshold
            [ 2026-11-25T00:18:25.433        0:  4269: 18777 W/LSPosedService-SafeMode ] System UI crashed 2 time(s), still below the threshold
            [ 2026-11-25T00:19:06.527        0:  4269:  7347 E/LSPosedService-SafeMode ] System UI crashed too many times, stop all modules and enter safe mode ...
            unrelated line
        """.trimIndent()
        val report = RootLogs.parseSafeMode(text)
        assertEquals(2, report.counter)
        assertTrue(report.entered)
        assertEquals(3, report.lines.size)
        assertTrue(report.lines.last().contains("stop all modules"))
        assertEquals(1, report.logFiles)
        assertTrue(report.readable)
        val none = RootLogs.parseSafeMode("nothing here")
        assertEquals(null, none.counter)
        assertFalse(none.entered)
        assertTrue(none.lines.isEmpty())
        assertFalse("no readable verbose log is not a confirmed zero", none.readable)
    }

    @Test
    fun `safe mode lines from an older boot are history not this boot`() {
        val boot = OffsetDateTime.parse("2026-11-27T00:00:00+03:00").toInstant().toEpochMilli()
        val text = """
            safeLogFiles=2
            [ 2026-11-24T21:46:45.219        0:  4269:  7347 W/LSPosedService-SafeMode ] System UI crashed 1 time(s), still below the threshold
            [ 2026-11-25T00:18:25.433        0:  4269: 18777 W/LSPosedService-SafeMode ] System UI crashed 2 time(s), still below the threshold
            [ 2026-11-27T00:05:00.000        0:  4269:  7347 W/LSPosedService-SafeMode ] System UI crashed 1 time(s), still below the threshold
        """.trimIndent()
        val report = RootLogs.parseSafeMode(text, boot, ZoneId.of("+03:00"))
        assertEquals("only the current boot counts", 1, report.counter)
        assertEquals("older boots are kept as history", 2, report.history.size)
        assertEquals(1, report.lines.size)
        assertTrue(report.lines.single().contains("2026-11-27"))
        assertFalse(report.entered)
    }

    @Test
    fun `an unparseable safe mode timestamp is history when this boot is known`() {
        val boot = OffsetDateTime.parse("2026-11-27T00:00:00+03:00").toInstant().toEpochMilli()
        val text = """
            safeLogFiles=1
            safeReadableFiles=1
            /data/adb/lspd/log/verbose_old.log:LSPosedService-SafeMode System UI crashed 4 time(s), still below the threshold
        """.trimIndent()
        val report = RootLogs.parseSafeMode(text, boot, ZoneId.of("+03:00"))
        assertEquals(
            "a matching line with no parseable timestamp cannot count for this boot",
            null,
            report.counter
        )
        assertEquals(1, report.history.size)
        assertTrue(report.lines.isEmpty())
        assertFalse(report.entered)
    }

    @Test
    fun `an unreadable safe mode log is unknown not zero`() {
        val report = RootLogs.parseSafeMode("safeLogFiles=3\nsafeReadableFiles=0\n")
        assertEquals("listed files stay informational", 3, report.logFiles)
        assertEquals("only readable files count as evidence", 0, report.readableFiles)
        assertFalse("listed but unreadable is not a readable log", report.readable)
        assertEquals("unknown", RootLogs.safeModeLabel(report))
        assertEquals(null, report.counter)
        assertEquals(
            "a read log is a real readable file",
            1,
            RootLogs.parseSafeMode("safeLogFiles=1\nsafeReadableFiles=1\n").readableFiles
        )

        val unreadable = RootLogs.privilegedSections(
            "=== safe mode ===\nsafeLogFiles=3\nsafeReadableFiles=0\n",
            "",
            null
        ).first { it.title == "safe mode" }.text
        assertTrue("an unreadable log must not report a zero counter: $unreadable", unreadable.contains("counter=unknown"))
        assertTrue(unreadable.contains("safeMode=unknown"))

        val readable = RootLogs.privilegedSections(
            "=== safe mode ===\nsafeLogFiles=1\nsafeReadableFiles=1\n",
            "",
            null
        ).first { it.title == "safe mode" }.text
        assertTrue("a read log with no crashes is a real zero: $readable", readable.contains("counter=0"))
        assertTrue(readable.contains("safeMode=no"))
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
    fun `the breadcrumb script is app-only and never reads the dropped data-dir file`() {
        val script = RootLogs.rootScript()
        assertTrue(
            "the app-only source line is missing: $script",
            script.contains(
                "source=app-only (Settings.Global; SystemUI data dir is not readable from root/Shizuku: SELinux)"
            )
        )
        assertTrue(
            "the settings read is missing",
            script.contains("settings get global duo_statusbar_breadcrumb 2>&1")
        )
        assertFalse("the undeliverable file must be gone", script.contains("duo-breadcrumb.txt"))
        assertFalse(
            "the SystemUI data dir must not be read",
            script.contains("/data/user_de/0/com.android.systemui/files")
        )
    }

    @Test
    fun `the breadcrumb section is present with a value and empty when unset`() {
        val value = "1795734715683|process=com.android.systemui|pid=25754|step=start"
        val present = RootLogs.privilegedSections(
            """
                === breadcrumb ===
                source=app-only (Settings.Global; SystemUI data dir is not readable from root/Shizuku: SELinux)
                $value
            """.trimIndent(),
            "",
            null
        ).first { it.title == "breadcrumb" }
        assertEquals(SectionState.PRESENT, present.state)
        assertTrue("the value must be kept: ${present.text}", present.text.contains(value))
        assertTrue(present.text.contains("source=app-only"))

        for (unsetText in listOf("null", "")) {
            val body = """
                === breadcrumb ===
                source=app-only (Settings.Global; SystemUI data dir is not readable from root/Shizuku: SELinux)
                $unsetText
            """.trimIndent()
            val unset = RootLogs.privilegedSections(body, "", null).first { it.title == "breadcrumb" }
            assertEquals("an unset breadcrumb is empty, not unreadable: $unset", SectionState.EMPTY, unset.state)
            assertTrue("an empty breadcrumb needs a plain reason", unset.reason.isNotBlank())
        }
    }

    @Test
    fun `missing routes say exactly why and an unreadable death count is not zero`() {
        assertTrue(RootLogs.rootUnavailableReason(false, true).contains("no root"))
        assertTrue(RootLogs.rootUnavailableReason(true, false).contains("no root shell"))
        assertTrue(RootLogs.shizukuUnavailableReason(false, true).contains("not running"))
        assertTrue(RootLogs.shizukuUnavailableReason(true, false).contains("not granted"))
        assertEquals("deaths=3", RootLogs.deathSummary(3, null))
        assertEquals("deaths=0", RootLogs.deathSummary(0, null))
        assertEquals("deaths=unknown", RootLogs.deathSummary(null, null))
        assertFalse("deaths=records must be gone", RootLogs.deathSummary(null, null).contains("records"))
        assertTrue(
            "an unreadable capture must not report zero deaths",
            RootLogs.deathSummary(null, "root shell timed out").startsWith("deaths=unreadable:")
        )
    }

    @Test
    fun `any log in a discovered framework directory is kept newest first`() {
        val listing = """
            dir=/data/adb/vector/log
            file=/data/adb/vector/log/events.log
            file=/data/adb/vector/log/vector_2026-11-25_10-00-00.log
            file=/data/adb/vector/log/notes.txt
            dir=/data/adb/lspatch/log
            file=/data/adb/lspatch/log/patch_2026-11-24_10-00-00.log
            dir=/data/adb/ksu/log
            file=/data/adb/ksu/log/logcat.log
        """.trimIndent()
        val ordered = RootLogs.discoverLogFiles(listing)
        assertEquals(3, ordered.size)
        assertTrue("newest timestamped log first: $ordered", ordered.first().contains("2026-11-25"))
        assertTrue("the second newest log next: $ordered", ordered[1].contains("2026-11-24"))
        assertTrue("a log without a timestamp stays last: $ordered", ordered.last().endsWith("events.log"))
        assertFalse("a non-log file is dropped", ordered.any { it.endsWith("notes.txt") })
        assertFalse("KernelSU logs stay out", ordered.any { it.contains("/ksu/") })
    }

    @Test
    fun `the framework log section tails only the allow-listed directories`() {
        val script = RootLogs.rootScript()
        assertTrue("per-directory .log glob missing", script.contains("/*.log"))
        assertTrue("safe mode reads verbose_*.log", script.contains("verbose_*.log"))
        assertTrue("the safe mode scan must be bounded to 8 files", script.contains("head -n 8"))
        assertTrue("safe mode reports how many logs were read", script.contains("safeLogFiles="))
        assertTrue(
            "safe mode greps only the LSPosed tag",
            script.contains("grep -a 'LSPosedService-SafeMode'")
        )
        assertFalse("no wildcard adb log scan", script.contains("/data/adb/*/log"))
        assertFalse("no nested wildcard adb log scan", script.contains("/data/adb/*/*/log"))
        assertFalse("KernelSU logs are never a source", script.contains("/data/adb/ksu/log"))
    }

    @Test
    fun `the capture reads only allow-listed framework log directories`() {
        val script = RootLogs.rootScript()
        RootLogs.FRAMEWORK_LOG_DIRS.forEach {
            assertTrue("missing allow-listed dir $it", script.contains(it))
        }
        assertEquals(
            "only the framework log dirs are allow-listed",
            listOf("/data/adb/lspd/log", "/data/adb/vector/log", "/data/adb/lspatch/log"),
            RootLogs.FRAMEWORK_LOG_DIRS
        )
    }

    @Test
    fun `only framework module ids are kept in the module prop section`() {
        listOf(
            "zygisk_lsposed",
            "lsposed",
            "vector",
            "zygisksu",
            "zygisk-next",
            "rezygisk",
            "lspatch",
            "zygisk_lspatch",
            "my-lsposed-fork"
        ).forEach { assertTrue(it, RootLogs.isFrameworkModuleId(it)) }
        listOf(
            "AppleFonts",
            "instagram",
            "com.instagram.android",
            "com.instagram.android-morphe",
            "com.hevy-morphe",
            "hybrid_mount",
            "tricky_store",
            ""
        ).forEach { assertFalse(it, RootLogs.isFrameworkModuleId(it)) }
        val script = RootLogs.rootScript()
        assertTrue("the script must filter by id", script.contains("*lsposed*"))
        assertTrue(script.contains("zygisksu"))
        assertTrue(script.contains("vector"))
        assertTrue(script.contains("lspatch"))
        assertTrue("only the prop header lines are printed", script.contains("head -n 4"))
    }

    @Test
    fun `module prop ids strip CRLF quotes and whitespace`() {
        val script = RootLogs.rootScript()
        assertTrue("CRLF must be stripped before the id case", script.contains("tr -d '\\r'"))
        assertTrue("surrounding quotes must be stripped", script.contains("tr -d '\"'"))
        assertTrue(RootLogs.isFrameworkModuleId("\"vector\""))
        assertTrue(RootLogs.isFrameworkModuleId("vector\r"))
        assertTrue(RootLogs.isFrameworkModuleId("  \"zygisk_lsposed\"\r  "))
        assertTrue(RootLogs.isFrameworkModuleId("my-lsposed-fork"))
        assertFalse(RootLogs.isFrameworkModuleId("\"hybrid_mount\""))
        listOf("lsposed", "vector", "zygisksu", "zygisk-next", "rezygisk", "lspatch", "zygisk_lspatch")
            .forEach { assertTrue("framework id $it must stay", RootLogs.isFrameworkModuleId(it)) }
    }

    @Test
    fun `crash entries older than the boot and the app update are history`() {
        val boot = OffsetDateTime.parse("2026-11-27T00:00:00+03:00").toInstant().toEpochMilli()
        val install = OffsetDateTime.parse("2026-11-27T00:15:00+03:00").toInstant().toEpochMilli()
        val threshold = RootLogs.crashThresholdMillis(boot, install)
        assertEquals("the newer app install is the threshold", install, threshold)

        val sections = listOf(
            CaptureSection(
                "crash records",
                SectionState.PRESENT,
                "",
                """
                    -- dropbox system_app_crash --
                    2026-11-24 16:50:39 system_app_crash (text, 5068 bytes)
                    Process: com.android.systemui
                    Timestamp: 2026-11-24 16:50:39.947+0300
                    --
                    2026-11-26 03:47:23 system_app_crash (text, 2914 bytes)
                    Process: com.android.systemui
                    Timestamp: 2026-11-26 03:47:23.812+0300
                    2026-11-27 01:00:00 system_app_crash (text, 100 bytes)
                    Process: com.android.systemui
                    Timestamp: 2026-11-27 01:00:00.123+0300
                """.trimIndent()
            ),
            CaptureSection(
                "tombstones",
                SectionState.PRESENT,
                "",
                """
                    tombstone=/data/tombstones/tombstone_11
                    Process name is com.android.systemui, uid is 10266, not key_process
                    Timestamp: 2026-11-20 22:53:42.585827020+0300
                """.trimIndent()
            ),
            CaptureSection("logcat crash buffer", SectionState.EMPTY, "no output", "")
        )
        val scoped = RootLogs.scopeCrashSections(sections, threshold, ZoneId.of("+03:00"))
        val records = scoped.sections.first { it.title == "crash records" }
        assertEquals(SectionState.PRESENT, records.state)
        assertTrue("the current dropbox entry stays", records.text.contains("2026-11-27 01:00:00"))
        assertFalse("the 11-24 entry is history", records.text.contains("2026-11-24 16:50:39"))
        assertFalse("the 11-26 entry is history", records.text.contains("2026-11-26 03:47:23"))
        val tombstone = scoped.sections.first { it.title == "tombstones" }
        assertEquals(SectionState.EMPTY, tombstone.state)
        assertTrue("an emptied section says why", tombstone.reason.isNotBlank())
        assertEquals(1, scoped.currentCount)
        assertEquals(3, scoped.historyCount)
        assertEquals("crash history (older boots)", scoped.history.title)
        assertEquals(SectionState.PRESENT, scoped.history.state)
        assertTrue(scoped.history.text.contains("2026-11-24 16:50:39"))
        assertTrue(scoped.history.text.contains("2026-11-26 03:47:23"))
        assertTrue(scoped.history.text.contains("2026-11-20 22:53:42"))
        assertFalse(
            "the current entry must not be history",
            scoped.history.text.contains("2026-11-27 01:00:00")
        )
    }

    @Test
    fun `a current tombstone stays current when its timestamp is echoed first`() {
        val boot = OffsetDateTime.parse("2026-11-27T00:00:00+03:00").toInstant().toEpochMilli()
        val text = """
            tombstone=/data/tombstones/tombstone_12
            Timestamp: 2026-11-27 00:30:00.123456789+0300
            Process name is com.android.systemui, uid is 10266, not key_process
            signal 6 (SIGABRT)
        """.trimIndent()
        val scoped = RootLogs.scopeCrashText(text, boot, ZoneId.of("+03:00"))
        assertEquals(1, scoped.currentCount)
        assertEquals(0, scoped.historyCount)
        assertTrue(scoped.current.contains("tombstone_12"))
        assertFalse(scoped.history.contains("tombstone_12"))
    }

    @Test
    fun `a current dropbox entry survives a wider header`() {
        val boot = OffsetDateTime.parse("2026-11-27T00:00:00+03:00").toInstant().toEpochMilli()
        val text = """
            2026-11-27 01:00:00 system_app_crash (text, 100 bytes)
            SystemUptimeMs: 1000
            Process: com.android.systemui
            PID: 1
            UID: 10266
            Frozen: false
            Flags: 0x1
            Package: com.android.systemui v169912 (16.99.12)
            Foreground: No
            Process-Runtime: 10
            Timestamp: 2026-11-27 01:00:00.123+0300
        """.trimIndent()
        val scoped = RootLogs.scopeCrashText(text, boot, ZoneId.of("+03:00"))
        assertEquals(1, scoped.currentCount)
        assertEquals(0, scoped.historyCount)
    }

    @Test
    fun `tombstones carry their timestamp and dropbox keeps a wide window`() {
        val script = RootLogs.rootScript()
        assertTrue(
            "a tombstone Timestamp outside the match window must be echoed",
            script.contains("grep -a -m1 'Timestamp:'")
        )
        val dropbox = script.lines().filter { it.contains("dumpsys dropbox --print") }
        assertEquals("all three dropbox sources must be widened", 3, dropbox.size)
        dropbox.forEach { line ->
            assertTrue("the entry header must stay adjacent: $line", line.contains("-B8 -A10"))
            assertFalse("the narrow window is what demoted a current crash: $line", line.contains("-B2 -A6"))
        }
    }

    @Test
    fun `the module scope section parses the sqlite row`() {
        val present = CaptureSection(
            "LSPosed config (enabled / scope)",
            SectionState.PRESENT,
            "",
            """
                db=/data/adb/lspd/config/modules_config.db
                sqlite=/system/bin/sqlite3
                module|enabled|scope
                io.github.kvmy666.duostatusbar|1|com.android.systemui
            """.trimIndent()
        )
        val text = RootLogs.withModuleScope(present).text
        assertTrue("explicit enabled line missing: $text", text.contains("enabled=true"))
        assertTrue(
            "explicit scope line missing: $text",
            text.contains("scope=com.android.systemui")
        )
        assertFalse("the old not-parsed wording must be gone", text.contains("not parsed"))
        assertTrue("the raw query must stay", text.contains("io.github.kvmy666.duostatusbar|1|"))

        val disabled = present.copy(
            text = """
                db=/data/adb/lspd/config/modules_config.db
                sqlite=/system/bin/sqlite3
                module|enabled|scope
                io.github.kvmy666.duostatusbar|0|com.android.settings
            """.trimIndent()
        )
        val disabledText = RootLogs.withModuleScope(disabled).text
        assertTrue("enabled=0 must become false: $disabledText", disabledText.contains("enabled=false"))
        assertTrue(disabledText.contains("scope=com.android.settings"))
    }

    @Test
    fun `the module scope section is unknown without a sqlite query`() {
        val absent = CaptureSection(
            "LSPosed config (enabled / scope)",
            SectionState.PRESENT,
            "",
            "db=/data/adb/lspd/config/modules_config.db\nsqlite3=absent"
        )
        val absentText = RootLogs.withModuleScope(absent).text
        assertTrue(
            "plain unknown line missing: $absentText",
            absentText.contains("enabled=unknown scope=unknown (modules_config.db not queried)")
        )
        assertFalse("no enabled value may be invented", absentText.contains("enabled=true"))
        assertFalse("no binary excerpt claim", absentText.contains("see db excerpt"))

        val error = CaptureSection(
            "LSPosed config (enabled / scope)",
            SectionState.UNREADABLE,
            "Permission denied",
            "db=/data/adb/lspd/config/modules_config.db\n" +
                "sqlite=/system/bin/sqlite3\n" +
                "Error: unable to open database: Permission denied"
        )
        val errorText = RootLogs.withModuleScope(error).text
        assertTrue(
            "an unreadable query must still state unknown: $errorText",
            errorText.contains("enabled=unknown scope=unknown (modules_config.db not queried)")
        )
        assertFalse("never claim not found from a binary grep", errorText.contains("not found"))
    }

    @Test
    fun `the config script distinguishes a missing db from a missing sqlite3`() {
        val script = RootLogs.rootScript()
        assertTrue(
            "a present sqlite3 with no DB must say so",
            script.contains("db not found; modules_config.db not queried")
        )
        assertTrue("a missing sqlite3 is its own state", script.contains("sqlite3=absent"))
        val missingDb = CaptureSection(
            "LSPosed config (enabled / scope)",
            SectionState.PRESENT,
            "",
            "db=unknown\nsqlite=/system/bin/sqlite3\ndb not found; modules_config.db not queried"
        )
        val text = RootLogs.withModuleScope(missingDb).text
        assertTrue(
            "enabled/scope must stay unknown: $text",
            text.contains("enabled=unknown scope=unknown (modules_config.db not queried)")
        )
        assertFalse("a missing DB is not enabled", text.contains("enabled=true"))
        assertFalse("a missing DB is not a missing binary", text.contains("sqlite3=absent"))
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
        assertTrue(script.contains("command -v sqlite3"))
        assertTrue(script.contains("-header -separator '|'"))
        assertTrue(script.contains("SELECT * FROM modules"))
        assertTrue(script.contains("org.lsposed.manager"))
        assertTrue(script.contains("module.prop"))
        assertTrue(script.contains("settings get global duo_statusbar_breadcrumb"))
        assertTrue(script.contains("source=app-only"))
        assertFalse(
            "the dropped SystemUI data-dir file must never be read",
            script.contains("/data/user_de/0/com.android.systemui/files/duo-breadcrumb.txt")
        )
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

    @Test
    fun `uptime comes from the monotonic clock and is numeric`() {
        val seconds = RootLogs.uptimeSeconds { 12_345_678L }
        assertEquals(12_345L, seconds)
        assertTrue("the report needs a numeric uptime", Regex("""^\d+s$""").matches("${seconds}s"))
        assertEquals(null, RootLogs.uptimeSeconds { -1L })
    }
}
