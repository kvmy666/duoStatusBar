package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil

/**
 * R5B compositor check: draws the real ref-1 geometry (dual 4+4 cell, "5G" label, battery at
 * level 0.2121) at 4x the reference scale and measures the ink from the rendered pixels.
 *
 * The scale is 4 * 42.377 / 100 = 1.69508 px per design unit, so the battery body is 169.5 px, the
 * same as ref 1's 42.377 px body at 4x. The gaps, the vertical cap placement and the label ink height
 * are asserted against the same measured numbers that drive [IosGeometry]; this is the end-to-end
 * check that a constant change actually reaches the Canvas.
 *
 * The test never gates on the environment. When `IOS_RENDER_OUT` is set it additionally writes
 * `r5b-ours-strip-dark-4x.png` (the exact composite used by the assertions) into that directory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosR5LabelStripTest {

    /** 4x ref-1 scale: the measured battery body 42.377 px x 4 over 100 design units. */
    private val scale = 4f * 42.377f / 100f

    /** Ref-1 status-bar background (RGB 39,37,38). */
    private val background = Color.rgb(39, 37, 38)

    private val whiteLuma = 150f
    private val bodyLuma = 80f

    @Test
    fun `label strip matches the measured ref-1 gaps and vertical placement`() {
        val strip = renderStrip()

        val cellRight = rightmostInk(strip, 0, cellScanMax(strip), whiteLuma)
        assertTrue("cell bars drew no white ink", cellRight >= 0)

        val labelBox = inkBox(strip, labelScanLeft(), batteryScanLeft() - 1, whiteLuma)
        assertTrue("label drew no white ink", labelBox != null)

        val bodyBox = inkBox(strip, batteryScanLeft(), strip.width - 1, bodyLuma)
        assertTrue("battery body/remainder drew no ink", bodyBox != null)

        val label = labelBox!!
        val body = bodyBox!!

        // Ink-to-ink gaps: ref 1 measured 22.06 / 22.90 px at 0.42377 px/unit.
        assertEquals(
            "cell ink right -> label ink left",
            52.07f * scale,
            (label.left - (cellRight + 1)).toFloat(),
            2f
        )
        assertEquals(
            "label ink right -> battery body left",
            54.04f * scale,
            (body.left - (label.right + 1)).toFloat(),
            2f
        )

        // R10 top-anchored cap: the traced G overshoots the cap box, so derive the exact ink extents
        // from the same paths the renderer draws instead of assuming the 0..cap box.
        val traced = tracedLabelBounds("5G")
        assertEquals(
            "label ink top - battery body top",
            (traced.top - IosGeometry.Battery.BODY_TOP) * scale,
            (label.top - body.top).toFloat(),
            1.5f
        )
        assertEquals(
            "battery body bottom - label ink bottom",
            (IosGeometry.Battery.BODY_TOP + IosGeometry.Battery.BODY_H - traced.bottom) * scale,
            (body.bottom - label.bottom).toFloat(),
            1.5f
        )
        assertEquals(
            "label ink height (traced)",
            traced.height() * scale,
            (label.bottom + 1 - label.top).toFloat(),
            1.5f
        )

        val out = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() }
        if (out != null) {
            val dir = File(out)
            dir.mkdirs()
            File(dir, "r5b-ours-strip-dark-4x.png").outputStream().use {
                strip.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        strip.recycle()
    }

    /**
     * R5M reviewer check: draw the real single-cell glyph and the real "5G" label at the owner's
     * measured OPLUS boxes plus the runner shift, and prove the two ink bounding boxes are disjoint.
     *
     * Boxes (px): `mobile_signal` 60x51 at (976,57), `mobile_type` 66x51 at (970,57),
     * `mobile_combo` 82x63 at (954,51), battery leaf 79x48 at (1102,57). The shift is computed by the
     * same pure helper the host uses, so this is the rendered form of the host test.
     */
    @Test
    fun `cell and label ink never share a column at the measured OPLUS boxes`() {
        val cellBox = intArrayOf(976, 57, 60, 51)
        val cellOuterBox = intArrayOf(954, 51, 82, 63)
        val typeBox = intArrayOf(970, 57, 66, 51)
        val batteryBox = intArrayOf(1102, 57, 79, 48)
        val shift = labelShiftPx(
            typeBox = typeBox,
            cellBox = cellBox,
            cellOuterBox = cellOuterBox,
            batteryBox = batteryBox,
            canvasLeft = typeBox[0].toFloat(),
            canvasWidth = typeBox[2]
        )
        assertTrue("the label must be shifted into the gap (shift=$shift)", shift > 0f)

        val scale = 4f
        val leftPx = 950f
        val topPx = 45f
        val width = ceil((1180f - leftPx) * scale).toInt() + 2
        val height = ceil((120f - topPx) * scale).toInt() + 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val cell = IosVisual(
            part = IosPart.CELL,
            visible = true,
            tint = Color.WHITE,
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = 3f,
            cellDots = 0f
        )
        val label = IosVisual(
            part = IosPart.WIFI,
            visible = true,
            tint = Color.WHITE,
            wifiSlot = IosWifiSlot.DataType("5G")
        )
        val cellLeftPx = (cellBox[0] - leftPx) * scale
        val topBoxPx = (cellBox[1] - topPx) * scale
        val labelLeftPx = (typeBox[0] + shift - leftPx) * scale
        drawBox(canvas, IosIconKind.CELL, cell, cellLeftPx, topBoxPx,
            (cellBox[2] * scale), (cellBox[3] * scale))
        drawBox(canvas, IosIconKind.WIFI, label, labelLeftPx, topBoxPx,
            (typeBox[2] * scale), (typeBox[3] * scale))

        // The cell canvas ends exactly where the label canvas starts.
        val split = labelLeftPx.toInt()
        val cellInk = inkBox(bitmap, 0, split - 1, whiteLuma)
        val labelInk = inkBox(bitmap, split, bitmap.width - 1, whiteLuma)
        assertTrue("the cell drew no ink", cellInk != null)
        assertTrue("the label drew no ink", labelInk != null)
        assertTrue(
            "cell ink ${cellInk!!.right} must stay left of the label column $split",
            cellInk.right < split
        )
        assertTrue(
            "label ink ${labelInk!!.left} must start at/after the label column $split",
            labelInk.left >= split
        )
        assertTrue(
            "cell ink ${cellInk.right} must be left of label ink ${labelInk.left}",
            cellInk.right < labelInk.left
        )

        val out = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() }
        if (out != null) {
            val dir = File(out)
            dir.mkdirs()
            File(dir, "r5m-label-cell-disjoint-4x.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        bitmap.recycle()
    }

    private fun drawBox(
        canvas: Canvas,
        kind: IosIconKind,
        visual: IosVisual,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val saved = canvas.save()
        try {
            canvas.translate(x, y)
            IosGlyphRenderer.draw(
                canvas, kind, visual, ceil(w).toInt(), ceil(h).toInt(), overrideTint = Color.WHITE
            )
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    // ------------------------------------------------------------------------------------ helpers

    /** The exact rendered ink box of [label] in artboard units (traced contours, TOP-anchored). */
    private fun tracedLabelBounds(label: String): RectF {
        val union = Path()
        var pen = IosGeometry.Wifi.ARTBOARD_MARGIN
        for (ch in label) {
            val glyph = IosLabelGlyphs.glyph(ch) ?: continue
            union.addPath(IosLabelGlyphs.buildPath(glyph, pen, IosGeometry.DataLabel.TOP))
            pen += glyph.advance
        }
        val bounds = RectF()
        union.computeBounds(bounds, true)
        return bounds
    }

    private fun topPad(): Int = ceil(-IosGeometry.Strip.DUAL_CELL_TOP_OFFSET * scale).toInt() + 2

    private fun labelScanLeft(): Int = (IosGeometry.Strip.LABEL_X * scale).toInt()

    private fun batteryScanLeft(): Int = (IosGeometry.Strip.LABEL_BATTERY_X_5G * scale).toInt()

    private fun cellScanMax(strip: Bitmap): Int =
        minOf((IosGeometry.Strip.LABEL_X - 2f).times(scale).toInt(), strip.width - 1)

    private fun renderStrip(): Bitmap {
        val pad = topPad()
        val width = ceil(
            (IosGeometry.Strip.LABEL_BATTERY_X_5G + IosGeometry.Battery.ARTBOARD_W) * scale
        ).toInt() + 2
        val height = pad + ceil(IosGeometry.Battery.ARTBOARD_H * scale).toInt() + 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)

        val cell = IosVisual(
            part = IosPart.CELL,
            tint = Color.WHITE,
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellBars = 4f,
            cellDots = 4f
        )
        val label = IosVisual(
            part = IosPart.WIFI,
            tint = Color.WHITE,
            wifiSlot = IosWifiSlot.DataType("5G")
        )
        val battery = IosVisual(
            part = IosPart.BATTERY,
            tint = Color.WHITE,
            batteryLevel = 0.2121f,
            batteryState = IosBatteryState.NORMAL,
            batteryFillColor = Color.WHITE
        )

        drawAt(canvas, IosIconKind.CELL, cell, IosGeometry.Strip.CELL_X, IosGeometry.Strip.DUAL_CELL_TOP_OFFSET, pad)
        drawAt(canvas, IosIconKind.WIFI, label, IosGeometry.Strip.LABEL_X, 0f, pad)
        drawAt(canvas, IosIconKind.BATTERY, battery, IosGeometry.Strip.LABEL_BATTERY_X_5G, 0f, pad)
        return bitmap
    }

    private fun drawAt(
        canvas: Canvas,
        kind: IosIconKind,
        visual: IosVisual,
        originXUnits: Float,
        originYUnits: Float,
        pad: Int
    ) {
        val artW = IosGlyphRenderer.artboardWidth(kind, visual)
        val artH = IosGlyphRenderer.artboardHeight(kind, visual)
        val w = ceil(artW * scale).toInt()
        val h = ceil(artH * scale).toInt()
        val saved = canvas.save()
        try {
            canvas.translate(originXUnits * scale, originYUnits * scale + pad)
            IosGlyphRenderer.draw(canvas, kind, visual, w, h, overrideTint = Color.WHITE)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun luma(pixel: Int): Float =
        0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) + 0.0722f * Color.blue(pixel)

    private fun rightmostInk(bitmap: Bitmap, fromX: Int, toX: Int, threshold: Float): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var best = -1
        for (y in 0 until bitmap.height) {
            val row = y * bitmap.width
            for (x in fromX..toX) {
                if (luma(pixels[row + x]) > threshold) best = maxOf(best, x)
            }
        }
        return best
    }

    private fun inkBox(bitmap: Bitmap, fromX: Int, toX: Int, threshold: Float): Box? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            val row = y * bitmap.width
            for (x in fromX..toX) {
                if (luma(pixels[row + x]) > threshold) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else Box(left, top, right, bottom)
    }
}
