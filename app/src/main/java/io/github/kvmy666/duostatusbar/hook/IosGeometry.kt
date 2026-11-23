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

        /** Common arc centre in artboard space; R9 lowers it 1.5 units (owner: slightly high). */
        const val CENTRE_X = 64.809f
        const val CENTRE_Y = 95.022f

        /** Outer arc: mid-radius, constant stroke width, round caps (R9 stroke 18.6). */
        const val OUTER_RADIUS = 81.2f
        const val OUTER_STROKE = 18.6f
        const val OUTER_TRIM_END = 0.232575f
        const val OUTER_TRIM_OFFSET = 0.884175f

        /** Inner arc: same centre, its own radius/stroke/span (R9 stroke 19.4). */
        const val INNER_RADIUS = 47.4f
        const val INNER_STROKE = 19.4f
        const val INNER_TRIM_END = 0.220078f
        const val INNER_TRIM_OFFSET = 0.889917f

        /** R9 dot 38 x 28, centred on the arc x and the measured dot row. */
        const val DOT_W = 38f
        const val DOT_H = 28f
        const val DOT_CENTRE_X = CENTRE_X
        const val DOT_CENTRE_Y = 81.77f

        /**
         * The measured dot as a left-right symmetric 32-gon (R9: 38 x 28). Absolute artboard
         * coordinates, closed. The dot stays at full tint, independent of [wifiLevel].
         */
        val DOT_POINTS: FloatArray = FloatArray(64).also { out ->
            for (i in 0 until 32) {
                val angle = 2.0 * Math.PI * i / 32.0
                out[i * 2] = DOT_CENTRE_X + (DOT_W / 2f) * cos(angle).toFloat()
                out[i * 2 + 1] = DOT_CENTRE_Y + (DOT_H / 2f) * sin(angle).toFloat()
            }
        }

        /** Opacity per Wi-Fi level (0..3), unchanged from the Duo binding rule. */
        val OUTER_ALPHA = floatArrayOf(0.3f, 0.3f, 0.3f, 1f)
        val INNER_ALPHA = floatArrayOf(0.3f, 0.3f, 1f, 1f)
    }

    /**
     * The data-type label that occupies the Wi-Fi slot for a measured cellular network
     * ("LTE", "4G", "5G", ...).
     *
     * **This label is now measured on a real iPhone.** Reference 1
     * `.pm/ref/r5/1-real-iphone-5g-dual.jpg` (sha256 `0e0096738eb3b246…`, 1272x2772, dark, dual-SIM)
     * is the first real iOS status-bar capture that shows it, reading "5G". Its 50 %-threshold
     * measurement (`.pm/ref/out-r5/r5b-measurements.md` / `.json`; battery body H = 42.377 px = 100
     * units, 1 px = 2.360 units, +/-0.5 px = +/-1.18 units) fixes [CAP_HEIGHT] and [TOP] as measured
     * values instead of the old bar/weight fallbacks.
     *
     * The label is TOP-ANCHORED: the measured cap top sits 14.95 units below the label/Wi-Fi artboard
     * top and the baseline at 92.63 ([TOP] + [CAP_HEIGHT] = 92.64). The old centre-on-Wi-Fi formula
     * would put the cap top at 9.54 for this cap, which is **not** what ref 1 shows; [TOP] is pinned
     * to the measurement. Ref 1's cap centre is 2.79 units below the battery body centre.
     *
     * The SF strip `.pm/ref/r4/5-sf-labels-LTE-E-4G-5G-5Gplus.jpg` stays the authority for the glyph
     * SHAPES, ink widths and internal spacing. **R6 item 3 makes every label glyph one weight:** the
     * letters, the plus and the G use [STROKE] = 15.54 units (0.200 cap), deliberately equal to the
     * battery digits' measured run-stroke ([IosDigits.RUN_STROKE] 11.40 x [DIGIT_SCALE]). The R5B
     * ref-1 SF label weight of 11.1 units (0.143 cap) remains provenance for the measurement only;
     * it is no longer a glyph constant, because it made the owner-visible "5" bold and "G" thin.
     * The 3/4/5 digits stay the untouched measured [IosDigits] glyphs — never retyped or thinned —
     * and the G ink fraction and every letter width are unchanged.
     *
     * Every letter and the plus are FILLED OUTLINES: one or more closed contours, consistently
     * clockwise, filled with [android.graphics.Path.FillType.WINDING] so overlapping shapes union
     * without alpha seams. There is no font and no stroked centre line. Digits reuse the [IosDigits]
     * widths, advances and filled even-odd contours (scaled by [DIGIT_SCALE]); the letter widths are
     * the measured reference ink widths as fractions of the reference cap. The group is laid out
     * exactly like [IosDigits]: `sum(advances) - firstBearing - lastBearing`. The artboard width is
     * dynamic ([widthOf] + 2 * [Wifi.ARTBOARD_MARGIN]) and its height stays [Wifi.ARTBOARD_H]; the ink
     * group starts at [Wifi.ARTBOARD_MARGIN] horizontally and at the measured [TOP] vertically.
     *
     * The SF reference cap is 36 px and its ink widths (px / 36) are L 0.611, T 0.806, E 0.667,
     * G 0.944 and plus 0.778 x 0.750. The shipped plus is an equal-arm 0.75-cap square (the measured
     * height; 1 px inside the measured width). Honouring that reference plus makes "5G+" wider than
     * the 80x50 production slot's height-limited width, so "5G+" is width-limited (~19.4 px cap,
     * ~0.63 of the Wi-Fi ink); at the measured cap LTE is too wide as well (~22.1 px cap, ~0.72),
     * while 4G/5G/3G/E stay height-limited (~25.1 px cap, 0.820 of the Wi-Fi ink). The G is the v3
     * measured letterform: a right-shifted bowl (0.612 W centre,
     * 0.612 W x 0.5 C outer radii), a 318.93..20.66 degree band with the open aperture crossing
     * 3 o'clock, a mid-height bar whose inner end is at 0.607 W, and a right stem from the bar to a
     * slanted bottom (0.975/0.920 C). The counter opens through the upper-right aperture, so there is
     * no closed hole and the glyph reads as G, not a C with a glued corner. All G strokes scale
     * with one weight from the measured reference (weight 1) to the label stroke
     * ([G_PRODUCTION_WEIGHT] = [STROKE] / [DataLabel.G_REFERENCE_STEM_STROKE] = ~1.407), so the
     * right stem is the 0.200-cap label stroke and the measured bowl/bar proportions are kept.
     */
    object DataLabel {
        /** Supported labels, in reader/reporting order. */
        val LABELS = listOf("LTE", "4G", "5G", "5G+", "3G", "E", "G")

        /** Reference cap (px): the flat-letter height (L/T/E/4) in the SF strip. */
        private const val REF_CAP_PX = 36f

        /** R9-design.md: label cap 71.4 units (10 pt). */
        const val CAP_HEIGHT = 71.4f

        /**
         * R9-design.md: label stroke 13.6 units (0.19 cap). The 3/4/5 reuse the digit stroke
         * skeleton at [DIGIT_SCALE], painted at [STROKE] / [DIGIT_SCALE] before the scale, so their
         * rendered stroke is 13.6.
         */
        const val STROKE = 13.6f

        /** R9-design.md: TOP 17.4 (cap centre 2 px below the battery centre). */
        const val TOP = 17.4f

        /** [IosDigits] cap 57 scaled to [CAP_HEIGHT]; digits scale through the Canvas, never retyped. */
        val DIGIT_SCALE: Float = CAP_HEIGHT / IosDigits.CAP_HEIGHT

        /** Measured reference ink width of L: 22 px / 36. */
        private const val L_INK_FRACTION = 22f / REF_CAP_PX
        /** Measured reference ink width of T: 29 px / 36. */
        private const val T_INK_FRACTION = 29f / REF_CAP_PX
        /** Measured reference ink width of E: 24 px / 36. */
        private const val E_INK_FRACTION = 24f / REF_CAP_PX
        /** Measured reference ink width of G: 0.847 cap (R9-design.md: G ink 60.5). */
        private const val G_INK_FRACTION = 0.847f
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
        /*
         * R6 item 3 keeps the R5B measured G construction. The G's weight metric is its RIGHT STEM
         * (the same `G stem` measurement used by the R5B reference), and the stem is set by STROKE
         * for any inner-radius choice. Production scales the measured band, bar and stem thicknesses
         * together by G_PRODUCTION_WEIGHT, so the right stem is 15.54 (0.200 cap) while the measured
         * bowl/bar proportions (bar ~0.88x, bowl top ~0.85x, bowl side ~1.19x of the stem) stay.
         */
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
        /** Weight that renders the measured G at the digit stroke ([STROKE] 15.54 / 11.04 = ~1.407). */
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
         * Horizontal advance (pitch) of one label character; `0` for anything unsupported. Every
         * glyph uses letter spacing: a digit's advance is its scaled [IosDigits] ink width plus
         * [LETTER_SIDE_BEARING] on both sides, never the battery's tabular [IosDigits.ADVANCE] cell.
         */
        fun advance(ch: Char): Float = when (ch) {
            'L', 'T', 'E', 'G' -> inkWidth(ch) + 2f * LETTER_SIDE_BEARING
            '+' -> inkWidth(ch) + 2f * PLUS_SIDE_BEARING
            else -> if (isDigit(ch)) inkWidth(ch) + 2f * LETTER_SIDE_BEARING else 0f
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
         * stroke; it scales the measured band thickness, bar thickness and stem width together, so
         * the glyph keeps the measured letterform and its measured stroke proportions at either
         * weight. The G's weight guard metric is the right stem (the R5B `G stem` measurement);
         * the band and bar are not uniform multiples of the stem and are not measured as the G
         * stroke. The bowl opens to the right above the bar, so the counter is not an enclosed hole
         * and the glyph is visibly a G, not a C.
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
     * are included, then the dot box is unioned in.
     */
    private fun wifiGlyphInkBox(): InkBox {
        val w = Wifi
        val radius = w.OUTER_RADIUS + w.OUTER_STROKE / 2f
        val startDeg = -90f + w.OUTER_TRIM_OFFSET * 360f
        val sweepDeg = w.OUTER_TRIM_END * 360f
        var left = w.DOT_CENTRE_X - w.DOT_W / 2f
        var top = w.DOT_CENTRE_Y - w.DOT_H / 2f
        var right = w.DOT_CENTRE_X + w.DOT_W / 2f
        var bottom = w.DOT_CENTRE_Y + w.DOT_H / 2f
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
