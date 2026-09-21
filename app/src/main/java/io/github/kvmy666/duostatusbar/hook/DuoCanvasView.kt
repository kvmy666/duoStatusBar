package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.View

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
    private val arcBounds = RectF()

    override val ui: View get() = this
    override val isReady: Boolean get() = true

    override fun start(): Boolean = true

    override fun render(v: DuoVisual) {
        try {
            visual = v
            invalidate()
        } catch (t: Throwable) {
            Log.w(TAG, "canvas render: ${t.message}")
        }
    }

    /** Nothing to animate here; the reveal is Rive's to run. */
    override fun reveal() = Unit

    override fun teardown() = Unit

    override fun onDraw(canvas: Canvas) {
        try {
            drawElement(canvas)
        } catch (t: Throwable) {
            // A drawing failure must never repeat: draw nothing rather than throw every frame.
            Log.w(TAG, "canvas onDraw: ${t.javaClass.simpleName}: ${t.message}")
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

        // Progress: left arc 0-50 %, right arc 50-100 %, exactly as the .riv splits it.
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        ring.color = visual.tint
        val leftSweep = (visual.trimLeftEnd - DuoMapping.LEFT_START) * 360f
        if (leftSweep > MIN_SWEEP) {
            canvas.drawArc(arcBounds, TRIM_ORIGIN + 360f * DuoMapping.LEFT_START, leftSweep, false, ring)
        }
        val rightSweep = (visual.trimRightEnd - DuoMapping.RIGHT_START) * 360f
        if (rightSweep > MIN_SWEEP) {
            canvas.drawArc(arcBounds, TRIM_ORIGIN + 360f * DuoMapping.RIGHT_START, rightSweep, false, ring)
        }

        if (visual.boltOpacity > 0f) {
            drawBolt(canvas, cx, cy, size)
            return
        }
        val text = visual.percentText.trim()
        if (text.isEmpty() || visual.percentOpacity <= 0f) return
        label.color = visual.fgColor
        label.textSize = visual.percentFontSize * k
        canvas.drawText(text, cx, cy - (label.descent() + label.ascent()) / 2f, label)
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
    }
}
