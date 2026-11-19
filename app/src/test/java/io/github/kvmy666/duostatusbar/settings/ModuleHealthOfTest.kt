package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ModuleHealthCheck.of reads both orientations so an unsaved landscape copy (which falls back to
 * portrait) cannot make a running module look switched off. The pure decide() rule is tested elsewhere;
 * this covers of()'s both-orientation OR and its order against the "never loaded" states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleHealthOfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Writes the two stamps the check reads, plus the status that keeps OK from becoming NOT_REPORTING. */
    private fun stampFresh(now: Long) {
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", now)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", now)
        DuoPrefs.writeStatus(context, "loaded")
    }

    @Test
    fun `one enabled orientation is not reported disabled`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = true), DuoOrientation.LANDSCAPE)

        assertNotEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `unsaved landscape falls back to the portrait switch`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(context, DuoSettings(enabled = true), DuoOrientation.PORTRAIT)

        assertNotEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `both orientations off is disabled`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.LANDSCAPE)

        assertEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `never loaded stays never loaded when off`() {
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", 0L)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", 0L)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.LANDSCAPE)

        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.of(context).state)
    }
}
