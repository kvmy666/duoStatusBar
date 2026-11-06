package io.github.kvmy666.duostatusbar.hook

/**
 * Pure alpha policy for the HUD/QS/shade hand-off (AGILE 1.7, measured 2026-11-03).
 *
 * The device recording showed a 267–283 ms gap on every QS/shade → collapsed hand-off: the outgoing
 * extra was hidden in the same frame while the main copy waited 50 ms before a 320 ms alpha-in.
 * A panel hand-off keeps the measured ROM reveal for the first attach, but makes the incoming main
 * copy opaque immediately and fades the outgoing surface over a short, bounded time.
 */
internal object PanelHandoff {

    /** Longest the outgoing main copy may stay visible while the panel covers it. */
    const val MAIN_HIDE_MS = 100L

    /** Coexistence fade for the QS/shade copies (the measured gap was 267–283 ms). */
    const val EXTRA_FADE_MS = 120L

    /** ROM status-bar reveal, kept for the initial (non-panel) reveal. */
    const val MAIN_ALPHA_IN_MS = 320L
    const val MAIN_ALPHA_IN_DELAY_MS = 50L

    /** Horizontal slide that carries the main copy from the outgoing panel copy's last x. */
    const val HANDOFF_MS = 120L

    /** Widest accepted hand-off slide, in dp; a larger difference means an unsettled or wrong anchor. */
    const val HANDOFF_MAX_DP = 24f

    /** Below this the copies are already aligned; a slide would only add motion. */
    const val HANDOFF_MIN_PX = 1f

    const val OPAQUE = 0.99f
    const val EPSILON = 0.01f

    internal sealed class Change {
        /** Write [alpha] in this frame. */
        data class Set(val alpha: Float) : Change()

        /** Animate to [alpha]; [startDelayMs] is only used by the ROM reveal. */
        data class Fade(
            val alpha: Float,
            val durationMs: Long,
            val startDelayMs: Long = 0L
        ) : Change()

        /** A fade to the requested alpha is already running; do not restart it. */
        object Keep : Change()
    }

    /**
     * Draw-order decision for one panel swap. The copy being replaced may only leave the screen once
     * the incoming copy is drawable; the first frame is a hold, never a blank.
     */
    internal enum class SwapOrder {
        /** The incoming copy is drawable: the outgoing copy may be hidden in this frame. */
        SWAP,

        /** Not drawable yet: keep the outgoing copy for exactly one frame. */
        HOLD_ONE_FRAME,

        /** The guard expired without a drawable copy: keep the outgoing copy (fail silent). */
        KEEP_OUTGOING
    }

    /**
     * @param incomingDrawable true when the incoming copy is attached, laid out, ready and visible.
     * @param waitedFrames 0 for the same-frame decision, 1 once the one-frame guard has run.
     */
    fun swapOrder(incomingDrawable: Boolean, waitedFrames: Int): SwapOrder = when {
        incomingDrawable -> SwapOrder.SWAP
        waitedFrames < 1 -> SwapOrder.HOLD_ONE_FRAME
        else -> SwapOrder.KEEP_OUTGOING
    }

    /** True when [alpha] is opaque enough that hiding the main copy cannot show a dim frame. */
    fun isOpaque(alpha: Float): Boolean = alpha >= OPAQUE

    /**
     * Draw-order decision for the panel cover (HUD -> QS/shade). The incoming copy must be fully
     * opaque, not merely attached: the owner capture showed one 60 % frame (f86) when the main copy
     * was hidden while the incoming copy was still fading in. A copy that never becomes opaque keeps
     * the main copy (fail silent), after the same one-frame hold.
     */
    fun coverOrder(incomingDrawable: Boolean, incomingAlpha: Float, waitedFrames: Int): SwapOrder =
        swapOrder(incomingDrawable && isOpaque(incomingAlpha), waitedFrames)

    /**
     * Draw-order latch for the QS/shade -> HUD collapse. Once the main copy is ready and the hand-off
     * starts, the outgoing copy is locked out immediately: it may not fade, animate or redraw back in
     * until the next panel expansion. The latch is per surface because the keyguard bar is also an
     * extra and must stay visible while the panel is collapsed.
     */
    internal class OutgoingLatch {
        private var locked: String? = null

        /**
         * Records the collapse order.
         *
         * @param outgoing the surface the panel was showing, or null when there was none.
         * @param mainReady true when the main copy can draw (attached, laid out, element ready).
         * @return true when the outgoing copy must be locked out now.
         */
        fun onCollapse(outgoing: String?, mainReady: Boolean): Boolean {
            locked = if (outgoing != null && mainReady) outgoing else null
            return locked != null
        }

        /** The next panel expansion owns the seat again; the old outgoing copy may draw. */
        fun onExpand() {
            locked = null
        }

        fun isLocked(name: String?): Boolean = name != null && name == locked

        /** Target alpha for an extra: a locked outgoing copy is always hard-zero. */
        fun target(name: String, requested: Float): Float = if (isLocked(name)) 0f else requested
    }

    /**
     * Main-bar copy policy.
     *
     * @param visible requested visibility.
     * @param immediateReveal true for the panel-collapse hand-off: no 50 ms delay and no 320 ms
     *   alpha-in, so the main copy is opaque in the same frame the owner clears.
     * @param current current alpha of the view.
     */
    fun main(visible: Boolean, immediateReveal: Boolean, current: Float): Change = when {
        !visible ->
            if (current <= EPSILON) Change.Set(0f) else Change.Fade(0f, MAIN_HIDE_MS)
        immediateReveal -> Change.Set(1f)
        current >= OPAQUE -> Change.Set(1f)
        else -> Change.Fade(1f, MAIN_ALPHA_IN_MS, MAIN_ALPHA_IN_DELAY_MS)
    }

    /** The x hand-off decision: either a bounded slide or a reason to leave the main copy alone. */
    internal sealed class Handoff {
        data class Slide(val deltaPx: Float, val durationMs: Long) : Handoff()

        /** [reason] is the one line the host logs; the main copy must not move. */
        data class Skip(val reason: String) : Handoff()
    }

    /**
     * Decides the horizontal hand-off from the outgoing QS/shade copy to the main copy.
     *
     * [extraCenterX] and [mainCenterX] are screen-space centres captured in the same frame the panel
     * reports collapsed. [baseStable] is false when the main view is not laid out, is already
     * animating, or its resting translation cannot be trusted; [maxPx] bounds the accepted slide.
     * Anything outside that window is skipped deliberately: a wrong delta must never move the main
     * copy, so the hand-off is either a small, known slide or nothing at all.
     */
    fun horizontalHandoff(
        extraCenterX: Float?,
        mainCenterX: Float?,
        baseStable: Boolean,
        maxPx: Float
    ): Handoff = when {
        extraCenterX == null || mainCenterX == null -> Handoff.Skip("no-centre")
        !extraCenterX.isFinite() || !mainCenterX.isFinite() -> Handoff.Skip("not-finite")
        !baseStable -> Handoff.Skip("base-unstable")
        !maxPx.isFinite() || maxPx <= 0f -> Handoff.Skip("bad-range")
        else -> {
            val delta = extraCenterX - mainCenterX
            when {
                kotlin.math.abs(delta) < HANDOFF_MIN_PX -> Handoff.Skip("already-aligned")
                kotlin.math.abs(delta) > maxPx -> Handoff.Skip("too-far")
                else -> Handoff.Slide(delta, HANDOFF_MS)
            }
        }
    }

    /**
     * QS/shade extra policy. A disabled surface is always hard-zero: the stock header is restored in
     * the same frame, never faded over. When another copy is visible the swap fades; otherwise the
     * target is applied directly (there is nothing to cross-fade with).
     *
     * @param requested target of a fade already running for this slot, if any.
     * @param coexist true when the main copy or the other header copy is currently visible.
     */
    fun extra(
        target: Float,
        enabled: Boolean,
        current: Float,
        requested: Float? = null,
        coexist: Boolean
    ): Change = when {
        !enabled -> Change.Set(0f)
        requested != null && kotlin.math.abs(requested - target) <= EPSILON -> Change.Keep
        kotlin.math.abs(current - target) <= EPSILON -> Change.Set(target)
        !coexist -> Change.Set(target)
        else -> Change.Fade(target, EXTRA_FADE_MS)
    }
}
