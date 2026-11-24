package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The safe-mode fix: no settings path may restart System UI automatically. LSPosed counts every
 * System UI death as a crash and switches the module to safe mode after four per boot, so the only
 * restart left is the explicit, manually pressed module-health button (which carries a warning).
 */
class NoAutomaticRestartTest {

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

    private val runtimeFiles = listOf(
        "app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoShell.kt",
        "app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoSettingsSections.kt",
        "app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoHook.kt",
        "app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoIconHost.kt",
        "app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoStateMonitor.kt"
    )

    @Test
    fun `runtime and settings apply never restart System UI`() {
        for (file in runtimeFiles) {
            assertFalse(
                "$file must not contain restartSystemUi( - all settings changes are live",
                read(file).contains("restartSystemUi(")
            )
        }
    }

    @Test
    fun `the only restart call site is the manual module health button`() {
        val screen = read("app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoSettingsScreen.kt")
        val calls = Regex("""restartSystemUi\(\s*context,""").findAll(screen).toList()
        assertEquals("exactly one manual restart call site", 1, calls.size)
        assertTrue(
            "the one call site must be the module-health button",
            screen.contains("restartSystemUi(context, \"module-health\")")
        )
        // The old automatic/design/size restart reasons must be gone.
        assertFalse(screen.contains("design-change"))
        assertFalse(screen.contains("stage-override-clear"))
        assertFalse(screen.contains("settings-restart"))
        assertTrue(
            "the helper itself stays in this file for the manual button",
            screen.contains("internal fun restartSystemUi(context: Context, reason: String)")
        )
    }

    @Test
    fun `clearing the stage override re-reads settings instead of restarting`() {
        val screen = read("app/src/main/java/io/github/kvmy666/duostatusbar/ui/DuoSettingsScreen.kt")
        val overrideBlock = screen
            .substringAfter("val override = stageOverride")
            .substringBefore("if (showCustomization)")
        assertFalse(
            "clearing the override must not restart System UI",
            overrideBlock.contains("restartSystemUi")
        )
        assertTrue(
            "the module must be told to re-read its settings",
            overrideBlock.contains("DuoPrefs.ACTION_SETTINGS_CHANGED")
        )
        assertTrue(
            "the signed bridge must push the current row too",
            overrideBlock.contains("SettingsBridge.push(context)")
        )
    }
}
