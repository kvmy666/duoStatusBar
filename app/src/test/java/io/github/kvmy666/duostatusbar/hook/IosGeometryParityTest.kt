package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Rive scene is text, so the Canvas constants and the scene can drift silently. This test reads
 * `rive/ios/scene.rml` and **derives every expected value from `IosGeometry`/`IosDigits`**, so changing
 * either side alone fails: the four artboards, the battery body/nub/bolt, the cell bars and dots, the
 * measured Wi-Fi arcs and dot, and the R9 stroke-skeleton digits (cap, stroke, advance, widths).
 * JVM only.
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

    // ------------------------------------------------------------------------------ RML helpers

    /** The `<Artboard ... name="[name]" ...>` tag. */
    private fun artboard(name: String): String {
        val match = Regex("<Artboard[^>]*name=\"${Regex.escape(name)}\"[^>]*>").find(scene)
        assertNotNull("artboard $name", match)
        return match!!.value
    }

    /** The `<Shape ... name="[name]" ...>` opening tag. */
    private fun shapeTag(name: String): String {
        val match = Regex("<Shape[^>]*name=\"${Regex.escape(name)}\"[^>]*>").find(scene)
        assertNotNull("shape $name", match)
        return match!!.value
    }

    /** Everything between a `<Shape name="[name]" ...>` and its first `</Shape>`. */
    private fun shapeBody(name: String): String {
        val match = Regex(
            "<Shape[^>]*name=\"${Regex.escape(name)}\"[^>]*>(.*?)</Shape>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("shape body $name", match)
        return match!!.groupValues[1]
    }

    /** The first `[element]` opening tag inside [body]. */
    private fun tag(element: String, body: String): String {
        val match = Regex("<$element[^>]*>", RegexOption.DOT_MATCHES_ALL).find(body)
        assertNotNull("$element in body", match)
        return match!!.value
    }

    /** A numeric attribute from an RML opening tag. */
    private fun attr(tag: String, name: String): Float {
        val match = Regex("\\b${Regex.escape(name)}=\"([-0-9.]+)\"").find(tag)
        assertNotNull("$name in $tag", match)
        return match!!.groupValues[1].toFloat()
    }

    private fun vertices(body: String): List<Pair<Float, Float>> =
        Regex("<StraightVertex x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"").findAll(body)
            .map { it.groupValues[1].toFloat() to it.groupValues[2].toFloat() }
            .toList()

    private fun xs(points: List<Pair<Float, Float>>): List<Float> = points.map { it.first }

    private fun ys(points: List<Pair<Float, Float>>): List<Float> = points.map { it.second }

    private fun span(values: List<Float>): Float {
        val min = values.minOrNull() ?: error("no values")
        val max = values.maxOrNull() ?: error("no values")
        return max - min
    }

    private fun animation(name: String): String {
        val match = Regex(
            "<LinearAnimation[^>]*name=\"${Regex.escape(name)}\"[^>]*>(.*?)</LinearAnimation>",
            RegexOption.DOT_MATCHES_ALL
        ).find(scene)
        assertNotNull("animation $name", match)
        return match!!.groupValues[1]
    }

    /** The first hold keyframe of `objectId` inside an animation. */
    private fun poseValue(animation: String, objectId: String): Float {
        val block = Regex(
            "<KeyedObject objectId=\"${Regex.escape(objectId)}\">(.*?)</KeyedObject>",
            RegexOption.DOT_MATCHES_ALL
        ).find(animation)
        assertNotNull("object $objectId in animation", block)
        val value = Regex("<KeyFrameDouble value=\"([-0-9.]+)\"").find(block!!.groupValues[1])
        assertNotNull("keyframe for $objectId", value)
        return value!!.groupValues[1].toFloat()
    }

    // ------------------------------------------------------------------------------------ tests

    @Test
    fun `artboards use the shared sizes`() {
        val b = IosGeometry.Battery
        val w = IosGeometry.Wifi
        val c = IosGeometry.Cell
        val d = IosDigits

        val battery = artboard("Ios")
        assertEquals(b.ARTBOARD_W.toDouble(), attr(battery, "width").toDouble(), 0.01)
        assertEquals(b.ARTBOARD_H.toDouble(), attr(battery, "height").toDouble(), 0.01)

        val wifi = artboard("IosWifi")
        assertEquals(w.ARTBOARD_W.toDouble(), attr(wifi, "width").toDouble(), 0.01)
        assertEquals(w.ARTBOARD_H.toDouble(), attr(wifi, "height").toDouble(), 0.01)

        val cell = artboard("IosCell")
        assertEquals(c.ARTBOARD_W.toDouble(), attr(cell, "width").toDouble(), 0.01)
        assertEquals(c.ARTBOARD_H.toDouble(), attr(cell, "height").toDouble(), 0.01)

        val digits = artboard("IosDigits")
        assertEquals(0f.toDouble(), attr(digits, "x").toDouble(), 0.001)
        assertEquals(220f.toDouble(), attr(digits, "y").toDouble(), 0.001)
        assertEquals(160f.toDouble(), attr(digits, "height").toDouble(), 0.001)
        // Symmetric 10-unit margins around the ten equal advance cells.
        var advances = 0f
        for (ch in '0'..'9') advances += d.advance(ch)
        assertEquals((20f + advances).toDouble(), attr(digits, "width").toDouble(), 0.01)
    }

    @Test
    fun `all three glyph artboards run a state machine named Ios`() {
        val machines = Regex("<StateMachine [^>]*name=\"Ios\"").findAll(scene).count()
        assertEquals(3, machines)
    }

    @Test
    fun `battery body, separate nub and clip match the shared battery`() {
        val b = IosGeometry.Battery

        // Body geometry is repeated by the fill, remainder and clip source.
        val fillBody = shapeBody("batteryFill")
        val fillRect = tag("Rectangle", fillBody)
        assertEquals(b.BODY_W.toDouble(), attr(fillRect, "width").toDouble(), 0.001)
        assertEquals(b.BODY_H.toDouble(), attr(fillRect, "height").toDouble(), 0.001)
        assertTrue("fill is bound to the width converter", fillBody.contains("propertyKey=\"20\""))
        assertTrue("fill uses converter 0:1014", fillBody.contains("converterId=\"0:1014\""))

        for (name in listOf("batteryRemainder", "bodyClip")) {
            val rect = tag("Rectangle", shapeBody(name))
            assertEquals("$name width", b.BODY_W.toDouble(), attr(rect, "width").toDouble(), 0.001)
            assertEquals("$name height", b.BODY_H.toDouble(), attr(rect, "height").toDouble(), 0.001)
            assertEquals(
                "$name radius",
                b.BODY_RADIUS.toDouble(),
                attr(rect, "cornerRadiusTL").toDouble(),
                0.001
            )
        }
        val converter = Regex("<DataConverterRangeMapper[^>]*maxOutput=\"([-0-9.]+)\"")
            .find(scene)!!.groupValues[1].toFloat()
        assertEquals(b.BODY_W.toDouble(), converter.toDouble(), 0.001)

        // Separate half-capsule nub: the exact 25-gon CAP_POINTS, 3.6 units after the body.
        assertEquals(
            "nub gap",
            b.CAP_GAP.toDouble(),
            (b.CAP_LEFT - (b.BODY_LEFT + b.BODY_W)).toDouble(),
            0.001
        )
        val capBody = shapeBody("cap")
        val cap = vertices(capBody)
        assertEquals(25, cap.size)
        for (i in 0 until 25) {
            assertEquals("cap vertex $i x", b.CAP_POINTS[i * 2].toDouble(), cap[i].first.toDouble(), 0.001)
            assertEquals(
                "cap vertex $i y",
                b.CAP_POINTS[i * 2 + 1].toDouble(),
                cap[i].second.toDouble(),
                0.001
            )
        }
        assertEquals("nub left", b.CAP_LEFT.toDouble(), xs(cap).minOrNull()!!.toDouble(), 0.01)
        assertEquals("nub width", b.CAP_W.toDouble(), span(xs(cap)).toDouble(), 0.01)
        assertEquals("nub top", b.CAP_TOP.toDouble(), ys(cap).minOrNull()!!.toDouble(), 0.01)
        assertEquals("nub height", b.CAP_H.toDouble(), span(ys(cap)).toDouble(), 0.01)
        assertTrue("nub is closed", capBody.contains("isClosed=\"true\""))
        assertTrue("nub opacity 0.50", shapeTag("cap").contains("opacity=\"0.50\""))
        assertTrue("nub is the neutral tint", capBody.contains("sourcePathIds=\"0:1000-0:1003\""))
        assertFalse("nub is not the state colour", capBody.contains("0:1006"))
    }

    @Test
    fun `battery bolt is the solid 67 by 92 system-ink glyph with no outline`() {
        val b = IosGeometry.Battery
        val d = IosDigits
        val boltBody = shapeBody("bolt")
        val bolt = vertices(boltBody)
        assertEquals(21, bolt.size)
        for (i in 0 until 21) {
            assertEquals(
                "bolt vertex $i x",
                b.BOLT_POINTS[i * 2].toDouble(),
                bolt[i].first.toDouble(),
                0.001
            )
            assertEquals(
                "bolt vertex $i y",
                b.BOLT_POINTS[i * 2 + 1].toDouble(),
                bolt[i].second.toDouble(),
                0.001
            )
        }
        // The solid no-digit bolt fills the LARGE_BOLT_W x LARGE_BOLT_H box at the body centre.
        val normX = (0 until 21).map { d.BOLT_NORMALIZED[it * 2] }
        val normY = (0 until 21).map { d.BOLT_NORMALIZED[it * 2 + 1] }
        assertEquals(
            "bolt ink width",
            (span(normX) * d.LARGE_BOLT_W).toDouble(),
            span(xs(bolt)).toDouble(),
            0.01
        )
        assertEquals(
            "bolt ink height",
            (span(normY) * d.LARGE_BOLT_H).toDouble(),
            span(ys(bolt)).toDouble(),
            0.01
        )
        assertTrue("bolt is closed", boltBody.contains("isClosed=\"true\""))
        assertFalse("R9 has no bolt outline", boltBody.contains("<Stroke"))
        assertFalse("R9 has no outline colour", boltBody.contains("8C000000"))
        assertTrue("bolt is solid system ink", boltBody.contains("sourcePathIds=\"0:1000-0:1003\""))
        assertFalse("bolt is not the state fill", boltBody.contains("0:1006"))
        assertTrue(
            "bolt opacity is bound to boltOpacity",
            boltBody.contains("propertyKey=\"18\" sourcePathIds=\"0:1000-0:1007\"")
        )
        assertEquals(0f.toDouble(), b.BOLT_OUTLINE.toDouble(), 0.0)
    }

    @Test
    fun `inline bolt sample stays the 40 by 59 knockout`() {
        val d = IosDigits
        val body = shapeBody("inlineBoltSample")
        val paths = Regex(
            "<PointsPath[^>]*isHole=\"true\"[^>]*>(.*?)</PointsPath>",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(body).toList()
        assertEquals(1, paths.size)
        val bolt = vertices(paths.first().groupValues[1])
        assertEquals(21, bolt.size)
        for (i in 0 until 21) {
            assertEquals(
                "inline bolt vertex $i x",
                (d.BOLT_NORMALIZED[i * 2] * d.INLINE_BOLT_W).toDouble(),
                bolt[i].first.toDouble(),
                0.02
            )
            assertEquals(
                "inline bolt vertex $i y",
                (d.BOLT_NORMALIZED[i * 2 + 1] * d.INLINE_BOLT_H).toDouble(),
                bolt[i].second.toDouble(),
                0.02
            )
        }
        assertTrue("inline bolt is closed", paths.first().value.contains("isClosed=\"true\""))
        assertTrue("inline bolt is the knockout hole", body.contains("fillRule=\"clockwise\""))
    }

    @Test
    fun `cell bars and dots match the shared cell`() {
        val c = IosGeometry.Cell

        for (i in c.SINGLE_BAR_HEIGHTS.indices) {
            val name = "singleBar${i + 1}"
            val shapeOpen = shapeTag(name)
            val rect = tag("Rectangle", shapeBody(name))
            val height = c.SINGLE_BAR_HEIGHTS[i]
            assertEquals("$name x", c.barLeft(i).toDouble(), attr(shapeOpen, "x").toDouble(), 0.01)
            assertEquals(
                "$name bottom",
                c.SINGLE_BAR_BOTTOM.toDouble(),
                (attr(shapeOpen, "y") + attr(rect, "height")).toDouble(),
                0.01
            )
            assertEquals("$name width", c.BAR_W.toDouble(), attr(rect, "width").toDouble(), 0.01)
            assertEquals("$name height", height.toDouble(), attr(rect, "height").toDouble(), 0.01)
            assertEquals(
                "$name radius",
                c.CORNER_RADIUS.toDouble(),
                attr(rect, "cornerRadiusTL").toDouble(),
                0.001
            )
        }
        assertEquals("bar 4 top", c.TOP.toDouble(), attr(shapeTag("singleBar4"), "y").toDouble(), 0.01)

        for (i in c.DUAL_BAR_HEIGHTS.indices) {
            val name = "dualBar${i + 1}"
            val shapeOpen = shapeTag(name)
            val rect = tag("Rectangle", shapeBody(name))
            val height = c.DUAL_BAR_HEIGHTS[i]
            assertEquals("$name x", c.dualBarLeft(i).toDouble(), attr(shapeOpen, "x").toDouble(), 0.01)
            assertEquals(
                "$name bottom",
                (c.TOP + c.DUAL_ROW_H).toDouble(),
                (attr(shapeOpen, "y") + attr(rect, "height")).toDouble(),
                0.01
            )
            assertEquals("$name width", c.DUAL_BAR_W.toDouble(), attr(rect, "width").toDouble(), 0.01)
            assertEquals("$name height", height.toDouble(), attr(rect, "height").toDouble(), 0.01)
            assertEquals(
                "$name radius",
                c.DUAL_CORNER_RADIUS.toDouble(),
                attr(rect, "cornerRadiusTL").toDouble(),
                0.001
            )
        }

        for (i in 0 until 4) {
            val name = "dot${i + 1}"
            val shapeOpen = shapeTag(name)
            assertEquals("$name centre x", c.dotCenterX(i).toDouble(), attr(shapeOpen, "x").toDouble(), 0.01)
            assertEquals("$name centre y", c.dotCenterY().toDouble(), attr(shapeOpen, "y").toDouble(), 0.01)
            val ellipse = tag("Ellipse", shapeBody(name))
            assertEquals("$name width", c.DOT_W.toDouble(), attr(ellipse, "width").toDouble(), 0.001)
            assertEquals("$name height", c.DOT_H.toDouble(), attr(ellipse, "height").toDouble(), 0.001)
        }
        assertEquals(
            4,
            Regex("<Shape[^>]*name=\"dot[1-4]\"[^>]*>").findAll(scene).count()
        )
    }

    @Test
    fun `wifi uses the measured arcs, trims, dot and level alphas`() {
        val w = IosGeometry.Wifi

        val outer = tag("Ellipse", shapeBody("wifiLayer1"))
        assertEquals(w.CENTRE_X.toDouble(), attr(outer, "x").toDouble(), 0.001)
        assertEquals(w.CENTRE_Y.toDouble(), attr(outer, "y").toDouble(), 0.001)
        assertEquals((2f * w.OUTER_RADIUS).toDouble(), attr(outer, "width").toDouble(), 0.01)
        assertEquals((2f * w.OUTER_RADIUS).toDouble(), attr(outer, "height").toDouble(), 0.01)
        val outerStroke = tag("Stroke", shapeBody("wifiLayer1"))
        assertEquals(w.OUTER_STROKE.toDouble(), attr(outerStroke, "thickness").toDouble(), 0.001)
        assertTrue(outerStroke.contains("cap=\"round\""))
        assertTrue(outerStroke.contains("join=\"round\""))

        val inner = tag("Ellipse", shapeBody("wifiLayer2"))
        assertEquals(w.CENTRE_X.toDouble(), attr(inner, "x").toDouble(), 0.001)
        assertEquals(w.CENTRE_Y.toDouble(), attr(inner, "y").toDouble(), 0.001)
        assertEquals((2f * w.INNER_RADIUS).toDouble(), attr(inner, "width").toDouble(), 0.01)
        assertEquals((2f * w.INNER_RADIUS).toDouble(), attr(inner, "height").toDouble(), 0.01)
        val innerStroke = tag("Stroke", shapeBody("wifiLayer2"))
        assertEquals(w.INNER_STROKE.toDouble(), attr(innerStroke, "thickness").toDouble(), 0.001)
        assertTrue(innerStroke.contains("cap=\"round\""))
        assertTrue(innerStroke.contains("join=\"round\""))

        val trims = Regex(
            "<TrimPath start=\"0\" end=\"([0-9.]+)\" offset=\"([0-9.]+)\" " +
                "name=\"Trim\" id=\"0:120([67])\""
        ).findAll(scene).associateBy { it.groupValues[3] }
        assertEquals(w.OUTER_TRIM_END.toDouble(), trims.getValue("6").groupValues[1].toDouble(), 0.000001)
        assertEquals(
            w.OUTER_TRIM_OFFSET.toDouble(),
            trims.getValue("6").groupValues[2].toDouble(),
            0.000001
        )
        assertEquals(w.INNER_TRIM_END.toDouble(), trims.getValue("7").groupValues[1].toDouble(), 0.000001)
        assertEquals(
            w.INNER_TRIM_OFFSET.toDouble(),
            trims.getValue("7").groupValues[2].toDouble(),
            0.000001
        )

        val dotBody = shapeBody("wifiDot")
        val dot = vertices(dotBody)
        assertEquals(32, dot.size)
        for (i in 0 until 32) {
            assertEquals("dot vertex $i x", w.DOT_POINTS[i * 2].toDouble(), dot[i].first.toDouble(), 0.001)
            assertEquals(
                "dot vertex $i y",
                w.DOT_POINTS[i * 2 + 1].toDouble(),
                dot[i].second.toDouble(),
                0.001
            )
        }
        assertEquals("dot width", w.DOT_W.toDouble(), span(xs(dot)).toDouble(), 0.01)
        assertEquals("dot height", w.DOT_H.toDouble(), span(ys(dot)).toDouble(), 0.01)
        assertTrue("dot is closed", dotBody.contains("isClosed=\"true\""))

        for (level in 0..3) {
            val pose = animation("WifiPose$level")
            assertEquals(
                "outer alpha level $level",
                w.OUTER_ALPHA[level].toDouble(),
                poseValue(pose, "0:1202").toDouble(),
                0.0001
            )
            assertEquals(
                "inner alpha level $level",
                w.INNER_ALPHA[level].toDouble(),
                poseValue(pose, "0:1203").toDouble(),
                0.0001
            )
        }
    }

    @Test
    fun `IosDigits is ten stroke skeletons with the shared cap, stroke, advance and widths`() {
        val d = IosDigits
        val artboard = artboard("IosDigits")
        var advances = 0f
        for (ch in '0'..'9') advances += d.advance(ch)
        assertEquals((20f + advances).toDouble(), attr(artboard, "width").toDouble(), 0.01)

        var offset = 10f
        var shapes = 0
        for (ch in '0'..'9') {
            val name = "digit$ch"
            val shapeOpen = shapeTag(name)
            assertEquals("digit$ch offset x", offset.toDouble(), attr(shapeOpen, "x").toDouble(), 0.01)
            assertEquals("digit$ch offset y", 10f.toDouble(), attr(shapeOpen, "y").toDouble(), 0.001)

            val body = shapeBody(name)
            // The old filled contours are gone: every digit is stroke-only.
            assertFalse("digit$ch is stroke-only", body.contains("<Fill"))
            val stroke = tag("Stroke", body)
            assertEquals("digit$ch stroke", d.STROKE.toDouble(), attr(stroke, "thickness").toDouble(), 0.001)
            assertTrue("digit$ch round cap", stroke.contains("cap=\"round\""))
            assertTrue("digit$ch round join", stroke.contains("join=\"round\""))

            val paths = Regex(
                "<PointsPath[^>]*>(.*?)</PointsPath>",
                RegexOption.DOT_MATCHES_ALL
            ).findAll(body).toList()
            assertTrue("digit$ch has at least one path", paths.isNotEmpty())
            val points = paths.flatMap { vertices(it.groupValues[1]) }
            val glyph = d.glyph(ch)
            // The drawn ink is the skeleton plus one stroke: half a stroke at each end.
            assertEquals(
                "digit$ch ink width",
                glyph.width.toDouble(),
                (span(xs(points)) + d.STROKE).toDouble(),
                0.05
            )
            assertEquals(
                "digit$ch ink cap",
                d.CAP_HEIGHT.toDouble(),
                (span(ys(points)) + d.STROKE).toDouble(),
                0.05
            )
            shapes++
            offset += glyph.advance
        }
        assertEquals(10, shapes)
        assertFalse("no filled contour digits remain", scene.contains("fillRule=\"evenOdd\""))
    }
}
