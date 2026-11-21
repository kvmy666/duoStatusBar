package io.github.kvmy666.duostatusbar.hook

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/**
 * A stock status-bar glyph replaced in place by the shared iOS vector renderer.
 *
 * The design source is `rive/ios/scene.rml` + [IosGeometry]; this drawable is the runtime and
 * [IosGlyphRenderer] supplies every shape and colour, so nothing is invented here. The host installs
 * the same instance on the stock leaf's `ImageView` and copies the original intrinsic size, which is
 * what keeps a `wrap_content` slot unchanged (C2).
 *
 * The system tints leaf drawables with `setTint`/`setTintList`; those calls are captured as the
 * dynamic colour instead of filtering our pixels, so the fixed battery state colours survive. A
 * non-zero-alpha call is the system's resolved colour (M17: never treat an intensity int as a tint).
 */
internal abstract class IosGlyphDrawable(
    val kind: IosIconKind,
    initialVisual: IosVisual?,
    private var stockWidth: Int,
    private var stockHeight: Int,
    userTransform: IosUserTransform = IosUserTransform()
) : Drawable() {

    private var visual: IosVisual? = initialVisual
    private var drawableAlpha = 255
    private var capturedTint: Int? = null
    private var visible = true
    private var detachFired = false

    /**
     * The user's per-glyph offset/scale. The identity value draws exactly the default pixels; a real
     * change redraws. It is applied inside the drawable's bounds and never writes the layout or
     * intrinsic size (C2).
     */
    var userTransform: IosUserTransform = userTransform
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    /**
     * Called once when the host's leaf detaches this drawable (the OEM replaced it). The host schedules
     * one bounded re-assert and clears this listener before an intentional teardown/restore.
     *
     * The framework's `Drawable.setCallback` is **final** on Android 33-36, so a replacement cannot be
     * observed by overriding it. The generic paths are [notifyDetached] (called by the host when its
     * own leaf hook sees the replacement) and [setVisible] false (a real view detach); both route
     * through this one listener and fire at most once until the listener is cleared.
     */
    var detachListener: (() -> Unit)? = null
        set(value) {
            field = value
            if (value != null) detachFired = false
        }

    /** Stores a new snapshot; redraws only when it actually changed. */
    fun update(v: IosVisual) {
        if (v == visual) return
        visual = v
        invalidateSelf()
    }

    /**
     * Sets the stock intrinsic size copied from the replaced drawable. Only a real change redraws, and
     * no view or layout is written from here (C2).
     */
    fun setStockSize(w: Int, h: Int) {
        if (w == stockWidth && h == stockHeight) return
        stockWidth = w
        stockHeight = h
        invalidateSelf()
    }

    /** Detaches the re-assert signal without removing the drawable (used before restore/teardown). */
    fun clearDetachListener() {
        detachListener = null
        detachFired = false
    }

    /**
     * Signals that the host's leaf replaced this drawable. Fires [detachListener] at most once; the
     * host clears the listener before an intentional teardown/restore.
     */
    fun notifyDetached() {
        val listener = detachListener ?: return
        if (detachFired) return
        detachFired = true
        listener()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        val wasVisible = this.visible
        this.visible = visible
        if (!visible && wasVisible) notifyDetached()
        return changed
    }

    override fun draw(canvas: Canvas) {
        val v = visual ?: return
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val t = sanitizedTransform()
        if (t.dxPx == 0f && t.scale == 1f) {
            // Identity: exactly the default draw path (no extra save/clip), so the pixels are
            // bit-identical to a drawable without a user transform.
            val saved = canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            try {
                renderInto(canvas, v, b)
            } finally {
                canvas.restoreToCount(saved)
            }
            return
        }
        if (t.scale <= 0f) return

        val saved = canvas.save()
        try {
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            canvas.clipRect(0f, 0f, b.width().toFloat(), b.height().toFloat())
            // Canvas pre-concatenates (M' = M * X; see Matrix.preTranslate/preScale), so a
            // translate(dx) issued BEFORE this pivot scale() is applied in device space and is not
            // multiplied by the scale: dx stays independent of t.scale. Swapping the two calls makes
            // the 0.5x shift 3 px instead of 6 px (regression pinned by IosDrawablesTest).
            canvas.translate(t.dxPx, 0f)
            canvas.scale(t.scale, t.scale, b.width() / 2f, b.height() / 2f)
            renderInto(canvas, v, b)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun renderInto(canvas: Canvas, v: IosVisual, b: Rect) {
        IosGlyphRenderer.draw(
            canvas,
            kind,
            v,
            b.width(),
            b.height(),
            alpha = drawableAlpha / 255f,
            overrideTint = capturedTint
        )
    }

    /** Non-finite values fall back to the identity component; a non-positive scale is kept. */
    private fun sanitizedTransform(): IosUserTransform {
        val t = userTransform
        val dx = if (t.dxPx.isFinite()) t.dxPx else 0f
        val scale = if (t.scale.isFinite()) t.scale else 1f
        return IosUserTransform(dx, scale)
    }

    override fun getIntrinsicWidth(): Int =
        if (stockWidth > 0) stockWidth else fallbackArtboardWidth()

    override fun getIntrinsicHeight(): Int =
        if (stockHeight > 0) stockHeight else fallbackArtboardHeight()

    override fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        if (clamped == drawableAlpha) return
        drawableAlpha = clamped
        invalidateSelf()
    }

    override fun getAlpha(): Int = drawableAlpha

    /**
     * Captures the system's tint. The drawable itself is never filtered: the renderer applies the
     * captured colour to the dynamic parts and keeps the fixed battery state colours.
     */
    override fun setTintList(tint: ColorStateList?) {
        captureTint(tint?.defaultColor ?: Color.TRANSPARENT)
    }

    override fun setTint(tintColor: Int) {
        captureTint(tintColor)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        // The renderer owns the colour; a framework tint mode must not filter the pixels.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Fixed state colours must survive; the captured tint is the only dynamic colour.
    }

    override fun getColorFilter(): ColorFilter? = null

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun captureTint(colour: Int) {
        if (Color.alpha(colour) == 0) return
        if (colour == capturedTint) return
        capturedTint = colour
        invalidateSelf()
    }

    private fun fallbackVisual(): IosVisual = visual ?: IosVisual(part = partOf(kind))

    private fun fallbackArtboardWidth(): Int =
        IosGlyphRenderer.artboardWidth(kind, fallbackVisual()).roundToInt()

    private fun fallbackArtboardHeight(): Int =
        IosGlyphRenderer.artboardHeight(kind, fallbackVisual()).roundToInt()

    private fun partOf(kind: IosIconKind): IosPart = when (kind) {
        IosIconKind.BATTERY -> IosPart.BATTERY
        IosIconKind.WIFI -> IosPart.WIFI
        IosIconKind.CELL -> IosPart.CELL
    }
}

/** The battery glyph as a drawable; see [IosGlyphDrawable]. */
internal class IosBatteryDrawable(
    initialVisual: IosVisual?,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    userTransform: IosUserTransform = IosUserTransform()
) : IosGlyphDrawable(IosIconKind.BATTERY, initialVisual, intrinsicWidth, intrinsicHeight, userTransform)

/** The Wi-Fi glyph as a drawable; see [IosGlyphDrawable]. */
internal class IosWifiDrawable(
    initialVisual: IosVisual?,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    userTransform: IosUserTransform = IosUserTransform()
) : IosGlyphDrawable(IosIconKind.WIFI, initialVisual, intrinsicWidth, intrinsicHeight, userTransform)

/** The cellular glyph as a drawable; see [IosGlyphDrawable]. */
internal class IosCellDrawable(
    initialVisual: IosVisual?,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    userTransform: IosUserTransform = IosUserTransform()
) : IosGlyphDrawable(IosIconKind.CELL, initialVisual, intrinsicWidth, intrinsicHeight, userTransform)

/** Creates the drawable for [kind], from the same renderer the Canvas fallback uses. */
internal object IosDrawables {
    fun create(
        kind: IosIconKind,
        visual: IosVisual?,
        intrinsicW: Int,
        intrinsicH: Int,
        userTransform: IosUserTransform = IosUserTransform()
    ): IosGlyphDrawable = when (kind) {
        IosIconKind.BATTERY -> IosBatteryDrawable(visual, intrinsicW, intrinsicH, userTransform)
        IosIconKind.WIFI -> IosWifiDrawable(visual, intrinsicW, intrinsicH, userTransform)
        IosIconKind.CELL -> IosCellDrawable(visual, intrinsicW, intrinsicH, userTransform)
    }
}
