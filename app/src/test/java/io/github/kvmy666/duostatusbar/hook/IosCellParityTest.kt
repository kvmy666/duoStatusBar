package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4A item 2: the iOS artboard has four dots while the platform can expose five levels. A full stock
 * bar must always be four dots, and a 4-of-5 source (raw 3) must be full on the 4-dot artboard.
 *
 * The Duo path's [SignalMapping.cellDots] table is deliberately pinned unchanged: only the iOS-only
 * [SignalMapping.iosDots] changes.
 */
class IosCellParityTest {

    private fun sample(slot: Int, subId: Int, raw: Int, count: Int): CellSample = CellSample(
        slot = slot,
        subscriptionId = subId,
        raw = raw,
        count = count,
        maxIndex = SignalMapping.cellMaxIndex(raw, count),
        rat = "Lte",
        dbm = -95,
        metric = "rsrp",
        dots = SignalMapping.cellDots(raw, count),
        src = SignalMapping.SRC_PER_SUB,
        airplane = false
    )

    @Test
    fun `five-level raws map to one two three four four`() {
        assertEquals(1, SignalMapping.iosDots(0, 5))
        assertEquals(2, SignalMapping.iosDots(1, 5))
        assertEquals(3, SignalMapping.iosDots(2, 5))
        assertEquals(4, SignalMapping.iosDots(3, 5))
        assertEquals(4, SignalMapping.iosDots(4, 5))
    }

    @Test
    fun `four-level raws map to one two three four`() {
        assertEquals(1, SignalMapping.iosDots(0, 4))
        assertEquals(2, SignalMapping.iosDots(1, 4))
        assertEquals(3, SignalMapping.iosDots(2, 4))
        assertEquals(4, SignalMapping.iosDots(3, 4))
    }

    @Test
    fun `the highest raw is four dots for both counted sources`() {
        assertEquals(4, SignalMapping.iosDots(4, 5))
        assertEquals(4, SignalMapping.iosDots(3, 4))
    }

    @Test
    fun `no service and negative raws are zero`() {
        assertEquals(0, SignalMapping.iosDots(0, 5, hasService = false))
        assertEquals(0, SignalMapping.iosDots(4, 5, hasService = false))
        assertEquals(0, SignalMapping.iosDots(-1, 5))
        assertEquals(0, SignalMapping.iosDots(-1, 4))
    }

    @Test
    fun `single plan draws the visible slots own sample level`() {
        // SimSignal carries the old Duo 3 dots; the sample's raw 3 of 5 must draw full on iOS.
        val signals = listOf(SimSignal(slot = 0, subscriptionId = 7, level = 3))
        val plan = IosMapping.cellPlan(
            signals,
            defaultSubId = 7,
            slots = listOf(CellSlot(7, visible = true)),
            samples = listOf(sample(0, 7, raw = 3, count = 5))
        )
        assertTrue(plan is IosCellPlan.Single)
        assertEquals(4f, (plan as IosCellPlan.Single).level, 0.0001f)
    }

    @Test
    fun `dual plan draws each subs own sample level`() {
        val signals = listOf(
            SimSignal(slot = 0, subscriptionId = 11, level = 2),
            SimSignal(slot = 1, subscriptionId = 22, level = 3)
        )
        val plan = IosMapping.cellPlan(
            signals,
            defaultSubId = 22,
            slots = listOf(CellSlot(11, visible = true), CellSlot(22, visible = true)),
            samples = listOf(
                sample(0, 11, raw = 2, count = 5),
                sample(1, 22, raw = 3, count = 5)
            )
        )
        assertTrue(plan is IosCellPlan.Dual)
        plan as IosCellPlan.Dual
        assertEquals("data sub raw 3 of 5 is full", 4f, plan.bars, 0.0001f)
        assertEquals("other sub raw 2 of 5 is three dots", 3f, plan.dots, 0.0001f)
    }

    @Test
    fun `without samples the historical SimSignal level is kept`() {
        val signals = listOf(SimSignal(slot = 0, subscriptionId = 7, level = 2))
        val plan = IosMapping.cellPlan(
            signals,
            defaultSubId = 7,
            slots = listOf(CellSlot(7, visible = true))
        )
        assertEquals(2f, (plan as IosCellPlan.Single).level, 0.0001f)
    }

    @Test
    fun `duo cellDots five-bar table is unchanged`() {
        // Duo's owner table still merges the two weakest bars: raw 3 of 5 -> 3 dots (unchanged).
        assertEquals(3, SignalMapping.cellDots(3, 5))
        assertEquals(listOf(1, 1, 2, 3, 4), (0..4).map { SignalMapping.cellDots(it, 5) })
    }
}
