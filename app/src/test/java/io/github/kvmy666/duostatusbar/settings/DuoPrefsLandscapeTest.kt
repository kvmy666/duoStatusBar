package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R4 item 6: a landscape row saved before the appended iOS columns existed must not reset them.
 *
 * The measured settings file had `landscape_set=true` but no `land_ios_*` keys, while portrait held
 * the user's values. Reading the landscape row directly therefore saw iosMode=false and every scale
 * at 100, and the next landscape write persisted those defaults. Missing appended columns now fall
 * back to portrait; legacy columns keep their existing defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoPrefsLandscapeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs() = context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs().edit().clear().commit()
    }

    @Test
    fun `appended columns missing from a saved landscape row fall back to portrait`() {
        prefs().edit()
            .putBoolean("landscape_set", true)
            .putBoolean(DuoPrefs.COL_IOS_MODE, true)
            .putInt(DuoPrefs.COL_IOS_BATTERY_SCALE, 130)
            .putInt(DuoPrefs.COL_IOS_WIFI_SCALE, 120)
            .putString(DuoPrefs.COL_SIM_DISPLAY, DuoPrefs.SIM_DISPLAY_DUAL)
            .commit()

        val read = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)

        assertTrue("ios mode is an appended column and must follow portrait", read.iosMode)
        assertEquals(130, read.iosBatteryScale)
        assertEquals(120, read.iosWifiScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_DUAL, read.simDisplay)
    }

    @Test
    fun `a landscape value that is present wins over the portrait fallback`() {
        prefs().edit()
            .putBoolean("landscape_set", true)
            .putBoolean(DuoPrefs.COL_IOS_MODE, true)
            .putInt(DuoPrefs.COL_IOS_BATTERY_SCALE, 130)
            .putInt(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_BATTERY_SCALE, 90)
            .putBoolean(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_MODE, false)
            .commit()

        val read = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)

        assertEquals(90, read.iosBatteryScale)
        assertEquals(false, read.iosMode)
    }

    @Test
    fun `legacy columns still read their own landscape defaults`() {
        prefs().edit()
            .putBoolean("landscape_set", true)
            .putInt(DuoPrefs.COL_SIZE_PERCENT, 176)
            .putInt(DuoPrefs.COL_EDGE_PADDING, 7)
            .commit()

        val read = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)

        // A legacy column is not part of the append-only fallback: the absent landscape value keeps
        // the landscape default instead of borrowing the portrait one.
        assertEquals(100, read.sizePercent)
        assertEquals(DuoPrefs.DEFAULT_EDGE_PADDING, read.edgePadding)
        assertEquals(176, DuoPrefs.read(context, DuoOrientation.PORTRAIT).sizePercent)
    }
}
