package io.github.kvmy666.duostatusbar.hook

import android.graphics.Path

/**
 * The percentage digits: traced filled contours from the R10 rounded-bold digit sheet
 * (`.pm/ref/r10/battery-digits-rounded-bold.jpg`, `rive/ios/glyphs-r10.json`, `R10Glyphs.DIGITS`),
 * not a font and not a stroke skeleton.
 *
 * Each glyph is a measured set of closed cubic contours in design units, cap top `y = 0` and
 * ink-left `x = 0`; the outer contour comes first, then any holes. A glyph is drawn as ONE
 * [Path.FillType.EVEN_ODD] path at its pen position, so the holes of `0`, `4`, `6`, `8` and `9`
 * stay unfilled. The cap is [CAP_HEIGHT] = 60.7 and the advances are the measured ink-left advances
 * from the data; the terminal `7` and `0` advances are extrapolated from the median side bearing and
 * are flagged by [advanceMeasured]. The battery knockout clears the filled ink band, so a counter is
 * body ink, not a hole.
 *
 * The runtime uses [buildPath] everywhere: the battery knockout/solid-white digits and the preview.
 * The bolt geometry below is the unchanged R9 bolt contract.
 */
internal object IosDigits {

    /** R10 regular sheet: digit cap 60.7 units (0.607 of the battery body). */
    const val CAP_HEIGHT = 60.7f

    const val INLINE_BOLT_W = 40f
    const val INLINE_BOLT_H = 59f
    const val INLINE_BOLT_GAP = 5f

    /** R9-design.md: the no-digit bolt is ~67 x 92, solid system ink, no outline. */
    const val LARGE_BOLT_W = 67f
    const val LARGE_BOLT_H = 92f

    /** R9 removes the outline; kept as a compile-time shim for the old tests. */
    const val LARGE_BOLT_OUTLINE = 0f

    /**
     * The bolt normalised to its own 0..1 box. The same points drive the large solid bolt and the
     * inline knockout bolt.
     */
    internal val BOLT_NORMALIZED: FloatArray = floatArrayOf(
        0.7133f, 0f,
        0.7133f, 0.0975f,
        0.5667f, 0.3008f,
        0.54f, 0.3856f,
        0.5533f, 0.4195f,
        0.6067f, 0.4449f,
        0.98f, 0.4364f,
        0.9933f, 0.4619f,
        0.98f, 0.4873f,
        0.66f, 0.7076f,
        0.4467f, 0.8941f,
        0.2867f, 0.9958f,
        0.2867f, 0.911f,
        0.4333f, 0.6992f,
        0.46f, 0.6144f,
        0.4067f, 0.572f,
        0.0333f, 0.572f,
        0.0067f, 0.5466f,
        0.18f, 0.3941f,
        0.2733f, 0.3432f,
        0.46f, 0.1822f
    )

    /** The traced digit for [ch], or `0` when a caller passes something that is not a digit. */
    fun glyph(ch: Char): R10Glyphs.Glyph = R10Glyphs.DIGITS[ch] ?: R10Glyphs.DIGITS.getValue('0')

    /** The ink width of [ch]; `0` when it is not a digit. */
    fun inkWidth(ch: Char): Float = R10Glyphs.DIGITS[ch]?.width ?: 0f

    /** The measured ink-left advance of [ch]; `0` when it is not a digit. */
    fun advance(ch: Char): Float = R10Glyphs.DIGITS[ch]?.advance ?: 0f

    /** True when the advance of [ch] was measured; `false` for extrapolated `7`/`0` or non-digits. */
    fun advanceMeasured(ch: Char): Boolean = R10Glyphs.DIGITS[ch]?.advanceMeasured ?: false

    /**
     * One [Path] for [glyph], translated to ([originX], [originY]) and filled with
     * [Path.FillType.EVEN_ODD]. Each contour is a closed cubic chain: `moveTo` its first segment
     * start, then `cubicTo` every segment, then `close`.
     */
    fun buildPath(glyph: R10Glyphs.Glyph, originX: Float, originY: Float): Path {
        val path = Path()
        path.fillType = Path.FillType.EVEN_ODD
        for (contour in glyph.contours) {
            if (contour.size < 8) continue
            path.moveTo(originX + contour[0], originY + contour[1])
            var i = 0
            while (i + 7 < contour.size) {
                path.cubicTo(
                    originX + contour[i + 2], originY + contour[i + 3],
                    originX + contour[i + 4], originY + contour[i + 5],
                    originX + contour[i + 6], originY + contour[i + 7]
                )
                i += 8
            }
            path.close()
        }
        return path
    }

    /**
     * Ink width of [text] in the ink-left model: the advances of every glyph except the last plus
     * the last glyph's ink width.
     */
    fun textInkWidth(text: String): Float {
        if (text.isEmpty()) return 0f
        var total = 0f
        for (i in 0 until text.length - 1) total += advance(text[i])
        total += inkWidth(text.last())
        return total
    }

    /** [textInkWidth] plus the inline bolt when [withBolt] is set. */
    fun groupWidth(text: String, withBolt: Boolean): Float =
        textInkWidth(text) + if (withBolt) INLINE_BOLT_GAP + INLINE_BOLT_W else 0f

    /**
     * A closed bolt from [BOLT_NORMALIZED], scaled to [w] x [h] and centred at ([cx], [cy]).
     */
    fun boltPath(w: Float, h: Float, cx: Float, cy: Float): Path {
        val left = cx - w / 2f
        val top = cy - h / 2f
        return Path().apply {
            var i = 0
            while (i < BOLT_NORMALIZED.size) {
                val x = left + BOLT_NORMALIZED[i] * w
                val y = top + BOLT_NORMALIZED[i + 1] * h
                if (i == 0) moveTo(x, y) else lineTo(x, y)
                i += 2
            }
            close()
        }
    }

    /** The large solid bolt centred on the battery body (R9: 67 x 92, no outline). */
    fun largeBoltPath(): Path = boltPath(
        LARGE_BOLT_W,
        LARGE_BOLT_H,
        IosGeometry.Battery.BODY_LEFT + IosGeometry.Battery.BODY_W / 2f,
        IosGeometry.Battery.BODY_TOP + IosGeometry.Battery.BODY_H / 2f
    )

    /** The small bolt that follows the percentage when the digits are inside the body. */
    fun inlineBoltPath(cx: Float, cy: Float): Path =
        boltPath(INLINE_BOLT_W, INLINE_BOLT_H, cx, cy)
}
