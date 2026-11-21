package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.view.View

/**
 * The iOS glyph drawn with the platform `Canvas` — the fail-silent sibling of [IosRiveView].
 *
 * The drawing itself lives in [IosGlyphRenderer], which is shared with [IosDrawables]: this view only
 * supplies the bounds and the snapshot, so the Canvas fallback and a true in-place Drawable replacement
 * produce identical pixels (`IosDrawableParityTest`). Every shape comes from [IosGeometry], the same
 * constants the Rive scene repeats, so the fallback draws every occupant the Rive path draws (C9/M13).
 * The stock view owns the slot; this view only fills the bounds it is given and scales the artboard
 * with `min(w/W, h/H)`, centred, exactly like Rive's `Fit.CONTAIN` + `Alignment.CENTER`.
 *
 * Dual SIM: one view over both stock slots, stacked block; no per-slot rows. The host suppresses both
 * mobile leaves and draws this view over their union bounds: `BOTH` draws the top bar row and the
 * bottom dot row exactly as the `IosCell` dual block does, while `DATA`/`OTHER` remain supported for a
 * single row (the single-SIM path uses `DATA`).
 */
internal class IosCanvasView(
    context: Context,
    val kind: IosIconKind
) : View(context), IosElement {

    private var visual: IosVisual? = null
    private var started = false
    private var renderActive = true

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
        IosGlyphRenderer.draw(canvas, kind, v, width, height)
    }
}
