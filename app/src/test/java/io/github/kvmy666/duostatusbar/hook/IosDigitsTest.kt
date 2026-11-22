package io.github.kvmy666.duostatusbar.hook

import android.graphics.Color
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
 * Pins the R3B digit source (`.pm/ref/out-r3/digits-final.json`): the ten glyph widths, the cell
 * bounds, the percentage widths that must fit the battery body, the normalised bolt box and the
 * knockout colour rule. The renderer tests cover the pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDigitsTest {

    private val widths = mapOf(
        '0' to 43f,
        '1' to 24f,
        '2' to 41f,
        '3' to 39.5f,
        '4' to 44f,
        '5' to 40f,
        '6' to 43f,
        '7' to 38f,
        '8' to 43f,
        '9' to 43f
    )

    private val strokeCounts = mapOf(
        '0' to 1, '1' to 2, '2' to 1, '3' to 1, '4' to 3,
        '5' to 1, '6' to 3, '7' to 1, '8' to 2, '9' to 3
    )

    private val pointCounts = mapOf(
        '0' to 43, '1' to 4, '2' to 29, '3' to 47, '4' to 6,
        '5' to 30, '6' to 51, '7' to 3, '8' to 66, '9' to 52
    )

    /**
     * The frozen golden digest of the current glyph data (`.pm/ref/out-r3/digits-final.json`).
     *
     * The canonical form is `ch|width|points` for '0'..'9', one glyph per line: width is 2dp, and
     * every stroke point is `x,y` at 2dp, flattened across strokes with `;`. The hash therefore pins
     * the width, the point order and each x/y orientation; a transposed point changes the digest.
     */
    private val expectedGlyphDigest =
        "422dd0232062fcdf75435adf9a0e288a2068ccf0e2ee0b8606d71c6151e5ee07"

    private fun canonicalGlyph(ch: Char, transpose: Boolean = false): String {
        val glyph = IosDigits.glyph(ch)
        val builder = StringBuilder()
        builder.append(ch).append('|')
        builder.append("%.2f".format(Locale.ROOT, glyph.width)).append('|')
        var first = true
        for (stroke in glyph.strokes) {
            var i = 0
            while (i < stroke.size) {
                if (!first) builder.append(';')
                val x = if (transpose) stroke[i + 1] else stroke[i]
                val y = if (transpose) stroke[i] else stroke[i + 1]
                builder.append("%.2f,%.2f".format(Locale.ROOT, x, y))
                first = false
                i += 2
            }
        }
        return builder.toString()
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }

    @Test
    fun `all ten glyphs use the measured widths`() {
        assertEquals(10, widths.size)
        for ((ch, width) in widths) {
            assertEquals("glyph $ch width", width, IosDigits.glyph(ch).width, 0.0001f)
        }
    }

    @Test
    fun `glyph geometry matches the frozen digest`() {
        val canonical = ('0'..'9').joinToString("\n") { canonicalGlyph(it) }
        assertEquals("R3B glyph digest changed", expectedGlyphDigest, sha256(canonical))

        // A transposed (y,x) point is a different glyph, so the golden digest must reject it too.
        val transposed = ('0'..'9').joinToString("\n") { canonicalGlyph(it, transpose = true) }
        assertNotEquals(
            "a transposed point must not pass the digest",
            expectedGlyphDigest,
            sha256(transposed)
        )
    }

    @Test
    fun `glyph stroke and point counts round-trip the canonical form`() {
        assertEquals(10, strokeCounts.size)
        assertEquals(10, pointCounts.size)
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            assertEquals("glyph $ch stroke count", strokeCounts.getValue(ch), glyph.strokes.size)
            val points = glyph.strokes.sumOf { it.size / 2 }
            assertEquals("glyph $ch point count", pointCounts.getValue(ch), points)
            for (stroke in glyph.strokes) {
                assertEquals("glyph $ch odd stroke", 0, stroke.size % 2)
            }
            // The canonical form must consume every point exactly once; a dropped or extra point
            // would change this count and the digest above.
            val canonicalPoints = canonicalGlyph(ch).substringAfter('|').substringAfter('|')
            assertEquals(
                "glyph $ch canonical point count",
                pointCounts.getValue(ch),
                canonicalPoints.split(';').size
            )
        }
    }

    @Test
    fun `glyph coordinates stay inside the cell`() {
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            for (stroke in glyph.strokes) {
                assertEquals("glyph $ch odd coordinate count", 0, stroke.size % 2)
                for (i in stroke.indices step 2) {
                    val x = stroke[i]
                    val y = stroke[i + 1]
                    assertTrue("glyph $ch x=$x is finite", x.isFinite())
                    assertTrue("glyph $ch y=$y is finite", y.isFinite())
                    assertTrue("glyph $ch x=$x outside 0..${glyph.width}", x in 0f..glyph.width)
                    assertTrue("glyph $ch y=$y outside 0..${IosDigits.CAP_HEIGHT}", y in 0f..IosDigits.CAP_HEIGHT)
                }
            }
        }
    }

    @Test
    fun `every glyph has at least one stroke with two points`() {
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            assertTrue("glyph $ch has no strokes", glyph.strokes.isNotEmpty())
            assertTrue(
                "glyph $ch has no two-point stroke",
                glyph.strokes.any { it.size >= 4 }
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
    fun `percentage groups keep an 8 unit margin inside the battery body`() {
        val body = IosGeometry.Battery.BODY_W
        // Measured: 100 + bolt = 137.5 + 5 + 40 = 182.5, i.e. 5.0 units per side (10 total).
        val hundred = IosDigits.groupWidth("100", withBolt = true)
        // Measured: 69 + bolt = 95.0 + 5 + 40 = 140.0, i.e. 26.25 units per side.
        val sixtyNine = IosDigits.groupWidth("69", withBolt = true)

        assertEquals(182.5f, hundred, 0.01f)
        assertEquals(140f, sixtyNine, 0.01f)
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
