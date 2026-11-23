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
 * Owner-review evidence for R4B V2: writes the light/dark data-type labels, light/dark family rows and
 * `measurements-v2.md` under `IOS_RENDER_OUT` when that environment variable is set. The test is a
 * no-op when the variable is absent, so a normal unit-test run does not touch the filesystem.
 *
 * The V2 files never overwrite the first R4B evidence (`r4-label-*-light.png` etc); every fixture has
 * a `-v2` suffix. The family row uses the production Wi-Fi path at the 30 px clock: every cell is the
 * 80x50 slot at 4x, so Wi-Fi, the labels and a standalone "345" (the measured [IosDigits] at the
 * label cap) are directly comparable at the same drawn size.
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
    fun `writes V2 label evidence when IOS_RENDER_OUT is set`() {
        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() } ?: return

        val out = File(dir)
        out.mkdirs()

        // Light and dark label PNGs, one per supported label.
        for (label in IosGeometry.DataLabel.LABELS) {
            val light = renderProduction(labelVisual(label, Color.BLACK), lightBg)
            write(light, File(out, "r4-label-${fileTag(label)}-light-v2.png"))
            light.recycle()

            val dark = renderProduction(labelVisual(label, Color.WHITE), darkBg)
            write(dark, File(out, "r4-label-${fileTag(label)}-dark-v2.png"))
            dark.recycle()
        }

        // Family rows: Wi-Fi, every label and the measured digits, all at the same drawn clock.
        val familyLight = familyRow(Color.BLACK, lightBg)
        write(familyLight, File(out, "r4-label-family-light-v2.png"))
        familyLight.recycle()

        val familyDark = familyRow(Color.WHITE, darkBg)
        write(familyDark, File(out, "r4-label-family-dark-v2.png"))
        familyDark.recycle()

        File(out, "measurements-v2.md").writeText(measurements())
    }

    @Test
    fun `writes V3 G evidence when IOS_RENDER_OUT is set`() {
        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() } ?: return
        val out = File(dir)
        out.mkdirs()

        // Per-glyph masks at 10x the reference cap (360 px). The letters use their measured
        // reference stroke so the comparison is weight-normalised; G weight 1 is the measured
        // reference letterform and G bold is the shipping digit weight. Digits are fixed-weight.
        val cap = 360
        val d = IosGeometry.DataLabel
        // Per-letter measured reference strokes (px at cap 36): L stem ~6, T/E/+ ~5.
        val glyphStrokes = mapOf(
            'L' to 5.8f / 36f * d.CAP_HEIGHT,
            'T' to 5.0f / 36f * d.CAP_HEIGHT,
            'E' to 5.0f / 36f * d.CAP_HEIGHT,
            '+' to 5.0f / 36f * d.CAP_HEIGHT,
            'G' to d.G_REFERENCE_STEM_STROKE
        )
        for (ch in listOf('L', 'T', 'E', 'G', '+', '3', '4', '5')) {
            val weight = glyphStrokes[ch] ?: d.STROKE
            val bitmap = glyphBitmap(ch, cap, weight)
            write(bitmap, File(out, "r4-glyph-${glyphTag(ch)}-refweight-v3.png"))
            bitmap.recycle()
        }
        val boldG = glyphBitmap('G', cap, d.STROKE)
        write(boldG, File(out, "r4-glyph-g-bold-v3.png"))
        boldG.recycle()

        // Every label that contains G, at the production 80x50 slot (4x supersample), light + dark.
        for (label in listOf("4G", "5G", "5G+", "3G")) {
            val light = renderProduction(labelVisual(label, Color.BLACK), lightBg)
            write(light, File(out, "r4-label-${fileTag(label)}-light-v3.png"))
            light.recycle()
            val dark = renderProduction(labelVisual(label, Color.WHITE), darkBg)
            write(dark, File(out, "r4-label-${fileTag(label)}-dark-v3.png"))
            dark.recycle()
        }

        // A V3 family row in the same order/shape as the V2 one, so the compare strip can be
        // recomposed without touching the V2 files.
        val familyLight = familyRow(Color.BLACK, lightBg)
        write(familyLight, File(out, "r4-label-family-light-v3.png"))
        familyLight.recycle()
        val familyDark = familyRow(Color.WHITE, darkBg)
        write(familyDark, File(out, "r4-label-family-dark-v3.png"))
        familyDark.recycle()
    }

    /** One glyph, supersampled at [capPx] with transparent background, in design-unit scaling. */
    private fun glyphBitmap(ch: Char, capPx: Int, stroke: Float): Bitmap {
        val d = IosGeometry.DataLabel
        val scale = capPx / d.CAP_HEIGHT
        val inkW = if (d.isDigit(ch)) IosDigits.glyph(ch).width * d.DIGIT_SCALE else d.inkWidth(ch)
        val pad = 8f
        val bw = ((inkW + 2f * pad) * scale).toInt()
        val bh = ((d.CAP_HEIGHT + 2f * pad) * scale).toInt()
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val saved = canvas.save()
        try {
            canvas.scale(scale, scale)
            if (d.isDigit(ch)) {
                canvas.translate(pad, pad)
                canvas.scale(d.DIGIT_SCALE, d.DIGIT_SCALE)
                canvas.drawPath(IosDigits.buildPath(IosDigits.glyph(ch), 0f, 0f), paint)
            } else {
                val path = android.graphics.Path().apply {
                    fillType = android.graphics.Path.FillType.WINDING
                    for (contour in d.outlines(ch, stroke)) {
                        if (contour.size < 6) continue
                        moveTo(pad + contour[0], pad + contour[1])
                        var i = 2
                        while (i < contour.size) {
                            lineTo(pad + contour[i], pad + contour[i + 1])
                            i += 2
                        }
                        close()
                    }
                }
                canvas.drawPath(path, paint)
            }
        } finally {
            canvas.restoreToCount(saved)
        }
        return bitmap
    }

    private fun familyRow(tint: Int, background: Int): Bitmap {
        val cellW = slotWidth * ss
        val cellH = slotHeight * ss
        val items = IosGeometry.DataLabel.LABELS.size + 2 // Wi-Fi + labels + digits
        val strip = Bitmap.createBitmap(cellW * items, cellH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(strip)
        canvas.drawColor(background)

        val cell = Bitmap.createBitmap(cellW, cellH, Bitmap.Config.ARGB_8888)
        val cellCanvas = Canvas(cell)
        var x = 0

        // 1) the real Wi-Fi glyph, through the production path.
        cellCanvas.drawColor(background)
        drawProductionInto(cellCanvas, cellW, cellH, wifiVisual(tint))
        canvas.drawBitmap(cell, x.toFloat(), 0f, null)
        x += cellW

        // 2) every supported label, in the same order readers see them.
        for (label in IosGeometry.DataLabel.LABELS) {
            cellCanvas.drawColor(background)
            drawProductionInto(cellCanvas, cellW, cellH, labelVisual(label, tint))
            canvas.drawBitmap(cell, x.toFloat(), 0f, null)
            x += cellW
        }

        // 3) the measured percentage digits, at the label cap on the same clock.
        cellCanvas.drawColor(background)
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
            tint
        )
        canvas.drawBitmap(cell, x.toFloat(), 0f, null)

        cell.recycle()
        return strip
    }

    private fun measurements(): String {
        val d = IosGeometry.DataLabel
        val wifiInk = inkBox(renderProduction(wifiVisual(Color.BLACK), null))
        val (innerW, innerH) = IosGeometry.Relative.sizeFor(
            IosIconKind.WIFI, slotWidth * ss, slotHeight * ss
        )
        val heightScale4x = innerH / IosGeometry.Wifi.ARTBOARD_H
        val tallestBar = IosGeometry.Cell.SINGLE_BAR_HEIGHTS.max()
        val wifiInkHeight = IosGeometry.Wifi.ARTBOARD_H - 2f * IosGeometry.Wifi.ARTBOARD_MARGIN
        val sb = StringBuilder()
        sb.append("# R5B data-type label — measurements\n\n")
        sb.append(
            "Real-device capture: `.pm/ref/r5/1-real-iphone-5g-dual.jpg` (sha256 `0e009673…`, " +
                "1272x2772, dark, dual-SIM) reads \"5G\" and is the first real iOS status-bar capture " +
                "of this label. Its 50%-threshold measurement is `.pm/ref/out-r5/r5b-measurements.md` " +
                "/ `.json`: battery body H = 42.377 px = 100 units (1 px = 2.360 units, +/-0.5 px = " +
                "+/-1.18 units). Shape/width/spacing authority remains " +
                "`.pm/ref/r4/5-sf-labels-LTE-E-4G-5G-5Gplus.jpg` (SF-style \"LTE E 4G 5G 5G+ 345\", " +
                "light on black). The fixed bold `IosDigits` 3/4/5 are not retyped; R6 item 3 raises " +
                "the letters, plus and G to the same 0.200-cap digit weight (15.54 u).\n\n"
        )
        val cellToLabel = IosGeometry.Strip.CELL_LABEL_GAP
        val labelToBattery = IosGeometry.Strip.LABEL_BATTERY_GAP
        sb.append("## Real capture (ref 1, cap 32.93 px = 77.69 u)\n\n")
        sb.append("| Quantity | px | design units |\n|---|---:|---:|\n")
        sb.append("| Label cap (5 flat top -> baseline) | 32.93 | 77.69 |\n")
        sb.append("| Cap top in label/Wi-Fi artboard | 6.33 | 14.95 |\n")
        sb.append("| Baseline in label/Wi-Fi artboard | 39.25 | 92.63 |\n")
        sb.append("| Label stroke (median 5/G stem) | 4.70 | 11.10 |\n")
        sb.append(
            String.format(Locale.US, "| Cell ink right -> label ink left | 22.06 | %.2f |%n", cellToLabel)
        )
        sb.append(
            String.format(Locale.US, "| Label ink right -> battery body left | 22.90 | %.2f |%n", labelToBattery)
        )
        sb.append("| Cap centre - battery body centre | +1.18 | +2.79 |\n")
        sb.append("| Battery remainder alpha | — | 0.4975 (0.50 confirmed) |\n")
        sb.append("| Battery cap alpha | — | 0.5021 (0.50 confirmed) |\n")
        sb.append("| Inactive bar/dot alpha | — | UNVERIFIED (ref 1 all active) |\n\n")

        sb.append("## SF shape/width reference (cap 36 px, flat letters L/T/E/4)\n\n")
        sb.append("| Quantity | Measured | / cap |\n|---|---:|---:|\n")
        sb.append("| Cap height (L/T/E/4) | 36 px | 1.000 |\n")
        sb.append("| L ink width | 22 px | 0.611 |\n")
        sb.append("| T ink width | 29 px | 0.806 |\n")
        sb.append("| E ink width | 24 px | 0.667 |\n")
        sb.append("| G ink width | 34 px | 0.944 |\n")
        sb.append("| + ink width x height | 28 x 27 px | 0.778 x 0.750 |\n")
        sb.append("| Digit 3 ink width | 25 px | 0.694 |\n")
        sb.append("| Digit 4 ink width | 27 px | 0.750 |\n")
        sb.append("| Digit 5 ink width | 24 px | 0.667 |\n")
        sb.append("| G bar y | 44..48 px | 0.444..0.556 |\n")
        sb.append("| G bar left | x204 (W 184..217) | 0.588 W |\n")
        sb.append("| G spur bottom | y59.5 | 0.875 |\n")
        sb.append("| G upper-right terminal | ~y33.5 | ~0.14 |\n")
        sb.append("| Gap T..E | 3 px | 0.083 |\n")
        sb.append("| Gap 4..G | 4 px | 0.111 |\n")
        sb.append("| Gap 5..G | 5 px | 0.139 |\n")
        sb.append("| Gap G..+ | 6 px | 0.167 |\n")
        sb.append("| Gap 3..4 | 4 px | 0.111 |\n")
        sb.append("| Gap 4..5 | 5 px | 0.139 |\n\n")
        sb.append("## Our constants\n\n")
        sb.append("| Quantity | Value | Source |\n|---|---:|---|\n")
        sb.append(
            String.format(
                Locale.US,
                "| Cap height | %.2f | measured ref-1 5 cap 32.93 px x 2.360 (0.777 of the body) |\n",
                d.CAP_HEIGHT
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Stroke | %.2f | R6 item 3: IosDigits.RUN_STROKE %.2f x (%.2f / 57) = %.2f cap, " +
                    "matching the battery digits; the R5B ref-1 SF label weight 11.10 (0.143 cap) " +
                    "is provenance only |\n",
                d.STROKE,
                IosDigits.RUN_STROKE,
                d.CAP_HEIGHT,
                d.STROKE / d.CAP_HEIGHT
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Cap-box top | %.2f | measured ref-1 cap top (top-anchored); baseline %.2f |\n",
                d.TOP,
                d.TOP + d.CAP_HEIGHT
            )
        )
        sb.append(String.format(Locale.US, "| L width | %.2f | 0.611 x cap |\n", d.inkWidth('L')))
        sb.append(String.format(Locale.US, "| T width | %.2f | 0.806 x cap |\n", d.inkWidth('T')))
        sb.append(String.format(Locale.US, "| E width | %.2f | 0.667 x cap |\n", d.inkWidth('E')))
        sb.append(String.format(Locale.US, "| G width | %.2f | 0.944 x cap |\n", d.inkWidth('G')))
        sb.append(
            String.format(
                Locale.US,
                "| + width | %.2f | 0.750 x cap square (measured height; reference width 0.778) |\n",
                d.inkWidth('+')
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Digit scale | %.4f | cap / %s |\n",
                d.DIGIT_SCALE,
                IosDigits.CAP_HEIGHT
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Letter side bearing | %.3f | 0.04 x cap (measured T..E 0.083 cap gap) |\n",
                d.bearing('L')
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Plus side bearing | %.3f | 0.06 x cap (G bearing 0.04 -> G..+ gap 0.10 cap) |\n",
                d.bearing('+')
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Cap / tallest bar | %.4f | %.2f / %.1f |\n",
                d.CAP_HEIGHT / tallestBar,
                d.CAP_HEIGHT,
                tallestBar
            )
        )
        sb.append(
            String.format(
                Locale.US,
                "| Cap / Wi-Fi ink | %.4f | %.2f / %.2f |\n\n",
                d.CAP_HEIGHT / wifiInkHeight,
                d.CAP_HEIGHT,
                wifiInkHeight
            )
        )

        sb.append("## Rendered labels at the production 80x50 slot (4x supersample, 1x pixels)\n\n")
        sb.append(
            "| Label | Artboard | Ink bbox | Ink height | / Wi-Fi ink | Limited by |\n" +
                "|---|---:|---|---:|---:|---|\n"
        )
        for (label in d.LABELS) {
            val visual = labelVisual(label, Color.BLACK)
            val ink = inkBox(renderProduction(visual, null))
            val artboard = IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, visual)
            val byWidth = innerW / artboard
            val byHeight = heightScale4x
            val limitedBy = if (byWidth < byHeight) "width" else "height"
            val ratio = ink.height / wifiInk.height
            sb.append(
                String.format(
                    Locale.US,
                    "| %s | %.2fx%.2f | (%.1f, %.1f)-(%.1f, %.1f) | %.2f | %.3f | %s |%n",
                    label,
                    artboard,
                    IosGeometry.Wifi.ARTBOARD_H,
                    ink.left,
                    ink.top,
                    ink.right,
                    ink.bottom,
                    ink.height,
                    ratio,
                    limitedBy
                )
            )
        }
        val fiveGPlus = IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, labelVisual("5G+", Color.BLACK))
        val fiveGPlusCap = d.CAP_HEIGHT * minOf(innerW / fiveGPlus, heightScale4x) / ss
        sb.append(
            String.format(
                Locale.US,
                "\nHeight-limited (4G, 5G, 3G, E): geometric cap %.2f px (1x) = %.3f x the " +
                    "%.2f-unit Wi-Fi ink. 5G+ is WIDTH-limited (artboard %.2f): geometric cap %.2f px, " +
                    "~0.63 of the Wi-Fi ink (measured ink ratio in the table is slightly higher because " +
                    "anti-aliasing adds ~1 px per edge). At the measured cap LTE is width-limited too " +
                    "(artboard ~176.3), at ~0.72 of the Wi-Fi ink. The shrink is the 80x50 slot " +
                    "constraint caused by honoring the measured 0.750-cap plus and the measured cap, " +
                    "not a geometry bug; the alternative is a smaller plus (~0.49-0.51 cap) or cap, " +
                    "closer to the constant cap but deviating from the reference. " +
                    "Geometric Wi-Fi ink %.2f units; measured Wi-Fi ink %.2f px.\n\n",
                d.CAP_HEIGHT * heightScale4x / ss,
                d.CAP_HEIGHT / wifiInkHeight,
                wifiInkHeight,
                fiveGPlus,
                fiveGPlusCap,
                wifiInkHeight,
                wifiInk.height
            )
        )

        sb.append("## Digit widths vs the reference (IosDigits is not retyped)\n\n")
        sb.append("| Digit | Reference / cap | IosDigits / cap | Ratio |\n|---|---:|---:|---:|\n")
        val referenceDigit = mapOf('3' to 25f / 36f, '4' to 27f / 36f, '5' to 24f / 36f)
        for (ch in charArrayOf('3', '4', '5')) {
            val ours = IosDigits.glyph(ch).width / IosDigits.CAP_HEIGHT
            val ref = referenceDigit.getValue(ch)
            sb.append(
                String.format(Locale.US, "| %c | %.4f | %.4f | %.3f |%n", ch, ref, ours, ours / ref)
            )
        }

        sb.append("\n\nFixtures: `r4-label-{lte,4g,5g,5g-plus,3g,e}-light-v2.png`, ")
        sb.append("`r4-label-{lte,4g,5g,5g-plus,3g,e}-dark-v2.png`, ")
        sb.append("`r4-label-family-light-v2.png`, `r4-label-family-dark-v2.png`.\n")
        sb.append(
            "\nMeasured on ref 1: cap 77.69, top 14.95 (top-anchored), baseline 92.64, ref-1 SF label " +
                "weight 11.10 (0.143 cap, provenance), cell->label / label->battery ink gaps 52.07 / " +
                "54.04. The battery remainder (0.4975) and cap (0.5021) alphas confirm 0.50. " +
                "UNVERIFIED: `Cell.INACTIVE_ALPHA` (ref 1 has all four bars and dots active) and the " +
                "battery cap W/H (low confidence, outside this change). R6 item 3 (supersedes the " +
                "R5B digit-weight limitation): the shipping stroke is 15.54 = 0.200 cap, " +
                "deliberately the battery digits' measured run-stroke, so the 3/4/5, the letters, " +
                "the plus and the G all draw at one weight; `IosLabelStrokeWeightTest` measures the " +
                "rendered glyphs. The plus uses the measured 0.750 height as an equal-arm square, " +
                "so 5G+ is width-limited at the 80x50 slot; at the measured cap LTE is width-limited " +
                "too, while 4G/5G/3G/E stay height-limited. This is a slot constraint, not a " +
                "geometry bug. Owner/device review required.\n"
        )
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

    private fun glyphTag(ch: Char): String = when (ch) {
        '+' -> "plus"
        else -> ch.toString().lowercase(Locale.US)
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
