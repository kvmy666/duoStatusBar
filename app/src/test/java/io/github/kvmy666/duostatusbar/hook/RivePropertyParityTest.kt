package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parity guard: every property DuoBinder writes must exist in both Rive scenes, and the two scenes
 * must agree on their property set. Catches the class of bug where a property is written by
 * DuoBinder but missing from one Rive scene.
 */
class RivePropertyParityTest {

    private fun findProjectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        throw IllegalStateException("project root (settings.gradle.kts) not found from ${System.getProperty("user.dir")}")
    }

    private fun readText(root: File, relative: String): String =
        File(root, relative).readText(Charsets.UTF_8)

    private fun parseSceneProperties(text: String): Set<String> {
        // Actual RML format: <ViewModelPropertyNumber/String/Color/Boolean name="..." ... />
        val regex = Regex("""<ViewModelProperty\w+[^>]*?\bname="([^"]+)"""")
        return regex.findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun parseBinderProperties(source: String): Set<String> {
        val constMap = mutableMapOf<String, String>()
        Regex("""const val (\w+)\s*=\s*"([^"]*)"""").findAll(source).forEach {
            constMap[it.groupValues[1]] = it.groupValues[2]
        }

        fun parseArray(name: String): List<String> {
            val match = Regex("""val $name = arrayOf\((.*?)\n\s*\)""", RegexOption.DOT_MATCHES_ALL)
                .find(source) ?: throw IllegalStateException("'$name' array not found in DuoBinder.kt")
            val result = mutableListOf<String>()
            for (line in match.groupValues[1].lines()) {
                val trimmed = line.trim()
                if (!trimmed.contains(" to ")) continue
                val left = trimmed.substringBefore(" to ").trim().trimEnd(',')
                val resolved = if (left.startsWith("\"")) left.trim('"') else constMap[left]
                if (resolved != null && resolved != "DuoSB") result += resolved
            }
            return result
        }

        val names = mutableSetOf<String>()
        names += parseArray("numbers")
        names += parseArray("colors")

        // Properties written via the named constants (strings, booleans, etc.).
        // Exclude REVEAL_MS: it is a separate arrival trigger, not part of the snapshot count
        // (PROPERTY_COUNT counts snapshot writes only).
        for ((key, value) in constMap) {
            if (key == "TAG" || key == "REVEAL_MS") continue
            names += value
        }
        return names
    }

    @Test
    fun rivePropertyParity() {
        val root = findProjectRoot()
        val sceneText = readText(root, "rive/duo/scene.rml")
        val oldText = readText(root, "rive/duo/scene_old.rml")
        val binderSource = readText(root, "app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoBinder.kt")

        val scene = parseSceneProperties(sceneText)
        val old = parseSceneProperties(oldText)
        val binder = parseBinderProperties(binderSource)

        // 1. scene.rml == scene_old.rml property sets
        val missingInOld = scene - old
        val extraInOld = old - scene
        val sceneDiff = buildString {
            if (missingInOld.isNotEmpty()) append("missing in scene_old.rml: ${missingInOld.sorted()}\n")
            if (extraInOld.isNotEmpty()) append("extra in scene_old.rml: ${extraInOld.sorted()}\n")
        }
        assertTrue("scene.rml and scene_old.rml property sets differ:\n$sceneDiff", scene == old)

        // 2. both contain every binder-written name
        val missingInScene = binder - scene
        val missingInSceneOld = binder - old
        assertTrue(
            "binder names missing from scene.rml: ${missingInScene.sorted()}",
            missingInScene.isEmpty()
        )
        assertTrue(
            "binder names missing from scene_old.rml: ${missingInSceneOld.sorted()}",
            missingInSceneOld.isEmpty()
        )

        // 3. PROPERTY_COUNT == binder-written count
        assertEquals("DuoBinder.PROPERTY_COUNT mismatch", DuoBinder.PROPERTY_COUNT, binder.size)
    }
}
