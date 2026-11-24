package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PART B (cell dots) mapping-to-drawing guard.
 *
 * Proves the computed dot opacities reach the bound Rive properties when the `show_cell_dots`
 * toggle is on, and pins the toggle as the only gate: with it off every dot and moon is written as
 * 0 regardless of the cellular level.
 *
 * The mapping is [SignalMapping.cellOpacities]: `level.coerceIn(0, 4)`, then a lit sphere is `1f`
 * and an unlit one keeps the authored `0.3f` gray dim (not 0 - an empty sphere is still drawn).
 * "Three lit" below therefore means three `1f` values, not three non-zero values.
 */
class CellDotsMappingTest {

    private fun findProjectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        throw IllegalStateException("project root (settings.gradle.kts) not found from ${System.getProperty("user.dir")}")
    }

    private fun visual(
        cellLevel: Int,
        showCellDots: Boolean,
        dnd: Boolean = false,
        dndDots: Boolean = false
    ): DuoVisual = DuoMapping.visual(
        level = 50,
        charging = false,
        saver = false,
        showPercent = false,
        wifiLevel = 0,
        cellLevel = cellLevel,
        airplane = false,
        dnd = dnd,
        wifiOn = false,
        wifiDots = false,
        dndDots = dndDots,
        showCellDots = showCellDots
    )

    private fun cells(v: DuoVisual): List<Float> =
        listOf(v.cell1Opacity, v.cell2Opacity, v.cell3Opacity, v.cell4Opacity)

    private fun moons(v: DuoVisual): List<Float> =
        listOf(v.moon1Opacity, v.moon2Opacity, v.moon3Opacity, v.moon4Opacity)

    @Test
    fun `toggle on lights the first three dots at level 3 and leaves the fourth dim`() {
        val v = visual(cellLevel = 3, showCellDots = true)

        assertEquals(listOf(1f, 1f, 1f, 0.3f), cells(v))
        assertEquals("exactly three dots are lit at level 3", 3, cells(v).count { it == 1f })
        assertEquals("the lit dots are the first three, in order", listOf(1f, 1f, 1f), cells(v).take(3))
        assertEquals("the fourth dot stays the empty-sphere dim", 0.3f, cells(v)[3], 0f)
    }

    @Test
    fun `level 5 saturates at four lit dots`() {
        // SignalMapping.cellOpacities coerces the level into 0..4, so 5 and 4 are the same picture.
        val v = visual(cellLevel = 5, showCellDots = true)

        assertEquals(listOf(1f, 1f, 1f, 1f), cells(v))
        assertEquals("all four dots are lit at level 5", 4, cells(v).count { it == 1f })
    }

    @Test
    fun `toggle off zeroes the four dots and every moon opacity`() {
        val on = visual(cellLevel = 3, showCellDots = true)
        assertEquals("control: the toggle on lights three dots", 3, cells(on).count { it == 1f })

        val off = visual(cellLevel = 3, showCellDots = false)
        assertEquals("every dot must be written as 0 when the toggle is off", listOf(0f, 0f, 0f, 0f), cells(off))
        assertEquals("every moon must be written as 0 when the toggle is off", listOf(0f, 0f, 0f, 0f), moons(off))
        assertEquals("the centre moon must be 0 when the toggle is off", 0f, off.centerMoonOpacity, 0f)
    }

    @Test
    fun `toggle off also zeroes dnd moons that would otherwise fill the dots`() {
        // With DND dots on and level 4 every sphere would become a crescent; the cell-dot gate owns
        // both the circles and the crescents, so turning it off must clear them together.
        val on = visual(cellLevel = 4, showCellDots = true, dnd = true, dndDots = true)
        assertEquals(
            "control: the same state with the toggle on fills all four moons",
            listOf(1f, 1f, 1f, 1f),
            moons(on)
        )

        val off = visual(cellLevel = 4, showCellDots = false, dnd = true, dndDots = true)
        assertEquals(listOf(0f, 0f, 0f, 0f), cells(off))
        assertEquals(listOf(0f, 0f, 0f, 0f), moons(off))
        assertEquals(0f, off.centerMoonOpacity, 0f)
    }

    @Test
    fun `design two lights six of eight dots at level 3 because both sides share the four opacities`() {
        // scene.rml draws cell1..cell8: cell1..cell4 on the right and the mirrored cell5..cell8 on
        // the left, and all eight bind to the same cell1Opacity..cell4Opacity properties. At level 3
        // the mapping lights three of those four, so each side shows three lit dots: 6 of 8.
        val root = findProjectRoot()
        val scene = File(root, "rive/duo/scene.rml").readText(Charsets.UTF_8)
        val bindings = cellBindings(scene)

        assertEquals("scene.rml must draw eight dots for design two", 8, bindings.size)
        val fourProperties = (1..4).map { "cell${it}Opacity" }.toSet()
        assertTrue(
            "every dot must bind one of $fourProperties, got $bindings",
            bindings.values.all { it in fourProperties }
        )

        val v = visual(cellLevel = 3, showCellDots = true)
        val opacityByName = mapOf(
            "cell1Opacity" to v.cell1Opacity,
            "cell2Opacity" to v.cell2Opacity,
            "cell3Opacity" to v.cell3Opacity,
            "cell4Opacity" to v.cell4Opacity
        )
        val lit = bindings.values.count { opacityByName[it] == 1f }
        assertEquals(
            "level 3 must light six of the eight drawn dots (three per mirrored side)",
            6,
            lit
        )
    }

    /** Shape name -> the opacity property its `propertyKey="18"` binding resolves to. */
    private fun cellBindings(scene: String): Map<String, String> {
        val propertiesById = Regex("""<ViewModelPropertyNumber\b([^>]*)/>""").findAll(scene)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                val id = Regex("""\bid="([^"]*)"""").find(attrs)?.groupValues?.get(1)
                val name = Regex("""\bname="([^"]*)"""").find(attrs)?.groupValues?.get(1)
                if (id != null && name != null) id to name else null
            }
            .toMap()

        return Regex("""<Shape\b([^>]*)>(.*?)</Shape>""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(scene)
            .mapNotNull { match ->
                val name = Regex("""\bname="([^"]*)"""").find(match.groupValues[1])
                    ?.groupValues
                    ?.get(1)
                    ?: return@mapNotNull null
                if (!name.matches(Regex("""cell\d+"""))) return@mapNotNull null
                val property = Regex("""<DataBindContext\b([^>]*)/>""")
                    .findAll(match.groupValues[2])
                    .map { it.groupValues[1] }
                    .filter { Regex("""\bpropertyKey="18"""").containsMatchIn(it) }
                    .mapNotNull {
                        Regex("""\bsourcePathIds="[^"]*-([^"]*)"""").find(it)
                            ?.groupValues
                            ?.get(1)
                    }
                    .mapNotNull { propertiesById[it] }
                    .firstOrNull()
                    ?: return@mapNotNull null
                name to property
            }
            .toMap()
    }
}
