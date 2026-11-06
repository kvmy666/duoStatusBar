package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.graphics.Color

class NavHoverTest {
    @Test
    fun `hover index at slot centers`() {
        val w = 100f
        assertEquals(0, hoverIndex(w * 0.5f, w, 4))
        assertEquals(1, hoverIndex(w * 1.5f, w, 4))
        assertEquals(2, hoverIndex(w * 2.5f, w, 4))
        assertEquals(3, hoverIndex(w * 3.5f, w, 4))
    }

    @Test
    fun `hover index clamps at edges`() {
        val w = 100f
        assertEquals(0, hoverIndex(-50f, w, 4))
        assertEquals(0, hoverIndex(0f, w, 4))
        assertEquals(3, hoverIndex(w * 4f, w, 4))
        assertEquals(3, hoverIndex(w * 10f, w, 4))
    }

    @Test
    fun `hover index between slots rounds`() {
        val w = 100f
        // between slot 0 and 1 center (x=100) -> 0.5? (100/100-0.5)=0.5 rounds to 1? roundToInt 0.5 -> 0? Kotlin roundToInt uses half-up? Actually 0.5 rounds to 1? Let's just check monotonic clamp boundary at slot edge.
        assertEquals(0, hoverIndex(60f, w, 4))
        assertEquals(1, hoverIndex(140f, w, 4))
    }

    @Test
    fun `hover index recomputes with width`() {
        // Pure expression of the stale-width case: the same centre maps to different tabs
        // when the slot width changes, so the Compose side must key on itemWidthPx.
        assertEquals(0, hoverIndex(40f, 100f, 4))
        assertEquals(1, hoverIndex(40f, 30f, 4))
    }

    @Test
    fun `color policy`() {
        val idle = Color.White
        val blue = Color(0xFF3C88F8)
        // idle selected -> blue
        assertEquals(blue, navTabColor(false, true, false, idle))
        // idle nonselected -> white
        assertEquals(idle, navTabColor(false, false, false, idle))
        // moving hovered -> blue
        assertEquals(blue, navTabColor(true, false, true, idle))
        // moving selected-not-hovered -> white
        assertEquals(idle, navTabColor(false, true, true, idle))
    }
}
