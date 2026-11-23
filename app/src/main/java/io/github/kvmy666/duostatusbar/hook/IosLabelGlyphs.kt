package io.github.kvmy666.duostatusbar.hook

import android.graphics.Path

/**
 * The one source of the data-label glyphs, read from the R10 regular-sheet trace
 * (`rive/ios/glyphs-r10.json`, `R10Glyphs.LABELS`).
 *
 * Every glyph is a measured set of closed cubic contours in design units, cap top `y = 0` and
 * ink-left `x = 0`, so a glyph is placed at its pen and the pen walks the measured
 * [advance] (the ink-left advance model; there is no separate bearing). Cap height is
 * [R10Glyphs.LABEL_CAP] = 71.4.
 *
 * Provenance: `L`, `T`, `E`, `G`, `3`, `4`, `5` and `+` shapes are the regular (r10) sheet; the plus
 * shape is taken from the r4 SF strip and is flagged. `L`, `T`, `5`, `4` and `3` advances are
 * measured; the `G`, `E` and `+` advances are extrapolated from the median measured side bearing and
 * flagged in the data.
 *
 * Digit glyphs in this table are independent of the percentage digits: the label `5` comes from the
 * label sheet, not from the battery digit table.
 */
internal object IosLabelGlyphs {

    /** The label cap height in design units (R10 regular sheet). */
    val CAP_HEIGHT: Float = R10Glyphs.LABEL_CAP

    /** The measured label glyph for [ch], or `null` when the character is not in the table. */
    fun glyph(ch: Char): R10Glyphs.Glyph? = R10Glyphs.LABELS[ch]

    /** True when [ch] has a traced label glyph. */
    fun supports(ch: Char): Boolean = R10Glyphs.LABELS.containsKey(ch)

    /** Ink width of [ch]; `0` when it is not a label glyph. */
    fun inkWidth(ch: Char): Float = R10Glyphs.LABELS[ch]?.width ?: 0f

    /** Measured advance of [ch]; `0` when it is not a label glyph. */
    fun advance(ch: Char): Float = R10Glyphs.LABELS[ch]?.advance ?: 0f

    /** True when the advance of [ch] was measured; `false` for an extrapolated or unknown glyph. */
    fun advanceMeasured(ch: Char): Boolean = R10Glyphs.LABELS[ch]?.advanceMeasured ?: false

    /**
     * The horizontal bearing in the ink-left model: always `0`, because the traced glyph starts at
     * its ink-left edge and the advance already contains the measured spacing.
     */
    fun bearing(ch: Char): Float = 0f

    /**
     * Ink width of [label] in the ink-left model:
     * `sum(advances of all but last) + last ink width`; `0` when empty.
     */
    fun groupInkWidth(label: String): Float {
        if (label.isEmpty()) return 0f
        var total = 0f
        for (i in 0 until label.length - 1) total += advance(label[i])
        total += inkWidth(label.last())
        return total
    }

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
}
