package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every app-facing status write must say *which process* wrote it. The attach-failure path shipped a
 * bare "attach failed after N attempts" line, which the app could not tell apart from a main-process or
 * side-process report. This is a source contract: a new `DuoSettingsClient.report(` call site that
 * forgets the identity prefix fails here instead of silently producing an anonymous status.
 */
class StatusWritesTest {

    private fun findProjectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        throw IllegalStateException(
            "project root (settings.gradle.kts) not found from ${System.getProperty("user.dir")}"
        )
    }

    private fun read(relative: String): String =
        File(findProjectRoot(), relative).readText(Charsets.UTF_8)

    private fun statusWrites(source: String): List<String> =
        source.lines().filter { it.contains("DuoSettingsClient.report(") }

    @Test
    fun `every status write in DuoHook carries the identity prefix`() {
        val source = read(
            "app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoHook.kt"
        )
        val calls = statusWrites(source)
        assertTrue("expected at least one DuoSettingsClient.report call in DuoHook", calls.isNotEmpty())
        val anonymous = calls.filterNot {
            it.contains("hookStatusPrefix") || it.contains("status")
        }
        assertTrue(
            "status write without process/pid/owner identity: " +
                anonymous.joinToString(" | ") { it.trim() },
            anonymous.isEmpty()
        )
    }

    @Test
    fun `the shared prefix helper is the one the status text builds on`() {
        val reporter = read(
            "app/src/main/java/io/github/kvmy666/duostatusbar/hook/HookReporter.kt"
        )
        assertTrue(
            "HookReporter must define hookStatusPrefix(owner)",
            reporter.contains("fun hookStatusPrefix(owner: String)")
        )
        assertTrue(
            "hookStatusText must build on hookStatusPrefix",
            reporter.contains("append(hookStatusPrefix(owner))")
        )
    }

    @Test
    fun `the status log line removes the identity before L adds it`() {
        val reporter = read(
            "app/src/main/java/io/github/kvmy666/duostatusbar/hook/HookReporter.kt"
        )
        assertTrue(
            "HookReporter must log the status without its own identity prefix",
            reporter.contains("status.removePrefix(hookStatusPrefix(owner.tag))")
        )
    }
}
