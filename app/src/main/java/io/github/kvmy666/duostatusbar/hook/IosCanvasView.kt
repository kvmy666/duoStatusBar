package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.view.View

/**
 * The iOS glyph drawn with the platform `Canvas` — the only iOS renderer created at runtime.
 *
 * The drawing itself lives in [IosGlyphRenderer], which is shared with [IosDrawables]: this view only
 * supplies the bounds and the snapshot, so the Canvas element and a true in-place Drawable replacement
 * produce identical pixels (`IosDrawableParityTest`). Every shape comes from [IosGeometry], the same
 * constants the design scene repeats, so every occupant is drawn (C9/M13).
 * The stock view owns the slot; this view only fills the bounds it is given and scales the artboard
 * with `min(w/W, h/H)`, centred, exactly like `Fit.CONTAIN` + `Alignment.CENTER`.
 *
 * Dual SIM: one view over both stock slots, stacked block; no per-slot rows. The host suppresses both
 * mobile leaves and draws this view over their union bounds: `BOTH` draws the top bar row and the
 * bottom dot row exactly as the dual block does, while `DATA`/`OTHER` remain supported for a
 * single row (the single-SIM path uses `DATA`).
 */
internal class IosCanvasView(
    context: Context,
    val kind: IosIconKind
) : View(context), IosElement {

    private var visual: IosVisual? = null
    private var started = false
    private var renderActive = true

    /**
     * Inset the glyph to [IosGeometry.Relative.sizeFor] inside this view's bounds. The view's size and
     * layout never change (C2); only the drawing box shrinks and centres. Off by default so previews and
     * direct renderers keep the full-slot draw.
     */
    var relativeSize: Boolean = false

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override val ui: View get() = this

    override val rendererName: String get() = "Canvas"

    override val isReady: Boolean get() = started

    override val canDraw: Boolean get() = true

    override fun start(): Boolean {
        started = true
        invalidate()
        return true
    }

    override fun render(v: IosVisual) {
        if (v == visual) return
        visual = v
        if (renderActive) invalidate()
    }

    override fun setRenderActive(active: Boolean) {
        renderActive = active
        if (active) invalidate()
    }

    override fun teardown() {
        started = false
        visual = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val v = visual ?: return
        if (relativeSize) {
            val (innerW, innerH) = IosGeometry.Relative.sizeFor(
                kind,
                width,
                height,
                dualCell = v.cellMode == IosCellMode.DUAL
            )
            if (innerW > 0 && innerH > 0) {
                val saved = canvas.save()
                try {
                    canvas.translate((width - innerW) / 2f, (height - innerH) / 2f)
                    IosGlyphRenderer.draw(canvas, kind, v, innerW, innerH)
                } finally {
                    canvas.restoreToCount(saved)
                }
                return
            }
        }
        IosGlyphRenderer.draw(canvas, kind, v, width, height)
    }
}
