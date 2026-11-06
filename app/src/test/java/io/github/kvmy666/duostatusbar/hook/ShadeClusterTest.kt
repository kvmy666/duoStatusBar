package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure rules behind the OnePlus shade hand-over: a 0x0 (or hidden) cluster is never attached to, and
 * the reflective getter fallback follows the field spelling the vendor hook hands over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShadeClusterTest {

    @Test
    fun `cluster is ready only after a real width and while visible and attached`() {
        // 0 is View.VISIBLE, 8 is View.GONE; the helper stays Android-free so it can be tested here.
        assertFalse(shadeClusterReady(0, 0, attached = true, shown = true))
        assertTrue(shadeClusterReady(1, 0, attached = true, shown = true))
        assertFalse(shadeClusterReady(386, 8, attached = true, shown = true))
        assertFalse(shadeClusterReady(386, 0, attached = false, shown = true))
        assertFalse(shadeClusterReady(386, 0, attached = true, shown = false))
    }

    @Test
    fun `getter fallback follows the measured field names`() {
        assertEquals("getQsStatusIcon", getterFor("qsStatusIcon"))
        assertEquals("getBatteryIcon", getterFor("batteryIcon"))
    }

    /**
     * QS and the shade must own separate slots. The measured QS cluster is 9x63 and the shade header
     * is 386x101, so dimensions decide first; the OnePlus simple header is QS even when wide, and the
     * split shade header is the shade even when the same source is handed a narrow cluster early.
     */
    @Test
    fun `qs and shade routes own separate slots`() {
        assertEquals("qs header", shadeSlotName("OplusQSSimpleHeader", 386, 101))
        assertEquals("qs header", shadeSlotName("window", 9, 63))
        assertEquals("qs header", shadeSlotName("OplusSplitShade", 9, 63))
        assertEquals("shade header", shadeSlotName("window", 386, 101))
        assertEquals("shade header", shadeSlotName("OplusSplitShade", 386, 101))
        assertEquals("shade header", shadeSlotName(null, 386, 101))
    }

    @Test
    fun `a slot owns a host only when its element is the same live child`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = FrameLayout(context)
        val other = FrameLayout(context)
        assertTrue(slotOwnsHost(target, target, elementAttached = true, target = target))
        assertFalse(slotOwnsHost(target, target, elementAttached = false, target = target))
        assertFalse(slotOwnsHost(other, target, elementAttached = true, target = target))
        assertFalse(slotOwnsHost(target, other, elementAttached = true, target = target))
    }
}
