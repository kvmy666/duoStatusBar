package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PART C: the stock-icon hide pass must stand down while the element views are off the display
 * (screen off/AOD), and showing them again is the real change that re-arms it. A layout pass in
 * between must never be counted as a suppress/restore thrash event.
 */
class HidePassPolicyTest {

    @Test
    fun `hidden elements never run a hide pass`() {
        assertFalse(HidePassPolicy.allowsHidePass(elementsHidden = true, thrashTripped = false))
        assertFalse(HidePassPolicy.allowsHidePass(elementsHidden = true, thrashTripped = true))
    }

    @Test
    fun `hidden elements never restore stock`() {
        assertFalse(HidePassPolicy.allowsStockRestore(elementsHidden = true))
    }

    @Test
    fun `a tripped guard blocks the pass`() {
        assertFalse(HidePassPolicy.allowsHidePass(elementsHidden = false, thrashTripped = true))
    }

    @Test
    fun `showing after hidden resets the thrash`() {
        assertTrue(HidePassPolicy.resetOnShow(wasHidden = true, nowVisible = true))
        assertFalse(HidePassPolicy.resetOnShow(wasHidden = false, nowVisible = true))
        assertFalse(HidePassPolicy.resetOnShow(wasHidden = true, nowVisible = false))
    }

    @Test
    fun `visible and healthy allows the pass`() {
        assertTrue(HidePassPolicy.allowsHidePass(elementsHidden = false, thrashTripped = false))
        assertTrue(HidePassPolicy.allowsStockRestore(elementsHidden = false))
    }
}
