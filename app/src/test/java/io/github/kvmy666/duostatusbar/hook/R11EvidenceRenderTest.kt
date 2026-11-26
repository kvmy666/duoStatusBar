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
import kotlin.math.roundToInt

/**
 * R11 evidence generator: renders the ACTUAL runtime [IosGlyphRenderer] at the host-clock target
 * boxes (`baseInkPx = 33.48`, `gapPx = 16.74`, the 63 px row) for the PM's before/after images.
 *
 * Files are written only when `IOS_RENDER_OUT` names a directory:
 *  - `r11-battery-{70,100,150}.png`, `r11-cell-{70,100,150}.png`, `r11-wifi-{70,100,150}.png`
 *  - `r11-label-5g.png`
 *  - `r11-row-base.png` (cell + Wi-Fi + battery at the target ink gaps)
 *  - `r11-metrics.json` (the R11 numbers)
 *
 * This is evidence, not a runtime path: every PNG is a 1x downsample of an 8x render of the same
 * renderer the Canvas fallback and the in-place drawable call. The PNGs are not committed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class R11EvidenceRenderTest {

    private companion object {
        const val BASE_INK = 33.48f
        const val GAP = 16.74f
        const val ROW = 63
        const val SS = 8

        const val DARK_BG = 0xFF060502.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }

    private val cellBoth = IosVisual(
        tint = WHITE,
        cellMode = IosCellMode.DUAL,
        cellRole = IosCellRole.BOTH,
        cellBars = 4f,
        cellDots = 4f
    )
    private val wifiVisual = IosVisual(tint = WHITE, wifiLevel = 3f)
    private val label5g = IosVisual(tint = WHITE, wifiSlot = IosWifiSlot.DataType("5G"))
    private val batteryVisual = IosVisual(
        tint = WHITE,
        batteryLevel = 1f,
        batteryState = IosBatteryState.NORMAL,
        batteryFillColor = WHITE
    )

    private fun metrics(kind: IosIconKind, visual: IosVisual, scale: Int): IosSlotMetrics =
        IosSlotLayout.metricsAtInk(kind, visual, BASE_INK, GAP, scale, 0, ROW)

    @Test
    fun `writes the R11 evidence when IOS_RENDER_OUT is set`() {
        // Always exercise the renderer (a cheap guard), then write only when asked.
        val battery100 = metrics(IosIconKind.BATTERY, batteryVisual, 100)
        val probe = renderGlyph(IosIconKind.BATTERY, batteryVisual, battery100.leaf.width, battery100.leaf.height)
        assertTrue("battery render is empty", hasInk(probe))
        probe.recycle()

        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() }?.let {
            File(it).apply { mkdirs() }
        } ?: return

        for (scale in listOf(70, 100, 150)) {
            val battery = metrics(IosIconKind.BATTERY, batteryVisual, scale)
            writeGlyph(dir, "r11-battery-$scale.png", IosIconKind.BATTERY, batteryVisual, battery.leaf)
            val cell = metrics(IosIconKind.CELL, cellBoth, scale)
            writeGlyph(dir, "r11-cell-$scale.png", IosIconKind.CELL, cellBoth, cell.leaf)
            val wifi = metrics(IosIconKind.WIFI, wifiVisual, scale)
            writeGlyph(dir, "r11-wifi-$scale.png", IosIconKind.WIFI, wifiVisual, wifi.leaf)
        }

        val label = IosSlotLayout.mobileMetricsAtInk(
            cellBoth, label5g, BASE_INK, GAP, 100, 0, 100, 0, ROW
        ).label!!
        writeGlyph(dir, "r11-label-5g.png", IosIconKind.WIFI, label5g, label)

        writeRow(dir)
        writeMetrics(dir)
    }

    private fun writeGlyph(
        dir: File,
        name: String,
        kind: IosIconKind,
        visual: IosVisual,
        box: IosSlotBox
    ) {
        val glyph = renderGlyph(kind, visual, box.width, box.height)
        write(glyph, File(dir, name))
        glyph.recycle()
    }

    /** Cell + Wi-Fi + battery at the equal target ink gap, composed at the 63 px row centre. */
    private fun writeRow(dir: File) {
        val slots = listOf(
            IosIconKind.CELL to (cellBoth to metrics(IosIconKind.CELL, cellBoth, 100)),
            IosIconKind.WIFI to (wifiVisual to metrics(IosIconKind.WIFI, wifiVisual, 100)),
            IosIconKind.BATTERY to (batteryVisual to metrics(IosIconKind.BATTERY, batteryVisual, 100))
        )
        val width = slots.sumOf { it.second.second.outerWidth }
        val height = ROW
        val big = Bitmap.createBitmap(width * SS, height * SS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        canvas.drawColor(DARK_BG)
        var leftPx = 0
        for ((kind, pair) in slots) {
            val (visual, slot) = pair
            // Draw directly at 8x; composing leaf Bitmaps via drawBitmap can pick up density scaling.
            canvas.save()
            canvas.translate(((leftPx + slot.leaf.left) * SS).toFloat(), (slot.leaf.top * SS).toFloat())
            IosGlyphRenderer.draw(
                canvas,
                kind,
                visual,
                slot.leaf.width * SS,
                slot.leaf.height * SS
            )
            canvas.restore()
            leftPx += slot.outerWidth
        }
        val small = Bitmap.createScaledBitmap(big, width, height, true)
        write(small, File(dir, "r11-row-base.png"))
        big.recycle()
        small.recycle()
    }

    private fun writeMetrics(dir: File) {
        val row = StringBuilder()
        row.appendLine("{")
        row.appendLine("  \"clockCapPx\": 31,")
        row.appendLine("  \"baseInkPx\": ${num(BASE_INK)},")
        row.appendLine("  \"gapPx\": ${num(GAP)},")
        row.appendLine("  \"gapRatio\": 0.50,")
        row.appendLine("  \"hostRatio\": 1.08,")
        row.appendLine("  \"rowHeight\": $ROW,")
        row.appendLine("  \"iphone\": { \"clockCap\": 38, \"iconInk\": 41, \"ratio\": 1.079,")
        row.appendLine("    \"gapRatioMeasured\": \"0.44-0.46\", \"gapRatioRequested\": 0.50 },")
        row.appendLine("  \"scales\": {")
        val names = listOf(70, 100, 150)
        for ((i, scale) in names.withIndex()) {
            val battery = metrics(IosIconKind.BATTERY, batteryVisual, scale)
            val cell = metrics(IosIconKind.CELL, cellBoth, scale)
            val wifi = metrics(IosIconKind.WIFI, wifiVisual, scale)
            row.appendLine("    \"$scale\": {")
            row.appendLine("      \"battery\": ${slotJson(battery)},")
            row.appendLine("      \"cellDualBoth\": ${slotJson(cell)},")
            row.appendLine("      \"wifi\": ${slotJson(wifi)}")
            row.append("    }")
            row.appendLine(if (i == names.lastIndex) "" else ",")
        }
        row.appendLine("  },")
        val mobile = IosSlotLayout.mobileMetricsAtInk(
            cellBoth, label5g, BASE_INK, GAP, 100, 0, 100, 0, ROW
        )
        row.appendLine("  \"label5g\": ${boxJson(mobile.label!!)},")
        row.appendLine("  \"mobile100\": { \"outerW\": ${mobile.outerWidth},")
        row.appendLine("    \"cell\": ${boxJson(mobile.leaf)}, \"label\": ${boxJson(mobile.label!!)} }")
        row.appendLine("}")
        File(dir, "r11-metrics.json").writeText(row.toString())
        println("R11_METRICS base=$BASE_INK gap=$GAP row=$ROW mobileOuter=${mobile.outerWidth} " +
            "label=${mobile.label.width}x${mobile.label.height}")
    }

    private fun slotJson(slot: IosSlotMetrics): String =
        "{ \"outerW\": ${slot.outerWidth}, \"outerH\": ${slot.outerHeight}, " +
            "\"leaf\": ${boxJson(slot.leaf)} }"

    private fun boxJson(box: IosSlotBox): String =
        "{ \"w\": ${box.width}, \"h\": ${box.height}, \"left\": ${box.left}, \"top\": ${box.top} }"

    private fun num(value: Float): String = String.format(Locale.US, "%.4f", value)

    /**
     * Draws [kind] with the runtime [IosGlyphRenderer] into a `width*SS` x `height*SS` bitmap (the same
     * CONTAIN fit the runtime leaf uses, only 8x larger) and downsamples it to the 1x box.
     */
    private fun renderGlyph(
        kind: IosIconKind,
        visual: IosVisual,
        width: Int,
        height: Int
    ): Bitmap {
        val big = Bitmap.createBitmap(
            (width * SS).coerceAtLeast(1),
            (height * SS).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(big)
        canvas.drawColor(DARK_BG)
        IosGlyphRenderer.draw(canvas, kind, visual, big.width, big.height)
        val small = Bitmap.createScaledBitmap(big, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
        big.recycle()
        return small
    }

    private fun hasInk(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 8 && it != DARK_BG }
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
