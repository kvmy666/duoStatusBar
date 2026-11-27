package io.github.kvmy666.duostatusbar.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app is English-only: a `values-*` folder would re-introduce a locale (and make lint demand a
 * translation of every key), so the resource tree keeps exactly the default `values` directory.
 */
class EnglishOnlyResourcesTest {

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

    @Test
    fun `the resource tree has no locale folders`() {
        val res = File(findProjectRoot(), "app/src/main/res")
        assertTrue("resource root is missing: $res", res.isDirectory)
        val valuesDirs = res.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { it.name }
            .sorted()
        assertEquals(
            "app/src/main/res must contain only the default values directory, found $valuesDirs",
            listOf("values"),
            valuesDirs
        )
    }

    @Test
    fun `the only strings file is the english default`() {
        val res = File(findProjectRoot(), "app/src/main/res")
        val stringsFiles = res.walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" }
            .map { it.relativeTo(res).invariantSeparatorsPath }
            .sorted()
            .toList()
        assertEquals(listOf("values/strings.xml"), stringsFiles)
    }
}
