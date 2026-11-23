package io.github.kvmy666.duostatusbar.hook

import android.graphics.Path
import android.graphics.PathMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Pins the R10 traced glyph data and the runtime wrappers around it:
 *
 *  - digit contour counts follow the hole rule, and each digit path reproduces its traced cubic
 *    extents (bbox) and metadata;
 *  - measured vs extrapolated advances are flagged honestly;
 *  - the label group widths are the measured ink-left sums;
 *  - the label `5` is independent data from the percentage digit `5`, and the label source file never
 *    references the digit table;
 *  - [IosDigits.buildPath] returns one even-odd path with the expected contour count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosTracedGlyphsTest {

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    @Test
    fun `digits have one contour per outer plus the documented holes`() {
        val holes = mapOf(
            '0' to 1, '1' to 0, '2' to 0, '3' to 0, '4' to 1,
            '5' to 0, '6' to 1, '7' to 0, '8' to 2, '9' to 1
        )
        for (ch in '0'..'9') {
            val glyph = R10Glyphs.DIGITS.getValue(ch)
            assertEquals("digit $ch contours", holes.getValue(ch) + 1, glyph.contours.size)
            assertEquals("digit $ch cap", R10Glyphs.DIGIT_CAP, IosDigits.CAP_HEIGHT, 0.0001f)
            assertEquals("digit $ch width metadata", glyph.width, IosDigits.inkWidth(ch), 0.0001f)
            assertEquals("digit $ch advance metadata", glyph.advance, IosDigits.advance(ch), 0.0001f)

            val path = IosDigits.buildPath(glyph, 0f, 0f)
            val bounds = android.graphics.RectF()
            path.computeBounds(bounds, true)
            val expected = exactBox(glyph)
            assertEquals("digit $ch path left", expected.left, bounds.left, 0.01f)
            assertEquals("digit $ch path top", expected.top, bounds.top, 0.01f)
            assertEquals("digit $ch path right", expected.right, bounds.right, 0.01f)
            assertEquals("digit $ch path bottom", expected.bottom, bounds.bottom, 0.01f)
            assertTrue("digit $ch path has ink", bounds.width() > 1f && bounds.height() > 1f)
        }
    }

    @Test
    fun `measured and extrapolated advances are flagged honestly`() {
        for (ch in charArrayOf('1', '2', '3', '4', '5', '6', '8', '9')) {
            assertTrue("digit $ch advance must be measured", IosDigits.advanceMeasured(ch))
        }
        for (ch in charArrayOf('7', '0')) {
            assertFalse("digit $ch advance is extrapolated", IosDigits.advanceMeasured(ch))
        }
        for (ch in charArrayOf('L', 'T', '5', '4', '3')) {
            assertTrue("label $ch advance must be measured", IosLabelGlyphs.advanceMeasured(ch))
        }
        for (ch in charArrayOf('G', 'E', '+')) {
            assertFalse("label $ch advance is extrapolated", IosLabelGlyphs.advanceMeasured(ch))
        }
    }

    @Test
    fun `label group widths are the measured ink-left sums`() {
        val expected = mapOf(
            "LTE" to 143.727f,
            "4G" to 118.513f,
            "5G" to 116.921f,
            "5G+" to 178.899f,
            "3G" to 115.484f,
            "E" to 43.714f,
            "G" to 56.138f
        )
        for ((label, width) in expected) {
            assertEquals("$label group ink", width, IosGeometry.DataLabel.widthOf(label)!!, 0.02f)
        }
    }

    @Test
    fun `label five is independent data from the digit five`() {
        val label = R10Glyphs.LABELS.getValue('5')
        val digit = R10Glyphs.DIGITS.getValue('5')
        assertNotSame("label and digit contour arrays must be different", label.contours, digit.contours)
        val limit = minOf(label.contours[0].size, digit.contours[0].size)
        var maxDelta = 0f
        for (i in 0 until limit) {
            maxDelta = maxOf(maxDelta, abs(label.contours[0][i] - digit.contours[0][i]))
        }
        if (label.contours[0].size != digit.contours[0].size) maxDelta = Float.MAX_VALUE
        assertTrue(
            "label '5' and digit '5' must differ by >0.5 unit (delta=$maxDelta)",
            maxDelta > 0.5f
        )
        // The label glyph source must not reference the percentage-digit table.
        val source = labelGlyphSource()
        assertFalse(
            "$source must not reference the digit glyph table",
            source.readText().contains("IosDigits")
        )
    }

    @Test
    fun `digit paths are one even-odd fill with the expected contours`() {
        for (ch in '0'..'9') {
            val glyph = R10Glyphs.DIGITS.getValue(ch)
            val path = IosDigits.buildPath(glyph, 7f, 11f)
            assertEquals("digit $ch fill type", Path.FillType.EVEN_ODD, path.fillType)
            var contours = 0
            val measure = PathMeasure(path, false)
            do {
                contours++
            } while (measure.nextContour())
            assertEquals("digit $ch built contours", glyph.contours.size, contours)
        }
    }

    // ------------------------------------------------------------------------------------ helpers

    private fun labelGlyphSource(): File {
        val relative = "io/github/kvmy666/duostatusbar/hook/IosLabelGlyphs.kt"
        val candidates = listOf(
            File("src/main/java/$relative"),
            File("app/src/main/java/$relative"),
            File("../app/src/main/java/$relative"),
            File(System.getProperty("user.dir"), "src/main/java/$relative")
        )
        val direct = candidates.firstOrNull { it.isFile }
        if (direct != null) return direct
        val resource = javaClass.classLoader?.getResource(relative)
        requireNotNull(resource) { "IosLabelGlyphs.kt not found from ${File(".").absolutePath}" }
        return File(resource.toURI())
    }

    /** The exact cubic-path bounding box of [glyph] (derivative roots included). */
    private fun exactBox(glyph: R10Glyphs.Glyph): Box {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (contour in glyph.contours) {
            var i = 0
            while (i + 7 < contour.size) {
                val xs = floatArrayOf(
                    contour[i], contour[i + 2], contour[i + 4], contour[i + 6]
                )
                val ys = floatArrayOf(
                    contour[i + 1], contour[i + 3], contour[i + 5], contour[i + 7]
                )
                for (v in xs) {
                    left = minOf(left, v)
                    right = maxOf(right, v)
                }
                for (v in ys) {
                    top = minOf(top, v)
                    bottom = maxOf(bottom, v)
                }
                left = minOf(left, cubicExtrema(xs).first)
                right = maxOf(right, cubicExtrema(xs).second)
                top = minOf(top, cubicExtrema(ys).first)
                bottom = maxOf(bottom, cubicExtrema(ys).second)
                i += 8
            }
        }
        return Box(left, top, right, bottom)
    }

    /** Min/max of one cubic `p0..p3` over t in [0, 1]. */
    private fun cubicExtrema(p: FloatArray): Pair<Float, Float> {
        var min = minOf(p[0], p[3])
        var max = maxOf(p[0], p[3])
        val a = -p[0] + 3f * p[1] - 3f * p[2] + p[3]
        val b = 2f * (p[0] - 2f * p[1] + p[2])
        val c = p[1] - p[0]
        val roots = ArrayList<Float>()
        if (abs(a) < 1e-9f) {
            if (abs(b) > 1e-9f) roots.add(-c / b)
        } else {
            val disc = b * b - 4f * a * c
            if (disc >= 0f) {
                val root = kotlin.math.sqrt(disc)
                roots.add((-b + root) / (2f * a))
                roots.add((-b - root) / (2f * a))
            }
        }
        for (t in roots) {
            if (t <= 0f || t >= 1f) continue
            val mt = 1f - t
            val value = mt * mt * mt * p[0] +
                3f * mt * mt * t * p[1] +
                3f * mt * t * t * p[2] +
                t * t * t * p[3]
            min = minOf(min, value)
            max = maxOf(max, value)
        }
        return min to max
    }
}
