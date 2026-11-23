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
import java.io.File
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * R6 item 6: the percentage digits and the inline bolt are a TRUE knockout.
 *
 * `percentInside` paints the battery body into an isolated layer and CLEARs the digit/bolt contours,
 * so the real background shows through both the charged fill and the neutral remainder. These are
 * real guards (no environment gate):
 *
 *  - on two distinct opaque backgrounds (white and a deterministic multi-colour "photo") every
 *    cleared pixel equals the background exactly (1-2 LSB tolerance at AA edges only), with holes
 *    over both the fill and the remainder at level 50 ("50": the 5 is left of the split, the 0 right);
 *  - the counter of the 0 stays body ink (even-odd clears the stroke, not the counter);
 *  - charging: the inline bolt is a hole and the large bolt is absent;
 *  - NORMAL / CHARGING / LOW / SAVER, black and white tints, all draw body ink and holes;
 *  - percentInside off has no hole and the digit seat stays body ink (R5B regression guard);
 *  - a drawable fade (alpha ~128) keeps the holes on the real background.
 *
 * When `IOS_R6_OUT` is set it also writes 32 evidence PNGs
 * (`digits-<tone>-<state>-<bg>.png`) at 4x the battery artboard:
 * tone = light (black ink, light mode) / dark (white ink), state = normal 50 % / charging 50 % /
 * low 10 % / saver 50 %, bg = white / black / yellow / photo (2 tones x 4 states x 4 backgrounds
 * = 32). Levels are fixed per state so the digits sit over both the fill and the remainder; LOW
 * 10 % is legal (low threshold 20 %) and still leaves a hole. The assertions above run without the
 * env var, so the PNGs are evidence, not proof.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosR6KnockoutEvidenceTest {

    private val b = IosGeometry.Battery

    private enum class Bg { NONE, WHITE, BLACK, YELLOW, PHOTO }

    // ------------------------------------------------------------------------------- real guards

    @Test
    fun `knockout shows the real background over fill and remainder`() {
        for (bg in listOf(Bg.WHITE, Bg.PHOTO)) {
            val off = render(visual(level = 50, inside = false), bg)
            val on = render(visual(level = 50, inside = true), bg)
            val box = bodyBox(on)

            var holes = 0
            var overFill = 0
            var overRemainder = 0
            var notBackground = 0
            val splitX = toPxX(on, b.BODY_LEFT + b.fillWidth(0.5f))
            for (y in box[1] until box[3]) {
                for (x in box[0] until box[2]) {
                    val a = on.getPixel(x, y)
                    val o = off.getPixel(x, y)
                    if (near(a, o, 2)) continue
                    if (matchesBackground(bg, on, x, y)) {
                        holes++
                        if (x < splitX) overFill++ else overRemainder++
                    } else {
                        notBackground++
                    }
                }
            }

            assertTrue("$bg: no cleared holes ($holes)", holes > 200)
            assertTrue("$bg: no holes over the charged fill ($overFill)", overFill > 20)
            assertTrue("$bg: no holes over the remainder ($overRemainder)", overRemainder > 20)
            // Only AA edge pixels may differ from the background; a painted white/black knockout
            // would put every hole in this bucket and fail `holes` on the photo background.
            assertTrue(
                "$bg: ${notBackground} changed pixels are not the background (painted ink?)",
                notBackground <= holes
            )
            off.recycle()
            on.recycle()
        }
    }

    @Test
    fun `the counter of a digit stays body ink`() {
        val off = render(visual(level = 50, inside = false), Bg.PHOTO)
        val on = render(visual(level = 50, inside = true), Bg.PHOTO)

        // Counter centroid of the second glyph ("0"), from its inner contour, in artboard units.
        val glyph = IosDigits.glyph('0')
        require(glyph.contours.size > 1) { "the 0 must have a counter contour" }
        val counter = glyph.contours[1]
        var sx = 0f
        var sy = 0f
        var n = 0
        var i = 0
        while (i < counter.size) {
            sx += counter[i]
            sy += counter[i + 1]
            n++
            i += 2
        }
        val origin = penOrigins("50")[1]
        val ux = origin + sx / n
        val uy = textTop() + sy / n
        val x = toPxX(on, ux)
        val y = toPxY(on, uy)

        val textPixel = on.getPixel(x, y)
        assertTrue(
            "counter was cleared (on=${hex(textPixel)}, off=${hex(off.getPixel(x, y))})",
            near(textPixel, off.getPixel(x, y), 2)
        )
        assertTrue("counter is the background, not body ink", !matchesBackground(Bg.PHOTO, on, x, y))
        off.recycle()
        on.recycle()
    }

    @Test
    fun `charging clears the inline bolt and drops the large bolt`() {
        val charging = IosMapping.battery(
            level = 60,
            lowThreshold = 20,
            saver = false,
            plugged = true,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            tint = Color.BLACK,
            percentInside = true
        )
        val off = render(charging.copy(percentInside = false), Bg.PHOTO)
        val on = render(charging, Bg.PHOTO)

        // The large bolt's top strip is solid body in inside mode (no white bolt, no hole).
        var stripHoles = 0
        for (y in toPxY(on, 4f) until toPxY(on, 18f)) {
            for (x in toPxX(on, 60f) until toPxX(on, 135f)) {
                if (matchesBackground(Bg.PHOTO, on, x, y)) stripHoles++
            }
        }
        assertEquals("large-bolt strip must not be a hole", 0, stripHoles)
        // The off mode fills that strip with the large white bolt; inside mode must not.
        assertTrue(
            "reference: the off mode paints the large bolt",
            countNearWhite(off, 60f, 4f, 135f, 18f) > 50
        )
        assertEquals(
            "the large bolt is still painted white in inside mode",
            0,
            countNearWhite(on, 60f, 4f, 135f, 18f)
        )

        // The inline bolt after the digits is a hole.
        val group = IosDigits.groupWidth("60", withBolt = true)
        val left = b.BODY_LEFT + b.BODY_W / 2f - group / 2f
        val boltLeft = left + IosDigits.textInkWidth("60") + IosDigits.INLINE_BOLT_GAP
        val boltTop = b.BODY_TOP + (b.BODY_H - IosDigits.INLINE_BOLT_H) / 2f
        var boltHoles = 0
        for (y in toPxY(on, boltTop + 2f) until toPxY(on, boltTop + IosDigits.INLINE_BOLT_H - 2f)) {
            for (x in toPxX(on, boltLeft + 2f) until
                toPxX(on, boltLeft + IosDigits.INLINE_BOLT_W - 2f)) {
                if (matchesBackground(Bg.PHOTO, on, x, y)) boltHoles++
            }
        }
        assertTrue("inline bolt is not cleared ($boltHoles)", boltHoles > 50)
        off.recycle()
        on.recycle()
    }

    @Test
    fun `every state and tint draws body ink and holes`() {
        for (tint in listOf(Color.BLACK, Color.WHITE)) {
            for (case in states(tint)) {
                val bitmap = render(case.visual, Bg.PHOTO)
                val box = bodyBox(bitmap)
                var body = 0
                var holes = 0
                for (y in box[1] until box[3]) {
                    for (x in box[0] until box[2]) {
                        if (matchesBackground(Bg.PHOTO, bitmap, x, y)) holes++ else body++
                    }
                }
                assertTrue("${case.name}: no body ink", body > 1000)
                assertTrue("${case.name}: no knockout holes ($holes)", holes > 50)
                // The opaque charged fill reaches x = BODY_LEFT + 3 at every level used here.
                val fillX = toPxX(bitmap, b.BODY_LEFT + 3f)
                val fillY = toPxY(bitmap, b.BODY_TOP + b.BODY_H / 2f)
                val expected = when (case.name) {
                    "normal" -> tint
                    "low" -> IosMapping.COLOR_LOW
                    "saver" -> IosMapping.COLOR_SAVER
                    else -> IosMapping.COLOR_CHARGING
                }
                assertEquals(
                    "${case.name}: charged fill colour",
                    expected,
                    bitmap.getPixel(fillX, fillY)
                )
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `percentInside off has no hole and keeps the digit seat inked`() {
        val transparent = render(visual(level = 69, inside = false), Bg.NONE)
        val box = safeBodyBox(transparent)
        var transparentPixels = 0
        for (y in box[1] until box[3]) {
            for (x in box[0] until box[2]) {
                if (Color.alpha(transparent.getPixel(x, y)) == 0) transparentPixels++
            }
        }
        assertEquals("percentInside off must not clear the body", 0, transparentPixels)

        val opaque = render(visual(level = 69, inside = false), Bg.PHOTO)
        var background = 0
        for (y in box[1] until box[3]) {
            for (x in box[0] until box[2]) {
                if (matchesBackground(Bg.PHOTO, opaque, x, y)) background++
            }
        }
        assertEquals("percentInside off must not punch background holes", 0, background)
        transparent.recycle()
        opaque.recycle()
    }

    @Test
    fun `a drawable fade keeps the holes on the real background`() {
        val faded = render(visual(level = 50, inside = true), Bg.PHOTO, alpha = 128)
        val box = bodyBox(faded)
        var holes = 0
        for (y in box[1] until box[3]) {
            for (x in box[0] until box[2]) {
                if (matchesBackground(Bg.PHOTO, faded, x, y)) holes++
            }
        }
        assertTrue("faded knockout lost its holes ($holes)", holes > 100)

        val fillX = toPxX(faded, b.BODY_LEFT + 3f)
        val fillY = toPxY(faded, b.BODY_TOP + b.BODY_H / 2f)
        val fadedFill = faded.getPixel(fillX, fillY)
        val solid = render(visual(level = 50, inside = true), Bg.PHOTO)
        val solidFill = solid.getPixel(fillX, fillY)
        solid.recycle()
        assertTrue("faded body is not translucent", !near(fadedFill, solidFill, 8))
        assertTrue("faded body equals the background", !matchesBackground(Bg.PHOTO, faded, fillX, fillY))

        // On a transparent canvas the fade keeps the body alpha and leaves the holes at 0.
        val transparent = render(visual(level = 50, inside = true), Bg.NONE, alpha = 128)
        // Sample the charged fill left of the digit group. Drawable alpha 128 gives ~128 fill
        // over ~64 remainder, i.e. ~160 composite body alpha: translucent, never opaque.
        val bodyX = toPxX(transparent, b.BODY_LEFT + 3f)
        val bodyY = toPxY(transparent, b.BODY_TOP + b.BODY_H / 2f)
        val bodyAlpha = Color.alpha(transparent.getPixel(bodyX, bodyY))
        assertTrue("faded body alpha $bodyAlpha", bodyAlpha in 120..200)
        faded.recycle()
        transparent.recycle()
    }

    // ---------------------------------------------------------------------------------- evidence

    @Test
    fun `writes the knockout evidence when IOS_R6_OUT is set`() {
        val dir = System.getenv("IOS_R6_OUT")?.takeIf { it.isNotBlank() } ?: return
        val out = File(dir).apply { mkdirs() }
        val scale = 4f
        for ((tone, tint) in listOf("light" to Color.BLACK, "dark" to Color.WHITE)) {
            for (case in states(tint)) {
                for (bg in listOf(Bg.WHITE, Bg.BLACK, Bg.YELLOW, Bg.PHOTO)) {
                    val bitmap = render(case.visual, bg, scale)
                    val box = bodyBox(bitmap)
                    var holes = 0
                    for (y in box[1] until box[3]) {
                        for (x in box[0] until box[2]) {
                            if (matchesBackground(bg, bitmap, x, y)) holes++
                        }
                    }
                    assertTrue("$tone-${case.name}-$bg has no holes", holes > 50)
                    val name = "digits-$tone-${case.name}-${bg.name.lowercase()}.png"
                    File(out, name).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------- helpers

    private data class Case(val name: String, val visual: IosVisual)

    private fun states(tint: Int): List<Case> = listOf(
        Case(
            "normal",
            IosMapping.battery(50, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING,
                tint, percentInside = true)
        ),
        Case(
            "charging",
            IosMapping.battery(50, 20, false, true, BatteryManager.BATTERY_STATUS_CHARGING,
                tint, percentInside = true)
        ),
        Case(
            "low",
            IosMapping.battery(10, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING,
                tint, percentInside = true)
        ),
        Case(
            "saver",
            IosMapping.battery(50, 20, true, false, BatteryManager.BATTERY_STATUS_DISCHARGING,
                tint, percentInside = true)
        )
    )

    private fun visual(level: Int, inside: Boolean): IosVisual =
        IosMapping.battery(level, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING,
            Color.BLACK, percentInside = inside)

    private fun textTop(): Float = b.BODY_TOP + b.BODY_H / 2f - IosDigits.CAP_HEIGHT / 2f

    /** Ink-edge origins of every glyph in [text], matching drawPercentInside. */
    private fun penOrigins(text: String): List<Float> {
        val group = IosDigits.groupWidth(text, withBolt = false)
        val left = b.BODY_LEFT + b.BODY_W / 2f - group / 2f
        var pen = left - IosDigits.bearing(IosDigits.glyph(text.first()))
        val out = ArrayList<Float>(text.length)
        for (ch in text) {
            val glyph = IosDigits.glyph(ch)
            out += pen + IosDigits.bearing(glyph)
            pen += glyph.advance
        }
        return out
    }

    private fun render(
        visual: IosVisual,
        bg: Bg,
        scale: Float = 1f,
        alpha: Int = 255
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (b.ARTBOARD_W * scale).roundToInt(),
            (b.ARTBOARD_H * scale).roundToInt(),
            Bitmap.Config.ARGB_8888
        )
        if (bg != Bg.NONE) paintBackground(bitmap, bg)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, bitmap.width, bitmap.height)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        if (alpha != 255) drawable.alpha = alpha
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun paintBackground(bitmap: Bitmap, bg: Bg) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                pixels[y * bitmap.width + x] = backgroundPixel(bg, x, y, bitmap.width, bitmap.height)
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun backgroundPixel(bg: Bg, x: Int, y: Int, w: Int, h: Int): Int = when (bg) {
        Bg.WHITE -> Color.WHITE
        Bg.BLACK -> Color.BLACK
        Bg.YELLOW -> Color.rgb(255, 196, 0)
        Bg.PHOTO -> photoPixel(x, y, w, h)
        Bg.NONE -> Color.TRANSPARENT
    }

    /** A deterministic multi-colour "photo": a diagonal gradient plus three coloured blobs. */
    private fun photoPixel(x: Int, y: Int, w: Int, h: Int): Int {
        var r = (x * 5 + y * 2 + 40) and 0xFF
        var g = (x * 2 + y * 7 + 90) and 0xFF
        var b = (x * 9 + y * 3 + 150) and 0xFF
        val blobs = arrayOf(
            floatArrayOf(0.25f * w, 0.30f * h, 0.22f * w, 90f, -40f, 20f),
            floatArrayOf(0.70f * w, 0.70f * h, 0.30f * w, -60f, 110f, -30f),
            floatArrayOf(0.85f * w, 0.20f * h, 0.18f * w, 30f, 40f, 100f)
        )
        for (blob in blobs) {
            val dx = x - blob[0]
            val dy = y - blob[1]
            val rr = blob[2]
            if (dx * dx + dy * dy <= rr * rr) {
                r += blob[3].toInt()
                g += blob[4].toInt()
                b += blob[5].toInt()
            }
        }
        return Color.rgb(r and 0xFF, g and 0xFF, b and 0xFF)
    }

    private fun matchesBackground(bg: Bg, bitmap: Bitmap, x: Int, y: Int): Boolean {
        if (bg == Bg.NONE) return Color.alpha(bitmap.getPixel(x, y)) == 0
        return near(bitmap.getPixel(x, y), backgroundPixel(bg, x, y, bitmap.width, bitmap.height), 2)
    }

    private fun near(a: Int, c: Int, tolerance: Int): Boolean =
        kotlin.math.abs(Color.red(a) - Color.red(c)) <= tolerance &&
            kotlin.math.abs(Color.green(a) - Color.green(c)) <= tolerance &&
            kotlin.math.abs(Color.blue(a) - Color.blue(c)) <= tolerance &&
            kotlin.math.abs(Color.alpha(a) - Color.alpha(c)) <= tolerance

    private fun countNearWhite(
        bitmap: Bitmap,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): Int {
        var count = 0
        for (y in toPxY(bitmap, y0) until toPxY(bitmap, y1)) {
            for (x in toPxX(bitmap, x0) until toPxX(bitmap, x1)) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) > 240 && Color.green(c) > 240 && Color.blue(c) > 240) count++
            }
        }
        return count
    }

    private fun hex(c: Int): String = Integer.toHexString(c)

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

    /** Body rectangle in pixels: [left, top, right, bottom). */
    private fun bodyBox(bitmap: Bitmap): IntArray = intArrayOf(
        toPxX(bitmap, b.BODY_LEFT),
        toPxY(bitmap, b.BODY_TOP),
        toPxX(bitmap, b.BODY_LEFT + b.BODY_W),
        toPxY(bitmap, b.BODY_TOP + b.BODY_H)
    )

    /** Body rectangle inset past the rounded corners, so every pixel is inside the body shape. */
    private fun safeBodyBox(bitmap: Bitmap): IntArray {
        val inset = b.BODY_RADIUS + 2f
        return intArrayOf(
            toPxX(bitmap, b.BODY_LEFT + inset),
            toPxY(bitmap, b.BODY_TOP + inset),
            toPxX(bitmap, b.BODY_LEFT + b.BODY_W - inset),
            toPxY(bitmap, b.BODY_TOP + b.BODY_H - inset)
        )
    }
}
