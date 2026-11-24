package io.github.kvmy666.duostatusbar

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

    @Test
    fun `blockedYoung is off unless the outcome says so`() {
        assertFalse(RestartOutcome(true, "").blockedYoung)
        assertFalse(RestartOutcome(false, "su: denied").blockedYoung)
        assertTrue(RestartOutcome(false, "refused: systemui age 3s < 15s", blockedYoung = true).blockedYoung)
    }
}
