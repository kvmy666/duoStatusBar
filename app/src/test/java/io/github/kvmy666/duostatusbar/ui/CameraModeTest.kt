package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home Camera switch is its own state: it writes [DuoSettings.lockRing] and nothing else, so the
 * Default/Compact/Custom selection and the Custom snapshot stay where the user left them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `camera on writes lockRing`() {
        val before = DuoSettings(enabled = true, sizePercent = 150, splitIndicators = false, lockRing = false)
        DuoPrefs.write(context, before, DuoOrientation.PORTRAIT)

        val updated = writeCameraMode(context, before, true)

        assertTrue(updated.lockRing)
        assertEquals(before, updated.copy(lockRing = false))
        val stored = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertTrue(stored.lockRing)
        assertFalse(stored.splitIndicators)
        assertEquals(150, stored.sizePercent)
    }

    @Test
    fun `camera off only clears lockRing`() {
        val before = DuoSettings(enabled = true, splitIndicators = true, lockRing = true)
        DuoPrefs.write(context, before, DuoOrientation.PORTRAIT)

        val updated = writeCameraMode(context, before, false)

        assertFalse(updated.lockRing)
        assertTrue(updated.splitIndicators)
        val stored = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertFalse(stored.lockRing)
        assertTrue(stored.splitIndicators)
    }
}
