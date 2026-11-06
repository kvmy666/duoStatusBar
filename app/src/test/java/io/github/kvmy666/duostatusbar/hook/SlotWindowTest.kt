package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P0 rotation rule: a cached slot may be reused only inside the window that owns it. A view left in
 * the old rotation window must be treated as stale, dropped, and rebuilt once in the new window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SlotWindowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `reuse requires attachment to the same window root`() {
        val first = FrameLayout(context)
        val second = FrameLayout(context)

        assertTrue(slotSharesWindow(elementAttached = true, elementRoot = first, windowRoot = first))
        assertFalse(slotSharesWindow(elementAttached = false, elementRoot = first, windowRoot = first))
        assertFalse(slotSharesWindow(elementAttached = true, elementRoot = first, windowRoot = second))
        assertFalse(slotSharesWindow(elementAttached = true, elementRoot = null, windowRoot = first))
        assertFalse(slotSharesWindow(elementAttached = true, elementRoot = first, windowRoot = null))
    }

    @Test
    fun `a cluster may be re-placed while its hierarchy is the same root`() {
        val window = FrameLayout(context)
        val oldStrip = FrameLayout(context)
        val newStrip = FrameLayout(context)
        window.addView(oldStrip)
        window.addView(newStrip)

        // Both strips resolve to the same window even before the window is attached.
        assertTrue(slotInSameWindow(oldStrip.rootView, newStrip.rootView))
        assertTrue(slotInSameWindow(oldStrip.rootView, window.rootView))
        assertFalse(slotInSameWindow(oldStrip.rootView, FrameLayout(context).rootView))
        assertFalse(slotInSameWindow(null, window.rootView))
    }
}
