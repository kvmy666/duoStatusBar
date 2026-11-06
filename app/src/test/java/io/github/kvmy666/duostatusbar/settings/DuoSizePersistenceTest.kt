package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Persistence stays physical while the control is logical: an existing 176 % value must read back
 * unchanged and display as 74 %, and changing the design/engine or orientation must not rewrite it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoSizePersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `an existing physical size is stored and read unchanged`() {
        DuoPrefs.write(context, DuoSettings(sizePercent = 176), DuoOrientation.PORTRAIT)

        val read = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertEquals(176, read.sizePercent)
        assertEquals(74, DuoPrefs.physicalToLogical(read.sizePercent))
    }

    @Test
    fun `the logical control writes the physical value`() {
        val physical = DuoPrefs.logicalToPhysical(74)
        DuoPrefs.write(context, DuoSettings(sizePercent = physical), DuoOrientation.PORTRAIT)

        assertEquals(176, DuoPrefs.read(context, DuoOrientation.PORTRAIT).sizePercent)
        assertEquals(
            176,
            context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
                .getInt(DuoPrefs.COL_SIZE_PERCENT, -1)
        )
    }

    @Test
    fun `the same physical size survives a design or engine change`() {
        val physical = DuoPrefs.logicalToPhysical(74)
        DuoPrefs.write(
            context,
            DuoSettings(sizePercent = physical, engineMode = DuoPrefs.ENGINE_RIVE),
            DuoOrientation.PORTRAIT
        )

        val rive = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        DuoPrefs.write(
            context,
            rive.copy(engineMode = DuoPrefs.ENGINE_CANVAS, useRive = false),
            DuoOrientation.PORTRAIT
        )
        val canvas = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertEquals(physical, rive.sizePercent)
        assertEquals(physical, canvas.sizePercent)
        assertEquals(74, DuoPrefs.physicalToLogical(canvas.sizePercent))
    }

    @Test
    fun `the same physical size is used in both orientations`() {
        val physical = DuoPrefs.logicalToPhysical(74)
        DuoPrefs.write(context, DuoSettings(sizePercent = physical), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(sizePercent = physical), DuoOrientation.LANDSCAPE)

        assertEquals(physical, DuoPrefs.read(context, DuoOrientation.PORTRAIT).sizePercent)
        assertEquals(physical, DuoPrefs.read(context, DuoOrientation.LANDSCAPE).sizePercent)
    }
}
