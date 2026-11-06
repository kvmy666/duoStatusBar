package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4: the inventory JSON is the only thing that crosses the settings channel for the per-icon list.
 * Both sides must agree on the list, so the encoder is pinned as canonical and the decoder as
 * forgiving: a truncated push from an older build must never crash System UI or the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StockIconInventoryTest {

    private val wifi = StockIconInventoryItem("slot:wifi", "Wi-Fi", "WIFI")
    private val alarm = StockIconInventoryItem("id:alarm", "Alarm", "OTHER")

    @Test
    fun `encode and decode round trip keys labels and kinds`() {
        val raw = StockIconInventory.encode(listOf(wifi, alarm))
        assertEquals(listOf(wifi, alarm), StockIconInventory.decode(raw))
    }

    @Test
    fun `encode trims, drops blanks and duplicates, and uppercases kind`() {
        val raw = StockIconInventory.encode(
            listOf(
                StockIconInventoryItem(" slot:wifi ", " Wi-Fi ", "wifi"),
                StockIconInventoryItem("slot:wifi", "Duplicate", "OTHER"),
                StockIconInventoryItem("   ", "Blank", "OTHER"),
                StockIconInventoryItem("id:alarm", "  ", "other")
            )
        )

        assertEquals(
            listOf(
                StockIconInventoryItem("slot:wifi", "Wi-Fi", "WIFI"),
                StockIconInventoryItem("id:alarm", "id:alarm", "OTHER")
            ),
            StockIconInventory.decode(raw)
        )
    }

    @Test
    fun `encode bounds a runaway inventory and overlong fields`() {
        val many = (0 until StockIconInventory.MAX_ITEMS + 10).map {
            StockIconInventoryItem("slot:icon$it", "Icon $it", "OTHER")
        }
        assertEquals(StockIconInventory.MAX_ITEMS, StockIconInventory.decode(StockIconInventory.encode(many)).size)

        val long = StockIconInventoryItem(
            "k".repeat(StockIconInventory.MAX_KEY_LENGTH + 20),
            "l".repeat(StockIconInventory.MAX_LABEL_LENGTH + 20),
            "K".repeat(StockIconInventory.MAX_KIND_LENGTH + 20)
        )
        val item = StockIconInventory.decode(StockIconInventory.encode(listOf(long))).single()
        assertEquals(StockIconInventory.MAX_KEY_LENGTH, item.key.length)
        assertEquals(StockIconInventory.MAX_LABEL_LENGTH, item.label.length)
        assertEquals(StockIconInventory.MAX_KIND_LENGTH, item.kind.length)
    }

    @Test
    fun `decode returns an empty list for blank, truncated or malformed payloads`() {
        assertEquals(emptyList<StockIconInventoryItem>(), StockIconInventory.decode(null))
        assertEquals(emptyList<StockIconInventoryItem>(), StockIconInventory.decode(""))
        assertEquals(emptyList<StockIconInventoryItem>(), StockIconInventory.decode("[{\"key\":"))
        assertEquals(emptyList<StockIconInventoryItem>(), StockIconInventory.decode("not json"))
        assertEquals(emptyList<StockIconInventoryItem>(), StockIconInventory.decode("[{\"key\":\"\"}]"))
    }

    @Test
    fun `selection toggles only keys present in the live inventory`() {
        val inventory = listOf(wifi, alarm)
        val hiddenWifi = StockIconSelection.toggle(DuoSettings(), inventory, wifi.key, hidden = true)
        assertTrue(StockIconSelection.isHidden(hiddenWifi, wifi.key))
        assertFalse(StockIconSelection.isHidden(hiddenWifi, alarm.key))
        assertEquals("slot:wifi", hiddenWifi.hiddenStockIcons)

        val restored = StockIconSelection.toggle(hiddenWifi, inventory, wifi.key, hidden = false)
        assertEquals("", restored.hiddenStockIcons)
        assertFalse(StockIconSelection.isHidden(restored, wifi.key))
    }

    @Test
    fun `a stale key is a no-op instead of a wrong hide`() {
        val settings = DuoSettings(hiddenStockIcons = "id:alarm")
        val next = StockIconSelection.toggle(settings, listOf(wifi), "id:gone", hidden = true)
        assertEquals(settings, next)
    }

    @Test
    fun `the all switch leaves the per-key selection alone`() {
        val settings = DuoSettings(hiddenStockIcons = "slot:wifi")
        val all = StockIconSelection.withAll(settings, true)
        assertTrue(all.hideOtherIcons)
        assertEquals("slot:wifi", all.hiddenStockIcons)
        assertEquals(settings, StockIconSelection.withAll(settings, false))
    }
}
