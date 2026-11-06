package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionSeatTest {

    @Test
    fun `normal seat 0 is the physical right edge`() {
        val container = 400
        val view = 40
        val padding = 0
        assertEquals(container - view - padding, normalSeatTranslationX(container, view, padding, 0, false))
    }

    @Test
    fun `negative offset moves left from the right edge`() {
        val container = 400
        val view = 40
        val padding = 0
        val limit = 200
        assertEquals(container - view - limit, normalSeatTranslationX(container, view, padding, -limit, false))
    }

    @Test
    fun `positive offset is not produced by the helper clamp contract`() {
        // The helper itself is a pure formula; clamping to 0 happens at the prefs/UI layer.
        // Here we just document that a positive input shifts right of the edge.
        val container = 400
        val view = 40
        assertEquals(container - view + 5, normalSeatTranslationX(container, view, 0, 5, false))
    }

    @Test
    fun `RTL keeps the same physical right seat`() {
        val container = 400
        val view = 40
        assertEquals(container - view, normalSeatTranslationX(container, view, 0, 0, true))
        assertEquals(container - view - 10, normalSeatTranslationX(container, view, 0, -10, true))
    }
}
