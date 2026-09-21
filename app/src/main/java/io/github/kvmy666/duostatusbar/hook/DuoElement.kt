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

    /** True once something is actually being drawn. */
    val isReady: Boolean

    /** Prepares the drawing. False means "nothing drew - do not touch the stock icons". */
    fun start(): Boolean

    fun render(v: DuoVisual)

    /** Re-fires the reveal animation; a no-op where there is no animation to run. */
    fun reveal()

    fun teardown()
}
