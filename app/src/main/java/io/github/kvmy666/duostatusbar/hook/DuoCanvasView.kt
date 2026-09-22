package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import io.github.kvmy666.duostatusbar.L

/**
 * The element drawn with plain Android Canvas — no Rive, no native code, nothing that can fault.
 *
 * It exists for two reasons:
 *
 *  1. **Stage 1** of the on-device test: inserting into `system_icons` and hiding the stock icons can
 *     be verified with zero native risk, separately from Rive.
 *  2. **Fallback**: if Rive fails to come up, the status bar still shows a right-looking element
 *     instead of a hole (FR-21).
 *
 * It draws what is known exactly — ring with the top gap, the track, the battery colour and the
 * percentage (or the bolt while charging) — and deliberately nothing else. Wi-Fi arcs and the four
 * cellular spheres are Rive-only; their positions were authored in the `.riv`, and inventing them again
 * here would mean shipping a second, unverified geometry. The mapping is shared with the Rive path via
 * [DuoMapping] so the ring and the colour can never disagree between the two.
 *
 * Canvas angles start at 3 o'clock, Rive trim fractions at 12 o'clock, hence [+TRIM_ORIGIN].
 */
internal class DuoCanvasView(context: Context) : View(context), DuoElement {

    private var visual: DuoVisual = DuoMapping.visual(
        level = 100, charging = false, saver = false, showPercent = true,
        wifiLevel = 3, cellLevel = 4, airplane = false
    )

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arcBounds = RectF()

    override val ui: View get() = this
    override val isReady: Boolean get() = true

    override fun start(): Boolean = true

    /** Canvas draws from the first frame, so readiness is never deferred. */
    override fun onReady(action: () -> Unit) = action()

    override fun onFailed(action: () -> Unit) = Unit

    override fun render(v: DuoVisual) {
        try {
            visual = v
            invalidate()
        } catch (t: Throwable) {
            L.w("canvas render: ${t.message}")
        }
    }

    /** Nothing to animate here; the reveal is Rive's to run. */
    override fun reveal(ms: Int) = Unit

    override fun teardown() = Unit

    override fun onDraw(canvas: Canvas) {
        try {
            drawElement(canvas)
        } catch (t: Throwable) {
            // A drawing failure must never repeat: draw nothing rather than throw every frame.
            L.w("canvas onDraw: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun drawElement(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (size <= 0f || height <= 0) return
        val k = size / DESIGN_SIZE
        val stroke = STROKE * k
        val radius = (size - stroke) / 2f
        val cx = width / 2f
        val cy = height / 2f
        ring.strokeWidth = stroke

        // Track: the whole ring, dimmed.
        ring.color = withAlpha(visual.fgColor, TRACK_ALPHA)
        canvas.drawCircle(cx, cy, radius, ring)

        // Progress: left arc 0-50 %, right arc 50-100 %, exactly as the .riv splits it. The trim ends
        // are arc *lengths* now, so they are the sweeps directly; the right one is 0 when the gap is
        // closed and the left half covers the whole ring on its own.
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        ring.color = visual.tint
        val leftSweep = visual.trimLeftEnd * 360f
        if (leftSweep > MIN_SWEEP) {
            canvas.drawArc(arcBounds, TRIM_ORIGIN + 360f * DuoMapping.LEFT_START, leftSweep, false, ring)
        }
        val rightSweep = visual.trimRightEnd * 360f
        if (rightSweep > MIN_SWEEP) {
            canvas.drawArc(arcBounds, TRIM_ORIGIN + 360f * DuoMapping.RIGHT_START, rightSweep, false, ring)
        }

        // FR-06: the DND crescent takes the middle slot (0, 17 design units below the ring centre).
        if (visual.middleMode == DuoMapping.MIDDLE_DND) {
            moonPaint.color = withAlpha(visual.fgColor, 1f)
            canvas.save()
            canvas.translate(cx, cy + DND_SLOT_Y * k)
            canvas.scale(k, k)
            canvas.drawPath(moonPath, moonPaint)
            canvas.restore()
        }

        // FR-06: with Wi-Fi off the slot shows the cellular generation. Same face and weight as the
        // Rive label; the ring's percentage is drawn below and does not overlap it.
        if (visual.middleMode == DuoMapping.MIDDLE_NETWORK && visual.networkText.isNotEmpty()) {
            label.color = visual.fgColor
            label.textSize = NETWORK_FONT_SIZE * k
            canvas.drawText(
                visual.networkText,
                cx,
                cy + NETWORK_SLOT_Y * k - (label.descent() + label.ascent()) / 2f,
                label
            )
        }

        if (visual.boltOpacity > 0f) {
            drawBolt(canvas, cx, cy, size)
            return
        }
        val text = visual.percentText.trim()
        if (text.isEmpty() || visual.percentOpacity <= 0f) return
        label.color = visual.fgColor
        label.textSize = visual.percentFontSize * k
        // In the ring's TOP GAP, exactly where the Rive digits sit (design y -39 from the ring centre).
        // Drawing it at the centre put it straight on top of the 4G/5G label - the reported conflict.
        canvas.drawText(
            text,
            cx,
            cy + PERCENT_SLOT_Y * k - (label.descent() + label.ascent()) / 2f,
            label
        )
    }

    /** Lightning bolt, unit coordinates scaled to the ring, shown while charging. */
    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val path = boltPath
        val scale = size * BOLT_SCALE
        boltPaint.color = visual.tint
        canvas.save()
        canvas.translate(cx - scale / 2f, cy - scale / 2f)
        canvas.scale(scale, scale)
        canvas.drawPath(path, boltPaint)
        canvas.restore()
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF) * factor
        return ((alpha.toInt().coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
    }

    private companion object {
        const val TAG = "DuoSB"
        const val DESIGN_SIZE = 103f  // ring diameter in design units: r 51.5
        const val STROKE = 8f
        const val TRIM_ORIGIN = 270f  // Rive fraction 0 == 12 o'clock == canvas 270 degrees
        const val TRACK_ALPHA = 0.22f
        const val MIN_SWEEP = 0.5f
        const val BOLT_SCALE = 0.55f
        val boltPath = Path().apply {
            moveTo(0.58f, 0.02f); lineTo(0.24f, 0.56f); lineTo(0.45f, 0.56f)
            lineTo(0.36f, 0.98f); lineTo(0.76f, 0.40f); lineTo(0.53f, 0.40f)
            close()
        }

        /** Middle slot, design units below the ring centre — the Wi-Fi centre the moon replaces. */
        const val DND_SLOT_Y = 17f

        /** The cellular label's centre, matching the Rive text node (group-relative y 1). */
        const val NETWORK_SLOT_Y = 1f
        const val NETWORK_FONT_SIZE = 30f

        /**
         * The percentage's centre in the ring's top gap. The Rive text node spans y -60..-18 about the
         * ring centre, so its centre is -39; drawing there keeps the digits above the middle-slot icon.
         */
        const val PERCENT_SLOT_Y = -39f

        /**
         * The DND crescent, generated from the device's own `drawable/stat_sys_dnd` by
         * `tools/dnd-moon-to-rive.py --android`. Same geometry as the Rive path, so the fallback and
         * the real element cannot disagree (this project does not ship a second, hand-drawn moon).
         */
        val moonPath = Path().apply {
            moveTo(-1.839f, -16.341f)
            cubicTo(-1.539f, -16.791f, -1.509f, -17.361f, -1.809f, -17.841f)
            cubicTo(-2.109f, -18.291f, -2.649f, -18.531f, -3.189f, -18.441f)
            cubicTo(-11.859f, -16.881f, -18.459f, -9.291f, -18.459f, -0.141f)
            cubicTo(-18.459f, 10.119f, -10.119f, 18.459f, 0.141f, 18.459f)
            cubicTo(9.291f, 18.459f, 16.881f, 11.859f, 18.441f, 3.159f)
            cubicTo(18.531f, 2.649f, 18.291f, 2.079f, 17.841f, 1.809f)
            cubicTo(17.361f, 1.509f, 16.791f, 1.509f, 16.341f, 1.839f)
            cubicTo(14.211f, 3.369f, 11.601f, 4.239f, 8.751f, 4.239f)
            cubicTo(1.551f, 4.239f, -4.269f, -1.581f, -4.269f, -8.781f)
            cubicTo(-4.269f, -11.601f, -3.369f, -14.181f, -1.869f, -16.341f)
            cubicTo(-1.869f, -16.341f, -1.839f, -16.341f, -1.839f, -16.341f)
            close()
        }
    }
}
