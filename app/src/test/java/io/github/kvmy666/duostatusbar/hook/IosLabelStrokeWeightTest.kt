package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * R10 traced data-label glyph guard and evidence.
 *
 * The R9 stroke-weight contract is retired: labels are now filled, measured cubic contours (there is
 * no label stroke constant). This test rasterises the shipping [IosLabelGlyphs] paths at 8x the label
 * cap and checks each glyph's rendered ink box against the traced width and path extents. It also
 * proves the label `3`/`4`/`5` are independent of the percentage [IosDigits] table.
 *
 * When `IOS_R6_OUT` is set it writes `label-traced-ours.png` (production labels at 4x the 36 px
 * reference cap) for owner review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosLabelStrokeWeightTest {

    private val d = IosGeometry.DataLabel

    /** 8x the label cap: 71.4 x 8 = 571.2, rounded to 571 px. */
    private val capPx = (d.CAP_HEIGHT * 8f).roundToInt()

    private val scale = capPx / d.CAP_HEIGHT

    /** 8 design units of transparent/white gutter around every glyph. */
    private val padUnits = 8f

    private val glyphs = listOf('5', 'G', 'L', 'T', 'E', '4', '3', '+')

    @Test
    fun `traced label glyphs draw their measured ink width`() {
        for (ch in glyphs) {
            val glyph = IosLabelGlyphs.glyph(ch) ?: error("$ch has no traced label glyph")
            val bounds = RectF()
            IosLabelGlyphs.buildPath(glyph, 0f, 0f).computeBounds(bounds, true)
            val bitmap = renderGlyph(ch)
            val mask = mask(bitmap)
            bitmap.recycle()
            val ink = maskBox(mask) ?: error("$ch drew no ink")
            val renderedWidth = ink.width() / scale
            val renderedHeight = ink.height() / scale
            assertTrue("$ch rendered width $renderedWidth must be positive", renderedWidth > 0f)
            assertEquals("$ch rendered ink width", glyph.width, renderedWidth, 0.8f)
            assertEquals("$ch rendered ink height", bounds.height(), renderedHeight, 0.8f)
        }
    }

    @Test
    fun `label digits are independent from the battery digit table`() {
        for (ch in charArrayOf('3', '4', '5')) {
            val label = R10Glyphs.LABELS.getValue(ch)
            val digit = R10Glyphs.DIGITS.getValue(ch)
            assertTrue(
                "label $ch ink width must differ from digit $ch",
                abs(label.width - digit.width) > 0.5f
            )
            val path = IosLabelGlyphs.buildPath(label, 0f, 0f)
            assertEquals("label $ch fill type", Path.FillType.EVEN_ODD, path.fillType)
            assertFalse("label $ch must have ink", path.isEmpty)
        }
    }

    /**
     * Evidence only: draws the production labels through [IosGlyphRenderer] and the measured digits
     * at the label cap on a reference-like dark background, at 4x the 36 px reference cap.
     */
    @Test
    fun `writes the traced label evidence when IOS_R6_OUT is set`() {
        val dir = System.getenv("IOS_R6_OUT")?.takeIf { it.isNotBlank() } ?: return
        val out = File(dir)
        out.mkdirs()

        val referenceCapPx = 36
        val multiplier = 4
        val evidenceCapPx = referenceCapPx * multiplier
        val renderScale = evidenceCapPx / d.CAP_HEIGHT
        val dark = 0xFF100F19.toInt()
        val ink = Color.rgb(236, 236, 240)
        val gap = 44
        val margin = 12

        val items = mutableListOf<Bitmap>()
        for (label in listOf("LTE", "E", "4G", "5G", "5G+")) {
            val artW = IosGeometry.DataLabel.artboardWidth(label)!!
            val artH = IosGeometry.Wifi.ARTBOARD_H
            val w = ceil(artW * renderScale).toInt()
            val h = ceil(artH * renderScale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(dark)
            IosGlyphRenderer.draw(
                canvas,
                IosIconKind.WIFI,
                IosVisual(
                    part = IosPart.WIFI,
                    visible = true,
                    tint = ink,
                    wifiSlot = IosWifiSlot.DataType(label)
                ),
                w,
                h,
                overrideTint = ink
            )
            items += bitmap
        }
        items += digitStrip("345", evidenceCapPx, dark, ink, renderScale)

        val width = margin * 2 + items.sumOf { it.width } + gap * (items.size - 1)
        val height = margin * 2 + items.maxOf { it.height }
        val strip = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(dark)
        var x = margin
        for (bitmap in items) {
            canvas.drawBitmap(bitmap, x.toFloat(), margin.toFloat(), null)
            x += bitmap.width + gap
            bitmap.recycle()
        }
        File(out, "label-traced-ours.png").outputStream().use {
            strip.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        strip.recycle()
    }

    // ---------------------------------------------------------------------------------- measuring

    /** One traced glyph, black on opaque white, at [capPx] cap. */
    private fun renderGlyph(ch: Char): Bitmap {
        val glyph = IosLabelGlyphs.glyph(ch) ?: error("$ch has no traced label glyph")
        val w = ceil((glyph.width + 2f * padUnits) * scale).toInt()
        val h = ceil((d.CAP_HEIGHT + 2f * padUnits) * scale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val saved = canvas.save()
        try {
            canvas.scale(scale, scale)
            canvas.drawPath(IosLabelGlyphs.buildPath(glyph, padUnits, padUnits), paint)
        } finally {
            canvas.restoreToCount(saved)
        }
        return bitmap
    }

    private class Mask(val width: Int, val height: Int, val ink: BooleanArray) {
        fun at(x: Int, y: Int): Boolean = ink[y * width + x]
    }

    private fun mask(bitmap: Bitmap): Mask {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Mask(bitmap.width, bitmap.height, BooleanArray(pixels.size) { luma(pixels[it]) < 128f })
    }

    private fun luma(pixel: Int): Float =
        0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) + 0.0722f * Color.blue(pixel)

    private fun maskBox(mask: Mask): RectF? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (mask.at(x, y)) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        if (maxX < 0 || maxY < 0) return null
        return RectF(minX.toFloat(), minY.toFloat(), (maxX + 1).toFloat(), (maxY + 1).toFloat())
    }

    // ----------------------------------------------------------------------------------- evidence

    private fun digitStrip(text: String, capPx: Int, background: Int, ink: Int, renderScale: Float): Bitmap {
        val unitScale = capPx / d.CAP_HEIGHT
        val inkW = IosDigits.textInkWidth(text)
        val w = ceil((inkW + 2f * IosGeometry.Wifi.ARTBOARD_MARGIN) * renderScale).toInt()
        val h = ceil(IosGeometry.Wifi.ARTBOARD_H * renderScale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ink
        }
        val top = d.TOP
        var pen = IosGeometry.Wifi.ARTBOARD_MARGIN
        val saved = canvas.save()
        try {
            canvas.scale(unitScale, unitScale)
            for (ch in text) {
                val glyph = IosDigits.glyph(ch)
                canvas.drawPath(IosDigits.buildPath(glyph, pen, top), paint)
                pen += glyph.advance
            }
        } finally {
            canvas.restoreToCount(saved)
        }
        return bitmap
    }
}
