package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue 7: the per-icon iOS customisation fields survive storage, clamping and the provider row, and a
 * row from an older app keeps today's pixels (0 offset, 100 % scale, automatic SIM display).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosIconSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val newPortraitColumns = listOf(
        DuoPrefs.COL_IOS_BATTERY_OFFSET,
        DuoPrefs.COL_IOS_BATTERY_SCALE,
        DuoPrefs.COL_IOS_WIFI_OFFSET,
        DuoPrefs.COL_IOS_WIFI_SCALE,
        DuoPrefs.COL_IOS_CELL_OFFSET,
        DuoPrefs.COL_IOS_CELL_SCALE,
        DuoPrefs.COL_SIM_DISPLAY
    )

    private fun rowWithout(settings: DuoSettings, drop: List<String>): Pair<Array<String>, List<Any>> {
        val columns = DuoPrefs.COLUMNS.filter { it !in drop }.toTypedArray()
        val values = DuoSettingsProvider.rowFor(settings, 1L).toMutableList()
        drop.map { DuoPrefs.COLUMNS.indexOf(it) }
            .sortedDescending()
            .forEach { values.removeAt(it) }
        return columns to values
    }

    private fun cursorOf(columns: Array<String>, values: List<Any>): Cursor =
        MatrixCursor(columns).apply { addRow(values) }.also { it.moveToFirst() }

    @Test
    fun `defaults are today's pixels`() {
        val defaults = DuoSettings()
        assertEquals(0, defaults.iosBatteryOffset)
        assertEquals(100, defaults.iosBatteryScale)
        assertEquals(0, defaults.iosWifiOffset)
        assertEquals(100, defaults.iosWifiScale)
        assertEquals(0, defaults.iosCellOffset)
        assertEquals(100, defaults.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_AUTO, defaults.simDisplay)
    }

    @Test
    fun `portrait write and read round trip the custom values`() {
        DuoPrefs.write(
            context,
            DuoSettings(
                iosBatteryOffset = 12,
                iosBatteryScale = 130,
                iosWifiOffset = -8,
                iosWifiScale = 80,
                iosCellOffset = 20,
                iosCellScale = 120,
                simDisplay = DuoPrefs.SIM_DISPLAY_SINGLE
            ),
            DuoOrientation.PORTRAIT
        )

        val read = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertEquals(12, read.iosBatteryOffset)
        assertEquals(130, read.iosBatteryScale)
        assertEquals(-8, read.iosWifiOffset)
        assertEquals(80, read.iosWifiScale)
        assertEquals(20, read.iosCellOffset)
        assertEquals(120, read.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_SINGLE, read.simDisplay)
    }

    @Test
    fun `write clamps the custom values and normalises sim display`() {
        DuoPrefs.write(
            context,
            DuoSettings(
                iosBatteryOffset = 999,
                iosBatteryScale = 999,
                iosWifiOffset = -999,
                iosWifiScale = 0,
                iosCellOffset = 999,
                iosCellScale = 0,
                simDisplay = "bogus"
            ),
            DuoOrientation.PORTRAIT
        )

        val read = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertEquals(DuoPrefs.MAX_ICON_OFFSET, read.iosBatteryOffset)
        assertEquals(DuoPrefs.MAX_ICON_SCALE, read.iosBatteryScale)
        assertEquals(DuoPrefs.MIN_ICON_OFFSET, read.iosWifiOffset)
        assertEquals(DuoPrefs.MIN_ICON_SCALE, read.iosWifiScale)
        assertEquals(DuoPrefs.MAX_ICON_OFFSET, read.iosCellOffset)
        assertEquals(DuoPrefs.MIN_ICON_SCALE, read.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_AUTO, read.simDisplay)
    }

    @Test
    fun `landscape round trips through the appended columns`() {
        DuoPrefs.write(
            context,
            DuoSettings(
                iosBatteryOffset = 5,
                iosBatteryScale = 90,
                iosWifiOffset = -4,
                iosWifiScale = 110,
                iosCellOffset = 7,
                iosCellScale = 140,
                simDisplay = DuoPrefs.SIM_DISPLAY_DUAL
            ),
            DuoOrientation.LANDSCAPE
        )

        val read = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertEquals(5, read.iosBatteryOffset)
        assertEquals(90, read.iosBatteryScale)
        assertEquals(-4, read.iosWifiOffset)
        assertEquals(110, read.iosWifiScale)
        assertEquals(7, read.iosCellOffset)
        assertEquals(140, read.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_DUAL, read.simDisplay)
    }

    @Test
    fun `the provider row publishes the clamped custom values`() {
        val row = DuoSettingsProvider.rowFor(
            DuoSettings(
                iosBatteryOffset = 999,
                iosBatteryScale = 300,
                iosWifiOffset = -999,
                iosWifiScale = 0,
                iosCellOffset = 999,
                iosCellScale = 300,
                simDisplay = "bogus"
            ),
            7L
        )

        assertEquals(DuoPrefs.COLUMNS.size, row.size)
        assertEquals(
            DuoPrefs.MAX_ICON_OFFSET,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_BATTERY_OFFSET)]
        )
        assertEquals(
            DuoPrefs.MAX_ICON_SCALE,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_BATTERY_SCALE)]
        )
        assertEquals(
            DuoPrefs.MIN_ICON_OFFSET,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_WIFI_OFFSET)]
        )
        assertEquals(
            DuoPrefs.MIN_ICON_SCALE,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_WIFI_SCALE)]
        )
        assertEquals(
            DuoPrefs.MAX_ICON_OFFSET,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_CELL_OFFSET)]
        )
        assertEquals(
            DuoPrefs.MAX_ICON_SCALE,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_CELL_SCALE)]
        )
        assertEquals(
            DuoPrefs.SIM_DISPLAY_AUTO,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SIM_DISPLAY)]
        )
        // The landscape copy is the tail of the row and ends with the appended SIM display column.
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_SIM_DISPLAY,
            DuoPrefs.COLUMNS.last()
        )
        assertEquals(
            DuoPrefs.SIM_DISPLAY_AUTO,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_SIM_DISPLAY)]
        )
    }

    @Test
    fun `a row from an older app keeps today's pixels`() {
        val drop = newPortraitColumns + newPortraitColumns.map { DuoPrefs.LAND_PREFIX + it }
        val (columns, values) = rowWithout(DuoSettings(), drop)
        val read = DuoSettingsClient.fromCursor(
            cursorOf(columns, values),
            Configuration.ORIENTATION_PORTRAIT
        )

        assertEquals(0, read.iosBatteryOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, read.iosBatteryScale)
        assertEquals(0, read.iosWifiOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, read.iosWifiScale)
        assertEquals(0, read.iosCellOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, read.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_AUTO, read.simDisplay)
    }

    @Test
    fun `the client parses a custom portrait row`() {
        val row = DuoSettingsProvider.rowFor(
            DuoSettings(
                iosBatteryOffset = 12,
                iosBatteryScale = 130,
                simDisplay = DuoPrefs.SIM_DISPLAY_SINGLE
            ),
            1L
        )
        val read = DuoSettingsClient.fromCursor(
            cursorOf(DuoPrefs.COLUMNS, row.toList()),
            Configuration.ORIENTATION_PORTRAIT
        )

        assertEquals(12, read.iosBatteryOffset)
        assertEquals(130, read.iosBatteryScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_SINGLE, read.simDisplay)
    }
}
