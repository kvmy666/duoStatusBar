package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The mode label is the fix for the field reports that said "DISABLED" while iOS owned the bar.
 * iOS active with the Duo switch off is healthy; only both switches off is DISABLED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleHealthModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 10_000_000L
    private val fresh = now - 1_000L

    private fun stampFresh(at: Long = System.currentTimeMillis()) {
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", at)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", at)
        DuoPrefs.writeStatus(context, "loaded")
    }

    @Test
    fun `ios active with the duo switch off is ok and ios`() {
        assertEquals(
            ModuleState.OK,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosEnabled = true
            )
        )
        assertEquals(ModuleMode.IOS, ModuleHealthCheck.modeFor(duoEnabled = false, iosEnabled = true))
        assertEquals("iOS active", ModuleMode.IOS.label)
    }

    @Test
    fun `both off is disabled and off`() {
        assertEquals(
            ModuleState.DISABLED,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosEnabled = false
            )
        )
        assertEquals(ModuleMode.OFF, ModuleHealthCheck.modeFor(duoEnabled = false, iosEnabled = false))
        assertEquals("off", ModuleMode.OFF.label)
    }

    @Test
    fun `duo active is ok and duo`() {
        assertEquals(ModuleState.OK, ModuleHealthCheck.decide(200L, 100L, false, fresh, now, true))
        assertEquals(ModuleMode.DUO, ModuleHealthCheck.modeFor(duoEnabled = true, iosEnabled = false))
        assertEquals("Duo active", ModuleMode.DUO.label)
    }

    @Test
    fun `a stale heartbeat is never loaded even when ios is on`() {
        val stale = now - DuoPrefs.HEARTBEAT_STALE_MS - 1
        assertEquals(
            ModuleState.NEVER_LOADED,
            ModuleHealthCheck.decide(
                200L, 100L, false, stale, now,
                settingsEnabled = false,
                iosEnabled = true
            )
        )
    }

    @Test
    fun `of reports ios active while the duo switch is off`() {
        val at = System.currentTimeMillis()
        stampFresh(at)
        DuoPrefs.write(context, DuoSettings(enabled = false, iosMode = true), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = false, iosMode = true), DuoOrientation.LANDSCAPE)

        val health = ModuleHealthCheck.of(context)
        assertEquals(ModuleState.OK, health.state)
        assertEquals(ModuleMode.IOS, health.mode)
    }

    @Test
    fun `of reports off when both switches are off`() {
        stampFresh()
        DuoPrefs.write(context, DuoSettings(enabled = false, iosMode = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = false, iosMode = false), DuoOrientation.LANDSCAPE)

        val health = ModuleHealthCheck.of(context)
        assertEquals(ModuleState.DISABLED, health.state)
        assertEquals(ModuleMode.OFF, health.mode)
    }
}
