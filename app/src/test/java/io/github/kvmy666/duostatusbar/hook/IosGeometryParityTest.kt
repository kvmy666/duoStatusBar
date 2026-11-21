package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Rive scene is text, so the Canvas constants and the scene can drift silently. This test reads
 * `rive/ios/scene.rml` and **derives every expected string from `IosGeometry`**, so changing either
 * side alone fails: artboard sizes, cap, cell bars/dots, battery body and the measured Wi-Fi arcs and
 * dot. JVM only.
 */
class IosGeometryParityTest {

    private val scene: String by lazy {
        val candidates = listOf(File("../rive/ios/scene.rml"), File("rive/ios/scene.rml"))
        val file = candidates.firstOrNull { it.isFile }
        assertTrue(
            "rive/ios/scene.rml not found from ${File(".").absolutePath}",
            file != null
        )
        file!!.readText()
    }

    /** RML writes whole numbers without a decimal point (73, not 73.0). */
    private fun num(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

    @Test
    fun `artboards use the shared sizes`() {
        val b = IosGeometry.Battery
        val w = IosGeometry.Wifi
        val c = IosGeometry.Cell
        assertTrue(
            scene.contains(
                "width=\"${num(b.ARTBOARD_W)}\" height=\"${num(b.ARTBOARD_H)}\" name=\"Ios\""
            )
        )
        // The Wi-Fi artboard is the raw bbox * scale + margins, so the RML carries a 2-decimal
        // rounded size; compare numerically rather than against a full float string.
        val wifiArtboard = Regex(
            "<Artboard[^>]*width=\"([0-9.]+)\" height=\"([0-9.]+)\" name=\"IosWifi\""
        ).find(scene)
        assertNotNull("IosWifi artboard", wifiArtboard)
        assertEquals(w.ARTBOARD_W, wifiArtboard!!.groupValues[1].toFloat(), 0.01f)
        assertEquals(w.ARTBOARD_H, wifiArtboard.groupValues[2].toFloat(), 0.01f)
        assertTrue(
            scene.contains(
                "width=\"${num(c.ARTBOARD_W)}\" height=\"${num(c.ARTBOARD_H)}\" name=\"IosCell\""
            )
        )
    }

    @Test
    fun `all three artboards run a state machine named Ios`() {
        val machines = Regex("<StateMachine [^>]*name=\"Ios\"").findAll(scene).count()
        assertEquals(3, machines)
    }

    @Test
    fun `battery body, cap and clip match the shared battery`() {
        val b = IosGeometry.Battery
        assertTrue(scene.contains("width=\"${num(b.BODY_W)}\" height=\"${num(b.BODY_H)}\""))
        assertTrue(scene.contains("cornerRadiusTL=\"${num(b.BODY_RADIUS)}\""))
        val cap = Regex(
            "<Shape[^>]*name=\"cap\"[^>]*>(.*?)</Shape>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("cap shape", cap)
        val body = cap!!.groupValues[1]
        val vertices = Regex("<StraightVertex x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"")
            .findAll(body).toList()
        assertEquals(25, vertices.size)
        // Every annotated cap point, not just the ends: a single drifted vertex is a different shape.
        for (i in 0 until 25) {
            assertEquals("cap vertex $i x", b.CAP_POINTS[i * 2], vertices[i].groupValues[1].toFloat(), 0.001f)
            assertEquals(
                "cap vertex $i y",
                b.CAP_POINTS[i * 2 + 1],
                vertices[i].groupValues[2].toFloat(),
                0.001f
            )
        }
        assertEquals(b.CAP_POINTS[0], vertices.first().groupValues[1].toFloat(), 0.001f)
        assertEquals(b.CAP_POINTS[1], vertices.first().groupValues[2].toFloat(), 0.001f)
        assertEquals(
            b.CAP_POINTS[b.CAP_POINTS.size - 2],
            vertices.last().groupValues[1].toFloat(),
            0.001f
        )
        assertEquals(
            b.CAP_POINTS[b.CAP_POINTS.size - 1],
            vertices.last().groupValues[2].toFloat(),
            0.001f
        )
        assertTrue(body.contains("isClosed=\"true\""))
        assertTrue(scene.contains("sourceId=\"0:1102\""))
    }

    @Test
    fun `cell bars and dots match the shared cell`() {
        val c = IosGeometry.Cell
        for (height in c.SINGLE_BAR_HEIGHTS) {
            assertTrue(
                "single bar ${num(height)}",
                scene.contains("width=\"${num(c.BAR_W)}\" height=\"${num(height)}\"")
            )
        }
        for (height in c.DUAL_BAR_HEIGHTS) {
            assertTrue(
                "dual bar ${num(height)}",
                scene.contains("width=\"${num(c.DUAL_BAR_W)}\" height=\"${num(height)}\"")
            )
        }
        // The single capsule row is bottom-aligned at y=100 in the 105.6 artboard, so every bar must
        // satisfy y + height == 100 (F4). Parse each named single bar rather than a loose substring.
        for (i in c.SINGLE_BAR_HEIGHTS.indices) {
            val shape = Regex(
                "<Shape x=\"([-0-9.]+)\" y=\"([-0-9.]+)\" name=\"singleBar${i + 1}\"[^>]*>(.*?)</Shape>",
                RegexOption.DOT_MATCHES_ALL
            ).find(scene)
            assertNotNull("single bar ${i + 1} shape", shape)
            val rect = Regex("<Rectangle width=\"([0-9.]+)\" height=\"([0-9.]+)\"")
                .find(shape!!.groupValues[3])
            assertNotNull("single bar ${i + 1} rectangle", rect)
            val y = shape.groupValues[2].toFloat()
            val width = rect!!.groupValues[1].toFloat()
            val height = rect.groupValues[2].toFloat()
            assertEquals("single bar ${i + 1} width", c.BAR_W, width, 0.001f)
            assertEquals(
                "single bar ${i + 1} height",
                c.SINGLE_BAR_HEIGHTS[i],
                height,
                0.001f
            )
            assertEquals("single bar ${i + 1} bottom-aligned at 100", 100f, y + height, 0.001f)
        }
        val dots = Regex(
            "<Ellipse width=\"${num(c.DOT_W)}\" height=\"${num(c.DOT_H)}\""
        ).findAll(scene).count()
        assertEquals(4, dots)
    }

    @Test
    fun `wifi uses the measured concentric arcs, trims and 32-gon`() {
        val w = IosGeometry.Wifi
        // No wrapper node any more: the ellipses carry the measured artboard centre directly.
        assertTrue(
            scene.contains(
                "<Ellipse x=\"${num(w.CENTRE_X)}\" y=\"${num(w.CENTRE_Y)}\" " +
                    "width=\"${num(w.OUTER_RADIUS * 2f)}\" height=\"${num(w.OUTER_RADIUS * 2f)}\""
            )
        )
        assertTrue(
            scene.contains(
                "<Ellipse x=\"${num(w.CENTRE_X)}\" y=\"${num(w.CENTRE_Y)}\" " +
                    "width=\"${num(w.INNER_RADIUS * 2f)}\" height=\"${num(w.INNER_RADIUS * 2f)}\""
            )
        )
        assertTrue(scene.contains("thickness=\"${num(w.OUTER_STROKE)}\""))
        assertTrue(scene.contains("thickness=\"${num(w.INNER_STROKE)}\""))

        val trims = Regex(
            "<TrimPath start=\"0\" end=\"([0-9.]+)\" offset=\"([0-9.]+)\" " +
                "name=\"Trim\" id=\"0:120([67])\""
        ).findAll(scene).associateBy { it.groupValues[3] }
        assertEquals(w.OUTER_TRIM_END, trims.getValue("6").groupValues[1].toFloat(), 0.000001f)
        assertEquals(w.OUTER_TRIM_OFFSET, trims.getValue("6").groupValues[2].toFloat(), 0.000001f)
        assertEquals(w.INNER_TRIM_END, trims.getValue("7").groupValues[1].toFloat(), 0.000001f)
        assertEquals(w.INNER_TRIM_OFFSET, trims.getValue("7").groupValues[2].toFloat(), 0.000001f)

        val dot = Regex(
            "<Shape[^>]*name=\"wifiDot\"[^>]*>(.*?)</Shape>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("wifiDot shape", dot)
        val body = dot!!.groupValues[1]
        val vertices = Regex("<StraightVertex x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"")
            .findAll(body).toList()
        assertEquals(32, vertices.size)
        assertEquals(w.DOT_POINTS[0], vertices.first().groupValues[1].toFloat(), 0.001f)
        assertEquals(w.DOT_POINTS[1], vertices.first().groupValues[2].toFloat(), 0.001f)
        assertEquals(
            w.DOT_POINTS[w.DOT_POINTS.size - 2],
            vertices.last().groupValues[1].toFloat(),
            0.001f
        )
        assertEquals(
            w.DOT_POINTS[w.DOT_POINTS.size - 1],
            vertices.last().groupValues[2].toFloat(),
            0.001f
        )
        assertTrue(body.contains("isClosed=\"true\""))
    }
}
