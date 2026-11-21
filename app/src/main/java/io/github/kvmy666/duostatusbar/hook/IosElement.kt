package io.github.kvmy666.duostatusbar.hook

import android.view.View

/** Which stock occupant an iOS glyph stands in for. */
internal enum class IosPart { BATTERY, WIFI, CELL }

/** The glyph a future Rive/Canvas implementation draws. Kept separate from [IosPart] on purpose. */
internal enum class IosIconKind { BATTERY, WIFI, CELL }

/**
 * Whatever draws an iOS glyph in place of one stock status-bar icon.
 *
 * The stock view owns the slot: its position, size, visibility and (for cellular) its SIM id never
 * change here. An implementation only supplies the drawing for the bounds it is given. When it cannot
 * draw, it reports [canDraw] = false and the host leaves the stock icon untouched (fail silent, rule 5).
 *
 * [canDraw] is the capability flag: an implementation that cannot draw reports false, so the host
 * never suppresses a stock icon on the strength of a renderer that has not started.
 */
internal interface IosElement {

    /** The view to draw into; the host places it over the stock icon's bounds. */
    val ui: View

    /** A short name for diagnostics: `"Rive"` or `"Canvas"`. */
    val rendererName: String

    /** True once something is actually being drawn. */
    val isReady: Boolean

    /**
     * Whether this implementation can draw at all in the current build. False means "leave the stock
     * icon alone"; the host must not hide anything when this is false.
     */
    val canDraw: Boolean

    /** Prepares the drawing. False means "nothing drew - do not touch the stock icons". */
    fun start(): Boolean

    /** Writes one immutable snapshot. */
    fun render(v: IosVisual)

    /** Whether the drawing should be running; idle renderers pause (C8). */
    fun setRenderActive(active: Boolean)

    /** Releases everything the implementation holds. Safe to call more than once. */
    fun teardown()
}
