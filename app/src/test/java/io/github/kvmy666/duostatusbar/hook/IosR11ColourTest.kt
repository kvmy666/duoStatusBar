package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * R11 colour rule, measured on the dark iPhone references (`.pm/out-r11/measurements.md` §3-§4):
 *
 *  - the charging battery body is green with **solid white** percentage digits (core 244,255,242),
 *    not a knockout;
 *  - the uncharged remainder is white ink at alpha 0.30 for LOW and CHARGING, and at the R9
 *    ink-luminance alpha (0.50 on dark) for every other state;
 *  - the cellular inactive bar stays 0.25.
 *
 * The raster checks run the real [IosGlyphRenderer] through [IosGlyphDrawable] at native graphics, so
 * they prove the pixels; the pure assertions pin the numbers for the states the raster does not cover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosR11ColourTest {

    private val b = IosGeometry.Battery

    /** The dark charging reference's bar background. */
    private val darkBg = Color.rgb(8, 8, 8)

    // ------------------------------------------------------------------------------ raster

    @Test
    fun `charging dark draws solid white digits over the green fill`() {
        val visual = IosMapping.battery(
            level = 80,
            lowThreshold = 20,
            saver = false,
            plugged = true,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            tint = Color.WHITE,
            percentInside = true
        )
        val bitmap = render(visual, darkBg, scale = 2f)

        // A knockout would show the dark bar or the green fill inside a stroke; the reference core is
        // white. The digit seat must contain white ink and no background pixel.
        assertEquals(
            "charging digits must not be background holes",
            0,
            countBackgroundInDigitSeat(bitmap, "80", withBolt = true)
        )
        assertTrue(
            "charging digits must be solid white",
            countNearWhiteInDigitSeat(bitmap, "80", withBolt = true) > 50
        )

        // The fill around the digits is still the fixed charging green, so the white is an ink layer
        // on top of the fill, not the knockout showing the bar through it.
        val fillX = toPxX(bitmap, b.BODY_LEFT + b.BODY_W * 0.10f)
        val fillY = toPxY(bitmap, b.BODY_TOP + b.BODY_H * 0.85f)
        assertEquals(IosMapping.COLOR_CHARGING, bitmap.getPixel(fillX, fillY))
        bitmap.recycle()
    }

    @Test
    fun `charging remainder raster is 0_30 while plain dark is 0_50`() {
        val charging = IosMapping.battery(
            50, 20, false, true, BatteryManager.BATTERY_STATUS_CHARGING, Color.WHITE
        )
        val normal = IosMapping.battery(
            50, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING, Color.WHITE
        )
        val chargingAlpha = measuredAlpha(render(charging, darkBg))
        val normalAlpha = measuredAlpha(render(normal, darkBg))

        assertEquals("charging remainder alpha", 0.30f, chargingAlpha, 0.03f)
        assertEquals("plain dark remainder alpha", 0.50f, normalAlpha, 0.03f)
        assertTrue(
            "charging remainder must be clearly darker than plain dark ($chargingAlpha vs $normalAlpha)",
            normalAlpha - chargingAlpha > 0.15f
        )
    }

    // -------------------------------------------------------------------------------- pure

    @Test
    fun `remainder alpha is state aware`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        // CHARGING and LOW measure 0.30 whatever the ink; every other state keeps the R9 rule.
        assertEquals(0.30f, IosGeometry.remainderAlpha(white, IosBatteryState.CHARGING), 0.0001f)
        assertEquals(0.50f, IosGeometry.remainderAlpha(white, IosBatteryState.NORMAL), 0.0001f)
        assertEquals(0.30f, IosGeometry.remainderAlpha(white, IosBatteryState.LOW), 0.0001f)
        assertEquals(0.50f, IosGeometry.remainderAlpha(white, IosBatteryState.SAVER), 0.0001f)
        assertEquals(0.50f, IosGeometry.remainderAlpha(white, IosBatteryState.CHARGING_SAVER), 0.0001f)
        // Light mode (dark ink) stays 0.30 for every state.
        assertEquals(0.30f, IosGeometry.remainderAlpha(black, IosBatteryState.NORMAL), 0.0001f)
        assertEquals(0.30f, IosGeometry.remainderAlpha(black, IosBatteryState.SAVER), 0.0001f)
        // UNVERIFIED (no reference): plugged-not-charging falls through to the ink rule.
        assertEquals(
            0.50f,
            IosGeometry.remainderAlpha(white, IosBatteryState.PLUGGED_NOT_CHARGING),
            0.0001f
        )
        // The cellular inactive bar is not part of this change.
        assertEquals(0.25f, IosGeometry.Cell.INACTIVE_ALPHA, 0.0001f)
    }

    // ------------------------------------------------------------------------------ helpers

    /** Renders the real battery drawable into an opaque [bg] bitmap (null = transparent). */
    private fun render(visual: IosVisual, bg: Int?, scale: Float = 1f): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (b.ARTBOARD_W * scale).roundToInt(),
            (b.ARTBOARD_H * scale).roundToInt(),
            Bitmap.Config.ARGB_8888
        )
        if (bg != null) bitmap.eraseColor(bg)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, bitmap.width, bitmap.height)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** The remaining-ink alpha read back from a clean, digit-free and bolt-free remainder patch. */
    private fun measuredAlpha(bitmap: Bitmap): Float {
        val px = bitmap.getPixel(
            toPxX(bitmap, b.BODY_LEFT + b.BODY_W * 0.85f),
            toPxY(bitmap, b.BODY_TOP + b.BODY_H / 2f)
        )
        val bg = Color.red(darkBg)
        return (Color.red(px) - bg) / (255f - bg)
    }

    private fun digitSeatBox(bitmap: Bitmap, text: String, withBolt: Boolean): IntArray {
        val group = IosDigits.groupWidth(text, withBolt)
        val left = b.BODY_LEFT + b.BODY_W / 2f - group / 2f
        val top = b.BODY_TOP + (b.BODY_H - IosDigits.CAP_HEIGHT) / 2f
        return intArrayOf(
            toPxX(bitmap, left),
            toPxY(bitmap, top),
            toPxX(bitmap, left + IosDigits.textInkWidth(text)),
            toPxY(bitmap, top + IosDigits.CAP_HEIGHT)
        )
    }

    private fun countBackgroundInDigitSeat(bitmap: Bitmap, text: String, withBolt: Boolean): Int {
        val box = digitSeatBox(bitmap, text, withBolt)
        var count = 0
        for (y in box[1] until box[3]) {
            for (x in box[0] until box[2]) {
                if (near(bitmap.getPixel(x, y), darkBg, 2)) count++
            }
        }
        return count
    }

    private fun countNearWhiteInDigitSeat(bitmap: Bitmap, text: String, withBolt: Boolean): Int {
        val box = digitSeatBox(bitmap, text, withBolt)
        var count = 0
        for (y in box[1] until box[3]) {
            for (x in box[0] until box[2]) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) > 240 && Color.green(c) > 240 && Color.blue(c) > 240) count++
            }
        }
        return count
    }

    private fun near(a: Int, c: Int, tolerance: Int): Boolean =
        kotlin.math.abs(Color.red(a) - Color.red(c)) <= tolerance &&
            kotlin.math.abs(Color.green(a) - Color.green(c)) <= tolerance &&
            kotlin.math.abs(Color.blue(a) - Color.blue(c)) <= tolerance

    private fun fitScale(bitmap: Bitmap): Float =
        min(bitmap.width / b.ARTBOARD_W, bitmap.height / b.ARTBOARD_H)

    private fun toPxX(bitmap: Bitmap, artboardX: Float): Int {
        val scale = fitScale(bitmap)
        val dx = (bitmap.width - b.ARTBOARD_W * scale) / 2f
        return (artboardX * scale + dx).roundToInt().coerceIn(0, bitmap.width - 1)
    }

    private fun toPxY(bitmap: Bitmap, artboardY: Float): Int {
        val scale = fitScale(bitmap)
        val dy = (bitmap.height - b.ARTBOARD_H * scale) / 2f
        return (artboardY * scale + dy).roundToInt().coerceIn(0, bitmap.height - 1)
    }
}
