package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoPrefsBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `round trip restores both orientations and app keys`() {
        val portrait = DuoSettings(
            enabled = true,
            useRive = false,
            sizePercent = 150,
            offsetX = -12,
            hideOtherIcons = true,
            hiddenStockIcons = "alarm,battery",
            showInQs = false,
            burnInEnabled = true
        )
        val landscape = DuoSettings(
            enabled = false,
            sizePercent = 80,
            offsetX = -5,
            hideOtherIcons = false,
            hiddenStockIcons = "clock",
            burnInEnabled = false
        )
        DuoPrefs.write(context, portrait, DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, landscape, DuoOrientation.LANDSCAPE)
        DuoPrefs.writeHideStockIcons(context, true)
        DuoPrefs.writeCheckUpdates(context, false)
        DuoPrefs.writeRootAllowed(context, true)
        DuoPrefs.writeDump(context, "DUMP")
        val revBefore = DuoPrefs.revision(context)

        val json = DuoPrefs.backupJson(context)

        // overwrite
        DuoPrefs.write(context, DuoSettings(enabled = false, sizePercent = 100), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = true, sizePercent = 100), DuoOrientation.LANDSCAPE)
        DuoPrefs.writeHideStockIcons(context, false)
        DuoPrefs.writeCheckUpdates(context, true)

        val result = DuoPrefs.restoreJson(context, json)
        assertTrue(result.success)
        assertTrue(result.restored > 0)
        assertNull(result.error)

        val p = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val l = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertEquals(true, p.enabled)
        assertEquals(false, p.useRive)
        assertEquals(150, p.sizePercent)
        assertEquals(-12, p.offsetX)
        assertEquals(true, p.hideOtherIcons)
        assertEquals("alarm,battery", p.hiddenStockIcons)
        assertEquals(false, p.showInQs)
        assertEquals(true, p.burnInEnabled)

        assertEquals(false, l.enabled)
        assertEquals(80, l.sizePercent)
        assertEquals(-5, l.offsetX)
        assertEquals(false, l.hideOtherIcons)
        assertEquals("clock", l.hiddenStockIcons)
        assertEquals(false, l.burnInEnabled)

        assertTrue(DuoPrefs.hideStockIcons(context))
        assertFalse(DuoPrefs.checkUpdates(context))

        assertTrue(DuoPrefs.revision(context) > revBefore)
        assertTrue(DuoPrefs.rootAllowed(context))
        assertEquals("DUMP", DuoPrefs.dump(context))
    }

    @Test
    fun `old physical sizes in a backup remain valid and are not reset`() {
        val json = """{"schema":"duo-settings/1","version":1,"exportedAt":1,"values":{"size_percent":200,"land_size_percent":50,"landscape_set":true}}"""
        val result = DuoPrefs.restoreJson(context, json)
        assertTrue(result.success)
        assertEquals(200, DuoPrefs.read(context, DuoOrientation.PORTRAIT).sizePercent)
        assertEquals(50, DuoPrefs.read(context, DuoOrientation.LANDSCAPE).sizePercent)
    }

    @Test
    fun `restore clamps out of range values`() {
        val json = """{"schema":"duo-settings/1","version":1,"exportedAt":1,"values":{"enabled":true,"size_percent":999,"land_size_percent":50}}"""
        // Note: land_size_percent alone won't set landscape_set; but restore writes landscape anyway.
        val result = DuoPrefs.restoreJson(context, json)
        assertTrue(result.success)
        val p = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertEquals(DuoPrefs.MAX_SIZE, p.sizePercent)
        assertEquals(220, p.sizePercent)
    }

}
