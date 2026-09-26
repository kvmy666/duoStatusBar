package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-08b: with "hide other icons" off, only the icons Duo replaces must go; everything else (silent,
 * vibrate, alarm) has to stay. A wrong rule here hides a user's icons and looks like the module is
 * broken, so the slot decision is pinned instead of only exercised on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StockIconHiderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A status icon view that reports a slot, like `StatusBarIconView.getSlot()`. */
    private class FakeIcon(context: Context, private val slot: String) : View(context) {
        @Suppress("unused")
        fun getSlot(): String = slot
    }

    @Test
    fun `wifi, mobile and battery slots are the ones Duo replaces`() {
        val hider = StockIconHider()
        assertTrue(hider.isReplaced(FakeIcon(context, "wifi")))
        assertTrue(hider.isReplaced(FakeIcon(context, "mobile")))
        assertTrue(hider.isReplaced(FakeIcon(context, "mobile_roaming")))
        assertTrue(hider.isReplaced(FakeIcon(context, "battery")))
        assertFalse(hider.isReplaced(FakeIcon(context, "volume")))
        assertFalse(hider.isReplaced(FakeIcon(context, "alarm_clock")))
    }

    @Test
    fun `hiding the replaced icons leaves the others visible`() {
        val strip = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        val silent = FakeIcon(context, "volume")
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(wifi)
        strip.addView(silent)
        strip.addView(alarm)

        StockIconHider().hideReplaced(strip, keep = null)

        assertEquals(View.GONE, wifi.visibility)
        assertEquals(View.VISIBLE, silent.visibility)
        assertEquals(View.VISIBLE, alarm.visibility)
    }

    @Test
    fun `a plain container is never treated as a replaced icon`() {
        // The strip and the icon container have no slot and are not batteries, so they must be walked
        // into, not hidden: hiding them would take the very icons the user asked to keep.
        assertFalse(StockIconHider().isReplaced(LinearLayout(context)))
    }
}
