package io.github.kvmy666.duostatusbar.hook

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The one source of truth for every iOS glyph dimension, in design units where the battery body
 * height is exactly [UNIT_HEIGHT] (100).
 *
 * The numbers come from the real-iPhone references (ref 6 single-SIM, ref 5 dual-SIM, ref 1 target)
 * and the owner device (ref 7), measured with a 50%-threshold connected-component script at battery
 * height 88.93 px (light) and 71.28 px (dark, a 0.798x image). The dark image is only trusted for the
 * dual-SIM layout, the inactive alpha and the battery split; the light image supplies the proportions.
 * The old step-3 "annotated" target (`ios-reference-arrows`, cap 15.2 x 36.8 and bars 48..99) is the
 * outlier: the recovered ref 6 confirms the light capture instead. Every superseded or unconfirmed
 * value is recorded as UNVERIFIED rather than averaged. The full R2 evidence is in
 * `.pm/ref/out-r2/measurements.md` and the frozen table is `docs/ios-geometry.md`.
 *
 * Both runtime renderers consume this object through [IosGlyphRenderer] ([IosCanvasView] and
 * [IosGlyphDrawable] share it), and `rive/ios/scene.rml` repeats the same numbers. `IosGeometryParityTest`
 * reads the RML and pins the artboard sizes, the cap, the dots and the measured Wi-Fi geometry against
 * these constants.
 *
 * Wi-Fi is its own measured glyph, fitted against the light single-SIM reference capture; it no longer
 * reuses Duo's geometry. The 8x fit predicts outer/inner/dot IoU 0.976/0.982/0.988, while the
 * composed evidence at the real slot size measures 0.972-0.984 (the prediction is not the evidence).
 * The measurement report is kept outside the repository and the frozen table is
 * `docs/ios-geometry.md`.
 */
internal object IosGeometry {

    const val UNIT_HEIGHT = 100f

    /** Battery: solid rounded body, no outline, half-capsule nub, clipped level fill. Artboard `Ios`. */
    object Battery {
        // R9-design.md §Final geometry: body 189.3 x 100, circular radius 28.6.
        const val BODY_W = 189.3f
        const val BODY_H = 100f
        const val BODY_RADIUS = 28.6f

        // R9-design.md: separate nub 14.3 x 32.1, gap 3.6, half-capsule (flat left, round right).
        const val CAP_W = 14.3f
        const val CAP_H = 32.1f
        const val CAP_GAP = 3.6f
        const val CAP_LIFT = 0f

        /**
         * Remainder/nub alpha by the ink luminance rule (R9-design.md §Colour rules): dark ink
         * (black) uses 0.30, light ink (white) uses 0.50. Kept as named constants so tests and the
         * Rive builder pin the same numbers.
         */
        const val REMAINDER_ALPHA_DARK_INK = 0.30f
        const val REMAINDER_ALPHA_LIGHT_INK = 0.50f

        /**
         * R9-design.md: artboard 209.2 x 102, body at (1,1). `CAP_*` keeps the historical name for
         * the separate nub: cap left is the body right + the 3.6-unit gap.
         */
        const val ARTBOARD_W = 209.2f
        const val ARTBOARD_H = 102f

        const val BODY_LEFT = 1f
        const val BODY_TOP = 1f
        const val CAP_LEFT = BODY_LEFT + BODY_W + CAP_GAP
        const val CAP_TOP = BODY_TOP + (BODY_H - CAP_H) / 2f - CAP_LIFT

        /**
         * The separate nub as a 25-gon: flat left edge at [CAP_LEFT] and a right half-ellipse.
         * Absolute artboard coordinates, closed, ordered from the top end to the bottom end; the
         * closing edge is the flat base at x=[CAP_LEFT]. Bbox exactly
         * CAP_LEFT..CAP_LEFT+CAP_W / CAP_TOP..CAP_TOP+CAP_H.
         */
        val CAP_POINTS: FloatArray = FloatArray(50).also { out ->
            val cy = CAP_TOP + CAP_H / 2f
            for (i in 0..24) {
                val angle = (-90.0 + 180.0 * i / 24.0) * (Math.PI / 180.0)
                out[i * 2] = CAP_LEFT + CAP_W * cos(angle).toFloat()
                out[i * 2 + 1] = cy + (CAP_H / 2f) * sin(angle).toFloat()
            }
        }

        /**
         * The R9 large bolt box (67 x 92, centred, solid system ink, no outline). `BOLT_POINTS` is
         * generated from [IosDigits.BOLT_NORMALIZED] for the Rive parity test; the runtime renderer
         * asks [IosDigits.largeBoltPath] instead. `BOLT_OUTLINE`/`BOLT_FILL` are kept as deprecated
         * compile-time shims for that test and are not used by the renderer (R9 removes the white
         * fill + black outline).
         */
        val BOLT_POINTS: FloatArray = FloatArray(42).also { out ->
            for (i in 0 until 21) {
                out[i * 2] = BODY_LEFT + BODY_W / 2f + (IosDigits.BOLT_NORMALIZED[i * 2] - 0.5f) *
                    IosDigits.LARGE_BOLT_W
                out[i * 2 + 1] = BODY_TOP + BODY_H / 2f +
                    (IosDigits.BOLT_NORMALIZED[i * 2 + 1] - 0.5f) * IosDigits.LARGE_BOLT_H
            }
        }

        @Deprecated("R9: the bolt has no outline.")
        const val BOLT_OUTLINE = 0f

        @Deprecated("R9: the bolt is solid system ink, not a static white fill.")
        const val BOLT_FILL = 0xFFFFFFFF.toInt()

        /** Fill width for a 0..1 level, clipped by the body path. */
        fun fillWidth(level: Float): Float = BODY_W * level.coerceIn(0f, 1f)
    }

    /** Cellular: four capsules in single mode; compressed bars + four dots in dual. Artboard `IosCell`. */
    object Cell {
        // R9-design.md §Final geometry.
        const val BAR_W = 25f
        const val DUAL_BAR_W = 27f
        const val PITCH = 39.3f
        const val DUAL_PITCH = 40.3f
        const val DOT_W = 27f
        const val DOT_H = 29f

        /** Inactive bars/dots are the foreground at 25% (measured 0.20-0.29). */
        const val INACTIVE_ALPHA = 0.25f

        /** R9-design.md: single 0.47w capsule radius; dual uses the same 0.47 x 27. */
        const val CORNER_RADIUS = 11.75f
        const val DUAL_CORNER_RADIUS = 12.69f

        val SINGLE_BAR_HEIGHTS = floatArrayOf(46.4f, 60.7f, 75.0f, 92.9f)
        val DUAL_BAR_HEIGHTS = floatArrayOf(37.9f, 46.1f, 52.9f, 64.3f)

        /** Single block 3 * 39.3 + 25 = 142.9; dual block 3 * 40.3 + 27 = 147.9. */
        const val BLOCK_W = 3f * PITCH + BAR_W
        const val DUAL_BLOCK_W = 3f * DUAL_PITCH + DUAL_BAR_W
        const val DUAL_ROW_H = 64.3f
        const val DUAL_GAP = 10f
        const val DUAL_BLOCK_H = DUAL_ROW_H + DUAL_GAP + DOT_H

        /**
         * Keep the measured 150.1 x 105.6 envelope (R9-design.md: "Keep the existing artboard
         * envelope where possible"); the ink bboxes inside it hit the R9 targets and callers position
         * by [inkBox].
         */
        const val ARTBOARD_W = 150.1f
        const val ARTBOARD_H = 105.6f

        const val LEFT = 1f
        const val TOP = 1f

        /** Bar 4's top is exactly [TOP]; bottom is 4.5 units above the artboard bottom. */
        const val SINGLE_BAR_BOTTOM = TOP + 92.9f

        const val DOT_TOP = TOP + DUAL_ROW_H + DUAL_GAP

        /**
         * Per-view dual-SIM row artboards. The full artboard remains the design source for the
         * stacked `BOTH` block; a per-slot row fills its own slot with the compressed bar row or the
         * dot row plus the same 1-unit margin.
         */
        const val ROW_ARTBOARD_W = DUAL_BLOCK_W + 2f
        const val DATA_ROW_ARTBOARD_H = DUAL_ROW_H + 2f
        const val OTHER_ROW_ARTBOARD_H = DOT_H + 2f

        /** Artboard-units translation that moves the absolute dot coordinates to [TOP]. */
        fun otherRowOffsetY(): Float = -(DOT_TOP - TOP)

        fun barLeft(index: Int): Float = LEFT + index * PITCH

        fun dualBarLeft(index: Int): Float = LEFT + index * DUAL_PITCH

        fun singleBarHeight(index: Int): Float = SINGLE_BAR_HEIGHTS[index]

        fun dualBarHeight(index: Int): Float = DUAL_BAR_HEIGHTS[index]

        fun dotCenterX(index: Int): Float = LEFT + index * DUAL_PITCH + DOT_W / 2f

        fun dotCenterY(): Float = DOT_TOP + DOT_H / 2f
    }

    /**
     * Wi-Fi: the iOS glyph measured from the light single-SIM reference capture, **restored verbatim
     * to the 2.0.6 (R10A) geometry**. R9's thicker arcs, lowered centre and elliptical dot are
     * removed; the renderer (`drawWifi`) never changed, so restoring these constants restores the
     * 2.0.6 glyph exactly.
     *
     * The reference glyph bbox at light px (243.24, 30.56) is 127.53 x 94.77; the artboard adds a
     * 1-unit margin on every side, so every coordinate below is in artboard space (+1 to the
     * viewBox x/y). The two arcs are concentric strokes with round caps; [CENTRE_X]/[CENTRE_Y] are
     * the artboard centre. The 8x fit predicts outer 0.976, inner 0.982, dot 0.988 IoU against the
     * light reference; the composed evidence at the real slot size measures 0.972-0.984. The dark
     * reference uses a different stroke weight, so it is not a design target (see the B4
     * measurement notes, kept outside the repository, §2.2).
     *
     * The trims are Rive fractions: a fraction f is f*360 degrees clockwise from 12 o'clock, and the
     * offset rotates the window. [IosGlyphRenderer] applies the same rule.
     */
    object Wifi {
        const val ARTBOARD_MARGIN = 1f
        const val ARTBOARD_W = 129.53f
        const val ARTBOARD_H = 96.77f

        /** Common arc centre in artboard space (2.0.6: viewBox 63.809, 92.522 + 1 margin). */
        const val CENTRE_X = 64.809f
        const val CENTRE_Y = 93.522f

        /** Outer arc: mid-radius, constant stroke width, round caps (2.0.6). */
        const val OUTER_RADIUS = 83.843f
        const val OUTER_STROKE = 16.903f
        const val OUTER_TRIM_END = 0.232575f
        const val OUTER_TRIM_OFFSET = 0.884175f

        /** Inner arc: same centre, its own radius/stroke/span (2.0.6). */
        const val INNER_RADIUS = 50.053f
        const val INNER_STROKE = 17.794f
        const val INNER_TRIM_END = 0.220078f
        const val INNER_TRIM_OFFSET = 0.889917f

        /**
         * The dot's measured bounding-box size: [DOT_W] x [DOT_H] are the measured bbox of the
         * restored 2.0.6 [DOT_POINTS]. `IosGeometryParityTest` compares both the bbox and the
         * point set against the Rive scene, so the two stay in lockstep.
         */
        const val DOT_W = 34.64f
        const val DOT_H = 26.68f

        /**
         * The measured dot: a left-right symmetric 32-gon, absolute artboard coordinates (viewBox
         * vertices + 1 margin); closed. The dot stays at full tint, independent of [wifiLevel].
         */
        val DOT_POINTS: FloatArray = floatArrayOf(
            80.91f, 82.43f,
            79.08f, 85.27f,
            76.87f, 87.42f,
            74.81f, 89.10f,
            73.28f, 90.89f,
            71.49f, 92.41f,
            69.60f, 93.96f,
            67.35f, 95.15f,
            64.82f, 95.77f,
            62.29f, 95.15f,
            60.04f, 93.96f,
            58.15f, 92.41f,
            56.36f, 90.89f,
            54.83f, 89.10f,
            52.77f, 87.42f,
            50.56f, 85.27f,
            48.73f, 82.43f,
            47.50f, 78.98f,
            48.56f, 75.69f,
            50.79f, 73.05f,
            53.94f, 71.54f,
            56.53f, 70.03f,
            59.44f, 69.45f,
            62.19f, 69.20f,
            64.82f, 69.09f,
            67.45f, 69.20f,
            70.20f, 69.45f,
            73.11f, 70.03f,
            75.70f, 71.54f,
            78.85f, 73.05f,
            81.08f, 75.69f,
            82.14f, 78.98f
        )

        /** Opacity per Wi-Fi level (0..3), unchanged from the Duo binding rule. */
        val OUTER_ALPHA = floatArrayOf(0.3f, 0.3f, 0.3f, 1f)
        val INNER_ALPHA = floatArrayOf(0.3f, 0.3f, 1f, 1f)
    }

    /**
     * The data-type label that occupies the Wi-Fi slot for a measured cellular network
     * ("LTE", "4G", "5G", ...).
     *
     * **Provenance:** the glyph shapes and widths are the R10 regular-sheet trace
     * (`rive/ios/glyphs-r10.json`, `R10Glyphs.LABELS`). The plus shape is taken from the r4 SF strip
     * and is flagged in the data. `L`, `T`, `5`, `4` and `3` advances are measured; the `G`, `E` and
     * `+` advances are extrapolated from the median measured side bearing and flagged. The label
     * digits are independent of the percentage [IosDigits] table (the label `5` has its own traced
     * contour). [CAP_HEIGHT] = 71.4 and [TOP] = 17.4 keep the R5B/ref-1 measured placement; the label
     * is TOP-ANCHORED (baseline 88.8 in the Wi-Fi artboard).
     *
     * All label geometry is delegated to [IosLabelGlyphs]: each glyph is a set of closed cubic
     * contours in design units, cap top `y = 0`, ink-left `x = 0`, drawn as one
     * [android.graphics.Path.FillType.EVEN_ODD] path at its pen. The group ink is the ink-left model
     * (`sum(advances of all but last) + last ink width`), so the first glyph's ink starts at
     * [Wifi.ARTBOARD_MARGIN] and each pen advances by the measured [advance]. The artboard width is
     * dynamic ([widthOf] + 2 * [Wifi.ARTBOARD_MARGIN]) and its height stays [Wifi.ARTBOARD_H].
     */
    object DataLabel {
        /** Supported labels, in reader/reporting order. */
        val LABELS = listOf("LTE", "4G", "5G", "5G+", "3G", "E", "G")

        /** R10 regular sheet: label cap 71.4 units (10 pt). */
        const val CAP_HEIGHT = 71.4f

        /** Measured/top-anchored cap top in the Wi-Fi artboard (baseline = [TOP] + [CAP_HEIGHT]). */
        const val TOP = 17.4f

        /** True only for the exact supported labels; a caller must not draw anything else. */
        fun supports(label: String): Boolean =
            label in LABELS && label.all { IosLabelGlyphs.supports(it) }

        /** Ink width of one label character; `0` for anything unsupported (delegated). */
        fun inkWidth(ch: Char): Float = IosLabelGlyphs.inkWidth(ch)

        /** Measured ink-left advance (pitch) of one label character (delegated). */
        fun advance(ch: Char): Float = IosLabelGlyphs.advance(ch)

        /** Ink-left bearing: zero, because the traced glyph starts at its ink-left edge (delegated). */
        fun bearing(ch: Char): Float = IosLabelGlyphs.bearing(ch)

        /**
         * Ink width of [label] in the ink-left model:
         * `sum(advances of all but last) + last ink width`; `0` if empty/unsupported (delegated).
         */
        fun groupInkWidth(label: String): Float = IosLabelGlyphs.groupInkWidth(label)

        /** Ink width of a supported [label], or `null` when it is not in [LABELS]. */
        fun widthOf(label: String): Float? = if (supports(label)) groupInkWidth(label) else null

        /** Dynamic artboard width (`ink + 2 * Wifi.ARTBOARD_MARGIN`), or `null` if unsupported. */
        fun artboardWidth(label: String): Float? =
            widthOf(label)?.plus(2f * Wifi.ARTBOARD_MARGIN)
    }

    /**
     * Reference strip composition: cell, Wi-Fi, battery at the measured gaps.
     *
     * The two consumers are separate. The Wi-Fi gaps ([CELL_WIFI_GAP], [WIFI_BATTERY_GAP]) drive the
     * in-app `IosPreview` composition, which uses only those two constants. The label gaps
     * ([CELL_LABEL_GAP], [LABEL_BATTERY_GAP]) drive the R5B 5G evidence strip
     * ([labelBatteryX], [LABEL_BATTERY_X_5G], [LABEL_WIDTH_5G], used by `IosR5LabelStripTest`). The
     * runtime does **not** use [Strip] at all: it gives each glyph its stock icon slot.
     *
     * All gaps are INK-to-INK: every artboard carries a 1-unit margin and [Battery.BODY_LEFT] is 1,
     * so [WIFI_X] / [LABEL_X] / [BATTERY_X] are artboard origins while the constants are the gaps
     * between the drawn edges. [CELL_WIFI_GAP] and [WIFI_BATTERY_GAP] stay the measured light Wi-Fi
     * values; ref 1's data-type label has its own measured ink gaps [CELL_LABEL_GAP] and
     * [LABEL_BATTERY_GAP] (22.06 / 22.90 px x 2.360 units/px = 52.07 / 54.04 units).
     */
    object Strip {
        /** Measured light gaps: 51.14 px (57.5 units) and 53.15 px (59.77 units). */
        const val CELL_WIFI_GAP = 57.5f
        const val WIFI_BATTERY_GAP = 59.77f

        /**
         * Measured ref-1 label gaps, ink to ink: cell ink right -> label ink left = 22.06 px =
         * 52.07 units, and label ink right -> battery body left = 22.90 px = 54.04 units.
         */
        const val CELL_LABEL_GAP = 52.07f
        const val LABEL_BATTERY_GAP = 54.04f

        const val CELL_X = 0f
        const val WIFI_X = Cell.BLOCK_W + CELL_WIFI_GAP
        const val BATTERY_X = WIFI_X + 127.53f + WIFI_BATTERY_GAP

        /**
         * Label artboard origin. The label strip's cell is the dual glyph (147.9 wide), and the
         * label ink starts at `LABEL_X + Wifi.ARTBOARD_MARGIN`, so the gap to the cell ink is
         * exactly [CELL_LABEL_GAP].
         */
        const val LABEL_X = Cell.DUAL_BLOCK_W + CELL_LABEL_GAP

        /**
         * Battery artboard origin for a label strip. The dynamic label artboard is
         * `artboardWidth(label)` and its ink ends one [Wifi.ARTBOARD_MARGIN] before the artboard's
         * right edge; the battery body starts at `labelBatteryX(label) + Battery.BODY_LEFT`, so the
         * ink gap is exactly [LABEL_BATTERY_GAP].
         *
         * @throws IllegalArgumentException when [label] is not a supported [DataLabel] label; an
         * unsupported name must not silently collapse the strip.
         */
        fun labelBatteryX(label: String): Float {
            val artboard = requireNotNull(DataLabel.artboardWidth(label)) {
                "$label is not a supported data label"
            }
            return LABEL_X + artboard - Wifi.ARTBOARD_MARGIN + LABEL_BATTERY_GAP - Battery.BODY_LEFT
        }

        /** The 5G evidence strip's battery artboard origin (ref 1 reads 5G). */
        val LABEL_BATTERY_X_5G: Float = labelBatteryX("5G")

        const val WIDTH = BATTERY_X + Battery.BODY_W + Battery.CAP_GAP + Battery.CAP_W

        /** Full 5G label-strip width (cell artboard origin through the battery cap). */
        val LABEL_WIDTH_5G: Float =
            LABEL_BATTERY_X_5G + Battery.BODY_W + Battery.CAP_GAP + Battery.CAP_W

        const val HEIGHT = 103f

        /** Top alignment: battery, Wi-Fi and single cell share the top; the dual cell is 4.2 units above. */
        const val DUAL_CELL_TOP_OFFSET = -4.2f
    }

    /** The ink extents of a glyph in artboard units (R9 layout builder input). */
    data class InkBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** True when [colour] is dark ink (the R9 remainder/nub luminance rule). */
    fun isDarkInk(colour: Int): Boolean {
        val r = (colour shr 16) and 0xFF
        val g = (colour shr 8) and 0xFF
        val b = colour and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f < 0.5f
    }

    /** R9 remainder/nub alpha: dark ink 0.30, light ink 0.50. */
    fun remainderAlpha(colour: Int): Float =
        if (isDarkInk(colour)) Battery.REMAINDER_ALPHA_DARK_INK else Battery.REMAINDER_ALPHA_LIGHT_INK

    /**
     * The tight drawn-ink box of [kind] in artboard units. The R9 layout builder positions each
     * glyph so this ink starts at G/2 inside its slot; the renderer's CONTAIN fit then uses the same
     * artboard, so the helper and the pixels cannot disagree about where the ink is.
     *
     * [IosWifiSlot.None] returns the full artboard (there is no ink for that occupant).
     */
    fun inkBox(kind: IosIconKind, visual: IosVisual): InkBox = when (kind) {
        IosIconKind.BATTERY -> InkBox(
            Battery.BODY_LEFT,
            Battery.BODY_TOP,
            Battery.CAP_LEFT + Battery.CAP_W,
            Battery.BODY_TOP + Battery.BODY_H
        )
        IosIconKind.CELL -> cellInkBox(visual)
        IosIconKind.WIFI -> wifiInkBox(visual)
    }

    private fun cellInkBox(visual: IosVisual): InkBox {
        val c = Cell
        if (visual.cellMode == IosCellMode.SINGLE) {
            return InkBox(c.LEFT, c.TOP, c.LEFT + c.BLOCK_W, c.SINGLE_BAR_BOTTOM)
        }
        val left = c.LEFT
        val right = c.LEFT + c.DUAL_BLOCK_W
        return when (visual.cellRole) {
            IosCellRole.DATA -> InkBox(left, c.TOP, right, c.TOP + c.DUAL_ROW_H)
            IosCellRole.OTHER -> InkBox(
                c.LEFT,
                c.TOP,
                c.LEFT + 3f * c.DUAL_PITCH + c.DOT_W,
                c.TOP + c.DOT_H
            )
            IosCellRole.BOTH -> InkBox(left, c.TOP, right, c.TOP + c.DUAL_BLOCK_H)
        }
    }

    private fun wifiInkBox(visual: IosVisual): InkBox {
        val slot = visual.wifiSlot
        if (slot is IosWifiSlot.DataType) {
            val artboard = DataLabel.artboardWidth(slot.label) ?: return wifiGlyphInkBox()
            return InkBox(
                Wifi.ARTBOARD_MARGIN,
                DataLabel.TOP,
                artboard - Wifi.ARTBOARD_MARGIN,
                DataLabel.TOP + DataLabel.CAP_HEIGHT
            )
        }
        if (slot == IosWifiSlot.None) {
            return InkBox(0f, 0f, Wifi.ARTBOARD_W, Wifi.ARTBOARD_H)
        }
        return wifiGlyphInkBox()
    }

    /**
     * The Wi-Fi glyph ink box. The outer arc is the widest element; its stroke extends
     * [Wifi.OUTER_STROKE]/2 beyond the mid-radius. The arc endpoints and the 90/270 degree extrema
     * are included, then the dot's actual vertex bbox ([Wifi.DOT_POINTS]) is unioned in.
     */
    private fun wifiGlyphInkBox(): InkBox {
        val w = Wifi
        val radius = w.OUTER_RADIUS + w.OUTER_STROKE / 2f
        val startDeg = -90f + w.OUTER_TRIM_OFFSET * 360f
        val sweepDeg = w.OUTER_TRIM_END * 360f
        val points = w.DOT_POINTS
        var left = points[0]
        var top = points[1]
        var right = points[0]
        var bottom = points[1]
        var i = 2
        while (i < points.size) {
            left = minOf(left, points[i])
            right = maxOf(right, points[i])
            top = minOf(top, points[i + 1])
            bottom = maxOf(bottom, points[i + 1])
            i += 2
        }
        for (i in 0..180) {
            val degrees = startDeg + sweepDeg * i / 180f
            val radians = degrees * (Math.PI / 180.0)
            val x = w.CENTRE_X + radius * cos(radians).toFloat()
            val y = w.CENTRE_Y + radius * sin(radians).toFloat()
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
        // The round caps extend half a stroke past each arc endpoint in every direction.
        val cap = w.OUTER_STROKE / 2f
        val endpointStart = startDeg * (Math.PI / 180.0)
        val endpointEnd = (startDeg + sweepDeg) * (Math.PI / 180.0)
        for (radians in listOf(endpointStart, endpointEnd)) {
            val x = w.CENTRE_X + w.OUTER_RADIUS * cos(radians).toFloat()
            val y = w.CENTRE_Y + w.OUTER_RADIUS * sin(radians).toFloat()
            left = minOf(left, x - cap)
            top = minOf(top, y - cap)
            right = maxOf(right, x + cap)
            bottom = maxOf(bottom, y + cap)
        }
        // The renderer is clipped by the artboard bounds.
        return InkBox(
            left.coerceIn(0f, w.ARTBOARD_W),
            top.coerceIn(0f, w.ARTBOARD_H),
            right.coerceIn(0f, w.ARTBOARD_W),
            bottom.coerceIn(0f, w.ARTBOARD_H)
        )
    }

    /**
     * Intrinsic glyph sizes relative to the battery body height (1.0) and the per-kind draw scale that
     * brings our on-device glyphs to the iOS target ratio.
     *
     * The ratios are measured from the recovered ref 6 (single-SIM), ref 5 (dual-SIM) and ref 1
     * (owner target): battery body = 1.0, clock digit cap = [CLOCK_CAP] (1.00 +/-3 %). The R3 draw
     * scales were the ref 7 on-device calibration, `iOS target ratio / current on-device ratio`:
     * battery 1.23 -> 1.00 = 0.81, Wi-Fi 1.67 -> 0.944 = 0.57, cell single 1.267 -> 0.955 = 0.754
     * (0.75; the older 1.32 -> 0.726 held before bar 4 became 95.5 units), cell dual 1.38 -> 1.042 =
     * 0.76. [CELL_DUAL_H] = 1.042 is the measured ref target; the shipped design block is
     * [Cell.DUAL_BLOCK_H] = 103.3 (1.033 of the battery body) because the dual bar heights are
     * unchanged, and the 0.76 scale is calibrated against the ref-7 on-device 1.377 ratio, not this
     * envelope. The cell scales are medium confidence (the logging phone showed the known
     * side-by-side dual bug).
     *
     * **R4 defaults are the R3 calibration x x1.10** (battery 0.891, Wi-Fi 0.627, cell single 0.825,
     * cell dual 0.836). The relative ratios ([WIFI_H] etc.) are unchanged. A user scale of 100 %
     * remains relative to 100 and therefore equals the new default, and the identity user transform
     * still draws the new default. These constants are the per-kind slot ratios: the host turns the
     * inset on via `relativeSize`, and the drawable/view applies it inside the stock slot (never a
     * layout change).
     *
     * **UNVERIFIED: the x1.10 growth is not measured on a device; owner review required.** The only
     * measured baseline is the R3 ref-7 calibration; the R4 ratios in `docs/ios-geometry.md` are
     * targets, and the production integer boxes can round a pixel away from them.
     */
    object Relative {
        /** Clock digit cap / battery body height (ref 6: 30/31 px, +/-3 %). */
        const val CLOCK_CAP = 1.00f
        const val BATTERY_H = 1.00f
        const val WIFI_H = 0.944f
        const val WIFI_W = 1.256f
        const val CELL_SINGLE_H = 0.955f
        const val CELL_DUAL_H = 1.042f
        const val CAP_H = 0.260f
        const val CAP_W = 0.095f
        const val CAP_GAP = 0.075f

        /** R4 defaults: the R3 ref-7 calibration x1.10 (see the object KDoc). */
        const val BATTERY_DRAW_SCALE = 0.891f
        const val WIFI_DRAW_SCALE = 0.627f
        const val CELL_DRAW_SCALE = 0.825f
        const val CELL_DUAL_DRAW_SCALE = 0.836f

        /**
         * R9-design.md §8: the V2 layout owns the real size, so the drawable transform stays identity.
         * The existing HOST/preview [drawScale] defaults are deliberately unchanged.
         */
        const val V2_DRAW_SCALE = 1f

        /** The draw scale for [kind]; a dual-SIM cell uses its own measured scale. */
        fun drawScale(kind: IosIconKind, dualCell: Boolean = false): Float = when (kind) {
            IosIconKind.BATTERY -> BATTERY_DRAW_SCALE
            IosIconKind.WIFI -> WIFI_DRAW_SCALE
            IosIconKind.CELL -> if (dualCell) CELL_DUAL_DRAW_SCALE else CELL_DRAW_SCALE
        }

        /**
         * The inner glyph box for a stock slot.
         *
         * The host insets the drawable to this box (it never resizes the stock view, C2); the renderer
         * then fits the glyph inside with its usual `min(w/W, h/H)` CONTAIN. A non-positive slot
         * returns `0 x 0`; the result never exceeds the slot.
         */
        fun sizeFor(
            kind: IosIconKind,
            slotWidth: Int,
            slotHeight: Int,
            dualCell: Boolean = false
        ): Pair<Int, Int> {
            if (slotWidth <= 0 || slotHeight <= 0) return 0 to 0
            val scale = drawScale(kind, dualCell)
            val w = (slotWidth * scale).roundToInt().coerceIn(0, slotWidth)
            val h = (slotHeight * scale).roundToInt().coerceIn(0, slotHeight)
            return w to h
        }
    }
}
