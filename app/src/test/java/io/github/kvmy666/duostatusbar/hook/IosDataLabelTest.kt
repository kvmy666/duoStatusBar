package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest

/**
 * R5B data-type label: the contract, the measured ref-1 metrics, and the rendered ink at the
 * production Wi-Fi path (`IosDrawables.create(WIFI, ..., 80, 50, relativeSize = true)`, 4x
 * supersample).
 *
 * Ref 1 (`.pm/ref/r5/1-real-iphone-5g-dual.jpg`, sha256 `0e009673…`, dark, dual-SIM) is the first
 * real iOS capture of the label (5G). It fixes the cap (77.69), the top-anchored [TOP] (14.95), the
 * baseline (92.64) and the stroke (11.1). The SF strip
 * `.pm/ref/r4/5-sf-labels-LTE-E-4G-5G-5Gplus.jpg` stays the shape/width authority; the fixed bold
 * `IosDigits` 3/4/5 are intentionally not retyped, so our "5" is bolder than ref 1's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDataLabelTest {

    private val slotWidth = 80
    private val slotHeight = 50
    private val ss = 4

    // ------------------------------------------------------------------------ contract and metrics

    @Test
    fun `every supported label has non-empty ink`() {
        for (label in IosGeometry.DataLabel.LABELS) {
            val ink = inkCount(render(labelVisual(label)))
            assertTrue("$label drew no ink", ink > 0)
        }
    }

    @Test
    fun `NONE and unsupported labels draw nothing`() {
        val none = IosVisual(
            part = IosPart.WIFI,
            tint = Color.BLACK,
            wifiLevel = 3f,
            wifiSlot = IosWifiSlot.None
        )
        assertEquals("NONE drew ink", 0, inkCount(render(none)))

        assertEquals("unsupported label drew ink", 0, inkCount(render(labelVisual("XX"))))
        assertEquals("unsupported label drew ink", 0, inkCount(render(labelVisual("LTE2"))))
    }

    @Test
    fun `WIFI still draws the exact Wi-Fi glyph`() {
        val default = IosVisual(part = IosPart.WIFI, tint = Color.BLACK, wifiLevel = 3f)
        val explicit = default.copy(wifiSlot = IosWifiSlot.Wifi)
        val bitmap = render(default)
        assertTrue("default WIFI drew no ink", inkCount(bitmap) > 0)
        assertTrue("explicit WIFI drew no ink", inkCount(render(explicit)) > 0)

        // Golden baseline of the default Wi-Fi render through the production path
        // (IosDrawables.create(WIFI, ..., 80*4, 50*4, relativeSize = true), 4x supersample,
        // transparent background). Any change to the Wi-Fi glyph or its fit fails here; the V2 label
        // work must not move it.
        assertEquals("Wi-Fi 4x ink bbox", "77,39,244,162", rawInkBox(bitmap))
        assertEquals(
            "Wi-Fi alpha channel SHA-256",
            "4d1f3883bf5ebac169f192d17428651edead76107eb9ce9bf39f785b35104373",
            alphaSha256(bitmap)
        )

        assertEquals(
            IosGeometry.Wifi.ARTBOARD_W,
            IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, default),
            0.0001f
        )
        assertEquals(
            IosGeometry.Wifi.ARTBOARD_H,
            IosGlyphRenderer.artboardHeight(IosIconKind.WIFI, default),
            0.0001f
        )
    }

    @Test
    fun `label artboard width and height stay consistent`() {
        val d = IosGeometry.DataLabel
        val wifi = IosGeometry.Wifi
        for (label in d.LABELS) {
            val ink = d.widthOf(label)
            assertNotNull(label, ink)
            val artboard = d.artboardWidth(label)
            assertNotNull(label, artboard)
            assertEquals(
                "$label ink + margins",
                ink!! + 2f * wifi.ARTBOARD_MARGIN,
                artboard!!,
                0.0001f
            )
            assertEquals(
                "$label renderer artboard width",
                artboard,
                IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, labelVisual(label)),
                0.0001f
            )
            assertEquals(
                "$label artboard height stays the Wi-Fi height",
                wifi.ARTBOARD_H,
                IosGlyphRenderer.artboardHeight(IosIconKind.WIFI, labelVisual(label)),
                0.0001f
            )
        }
    }

    @Test
    fun `supports accepts only the measured labels`() {
        val d = IosGeometry.DataLabel
        for (label in d.LABELS) assertTrue(label, d.supports(label))
        for (label in listOf("", "L", "WIFI", "lte", "5G++", "NR", "H+")) {
            assertFalse("$label must be unsupported", d.supports(label))
            assertNull("$label has no ink width", d.widthOf(label))
            assertNull("$label has no artboard", d.artboardWidth(label))
        }
    }

    @Test
    fun `cap matches the measured real-iPhone label`() {
        val tallest = IosGeometry.Cell.SINGLE_BAR_HEIGHTS.max()
        val d = IosGeometry.DataLabel
        // ref 1: 5 cap 32.93 px x 2.360 units/px.
        assertEquals(77.69f, d.CAP_HEIGHT, 0.01f)
        assertEquals("cap / battery body", 0.7769f, d.CAP_HEIGHT / IosGeometry.Battery.BODY_H, 0.001f)
        assertEquals("cap / tallest bar", 0.8135f, d.CAP_HEIGHT / tallest, 0.001f)
        assertEquals(95.5f, tallest, 0.0001f)
    }

    @Test
    fun `top is pinned to the measured label and not centred on the Wi-Fi ink`() {
        val d = IosGeometry.DataLabel
        // ref 1: cap top 14.95, baseline 92.63 (TOP + CAP = 92.64), cap centre 2.79 below body centre.
        assertEquals(14.95f, d.TOP, 0.01f)
        assertEquals("baseline", 92.64f, d.TOP + d.CAP_HEIGHT, 0.02f)
        val bodyCentre = IosGeometry.Battery.BODY_TOP + IosGeometry.Battery.BODY_H / 2f
        val capCentre = d.TOP + d.CAP_HEIGHT / 2f
        assertEquals("cap centre - body centre", 2.79f, capCentre - bodyCentre, 0.05f)

        // The old centre-on-Wi-Fi formula gives 9.54 at this cap; a regression to it must fail here.
        val wifiInkTop = IosGeometry.Wifi.ARTBOARD_MARGIN
        val wifiInkBottom = IosGeometry.Wifi.ARTBOARD_H - IosGeometry.Wifi.ARTBOARD_MARGIN
        val centringTop = wifiInkTop + (wifiInkBottom - wifiInkTop - d.CAP_HEIGHT) / 2f
        assertEquals("the centring formula is 9.54", 9.54f, centringTop, 0.05f)
        assertTrue("TOP must not be the Wi-Fi-centred formula", kotlin.math.abs(d.TOP - centringTop) > 1f)

        // ref 1: median of the 5 stem (9.87) and G stem (12.33); 0.143 cap.
        assertEquals(11.1f, d.STROKE, 0.01f)
        assertEquals("stroke / cap", 0.1429f, d.STROKE / d.CAP_HEIGHT, 0.0005f)
    }

    @Test
    fun `letters use the measured reference widths and the plus is a square`() {
        val d = IosGeometry.DataLabel
        val c = d.CAP_HEIGHT
        // Measured ink widths / reference cap 36: L 22, T 29, E 24, G 34.
        assertEquals(22f / 36f, d.inkWidth('L') / c, 0.0001f)
        assertEquals(29f / 36f, d.inkWidth('T') / c, 0.0001f)
        assertEquals(24f / 36f, d.inkWidth('E') / c, 0.0001f)
        assertEquals(34f / 36f, d.inkWidth('G') / c, 0.0001f)
        // Equal arms use the measured plus HEIGHT 27/36 = 0.750 (the reference width is 28/36 = 0.778).
        assertEquals(27f / 36f, d.inkWidth('+') / c, 0.0001f)
        assertEquals("plus side bearing", 0.06f, d.bearing('+') / c, 0.0001f)

        // The plus is two equal-arm rectangles: the horizontal and vertical spans are identical, and
        // the arm thickness is the common STROKE.
        val arms = d.outlines('+')
        assertEquals("plus has two rectangles", 2, arms.size)
        val boxes = arms.map { bbox(it) }
        val horizontal = boxes.maxByOrNull { it.width }!!
        val vertical = boxes.maxByOrNull { it.height }!!
        assertEquals("plus arms have equal span", horizontal.width, vertical.height, 0.0001f)
        assertEquals("horizontal arm thickness", d.STROKE, horizontal.height, 0.0001f)
        assertEquals("vertical arm thickness", d.STROKE, vertical.width, 0.0001f)
        assertEquals("plus is square", d.inkWidth('+'), d.CAP_HEIGHT * 0.75f, 0.0001f)
    }

    @Test
    fun `G v3 has the measured bar, right stem and open upper aperture`() {
        val d = IosGeometry.DataLabel
        val c = d.CAP_HEIGHT
        val w = d.inkWidth('G')
        val outlines = d.outlines('G')
        assertEquals("G = band + stem + bar", 3, outlines.size)

        val rects = outlines.drop(1).map { bbox(it) }
        // Bar at the measured centre (0.5102 C), thickened from the reference band by the
        // production weight; its inner end reaches into the counter at 0.60653 W.
        val weight = d.STROKE / d.G_REFERENCE_STEM_STROKE
        val bar = rects.maxByOrNull { it.width }!!
        assertEquals("bar centre", 0.5102f * c, (bar.top + bar.bottom) / 2f, 0.01f)
        assertEquals("bar thickness", 2f * 0.06288f * c * weight, bar.height, 0.02f)
        assertEquals("bar inner end", 0.60653f * w, bar.left, 0.02f)
        assertEquals("bar reaches the right ink edge", w, bar.right, 0.01f)

        // Right stem: exactly the digit stroke wide, from the bar down to the measured slanted
        // bottom (0.97497 C on the left, 0.92007 C on the right). This is the stroke a C lacks.
        val stem = rects.maxByOrNull { it.height }!!
        assertEquals("stem width is the digit stroke", w - d.STROKE, stem.left, 0.02f)
        assertEquals("stem right edge", w, stem.right, 0.01f)
        assertEquals("stem starts at the bar", bar.top, stem.top, 0.01f)
        assertEquals("stem bottom", 0.97497f * c, stem.bottom, 0.02f)

        // The weight-1 rebuild is the measured reference letterform: bar y 0.4473..0.5731 C,
        // inner end 0.60653 W, stem left 0.84951 W and bottom 0.97497 C.
        val reference = d.outlines('G', d.G_REFERENCE_STEM_STROKE).drop(1).map { bbox(it) }
        val refBar = reference.maxByOrNull { it.width }!!
        assertEquals(0.5102f * c - 0.06288f * c, refBar.top, 0.01f)
        assertEquals(0.5102f * c + 0.06288f * c, refBar.bottom, 0.01f)
        assertEquals(0.60653f * w, refBar.left, 0.01f)
        val refStem = reference.maxByOrNull { it.height }!!
        assertEquals(0.84951f * w, refStem.left, 0.01f)
        assertEquals(0.97497f * c, refStem.bottom, 0.01f)

        // The counter is not closed (no holes), the upper-right aperture is open, and the right
        // stem is inked below the bar. A C would be transparent at the stem pixel and a "G with a
        // glued corner" fails the bar/stem geometry above.
        val bitmap = renderGlyph('G')
        assertEquals("G must not have a closed counter", 0, enclosedBackgroundPixels(bitmap))
        val apertureX = (glyphPad + 0.87f * w).toInt()
        val apertureY = (glyphPad + 0.35f * c).toInt()
        assertEquals("G upper-right aperture is not open", 0, Color.alpha(bitmap.getPixel(apertureX, apertureY)))
        val stemX = (glyphPad + 0.93f * w).toInt()
        val stemY = (glyphPad + 0.70f * c).toInt()
        assertTrue(
            "G right stem must be inked below the bar (a C is not)",
            Color.alpha(bitmap.getPixel(stemX, stemY)) > 8
        )

    }

    @Test
    fun `digits in labels match IosDigits scaled by the cap`() {
        val d = IosGeometry.DataLabel
        val scale = d.CAP_HEIGHT / IosDigits.CAP_HEIGHT
        for (ch in charArrayOf('3', '4', '5')) {
            val glyph = IosDigits.glyph(ch)
            assertEquals("$ch width", glyph.width * scale, d.inkWidth(ch), 0.0001f)
            assertEquals("$ch advance", glyph.advance * scale, d.advance(ch), 0.0001f)
            assertEquals(
                "$ch bearing",
                IosDigits.bearing(glyph) * scale,
                d.bearing(ch),
                0.0001f
            )
            assertTrue("$ch must be a scaled digit", d.isDigit(ch))
        }
        assertTrue(
            "the label set must exercise the digit path",
            d.LABELS.any { label -> label.any { d.isDigit(it) } }
        )
    }

    // ---------------------------------------------------------------------- production rendered ink

    @Test
    fun `labels fill the production box at their exact CONTAIN fit`() {
        val wifiInk = inkBox(render(baseWifiVisual()))
        assertTrue("Wi-Fi drew no ink", wifiInk.height > 0f)

        // The drawable insets the production 80x50 slot at the 4x render scale; derive the inner box
        // from the same call instead of hard-coding it, then compare in 1x pixels.
        val (innerW4x, innerH4x) = IosGeometry.Relative.sizeFor(
            IosIconKind.WIFI, slotWidth * ss, slotHeight * ss
        )
        val innerW = innerW4x / ss.toFloat()
        val innerH = innerH4x / ss.toFloat()
        val heightScale4x = innerH4x / IosGeometry.Wifi.ARTBOARD_H
        val aaBleed = 0.5f
        for (label in IosGeometry.DataLabel.LABELS) {
            val visual = labelVisual(label)
            val ink = inkBox(render(visual))
            val artboard = IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, visual)
            val byWidth = innerW4x / artboard
            val byHeight = heightScale4x
            val scale4x = minOf(byWidth, byHeight)
            val expectedCap1x = IosGeometry.DataLabel.CAP_HEIGHT * scale4x / ss
            val ratioToWifi = ink.height / wifiInk.height

            // 4x supersampling adds ~2 px of anti-aliased fringe per edge, so allow ~0.7 px (1x)
            // slack on the small cap; the exact ratio is pinned in the constants test above.
            assertEquals(
                "$label rendered cap in 1x pixels (byWidth $byWidth vs byHeight $byHeight)",
                expectedCap1x,
                ink.height,
                0.7f
            )
            // At the measured cap the 80x50 inner box (50.25 x 31.25 px at 1x) no longer fits every
            // label at its height-limited scale: LTE's artboard is 176.28 wide, so it is width-limited
            // together with 5G+. The ratio follows the actual CONTAIN fit:
            //   (cap / Wi-Fi ink) * min(byWidth, byHeight) / byHeight.
            val limitedByWidth = byWidth < byHeight
            val expectedRatio = 0.8198f * minOf(byWidth, byHeight) / byHeight
            assertEquals(
                "$label ink / Wi-Fi ink at the CONTAIN fit",
                expectedRatio,
                ratioToWifi,
                0.02f
            )
            when (label) {
                "5G+" -> {
                    // Honouring the reference plus makes "5G+" wider than the height-limited width.
                    assertTrue("5G+ must be width-limited", limitedByWidth)
                    // Rendered at 4x: ~19.4 px cap, ~0.635 of the measured Wi-Fi ink.
                    assertEquals(
                        "5G+ ink / Wi-Fi ink is the measured ~0.63 (AA slack)",
                        0.635f,
                        ratioToWifi,
                        0.02f
                    )
                }
                "LTE" -> {
                    // Measured-cap consequence: L+T+E ink is 174.28 units wide, so LTE is
                    // width-limited (~22.1 px cap, ~0.724 of the Wi-Fi ink) in the 80x50 slot.
                    assertTrue(
                        "LTE must be width-limited at the measured cap " +
                            "(byWidth $byWidth vs byHeight $byHeight)",
                        limitedByWidth
                    )
                    assertEquals(
                        "LTE ink / Wi-Fi ink at the width-limited cap",
                        0.724f,
                        ratioToWifi,
                        0.02f
                    )
                }
                else -> {
                    assertTrue(
                        "$label must be height-limited (byWidth $byWidth vs byHeight $byHeight)",
                        !limitedByWidth
                    )
                    assertEquals(
                        "$label ink / Wi-Fi ink is the measured 0.820 cap ratio (AA slack)",
                        0.820f,
                        ratioToWifi,
                        0.02f
                    )
                }
            }
            assertTrue(
                "$label ink width ${ink.width} bleeds past the ${innerW}px inner box " +
                    "(allowed AA bleed $aaBleed)",
                ink.width <= innerW + aaBleed
            )
            assertTrue(
                "$label ink height ${ink.height} bleeds past the ${innerH}px inner box " +
                    "(allowed AA bleed $aaBleed)",
                ink.height <= innerH + aaBleed
            )
        }
    }

    // ------------------------------------------------------------------------------------ helpers

    private val glyphPad = 8f

    private fun baseWifiVisual(): IosVisual =
        IosVisual(part = IosPart.WIFI, tint = Color.BLACK, wifiLevel = 3f)

    private fun labelVisual(label: String): IosVisual = IosVisual(
        part = IosPart.WIFI,
        tint = Color.BLACK,
        wifiLevel = 3f,
        wifiSlot = IosWifiSlot.DataType(label)
    )

    private fun render(
        visual: IosVisual,
        background: Int? = null,
        relativeSize: Boolean = true
    ): Bitmap {
        val bw = slotWidth * ss
        val bh = slotHeight * ss
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background != null) canvas.drawColor(background)
        val drawable = IosDrawables.create(
            IosIconKind.WIFI, visual, bw, bh, relativeSize = relativeSize
        )
        drawable.setBounds(0, 0, bw, bh)
        drawable.draw(canvas)
        return bitmap
    }

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    private fun bbox(contour: FloatArray): Box {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < contour.size) {
            minX = minOf(minX, contour[i])
            maxX = maxOf(maxX, contour[i])
            minY = minOf(minY, contour[i + 1])
            maxY = maxOf(maxY, contour[i + 1])
            i += 2
        }
        return Box(minX, minY, maxX, maxY)
    }

    /** Rasterises one glyph outline (no digits) into a padded bitmap, 1 unit = 1 px in the ink box. */
    private fun renderGlyph(ch: Char): Bitmap {
        val d = IosGeometry.DataLabel
        val w = d.inkWidth(ch)
        val h = d.CAP_HEIGHT
        val bw = (w + 2f * glyphPad).toInt()
        val bh = (h + 2f * glyphPad).toInt()
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = Path().apply {
            fillType = Path.FillType.WINDING
            for (contour in d.outlines(ch)) {
                if (contour.size < 6) continue
                moveTo(glyphPad + contour[0], glyphPad + contour[1])
                var i = 2
                while (i < contour.size) {
                    lineTo(glyphPad + contour[i], glyphPad + contour[i + 1])
                    i += 2
                }
                close()
            }
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.BLACK
            }
        )
        return bitmap
    }

    /**
     * Counts background pixels (alpha 0) not reachable from the border by a 4-connected flood: these
     * are enclosed holes (a "6" would score > 0, an open G scores 0).
     */
    private fun enclosedBackgroundPixels(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val background = BooleanArray(w * h) { Color.alpha(pixels[it]) <= 8 }
        val reached = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun seed(index: Int) {
            if (background[index] && !reached[index]) {
                reached[index] = true
                queue[tail++] = index
            }
        }
        for (x in 0 until w) {
            seed(x)
            seed((h - 1) * w + x)
        }
        for (y in 0 until h) {
            seed(y * w)
            seed(y * w + w - 1)
        }
        while (head < tail) {
            val index = queue[head++]
            val x = index % w
            val y = index / w
            if (x > 0) seed(index - 1)
            if (x < w - 1) seed(index + 1)
            if (y > 0) seed(index - w)
            if (y < h - 1) seed(index + w)
        }
        var holes = 0
        for (i in background.indices) if (background[i] && !reached[i]) holes++
        return holes
    }

    private fun inkCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 8 }
    }

    private data class InkBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** The tight opaque bbox in raw bitmap pixels: `left,top,right,bottom` with exclusive edges. */
    private fun rawInkBox(bitmap: Bitmap): String {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        require(maxX >= 0 && maxY >= 0) { "no ink in the bitmap" }
        return "$minX,$minY,${maxX + 1},${maxY + 1}"
    }

    /** SHA-256 of the alpha channel, row-major, one byte per pixel. */
    private fun alphaSha256(bitmap: Bitmap): String {
        val md = MessageDigest.getInstance("SHA-256")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            md.update((Color.alpha(pixel) and 0xFF).toByte())
        }
        return md.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** The tight opaque bbox in 1x pixels; the bitmap is rendered at [ss]x. */
    private fun inkBox(bitmap: Bitmap): InkBox {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        require(maxX >= 0 && maxY >= 0) { "no ink in the bitmap" }
        return InkBox(
            minX / ss.toFloat(),
            minY / ss.toFloat(),
            (maxX + 1) / ss.toFloat(),
            (maxY + 1) / ss.toFloat()
        )
    }
}
