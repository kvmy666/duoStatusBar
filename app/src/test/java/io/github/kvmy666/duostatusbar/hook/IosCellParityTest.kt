package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R4A item 2: the iOS artboard has four dots while the platform can expose five levels. A full stock
 * bar must always be four dots, and a 4-of-5 source (raw 3) must be full on the 4-dot artboard.
 *
 * The Duo path's [SignalMapping.cellDots] table is deliberately pinned unchanged: only the iOS-only
 * [SignalMapping.iosDots] changes.
 */
class IosCellParityTest {

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
    fun `duo cellDots five-bar table is unchanged`() {
        // Duo's owner table still merges the two weakest bars: raw 3 of 5 -> 3 dots (unchanged).
        assertEquals(3, SignalMapping.cellDots(3, 5))
        assertEquals(listOf(1, 1, 2, 3, 4), (0..4).map { SignalMapping.cellDots(it, 5) })
    }
}

