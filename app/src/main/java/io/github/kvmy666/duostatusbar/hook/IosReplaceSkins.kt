package io.github.kvmy666.duostatusbar.hook

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.L

/**
 * Replaces one stock `ImageView` glyph with [drawable] and keeps it there across OEM pushes.
 *
 * `android.graphics.drawable.Drawable.setCallback` is **final** on Android 33-36, so a replacement
 * cannot be observed by overriding it. The replacement signal is one per-leaf
 * [ViewTreeObserver.OnPreDrawListener]: it runs only while the view tree draws and writes only when the
 * leaf holds a foreign non-null drawable, then our drawable is back before the frame is drawn (no
 * flash, no polling). After our write the next pre-draw sees ours and returns false, so the swap itself
 * cannot loop. [onForeignPush] is the fast path an OEM push hook calls in the same `setImageDrawable`;
 * a successful replacement is progress and never counts. Only a write that does not stick (the leaf
 * still holds a foreign drawable after our `setImageDrawable`) calls [onReassert], so the host's
 * `ThrashGuard` backs off a true no-progress fight rather than every legitimate level update.
 *
 * Nothing here writes layout params, visibility or size (C2): the stock slot and its `wrap_content`
 * measure stay exactly as the ROM laid them out.
 */
internal class IosDrawableSwap(
    private val leaf: ImageView,
    private val drawable: IosGlyphDrawable,
    private val onReassert: () -> Unit = {},
    private val restoreDrawable: Drawable? = leaf.drawable
) : ViewTreeObserver.OnPreDrawListener {

    private val logOnce = LogOnce()
    private var listenerAdded = false
    private var listenerObserver: ViewTreeObserver? = null
    private var inReassert = false
    private var restored = false

    /** The original captured atomically at [apply] (a stale constructor snapshot is only a fallback). */
    private var captured: Drawable? = restoreDrawable

    /**
     * Pins the stock intrinsic size, installs [drawable] on the leaf and arms the pre-draw listener.
     *
     * Returns false when the leaf has no usable size yet (the host retries after layout) or when the
     * install did not take. A failure leaves the leaf as it was.
     */
    fun apply(): Boolean {
        if (restored) return false
        return try {
            if (leaf.drawable === drawable) return true
            captured = leaf.drawable ?: restoreDrawable
            val width = captured?.intrinsicWidth?.takeIf { it > 0 } ?: leaf.width
            val height = captured?.intrinsicHeight?.takeIf { it > 0 } ?: leaf.height
            if (width <= 0 || height <= 0) return false

            drawable.setStockSize(width, height)
            leaf.setImageDrawable(drawable)
            if (leaf.drawable !== drawable) {
                removeListener()
                return false
            }
            addListener()
            true
        } catch (t: Throwable) {
            fail("ios-swap-apply", t)
            // A half-applied install must not leave our drawable without a listener.
            try {
                if (leaf.drawable === drawable) leaf.setImageDrawable(captured)
            } catch (_: Throwable) {
            }
            removeListener()
            false
        }
    }

    /** Updates the snapshot; no view or layout write. */
    fun update(v: IosVisual) {
        drawable.update(v)
    }

    /**
     * Puts our drawable back after a foreign OEM push. This is the same write [onForeignPush] performs,
     * but a write that does not stick is counted as no progress. Returns true only when our drawable is
     * installed afterwards.
     */
    fun reassert(): Boolean = installForeign(countFailure = true)

    /**
     * The OEM push fast path: installs our drawable when the leaf holds a foreign non-null drawable.
     *
     * This runs inside the OEM's own `setImageDrawable` after-hook, so the replacement lands in the same
     * call and the frame never shows the stock glyph. A successful push is progress, not a fight: it
     * never calls [onReassert]. Returns true only when our drawable is installed afterwards.
     */
    fun onForeignPush(): Boolean = installForeign(countFailure = false)

    private fun installForeign(countFailure: Boolean): Boolean {
        if (restored || inReassert) return false
        ensureListener()
        return try {
            // Respect the OEM's own hide: never draw into a GONE/INVISIBLE or detached leaf.
            if (!leaf.isShown || leaf.visibility != View.VISIBLE) return false
            val current = leaf.drawable ?: return false
            // A null drawable is the OEM hiding/clearing the icon; never resurrect it.
            if (current === drawable) return false

            inReassert = true
            try {
                leaf.setImageDrawable(drawable)
                val stuck = leaf.drawable === drawable
                if (!stuck && countFailure) {
                    try {
                        onReassert()
                    } catch (_: Throwable) {
                    }
                }
                stuck
            } finally {
                inReassert = false
            }
        } catch (t: Throwable) {
            fail("ios-swap-reassert", t)
            false
        }
    }

    /**
     * Removes the listener and puts the stock drawable back, but only while the leaf still holds ours:
     * a drawable the OEM pushed meanwhile is stock and must never be overwritten. Idempotent.
     */
    fun restore() {
        if (restored) return
        restored = true
        removeListener()
        try {
            drawable.clearDetachListener()
        } catch (_: Throwable) {
        }
        try {
            if (leaf.drawable === drawable) {
                leaf.setImageDrawable(captured)
            }
        } catch (t: Throwable) {
            // A failed restore leaves the leaf as it is: blanking it would hide a stock icon (rule 5).
            fail("ios-swap-restore", t)
        }
    }

    override fun onPreDraw(): Boolean {
        reassert()
        // Never cancel the draw; the frame must always go through.
        return true
    }

    /**
     * Re-arms the per-draw guard on the observer that currently belongs to the leaf.
     *
     * A detach/reattach (shade expand/collapse, rotation) replaces the `ViewTreeObserver`, silently
     * dropping the listener while [listenerAdded] still says it is there. Comparing the observer
     * identity re-adds it exactly once to the live observer and never duplicates it on the same one.
     */
    fun ensureListener() {
        if (restored) return
        try {
            val observer = leaf.viewTreeObserver
            if (listenerAdded && observer === listenerObserver && observer.isAlive) return
            removeListener()
            observer.addOnPreDrawListener(this)
            listenerObserver = observer
            listenerAdded = true
        } catch (_: Throwable) {
            listenerAdded = false
            listenerObserver = null
        }
    }

    private fun addListener() {
        ensureListener()
    }

    private fun removeListener() {
        val observer = listenerObserver
        listenerAdded = false
        listenerObserver = null
        if (observer == null) return
        try {
            observer.removeOnPreDrawListener(this)
        } catch (_: Throwable) {
        }
    }

    private fun fail(key: String, t: Throwable) {
        logOnce.once(key) {
            L.w("iOS swap $key: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}

/**
 * Blanks one non-drawing sibling of the replaced leaf (a bolt, in/out arrows, a standard/voWiFi text)
 * without touching layout or the main drawing leaf.
 *
 * An `ImageView` gets an alpha-0 [IosGlyphDrawable] of the same intrinsic size plus the same per-leaf
 * pre-draw guard as [IosDrawableSwap]; any other `View` is made `INVISIBLE` (layout is kept) and
 * re-asserted only if the OEM makes it `VISIBLE` again. A sibling that is already `GONE` is left
 * completely alone, and restore is then a no-op. The caller must never pass the main leaf, a
 * percentage/digit view, `status_bar_dot`, or an **ancestor of the main leaf** (the Samsung
 * `wifi_iconset` contains `wifi_signal`); filtering that is the host's job before construction.
 */
internal class IosSiblingSwap(
    private val view: View,
    private val emptyDrawable: IosGlyphDrawable?,
    private val originalDrawable: Drawable? = (view as? ImageView)?.drawable,
    private val originalVisibility: Int = view.visibility
) : ViewTreeObserver.OnPreDrawListener {

    private val logOnce = LogOnce()
    private var applied = false
    private var listenerAdded = false
    private var listenerObserver: ViewTreeObserver? = null
    private var inReassert = false

    /** The original captured atomically at [apply] (a stale constructor snapshot is only a fallback). */
    private var captured: Drawable? = originalDrawable

    /**
     * Hides the sibling. Returns true when it is already hidden (`GONE`), when the blank drawable was
     * installed, or when a visible non-ImageView was made `INVISIBLE`; false only when an `ImageView`
     * cannot be blanked (no [emptyDrawable] or no measurable intrinsic size yet).
     */
    fun apply(): Boolean {
        if (applied) return true
        if (view.visibility == View.GONE) return true
        return try {
            val image = view as? ImageView
            if (image != null) {
                val empty = emptyDrawable ?: return false
                captured = image.drawable ?: originalDrawable
                val width = captured?.intrinsicWidth?.takeIf { it > 0 } ?: image.width
                val height = captured?.intrinsicHeight?.takeIf { it > 0 } ?: image.height
                if (width <= 0 || height <= 0) return false

                empty.setStockSize(width, height)
                empty.alpha = 0
                image.setImageDrawable(empty)
                if (image.drawable !== empty) return false
                addListener()
                applied = true
                // Item 0d: the OEM's next push re-blanks this sibling in the same call.
                IosPushHooks.watchPush(image) { reassert() }
                true
            } else {
                if (view.visibility != View.VISIBLE) return true
                view.visibility = View.INVISIBLE
                addListener()
                applied = true
                true
            }
        } catch (t: Throwable) {
            fail("ios-sibling-apply", t)
            // A half-applied blank must not leave a stock sibling hidden without a tracked swap.
            try {
                val image = view as? ImageView
                if (image != null) {
                    if (image.drawable === emptyDrawable) image.setImageDrawable(captured)
                } else if (view.visibility == View.INVISIBLE) {
                    view.visibility = originalVisibility
                }
            } catch (_: Throwable) {
            }
            removeListener()
            false
        }
    }

    /** Re-hides the sibling after an OEM push; no-op when it is not shown or already hidden. */
    fun reassert() {
        if (!applied || inReassert) return
        ensureListener()
        try {
            val image = view as? ImageView
            if (image != null) {
                val empty = emptyDrawable ?: return
                if (!image.isShown || image.visibility != View.VISIBLE) return
                val current = image.drawable ?: return
                if (current === empty) return

                inReassert = true
                try {
                    image.setImageDrawable(empty)
                } finally {
                    inReassert = false
                }
            } else {
                if (view.visibility != View.VISIBLE) return
                view.visibility = View.INVISIBLE
            }
        } catch (t: Throwable) {
            fail("ios-sibling-reassert", t)
        }
    }

    /** Restores the exact captured drawable/visibility. Idempotent; a second call does nothing. */
    fun restore() {
        if (!applied) return
        applied = false
        (view as? ImageView)?.let { IosPushHooks.unwatchPush(it) }
        removeListener()
        try {
            emptyDrawable?.clearDetachListener()
        } catch (_: Throwable) {
        }
        try {
            val image = view as? ImageView
            if (image != null) {
                if (image.drawable === emptyDrawable) image.setImageDrawable(captured)
            } else if (view.visibility == View.INVISIBLE) {
                // Only undo our own write. If the OEM set GONE (or VISIBLE) while iOS owned the icon,
                // that is a newer intent and must win over our captured visibility.
                view.visibility = originalVisibility
            }
        } catch (t: Throwable) {
            fail("ios-sibling-restore", t)
        }
    }

    override fun onPreDraw(): Boolean {
        reassert()
        return true
    }

    /**
     * Re-arms the guard on the leaf's current observer after a detach/reattach, exactly once. See
     * [IosDrawableSwap.ensureListener].
     */
    fun ensureListener() {
        try {
            val observer = view.viewTreeObserver
            if (listenerAdded && observer === listenerObserver && observer.isAlive) return
            removeListener()
            observer.addOnPreDrawListener(this)
            listenerObserver = observer
            listenerAdded = true
        } catch (_: Throwable) {
            listenerAdded = false
            listenerObserver = null
        }
    }

    private fun addListener() {
        ensureListener()
    }

    private fun removeListener() {
        val observer = listenerObserver
        listenerAdded = false
        listenerObserver = null
        if (observer == null) return
        try {
            observer.removeOnPreDrawListener(this)
        } catch (_: Throwable) {
        }
    }

    private fun fail(key: String, t: Throwable) {
        logOnce.once(key) {
            L.w("iOS sibling $key: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
