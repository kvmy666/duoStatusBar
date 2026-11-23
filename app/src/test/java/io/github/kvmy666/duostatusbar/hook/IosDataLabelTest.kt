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
import kotlin.math.min

/**
 * R10 data-type label contract: the measured glyph metrics, the ink-left group widths and the
 * rendered ink at the production Wi-Fi path (`IosDrawables.create(WIFI, ..., 80, 50,
 * relativeSize = true)`, 4x supersample).
 *
 * The shapes/widths/advances are the R10 regular-sheet trace (`R10Glyphs.LABELS`); the plus shape is
 * from the r4 SF strip and its advance is extrapolated, as are the `G` and `E` advances. The label
 * digits are independent data from the percentage [IosDigits] table.
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

        // Golden baseline of the restored 2.0.6 Wi-Fi render through the production path
        // (IosDrawables.create(WIFI, ..., 80*4, 50*4, relativeSize = true), 4x supersample,
        // transparent background). A change to the Wi-Fi glyph or its fit fails here.
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
        assertEquals(listOf("LTE", "4G", "5G", "5G+", "3G", "E", "G"), d.LABELS)
        assertTrue("G is the measured traced glyph and must be supported", d.supports("G"))
        for (label in d.LABELS) assertTrue(label, d.supports(label))
        for (label in listOf("", "L", "WIFI", "lte", "5G++", "NR", "H+")) {
            assertFalse("$label must be unsupported", d.supports(label))
            assertNull("$label has no ink width", d.widthOf(label))
            assertNull("$label has no artboard", d.artboardWidth(label))
        }
    }

    @Test
    fun `cap matches the R10 measured label`() {
        val tallest = IosGeometry.Cell.SINGLE_BAR_HEIGHTS.max()
        val d = IosGeometry.DataLabel
        assertEquals(71.4f, d.CAP_HEIGHT, 0.01f)
        assertEquals("cap / battery body", 0.714f, d.CAP_HEIGHT / IosGeometry.Battery.BODY_H, 0.001f)
        assertEquals("cap / tallest bar", 0.7686f, d.CAP_HEIGHT / tallest, 0.001f)
        assertEquals(92.9f, tallest, 0.0001f)
    }

    @Test
    fun `top is pinned to the measured label and not centred on the Wi-Fi ink`() {
        val d = IosGeometry.DataLabel
        assertEquals(17.4f, d.TOP, 0.01f)
        assertEquals("baseline", 88.8f, d.TOP + d.CAP_HEIGHT, 0.02f)
        val bodyCentre = IosGeometry.Battery.BODY_TOP + IosGeometry.Battery.BODY_H / 2f
        val capCentre = d.TOP + d.CAP_HEIGHT / 2f
        assertEquals("cap centre - body centre", 2.1f, capCentre - bodyCentre, 0.05f)

        // The old centre-on-Wi-Fi formula gives 12.68 at this cap; a regression must fail here.
        val wifiInkTop = IosGeometry.Wifi.ARTBOARD_MARGIN
        val wifiInkBottom = IosGeometry.Wifi.ARTBOARD_H - IosGeometry.Wifi.ARTBOARD_MARGIN
        val centringTop = wifiInkTop + (wifiInkBottom - wifiInkTop - d.CAP_HEIGHT) / 2f
        assertEquals("the centring formula is 12.68", 12.68f, centringTop, 0.05f)
        assertTrue("TOP must not be the Wi-Fi-centred formula", kotlin.math.abs(d.TOP - centringTop) > 1f)
    }

    @Test
    fun `label glyphs use the measured traced widths and advances`() {
        val d = IosGeometry.DataLabel
        val widths = mapOf(
            'L' to 42.522f,
            'T' to 53.516f,
            'E' to 43.714f,
            'G' to 56.138f,
            '+' to 52.688f
        )
        val advances = mapOf(
            'L' to 37.488f,
            'T' to 62.525f,
            'E' to 52.987f,
            'G' to 65.428f,
            '+' to 61.961f
        )
        for ((ch, width) in widths) {
            assertEquals("$ch ink width", width, d.inkWidth(ch), 0.001f)
            assertEquals("$ch traced ink width", width, IosLabelGlyphs.inkWidth(ch), 0.001f)
        }
        for ((ch, advance) in advances) {
            assertEquals("$ch advance", advance, d.advance(ch), 0.001f)
            assertEquals("$ch traced advance", advance, IosLabelGlyphs.advance(ch), 0.001f)
        }
        assertEquals("ink-left bearing is zero", 0f, d.bearing('+'), 0.0001f)
    }

    @Test
    fun `G is one traced contour with an open counter`() {
        val glyph = IosLabelGlyphs.glyph('G')!!
        assertEquals("G traced contours", 1, glyph.contours.size)
        assertEquals("G ink width", 56.138f, glyph.width, 0.001f)
        val path = IosLabelGlyphs.buildPath(glyph, 0f, 0f)
        assertEquals("G fill type", Path.FillType.EVEN_ODD, path.fillType)

        // The counter opens to the right, so the traced single contour encloses no background.
        val bitmap = renderGlyph('G')
        assertEquals("G must not have a closed counter", 0, enclosedBackgroundPixels(bitmap))
    }

    @Test
    fun `label digits come from the label sheet and keep its advances`() {
        val d = IosGeometry.DataLabel
        val widths = mapOf('3' to 49.808f, '4' to 54.015f, '5' to 49.635f)
        val advances = mapOf('3' to 59.346f, '4' to 62.375f, '5' to 60.783f)
        for (ch in charArrayOf('3', '4', '5')) {
            assertEquals("$ch width", widths.getValue(ch), d.inkWidth(ch), 0.001f)
            assertEquals("$ch advance", advances.getValue(ch), d.advance(ch), 0.001f)
        }
        assertEquals(116.921f, d.widthOf("5G")!!, 0.02f)
        assertFalse(
            "label 5 must not reuse the digit 5 width",
            kotlin.math.abs(R10Glyphs.LABELS.getValue('5').width - R10Glyphs.DIGITS.getValue('5').width) < 0.5f
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
            val traced = tracedLabelBox(label)
            val expectedHeight1x = traced.height * scale4x / ss
            val ratioToWifi = ink.height / wifiInk.height

            // 4x supersampling adds ~2 px of anti-aliased fringe per edge, so allow ~0.7 px (1x)
            // slack on the small cap; the exact traced height is pinned separately.
            assertEquals(
                "$label rendered traced ink height in 1x pixels (byWidth $byWidth vs byHeight $byHeight)",
                expectedHeight1x,
                ink.height,
                0.7f
            )
            val limitedByWidth = byWidth < byHeight
            val wifiInkHeight = IosGeometry.inkBox(
                IosIconKind.WIFI,
                baseWifiVisual()
            ).height
            val expectedRatio = (traced.height / wifiInkHeight) * scale4x / byHeight
            assertEquals(
                "$label ink / Wi-Fi ink at the CONTAIN fit",
                expectedRatio,
                ratioToWifi,
                0.02f
            )
            if (label == "5G+") {
                assertTrue("5G+ must be width-limited", limitedByWidth)
            } else {
                assertTrue(
                    "$label must be height-limited (byWidth $byWidth vs byHeight $byHeight)",
                    !limitedByWidth
                )
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

    /** The tight ink box of [label] in design units, from the same paths the renderer draws. */
    private fun tracedLabelBox(label: String): Box {
        val union = Path()
        var pen = IosGeometry.Wifi.ARTBOARD_MARGIN
        for (ch in label) {
            val glyph = IosLabelGlyphs.glyph(ch) ?: continue
            union.addPath(IosLabelGlyphs.buildPath(glyph, pen, IosGeometry.DataLabel.TOP))
            pen += glyph.advance
        }
        val bounds = android.graphics.RectF()
        union.computeBounds(bounds, true)
        return Box(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    /** Rasterises one label glyph into a padded bitmap, 1 unit = 1 px in the ink box. */
    private fun renderGlyph(ch: Char): Bitmap {
        val glyph = IosLabelGlyphs.glyph(ch) ?: error("$ch has no label glyph")
        val w = glyph.width
        val h = IosGeometry.DataLabel.CAP_HEIGHT
        val bw = (w + 2f * glyphPad).toInt()
        val bh = (h + 2f * glyphPad).toInt()
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPath(
            IosLabelGlyphs.buildPath(glyph, glyphPad, glyphPad),
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
