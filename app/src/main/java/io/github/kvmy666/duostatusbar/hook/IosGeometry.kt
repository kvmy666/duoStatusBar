package io.github.kvmy666.duostatusbar.hook

import kotlin.math.roundToInt

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

    /** Battery: solid rounded body, no outline, cap nub, clipped level fill. Artboard `Ios`. */
    object Battery {
        const val BODY_W = 192.5f
        const val BODY_H = 100f
        const val BODY_RADIUS = 33f

        /**
         * Measured cap (refs 1/5/6): median 9.5 x 26.0 with a 7.5 gap, same colour as the uncharged
         * remainder ([CAP_ALPHA]). The earlier annotated 15.2 x 36.8 target is the outlier (55 %
         * wider / 26 % taller than the light capture) and is superseded by the recovered evidence.
         */
        const val CAP_W = 9.5f
        const val CAP_H = 26.0f
        const val CAP_GAP = 7.5f
        /** The measured cap centre sits on the body centre. */
        const val CAP_LIFT = 0f

        /**
         * The cap is the inactive grey, i.e. the same colour as the uncharged remainder. The owner
         * rule is 0.50; confirmed by the partial-charge refs 1/5 and the dual dark capture. The light
         * 100 % reference draws the cap at full ink (UNVERIFIED exception, see `docs/ios-geometry.md`).
         */
        const val CAP_ALPHA = 0.50f

        /**
         * The cap is the right-hand half of a superellipse with this exponent, flat on the body side.
         * The measured refs show the same rounded-triangular/trapezoid shape, just smaller than the
         * old annotated one.
         */
        const val CAP_SHAPE_N = 1.8f

        /**
         * Frozen envelope: keep the pre-R2 217.2 x 102 so every runtime/preview/evidence placement
         * that pins the battery artboard does not move. The smaller cap simply leaves more room on
         * the right; do not derive the width from [CAP_W] again.
         */
        const val ARTBOARD_W = 217.2f
        const val ARTBOARD_H = 102f

        const val BODY_LEFT = 1f
        const val BODY_TOP = 1f
        const val CAP_LEFT = BODY_LEFT + BODY_W + CAP_GAP
        const val CAP_TOP = BODY_TOP + (BODY_H - CAP_H) / 2f - CAP_LIFT

        /**
         * The measured cap: the right-hand half of the [CAP_SHAPE_N] superellipse, discretised as a
         * 25-gon, flat base on the body side. Absolute artboard coordinates, closed, ordered from the
         * top end to the bottom end; the closing edge is the flat base at x=[CAP_LEFT]. Generated at
         * [CAP_W] x [CAP_H] (bbox exactly CAP_LEFT..CAP_LEFT+CAP_W / CAP_TOP..CAP_TOP+CAP_H). Both
         * renderers draw this same polygon.
         */
        val CAP_POINTS: FloatArray = floatArrayOf(
            201.00f, 38.00f,
            201.99f, 38.12f,
            203.12f, 38.49f,
            204.27f, 39.09f,
            205.40f, 39.92f,
            206.47f, 40.95f,
            207.46f, 42.15f,
            208.35f, 43.51f,
            209.10f, 44.98f,
            209.70f, 46.53f,
            210.14f, 48.10f,
            210.41f, 49.65f,
            210.50f, 51.00f,
            210.41f, 52.35f,
            210.14f, 53.90f,
            209.70f, 55.47f,
            209.10f, 57.02f,
            208.35f, 58.49f,
            207.46f, 59.85f,
            206.47f, 61.05f,
            205.40f, 62.08f,
            204.27f, 62.91f,
            203.12f, 63.51f,
            201.99f, 63.88f,
            201.00f, 64.00f
        )

        /** The uncharged part of the body is the same colour at half alpha. */
        const val REMAINDER_ALPHA = 0.50f

        /**
         * A classic lightning bolt, centred in the body. There is no charging reference image, so this
         * is a design invention; the owner's kit does not publish a status-bar bolt. Points are absolute
         * artboard coordinates, in order, closed.
         */
        val BOLT_POINTS: FloatArray = floatArrayOf(
            100.25f, 23.50f,
            85.25f, 53.75f,
            94.85f, 53.75f,
            91.85f, 78.50f,
            109.25f, 45.50f,
            99.65f, 45.50f,
            104.75f, 23.50f
        )

        /** Fill width for a 0..1 level, clipped by the body path. */
        fun fillWidth(level: Float): Float = BODY_W * level.coerceIn(0f, 1f)
    }

    /** Cellular: four capsules in single mode; compressed bars + four dots in dual. Artboard `IosCell`. */
    object Cell {
        const val BAR_W = 25.1f
        const val DUAL_BAR_W = 25.3f
        const val PITCH = 41f
        /** Measured dark dots: 18.0 px wide x 19.5 px tall at 0.7128 px/unit -> a slight oval. */
        const val DOT_W = 26f
        const val DOT_H = 27.4f

        /** Inactive bars/dots are the foreground at 25% (measured 0.243). */
        const val INACTIVE_ALPHA = 0.25f

        /**
         * Measured single bars (ref 6 light/native): 36.0 / 52.8 / 73.0 / 95.5, bottom-aligned at
         * [SINGLE_BAR_BOTTOM] = 96.5, so bar 4's top is exactly [TOP] (1.0, level with the battery
         * body top). Supersedes the annotated 48 / 63.4 / 81.2 / 99 mockup, which is the outlier.
         */
        val SINGLE_BAR_HEIGHTS = floatArrayOf(36.0f, 52.8f, 73.0f, 95.5f)

        /** Measured dark (compressed dual row): 0.303 / 0.375 / 0.499 / 0.627 (unchanged). */
        val DUAL_BAR_HEIGHTS = floatArrayOf(30.3f, 37.5f, 49.9f, 62.7f)

        const val BLOCK_W = 3f * PITCH + BAR_W
        const val DUAL_ROW_H = 62.7f
        /** Measured row gap 12.68 units (old 13.5). */
        const val DUAL_GAP = 12.7f
        const val DUAL_BLOCK_H = DUAL_ROW_H + DUAL_GAP + DOT_H

        /**
         * Frozen envelope: the measured dual block is [DUAL_BLOCK_H] = 102.8, but the artboard stays
         * at the pre-R2 150.1 x 105.6 so the scene/preview/compare placements do not move. Do not
         * derive the height from [DUAL_BLOCK_H] again.
         */
        const val ARTBOARD_W = BLOCK_W + 2f
        const val ARTBOARD_H = 105.6f

        const val LEFT = 1f
        const val TOP = 1f

        /**
         * The single-bar baseline in the frozen 105.6 artboard. Bar 4's top is exactly [TOP]; the
         * bottom is 4.5 units above the body bottom, as measured on ref 6.
         */
        const val SINGLE_BAR_BOTTOM = TOP + 95.5f

        const val DOT_TOP = TOP + DUAL_ROW_H + DUAL_GAP

        /**
         * Per-view dual-SIM row artboards (the measured replacement map, device dumps, §5; `docs/ios-geometry.md`).
         *
         * The Rive scene has no row artboard: [ARTBOARD_W] x [ARTBOARD_H] stays the design source and
         * still draws the stacked `BOTH` block. The runtime, however, gives each stock mobile slot its
         * own view, so a row must fill its slot without shrinking the shared glyph. [ROW_ARTBOARD_W] is
         * the block width plus the same 1-unit margin as [ARTBOARD_W]; [DATA_ROW_ARTBOARD_H] is the
         * compressed bar row plus that margin; [OTHER_ROW_ARTBOARD_H] is the dot row plus the margin.
         */
        const val ROW_ARTBOARD_W = BLOCK_W + 2f
        const val DATA_ROW_ARTBOARD_H = DUAL_ROW_H + 2f
        const val OTHER_ROW_ARTBOARD_H = DOT_H + 2f

        /**
         * Artboard-units translation that moves the absolute dot coordinates to [TOP] in the
         * other-row artboard, so the dot row sits at the top of its own slot.
         */
        fun otherRowOffsetY(): Float = -(DOT_TOP - TOP)

        fun barLeft(index: Int): Float = LEFT + index * PITCH

        fun singleBarHeight(index: Int): Float = SINGLE_BAR_HEIGHTS[index]

        fun dualBarHeight(index: Int): Float = DUAL_BAR_HEIGHTS[index]

        fun dotCenterX(index: Int): Float = LEFT + index * PITCH + BAR_W / 2f

        fun dotCenterY(): Float = DOT_TOP + DOT_H / 2f
    }

    /**
     * Wi-Fi: the iOS glyph measured from the light single-SIM reference capture (no longer Duo's).
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

        /** Common arc centre in artboard space (viewBox 63.809, 92.522 + 1 margin). */
        const val CENTRE_X = 64.809f
        const val CENTRE_Y = 93.522f

        /** Outer arc: mid-radius, constant stroke width, round caps. */
        const val OUTER_RADIUS = 83.843f
        const val OUTER_STROKE = 16.903f
        const val OUTER_TRIM_END = 0.232575f
        const val OUTER_TRIM_OFFSET = 0.884175f

        /** Inner arc: same centre, its own radius/stroke/span. */
        const val INNER_RADIUS = 50.053f
        const val INNER_STROKE = 17.794f
        const val INNER_TRIM_END = 0.220078f
        const val INNER_TRIM_OFFSET = 0.889917f

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
     * Reference strip composition: cell, Wi-Fi, battery at the measured gaps. Used by the preview and
     * the PNG evidence compositor; the runtime still gives each glyph its stock slot.
     */
    object Strip {
        /** Measured light gaps: 51.14 px (57.5 units) and 53.15 px (59.77 units). */
        const val CELL_WIFI_GAP = 57.5f
        const val WIFI_BATTERY_GAP = 59.77f

        const val CELL_X = 0f
        const val WIFI_X = Cell.BLOCK_W + CELL_WIFI_GAP
        const val BATTERY_X = WIFI_X + 127.53f + WIFI_BATTERY_GAP
        const val WIDTH = BATTERY_X + Battery.BODY_W + Battery.CAP_GAP + Battery.CAP_W

        const val HEIGHT = 103f

        /** Top alignment: battery, Wi-Fi and single cell share the top; the dual cell is 4.2 units above. */
        const val DUAL_CELL_TOP_OFFSET = -4.2f
    }

    /**
     * Intrinsic glyph sizes relative to the battery body height (1.0) and the per-kind draw scale that
     * brings our on-device glyphs to the iOS target ratio.
     *
     * The ratios are measured from the recovered ref 6 (single-SIM), ref 5 (dual-SIM) and ref 1
     * (owner target): battery body = 1.0, clock digit cap = [CLOCK_CAP] (1.00 +/-3 %). The draw scales
     * are the ref 7 on-device calibration, `iOS target ratio / current on-device ratio`:
     * battery 1.23 -> 1.00 = 0.81, Wi-Fi 1.67 -> 0.944 = 0.57, cell single 1.267 -> 0.955 = 0.754
     * (0.75; the older 1.32 -> 0.726 held before bar 4 became 95.5 units), cell dual 1.38 -> 1.042 =
     * 0.76. [CELL_DUAL_H] = 1.042 is the measured ref target; the shipped design block is
     * [Cell.DUAL_BLOCK_H] = 102.8 (1.028 of the battery body) because the dual bar heights are
     * unchanged, and the 0.76 scale is calibrated against the ref-7 on-device 1.377 ratio, not this
     * envelope. The cell scales are medium confidence (the logging phone showed the known
     * side-by-side dual bug). These constants are data only: the host applies them, the renderer and
     * the drawable do not.
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

        /** Ref 7 calibration: target ratio / current on-device ratio (see the object KDoc). */
        const val BATTERY_DRAW_SCALE = 0.81f
        const val WIFI_DRAW_SCALE = 0.57f
        const val CELL_DRAW_SCALE = 0.75f
        const val CELL_DUAL_DRAW_SCALE = 0.76f

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

/**
 * Per-user placement offset and scale for an in-place iOS glyph.
 *
 * [dxPx] is a device-pixel horizontal shift applied after scaling, so it is independent of [scale].
 * [scale] is applied around the drawable's bounds centre. The drawable clips the result to its own
 * bounds and never writes its layout or intrinsic size (C2).
 *
 * R2C declares the same type for its settings model; the PM merge dedupes the two declarations, so
 * keep this exact signature.
 */
internal data class IosUserTransform(val dxPx: Float = 0f, val scale: Float = 1f)
