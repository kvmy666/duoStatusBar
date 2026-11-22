package io.github.kvmy666.duostatusbar.hook

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest
import java.util.Locale

/**
 * Pins the v5 digit source (`.pm/ref/out-r3/digit-v4/digits-v5.json`): the ten glyph widths and
 * advances, the contour layout (first contour outer, further contours counters), the point counts,
 * the percentage widths that must fit the battery body, the normalised bolt box and the knockout
 * colour rule. The renderer tests cover the pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDigitsTest {

    private val widths = mapOf(
        '0' to 37.81f,
        '1' to 20.81f,
        '2' to 38.88f,
        '3' to 40.50f,
        '4' to 42.56f,
        '5' to 37.62f,
        '6' to 42.62f,
        '7' to 34.88f,
        '8' to 37.06f,
        '9' to 43.25f
    )

    private val advances = mapOf(
        '0' to 43.81f,
        '1' to 30.91f,
        '2' to 44.81f,
        '3' to 49.47f,
        '4' to 46.54f,
        '5' to 43.71f,
        '6' to 50.59f,
        '7' to 39.34f,
        '8' to 43.55f,
        '9' to 50.59f
    )

    private val contourCounts = mapOf(
        '0' to 2, '1' to 1, '2' to 1, '3' to 1, '4' to 2,
        '5' to 1, '6' to 2, '7' to 1, '8' to 3, '9' to 2
    )

    private val pointCounts = mapOf(
        '0' to 247, '1' to 63, '2' to 200, '3' to 216, '4' to 153,
        '5' to 268, '6' to 184, '7' to 162, '8' to 315, '9' to 200
    )

    /**
     * The frozen golden digest of the v5 glyph data
     * (`.pm/ref/out-r3/digit-v4/digits-v5.json`).
     *
     * The canonical form is `ch|advance|width|contours` for '0'..'9', one glyph per line:
     * fields at 2dp, each point `x:y` at 2dp, points joined by `,`, contours joined by `;`.
     * The hash therefore pins the advance, the width, the contour split, the point order and each
     * x/y orientation; a transposed point changes the digest.
     */
    private val expectedGlyphDigest =
        "9ab787bc44b3ca9e4b7b88f0c3487c95f93c32096136b7607aac7e105f1f2113"

    private fun canonicalGlyph(ch: Char, transpose: Boolean = false): String {
        val glyph = IosDigits.glyph(ch)
        val builder = StringBuilder()
        builder.append(ch).append('|')
        builder.append("%.2f".format(Locale.ROOT, glyph.advance)).append('|')
        builder.append("%.2f".format(Locale.ROOT, glyph.width)).append('|')
        builder.append(
            glyph.contours.joinToString(";") { contour ->
                val points = StringBuilder()
                var i = 0
                while (i < contour.size) {
                    if (i > 0) points.append(',')
                    val x = if (transpose) contour[i + 1] else contour[i]
                    val y = if (transpose) contour[i] else contour[i + 1]
                    points.append("%.2f:%.2f".format(Locale.ROOT, x, y))
                    i += 2
                }
                points.toString()
            }
        )
        return builder.toString()
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    @Test
    fun `all ten glyphs use the measured widths and advances`() {
        assertEquals(10, widths.size)
        assertEquals(10, advances.size)
        for (ch in '0'..'9') {
            assertEquals("glyph $ch width", widths.getValue(ch), IosDigits.glyph(ch).width, 0.0001f)
            assertEquals(
                "glyph $ch advance",
                advances.getValue(ch),
                IosDigits.glyph(ch).advance,
                0.0001f
            )
        }
    }

    @Test
    fun `glyph geometry matches the frozen digest`() {
        val canonical = ('0'..'9').joinToString("\n") { canonicalGlyph(it) }
        assertEquals("v5 glyph digest changed", expectedGlyphDigest, sha256(canonical))

        // A transposed (y,x) point is a different glyph, so the golden digest must reject it too.
        val transposed = ('0'..'9').joinToString("\n") { canonicalGlyph(it, transpose = true) }
        assertNotEquals(
            "a transposed point must not pass the digest",
            expectedGlyphDigest,
            sha256(transposed)
        )
    }

    @Test
    fun `glyph contour and point counts round-trip the canonical form`() {
        assertEquals(10, contourCounts.size)
        assertEquals(10, pointCounts.size)
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            assertEquals("glyph $ch contour count", contourCounts.getValue(ch), glyph.contours.size)
            val points = glyph.contours.sumOf { it.size / 2 }
            assertEquals("glyph $ch point count", pointCounts.getValue(ch), points)
            for (contour in glyph.contours) {
                assertEquals("glyph $ch odd contour", 0, contour.size % 2)
                assertTrue("glyph $ch short contour", contour.size >= 6)
            }
        }
        assertEquals("total point count", 2008, pointCounts.values.sum())
    }

    @Test
    fun `glyph coordinates stay inside the cell`() {
        // The 16 px/unit traced mask quantises its cap row to 1/16 = 0.0625 units, so the measured
        // top/bottom row lands at 56.94 rather than exactly 57.0. Keep a 0.05-unit guard (56.95)
        // and require the ink to actually reach the cap instead of hiding a short glyph.
        val capLimit = IosDigits.CAP_HEIGHT - 0.05f
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            var maxY = 0f
            for (contour in glyph.contours) {
                for (i in contour.indices step 2) {
                    val x = contour[i]
                    val y = contour[i + 1]
                    maxY = maxOf(maxY, y)
                    assertTrue("glyph $ch x=$x is finite", x.isFinite())
                    assertTrue("glyph $ch y=$y is finite", y.isFinite())
                    assertTrue("glyph $ch x=$x outside 0..${glyph.width}", x in 0f..glyph.width)
                    assertTrue("glyph $ch y=$y outside 0..$capLimit", y in 0f..capLimit)
                }
            }
            assertTrue("glyph $ch does not reach the cap (max y $maxY)", maxY >= 56.94f - 0.001f)
        }
    }

    @Test
    fun `every glyph has one outer contour with at least three points`() {
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            assertTrue("glyph $ch has no contours", glyph.contours.isNotEmpty())
            val outer = glyph.contours.first()
            assertTrue("glyph $ch outer contour has ${outer.size / 2} points", outer.size / 2 >= 3)
            // Counters are separate contours: every digit with a contour count above one must have
            // at least one more closed loop, not a single multi-winding path.
            assertEquals(
                "glyph $ch counter contours",
                contourCounts.getValue(ch) - 1,
                glyph.contours.drop(1).count { it.size / 2 >= 3 }
            )
        }
    }

    @Test
    fun `buildPath is one closed even-odd path with every contour`() {
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            val path = IosDigits.buildPath(glyph, 0f, 0f)
            assertEquals("glyph $ch fill type", Path.FillType.EVEN_ODD, path.fillType)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            assertTrue("glyph $ch path is empty", bounds.width() > 0f && bounds.height() > 0f)
            assertTrue("glyph $ch opens outside the cell", bounds.left >= 0f && bounds.top >= 0f)
            assertTrue("glyph $ch right ${bounds.right}", bounds.right <= glyph.width + 0.001f)
            assertTrue(
                "glyph $ch bottom ${bounds.bottom}",
                bounds.bottom <= IosDigits.CAP_HEIGHT + 0.001f
            )
        }
    }

    @Test
    fun `three digit percentage fits inside the battery body`() {
        val ink = IosDigits.textInkWidth("100")
        val margin = (IosGeometry.Battery.BODY_W - ink) / 2f
        assertTrue("100 % ink $ink with margin $margin", margin >= 10f)
    }

    @Test
    fun `two digits plus the inline bolt fit inside the battery body`() {
        val group = IosDigits.groupWidth("69", withBolt = true)
        val margin = (IosGeometry.Battery.BODY_W - group) / 2f
        assertTrue("69 + bolt group $group with margin $margin", margin >= 10f)
        assertEquals(
            IosDigits.textInkWidth("69") + IosDigits.INLINE_BOLT_GAP + IosDigits.INLINE_BOLT_W,
            group,
            0.0001f
        )
    }

    @Test
    fun `percentage groups use the measured v5 advances`() {
        val body = IosGeometry.Battery.BODY_W
        // Advances 30.91 + 43.81 + 43.81 = 118.53, minus the first/last bearings 5.05 + 3.00
        // = 110.48 ink; plus the 5-unit gap and 40-unit inline bolt = 155.48.
        val hundredInk = IosDigits.textInkWidth("100")
        val hundred = IosDigits.groupWidth("100", withBolt = true)
        // Advances 50.59 + 50.59 = 101.18, minus bearings 3.985 + 3.670 = 93.53 ink;
        // with the inline bolt 138.53.
        val sixtyNineInk = IosDigits.textInkWidth("69")
        val sixtyNine = IosDigits.groupWidth("69", withBolt = true)

        assertEquals(110.48f, hundredInk, 0.01f)
        assertEquals(155.48f, hundred, 0.01f)
        assertEquals(93.53f, sixtyNineInk, 0.01f)
        assertEquals(138.53f, sixtyNine, 0.01f)
        assertTrue("100 + bolt $hundred < body $body", hundred < body)
        assertTrue("100 + bolt $hundred <= body - 8", hundred <= body - 8f)
        assertTrue("69 + bolt $sixtyNine < body $body", sixtyNine < body)
        assertTrue("69 + bolt $sixtyNine <= body - 8", sixtyNine <= body - 8f)
    }

    @Test
    fun `bolt is normalised and the large box is 61 by 96`() {
        assertEquals(61f, IosDigits.LARGE_BOLT_W, 0.0001f)
        assertEquals(96f, IosDigits.LARGE_BOLT_H, 0.0001f)
        assertEquals(3.0f, IosDigits.LARGE_BOLT_OUTLINE, 0.0001f)

        // A unit box around (0.5, 0.5) exposes the normalised point bounds: they must stay in 0..1.
        val unit = RectF()
        IosDigits.boltPath(1f, 1f, 0.5f, 0.5f).computeBounds(unit, true)
        assertTrue("normalised left ${unit.left}", unit.left >= 0f)
        assertTrue("normalised top ${unit.top}", unit.top >= 0f)
        assertTrue("normalised right ${unit.right}", unit.right <= 1f)
        assertTrue("normalised bottom ${unit.bottom}", unit.bottom <= 1f)

        val large = RectF()
        IosDigits.largeBoltPath().computeBounds(large, true)
        val b = IosGeometry.Battery
        assertTrue("large bolt inside the 61 x 96 box", large.width() <= IosDigits.LARGE_BOLT_W + 0.01f)
        assertTrue("large bolt inside the 61 x 96 box", large.height() <= IosDigits.LARGE_BOLT_H + 0.01f)
        assertEquals(b.BODY_LEFT + b.BODY_W / 2f, large.centerX(), 0.01f)
        assertEquals(b.BODY_TOP + b.BODY_H / 2f, large.centerY(), 0.5f)
    }

    @Test
    fun `the large bolt points are the one normalised source`() {
        val points = IosGeometry.Battery.BOLT_POINTS
        val normalised = IosDigits.BOLT_NORMALIZED
        assertEquals("bolt point pairs", 21, points.size / 2)
        assertEquals("normalised point pairs", 21, normalised.size / 2)

        for (i in 0 until 21) {
            val nx = normalised[2 * i]
            val ny = normalised[2 * i + 1]
            assertEquals(
                "bolt[$i].x from normalised[0]",
                66.75f + nx * 61f,
                points[2 * i],
                0.02f
            )
            assertEquals(
                "bolt[$i].y from normalised[1]",
                3.0f + ny * 96f,
                points[2 * i + 1],
                0.02f
            )
        }
    }

    @Test
    fun `the bolt outline fits the frozen artboard`() {
        val b = IosGeometry.Battery
        assertEquals(3.0f, b.BOLT_OUTLINE, 0.0001f)
        assertEquals(3.0f, IosDigits.LARGE_BOLT_OUTLINE, 0.0001f)
        assertTrue(
            "2 * outline + large bolt height must fit the artboard",
            2f * b.BOLT_OUTLINE + IosDigits.LARGE_BOLT_H <= b.ARTBOARD_H + 0.01f
        )
    }

    @Test
    fun `background colour follows the tint luminance`() {
        assertEquals(Color.WHITE, IosDigits.backgroundColour(Color.BLACK))
        assertEquals(Color.BLACK, IosDigits.backgroundColour(Color.WHITE))
        // 127 stays white, 128 flips to black (the strict > 127 rule).
        assertEquals(Color.WHITE, IosDigits.backgroundColour(Color.rgb(127, 127, 127)))
        assertEquals(Color.BLACK, IosDigits.backgroundColour(Color.rgb(128, 128, 128)))
    }
}
