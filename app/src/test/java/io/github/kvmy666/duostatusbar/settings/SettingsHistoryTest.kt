package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The last-change history is a bounded, pure record written only from [DuoPrefs.write]: a report can
 * say when the settings last changed and which keys moved, instead of "not recorded" forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsHistoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `changed keys are bounded to twelve and keep declaration order`() {
        val next = DuoSettings(
            enabled = true,
            useRive = false,
            showPercent = false,
            sizePercent = 120,
            offsetX = 5,
            percentHeight = 0,
            splitIndicators = true,
            indicatorsOffsetX = 1,
            lockRing = true,
            liveApply = false,
            systemClockFont = false,
            revealMs = 500,
            animationsEnabled = false,
            arrivalEnabled = false
        )
        val keys = SettingsHistory.changedKeys(DuoSettings(), next)
        assertEquals(SettingsHistory.MAX_KEYS, keys.size)
        assertEquals("enabled", keys.first())
        assertEquals(12, SettingsHistory.MAX_KEYS)
    }

    @Test
    fun `a landscape record prefixes its keys and keeps the timestamp`() {
        val record = SettingsHistory.record(
            DuoSettings(),
            DuoSettings(enabled = true, iosMode = true),
            at = 1234L,
            orientation = DuoOrientation.LANDSCAPE
        )
        assertEquals(1234L, record.at)
        assertTrue(record.keys.contains("land_enabled"))
        assertTrue(record.keys.contains("land_ios_mode"))
    }

    @Test
    fun `no history yet reads as null`() {
        assertNull(SettingsHistory.read(context))
    }

    @Test
    fun `write then read round trips through the settings store`() {
        val record = SettingsHistory.Record(99L, listOf("enabled", "ios_mode"))
        SettingsHistory.write(context, record)
        val read = SettingsHistory.read(context)
        assertNotNull(read)
        assertEquals(99L, read!!.at)
        assertEquals(listOf("enabled", "ios_mode"), read.keys)
    }

    @Test
    fun `duo prefs write records exactly the changed keys`() {
        // A first install is already enabled (owner decision), so start from an existing store whose
        // stored value is false; the write then genuinely changes `enabled` and the history says so.
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit()
            .putBoolean(DuoPrefs.COL_ENABLED, false)
            .commit()
        DuoPrefs.write(context, DuoSettings(enabled = true), DuoOrientation.PORTRAIT)
        val record = SettingsHistory.read(context)
        assertNotNull(record)
        assertTrue(record!!.at > 0L)
        assertTrue(record.keys.contains("enabled"))
    }

    @Test
    fun `render shows the timestamp keys and revision`() {
        val rendered = SettingsHistory.render(
            SettingsHistory.Record(1L, listOf("enabled", "ios_mode")),
            revision = 9L
        ) { "2026-01-01 00:00:00" }
        assertEquals("2026-01-01 00:00:00 keys=enabled,ios_mode rev=9", rendered)
    }

    @Test
    fun `an absent record says the build predates history`() {
        assertEquals(
            SettingsHistory.ABSENT,
            SettingsHistory.render(null, revision = 9L) { "ignored" }
        )
    }
}
