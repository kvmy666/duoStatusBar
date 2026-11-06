package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home ring-design selector writes [DuoSettings.designStyle] and nothing else, so the
 * Default/Compact/Custom selection and the Custom snapshot stay where the user left them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingDesignTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `choosing 2 SIM writes designStyle`() {
        val before = DuoSettings(
            enabled = true,
            sizePercent = 150,
            splitIndicators = false,
            designStyle = DuoPrefs.DESIGN_OLD
        )
        DuoPrefs.write(context, before, DuoOrientation.PORTRAIT)

        val updated = writeRingDesign(context, before, DuoPrefs.DESIGN_NEW)

        assertEquals(DuoPrefs.DESIGN_NEW, updated.designStyle)
        assertEquals(before, updated.copy(designStyle = DuoPrefs.DESIGN_OLD))
        val stored = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertEquals(DuoPrefs.DESIGN_NEW, stored.designStyle)
        assertEquals(150, stored.sizePercent)
    }

    @Test
    fun `choosing Classic writes designStyle`() {
        val before = DuoSettings(enabled = true, designStyle = DuoPrefs.DESIGN_NEW)
        DuoPrefs.write(context, before, DuoOrientation.PORTRAIT)

        val updated = writeRingDesign(context, before, DuoPrefs.DESIGN_OLD)

        assertEquals(DuoPrefs.DESIGN_OLD, updated.designStyle)
        assertEquals(before, updated.copy(designStyle = DuoPrefs.DESIGN_NEW))
        assertEquals(DuoPrefs.DESIGN_OLD, DuoPrefs.read(context, DuoOrientation.PORTRAIT).designStyle)
    }
}
