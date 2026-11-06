package io.github.kvmy666.duostatusbar.settings

import android.content.res.Configuration
import android.database.MatrixCursor
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsBridgeSkewTest {

    @Test
    fun `81 values into 73 columns uses 73`() {
        assertEquals(73, DuoSettingsClient.bridgeColumnCount(81, 73))
    }

    @Test
    fun `73 values into 81 columns uses 73 and defaults`() {
        assertEquals(73, DuoSettingsClient.bridgeColumnCount(73, 81))
        val columns = DuoPrefs.COLUMNS.copyOf(73)
        val full = DuoSettingsProvider.rowFor(DuoSettings(), 1L)
        val values = full.take(73)
        val cursor = MatrixCursor(columns).apply { addRow(values.toTypedArray()) }
        cursor.moveToFirst()
        val read = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        // No throw; missing tail columns fall back to defaults.
        assertEquals(DuoPrefs.DEFAULT_EDGE_PADDING, read.edgePadding)
        // The newest columns are beyond the truncated row, so they fall back to their defaults.
        assertEquals(true, read.downloadProgressEnabled)
        assertEquals(false, read.burnInEnabled)
        assertEquals("", read.hiddenStockIcons)
    }

    @Test
    fun `provider row invariant`() {
        val row = DuoSettingsProvider.rowFor(DuoSettings(), 1L)
        assertEquals(DuoPrefs.COLUMNS.size, row.size)
    }

    @Test
    fun `empty values are ignored without throwing`() {
        // Exercise the helper boundary; an empty push must not produce a cursor.
        assertEquals(0, DuoSettingsClient.bridgeColumnCount(0, DuoPrefs.COLUMNS.size))
    }
}
