package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * R6 item 3: every data-label glyph must draw at the battery digits' weight.
 *
 * The measured v6 percentage digits have a run-stroke of 11.40 at cap 57
 * (`.pm/ref/out-r3/stroke-r3d.csv`: 3 = 11.38, 4 = 11.38, 5 = 11.44), i.e. 0.200 cap. This test
 * rasterises the SHIPPING geometry (the same [IosGeometry.DataLabel.outlines] and
 * [IosDigits.buildPath] the renderer uses, at [DIGIT_SCALE] for the digits) at 8x the label cap
 * (622 px), takes the 50 % ink mask and measures a documented stroke per glyph:
 *
 *  - L / T / E: horizontal run containing the vertical stem, rows between the arms;
 *  - G: horizontal run touching the right ink edge below the bar (the measured G stem metric);
 *  - +: vertical run through the horizontal arm, columns away from the centre;
 *  - 3 / 4 / 5: median ink run across every row and column, i.e. the R3D `run_stroke` definition
 *    (`.pm/ref/out-r3/digit-v4/01_measure_refs.py`, runs shorter than 0.45 cap).
 *
 * The second, independent number in the table is the pooled run median across both axes, reported
 * as a cross-check; it is the primary metric for the digits only.
 *
 * Comparison is weight-normalised by cap (stroke / cap), because the label cap is 77.69 and the
 * percentage cap is 57. Each label glyph must be within +/-5 % of the digit mean, and the digit
 * mean must be within 2 % of `IosDigits.RUN_STROKE * DataLabel.DIGIT_SCALE`.
 *
 * The test is a real guard with no environment gate. When `IOS_R6_OUT` is set it writes
 * `stroke-table.md` there, and the sibling evidence test writes `label-weight-ours.png` (the
 * production renderer at 4x the 36 px reference cap) for the composed `label-weight.png`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosLabelStrokeWeightTest {

    private val d = IosGeometry.DataLabel

    /** 8x the label cap: 77.69 x 8 = 621.52, rounded to 622 px. */
    private val capPx = (d.CAP_HEIGHT * 8f).roundToInt()

    private val scale = capPx / d.CAP_HEIGHT

    /** 8 design units of transparent/white gutter around every glyph. */
    private val padUnits = 8f

    /** Every glyph that must draw at the one measured weight. */
    private val glyphs = listOf('5', 'G', 'L', 'T', 'E', '4', '3', '+')

    private val digitGlyphs = listOf('3', '4', '5')

    // -------------------------------------------------------------------------------------- guard

    @Test
    fun `label glyphs draw at the battery digit weight`() {
        val measured = glyphs.associateWith { measure(it) }
        for (ch in glyphs) {
            assertTrue("$ch drew no ink", measured.getValue(ch).px > 0.0)
        }

        val digitMeanCap = digitGlyphs
            .map { measured.getValue(it).px / scale / d.CAP_HEIGHT }
            .average()
        val expectedDigitCap = (IosDigits.RUN_STROKE * d.DIGIT_SCALE / d.CAP_HEIGHT).toDouble()

        // The pixels agree with the measured constant (the spec says 2 %).
        assertEquals(
            "digit mean stroke/cap must equal RUN_STROKE x DIGIT_SCALE / CAP within 2 %",
            expectedDigitCap,
            digitMeanCap,
            expectedDigitCap * 0.02
        )

        for (ch in glyphs) {
            val cap = measured.getValue(ch).px / scale / d.CAP_HEIGHT
            val delta = cap / digitMeanCap - 1.0
            assertTrue(
                String.format(
                    Locale.US,
                    "%c stroke/cap %.4f is %.1f%% from the digit mean %.4f (must be within +/-5%%)",
                    ch, cap, delta * 100, digitMeanCap
                ),
                abs(delta) <= 0.05
            )
        }

        // Keep the table next to the guard so a failure is readable without re-running the maths.
        val out = System.getenv("IOS_R6_OUT")?.takeIf { it.isNotBlank() }
        if (out != null) {
            val dir = File(out)
            dir.mkdirs()
            File(dir, "stroke-table.md").writeText(strokeTable(measured, digitMeanCap, expectedDigitCap))
        }
    }

    /**
     * Evidence only: draws the production labels through [IosGlyphRenderer] and the measured digits
     * at the label cap on the reference-like dark background, 4x the 36 px reference cap (144 px).
     * Writes `label-weight-ours.png` when `IOS_R6_OUT` is set; the committed composite is composed
     * by `compose_label_weight.py` in the same directory.
     */
    @Test
    fun `writes the label weight evidence when IOS_R6_OUT is set`() {
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
        // The measured digits at the label cap, as the family-row evidence does: "345" is not a
        // supported label, so it is drawn from the same shipping IosDigits path at DIGIT_SCALE.
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
        File(out, "label-weight-ours.png").outputStream().use {
            strip.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        strip.recycle()
    }

    // ---------------------------------------------------------------------------------- measuring

    private data class Measure(
        val glyph: Char,
        val metric: String,
        val px: Double,
        val crossPx: Double
    )

    private fun measure(ch: Char): Measure {
        val bitmap = renderGlyph(ch)
        val mask = mask(bitmap)
        bitmap.recycle()
        val pooled = runStroke(mask)
        val axisCross = axisRunCrossCheck(mask)
        return when (ch) {
            'L' -> Measure(ch, "stem run, rows 0.15..0.75 cap",
                stemRun(mask, 0.15f, 0.75f, d.STROKE / 2f), pooled)
            'T' -> Measure(ch, "stem run, rows 0.30..0.90 cap",
                stemRun(mask, 0.30f, 0.90f, d.inkWidth('T') / 2f), pooled)
            'E' -> Measure(ch, "stem run, rows 0.25..0.38 + 0.62..0.75 cap",
                medianOf(
                    stemRuns(mask, 0.25f, 0.38f, d.STROKE / 2f) +
                        stemRuns(mask, 0.62f, 0.75f, d.STROKE / 2f)
                ), pooled)
            'G' -> Measure(ch, "right stem, rightmost run rows 0.66..0.85 cap",
                gStemRun(mask), pooled)
            '+' -> Measure(ch, "horizontal arm vertical run, side columns",
                plusArmRun(mask), pooled)
            else -> Measure(ch, "median ink run rows+cols (R3D run_stroke)", pooled, axisCross)
        }
    }

    /** One glyph from the shipping geometry, black on opaque white, at [capPx] cap. */
    private fun renderGlyph(ch: Char): Bitmap {
        val inkW = if (d.isDigit(ch)) IosDigits.glyph(ch).width * d.DIGIT_SCALE else d.inkWidth(ch)
        val w = ceil((inkW + 2f * padUnits) * scale).toInt()
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
            canvas.translate(padUnits, padUnits)
            if (d.isDigit(ch)) {
                canvas.scale(d.DIGIT_SCALE, d.DIGIT_SCALE)
                canvas.drawPath(IosDigits.buildPath(IosDigits.glyph(ch), 0f, 0f), paint)
            } else {
                val path = Path().apply {
                    fillType = Path.FillType.WINDING
                    for (contour in d.outlines(ch)) {
                        if (contour.size < 6) continue
                        moveTo(contour[0], contour[1])
                        var i = 2
                        while (i < contour.size) {
                            lineTo(contour[i], contour[i + 1])
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

    /** Ink runs (start, length) along one row or column. */
    private fun runs(mask: Mask, horizontal: Boolean, index: Int): List<Pair<Int, Int>> {
        val limit = if (horizontal) mask.width else mask.height
        val out = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (i in 0 until limit) {
            val on = if (horizontal) mask.at(i, index) else mask.at(index, i)
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                out += start to (i - start)
                start = -1
            }
        }
        if (start >= 0) out += start to (limit - start)
        return out
    }

    /** R3D `run_stroke`: median run across every row and column, runs shorter than 0.45 cap. */
    private fun runStroke(mask: Mask): Double {
        val lengths = ArrayList<Int>()
        val capLimit = 0.45 * capPx
        for (y in 0 until mask.height) for (r in runs(mask, true, y)) {
            if (r.second >= 1 && r.second < capLimit) lengths += r.second
        }
        for (x in 0 until mask.width) for (r in runs(mask, false, x)) {
            if (r.second >= 1 && r.second < capLimit) lengths += r.second
        }
        return medianOf(lengths)
    }

    /** Cross-check: mean of the row-run median and the column-run median. */
    private fun axisRunCrossCheck(mask: Mask): Double {
        val capLimit = 0.45 * capPx
        val rows = ArrayList<Int>()
        val cols = ArrayList<Int>()
        for (y in 0 until mask.height) for (r in runs(mask, true, y)) {
            if (r.second >= 1 && r.second < capLimit) rows += r.second
        }
        for (x in 0 until mask.width) for (r in runs(mask, false, x)) {
            if (r.second >= 1 && r.second < capLimit) cols += r.second
        }
        return (medianOf(rows) + medianOf(cols)) / 2.0
    }

    private fun stemRuns(mask: Mask, fromCap: Float, toCap: Float, xUnits: Float): List<Int> {
        val x = ((padUnits + xUnits) * scale).roundToInt()
        val out = ArrayList<Int>()
        val from = ((padUnits + fromCap * d.CAP_HEIGHT) * scale).roundToInt()
        val to = ((padUnits + toCap * d.CAP_HEIGHT) * scale).roundToInt()
        for (y in from..to) {
            for (r in runs(mask, true, y)) {
                if (x in r.first until r.first + r.second) out += r.second
            }
        }
        return out
    }

    private fun stemRun(mask: Mask, fromCap: Float, toCap: Float, xUnits: Float): Double =
        medianOf(stemRuns(mask, fromCap, toCap, xUnits))

    /** Right stem: the rightmost ink run, rows below the bar and above the slanted bottom. */
    private fun gStemRun(mask: Mask): Double {
        val from = ((padUnits + 0.66f * d.CAP_HEIGHT) * scale).roundToInt()
        val to = ((padUnits + 0.85f * d.CAP_HEIGHT) * scale).roundToInt()
        val out = ArrayList<Int>()
        for (y in from..to) {
            val row = runs(mask, true, y)
            if (row.isNotEmpty()) out += row.last().second
        }
        return medianOf(out)
    }

    /** Horizontal arm thickness: vertical runs at side columns, away from the crossing. */
    private fun plusArmRun(mask: Mask): Double {
        val plusW = d.inkWidth('+')
        val y = ((padUnits + d.CAP_HEIGHT / 2f) * scale).roundToInt()
        val out = ArrayList<Int>()
        for (fraction in listOf(0.12f, 0.16f, 0.20f, 0.80f, 0.84f, 0.88f)) {
            val x = ((padUnits + fraction * plusW) * scale).roundToInt()
            for (r in runs(mask, false, x)) {
                if (y in r.first until r.first + r.second) out += r.second
            }
        }
        return medianOf(out)
    }

    private fun medianOf(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid].toDouble()
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    // ----------------------------------------------------------------------------------- evidence

    private fun digitStrip(text: String, capPx: Int, background: Int, ink: Int, renderScale: Float): Bitmap {
        val unitScale = capPx / d.CAP_HEIGHT
        var advance = 0f
        for (ch in text) advance += IosDigits.glyph(ch).advance * d.DIGIT_SCALE
        val firstBearing = IosDigits.bearing(IosDigits.glyph(text.first())) * d.DIGIT_SCALE
        val lastBearing = IosDigits.bearing(IosDigits.glyph(text.last())) * d.DIGIT_SCALE
        val inkW = advance - firstBearing - lastBearing
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
        var pen = IosGeometry.Wifi.ARTBOARD_MARGIN - firstBearing
        val saved = canvas.save()
        try {
            canvas.scale(unitScale, unitScale)
            for (ch in text) {
                val glyphLeft = pen + IosDigits.bearing(IosDigits.glyph(ch)) * d.DIGIT_SCALE
                val inner = canvas.save()
                try {
                    canvas.translate(glyphLeft, top)
                    canvas.scale(d.DIGIT_SCALE, d.DIGIT_SCALE)
                    canvas.drawPath(IosDigits.buildPath(IosDigits.glyph(ch), 0f, 0f), paint)
                } finally {
                    canvas.restoreToCount(inner)
                }
                pen += IosDigits.glyph(ch).advance * d.DIGIT_SCALE
            }
        } finally {
            canvas.restoreToCount(saved)
        }
        return bitmap
    }

    private fun strokeTable(
        measured: Map<Char, Measure>,
        digitMeanCap: Double,
        expectedDigitCap: Double
    ): String {
        val sb = StringBuilder()
        sb.append("# R6 item 3 — label stroke weight\n\n")
        sb.append(
            "Rasterised from the shipping geometry at cap $capPx px (8x 77.69), 50 % ink mask. " +
                "Digits use the R3D `run_stroke` (median run across rows+columns, runs < 0.45 cap); " +
                "L/T/E use the vertical stem run, G the right stem below the bar, + the horizontal " +
                "arm thickness. `IosLabelStrokeWeightTest` enforces +/-5 % against the digit mean and " +
                "2 % for the digit mean vs `RUN_STROKE x DIGIT_SCALE`.\n\n"
        )
        sb.append(
            String.format(
                Locale.US,
                "Digit mean: **%.4f cap** (expected %.4f). Label weight target: **%.2f u = %.4f cap**.\n\n",
                digitMeanCap, expectedDigitCap, d.STROKE, d.STROKE / d.CAP_HEIGHT
            )
        )
        sb.append("| Glyph | Metric | Measured px | Design units | stroke / cap | delta vs digit mean | Cross-check (pooled run, px) |\n")
        sb.append("|---|---|---:|---:|---:|---:|---:|\n")
        for (ch in glyphs) {
            val m = measured.getValue(ch)
            val units = m.px / scale
            val cap = units / d.CAP_HEIGHT
            sb.append(
                String.format(
                    Locale.US,
                    "| %c | %s | %.1f | %.2f | %.4f | %+.1f%% | %.1f |\n",
                    ch, m.metric, m.px, units, cap, (cap / digitMeanCap - 1.0) * 100.0, m.crossPx
                )
            )
        }
        sb.append("\n")
        sb.append(
            "G's stroke metric is its **right stem** (rows 0.66..0.85 cap), the same `G stem` " +
                "measurement as the R5B reference. The pooled-run cross-check includes G's measured " +
                "bowl/bar, which are not uniform multiples of the stem, and is not the G stroke " +
                "metric.\n\n"
        )
        sb.append(
            "The SF reference label is lighter (R5B measured 11.10 u of 77.69 = 0.143 cap); " +
                "R6 item 3 deliberately adopted the battery digits' 0.200 cap so every label glyph " +
                "is one weight. Evidence: `label-weight-ours.png`, `label-weight.png`.\n"
        )
        return sb.toString()
    }
}
