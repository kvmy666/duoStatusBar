package io.github.kvmy666.duostatusbar.settings

import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Column-skew guard for the appended iOS column, modeled on the "row without X keeps default"
 * tests in [SettingsChannelTest]. A missing column must keep the old pixels (rule 6): iOS mode off,
 * whether the row comes from an older app or the bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosColumnSkewTest {

    /** A provider row without [drop], with the values removed at the same positions. */
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
    fun `ios mode round trips through the provider row and the module client`() {
        val row = DuoSettingsProvider.rowFor(DuoSettings(iosMode = true), 1L)
        val read = DuoSettingsClient.fromCursor(
            cursorOf(DuoPrefs.COLUMNS, row.toList()),
            Configuration.ORIENTATION_PORTRAIT
        )
        assertTrue(read.iosMode)
    }

    @Test
    fun `a portrait row without the ios column keeps the mode off`() {
        val (columns, values) = rowWithout(
            DuoSettings(iosMode = true),
            listOf(DuoPrefs.COL_IOS_MODE)
        )
        val read = DuoSettingsClient.fromCursor(
            cursorOf(columns, values),
            Configuration.ORIENTATION_PORTRAIT
        )
        assertFalse(read.iosMode)
    }

    @Test
    fun `landscape keeps the default without its ios column and reads it when present`() {
        val (columns, values) = rowWithout(
            DuoSettings(),
            listOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_MODE)
        )
        val fallback = DuoSettingsClient.fromCursor(
            cursorOf(columns, values),
            Configuration.ORIENTATION_LANDSCAPE
        )
        assertFalse(fallback.iosMode)

        val withColumns = DuoSettingsProvider.rowFor(
            DuoSettings(iosMode = false),
            1L,
            DuoSettings(iosMode = true)
        )
        val read = DuoSettingsClient.fromCursor(
            cursorOf(DuoPrefs.COLUMNS, withColumns.toList()),
            Configuration.ORIENTATION_LANDSCAPE
        )
        assertTrue(read.iosMode)
    }

    @Test
    fun `the row has the declared columns with the ios columns appended last`() {
        val row = DuoSettingsProvider.rowFor(DuoSettings(), 1L)
        assertEquals(DuoPrefs.COLUMNS.size, row.size)
        assertEquals(103, DuoPrefs.COLUMNS.size)
        assertEquals(52, DuoPrefs.PORTRAIT_COLUMNS.size)
        assertEquals(51, DuoPrefs.LANDSCAPE_COLUMNS.size)
        // Portrait ends with the plain name; COLUMNS additionally carries its landscape copy.
        assertEquals(DuoPrefs.COL_SIM_DISPLAY, DuoPrefs.PORTRAIT_COLUMNS.last())
        assertEquals(
            DuoPrefs.COL_IOS_CELL_SCALE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 1]
        )
        assertEquals(
            DuoPrefs.COL_IOS_CELL_OFFSET,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 2]
        )
        assertEquals(
            DuoPrefs.COL_IOS_WIFI_SCALE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 3]
        )
        assertEquals(
            DuoPrefs.COL_IOS_WIFI_OFFSET,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 4]
        )
        assertEquals(
            DuoPrefs.COL_IOS_BATTERY_SCALE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 5]
        )
        assertEquals(
            DuoPrefs.COL_IOS_BATTERY_OFFSET,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 6]
        )
        assertEquals(
            DuoPrefs.COL_IOS_MODE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 7]
        )
        assertEquals(
            DuoPrefs.COL_DESIGN_STYLE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 8]
        )
        assertEquals(
            DuoPrefs.COL_HIDDEN_STOCK_ICONS,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 9]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_SIM_DISPLAY,
            DuoPrefs.COLUMNS.last()
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_CELL_SCALE,
            DuoPrefs.COLUMNS[DuoPrefs.COLUMNS.lastIndex - 1]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_MODE,
            DuoPrefs.COLUMNS[DuoPrefs.COLUMNS.lastIndex - 7]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DESIGN_STYLE,
            DuoPrefs.COLUMNS[DuoPrefs.COLUMNS.lastIndex - 8]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDDEN_STOCK_ICONS,
            DuoPrefs.COLUMNS[DuoPrefs.COLUMNS.lastIndex - 9]
        )
    }
}
