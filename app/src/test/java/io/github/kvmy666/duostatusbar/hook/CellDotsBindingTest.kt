package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PART B (cell dots) evidence guard.
 *
 * Pins the Rive side of the four cellular dots in both designs: every dot shape exists, and each one
 * reads one of `cell1Opacity..cell4Opacity`, the exact properties [DuoMapping.visual] writes through
 * `DuoBinder`. The owner's design-one dots were invisible because the saved portrait `show_cell_dots`
 * was false, and these tests keep that cause distinguishable from a missing shape or a lost binding.
 *
 * The shipped `.riv` binaries themselves are hashed offline by `tools/check-rive-shipped.sh`; here we
 * only prove the assets are present and non-empty.
 */
class CellDotsBindingTest {

    private data class ShapeBlock(val name: String, val body: String)

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

    private fun attribute(tagText: String, name: String): String? =
        Regex("""\b$name="([^"]*)"""").find(tagText)?.groupValues?.get(1)

    /** All non-nested `<Shape>` elements, keyed by their `name` attribute. */
    private fun parseShapes(scene: String): List<ShapeBlock> =
        Regex("""<Shape\b([^>]*)>(.*?)</Shape>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(scene)
            .mapNotNull { match ->
                val name = attribute(match.groupValues[1], "name") ?: return@mapNotNull null
                ShapeBlock(name, match.groupValues[2])
            }
            .toList()

    /** id (`0:71`) -> name (`cell1Opacity`) from every ViewModel number property in the scene. */
    private fun propertyNamesById(scene: String): Map<String, String> =
        Regex("""<ViewModelPropertyNumber\b([^>]*)/>""").findAll(scene)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                val id = attribute(attrs, "id") ?: return@mapNotNull null
                val name = attribute(attrs, "name") ?: return@mapNotNull null
                id to name
            }
            .toMap()

    /** The opacity property each shape's opacity binding (`propertyKey="18"`) resolves to. */
    private fun boundOpacityProperties(
        shape: ShapeBlock,
        properties: Map<String, String>
    ): List<String> =
        Regex("""<DataBindContext\b([^>]*)/>""").findAll(shape.body)
            .map { it.groupValues[1] }
            .filter { attribute(it, "propertyKey") == "18" }
            .mapNotNull { attribute(it, "sourcePathIds") }
            .map { it.substringAfterLast('-') }
            .mapNotNull { properties[it] }
            .toList()

    @Test
    fun `old design authors four cell dots bound to the four opacity properties`() {
        val root = findProjectRoot()
        val scene = readText(root, "rive/duo/scene_old.rml")
        val properties = propertyNamesById(scene)
        val expected = (1..4).associate { "cell$it" to "cell${it}Opacity" }

        for ((_, propertyName) in expected) {
            assertTrue(
                "scene_old.rml never declares $propertyName",
                properties.containsValue(propertyName)
            )
        }

        val dots = parseShapes(scene).filter { it.name.matches(Regex("""cell\d+""")) }
        assertEquals("scene_old.rml dot shape names", expected.keys, dots.map { it.name }.toSet())
        assertEquals("scene_old.rml must draw exactly four dots", 4, dots.size)

        for ((shapeName, propertyName) in expected) {
            val shape = dots.single { it.name == shapeName }
            assertEquals(
                "$shapeName opacity bind",
                listOf(propertyName),
                boundOpacityProperties(shape, properties)
            )
        }

        assertTrue(
            "scene_old.rml has no cellGroup container",
            scene.contains("""<Node name="cellGroup"""")
        )
    }

    @Test
    fun `new design mirrors eight cell dots onto the same four opacity properties`() {
        val root = findProjectRoot()
        val scene = readText(root, "rive/duo/scene.rml")
        val properties = propertyNamesById(scene)
        // Right side: cell1..cell4 -> cell1Opacity..cell4Opacity. Left mirror: cell5..cell8 -> the
        // same four properties, so SIM2 intentionally duplicates SIM1 until a second axis exists.
        val expected = (1..8).associate { "cell$it" to "cell${((it - 1) % 4) + 1}Opacity" }

        for ((_, propertyName) in expected) {
            assertTrue(
                "scene.rml never declares $propertyName",
                properties.containsValue(propertyName)
            )
        }

        val dots = parseShapes(scene).filter { it.name.matches(Regex("""cell\d+""")) }
        assertEquals("scene.rml dot shape names", expected.keys, dots.map { it.name }.toSet())
        assertEquals("scene.rml must draw exactly eight dots", 8, dots.size)

        for ((shapeName, propertyName) in expected) {
            val shape = dots.single { it.name == shapeName }
            assertEquals(
                "$shapeName opacity bind",
                listOf(propertyName),
                boundOpacityProperties(shape, properties)
            )
        }

        val bindingsPerProperty = dots
            .flatMap { boundOpacityProperties(it, properties) }
            .groupingBy { it }
            .eachCount()
        for (n in 1..4) {
            assertEquals(
                "cell${n}Opacity must feed both mirrored sides",
                2,
                bindingsPerProperty["cell${n}Opacity"]
            )
        }

        // Grouping: the right node precedes its four shapes, the mirrored left node follows the
        // right group and owns cell5..cell8. This is the structure that makes one computed axis
        // light the same dot pair on both sides.
        val right = scene.indexOf("""<Node name="cellRight"""")
        val left = scene.indexOf("""<Node name="cellLeft"""")
        assertTrue("cellRight must exist before cellLeft", right in 0 until left)
        val leftTag = Regex("""<Node name="cellLeft"[^>]*>""").find(scene)?.value
        assertTrue(
            "cellLeft must be the mirrored node (scaleX=\"-1\")",
            leftTag?.contains("""scaleX="-1"""") == true
        )
        fun shapeAt(name: String): Int = scene.indexOf("""<Shape name="$name"""")
        for (n in 1..4) {
            assertTrue("cell$n must sit after cellRight", shapeAt("cell$n") > right)
            assertTrue("cell$n must sit before the left mirror", shapeAt("cell$n") < left)
        }
        for (n in 5..8) {
            assertTrue("cell$n must sit inside the left mirror", shapeAt("cell$n") > left)
        }
    }

    @Test
    fun `binder writes all four cell opacity properties`() {
        val root = findProjectRoot()
        val source = readText(
            root,
            "app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoBinder.kt"
        )
        val numbers = Regex("""val numbers = arrayOf\((.*?)\n\s*\)""", RegexOption.DOT_MATCHES_ALL)
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: throw AssertionError("'numbers' array not found in DuoBinder.kt")
        val written = numbers.lineSequence()
            .filter { it.contains(" to ") }
            .map { it.trim().substringBefore(" to ").trim().trim('"', ',') }
            .toSet()

        for (n in 1..4) {
            assertTrue(
                "DuoBinder.numbers does not write cell${n}Opacity",
                written.contains("cell${n}Opacity")
            )
        }
    }

    @Test
    fun `both shipped rive assets exist and are non-empty`() {
        val root = findProjectRoot()
        for (relative in listOf(
            "app/src/main/res/raw/duo_old.riv",
            "app/src/main/res/raw/duo.riv"
        )) {
            val file = File(root, relative)
            assertTrue("$relative is missing", file.isFile)
            assertTrue("$relative is empty", file.length() > 0L)
        }
    }
}
