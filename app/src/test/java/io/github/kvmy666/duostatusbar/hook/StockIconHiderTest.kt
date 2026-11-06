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

    /** An OEM icon view with no readable slot, named like ColorOS's. */
    private class OplusModernStatusBarMobileView(context: Context) : View(context)

    /** A non-replaced icon view whose name must not match. */
    private class BluetoothHeadsetView(context: Context) : View(context)

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
    fun `an OEM icon with no slot is matched by its class, others are not`() {
        val hider = StockIconHider()
        assertTrue(hider.isReplaced(OplusModernStatusBarMobileView(context)))
        assertFalse(hider.isReplaced(BluetoothHeadsetView(context)))
    }

    @Test
    fun `a plain container is never treated as a replaced icon`() {
        // The strip and the icon container have no slot and are not batteries, so they must be walked
        // into, not hidden: hiding them would take the very icons the user asked to keep.
        assertFalse(StockIconHider().isReplaced(LinearLayout(context)))
    }

    @Test
    fun `a battery is always INVISIBLE and keeps its layout`() {
        val strip = LinearLayout(context)
        val battery = FakeIcon(context, "battery").apply {
            layoutParams = LinearLayout.LayoutParams(40, 30)
        }
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(battery)
        strip.addView(alarm)

        // No keepLayout argument: the battery is still the overlay anchor, so it must never be GONE.
        StockIconHider().hideReplaced(strip, keep = null)

        assertEquals(View.INVISIBLE, battery.visibility)
        assertEquals(40, battery.layoutParams.width)
        assertEquals(30, battery.layoutParams.height)
        assertEquals(View.VISIBLE, alarm.visibility)
    }

    @Test
    fun `hide all hides every child except the kept view`() {
        val strip = LinearLayout(context)
        val battery = FakeIcon(context, "battery")
        val wifi = FakeIcon(context, "wifi")
        val alarm = FakeIcon(context, "alarm_clock")
        val keep = FakeIcon(context, "clock")
        strip.addView(battery)
        strip.addView(wifi)
        strip.addView(alarm)
        strip.addView(keep)

        StockIconHider().hideAllExcept(strip, keep = keep, keepLayout = battery)

        assertEquals(View.INVISIBLE, battery.visibility)
        assertEquals(View.GONE, wifi.visibility)
        assertEquals(View.GONE, alarm.visibility)
        assertEquals(View.VISIBLE, keep.visibility)
    }

    @Test
    fun `per-key mode hides the replaced icons plus exactly the selected key`() {
        val strip = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        val alarm = FakeIcon(context, "alarm_clock")
        val silent = FakeIcon(context, "volume")
        strip.addView(wifi)
        strip.addView(alarm)
        strip.addView(silent)

        StockIconHider().hideReplaced(strip, keep = null, selected = setOf("slot:alarm_clock"))

        assertEquals(View.GONE, wifi.visibility)
        assertEquals(View.GONE, alarm.visibility)
        assertEquals(View.VISIBLE, silent.visibility)
    }

    @Test
    fun `only the selected duplicate occurrence is hidden`() {
        val strip = LinearLayout(context)
        val first = FakeIcon(context, "alarm_clock")
        val second = FakeIcon(context, "alarm_clock")
        strip.addView(first)
        strip.addView(second)

        StockIconHider().hideReplaced(strip, keep = null, selected = setOf("slot:alarm_clock#2"))

        assertEquals(View.VISIBLE, first.visibility)
        assertEquals(View.GONE, second.visibility)
    }

    @Test
    fun `nested containers are walked but never hidden`() {
        val strip = LinearLayout(context)
        val inner = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(inner)
        inner.addView(wifi)
        inner.addView(alarm)

        StockIconHider().hideReplaced(
            strip,
            keep = null,
            selected = setOf("slot:alarm_clock")
        )

        assertEquals(View.VISIBLE, inner.visibility)
        assertEquals(View.GONE, wifi.visibility)
        assertEquals(View.GONE, alarm.visibility)
    }

    @Test
    fun `an unknown selected key is a no-op`() {
        val strip = LinearLayout(context)
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(alarm)

        StockIconHider().hideReplaced(
            strip,
            keep = null,
            selected = setOf("slot:not_on_this_rom")
        )

        assertEquals(View.VISIBLE, alarm.visibility)
    }

    @Test
    fun `restore puts back visibility and layout exactly`() {
        val strip = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi").apply {
            layoutParams = LinearLayout.LayoutParams(44, 22)
        }
        val battery = FakeIcon(context, "battery").apply {
            layoutParams = LinearLayout.LayoutParams(39, 31)
        }
        val alarm = FakeIcon(context, "alarm_clock").apply {
            layoutParams = LinearLayout.LayoutParams(20, 18)
        }
        strip.addView(wifi)
        strip.addView(battery)
        strip.addView(alarm)

        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null, selected = setOf("slot:alarm_clock"))
        assertEquals(View.GONE, wifi.visibility)
        assertEquals(View.INVISIBLE, battery.visibility)
        assertEquals(View.GONE, alarm.visibility)

        hider.restore()

        assertEquals(View.VISIBLE, wifi.visibility)
        assertEquals(44, wifi.layoutParams.width)
        assertEquals(22, wifi.layoutParams.height)
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(39, battery.layoutParams.width)
        assertEquals(31, battery.layoutParams.height)
        assertEquals(View.VISIBLE, alarm.visibility)
        assertEquals(20, alarm.layoutParams.width)
        assertEquals(18, alarm.layoutParams.height)
    }

    @Test
    fun `restore keeps a view that was already gone gone`() {
        val strip = LinearLayout(context)
        val alarm = FakeIcon(context, "alarm_clock").apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(20, 18)
        }
        strip.addView(alarm)

        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null, selected = setOf("slot:alarm_clock"))
        hider.restore()

        assertEquals(View.GONE, alarm.visibility)
        assertEquals(20, alarm.layoutParams.width)
        assertEquals(18, alarm.layoutParams.height)
    }
}
