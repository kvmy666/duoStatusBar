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
import kotlin.math.roundToInt

/**
 * R10A evidence generator: renders the ACTUAL runtime [IosGlyphRenderer] at SS=8 and downsamples to
 * the device 1x grid, writing the PNGs consumed by `.pm/out-r10/compose.py`.
 *
 * This is evidence, not a guard: nothing here is a new runtime path. It calls the same renderer the
 * Canvas fallback and the in-place drawable call, with the [IosVisual] the mapper produces, at the
 * device unit [UNIT_PX] = 1272 * [IosSlotLayout.UNIT_PER_SCREEN_WIDTH] / 100 = 0.4438008 px per
 * artboard unit. Every PNG is therefore labelled in the report as a runtime Canvas render, NOT a
 * device capture.
 *
 * Files are written only when `IOS_RENDER_OUT` names a directory:
 *  - `battery-<value>-ss8.png` / `battery-<value>.png` / `battery-<value>-bg.png` (77/61/100/30/48/55)
 *  - `label-<label>-ss8.png` / `label-<label>.png` / `label-<label>-light.png` / `-dark.png`
 *    (5G/4G/LTE/3G)
 *  - `digit-<d>-ink-ss8.png` / `digit-<d>-ink.png` (the traced ink band the runtime clears)
 *    and `digit-<d>-ko-light.png` / `digit-<d>-ko-dark.png` (the same digits as the runtime knockout)
 *  - `row-cell.png`, `row-label-5g.png`, `row-battery-100.png` (67x47, 53x43, 93x45)
 *  - `metrics.json` (IosSlotLayout numbers at 1272 plus the raw 2.0.7 dump boxes)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class R10EvidenceRenderTest {

    private companion object {
        /** Device grid of the owner's 1272 px screen: px per artboard design unit. */
        const val UNIT_PX = 1272f * 0.03489f / 100f // 0.4438008
        const val SS = 8

        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()

        val LIGHT_BG = Color.rgb(255, 255, 255)
        val DARK_BG = Color.rgb(6, 5, 2)
        val GREY_BG = Color.rgb(193, 192, 197)
        val NEAR_WHITE_BG = Color.rgb(253, 253, 254)
        val GREEN = 0xFF34C759.toInt()
        val YELLOW = 0xFFFFCC00.toInt()
        val INK_WHITE = Color.rgb(253, 252, 251)
        val INK_GREY_BLACK = Color.rgb(3, 3, 5)
    }

    private data class BatteryCase(
        val name: String,
        val level: Float,
        val state: IosBatteryState,
        val tint: Int,
        val fill: Int,
        val bg: Int,
        val bolt: Float
    )

    /** The six owner states, colours copied from `.pm/out-r9/work-r9/render_r9_compare.py` 255-286. */
    private val batteryCases = listOf(
        BatteryCase("77", 0.77f, IosBatteryState.NORMAL, BLACK, BLACK, LIGHT_BG, 0f),
        BatteryCase("61", 0.61f, IosBatteryState.NORMAL, INK_WHITE, INK_WHITE, DARK_BG, 0f),
        BatteryCase("100", 1.0f, IosBatteryState.NORMAL, INK_GREY_BLACK, INK_GREY_BLACK, GREY_BG, 0f),
        BatteryCase("30", 0.30f, IosBatteryState.NORMAL, BLACK, BLACK, LIGHT_BG, 0f),
        BatteryCase("48", 0.48f, IosBatteryState.SAVER, WHITE, YELLOW, Color.BLACK, 1f),
        BatteryCase("55", 0.55f, IosBatteryState.CHARGING, BLACK, GREEN, NEAR_WHITE_BG, 1f)
    )

    @Test
    fun `writes the R10A render evidence when IOS_RENDER_OUT is set`() {
        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() }?.let {
            File(it).apply { mkdirs() }
        }

        // Always exercise the renderer (a cheap guard), then write only when asked.
        val battery = batteryVisual(batteryCases.first { it.name == "100" })
        val batterySmall = renderBattery(battery, 93, 45, null).second
        assertTrue("battery render is empty", hasInk(batterySmall))
        batterySmall.recycle()

        if (dir != null) {
            writeBatteries(dir)
            writeLabels(dir)
            writeDigits(dir)
            writeRowIcons(dir)
            writeMetrics(dir)
        }
    }

    // --------------------------------------------------------------------------------- battery

    private fun writeBatteries(dir: File) {
        for (case in batteryCases) {
            val visual = batteryVisual(case)
            val (big, small) = renderBattery(visual, 93, 45, null)
            write(big, File(dir, "battery-${case.name}-ss8.png"))
            write(small, File(dir, "battery-${case.name}.png"))
            big.recycle()
            small.recycle()

            val (_, onBg) = renderBattery(visual, 93, 45, case.bg)
            write(onBg, File(dir, "battery-${case.name}-bg.png"))
            onBg.recycle()
        }
    }

    private fun batteryVisual(case: BatteryCase): IosVisual = IosVisual(
        tint = case.tint,
        batteryLevel = case.level,
        batteryState = case.state,
        batteryFillColor = case.fill,
        boltOpacity = case.bolt,
        percentInside = true
    )

    // ---------------------------------------------------------------------------------- labels

    private fun writeLabels(dir: File) {
        for (label in listOf("5G", "4G", "LTE", "3G")) {
            val artW = requireNotNull(IosGeometry.DataLabel.artboardWidth(label)) { "bad label $label" }
            val deviceW = (artW * UNIT_PX).roundToInt()
            val deviceH = (IosGeometry.Wifi.ARTBOARD_H * UNIT_PX).roundToInt()
            val black = labelVisual(label, BLACK)
            val (big, small) = renderGlyph(IosIconKind.WIFI, black, deviceW, deviceH, null)
            write(big, File(dir, "label-$label-ss8.png"))
            write(small, File(dir, "label-$label.png"))
            big.recycle()
            small.recycle()

            write(
                renderGlyph(IosIconKind.WIFI, black, deviceW, deviceH, LIGHT_BG).second,
                File(dir, "label-$label-light.png")
            )
            write(
                renderGlyph(IosIconKind.WIFI, labelVisual(label, WHITE), deviceW, deviceH, Color.BLACK).second,
                File(dir, "label-$label-dark.png")
            )
        }
    }

    private fun labelVisual(label: String, tint: Int): IosVisual = IosVisual(
        tint = tint,
        wifiSlot = IosWifiSlot.DataType(label)
    )

    // ---------------------------------------------------------------------------------- digits

    private fun writeDigits(dir: File) {
        for (d in 0..9) {
            val glyph = IosDigits.glyph('0' + d)
            val tileW = (glyph.advance * UNIT_PX).roundToInt().coerceAtLeast(1)
            val tileH = (IosDigits.CAP_HEIGHT * UNIT_PX).roundToInt().coerceAtLeast(1)

            // The traced ink band the runtime renders as a CLEAR knockout (buildPath, filled).
            val (bigInk, ink) = renderDigitInk(glyph, tileW, tileH)
            write(bigInk, File(dir, "digit-$d-ink-ss8.png"))
            write(ink, File(dir, "digit-$d-ink.png"))
            bigInk.recycle()
            ink.recycle()

            // The actual runtime path: battery percentInside at level d/100 (knockout).
            val visual = IosVisual(
                tint = BLACK,
                batteryLevel = d / 100f,
                batteryState = IosBatteryState.NORMAL,
                batteryFillColor = BLACK,
                percentInside = true
            )
            write(
                renderBattery(visual, 93, 45, LIGHT_BG).second,
                File(dir, "digit-$d-ko-light.png")
            )
            val whiteVisual = visual.copy(tint = WHITE, batteryFillColor = WHITE)
            write(
                renderBattery(whiteVisual, 93, 45, Color.BLACK).second,
                File(dir, "digit-$d-ko-dark.png")
            )
        }
    }

    /** Draws [glyph] at the device unit scale into a transparent tile, SS=8 then downsample to 1x. */
    private fun renderDigitInk(
        glyph: R10Glyphs.Glyph,
        tileW: Int,
        tileH: Int
    ): Pair<Bitmap, Bitmap> {
        val big = Bitmap.createBitmap(tileW * SS, tileH * SS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = BLACK
        }
        canvas.scale(UNIT_PX * SS, UNIT_PX * SS)
        canvas.drawPath(IosDigits.buildPath(glyph, 0f, 0f), paint)
        val small = Bitmap.createScaledBitmap(big, tileW, tileH, true)
        return big to small
    }

    // -------------------------------------------------------------------------------- row icons

    private fun writeRowIcons(dir: File) {
        val cell = IosVisual(
            tint = BLACK,
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellBars = 4f,
            cellDots = 4f
        )
        val (_, cellPng) = renderGlyph(
            IosIconKind.CELL, cell,
            (IosGeometry.Cell.ARTBOARD_W * UNIT_PX).roundToInt(),
            (IosGeometry.Cell.ARTBOARD_H * UNIT_PX).roundToInt(),
            null
        )
        write(cellPng, File(dir, "row-cell.png"))
        cellPng.recycle()

        val label = labelVisual("5G", BLACK)
        val labelW = requireNotNull(IosGeometry.DataLabel.artboardWidth("5G"))
        val (_, labelPng) = renderGlyph(
            IosIconKind.WIFI, label,
            (labelW * UNIT_PX).roundToInt(),
            (IosGeometry.Wifi.ARTBOARD_H * UNIT_PX).roundToInt(),
            null
        )
        write(labelPng, File(dir, "row-label-5g.png"))
        labelPng.recycle()

        val battery = batteryVisual(batteryCases.first { it.name == "100" })
        write(renderBattery(battery, 93, 45, null).second, File(dir, "row-battery-100.png"))
    }

    // ---------------------------------------------------------------------------------- metrics

    private fun writeMetrics(dir: File) {
        IosSlotLayout.screenWidthOverrideForTest = 1272
        try {
            val screenW = 1272
            val rowHeight = IosSlotLayout.rowHeight(screenW)
            val unitPx = IosSlotLayout.unit(screenW)
            val gapPx = IosSlotLayout.gap(screenW)
            val cell = IosVisual(
                tint = BLACK,
                cellMode = IosCellMode.DUAL,
                cellRole = IosCellRole.BOTH,
                cellBars = 4f,
                cellDots = 4f
            )
            val label = labelVisual("5G", BLACK)
            val battery = batteryVisual(batteryCases.first { it.name == "100" })

            val mobile = IosSlotLayout.mobileMetrics(cell, label, screenW, 100, 0, 98, 0, rowHeight)
            val batterySlot = IosSlotLayout.metrics(IosIconKind.BATTERY, battery, screenW, 100, 0, rowHeight)
            val labelBox = requireNotNull(mobile.label) { "mobile label box missing" }

            val json = buildString {
                appendLine("{")
                appendLine("  \"screenWidth\": $screenW,")
                appendLine("  \"unitPx\": ${num(unitPx)},")
                appendLine("  \"gapPx\": ${num(gapPx)},")
                appendLine("  \"rowHeight\": $rowHeight,")
                appendLine("  \"scales\": { \"cell\": 100, \"wifi\": 98, \"battery\": 100 },")
                appendLine("  \"mobile\": {")
                appendLine("    \"outerW\": ${mobile.outerWidth}, \"outerH\": ${mobile.outerHeight},")
                appendLine("    \"cellLeaf\": ${box(mobile.leaf)},")
                appendLine("    \"label\": ${box(labelBox)}")
                appendLine("  },")
                appendLine("  \"battery\": {")
                appendLine("    \"outerW\": ${batterySlot.outerWidth}, \"outerH\": ${batterySlot.outerHeight},")
                appendLine("    \"leaf\": ${box(batterySlot.leaf)}")
                appendLine("  },")
                appendLine("  \"dump207\": {")
                appendLine("    \"statusIconContainer\": { \"w\": 316, \"h\": 94 },")
                appendLine("    \"mobile\": { \"outerW\": 224, \"outerH\": 94,")
                appendLine("      \"cellLeaf\": { \"w\": 133, \"h\": 94, \"left\": 9, \"top\": 0 },")
                appendLine("      \"label\": { \"w\": 52, \"h\": 42, \"left\": 162, \"top\": 26 } },")
                appendLine("    \"battery\": { \"outerW\": 113, \"outerH\": 63,")
                appendLine("      \"leaf\": { \"w\": 93, \"h\": 45, \"left\": 10, \"top\": 18 } }")
                appendLine("  }")
                appendLine("}")
            }
            File(dir, "metrics.json").writeText(json)
            println("R10_METRICS gap=${num(gapPx)} row=$rowHeight unit=${num(unitPx)}")
            println("R10_METRICS mobile outer=${mobile.outerWidth}x${mobile.outerHeight} " +
                "cell=${box(mobile.leaf)} label=${box(labelBox)}")
            println("R10_METRICS battery outer=${batterySlot.outerWidth}x${batterySlot.outerHeight} " +
                "leaf=${box(batterySlot.leaf)}")
        } finally {
            IosSlotLayout.screenWidthOverrideForTest = null
        }
    }

    private fun num(value: Float): String = String.format(java.util.Locale.US, "%.4f", value)

    private fun box(box: IosSlotBox): String =
        "{ \"w\": ${box.width}, \"h\": ${box.height}, \"left\": ${box.left}, \"top\": ${box.top} }"

    // ---------------------------------------------------------------------------------- render

    private fun renderBattery(
        visual: IosVisual,
        width: Int,
        height: Int,
        background: Int?
    ): Pair<Bitmap, Bitmap> = renderGlyph(IosIconKind.BATTERY, visual, width, height, background)

    /**
     * Draws [kind] with the runtime [IosGlyphRenderer] into a `width*SS` x `height*SS` bitmap (the
     * same `min(w/W, h/H)` CONTAIN fit the runtime leaf uses, only 8x larger) and downsamples it to
     * the exact device 1x box. Returns (8x, 1x). A non-null [background] is painted first.
     */
    private fun renderGlyph(
        kind: IosIconKind,
        visual: IosVisual,
        width: Int,
        height: Int,
        background: Int?
    ): Pair<Bitmap, Bitmap> {
        val big = Bitmap.createBitmap(width * SS, height * SS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        if (background != null) canvas.drawColor(background)
        IosGlyphRenderer.draw(canvas, kind, visual, big.width, big.height)
        val small = Bitmap.createScaledBitmap(big, width, height, true)
        return big to small
    }

    private fun hasInk(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 8 }
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
