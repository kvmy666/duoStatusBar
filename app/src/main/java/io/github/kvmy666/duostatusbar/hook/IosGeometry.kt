package io.github.kvmy666.duostatusbar.hook

/**
 * The one source of truth for every iOS glyph dimension, in design units where the battery body
 * height is exactly [UNIT_HEIGHT] (100).
 *
 * The numbers come from the owner's two reference images, measured with a 50%-threshold connected-
 * component script at battery height 88.93 px (light) and 71.28 px (dark, a 0.798x image). The dark
 * image is only trusted for the dual-SIM layout, the inactive alpha and the battery split; the light
 * image supplies the proportions. The step-3 cap/bar polish target is the owner's annotated
 * reference capture (measurements in
 * the measured polish tables, kept outside the repository); where it conflicts with the light PNG the conflict
 * is recorded as UNVERIFIED rather than averaged. See the B3 measurement notes (kept outside the repository) and
 * `docs/ios-geometry.md`.
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
         * Step-3 polish target: the owner's annotated reference, which measures 15.17 x 36.84 units
         * (annotated 50% bbox 19.62 x 47.64 px at s=1.2930). The light PNG's 9.79 x 28.76 is a
         * different, smaller variant; the size conflict is UNVERIFIED and is not averaged. See
         * the measured polish tables (kept outside the repository) §1 and `docs/ios-geometry.md`.
         */
        const val CAP_W = 15.2f
        const val CAP_H = 36.8f
        const val CAP_GAP = 7.5f
        /** The annotated cap centre sits on the body centre (annotated delta -0.07 units). */
        const val CAP_LIFT = 0f

        /**
         * The cap is the right-hand half of a superellipse with this exponent, flat on the body side:
         * the owner-annotated chunky/rounded-triangular (trapezoid-like) shape. The light PNG shows
         * the same shape at a smaller size (shape IoU 0.987).
         */
        const val CAP_SHAPE_N = 1.8f

        /** 1-unit margin around the tight glyph bounds. */
        const val ARTBOARD_W = BODY_W + CAP_GAP + CAP_W + 2f
        const val ARTBOARD_H = BODY_H + 2f

        const val BODY_LEFT = 1f
        const val BODY_TOP = 1f
        const val CAP_LEFT = BODY_LEFT + BODY_W + CAP_GAP
        const val CAP_TOP = BODY_TOP + (BODY_H - CAP_H) / 2f - CAP_LIFT

        /**
         * The owner-annotated cap: the right-hand half of the [CAP_SHAPE_N] superellipse, discretised
         * as a 25-gon (rounded triangular/trapezoid), flat base on the body side. The annotated cap
         * IoU is 0.971 (n=1.8) versus 0.789 for the old 24-gon; the light PNG matches this shape at
         * its smaller size (0.987). Absolute artboard coordinates, closed, clockwise; the closing edge
         * is the flat base at x=CAP_LEFT. Both renderers draw this same polygon.
         */
        val CAP_POINTS: FloatArray = floatArrayOf(
            201.00f, 32.60f,
            206.20f, 34.13f,
            208.49f, 35.67f,
            210.19f, 37.20f,
            211.55f, 38.73f,
            212.67f, 40.27f,
            213.59f, 41.80f,
            214.36f, 43.33f,
            214.99f, 44.87f,
            215.49f, 46.40f,
            215.86f, 47.93f,
            216.10f, 49.47f,
            216.20f, 51.00f,
            216.10f, 52.53f,
            215.86f, 54.07f,
            215.49f, 55.60f,
            214.99f, 57.13f,
            214.36f, 58.67f,
            213.59f, 60.20f,
            212.67f, 61.73f,
            211.55f, 63.27f,
            210.19f, 64.80f,
            208.49f, 66.33f,
            206.20f, 67.87f,
            201.00f, 69.40f
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
         * Step-3 target: the owner-annotated single bars, 0.480 / 0.634 / 0.812 / 0.990 of the
         * battery height, bottom-aligned at y=100 in the 105.6 artboard (annotated 47.95 / 63.42 /
         * 81.21 / 98.99; the light PNG's 0.364..0.948 is a shorter variant, UNVERIFIED).
         */
        val SINGLE_BAR_HEIGHTS = floatArrayOf(48.0f, 63.4f, 81.2f, 99.0f)

        /** Measured dark (compressed dual row): 0.303 / 0.375 / 0.499 / 0.627. */
        val DUAL_BAR_HEIGHTS = floatArrayOf(30.3f, 37.5f, 49.9f, 62.7f)

        const val BLOCK_W = 3f * PITCH + BAR_W
        const val DUAL_ROW_H = 62.7f
        const val DUAL_GAP = 13.5f
        const val DUAL_BLOCK_H = DUAL_ROW_H + DUAL_GAP + DOT_H

        /** Artboard holds the taller dual block; the single bars keep their measured height inside it. */
        const val ARTBOARD_W = BLOCK_W + 2f
        const val ARTBOARD_H = DUAL_BLOCK_H + 2f

        const val LEFT = 1f
        const val TOP = 1f
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
}
