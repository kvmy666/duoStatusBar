package io.github.kvmy666.duostatusbar.hook

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

/**
 * The R9 percentage digits: OUR OWN stroke skeletons, not a font and not filled contours.
 *
 * R9-design.md fixes cap **60.7**, stroke **12.1**, one equal advance **64.3** and per-digit ink
 * widths `50.8/26.7/43.0/44.0/49.2/44.0/46.1/42.5/45.7/46.8` for 0..9. The skeleton is drawn with
 * `Paint.Style.STROKE`, `Cap.ROUND`, `Join.ROUND`, so a terminal is a semicircle and the drawn ink
 * width is `skeleton width + stroke`.
 *
 * The runtime uses [buildPath] everywhere: the battery knockout (CLEAR stroke), the data-label digits
 * (tint stroke) and the preview. The [Glyph.contours] property is a deprecated, empty compile-time
 * shim for `IosGeometryParityTest` (the Rive scene is rebuilt by another builder); there is no
 * filled-contour glyph API.
 */
internal object IosDigits {

    /** R9-design.md: digit cap 60.7 units (0.607 of the battery body). */
    const val CAP_HEIGHT = 60.7f

    /** R9-design.md: digit stroke 12.1 units (0.199 cap). */
    const val STROKE = 12.1f

    /** Kept for call sites/tests that used the old name; R9's digit stroke is [STROKE]. */
    const val RUN_STROKE = STROKE

    /** R9-design.md: one equal advance cell for every digit. */
    const val ADVANCE = 64.3f

    const val INLINE_BOLT_W = 40f
    const val INLINE_BOLT_H = 59f
    const val INLINE_BOLT_GAP = 5f

    /** R9-design.md: the no-digit bolt is ~67 x 92, solid system ink, no outline. */
    const val LARGE_BOLT_W = 67f
    const val LARGE_BOLT_H = 92f

    /** R9 removes the outline; kept as a compile-time shim for the old tests. */
    const val LARGE_BOLT_OUTLINE = 0f

    /** The skeleton inset (the stroke radius); the ink box is `0..width` / `0..CAP`. */
    private val R = STROKE / 2f

    /**
     * One digit: its ink [width], its horizontal [advance] (pitch), and the centre-line [builder].
     * Callers draw it as a stroked path with round caps/joins.
     */
    class Glyph(
        val width: Float,
        val advance: Float,
        @Deprecated("R9: digits are stroke skeletons; kept for the Rive parity test only.")
        val contours: List<FloatArray>,
        internal val builder: (Path, Float) -> Unit
    )

    private val GLYPHS: Map<Char, Glyph> = mapOf(
        '0' to glyph(50.8f) { p, w ->
            p.addRoundRect(RectF(R, R, w - R, CAP_HEIGHT - R), 19f, 19f, Path.Direction.CW)
        },
        '1' to glyph(26.7f) { p, w ->
            p.moveTo(R, R + 18f)
            p.lineTo(w - R, R)
            p.lineTo(w - R, CAP_HEIGHT - R)
        },
        '2' to glyph(43.0f) { p, w ->
            p.moveTo(R, R + 18f)
            p.arcTo(RectF(R, R, w - R, R + 36f), 180f, 180f, false)
            p.lineTo(R, CAP_HEIGHT - R)
            p.lineTo(w - R, CAP_HEIGHT - R)
        },
        '3' to glyph(44.0f) { p, w ->
            val mid = w / 2f
            p.moveTo(R, R + 18f)
            p.arcTo(RectF(R, R, w - R, R + 36f), 180f, 180f, false)
            p.cubicTo(w - R, R + 24f, mid + 4f, R + 30.35f, mid, R + 30.35f)
            p.cubicTo(mid + 4f, R + 30.35f, w - R, R + 36f, w - R, R + 42.7f)
            p.arcTo(
                RectF(R, CAP_HEIGHT - R - 36f, w - R, CAP_HEIGHT - R),
                0f,
                180f,
                false
            )
        },
        '4' to glyph(49.2f) { p, w ->
            val crossY = R + 32f
            p.moveTo(R, crossY)
            p.lineTo(w - R - 10f, R)
            p.moveTo(w - R, R)
            p.lineTo(w - R, CAP_HEIGHT - R)
            p.moveTo(R, crossY)
            p.lineTo(w - R, crossY)
        },
        '5' to glyph(44.0f) { p, w ->
            p.moveTo(w - R, R)
            p.lineTo(R, R)
            p.lineTo(R, R + 20f)
            p.cubicTo(R + 8f, R + 20f, w - R, R + 24f, w - R, R + 34f)
            p.cubicTo(w - R, R + 44f, R + 16f, CAP_HEIGHT - R, R + 8f, CAP_HEIGHT - R)
        },
        '6' to glyph(46.1f) { p, w ->
            p.moveTo(w - R, R)
            p.cubicTo(w / 2f, R, R, R + 12f, R, R + 30f)
            p.addOval(RectF(R, R + 20f, w - R, CAP_HEIGHT - R), Path.Direction.CW)
        },
        '7' to glyph(42.5f) { p, w ->
            p.moveTo(R, R)
            p.lineTo(w - R, R)
            p.lineTo(R + 8f, CAP_HEIGHT - R)
        },
        '8' to glyph(45.7f) { p, w ->
            p.addOval(RectF(R, R, w - R, R + 26f), Path.Direction.CW)
            p.addOval(RectF(R, CAP_HEIGHT - R - 26f, w - R, CAP_HEIGHT - R), Path.Direction.CW)
        },
        '9' to glyph(46.8f) { p, w ->
            p.addOval(RectF(R, R, w - R, R + 36f), Path.Direction.CW)
            p.moveTo(w - R, R + 18f)
            p.lineTo(w - R, CAP_HEIGHT - R - 14f)
            p.cubicTo(w - R, CAP_HEIGHT - R, w / 2f, CAP_HEIGHT - R, R + 10f, CAP_HEIGHT - R)
        }
    )

    private fun glyph(width: Float, builder: (Path, Float) -> Unit): Glyph =
        Glyph(width, ADVANCE, emptyList(), builder)

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

    /** The horizontal bearing that centres a glyph in its advance cell. */
    fun bearing(glyph: Glyph): Float = (glyph.advance - glyph.width) / 2f

    /** The digit for [ch], or `0` when a caller passes something that is not a digit. */
    fun glyph(ch: Char): Glyph = GLYPHS[ch] ?: GLYPHS.getValue('0')

    /** The ink width of [ch]; `0` when it is not a digit. */
    fun inkWidth(ch: Char): Float = GLYPHS[ch]?.width ?: 0f

    /** The advance of [ch]; `ADVANCE` for every digit. */
    fun advance(ch: Char): Float = GLYPHS[ch]?.advance ?: 0f

    /**
     * The centre-line [Path] of [glyph], translated to ([originX], [originY]). Draw it with a
     * `Style.STROKE` paint whose width is [STROKE] and whose cap/join are round.
     */
    fun buildPath(glyph: Glyph, originX: Float, originY: Float): Path {
        val path = Path()
        glyph.builder.invoke(path, glyph.width)
        if (originX != 0f || originY != 0f) {
            path.transform(Matrix().apply { setTranslate(originX, originY) })
        }
        return path
    }

    /**
     * Ink width of [text] when every glyph is centred in its own advance cell:
     * `sum(advances) - firstBearing - lastBearing`.
     */
    fun textInkWidth(text: String): Float {
        if (text.isEmpty()) return 0f
        var total = 0f
        for (ch in text) total += glyph(ch).advance
        total -= bearing(glyph(text.first()))
        total -= bearing(glyph(text.last()))
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
