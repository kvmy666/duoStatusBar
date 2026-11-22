package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** IOS-R2 item 5: the measured strip ids resolve in a fixed, testable priority order. */
class IosSurfaceRoutingTest {

    @Test
    fun `header ids are tried in the measured order`() {
        assertEquals(
            listOf("shade_header_system_icons", "system_icons", "quick_qs_status_icons"),
            IosSurfaceRouting.HEADER_IDS
        )
        assertEquals(
            "shade_header_system_icons",
            IosSurfaceRouting.firstAttachable(IosSurfaceRouting.HEADER_IDS) { true }
        )
    }

    @Test
    fun `window ids prefer the expanded QS header`() {
        assertEquals(
            listOf("quick_qs_status_icons", "shade_header_system_icons", "system_icons"),
            IosSurfaceRouting.WINDOW_IDS
        )
        assertEquals(
            "quick_qs_status_icons",
            IosSurfaceRouting.firstAttachable(IosSurfaceRouting.WINDOW_IDS) { true }
        )
    }

    @Test
    fun `a missing first id falls back to the next attachable one`() {
        val attachable = setOf("system_icons")
        assertEquals(
            "system_icons",
            IosSurfaceRouting.firstAttachable(IosSurfaceRouting.HEADER_IDS) { it in attachable }
        )
        assertNull(IosSurfaceRouting.firstAttachable(IosSurfaceRouting.WINDOW_IDS) { false })
    }

    @Test
    fun `the predicate is evaluated in order and stops at the first hit`() {
        val asked = ArrayList<String>()
        val id = IosSurfaceRouting.firstAttachable(listOf("a", "b", "c")) {
            asked.add(it)
            it == "b"
        }
        assertEquals("b", id)
        assertEquals(listOf("a", "b"), asked)
    }

    @Test
    fun `an invisible copy is not chosen when a visible copy exists`() {
        data class Copy(val name: String, val shown: Boolean, val area: Int)
        val invisible = Copy("header", shown = false, area = 1200)
        val visible = Copy("qs", shown = true, area = 800)
        assertEquals(
            visible,
            IosSurfaceRouting.firstUsable(listOf(invisible, visible), { it.shown }, { it.area })
        )
    }

    @Test
    fun `a zero area strip is rejected`() {
        data class Copy(val name: String, val shown: Boolean, val area: Int)
        val empty = Copy("header", shown = true, area = 0)
        val usable = Copy("qs", shown = true, area = 300)
        assertEquals(
            usable,
            IosSurfaceRouting.firstUsable(listOf(empty, usable), { it.shown }, { it.area })
        )
        assertNull(
            IosSurfaceRouting.firstUsable(listOf(empty), { it.shown }, { it.area })
        )
    }
}
