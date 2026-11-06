package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4: the stable key and the read-only inventory are what the settings list and the hiding pass
 * both rely on, so the resolution order, the occurrence suffix and the ignore rules are pinned here.
 * A drift between this inventory and [StockIconHider] would hide the wrong icon for a stored key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StockIconKeyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A status icon view that reports a slot, like `StatusBarIconView.getSlot()`. */
    private class FakeIcon(context: Context, private val slot: String) : View(context) {
        @Suppress("unused")
        fun getSlot(): String = slot
    }

    /** An OEM icon that only exposes `getSlotTag()`. */
    private class TaggedIcon(context: Context, private val slot: String) : View(context) {
        @Suppress("unused")
        fun getSlotTag(): String = slot
    }

    /** No slot, no matching id: the class name is the last key source. */
    private class AlarmClockView(context: Context) : View(context)

    /** A Duo-owned view by name, as the module creates. */
    private class DuoPreviewView(context: Context) : View(context)

    @Test
    fun `key prefers slot, then resource id, then class`() {
        val bySlot = FakeIcon(context, "battery")
        bySlot.id = android.R.id.text1
        assertEquals("slot:battery", StockIconKey.baseKey(bySlot))

        val byId = AlarmClockView(context)
        byId.id = android.R.id.text1
        assertEquals("id:text1", StockIconKey.baseKey(byId))

        val byClass = AlarmClockView(context)
        assertEquals("class:alarmclockview", StockIconKey.baseKey(byClass))
    }

    @Test
    fun `an OEM slot tag is a slot`() {
        assertEquals("slot:wifi", StockIconKey.baseKey(TaggedIcon(context, "wifi")))
    }

    @Test
    fun `duplicate base keys get a numbered occurrence suffix`() {
        val mobile = FakeIcon(context, "mobile")
        assertEquals("slot:mobile", StockIconKey.keyOf(mobile, occurrence = 1))
        assertEquals("slot:mobile", StockIconKey.keyOf(mobile))
        assertEquals("slot:mobile#2", StockIconKey.keyOf(mobile, occurrence = 2))
        assertEquals("slot:mobile", StockIconKey.key("slot:mobile", 1))
        assertEquals("slot:mobile#3", StockIconKey.key("slot:mobile", 3))
    }

    @Test
    fun `containers are walked into, never listed as an icon`() {
        val strip = LinearLayout(context)
        val container = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        strip.addView(container)
        container.addView(wifi)

        assertEquals(StockIconKind.IGNORED, StockIconKey.kindOf(container))
        val inventory = StockIconKey.inventory(strip)
        assertEquals(listOf("slot:wifi"), inventory.map { it.key })
        assertSame(wifi, inventory.single().view)
    }

    @Test
    fun `inner signal parts, the status dot and Duo views are ignored`() {
        val inner = FakeIcon(context, "mobile_signal")
        val dot = FakeIcon(context, "status_bar_dot")
        val duo = DuoPreviewView(context)
        assertTrue(StockIconKey.isIgnored(inner))
        assertTrue(StockIconKey.isIgnored(dot))
        assertTrue(StockIconKey.isIgnored(duo))
        assertEquals(StockIconKind.IGNORED, StockIconKey.kindOf(FakeIcon(context, "wifi_signal")))

        val strip = LinearLayout(context)
        strip.addView(inner)
        strip.addView(dot)
        strip.addView(duo)
        assertTrue(StockIconKey.inventory(strip).isEmpty())
    }

    @Test
    fun `labels are plain and stable for live views and stored keys`() {
        assertEquals("Wi-Fi", StockIconKey.labelOf(FakeIcon(context, "wifi")))
        assertEquals("Cellular", StockIconKey.labelOf(FakeIcon(context, "mobile_combo")))
        assertEquals("Alarm", StockIconKey.labelOf(FakeIcon(context, "alarm_clock")))
        assertEquals("Cellular", StockIconKey.labelFor("id:mobile_combo#2"))
        assertEquals("Wi-Fi", StockIconKey.labelFor("slot:wifi"))
        assertEquals("Unknown Vendor", StockIconKey.labelFor("slot:unknown_vendor"))
        assertEquals("Status icon", StockIconKey.labelFor(""))
    }

    @Test
    fun `inventory is depth first in child order with matching keys and kinds`() {
        val strip = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        val inner = LinearLayout(context)
        val alarm = FakeIcon(context, "alarm_clock")
        val clock = FakeIcon(context, "clock")
        val firstMobile = FakeIcon(context, "mobile")
        val secondMobile = FakeIcon(context, "mobile")
        strip.addView(wifi)
        inner.addView(alarm)
        inner.addView(clock)
        strip.addView(inner)
        strip.addView(firstMobile)
        strip.addView(secondMobile)

        val inventory = StockIconKey.inventory(strip)

        assertEquals(
            listOf("slot:wifi", "slot:alarm_clock", "slot:clock", "slot:mobile", "slot:mobile#2"),
            inventory.map { it.key }
        )
        assertEquals(
            listOf(
                StockIconKind.WIFI,
                StockIconKind.OTHER,
                StockIconKind.OTHER,
                StockIconKind.CELLULAR,
                StockIconKind.CELLULAR
            ),
            inventory.map { it.kind }
        )
        assertSame(wifi, inventory[0].view)
        assertSame(alarm, inventory[1].view)
        assertSame(clock, inventory[2].view)
        assertEquals("Cellular", inventory[4].label)
        assertSame(secondMobile, inventory[4].view)
    }

    @Test
    fun `find reports the same key the inventory lists`() {
        val strip = LinearLayout(context)
        val inner = LinearLayout(context)
        val alarm = FakeIcon(context, "alarm_clock")
        strip.addView(inner)
        inner.addView(alarm)

        assertEquals("slot:alarm_clock", StockIconKey.find(strip, alarm)?.key)
        assertNull(StockIconKey.find(strip, inner))
    }

    @Test
    fun `inventory summary is the same list the app can decode`() {
        val strip = LinearLayout(context)
        val wifi = FakeIcon(context, "wifi")
        val firstMobile = FakeIcon(context, "mobile")
        val secondMobile = FakeIcon(context, "mobile")
        strip.addView(wifi)
        strip.addView(firstMobile)
        strip.addView(secondMobile)

        val decoded = io.github.kvmy666.duostatusbar.settings.StockIconInventory
            .decode(StockIconKey.inventorySummary(strip))

        assertEquals(
            listOf("slot:wifi", "slot:mobile", "slot:mobile#2"),
            decoded.map { it.key }
        )
        assertEquals(listOf("Wi-Fi", "Cellular", "Cellular"), decoded.map { it.label })
        assertEquals(listOf("WIFI", "CELLULAR", "CELLULAR"), decoded.map { it.kind })
    }
}
