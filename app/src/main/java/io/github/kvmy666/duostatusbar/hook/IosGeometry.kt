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
         * The runtime applies full ink at 100% per the PM rule while the design scene stays 0.50.
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
         * The R3B lightning bolt: the measured 21-point polygon from refs 1/2/4, normalised to a
         * 61 x 96 box at the body centre. Absolute artboard coordinates, in order, closed. The shape
         * is `66.75 + nx * 61`, `3.0 + ny * 96` from `.pm/ref/out-r3/digits-final.json`; ref 3 draws
         * it white, so the renderer uses a white fill with a black outline ([BOLT_OUTLINE]).
         */
        val BOLT_POINTS: FloatArray = floatArrayOf(
            110.26f, 3.00f,
            110.26f, 12.36f,
            101.32f, 31.88f,
            99.69f, 40.02f,
            100.50f, 43.27f,
            103.76f, 45.71f,
            126.53f, 44.89f,
            127.34f, 47.34f,
            126.53f, 49.78f,
            107.01f, 70.93f,
            94.00f, 88.83f,
            84.24f, 98.60f,
            84.24f, 90.46f,
            93.18f, 70.12f,
            94.81f, 61.98f,
            91.56f, 57.91f,
            68.78f, 57.91f,
            67.16f, 55.47f,
            77.73f, 40.83f,
            83.42f, 35.95f,
            94.81f, 20.49f
        )

        /**
         * Ref 3's ring measures ~4.5 units, but the frozen 102-unit artboard has only 3 units
         * vertically around the 96-unit white bolt (white y 3..98.6). A [BOLT_OUTLINE] of 3.0 puts
         * the stroked extent at 0..101.6 and prevents the view-bound clip found in review; the
         * renderer still draws it as a 2x stroke around the white fill. [BOLT_POINTS]/[BOLT_FILL]
         * are unchanged.
         */
        const val BOLT_OUTLINE = 3.0f

        /** Ref 3 shows a plain white bolt, not a dynamic tint. */
        const val BOLT_FILL = 0xFFFFFFFF.toInt()

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
     * The data-type label that occupies the Wi-Fi slot for a measured cellular network
     * ("LTE", "4G", "5G", ...).
     *
     * **No iOS status-bar capture shows this label.** The reference
     * `.pm/ref/r4/5-sf-labels-LTE-E-4G-5G-5Gplus.jpg` (an SF-style "LTE E 4G 5G 5G+ 345" strip) is the
     * authority for the glyph SHAPES, widths and spacing only; its weight is lighter than the shipped
     * bold digits. The size is the PM fallback: [CAP_HEIGHT] is 0.70 of the tallest measured cellular
     * bar ([Cell.SINGLE_BAR_HEIGHTS] max 95.5), so the label cap is ~70 % of the Wi-Fi ink height, and
     * [STROKE] is the in-battery bold digit run-stroke (11.40 at [IosDigits.CAP_HEIGHT] 57) scaled to
     * that cap. The cap/bar ratio, the vertical placement and the letterforms are **UNVERIFIED /
     * owner review required** until approved on a device.
     *
     * Every letter and the plus are FILLED OUTLINES: one or more closed contours, consistently
     * clockwise, filled with [android.graphics.Path.FillType.WINDING] so overlapping shapes union
     * without alpha seams. There is no font and no stroked centre line. Digits reuse the [IosDigits]
     * widths, advances and filled even-odd contours (scaled by [DIGIT_SCALE]); the letter widths are
     * the measured reference ink widths as fractions of the reference cap. The group is laid out
     * exactly like [IosDigits]: `sum(advances) - firstBearing - lastBearing`. The artboard width is
     * dynamic ([widthOf] + 2 * [Wifi.ARTBOARD_MARGIN]) and its height stays [Wifi.ARTBOARD_H]; the ink
     * group starts at [Wifi.ARTBOARD_MARGIN] horizontally and the cap box is vertically centred on
     * the Wi-Fi glyph ([TOP]).
     *
     * The measured reference cap is 36 px and the ink widths (px / 36) are L 0.611, T 0.806, E 0.667,
     * G 0.944 and plus 0.778 x 0.750. The shipped plus is an equal-arm 0.75-cap square (the measured
     * height; 1 px inside the measured width). Honouring that reference plus makes "5G+" wider than
     * the 80x50 production slot's height-limited width, so "5G+" is width-limited (~19.4 px cap) while
     * the five other labels stay height-limited (~21.6 px cap, 0.705 of the Wi-Fi ink). The G is
     * the v3 measured letterform: a right-shifted bowl (0.612 W centre, 0.612 W x 0.5 C outer radii),
     * a 318.93..20.66 degree band with the open aperture crossing 3 o'clock, a mid-height bar whose
     * inner end is at 0.607 W, and a right stem from the bar to a slanted bottom (0.975/0.920 C).
     * The counter opens through the upper-right aperture, so there is no closed hole and the glyph
     * reads as G, not a C with a glued corner. All G strokes scale with a single weight from the
     * measured reference (weight 1) to the digit stroke ([G_PRODUCTION_WEIGHT]).
     */
    object DataLabel {
        /** Supported labels, in reader/reporting order. */
        val LABELS = listOf("LTE", "4G", "5G", "5G+", "3G", "E")

        /** Reference cap (px): the flat-letter height (L/T/E/4) in the SF strip. */
        private const val REF_CAP_PX = 36f

        /**
         * Cap height = 0.70 of the tallest measured single-SIM bar (95.5). Fallback: no iOS
         * status-bar capture shows the label, so this is the bar-geometry rule, not a measurement.
         */
        val CAP_HEIGHT: Float = 0.70f * Cell.SINGLE_BAR_HEIGHTS.max()

        /** The measured percentage digit run-stroke (11.40 at cap 57), same family/weight. */
        val STROKE: Float = CAP_HEIGHT * (11.40f / 57f)

        /**
         * Cap-box top: the cap is centred on the Wi-Fi glyph. The Wi-Fi ink height is
         * [Wifi.ARTBOARD_H] - 2 * [Wifi.ARTBOARD_MARGIN] = 94.77, so the extra space splits evenly.
         */
        val TOP: Float = Wifi.ARTBOARD_MARGIN +
            (Wifi.ARTBOARD_H - 2f * Wifi.ARTBOARD_MARGIN - CAP_HEIGHT) / 2f

        /** [IosDigits] cap 57 scaled to [CAP_HEIGHT]; digits scale through the Canvas, never retyped. */
        val DIGIT_SCALE: Float = CAP_HEIGHT / IosDigits.CAP_HEIGHT

        /** Measured reference ink width of L: 22 px / 36. */
        private const val L_INK_FRACTION = 22f / REF_CAP_PX
        /** Measured reference ink width of T: 29 px / 36. */
        private const val T_INK_FRACTION = 29f / REF_CAP_PX
        /** Measured reference ink width of E: 24 px / 36. */
        private const val E_INK_FRACTION = 24f / REF_CAP_PX
        /** Measured reference ink width of G: 34 px / 36 (a near-round bowl, wider than the old 0.66). */
        private const val G_INK_FRACTION = 34f / REF_CAP_PX
        /**
         * Shipped plus width: an equal-arm square at 0.75 cap, the measured reference plus HEIGHT
         * (27 px / 36 = 0.750). The reference width is 28 px / 36 = 0.778; equal arms use the measured
         * 0.750 (1 px inside the measured width). Honouring the reference plus makes "5G+" wider than
         * the 80x50 slot's height-limited width, so "5G+" is width-limited (see the object KDoc).
         */
        private const val PLUS_INK_FRACTION = 0.75f

        /** E middle arm is slightly shorter than the full width (reference 23 / 24). */
        private const val E_MID_ARM_FRACTION = 0.95f

        /*
         * G v3: measured from the 50 % mask of the three Gs in
         * `.pm/ref/r4/5-sf-labels-LTE-E-4G-5G-5Gplus.jpg` (cap 36 px, G ink box 33 x 38 px).
         *
         * The old G was a symmetric `W/2, C/2` oval band with a bar ending at 0.588 W and a short
         * stem; at production size it read as a C with a rectangle glued at the right. The v3 bowl
         * is shifted right (the G's ink box includes the right stem, so the bowl itself is not
         * centred), the bar starts further right, and the stem runs from the bar to the bottom of
         * the bowl as a trapezoid, so the upper-right aperture and the right stem are both visible.
         *
         * All numbers below are in the G ink box, x in `0..W`, y in `0..C`; they are the weight-1
         * (reference-weight) shape. [G_PRODUCTION_WEIGHT] scales every stroke (bowl band, bar,
         * stem) to the measured digit weight, keeping the outer envelope and stroke proportions.
         */
        private const val G_OUTER_CX_FRACTION = 0.61188f
        private const val G_OUTER_CY_FRACTION = 0.5f
        private const val G_OUTER_RX_FRACTION = 0.61188f
        private const val G_OUTER_RY_FRACTION = 0.5f
        /** Reference-weight inner bowl radii (the band thickness is `outer - inner` per axis). */
        private const val G_INNER_RX_FRACTION = 0.43272f
        private const val G_INNER_RY_FRACTION = 0.37898f
        /** Bar centre y and half thickness; measured bar rows y44..48 of the y27..64 G box. */
        private const val G_BAR_CENTER_FRACTION = 0.5102f
        private const val G_BAR_HALF_FRACTION = 0.06288f
        /** Bar inner end: x204 of the 184..217 box (0.607 of the 33 px ink width). */
        private const val G_BAR_LEFT_FRACTION = 0.60653f
        /** Stem left edge and its slanted bottom (left y60, right y59 of the measured box). */
        private const val G_STEM_LEFT_FRACTION = 0.84951f
        private const val G_STEM_BOTTOM_LEFT_FRACTION = 0.97497f
        private const val G_STEM_BOTTOM_RIGHT_FRACTION = 0.92007f
        /**
         * Open aperture: the band sweeps counter-clockwise from the measured upper-right terminal
         * (318.93 deg) through the top/left/bottom to the lower-right (20.66 deg). The gap between
         * those terminals (~62 deg, crossing 3 o'clock) is the aperture; it is not filled by the bar,
         * which starts below it.
         */
        private const val G_ARC_START_DEG = 318.932f
        private const val G_ARC_END_DEG = 20.661f
        /** Reference-weight stem thickness, the weight baseline: `STROKE / this` is the weight. */
        internal val G_REFERENCE_STEM_STROKE: Float =
            (1f - G_STEM_LEFT_FRACTION) * G_INK_FRACTION * CAP_HEIGHT
        /** Weight that renders the measured G at the digit stroke ([STROKE]). */
        internal val G_PRODUCTION_WEIGHT: Float = STROKE / G_REFERENCE_STEM_STROKE

        /**
         * Letter side bearing: 0.04 C gives a measured 3 px (0.083 C) T..E gap. The plus bearing is
         * 0.06 C, so the G..+ gap is 0.10 C, closer to the measured 0.167 C. This honouring of the
         * reference plus makes "5G+" width-limited in the 80x50 slot (the five other labels stay
         * height-limited).
         */
        private const val LETTER_SIDE_BEARING_FRACTION = 0.04f
        private const val PLUS_SIDE_BEARING_FRACTION = 0.06f

        private val L_WIDTH: Float = L_INK_FRACTION * CAP_HEIGHT
        private val T_WIDTH: Float = T_INK_FRACTION * CAP_HEIGHT
        private val E_WIDTH: Float = E_INK_FRACTION * CAP_HEIGHT
        private val G_WIDTH: Float = G_INK_FRACTION * CAP_HEIGHT
        private val PLUS_WIDTH: Float = PLUS_INK_FRACTION * CAP_HEIGHT
        private val LETTER_SIDE_BEARING: Float = LETTER_SIDE_BEARING_FRACTION * CAP_HEIGHT
        private val PLUS_SIDE_BEARING: Float = PLUS_SIDE_BEARING_FRACTION * CAP_HEIGHT

        /** True only for the exact supported labels; a caller must not draw anything else. */
        fun supports(label: String): Boolean = label in LABELS

        /** True for the digits rebuilt from [IosDigits]; those are filled even-odd, the rest WINDING. */
        fun isDigit(ch: Char): Boolean = ch == '3' || ch == '4' || ch == '5'

        /** Ink width of one label character; `0` for anything unsupported. */
        fun inkWidth(ch: Char): Float = when (ch) {
            'L' -> L_WIDTH
            'T' -> T_WIDTH
            'E' -> E_WIDTH
            'G' -> G_WIDTH
            '+' -> PLUS_WIDTH
            else -> if (isDigit(ch)) IosDigits.glyph(ch).width * DIGIT_SCALE else 0f
        }

        /**
         * Horizontal advance (pitch) of one label character; `0` for anything unsupported. Digits
         * carry the measured [IosDigits] advance scaled by [DIGIT_SCALE].
         */
        fun advance(ch: Char): Float = when (ch) {
            'L', 'T', 'E', 'G' -> inkWidth(ch) + 2f * LETTER_SIDE_BEARING
            '+' -> inkWidth(ch) + 2f * PLUS_SIDE_BEARING
            else -> if (isDigit(ch)) IosDigits.glyph(ch).advance * DIGIT_SCALE else 0f
        }

        /** The horizontal bearing that centres a glyph in its advance cell. */
        fun bearing(ch: Char): Float = (advance(ch) - inkWidth(ch)) / 2f

        /** Ink width of [label] (`sum(advances) - firstBearing - lastBearing`), `0` if empty/unsupported. */
        fun groupInkWidth(label: String): Float {
            if (label.isEmpty()) return 0f
            var total = 0f
            for (ch in label) total += advance(ch)
            total -= bearing(label.first())
            total -= bearing(label.last())
            return total
        }

        /** Ink width of a supported [label], or `null` when it is not in [LABELS]. */
        fun widthOf(label: String): Float? = if (supports(label)) groupInkWidth(label) else null

        /** Dynamic artboard width (`ink + 2 * Wifi.ARTBOARD_MARGIN`), or `null` if unsupported. */
        fun artboardWidth(label: String): Float? =
            widthOf(label)?.plus(2f * Wifi.ARTBOARD_MARGIN)

        /**
         * The filled outlines for a letter or plus, in the label cap box (`x` in
         * `0..inkWidth(ch)`, `y` in `0..CAP_HEIGHT`). Each [FloatArray] is one closed contour
         * (`x, y, x, y, ...`, first point not repeated), consistently clockwise; draw them together
         * with [android.graphics.Path.FillType.WINDING] so overlaps union. Digits return an empty
         * list because the renderer fills them from [IosDigits].
         */
        fun outlines(ch: Char): List<FloatArray> = outlines(ch, STROKE)

        /**
         * The same outlines rebuilt at [stroke] design units (the vertical stem/arm thickness).
         * Production passes [STROKE]; the raster evidence passes the measured reference stroke to
         * remove the bold/lighter weight difference before comparing IoU. G keeps the measured
         * reference shape and scales all of its strokes by `stroke / [G_REFERENCE_STEM_STROKE]`.
         */
        internal fun outlines(ch: Char, stroke: Float): List<FloatArray> = when (ch) {
            'L' -> listOf(glyphPolygon(
                0f, 0f, stroke, 0f, stroke, CAP_HEIGHT - stroke, L_WIDTH, CAP_HEIGHT - stroke,
                L_WIDTH, CAP_HEIGHT, 0f, CAP_HEIGHT
            ))
            'T' -> listOf(glyphPolygon(
                0f, 0f, T_WIDTH, 0f, T_WIDTH, stroke,
                T_WIDTH / 2f + stroke / 2f, stroke,
                T_WIDTH / 2f + stroke / 2f, CAP_HEIGHT,
                T_WIDTH / 2f - stroke / 2f, CAP_HEIGHT,
                T_WIDTH / 2f - stroke / 2f, stroke,
                0f, stroke
            ))
            'E' -> listOf(
                glyphRect(0f, 0f, stroke, CAP_HEIGHT),
                glyphRect(0f, 0f, E_WIDTH, stroke),
                glyphRect(0f, CAP_HEIGHT / 2f - stroke / 2f, E_MID_ARM_FRACTION * E_WIDTH, CAP_HEIGHT / 2f + stroke / 2f),
                glyphRect(0f, CAP_HEIGHT - stroke, E_WIDTH, CAP_HEIGHT)
            )
            'G' -> gOutlines(stroke / G_REFERENCE_STEM_STROKE)
            '+' -> listOf(
                glyphRect(
                    0f, CAP_HEIGHT / 2f - stroke / 2f,
                    PLUS_WIDTH, CAP_HEIGHT / 2f + stroke / 2f
                ),
                glyphRect(
                    PLUS_WIDTH / 2f - stroke / 2f, CAP_HEIGHT / 2f - PLUS_WIDTH / 2f,
                    PLUS_WIDTH / 2f + stroke / 2f, CAP_HEIGHT / 2f + PLUS_WIDTH / 2f
                )
            )
            else -> emptyList()
        }

        /**
         * The SF-style G, rebuilt from the measured 50 % mask (see the `G v3` constants block).
         *
         * Screen coordinates put 3 o'clock at 0 deg, 6 at +90, 9 at +180 and 12 at +270. The bowl
         * band sweeps counter-clockwise from the upper-right terminal ([G_ARC_START_DEG] 318.93)
         * through the top, left and bottom to the lower-right ([G_ARC_END_DEG] 20.66). The gap
         * between those ends crosses 3 o'clock and is the open upper aperture; it is not filled.
         *
         * Three clockwise contours union under WINDING:
         *  - the bowl band (outer arc forward, inner arc backward), whose outer x is clamped to the
         *    ink-box right edge because the measured bowl ellipse extends under the stem;
         *  - the horizontal bar from [G_BAR_LEFT_FRACTION] W to W at the measured mid height;
         *  - the right stem from the bar down to a slanted bottom ([G_STEM_BOTTOM_LEFT_FRACTION] on
         *    the left, [G_STEM_BOTTOM_RIGHT_FRACTION] on the right), which joins the bowl bottom.
         *
         * [weight] is 1 at the measured reference stroke and [G_PRODUCTION_WEIGHT] at the digit
         * stroke; it scales the band thickness, bar thickness and stem width together, so the
         * glyph keeps one letterform at either weight. The bowl opens to the right above the bar,
         * so the counter is not an enclosed hole and the glyph is visibly a G, not a C.
         */
        private fun gOutlines(weight: Float = G_PRODUCTION_WEIGHT): List<FloatArray> {
            val cx = G_OUTER_CX_FRACTION * G_WIDTH
            val cy = G_OUTER_CY_FRACTION * CAP_HEIGHT
            val outerRx = G_OUTER_RX_FRACTION * G_WIDTH
            val outerRy = G_OUTER_RY_FRACTION * CAP_HEIGHT
            val innerRx =
                outerRx - (G_OUTER_RX_FRACTION - G_INNER_RX_FRACTION) * G_WIDTH * weight
            val innerRy =
                outerRy - (G_OUTER_RY_FRACTION - G_INNER_RY_FRACTION) * CAP_HEIGHT * weight
            val steps = 160
            val outer = FloatArray((steps + 1) * 2)
            val inner = FloatArray((steps + 1) * 2)
            for (i in 0..steps) {
                val degrees = G_ARC_START_DEG + (G_ARC_END_DEG - G_ARC_START_DEG) * i / steps
                val radians = degrees * (Math.PI / 180.0)
                val c = cos(radians).toFloat()
                val s = sin(radians).toFloat()
                // The measured bowl reaches under the stem; clamp it to the ink box so the glyph
                // never draws past its own advance.
                outer[i * 2] = (cx + outerRx * c).coerceAtMost(G_WIDTH)
                outer[i * 2 + 1] = cy + outerRy * s
                inner[i * 2] = (cx + innerRx * c).coerceAtMost(G_WIDTH)
                inner[i * 2 + 1] = cy + innerRy * s
            }
            val n = (steps + 1) * 2
            val band = FloatArray(n * 2)
            System.arraycopy(outer, 0, band, 0, n)
            for (i in 0..steps) {
                band[n + i * 2] = inner[(steps - i) * 2]
                band[n + i * 2 + 1] = inner[(steps - i) * 2 + 1]
            }
            val barCenter = G_BAR_CENTER_FRACTION * CAP_HEIGHT
            val barHalf = G_BAR_HALF_FRACTION * CAP_HEIGHT * weight
            val stemLeft = G_WIDTH - G_REFERENCE_STEM_STROKE * weight
            val stemBottomLeft = G_STEM_BOTTOM_LEFT_FRACTION * CAP_HEIGHT
            val stemBottomRight = G_STEM_BOTTOM_RIGHT_FRACTION * CAP_HEIGHT
            return listOf(
                ensureClockwise(band),
                glyphRect(
                    G_BAR_LEFT_FRACTION * G_WIDTH, barCenter - barHalf,
                    G_WIDTH, barCenter + barHalf
                ),
                glyphPolygon(
                    stemLeft, barCenter - barHalf,
                    G_WIDTH, barCenter - barHalf,
                    G_WIDTH, stemBottomRight,
                    stemLeft, stemBottomLeft
                )
            )
        }

        /** A closed rectangle contour, clockwise in screen coordinates. */
        private fun glyphRect(left: Float, top: Float, right: Float, bottom: Float): FloatArray =
            glyphPolygon(left, top, right, top, right, bottom, left, bottom)

        /** A closed contour from `x, y, ...` pairs, normalised to clockwise (screen y down). */
        private fun glyphPolygon(vararg points: Float): FloatArray =
            ensureClockwise(points.copyOf())

        /**
         * Reverses [points] when its signed area is negative (counter-clockwise in screen
         * coordinates, where positive shoelace area is clockwise) so WINDING fills union.
         */
        private fun ensureClockwise(points: FloatArray): FloatArray {
            var area = 0f
            val count = points.size / 2
            for (i in 0 until count) {
                val j = (i + 1) % count
                area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1]
            }
            if (area >= 0f) return points
            val reversed = FloatArray(points.size)
            for (i in 0 until count) {
                reversed[i * 2] = points[(count - 1 - i) * 2]
                reversed[i * 2 + 1] = points[(count - 1 - i) * 2 + 1]
            }
            return reversed
        }
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
     * (owner target): battery body = 1.0, clock digit cap = [CLOCK_CAP] (1.00 +/-3 %). The R3 draw
     * scales were the ref 7 on-device calibration, `iOS target ratio / current on-device ratio`:
     * battery 1.23 -> 1.00 = 0.81, Wi-Fi 1.67 -> 0.944 = 0.57, cell single 1.267 -> 0.955 = 0.754
     * (0.75; the older 1.32 -> 0.726 held before bar 4 became 95.5 units), cell dual 1.38 -> 1.042 =
     * 0.76. [CELL_DUAL_H] = 1.042 is the measured ref target; the shipped design block is
     * [Cell.DUAL_BLOCK_H] = 102.8 (1.028 of the battery body) because the dual bar heights are
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
