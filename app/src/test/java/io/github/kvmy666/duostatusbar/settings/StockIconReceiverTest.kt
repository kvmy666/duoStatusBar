package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A4: the bridge carries the module's live icon list into the app's storage the same way status does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StockIconReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `stock icon push is stored for the settings screen`() {
        DuoPrefs.writeStockIcons(context, "")
        val json = StockIconInventory.encode(
            listOf(
                StockIconInventoryItem("slot:wifi", "Wi-Fi", "WIFI"),
                StockIconInventoryItem("slot:mobile_combo", "Cellular", "CELLULAR")
            )
        )

        StatusBridgeReceiver().onReceive(
            context,
            Intent(SettingsBridge.ACTION_STOCK_ICONS_PUSH)
                .putExtra(SettingsBridge.EXTRA_STOCK_ICONS, json)
        )

        assertEquals(json, DuoPrefs.readStockIcons(context))
        assertEquals(
            listOf("slot:wifi", "slot:mobile_combo"),
            StockIconInventory.decode(DuoPrefs.readStockIcons(context)).map { it.key }
        )
    }

    @Test
    fun `an empty push is ignored instead of clearing the last list`() {
        DuoPrefs.writeStockIcons(context, "[\"kept\"]")
        StatusBridgeReceiver().onReceive(
            context,
            Intent(SettingsBridge.ACTION_STOCK_ICONS_PUSH)
                .putExtra(SettingsBridge.EXTRA_STOCK_ICONS, "")
        )
        assertEquals("[\"kept\"]", DuoPrefs.readStockIcons(context))
    }
}
