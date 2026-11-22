package io.github.kvmy666.duostatusbar.hook

import android.graphics.Color
import android.graphics.Path

/**
 * The R3B percentage glyphs, measured from the owner references (refs 1/2/4) as our own polyline
 * drawings. There is no font file: every digit is a set of round-capped strokes inside a cell of
 * [CAP_HEIGHT] height, and [ADVANCE] is the horizontal cell pitch.
 *
 * The source is `.pm/ref/out-r3/digits-final.json` (cap 57, stroke 10.5, advance 52, widths
 * 43/24/41/39.5/44/40/43/38/43/43, group gap 5, inline bolt 40 x 59, large bolt 61 x 96, outline
 * 4.5 in the reference, clamped to 3.0 for the frozen artboard), generated into the constants below.
 * [IosGlyphRenderer] is the only consumer at runtime; the geometry is deliberately independent of
 * the clock/ring drawing so a change there cannot move the percentage.
 */
internal object IosDigits {

    const val CAP_HEIGHT = 57f
    const val STROKE = 10.5f
    const val ADVANCE = 52f

    const val INLINE_BOLT_W = 40f
    const val INLINE_BOLT_H = 59f
    const val INLINE_BOLT_GAP = 5f

    const val LARGE_BOLT_W = 61f
    const val LARGE_BOLT_H = 96f

    /** Kept in sync with [IosGeometry.Battery.BOLT_OUTLINE]: 3.0 fits the frozen 102-unit artboard. */
    const val LARGE_BOLT_OUTLINE = 3.0f

    /** One digit: its cell [width] and one or more open polylines (x, y pairs) inside 0..width. */
    data class Glyph(val width: Float, val strokes: List<FloatArray>)

    private val GLYPHS: Map<Char, Glyph> = mapOf(
        '0' to Glyph(
            43f,
            listOf(
                floatArrayOf(
                    37.75f, 28.5f, 37.43f, 33.12f, 36.47f, 37.55f, 34.91f, 41.62f, 33.15f, 44.7f,
                    30.67f, 47.69f, 29.22f, 48.96f, 27.83f, 49.92f, 26.08f, 50.81f, 24.73f, 51.29f,
                    23.35f, 51.6f, 21.5f, 51.75f, 19.65f, 51.6f, 18.27f, 51.29f, 15.6f, 50.17f,
                    13.92f, 49.06f, 12.71f, 48.06f, 10.17f, 45.17f, 8.09f, 41.62f, 6.53f, 37.55f,
                    5.57f, 33.12f, 5.25f, 28.5f, 5.57f, 23.88f, 6.53f, 19.45f, 8.09f, 15.38f,
                    10.17f, 11.83f, 12.33f, 9.31f, 13.78f, 8.04f, 15.17f, 7.08f, 16.7f, 6.29f,
                    18.27f, 5.71f, 20.11f, 5.34f, 21.5f, 5.25f, 23.35f, 5.4f, 24.73f, 5.71f,
                    27.4f, 6.83f, 28.67f, 7.64f, 30.29f, 8.94f, 32.83f, 11.83f, 34.91f, 15.38f,
                    36.47f, 19.45f, 37.43f, 23.88f, 37.75f, 28.5f
                )
            )
        ),
        '1' to Glyph(
            24f,
            listOf(
                floatArrayOf(18.75f, 5.25f, 18.75f, 51.75f),
                floatArrayOf(5.25f, 17f, 18.75f, 5.25f)
            )
        ),
        '2' to Glyph(
            41f,
            listOf(
                floatArrayOf(
                    5.25f, 16f, 5.49f, 14.09f, 6.1f, 12.47f, 7.05f, 10.93f, 8.33f, 9.52f,
                    10.03f, 8.18f, 12.02f, 7.06f, 14.1f, 6.24f, 16.32f, 5.66f, 18.66f, 5.33f,
                    21.03f, 5.26f, 23.73f, 5.49f, 26.01f, 5.98f, 28.15f, 6.7f, 30.37f, 7.81f,
                    32.28f, 9.17f, 33.64f, 10.54f, 34.42f, 11.61f, 35.13f, 12.96f, 35.57f, 14.36f,
                    35.74f, 15.55f, 35.71f, 16.75f, 35.43f, 18.17f, 34.89f, 19.56f, 34.24f, 20.67f,
                    32.43f, 22.69f, 30.3f, 24.23f, 5.25f, 51.75f, 35.75f, 51.75f
                )
            )
        ),
        '3' to Glyph(
            39.5f,
            listOf(
                floatArrayOf(
                    5.25f, 11.75f, 5.41f, 10.75f, 5.96f, 9.65f, 6.78f, 8.72f, 8.03f, 7.77f,
                    9.6f, 6.94f, 11.44f, 6.25f, 15.44f, 5.43f, 19.78f, 5.28f, 23.98f, 5.83f,
                    25.78f, 6.32f, 27.59f, 7.02f, 29.13f, 7.87f, 30.34f, 8.83f, 31.45f, 10.37f,
                    31.75f, 11.88f, 31.55f, 12.88f, 30.95f, 13.97f, 30.7f, 16.08f, 29.9f, 18.31f,
                    28.68f, 20.51f, 26.95f, 22.92f, 20.5f, 30.25f, 23.4f, 30.47f, 25.85f, 31.01f,
                    28.44f, 32.01f, 30.58f, 33.27f, 32.36f, 34.82f, 33.79f, 36.7f, 34.6f, 38.5f,
                    34.99f, 40.65f, 34.85f, 42.54f, 34.14f, 44.64f, 33.07f, 46.36f, 31.49f, 48.01f,
                    29.52f, 49.42f, 27.38f, 50.46f, 24.86f, 51.25f, 22.18f, 51.68f, 19.45f, 51.72f,
                    16.75f, 51.38f, 14.02f, 50.62f, 11.84f, 49.62f, 9.93f, 48.36f, 8.16f, 46.64f,
                    6.88f, 44.7f, 6.22f, 42.87f
                )
            )
        ),
        '4' to Glyph(
            44f,
            listOf(
                floatArrayOf(38.75f, 5.25f, 38.75f, 51.75f),
                floatArrayOf(32f, 5.25f, 5.25f, 39f),
                floatArrayOf(5.25f, 39f, 38.75f, 39f)
            )
        ),
        '5' to Glyph(
            40f,
            listOf(
                floatArrayOf(
                    34.75f, 5.25f, 5.25f, 5.25f, 5.25f, 28.5f, 20f, 27.25f, 23.07f, 27.52f,
                    26f, 28.31f, 27.82f, 29.11f, 29.48f, 30.12f, 30.96f, 31.3f, 32.23f, 32.65f,
                    33.26f, 34.13f, 34.03f, 35.71f, 34.53f, 37.37f, 34.74f, 39.07f, 34.61f, 41.2f,
                    34.03f, 43.29f, 33.1f, 45.12f, 31.83f, 46.82f, 30.25f, 48.31f, 28.39f, 49.58f,
                    26.31f, 50.57f, 24.07f, 51.28f, 21.71f, 51.67f, 19.31f, 51.74f, 16.93f, 51.48f,
                    14.47f, 50.86f, 12.18f, 49.89f, 10.26f, 48.7f, 8.59f, 47.26f, 7.23f, 45.62f
                )
            )
        ),
        '6' to Glyph(
            43f,
            listOf(
                floatArrayOf(
                    35.75f, 10.5f, 35.47f, 9.5f, 34.64f, 8.53f, 33.29f, 7.64f, 31.28f, 6.79f,
                    29.03f, 6.15f, 26.46f, 5.67f, 23.41f, 5.35f, 20.5f, 5.25f, 14.79f, 5.63f,
                    12.19f, 6.1f, 9.72f, 6.79f, 7.86f, 7.56f, 6.46f, 8.45f, 5.53f, 9.5f,
                    5.25f, 10.5f
                ),
                floatArrayOf(5.25f, 10.5f, 5.25f, 38f),
                floatArrayOf(
                    37.75f, 38f, 37.43f, 40.73f, 36.56f, 43.17f, 35.17f, 45.43f, 33.15f, 47.58f,
                    30.67f, 49.35f, 27.83f, 50.67f, 24.73f, 51.48f, 21.5f, 51.75f, 18.27f, 51.48f,
                    15.17f, 50.67f, 12.33f, 49.35f, 10.01f, 47.72f, 8.09f, 45.76f, 6.53f, 43.35f,
                    5.57f, 40.73f, 5.25f, 38f, 5.57f, 35.27f, 6.53f, 32.65f, 8.09f, 30.24f,
                    10.17f, 28.14f, 12.33f, 26.65f, 15.17f, 25.33f, 18.27f, 24.52f, 21.5f, 24.25f,
                    24.73f, 24.52f, 27.83f, 25.33f, 30.67f, 26.65f, 32.99f, 28.28f, 34.91f, 30.24f,
                    36.47f, 32.65f, 37.43f, 35.27f, 37.75f, 38f
                )
            )
        ),
        '7' to Glyph(
            38f,
            listOf(floatArrayOf(5.25f, 5.25f, 32.75f, 5.25f, 12f, 51.75f))
        ),
        '8' to Glyph(
            43f,
            listOf(
                floatArrayOf(
                    35.5f, 15.5f, 35.19f, 17.63f, 34.48f, 19.34f, 33.11f, 21.23f, 31.4f, 22.75f,
                    29.33f, 24f, 26.74f, 25f, 23.93f, 25.59f, 21.5f, 25.75f, 19.07f, 25.59f,
                    16.26f, 25f, 13.67f, 24f, 11.6f, 22.75f, 9.89f, 21.23f, 8.52f, 19.34f,
                    7.81f, 17.63f, 7.5f, 15.5f, 7.71f, 13.72f, 8.52f, 11.66f, 9.89f, 9.77f,
                    11.6f, 8.25f, 13.67f, 7f, 16.26f, 6f, 18.83f, 5.44f, 21.5f, 5.25f,
                    23.93f, 5.41f, 26.74f, 6f, 29.33f, 7f, 31.4f, 8.25f, 33.11f, 9.77f,
                    34.48f, 11.66f, 35.19f, 13.37f, 35.5f, 15.5f
                ),
                floatArrayOf(
                    37.75f, 40f, 37.5f, 42.04f, 36.57f, 44.4f, 34.97f, 46.57f, 33.19f, 48.16f,
                    30.59f, 49.74f, 27.76f, 50.84f, 24.88f, 51.49f, 21.5f, 51.75f, 18.12f, 51.49f,
                    15.24f, 50.84f, 12.41f, 49.74f, 9.81f, 48.16f, 7.72f, 46.23f, 6.43f, 44.4f,
                    5.61f, 42.44f, 5.25f, 40f, 5.5f, 37.96f, 6.43f, 35.6f, 8.03f, 33.43f,
                    10.01f, 31.69f, 12.41f, 30.26f, 15.41f, 29.11f, 18.31f, 28.48f, 21.5f, 28.25f,
                    24.88f, 28.51f, 28.11f, 29.27f, 30.59f, 30.26f, 33.19f, 31.84f, 34.97f, 33.43f,
                    36.57f, 35.6f, 37.5f, 37.96f, 37.75f, 40f
                )
            )
        ),
        '9' to Glyph(
            43f,
            listOf(
                floatArrayOf(
                    7.25f, 46.5f, 7.53f, 47.5f, 8.36f, 48.47f, 9.71f, 49.36f, 11.72f, 50.21f,
                    13.97f, 50.85f, 16.54f, 51.33f, 19.59f, 51.65f, 22.5f, 51.75f, 25.41f, 51.65f,
                    28.46f, 51.33f, 31.03f, 50.85f, 33.28f, 50.21f, 35.29f, 49.36f, 36.64f, 48.47f,
                    37.47f, 47.5f, 37.75f, 46.5f
                ),
                floatArrayOf(37.75f, 46.5f, 37.75f, 19f),
                floatArrayOf(
                    5.25f, 19f, 5.57f, 16.27f, 6.53f, 13.65f, 8.09f, 11.24f, 9.85f, 9.42f,
                    12.33f, 7.65f, 15.17f, 6.33f, 18.27f, 5.52f, 21.5f, 5.25f, 24.27f, 5.45f,
                    27.4f, 6.19f, 30.29f, 7.43f, 32.83f, 9.14f, 34.91f, 11.24f, 36.47f, 13.65f,
                    37.43f, 16.27f, 37.75f, 19f, 37.43f, 21.73f, 36.47f, 24.35f, 34.91f, 26.76f,
                    32.83f, 28.86f, 30.29f, 30.57f, 27.61f, 31.74f, 24.73f, 32.48f, 21.5f, 32.75f,
                    18.73f, 32.55f, 15.6f, 31.81f, 12.71f, 30.57f, 10.17f, 28.86f, 8.09f, 26.76f,
                    6.53f, 24.35f, 5.57f, 21.73f, 5.25f, 19f
                )
            )
        )
    )

    /**
     * The bolt normalised to its own 0..1 box (`boltNormalized` in the source JSON). The same points
     * drive the 61 x 96 large bolt and the 40 x 59 inline bolt.
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

    /** The digit for [ch], or `0` when a caller passes something that is not a digit. */
    fun glyph(ch: Char): Glyph = GLYPHS[ch] ?: GLYPHS.getValue('0')

    /** One open [Path] per stroke, translated to ([originX], [originY]). Strokes are never closed. */
    fun buildPaths(glyph: Glyph, originX: Float, originY: Float): List<Path> =
        glyph.strokes.map { points ->
            Path().apply {
                moveTo(originX + points[0], originY + points[1])
                var i = 2
                while (i < points.size) {
                    lineTo(originX + points[i], originY + points[i + 1])
                    i += 2
                }
            }
        }

    /**
     * Ink width of [text] when every glyph is centred in its [ADVANCE] cell: the first cell's ink
     * starts at `(ADVANCE - first)/2` and the last cell's ink ends at
     * `(n - 1) * ADVANCE + (ADVANCE - last)/2 + last`.
     */
    fun textInkWidth(text: String): Float {
        if (text.isEmpty()) return 0f
        val first = glyph(text.first()).width
        val last = glyph(text.last()).width
        val firstStart = (ADVANCE - first) / 2f
        val lastEnd = (text.length - 1) * ADVANCE + (ADVANCE - last) / 2f + last
        return lastEnd - firstStart
    }

    /** [textInkWidth] plus the inline bolt when [withBolt] is set. */
    fun groupWidth(text: String, withBolt: Boolean): Float =
        textInkWidth(text) + if (withBolt) INLINE_BOLT_GAP + INLINE_BOLT_W else 0f

    /**
     * The colour the percentage is drawn in: the status-bar background, so the strokes knock out of
     * both the charged fill and the neutral remainder. A light tint implies a dark bar and vice versa.
     */
    fun backgroundColour(tint: Int): Int {
        val luminance = (Color.red(tint) * 299 + Color.green(tint) * 587 + Color.blue(tint) * 114) / 1000
        return if (luminance > 127) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /**
     * A closed bolt from [BOLT_NORMALIZED], scaled to [w] x [h] and centred at ([cx], [cy]).
     * The normalised point box is the design box: the ink may fall just inside an edge.
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

    /** The large bolt centred on the battery body. */
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
