package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.min

/**
 * R9 raster evidence: draws battery/cell/Wi-Fi/5G/digits at a known 4x box, measures the
 * non-transparent ink bboxes and asserts the `.pm/out-r9/R9-design.md` ratios. This is the pixel
 * proof of the new geometry (the unit constants are pinned separately in `IosGeometryTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosR9InkRatioTest {

    private companion object {
        const val SS = 4f
        const val ALPHA = 8
    }

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    private data class Run(val left: Int, val right: Int, val length: Int)

    @Test
    fun `battery body, radius and nub match the R9 ratios`() {
        val visual = IosMapping.battery(
            50, 20, false, false, 3, Color.BLACK
        )
        val rendered = render(IosIconKind.BATTERY, visual)
        val ink = inkBox(rendered.bitmap) ?: error("battery drew no ink")
        val fit = rendered.fit

        // A horizontal scan through the body centre sees the body and the separate nub.
        val centreY = ((ink.top + ink.bottom) / 2f).toInt()
        val runs = runs(rendered.bitmap, centreY).filter { it.length > 1 }
        assertEquals("body + nub runs", 2, runs.size)
        val body = runs[0]
        val nub = runs[1]

        // The body height comes from a vertical scan in the body, not the artboard.
        val bodyCentreX = (body.left + body.right) / 2
        val bodySpan = verticalSpan(rendered.bitmap, bodyCentreX)
        val bodyH = bodySpan / fit
        val bodyW = body.length / fit
        assertEquals("body W/H", 1.893f, bodyW / bodyH, 0.03f)
        assertEquals("body H", IosGeometry.Battery.BODY_H, bodyH, 1.5f)
        assertEquals("nub W/H", 0.143f, (nub.length / fit) / bodyH, 0.01f)
        assertEquals(
            "nub gap/body",
            0.036f,
            ((nub.left - body.right) / fit) / bodyH,
            0.008f
        )
        assertEquals("nub H/H", 0.321f, verticalSpan(rendered.bitmap, nub.left + 1) / fit / bodyH, 0.02f)

        // Circular corner: the flat top edge starts one radius in from the body left. The 4-unit
        // tolerance covers the anti-aliased tangent row at 4x.
        val topRow = ink.top.toInt()
        val firstRun = runs(rendered.bitmap, topRow).first()
        val leftInset = (firstRun.left - body.left) / fit
        assertEquals("body corner radius", IosGeometry.Battery.BODY_RADIUS, leftInset, 4.0f)
    }

    @Test
    fun `digits match the R10 cap and traced advances`() {
        // The traced round glyphs overshoot the cap; the flat "1" is the cap reference.
        val cap = digitInk("1").height / SS
        assertEquals(IosDigits.CAP_HEIGHT, cap, 0.6f)

        val body = render(
            IosIconKind.BATTERY,
            IosMapping.battery(50, 20, false, false, 3, Color.BLACK)
        )
        val bodyX = ((IosGeometry.Battery.BODY_LEFT + IosGeometry.Battery.BODY_W / 2f) *
            body.fit + body.dx).toInt()
        val bodyH = verticalSpan(body.bitmap, bodyX) / body.fit
        assertEquals("digit cap / body", 0.607f, cap / bodyH, 0.02f)

        val hundred = digitInk("100")
        assertEquals(
            "100 group width",
            IosDigits.textInkWidth("100") * SS,
            hundred.width,
            1.5f
        )
        assertEquals("100/body width", 1.444f, (hundred.width / SS) / 100f, 0.03f)

        val widths = mapOf(
            '0' to 47.889f, '1' to 28.362f, '2' to 43.117f, '3' to 44.619f, '4' to 49.302f,
            '5' to 44.089f, '6' to 46.828f, '7' to 42.676f, '8' to 47.8f, '9' to 47.27f
        )
        for ((ch, expected) in widths) {
            // 4x AA adds ~1.5 px to the sampled edge; the data width is pinned separately.
            assertEquals("digit $ch ink width", expected * SS, digitInk(ch.toString()).width, 2.0f)
        }
    }

    @Test
    fun `single cell bar heights and block match R9`() {
        val visual = IosVisual(
            part = IosPart.CELL,
            tint = Color.BLACK,
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = 4f
        )
        val rendered = render(IosIconKind.CELL, visual)
        val ink = inkBox(rendered.bitmap) ?: error("cell drew no ink")
        val fit = rendered.fit
        val c = IosGeometry.Cell

        assertEquals("cell block W", c.BLOCK_W, ink.width / fit, 1.5f)
        assertEquals("cell block H", c.SINGLE_BAR_HEIGHTS.max(), ink.height / fit, 1.5f)
        for (i in 0 until 4) {
            val x = ((c.LEFT + i * c.PITCH + c.BAR_W / 2f) * fit + rendered.dx).toInt()
            val height = verticalSpan(rendered.bitmap, x) / fit
            assertEquals("single bar ${i + 1}", c.SINGLE_BAR_HEIGHTS[i], height, 1.5f)
        }
        val barMidY = ((c.SINGLE_BAR_BOTTOM - c.SINGLE_BAR_HEIGHTS[0] / 2f) * fit + rendered.dy).toInt()
        assertEquals("single bar W", c.BAR_W, firstRun(rendered.bitmap, barMidY).length / fit, 1.5f)
    }

    @Test
    fun `dual cell block and dots match R9`() {
        val visual = IosVisual(
            part = IosPart.CELL,
            tint = Color.BLACK,
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellBars = 4f,
            cellDots = 4f
        )
        val rendered = render(IosIconKind.CELL, visual)
        val ink = inkBox(rendered.bitmap) ?: error("dual cell drew no ink")
        val fit = rendered.fit
        val c = IosGeometry.Cell
        assertEquals("dual block W", c.DUAL_BLOCK_W, ink.width / fit, 1.5f)
        assertEquals("dual block H", c.DUAL_BLOCK_H, ink.height / fit, 1.5f)
        val dotRowY = ((c.DOT_TOP + c.DOT_H / 2f) * fit + rendered.dy).toInt()
        val dot = firstRun(rendered.bitmap, dotRowY)
        assertEquals("dot width", c.DOT_W, dot.length / fit, 1.5f)
    }

    @Test
    fun `wifi ink matches the restored 2_0_6 arcs and dot`() {
        val visual = IosVisual(part = IosPart.WIFI, tint = Color.BLACK, wifiLevel = 3f)
        val rendered = render(IosIconKind.WIFI, visual)
        val ink = inkBox(rendered.bitmap) ?: error("wifi drew no ink")
        val expected = IosGeometry.inkBox(IosIconKind.WIFI, visual)
        assertEquals("wifi ink W", expected.width, ink.width / rendered.fit, 1.5f)
        assertEquals("wifi ink H", expected.height, ink.height / rendered.fit, 1.5f)
        // Restored 2.0.6 target: the Wi-Fi ink is 0.9454 of the battery body height.
        assertEquals("wifi ink H / body", 0.9454f, (ink.height / rendered.fit) / 100f, 0.02f)
    }

    @Test
    fun `5G label ink matches the R10 cap and traced width`() {
        val visual = IosVisual(
            part = IosPart.WIFI,
            tint = Color.BLACK,
            wifiSlot = IosWifiSlot.DataType("5G")
        )
        val rendered = render(IosIconKind.WIFI, visual)
        val ink = inkBox(rendered.bitmap) ?: error("5G drew no ink")
        val d = IosGeometry.DataLabel
        // The traced G overshoots the cap box slightly; the width is the ink-left group sum.
        assertEquals("5G geometric ink W", 116.921f, d.widthOf("5G")!!, 0.01f)
        assertEquals("5G ink W", d.widthOf("5G")!!, ink.width / rendered.fit, 2.0f)
        assertTrue("5G ink height ${ink.height / rendered.fit}", ink.height / rendered.fit > d.CAP_HEIGHT)
    }

    // ------------------------------------------------------------------------------------ helpers

    private data class Rendered(val bitmap: Bitmap, val fit: Float, val dx: Float, val dy: Float)

    private fun render(kind: IosIconKind, visual: IosVisual): Rendered {
        val aw = IosGlyphRenderer.artboardWidth(kind, visual)
        val ah = IosGlyphRenderer.artboardHeight(kind, visual)
        val w = ceil(aw * SS).toInt()
        val h = ceil(ah * SS).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val drawable = IosDrawables.create(kind, visual, w, h)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        val fit = min(w / aw, h / ah)
        return Rendered(bitmap, fit, (w - aw * fit) / 2f, (h - ah * fit) / 2f)
    }

    /** Renders a digit group at [SS]x with the traced even-odd contours on a transparent background. */
    private fun digitInk(text: String): Box {
        val pad = 8f
        val inkW = IosDigits.textInkWidth(text)
        val w = ceil((inkW + 2f * pad) * SS).toInt()
        val h = ceil((IosDigits.CAP_HEIGHT + 2f * pad) * SS).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        canvas.scale(SS, SS)
        var pen = pad
        for (ch in text) {
            val glyph = IosDigits.glyph(ch)
            canvas.drawPath(IosDigits.buildPath(glyph, pen, pad), paint)
            pen += glyph.advance
        }
        return inkBox(bitmap) ?: error("digits '$text' drew no ink")
    }

    private fun inkBox(bitmap: Bitmap): Box? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > ALPHA) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        if (maxX < 0 || maxY < 0) return null
        return Box(minX.toFloat(), minY.toFloat(), (maxX + 1).toFloat(), (maxY + 1).toFloat())
    }

    private fun runs(bitmap: Bitmap, y: Int): List<Run> {
        val out = ArrayList<Run>()
        var start = -1
        for (x in 0 until bitmap.width) {
            val on = Color.alpha(bitmap.getPixel(x, y)) > ALPHA
            if (on && start < 0) start = x
            if (!on && start >= 0) {
                out += Run(start, x - 1, x - start)
                start = -1
            }
        }
        if (start >= 0) out += Run(start, bitmap.width - 1, bitmap.width - start)
        return out.filter { it.length > 1 }
    }

    private fun firstRun(bitmap: Bitmap, y: Int): Run {
        val row = runs(bitmap, y)
        require(row.isNotEmpty()) { "no ink on row $y" }
        return row.first()
    }

    private fun verticalSpan(bitmap: Bitmap, x: Int): Float {
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until bitmap.height) {
            if (Color.alpha(bitmap.getPixel(x, y)) > ALPHA) {
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
        }
        require(maxY >= 0) { "no ink in column $x" }
        return (maxY - minY + 1).toFloat()
    }


}
