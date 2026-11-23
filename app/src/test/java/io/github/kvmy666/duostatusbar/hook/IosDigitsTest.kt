package io.github.kvmy666.duostatusbar.hook

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Pins the R9 stroke-skeleton digits (`.pm/out-r9/R9-design.md`): cap 60.7, stroke 12.1, equal
 * advance 64.3, the ten per-digit ink widths, a centre line whose stroke reaches the ink box, the
 * 100 group width and the 40 x 59 / 67 x 92 bolt boxes. There is no filled-contour digit API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDigitsTest {

    private val widths = mapOf(
        '0' to 50.8f,
        '1' to 26.7f,
        '2' to 43.0f,
        '3' to 44.0f,
        '4' to 49.2f,
        '5' to 44.0f,
        '6' to 46.1f,
        '7' to 42.5f,
        '8' to 45.7f,
        '9' to 46.8f
    )

    @Test
    fun `all ten glyphs use the R9 widths and one equal advance`() {
        assertEquals(10, widths.size)
        assertEquals(60.7f, IosDigits.CAP_HEIGHT, 0.001f)
        assertEquals(12.1f, IosDigits.STROKE, 0.001f)
        assertEquals(64.3f, IosDigits.ADVANCE, 0.001f)
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            assertEquals("glyph $ch width", widths.getValue(ch), glyph.width, 0.0001f)
            assertEquals("glyph $ch advance", IosDigits.ADVANCE, glyph.advance, 0.0001f)
            assertEquals("glyph $ch inkWidth", widths.getValue(ch), IosDigits.inkWidth(ch), 0.0001f)
            assertEquals("glyph $ch advance()", IosDigits.ADVANCE, IosDigits.advance(ch), 0.0001f)
        }
    }

    @Test
    fun `stroke skeletons reach the ink box`() {
        val r = IosDigits.STROKE / 2f
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            val path = IosDigits.buildPath(glyph, 0f, 0f)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            assertTrue("glyph $ch path is empty", bounds.width() > 0f && bounds.height() > 0f)
            // The skeleton is inset by the stroke radius; the drawn stroke then spans the full
            // 0..width / 0..cap ink box (within a sub-unit tolerance for the curve extrema).
            assertTrue("glyph $ch left ${bounds.left}", abs(bounds.left - r) <= 0.8f)
            assertTrue("glyph $ch right ${bounds.right}", abs(bounds.right - (glyph.width - r)) <= 0.8f)
            assertTrue("glyph $ch top ${bounds.top}", abs(bounds.top - r) <= 0.8f)
            assertTrue(
                "glyph $ch bottom ${bounds.bottom}",
                abs(bounds.bottom - (IosDigits.CAP_HEIGHT - r)) <= 0.8f
            )
            assertTrue("glyph $ch x", bounds.left >= -0.01f && bounds.right <= glyph.width + 0.01f)
            assertTrue("glyph $ch y", bounds.top >= -0.01f && bounds.bottom <= IosDigits.CAP_HEIGHT)
        }
    }

    @Test
    fun `buildPath translates the skeleton and keeps the path as an open stroke`() {
        val glyph = IosDigits.glyph('0')
        val path = IosDigits.buildPath(glyph, 10f, 20f)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val r = IosDigits.STROKE / 2f
        assertEquals(10f + r, bounds.left, 0.8f)
        assertEquals(20f + r, bounds.top, 0.8f)
    }

    @Test
    fun `rounded terminals are a renderer property`() {
        // The path is a centre line; the renderer draws it with round cap/join. The stroke width is
        // pinned here so a caller cannot silently draw a different weight.
        assertEquals(12.1f, IosDigits.STROKE, 0.0001f)
    }

    @Test
    fun `100 fits the body at the R9 group width`() {
        val body = IosGeometry.Battery.BODY_W
        val hundred = IosDigits.textInkWidth("100")
        assertEquals("100 / body", 1.66f, hundred / IosGeometry.UNIT_HEIGHT, 0.02f)
        assertTrue("100 ink $hundred < body $body", hundred < body)
        val margin = (body - hundred) / 2f
        assertTrue("100 margin $margin", margin >= 10f)
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
    fun `percentage groups use the R9 equal advances`() {
        val body = IosGeometry.Battery.BODY_W
        val hundredInk = IosDigits.textInkWidth("100")
        val hundred = IosDigits.groupWidth("100", withBolt = true)
        val sixtyNineInk = IosDigits.textInkWidth("69")
        val sixtyNine = IosDigits.groupWidth("69", withBolt = true)

        // 3 * 64.3 = 192.9, minus the "1" bearing 18.8 and the "0" bearing 6.75.
        assertEquals(167.35f, hundredInk, 0.01f)
        assertEquals(212.35f, hundred, 0.01f)
        // 2 * 64.3 = 128.6, minus the "6" bearing 9.1 and the "9" bearing 8.75.
        assertEquals(110.75f, sixtyNineInk, 0.01f)
        assertEquals(155.75f, sixtyNine, 0.01f)
        assertTrue("100 < body", hundredInk < body)
        assertTrue("100 <= body - 8", hundredInk <= body - 8f)
        assertTrue("69 + bolt < body", sixtyNine < body)
    }

    @Test
    fun `large bolt is the R9 67 by 92 solid box with no outline`() {
        assertEquals(67f, IosDigits.LARGE_BOLT_W, 0.0001f)
        assertEquals(92f, IosDigits.LARGE_BOLT_H, 0.0001f)
        assertEquals(0f, IosDigits.LARGE_BOLT_OUTLINE, 0.0001f)
        assertEquals(0f, IosGeometry.Battery.BOLT_OUTLINE, 0.0001f)

        val unit = RectF()
        IosDigits.boltPath(1f, 1f, 0.5f, 0.5f).computeBounds(unit, true)
        assertTrue("normalised left ${unit.left}", unit.left >= 0f)
        assertTrue("normalised top ${unit.top}", unit.top >= 0f)
        assertTrue("normalised right ${unit.right}", unit.right <= 1f)
        assertTrue("normalised bottom ${unit.bottom}", unit.bottom <= 1f)

        val large = RectF()
        IosDigits.largeBoltPath().computeBounds(large, true)
        val b = IosGeometry.Battery
        assertEquals(IosDigits.LARGE_BOLT_W, large.width(), 1.0f)
        assertEquals(IosDigits.LARGE_BOLT_H, large.height(), 1.0f)
        assertEquals(b.BODY_LEFT + b.BODY_W / 2f, large.centerX(), 0.05f)
        // The normalised point box is not exactly symmetric in y; the ink centre is 0.19 units high.
        assertEquals(50.807f, large.centerY(), 0.05f)
    }

    @Test
    fun `the inline bolt is 40 by 59 at the measured gap`() {
        assertEquals(40f, IosDigits.INLINE_BOLT_W, 0.0001f)
        assertEquals(59f, IosDigits.INLINE_BOLT_H, 0.0001f)
        assertEquals(5f, IosDigits.INLINE_BOLT_GAP, 0.0001f)
        assertEquals(21, IosDigits.BOLT_NORMALIZED.size / 2)
    }
}
