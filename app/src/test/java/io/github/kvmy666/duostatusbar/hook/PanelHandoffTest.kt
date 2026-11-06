package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.kvmy666.duostatusbar.hook.PanelHandoff.Change
import io.github.kvmy666.duostatusbar.hook.PanelHandoff.Handoff
import io.github.kvmy666.duostatusbar.hook.PanelHandoff.SwapOrder

class PanelHandoffTest {

    @Test
    fun `main hide is a bounded fade, never an instant blackout`() {
        val change = PanelHandoff.main(visible = false, immediateReveal = false, current = 1f)
        assertEquals(Change.Fade(0f, PanelHandoff.MAIN_HIDE_MS), change)
        assertTrue("hide must stay within the measured 100 ms budget",
            (change as Change.Fade).durationMs in 1..100)
    }

    @Test
    fun `main collapse reveal is opaque in the same frame`() {
        assertEquals(
            Change.Set(1f),
            PanelHandoff.main(visible = true, immediateReveal = true, current = 0f)
        )
        assertEquals(
            Change.Set(1f),
            PanelHandoff.main(visible = true, immediateReveal = true, current = 0.4f)
        )
    }

    @Test
    fun `initial reveal keeps the ROM 320 ms motion`() {
        assertEquals(
            Change.Fade(1f, PanelHandoff.MAIN_ALPHA_IN_MS, PanelHandoff.MAIN_ALPHA_IN_DELAY_MS),
            PanelHandoff.main(visible = true, immediateReveal = false, current = 0f)
        )
    }

    @Test
    fun `an already opaque main copy is not animated again`() {
        assertEquals(
            Change.Set(1f),
            PanelHandoff.main(visible = true, immediateReveal = false, current = 1f)
        )
    }

    @Test
    fun `a hidden main copy is not faded from zero`() {
        assertEquals(
            Change.Set(0f),
            PanelHandoff.main(visible = false, immediateReveal = false, current = 0f)
        )
    }

    @Test
    fun `disabled surface is hard-zero even while a fade is running`() {
        assertEquals(
            Change.Set(0f),
            PanelHandoff.extra(
                target = 1f, enabled = false, current = 1f, requested = 1f, coexist = true
            )
        )
    }

    @Test
    fun `outgoing extra fades over the coexistence window`() {
        val change = PanelHandoff.extra(
            target = 0f, enabled = true, current = 1f, requested = null, coexist = true
        )
        assertEquals(Change.Fade(0f, PanelHandoff.EXTRA_FADE_MS), change)
        assertTrue("extra fade must stay inside 100–140 ms",
            (change as Change.Fade).durationMs in 100..140)
    }

    @Test
    fun `incoming extra fades in while the main copy is still visible`() {
        assertEquals(
            Change.Fade(1f, PanelHandoff.EXTRA_FADE_MS),
            PanelHandoff.extra(
                target = 1f, enabled = true, current = 0f, requested = null, coexist = true
            )
        )
    }

    @Test
    fun `extra with nothing to cross-fade is applied directly`() {
        assertEquals(
            Change.Set(1f),
            PanelHandoff.extra(
                target = 1f, enabled = true, current = 0f, requested = null, coexist = false
            )
        )
        assertEquals(
            Change.Set(0f),
            PanelHandoff.extra(
                target = 0f, enabled = true, current = 1f, requested = null, coexist = false
            )
        )
    }

    @Test
    fun `extra already at target is a direct set`() {
        assertEquals(
            Change.Set(1f),
            PanelHandoff.extra(
                target = 1f, enabled = true, current = 1f, requested = null, coexist = true
            )
        )
    }

    @Test
    fun `a fade to the same target is not restarted`() {
        assertEquals(
            Change.Keep,
            PanelHandoff.extra(
                target = 1f, enabled = true, current = 0.3f, requested = 1f, coexist = true
            )
        )
    }

    @Test
    fun `a changed target restarts the fade`() {
        assertEquals(
            Change.Fade(0f, PanelHandoff.EXTRA_FADE_MS),
            PanelHandoff.extra(
                target = 0f, enabled = true, current = 0.7f, requested = 1f, coexist = true
            )
        )
    }

    @Test
    fun `a drawable incoming copy may replace the outgoing copy in the same frame`() {
        assertEquals(SwapOrder.SWAP, PanelHandoff.swapOrder(incomingDrawable = true, waitedFrames = 0))
        assertEquals(SwapOrder.SWAP, PanelHandoff.swapOrder(incomingDrawable = true, waitedFrames = 1))
    }

    @Test
    fun `an undrawn incoming copy holds the outgoing copy for exactly one frame`() {
        assertEquals(
            SwapOrder.HOLD_ONE_FRAME,
            PanelHandoff.swapOrder(incomingDrawable = false, waitedFrames = 0)
        )
    }

    @Test
    fun `an undrawn incoming copy keeps the outgoing copy after the guard`() {
        assertEquals(
            SwapOrder.KEEP_OUTGOING,
            PanelHandoff.swapOrder(incomingDrawable = false, waitedFrames = 1)
        )
    }

    @Test
    fun `the draw-order hold budget never exceeds one frame`() {
        assertEquals(
            SwapOrder.HOLD_ONE_FRAME,
            PanelHandoff.swapOrder(incomingDrawable = false, waitedFrames = 0)
        )
        assertEquals(
            SwapOrder.KEEP_OUTGOING,
            PanelHandoff.swapOrder(incomingDrawable = false, waitedFrames = 1)
        )
        assertEquals(
            SwapOrder.SWAP,
            PanelHandoff.swapOrder(incomingDrawable = true, waitedFrames = 1)
        )
    }

    @Test
    fun `a sixty percent incoming copy holds the main copy`() {
        assertEquals(
            SwapOrder.HOLD_ONE_FRAME,
            PanelHandoff.coverOrder(incomingDrawable = true, incomingAlpha = 0.6f, waitedFrames = 0)
        )
        assertEquals(
            SwapOrder.KEEP_OUTGOING,
            PanelHandoff.coverOrder(incomingDrawable = true, incomingAlpha = 0.6f, waitedFrames = 1)
        )
    }

    @Test
    fun `an opaque incoming copy covers the main copy in the same frame`() {
        assertEquals(
            SwapOrder.SWAP,
            PanelHandoff.coverOrder(incomingDrawable = true, incomingAlpha = 1f, waitedFrames = 0)
        )
        assertEquals(
            SwapOrder.SWAP,
            PanelHandoff.coverOrder(
                incomingDrawable = true, incomingAlpha = PanelHandoff.OPAQUE, waitedFrames = 0
            )
        )
    }

    @Test
    fun `an undrawn incoming copy never covers the main copy`() {
        assertEquals(
            SwapOrder.KEEP_OUTGOING,
            PanelHandoff.coverOrder(incomingDrawable = false, incomingAlpha = 1f, waitedFrames = 1)
        )
    }

    @Test
    fun `collapse locks only the outgoing surface when the main copy can draw`() {
        val latch = PanelHandoff.OutgoingLatch()
        assertTrue(latch.onCollapse("qs header", mainReady = true))
        assertTrue(latch.isLocked("qs header"))
        assertFalse(latch.isLocked("shade header"))
        assertFalse(latch.isLocked(null))
        assertEquals(0f, latch.target("qs header", 1f))
        assertEquals(1f, latch.target("shade header", 1f))
    }

    @Test
    fun `collapse without a drawable main copy keeps the outgoing surface`() {
        val latch = PanelHandoff.OutgoingLatch()
        assertFalse(latch.onCollapse("shade header", mainReady = false))
        assertFalse(latch.isLocked("shade header"))
        assertEquals(1f, latch.target("shade header", 1f))
    }

    @Test
    fun `the next expansion releases the outgoing latch`() {
        val latch = PanelHandoff.OutgoingLatch()
        latch.onCollapse("qs header", mainReady = true)
        latch.onExpand()
        assertFalse(latch.isLocked("qs header"))
        assertEquals(1f, latch.target("qs header", 1f))
    }

    @Test
    fun `measured QS hand-off slides the main copy left over the bounded window`() {
        val decision = PanelHandoff.horizontalHandoff(
            extraCenterX = 1090.5f,
            mainCenterX = 1126.5f,
            baseStable = true,
            maxPx = 72f
        )
        assertEquals(Handoff.Slide(-36f, PanelHandoff.HANDOFF_MS), decision)
        assertTrue("hand-off must stay inside 100–140 ms",
            (decision as Handoff.Slide).durationMs in 100..140)
    }

    @Test
    fun `measured shade hand-off slides the main copy right over the bounded window`() {
        assertEquals(
            Handoff.Slide(38f, PanelHandoff.HANDOFF_MS),
            PanelHandoff.horizontalHandoff(
                extraCenterX = 1164.5f,
                mainCenterX = 1126.5f,
                baseStable = true,
                maxPx = 72f
            )
        )
    }

    @Test
    fun `a difference beyond the safe range is not animated`() {
        assertEquals(
            Handoff.Skip("too-far"),
            PanelHandoff.horizontalHandoff(
                extraCenterX = 1400f,
                mainCenterX = 1126.5f,
                baseStable = true,
                maxPx = 72f
            )
        )
    }

    @Test
    fun `an unstable base translation is never moved`() {
        assertEquals(
            Handoff.Skip("base-unstable"),
            PanelHandoff.horizontalHandoff(
                extraCenterX = 1090.5f,
                mainCenterX = 1126.5f,
                baseStable = false,
                maxPx = 72f
            )
        )
    }

    @Test
    fun `an already aligned copy is not animated`() {
        assertEquals(
            Handoff.Skip("already-aligned"),
            PanelHandoff.horizontalHandoff(
                extraCenterX = 1126.0f,
                mainCenterX = 1126.5f,
                baseStable = true,
                maxPx = 72f
            )
        )
    }

    @Test
    fun `a missing centre or a non-finite measurement is skipped`() {
        assertEquals(
            Handoff.Skip("no-centre"),
            PanelHandoff.horizontalHandoff(
                extraCenterX = null, mainCenterX = 1126.5f, baseStable = true, maxPx = 72f
            )
        )
        assertEquals(
            Handoff.Skip("not-finite"),
            PanelHandoff.horizontalHandoff(
                extraCenterX = Float.NaN, mainCenterX = 1126.5f, baseStable = true, maxPx = 72f
            )
        )
    }

    @Test
    fun `an invalid safe range is skipped instead of guessed`() {
        assertEquals(
            Handoff.Skip("bad-range"),
            PanelHandoff.horizontalHandoff(
                extraCenterX = 1090.5f, mainCenterX = 1126.5f, baseStable = true, maxPx = 0f
            )
        )
    }
}
