package io.github.kvmy666.duostatusbar.hook

import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the R10 traced percentage digits: cap 60.7, the measured ink widths/advances from
 * `rive/ios/glyphs-r10.json`, the ink-left group widths, and the unchanged 40 x 59 / 67 x 92 bolt
 * contract. Each glyph is a filled even-odd cubic path; there is no stroke skeleton.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDigitsTest {

    private val widths = mapOf(
        '0' to 47.889f,
        '1' to 28.362f,
        '2' to 43.117f,
        '3' to 44.619f,
        '4' to 49.302f,
        '5' to 44.089f,
        '6' to 46.828f,
        '7' to 42.676f,
        '8' to 47.8f,
        '9' to 47.27f
    )

    private val advances = mapOf(
        '0' to 54.824f,
        '1' to 41.704f,
        '2' to 50.451f,
        '3' to 51.599f,
        '4' to 56.194f,
        '5' to 50.186f,
        '6' to 52.66f,
        '7' to 49.611f,
        '8' to 53.985f,
        '9' to 54.427f
    )

    @Test
    fun `all ten glyphs use the R10 traced widths and advances`() {
        assertEquals(10, widths.size)
        assertEquals(60.7f, IosDigits.CAP_HEIGHT, 0.001f)
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            assertEquals("glyph $ch width", widths.getValue(ch), glyph.width, 0.0001f)
            assertEquals("glyph $ch advance", advances.getValue(ch), glyph.advance, 0.0001f)
            assertEquals("glyph $ch inkWidth", widths.getValue(ch), IosDigits.inkWidth(ch), 0.0001f)
            assertEquals("glyph $ch advance()", advances.getValue(ch), IosDigits.advance(ch), 0.0001f)
            assertEquals("glyph $ch char", ch, glyph.char)
        }
    }

    @Test
    fun `traced digits are filled even-odd cubic contours`() {
        for (ch in '0'..'9') {
            val path = IosDigits.buildPath(IosDigits.glyph(ch), 0f, 0f)
            assertEquals("glyph $ch fill type", Path.FillType.EVEN_ODD, path.fillType)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            assertTrue("glyph $ch path is empty", bounds.width() > 0f && bounds.height() > 0f)
            // The traced ink-left box is 0..width; a small overshoot is part of the measured curves.
            assertTrue("glyph $ch left ${bounds.left}", bounds.left >= -0.5f)
            assertTrue("glyph $ch right ${bounds.right}", bounds.right <= widths.getValue(ch) + 0.5f)
            assertTrue("glyph $ch top ${bounds.top}", bounds.top >= -0.6f)
            assertTrue("glyph $ch bottom ${bounds.bottom}", bounds.bottom <= IosDigits.CAP_HEIGHT + 0.6f)
        }
    }

    @Test
    fun `buildPath translates the traced glyph`() {
        val path = IosDigits.buildPath(IosDigits.glyph('0'), 10f, 20f)
        val bounds = RectF()
        path.computeBounds(bounds, true)
        assertEquals(10f, bounds.left, 0.01f)
        assertEquals(10f + 47.889f, bounds.right, 0.01f)
        assertEquals(20f - 0.53f, bounds.top, 0.01f)
        assertEquals(20f + 61.23f, bounds.bottom, 0.01f)
    }

    @Test
    fun `100 fits the body at the R10 group width`() {
        val body = IosGeometry.Battery.BODY_W
        val hundred = IosDigits.textInkWidth("100")
        assertEquals(144.417f, hundred, 0.01f)
        assertEquals("100 / body", 1.44417f, hundred / IosGeometry.UNIT_HEIGHT, 0.02f)
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
    fun `percentage groups use the traced ink-left advances`() {
        val body = IosGeometry.Battery.BODY_W
        val hundredInk = IosDigits.textInkWidth("100")
        val hundred = IosDigits.groupWidth("100", withBolt = true)
        val sixtyNineInk = IosDigits.textInkWidth("69")
        val sixtyNine = IosDigits.groupWidth("69", withBolt = true)

        // "100" = 1 advance + 0 advance + 0 ink width = 41.704 + 54.824 + 47.889.
        assertEquals(144.417f, hundredInk, 0.01f)
        assertEquals(189.417f, hundred, 0.01f)
        // "69" = 6 advance + 9 ink width = 52.66 + 47.27.
        assertEquals(99.93f, sixtyNineInk, 0.01f)
        assertEquals(144.93f, sixtyNine, 0.01f)
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
