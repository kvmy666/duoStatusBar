package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

    /** Android 17 AOSP's Compose icon cluster: a unit hidden with its layout kept. */
    private class SingleBindableStatusBarComposeIconView(context: Context) : View(context)

    /** A generic Compose host the host resolved as the cluster; hidden explicitly by the anchor path. */
    private class ComposeView(context: Context) : View(context)

    /** Motorola's dual-SIM composite: one CELLULAR view whose id carries both SIM rows. */
    private class MobileSignalGroupView(context: Context) : View(context)

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

    @Test
    fun `the Compose cluster is INVISIBLE and is restored`() {
        val strip = LinearLayout(context)
        val cluster = SingleBindableStatusBarComposeIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(85, 58)
        }
        strip.addView(cluster)

        // Default per-icon mode: hideOnlyReplaced with hideOtherIcons off.
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)

        assertEquals(View.INVISIBLE, cluster.visibility)
        assertEquals(85, cluster.layoutParams.width)
        assertEquals(58, cluster.layoutParams.height)

        hider.restore()
        assertEquals(View.VISIBLE, cluster.visibility)
        assertEquals(85, cluster.layoutParams.width)
        assertEquals(58, cluster.layoutParams.height)
    }

    @Test
    fun `hide all keeps the Compose cluster laid out`() {
        val strip = LinearLayout(context)
        val cluster = SingleBindableStatusBarComposeIconView(context)
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(cluster)
        strip.addView(alarm)

        StockIconHider().hideAllExcept(strip, keep = null)

        assertEquals(View.INVISIBLE, cluster.visibility)
    }

    @Test
    fun `a generic ComposeView passed as the Compose anchor is hidden and restored`() {
        val strip = LinearLayout(context)
        val anchor = ComposeView(context)
        strip.addView(anchor)

        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null, composeAnchor = anchor)

        assertEquals(View.INVISIBLE, anchor.visibility)

        hider.restore()
        assertEquals(View.VISIBLE, anchor.visibility)
    }

    @Test
    fun `hide makes a signal cluster GONE and zero and restore puts it back`() {
        val cluster = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(120, 40)
        }

        val hider = StockIconHider()
        hider.hide(cluster)

        assertEquals(View.GONE, cluster.visibility)
        assertEquals(0, cluster.layoutParams.width)
        assertEquals(0, cluster.layoutParams.height)

        hider.restore()

        assertEquals(View.VISIBLE, cluster.visibility)
        assertEquals(120, cluster.layoutParams.width)
        assertEquals(40, cluster.layoutParams.height)
    }

    // ------------------------------------------------------------------ R12 dual-SIM composite

    private fun dualComposite() = MobileSignalGroupView(context).apply {
        id = android.R.id.text1 // base key: id:text1
        layoutParams = LinearLayout.LayoutParams(80, 36)
    }

    @Test
    fun `a dual-SIM composite is hidden by the default hider`() {
        val strip = LinearLayout(context)
        val composite = dualComposite()
        strip.addView(composite)

        StockIconHider().hideReplaced(strip, keep = null)

        assertEquals("the old behaviour is still the default", View.GONE, composite.visibility)
    }

    @Test
    fun `a kept dual-SIM composite stays visible and the selected key still hides`() {
        val strip = LinearLayout(context)
        val composite = dualComposite()
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(composite)
        strip.addView(alarm)

        val hider = StockIconHider(setOf("id:text1"))
        hider.hideReplaced(strip, keep = null, selected = setOf("slot:alarm_clock"))

        assertEquals("both SIM rows must stay live", View.VISIBLE, composite.visibility)
        assertEquals(View.GONE, alarm.visibility)

        hider.restore()
        assertEquals("a kept view was never hidden, so restore leaves it alone", View.VISIBLE, composite.visibility)
    }

    @Test
    fun `a kept dual-SIM composite survives hide all`() {
        val strip = LinearLayout(context)
        val composite = dualComposite()
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(composite)
        strip.addView(alarm)

        StockIconHider(setOf("id:text1")).hideAllExcept(strip, keep = null)

        assertEquals("both SIM rows must stay live", View.VISIBLE, composite.visibility)
        assertEquals(View.GONE, alarm.visibility)
    }

    // ------------------------------------------------------------------ R3 A6 re-show predicate

    @Test
    fun `no reapply is needed right after a full pass`() {
        val strip = LinearLayout(context)
        strip.addView(FakeIcon(context, "wifi"))
        strip.addView(FakeIcon(context, "alarm_clock"))
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)

        assertFalse(hider.needsReapply(strip))
    }

    @Test
    fun `a remembered hidden view shown again needs a reapply`() {
        val strip = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        strip.addView(wifi)
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)
        assertFalse(hider.needsReapply(strip))

        // The ROM re-shows its icon without changing the child count.
        wifi.visibility = View.VISIBLE
        assertTrue(hider.needsReapply(strip))
    }

    @Test
    fun `a changed container or child count needs a reapply`() {
        val strip = LinearLayout(context)
        strip.addView(FakeIcon(context, "wifi"))
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)

        strip.addView(FakeIcon(context, "alarm_clock"))
        assertTrue(hider.needsReapply(strip))

        val other = LinearLayout(context)
        other.addView(FakeIcon(context, "wifi"))
        assertTrue(hider.needsReapply(other))
    }

    @Test
    fun `re-added same-count views need a reapply`() {
        val strip = LinearLayout(context)
        strip.addView(FakeIcon(context, "wifi"))
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)
        assertFalse(hider.needsReapply(strip))

        // Keyguard/unlock re-inflates the strip with new view objects but the same child count.
        strip.removeAllViews()
        strip.addView(FakeIcon(context, "wifi"))

        assertTrue(hider.needsReapply(strip))
    }

    @Test
    fun `hide re-applies to the re-added views after a keyguard cycle`() {
        val strip = LinearLayout(context)
        strip.addView(FakeIcon(context, "wifi"))
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)

        strip.removeAllViews()
        val newWifi = FakeIcon(context, "wifi")
        val newAlarm = FakeIcon(context, "alarm_clock")
        strip.addView(newWifi)
        strip.addView(newAlarm)

        hider.hideReplaced(strip, keep = null)

        assertEquals(View.GONE, newWifi.visibility)
        assertEquals(View.VISIBLE, newAlarm.visibility)
    }

    @Test
    fun `restore clears the pass so the next layout runs the full walk`() {
        val strip = LinearLayout(context)
        strip.addView(FakeIcon(context, "wifi"))
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)
        hider.restore()

        assertTrue(hider.needsReapply(strip))
    }

    @Test
    fun `a re-inflation drops the detached views an earlier pass hid`() {
        val strip = LinearLayout(context)
        val old = FakeIcon(context, "wifi")
        strip.addView(old)
        val hider = StockIconHider()
        hider.hideReplaced(strip, keep = null)
        assertEquals(View.GONE, old.visibility)

        // The ROM re-inflates the same container with new view objects (same child count).
        strip.removeAllViews()
        val fresh = FakeIcon(context, "wifi")
        strip.addView(fresh)
        hider.hideReplaced(strip, keep = null)

        hider.restore()
        assertEquals(View.VISIBLE, fresh.visibility)
        assertEquals("a detached view must not be resurrected", View.GONE, old.visibility)
    }

    @Test
    fun `a nested pass keeps the sibling battery the same hider hid`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val outer = FrameLayout(activity)
        val icons = LinearLayout(activity)
        val wifi = FakeIcon(activity, "wifi")
        icons.addView(wifi)
        val battery = FakeIcon(activity, "battery").apply {
            layoutParams = FrameLayout.LayoutParams(40, 30)
        }
        outer.addView(icons)
        outer.addView(battery)
        activity.setContentView(outer)
        outer.layout(0, 0, 1080, 200)

        val hider = StockIconHider()
        // The QS cluster hides the inner statusIcons before the outer cluster is noted; a later pass
        // walks the inner container first again, and the sibling battery must survive that prune.
        hider.hideReplaced(icons, keep = null)
        hider.hideKeepLayout(battery)
        hider.notePass(outer)
        hider.hideReplaced(icons, keep = null)
        hider.hideKeepLayout(battery)
        hider.notePass(outer)

        hider.restore()
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(View.VISIBLE, wifi.visibility)
        assertEquals(40, battery.layoutParams.width)
    }
}
