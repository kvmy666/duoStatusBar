package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import io.github.kvmy666.duostatusbar.hook.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R8: the iOS "Hide extra icons" toggle is appended after [DuoPrefs.COL_IOS_PERCENT_INSIDE], defaults to
 * on, and a row from an older app that omits the column still hides the extras (never blanks the bar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HideExtrasSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun cursorOf(columns: Array<String>, values: List<Any>): Cursor =
        MatrixCursor(columns).apply { addRow(values) }.also { it.moveToFirst() }

    private fun rowWithout(settings: DuoSettings, drop: List<String>): Pair<Array<String>, List<Any>> {
        val columns = DuoPrefs.COLUMNS.filter { it !in drop }.toTypedArray()
        val values = DuoSettingsProvider.rowFor(settings, 1L).toMutableList()
        drop.map { DuoPrefs.COLUMNS.indexOf(it) }
            .sortedDescending()
            .forEach { values.removeAt(it) }
        return columns to values
    }

    @Before
    fun clear() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `the default is on in both settings objects`() {
        assertTrue(DuoSettings().hideExtras)
        assertTrue(ModuleSettings.DEFAULT.hideExtras)
    }

    @Test
    fun `the toggle round trips through prefs, the provider row and the client`() {
        DuoPrefs.write(context, DuoSettings(hideExtras = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(hideExtras = true), DuoOrientation.LANDSCAPE)

        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertFalse(portrait.hideExtras)
        assertTrue(landscape.hideExtras)

        val row = DuoSettingsProvider.rowFor(portrait, revision = 1L, landscape)
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_HIDE_EXTRAS)])
        assertEquals(
            1,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDE_EXTRAS)]
        )
        val cursor = cursorOf(DuoPrefs.COLUMNS, row.toList())
        assertFalse(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT).hideExtras
        )
        assertTrue(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE).hideExtras
        )
    }

    @Test
    fun `the provider appends the column after ios percent inside`() {
        val row = DuoSettingsProvider.rowFor(DuoSettings(), 1L)

        assertEquals(
            DuoPrefs.PORTRAIT_COLUMNS.size - 1,
            DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_HIDE_EXTRAS)
        )
        assertEquals(
            DuoPrefs.PORTRAIT_COLUMNS.size - 2,
            DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_IOS_PERCENT_INSIDE)
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDE_EXTRAS,
            DuoPrefs.COLUMNS.last()
        )
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_HIDE_EXTRAS)])
        assertEquals(
            DuoPrefs.COLUMNS.size,
            row.size
        )
    }

    @Test
    fun `an older row without the column keeps the extras hidden`() {
        val drop = listOf(
            DuoPrefs.COL_HIDE_EXTRAS,
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDE_EXTRAS
        )
        val (columns, values) = rowWithout(DuoSettings(hideExtras = false), drop)
        val cursor = cursorOf(columns, values)

        assertTrue(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT).hideExtras
        )
        assertTrue(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE).hideExtras
        )
    }
}
