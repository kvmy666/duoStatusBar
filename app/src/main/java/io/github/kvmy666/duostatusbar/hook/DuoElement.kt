package io.github.kvmy666.duostatusbar.hook

import android.view.View

/**
 * Whatever draws the element in the status bar.
 *
 * Two implementations, and the indirection is deliberate:
 *
 *  - [DuoRiveView]  — the real thing (Rive, animated). Native code, so it can fail in ways no guard
 *                     can catch; it is only ever used when the guard allows it.
 *  - [DuoCanvasView] — plain Android Canvas, no native code at all. It is the stage-1 renderer (proves
 *                     insertion + hiding + geometry with zero native risk) and the fallback, so a Rive
 *                     failure leaves a right-looking status bar instead of an empty one (FR-21).
 *
 * The mapping from state to pixels stays in [DuoMapping]; both implementations consume the same
 * [DuoVisual], so they cannot drift apart.
 */
internal interface DuoElement {

    /** The view to insert into the status bar. */
    val ui: View

    /** A short name for diagnostics: `"Rive"` or `"Canvas"`. */
    val rendererName: String

    /** True once something is actually being drawn. */
    val isReady: Boolean

    /** Prepares the drawing. False means "nothing drew - do not touch the stock icons". */
    fun start(): Boolean

    /**
     * Runs [action] once the drawing is actually live, and immediately when it already is.
     *
     * Readiness can arrive late: a Rive state machine only binds its view model after the view has been
     * attached to a window, which is after [start] returns. The host hides the stock icons and fires the
     * first reveal from here, never from the synchronous return, so a late binding cannot leave a blank
     * status bar (FR-21).
     */
    fun onReady(action: () -> Unit)

    /** Runs [action] when the element can never become ready, so the host can fall back. */
    fun onFailed(action: () -> Unit)

    fun render(v: DuoVisual)

    /**
     * Whether the drawing should be running at all.
     *
     * The Rive state machine's idle animations loop forever, so left alone the renderer advances and
     * draws at frame rate for the life of the process — including while the element is off screen. That
     * is the SystemUI battery drain (130 mAh vs the ~15 mAh baseline), and it has nothing to do with the
     * screen being on. The host turns it off whenever the element is not shown; Canvas ignores it.
     */
    fun setRenderActive(active: Boolean) {
        // No-op by default: a renderer that only draws when asked has nothing to stop.
    }

    /** Re-fires the reveal animation; a no-op where there is no animation to run. */
    /** Fires an arrival of [ms] milliseconds. */
    fun reveal(ms: Int)

    fun teardown()
}
