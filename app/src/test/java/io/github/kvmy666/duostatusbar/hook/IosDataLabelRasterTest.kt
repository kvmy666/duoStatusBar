package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Owner-review evidence for R4B: writes the data-type labels, a dark variant and a family row, plus
 * `measurements.md`, under `IOS_RENDER_OUT` when that environment variable is set. The test is a
 * no-op when the variable is absent, so a normal unit-test run does not touch the filesystem.
 *
 * The family row uses the production Wi-Fi path at the 30 px clock: every cell is the 80x50 slot at
 * 4x, so Wi-Fi, the labels and a standalone "345" (the measured [IosDigits] at the label cap) are
 * directly comparable at the same drawn size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDataLabelRasterTest {

    private val slotWidth = 80
    private val slotHeight = 50
    private val ss = 4
    private val lightBg = 0xFFFFFFFF.toInt()
    private val darkBg = 0xFF100F19.toInt()

    @Test
    fun `writes label evidence when IOS_RENDER_OUT is set`() {
        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() } ?: return

        val out = File(dir)
        out.mkdirs()

        // Light label PNGs, one per supported label.
        for (label in IosGeometry.DataLabel.LABELS) {
            val bitmap = renderProduction(labelVisual(label, Color.BLACK), lightBg)
            write(bitmap, File(out, "r4-label-${fileTag(label)}-light.png"))
            bitmap.recycle()
        }

        // One dark frame so the label is reviewed on the dark bar too (LTE is representative).
        val dark = renderProduction(labelVisual("LTE", Color.WHITE), darkBg)
        write(dark, File(out, "r4-label-lte-dark.png"))
        dark.recycle()

        // Family row: Wi-Fi, every label and the measured digits, all at the same drawn clock.
        val family = familyRow()
        write(family, File(out, "r4-label-family-light.png"))
        family.recycle()

        File(out, "measurements.md").writeText(measurements())
    }

    private fun familyRow(): Bitmap {
        val cellW = slotWidth * ss
        val cellH = slotHeight * ss
        val items = IosGeometry.DataLabel.LABELS.size + 2 // Wi-Fi + labels + digits
        val strip = Bitmap.createBitmap(cellW * items, cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(lightBg)

        val cell = Bitmap.createBitmap(cellW, cellH, Bitmap.Config.ARGB_8888)
        val cellCanvas = Canvas(cell)
        var x = 0

        // 1) the real Wi-Fi glyph, through the production path.
        cellCanvas.drawColor(lightBg)
        drawProductionInto(cellCanvas, cellW, cellH, wifiVisual(Color.BLACK))
        canvas.drawBitmap(cell, x.toFloat(), 0f, null)
        x += cellW

        // 2) every supported label, in the same order readers see them.
        for (label in IosGeometry.DataLabel.LABELS) {
            cellCanvas.drawColor(lightBg)
            drawProductionInto(cellCanvas, cellW, cellH, labelVisual(label, Color.BLACK))
            canvas.drawBitmap(cell, x.toFloat(), 0f, null)
            x += cellW
        }

        // 3) the measured percentage digits, at the label cap on the same clock.
        cellCanvas.drawColor(lightBg)
        val (innerW, innerH) = IosGeometry.Relative.sizeFor(
            IosIconKind.WIFI, cellW, cellH
        )
        val slotScale = minOf(
            innerW / IosGeometry.Wifi.ARTBOARD_W,
            innerH / IosGeometry.Wifi.ARTBOARD_H
        )
        drawDigits(
            cellCanvas,
            "345",
            cellW / 2f,
            cellH / 2f,
            IosGeometry.DataLabel.CAP_HEIGHT * slotScale,
            Color.BLACK
        )
        canvas.drawBitmap(cell, x.toFloat(), 0f, null)

        cell.recycle()
        return strip
    }

    private fun measurements(): String {
        val wifiInk = inkBox(renderProduction(wifiVisual(Color.BLACK), null))
        val (innerW, innerH) = IosGeometry.Relative.sizeFor(
            IosIconKind.WIFI, slotWidth * ss, slotHeight * ss
        )
        val sb = StringBuilder()
        sb.append("# R4B data-type label measurements\n\n")
        sb.append(
            "Rendered through `IosDrawables.create(WIFI, visual, 80*4, 50*4, relativeSize = true)`; " +
                "values are 1x pixels. The label cap is the Wi-Fi ink height; " +
                "`5G+` is expected to be width-limited in a narrow slot.\n\n"
        )
        sb.append(
            "| Label | Slot | Inner box | Label artboard | Ink bbox | Ink height | " +
                "Wi-Fi ink height | Ratio | Limited by |\n"
        )
        sb.append("|---|---|---|---|---|---|---|---|---|\n")
        for (label in IosGeometry.DataLabel.LABELS) {
            val visual = labelVisual(label, Color.BLACK)
            val ink = inkBox(renderProduction(visual, null))
            val artboard = IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, visual)
            val byWidth = innerW / artboard
            val byHeight = innerH / IosGeometry.Wifi.ARTBOARD_H
            val limitedBy = if (byWidth < byHeight) "width" else "height"
            val ratio = ink.height / wifiInk.height
            sb.append(
                String.format(
                    Locale.US,
                    "| %s | %dx%d | %.2fx%.2f | %.2fx%.2f | (%.1f, %.1f)-(%.1f, %.1f) | " +
                        "%.2f | %.2f | %.3f | %s |%n",
                    label,
                    slotWidth,
                    slotHeight,
                    innerW / ss.toFloat(),
                    innerH / ss.toFloat(),
                    artboard,
                    IosGeometry.Wifi.ARTBOARD_H,
                    ink.left,
                    ink.top,
                    ink.right,
                    ink.bottom,
                    ink.height,
                    wifiInk.height,
                    ratio,
                    limitedBy
                )
            )
        }
        sb.append("\n")
        sb.append("Fixtures: `r4-label-{lte,4g,5g,5g-plus,3g,e}-light.png`, ")
        sb.append("`r4-label-lte-dark.png`, `r4-label-family-light.png`.\n")
        sb.append("\nUNVERIFIED: no iOS reference shows the data-type label; owner review required.\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------------------------ helpers

    private fun wifiVisual(tint: Int): IosVisual =
        IosVisual(part = IosPart.WIFI, tint = tint, wifiLevel = 3f)

    private fun labelVisual(label: String, tint: Int): IosVisual = IosVisual(
        part = IosPart.WIFI,
        tint = tint,
        wifiLevel = 3f,
        wifiSlot = IosWifiSlot.DataType(label)
    )

    private fun renderProduction(visual: IosVisual, background: Int?): Bitmap {
        val bw = slotWidth * ss
        val bh = slotHeight * ss
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background != null) canvas.drawColor(background)
        drawProductionInto(canvas, bw, bh, visual)
        return bitmap
    }

    private fun drawProductionInto(canvas: Canvas, width: Int, height: Int, visual: IosVisual) {
        val drawable = IosDrawables.create(
            IosIconKind.WIFI, visual, width, height, relativeSize = true
        )
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
    }

    private fun drawDigits(
        canvas: Canvas,
        text: String,
        centreX: Float,
        centreY: Float,
        capHeight: Float,
        colour: Int
    ) {
        val scale = capHeight / IosDigits.CAP_HEIGHT
        val group = IosDigits.textInkWidth(text)
        val left = -group / 2f
        val top = -IosDigits.CAP_HEIGHT / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = colour
        }
        val saved = canvas.save()
        try {
            canvas.translate(centreX, centreY)
            canvas.scale(scale, scale)
            val first = IosDigits.glyph(text.first())
            var pen = left - IosDigits.bearing(first)
            for (ch in text) {
                val glyph = IosDigits.glyph(ch)
                canvas.drawPath(
                    IosDigits.buildPath(glyph, pen + IosDigits.bearing(glyph), top),
                    paint
                )
                pen += glyph.advance
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fileTag(label: String): String = when (label) {
        "LTE" -> "lte"
        "4G" -> "4g"
        "5G" -> "5g"
        "5G+" -> "5g-plus"
        "3G" -> "3g"
        "E" -> "e"
        else -> label.lowercase(Locale.US)
    }

    private data class InkBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val height: Float get() = bottom - top
    }

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
        assertTrue("no ink in the bitmap", maxX >= 0 && maxY >= 0)
        return InkBox(
            minX / ss.toFloat(),
            minY / ss.toFloat(),
            (maxX + 1) / ss.toFloat(),
            (maxY + 1) / ss.toFloat()
        )
    }
}
