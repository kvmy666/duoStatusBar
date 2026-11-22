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
        // The cap is the inactive grey: shape opacity 0.50, Fill bound to the neutral tint (0:1003).
        val capTag = Regex("<Shape[^>]*name=\"cap\"[^>]*>").find(scene)!!.value
        assertTrue("cap opacity 0.50", capTag.contains("opacity=\"0.50\""))
        assertTrue("cap fill is the neutral tint", body.contains("sourcePathIds=\"0:1000-0:1003\""))
        assertTrue(!body.contains("sourcePathIds=\"0:1000-0:1006\""))
        assertTrue(scene.contains("sourceId=\"0:1102\""))
    }

    @Test
    fun `uncharged remainder uses the neutral tint at 0 x 50`() {
        val remainder = Regex(
            "<Shape[^>]*name=\"batteryRemainder\"[^>]*>(.*?)</Shape>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("remainder shape", remainder)
        val body = remainder!!.groupValues[1]
        assertTrue("remainder is the neutral tint", body.contains("sourcePathIds=\"0:1000-0:1003\""))
        assertTrue(
            "remainder is not the state colour",
            !body.contains("sourcePathIds=\"0:1000-0:1006\"")
        )
        val tag = Regex("<Shape[^>]*name=\"batteryRemainder\"[^>]*>").find(scene)!!.value
        assertTrue("remainder opacity 0.50", tag.contains("opacity=\"0.50\""))
    }

    @Test
    fun `battery bolt is the measured 21-point white glyph with its dark outline`() {
        val b = IosGeometry.Battery
        val shape = Regex(
            "<Shape[^>]*name=\"bolt\"[^>]*>(.*?)</Shape>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("bolt shape", shape)
        val body = shape!!.groupValues[1]
        val vertices = Regex("<StraightVertex x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"")
            .findAll(body).toList()
        assertEquals(21, vertices.size)
        assertTrue(body.contains("isClosed=\"true\""))
        for (i in 0 until 21) {
            assertEquals(
                "bolt vertex $i x",
                b.BOLT_POINTS[i * 2],
                vertices[i].groupValues[1].toFloat(),
                0.001f
            )
            assertEquals(
                "bolt vertex $i y",
                b.BOLT_POINTS[i * 2 + 1],
                vertices[i].groupValues[2].toFloat(),
                0.001f
            )
        }
        // Ref 3 draws a static white bolt, not the dynamic tint.
        val fill = Regex("<Fill[^>]*name=\"Fill\"[^>]*>(.*?)</Fill>", RegexOption.DOT_MATCHES_ALL)
            .find(body)
        assertNotNull("bolt fill", fill)
        val fillBody = fill!!.groupValues[1]
        assertTrue("bolt fill is white", fillBody.contains("colorValue=\"FFFFFFFF\""))
        assertTrue("bolt fill is static", !fillBody.contains("DataBindContext"))
        // The ref-3 ring measures ~4.5 units, but the 96-unit white bolt leaves only 3 units of
        // vertical room in the 102-unit artboard, so the scene ships a 6-unit round stroke
        // (3.0 visible units either side, BOLT_OUTLINE).
        val outline = Regex(
            "<Stroke[^>]*name=\"Outline\"[^>]*>(.*?)</Stroke>",
            RegexOption.DOT_MATCHES_ALL
        ).find(body)
        assertNotNull("bolt outline", outline)
        assertTrue("outline thickness 6", outline!!.groupValues[0].contains("thickness=\"6\""))
        assertTrue("outline is round", outline.groupValues[0].contains("cap=\"round\""))
        assertTrue("outline join is round", outline.groupValues[0].contains("join=\"round\""))
        assertTrue("outline colour", outline.groupValues[1].contains("colorValue=\"8C000000\""))
        assertEquals(3.0f, b.BOLT_OUTLINE, 0.001f)
        // The stroked bolt must fit the frozen artboard: 96 + 2 * 3.0 = 102 exactly, so the
        // rendered extent is 0..101.6 (the bolt occupies y 3.0..98.6 before the stroke).
        assertTrue(
            "stroked bolt fits the 102-unit artboard",
            2f * b.BOLT_OUTLINE + 96f <= b.ARTBOARD_H + 0.01f
        )
        // Shape opacity is still the only property bound to boltOpacity.
        assertTrue(body.contains("propertyKey=\"18\" sourcePathIds=\"0:1000-0:1007\""))
    }

    @Test
    fun `IosDigits artboard repeats every filled contour and the inline bolt`() {
        val artboard = Regex(
            "<Artboard[^>]*name=\"IosDigits\"[^>]*>(.*?)</Artboard>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("IosDigits artboard", artboard)
        val tag = Regex("<Artboard[^>]*name=\"IosDigits\"[^>]*>").find(scene)!!.value
        assertTrue(tag.contains("x=\"0\" y=\"220\" width=\"630\" height=\"160\""))
        val body = artboard!!.groupValues[1]
        var shapes = 0
        var offset = 10f
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            val shape = Regex(
                "<Shape x=\"([-0-9.]+)\" y=\"([-0-9.]+)\" name=\"digit$ch\"[^>]*>(.*?)</Shape>",
                RegexOption.DOT_MATCHES_ALL
            ).find(body)
            assertNotNull("digit$ch shape", shape)
            assertEquals("digit$ch offset x", offset, shape!!.groupValues[1].toFloat(), 0.01f)
            assertEquals("digit$ch offset y", 10f, shape.groupValues[2].toFloat(), 0.001f)
            val shapeBody = shape.groupValues[3]
            // One closed PointsPath per contour: the first is the outer edge, the rest are counters.
            val paths = Regex(
                "<PointsPath isClosed=\"true\" name=\"Path[0-9]+\">(.*?)</PointsPath>",
                RegexOption.DOT_MATCHES_ALL
            ).findAll(shapeBody).toList()
            assertEquals("digit$ch contour count", glyph.contours.size, paths.size)
            for (i in glyph.contours.indices) {
                val contour = glyph.contours[i]
                val vertices = Regex("<StraightVertex x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"")
                    .findAll(paths[i].groupValues[1]).toList()
                assertEquals("digit$ch contour $i vertex count", contour.size / 2, vertices.size)
                for (v in vertices.indices) {
                    assertEquals(
                        "digit$ch contour $i vertex $v x",
                        contour[v * 2],
                        vertices[v].groupValues[1].toFloat(),
                        0.001f
                    )
                    assertEquals(
                        "digit$ch contour $i vertex $v y",
                        contour[v * 2 + 1],
                        vertices[v].groupValues[2].toFloat(),
                        0.001f
                    )
                }
            }
            assertTrue(
                "digit$ch has an even-odd fill",
                shapeBody.contains("<Fill fillRule=\"evenOdd\" name=\"Fill\">")
            )
            assertTrue(
                "digit$ch fill is white",
                shapeBody.contains("colorValue=\"FFFFFFFF\"")
            )
            shapes++
            offset += glyph.advance
        }
        assertEquals(10, shapes)
        val inline = Regex(
            "<Shape[^>]*name=\"inlineBoltSample\"[^>]*>(.*?)</Shape>",
            RegexOption.DOT_MATCHES_ALL
        ).find(body)
        assertNotNull("inline bolt sample", inline)
        val inlineBody = inline!!.groupValues[1]
        val inlineVertices = Regex("<StraightVertex x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"")
            .findAll(inlineBody).toList()
        assertEquals(21, inlineVertices.size)
        assertTrue("inline bolt is closed", inlineBody.contains("isClosed=\"true\""))
        for (i in 0 until 21) {
            assertEquals(
                "inline bolt vertex $i x",
                IosDigits.BOLT_NORMALIZED[i * 2] * IosDigits.INLINE_BOLT_W,
                inlineVertices[i].groupValues[1].toFloat(),
                0.01f
            )
            assertEquals(
                "inline bolt vertex $i y",
                IosDigits.BOLT_NORMALIZED[i * 2 + 1] * IosDigits.INLINE_BOLT_H,
                inlineVertices[i].groupValues[2].toFloat(),
                0.01f
            )
        }
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
        // The single capsule row is bottom-aligned at SINGLE_BAR_BOTTOM (96.5), so every bar must
        // satisfy y + height == 96.5 and bar 4's top must be exactly TOP (1.0). Parse each named
        // single bar rather than a loose substring.
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
            assertEquals(
                "single bar ${i + 1} bottom-aligned at 96.5",
                c.SINGLE_BAR_BOTTOM,
                y + height,
                0.001f
            )
        }
        // Bar 4's top is exactly TOP, level with the battery body top.
        val bar4 = Regex(
            "<Shape x=\"([-0-9.]+)\" y=\"([-0-9.]+)\" name=\"singleBar4\"[^>]*>"
        ).find(scene)!!
        assertEquals(c.TOP, bar4.groupValues[2].toFloat(), 0.001f)

        // Dots are centred at DOT_TOP + DOT_H/2 (90.1), sizes unchanged.
        for (i in 1..4) {
            val dot = Regex(
                "<Shape x=\"([-0-9.]+)\" y=\"([-0-9.]+)\" name=\"dot$i\"[^>]*>(.*?)</Shape>",
                RegexOption.DOT_MATCHES_ALL
            ).find(scene)
            assertNotNull("dot $i shape", dot)
            assertEquals(
                "dot $i centre y",
                c.dotCenterY(),
                dot!!.groupValues[2].toFloat(),
                0.01f
            )
            val dotRect = Regex("<Ellipse width=\"([0-9.]+)\" height=\"([0-9.]+)\"")
                .find(dot.groupValues[3])!!
            assertEquals(c.DOT_W, dotRect.groupValues[1].toFloat(), 0.001f)
            assertEquals(c.DOT_H, dotRect.groupValues[2].toFloat(), 0.001f)
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
