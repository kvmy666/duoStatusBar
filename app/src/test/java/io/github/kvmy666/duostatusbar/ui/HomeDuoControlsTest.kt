package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W4 Home side: the Duo cards follow [duoControlsEnabled], and the three Home writes are defensively
 * refused while iOS mode owns the bar. The OFF path must stay identical to the master switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDuoControlsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `duo controls follow the master switch and only an available iOS mode`() {
        // The drawing is available: an ON owns the bar and stops every Duo control.
        assertFalse(duoControlsEnabled(DuoSettings(enabled = true, iosMode = true)))
        assertFalse(duoControlsEnabled(DuoSettings(enabled = false, iosMode = true)))
        // The explicit seam proves the same matrix.
        assertFalse(duoControlsEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = true))
        assertTrue(duoControlsEnabled(DuoSettings(enabled = true, iosMode = false), iosAvailable = true))
        assertFalse(duoControlsEnabled(DuoSettings(enabled = false, iosMode = false), iosAvailable = true))
    }

    @Test
    fun `off path is exactly the master switch`() {
        for (enabled in listOf(true, false)) {
            assertEquals(
                enabled,
                duoControlsEnabled(DuoSettings(enabled = enabled, iosMode = false))
            )
        }
    }

    @Test
    fun `camera write is refused while iOS mode is on`() {
        val seeded = seed(DuoSettings(enabled = true, iosMode = true, lockRing = false))

        val result = writeCameraMode(context, seeded, true)

        assertSame(seeded, result)
        assertFalse(result.lockRing)
        assertFalse(DuoPrefs.read(context, DuoOrientation.PORTRAIT).lockRing)
    }

    @Test
    fun `an unsupported ios mode does not lock the duo home controls`() {
        assertTrue(
            "Duo owns the bar when the report says iOS cannot",
            duoControlsEnabled(DuoSettings(enabled = true, iosMode = true), iosAvailable = false)
        )
        assertFalse(
            "the Duo master switch still controls the Home cards",
            duoControlsEnabled(DuoSettings(enabled = false, iosMode = true), iosAvailable = false)
        )
    }

    @Test
    fun `a write is allowed when the ios mode cannot own the bar`() {
        val seeded = seed(DuoSettings(enabled = true, iosMode = true, lockRing = false))

        val camera = writeCameraMode(context, seeded, true, iosAvailable = false)
        val invert = writeInvertCenter(
            context,
            camera,
            on = true,
            iosAvailable = false
        )

        assertTrue(camera.lockRing)
        assertEquals(DuoPrefs.MIDDLE_SLOT_PERCENT, invert.middleSlot)
        assertTrue(DuoPrefs.read(context, DuoOrientation.PORTRAIT).lockRing)
        assertEquals(
            DuoPrefs.MIDDLE_SLOT_PERCENT,
            DuoPrefs.read(context, DuoOrientation.PORTRAIT).middleSlot
        )
    }

    @Test
    fun `ring design write is refused while iOS mode is on`() {
        val seeded = seed(
            DuoSettings(enabled = true, iosMode = true, designStyle = DuoPrefs.DESIGN_OLD)
        )

        val result = writeRingDesign(context, seeded, DuoPrefs.DESIGN_NEW)

        assertSame(seeded, result)
        assertEquals(DuoPrefs.DESIGN_OLD, result.designStyle)
        assertEquals(
            DuoPrefs.DESIGN_OLD,
            DuoPrefs.read(context, DuoOrientation.PORTRAIT).designStyle
        )
    }

    @Test
    fun `invert center write is refused while iOS mode is on`() {
        val seeded = seed(
            DuoSettings(enabled = true, iosMode = true, middleSlot = DuoPrefs.MIDDLE_SLOT_AUTO)
        )

        val result = writeInvertCenter(context, seeded, true)

        assertSame(seeded, result)
        assertEquals(DuoPrefs.MIDDLE_SLOT_AUTO, result.middleSlot)
        assertEquals(
            DuoPrefs.MIDDLE_SLOT_AUTO,
            DuoPrefs.read(context, DuoOrientation.PORTRAIT).middleSlot
        )
    }

    /** Writes the model and reads it back, so the test starts from the stored (normalised) instance. */
    private fun seed(settings: DuoSettings): DuoSettings {
        DuoPrefs.write(context, settings, DuoOrientation.PORTRAIT)
        return DuoPrefs.read(context, DuoOrientation.PORTRAIT)
    }
}
