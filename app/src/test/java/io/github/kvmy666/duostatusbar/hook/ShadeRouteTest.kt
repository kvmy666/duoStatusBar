package io.github.kvmy666.duostatusbar.hook

import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R10: vivo leaves `combined_qs_header` in the tree while it is INVISIBLE/GONE, so a present-but-dead
 * header must route the attach to the shade window. The decision is pure, so it is pinned here.
 */
class ShadeRouteTest {

    @Test
    fun `a gone header routes to the window`() {
        assertTrue(ShadeRoute.shouldTryWindow(attached = true, visibility = View.GONE, shown = true))
    }

    @Test
    fun `an invisible header routes to the window`() {
        assertTrue(ShadeRoute.shouldTryWindow(attached = true, visibility = View.INVISIBLE, shown = true))
    }

    @Test
    fun `a detached header routes to the window`() {
        assertTrue(ShadeRoute.shouldTryWindow(attached = false, visibility = View.VISIBLE, shown = true))
    }

    @Test
    fun `a visible attached shown header keeps the header route`() {
        assertFalse(ShadeRoute.shouldTryWindow(attached = true, visibility = View.VISIBLE, shown = true))
    }
}
