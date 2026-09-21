package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app ↔ module channel is the newest surface and the one with the least device coverage, so its contract
 * is pinned here: the settings must survive a round trip through the provider *as the module reads them*, and
 * the provider must expose exactly the columns the module asks for.
 *
 * This is the test that catches the failure mode I cannot see from either side alone: the app writes
 * `size_percent`, the module reads `size_percent`, and a typo in one of those strings would otherwise be
 * discovered by a user whose size slider does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsChannelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `settings survive a write and a read`() {
        val written = DuoSettings(
            enabled = true,
            useRive = false,
            showPercent = false,
            sizePercent = 120,
            offsetX = -12,
            tapAction = "toggle_flashlight",
            doubleTapAction = "show_notifications",
            longPressAction = "take_screenshot"
        )
        DuoPrefs.write(context, written)
        assertEquals(written, DuoPrefs.read(context))
    }

    @Test
    fun `out of range values are clamped on write, not stored`() {
        DuoPrefs.write(context, DuoSettings(sizePercent = 9_999, offsetX = 9_999))
        val read = DuoPrefs.read(context)
        assertEquals(DuoPrefs.MAX_SIZE, read.sizePercent)
        assertEquals(DuoPrefs.MAX_OFFSET, read.offsetX)
    }

    @Test
    fun `the revision moves on every write so the module can notice changes`() {
        val first = DuoPrefs.write(context, DuoSettings(enabled = true))
        val second = DuoPrefs.write(context, DuoSettings(enabled = false))
        assertTrue("revision must increase", second > first)
    }

    @Test
    fun `the row the module reads has one value per column, in the declared order`() {
        val row = DuoSettingsProvider.rowFor(
            DuoSettings(
                enabled = true,
                useRive = false,
                showPercent = false,
                sizePercent = 130,
                offsetX = 7,
                tapAction = "toggle_flashlight",
                doubleTapAction = "no_action",
                longPressAction = "take_screenshot"
            ),
            revision = 42L
        )
        assertEquals(
            "a column added or reordered without the module being updated is the silent failure this guards",
            DuoPrefs.COLUMNS.size,
            row.size
        )
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_ENABLED)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_USE_RIVE)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_PERCENT)])
        assertEquals(130, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SIZE_PERCENT)])
        assertEquals(7, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_OFFSET_X)])
        assertEquals(42L, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_REVISION)])
        assertEquals("toggle_flashlight", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_TAP)])
        assertEquals("no_action", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DOUBLE_TAP)])
        assertEquals("take_screenshot", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_LONG_PRESS)])
        assertEquals(1000, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_REVEAL_MS)])
    }

    @Test
    fun `an arrival duration is snapped to one the Rive file can actually play`() {
        // The file holds one timeline at five speeds. Anything between two of them has to become one of
        // them, or the state machine would simply never fire and the element would never arrive.
        for (choice in DuoPrefs.REVEAL_CHOICES) {
            assertEquals(choice, DuoPrefs.nearestReveal(choice))
            assertEquals("just under $choice", choice, DuoPrefs.nearestReveal(choice - 1))
        }
        assertEquals(500, DuoPrefs.nearestReveal(0))
        assertEquals(1500, DuoPrefs.nearestReveal(99_999))
        // 624 is nearer 500 than 750; 626 tips the other way. The boundary is the midpoint.
        assertEquals(500, DuoPrefs.nearestReveal(624))
        assertEquals(750, DuoPrefs.nearestReveal(626))
    }

    @Test
    fun `status history keeps the newest reports and ignores repeats`() {
        DuoPrefs.writeStatus(context, "stage=1 renderer=Canvas")
        DuoPrefs.writeStatus(context, "stage=1 renderer=Canvas")   // repeat: ignored
        DuoPrefs.writeStatus(context, "stage=2 renderer=Rive")
        assertEquals("stage=2 renderer=Rive", DuoPrefs.status(context))
        val history = DuoPrefs.statusHistory(context)
        assertEquals("the repeat must not be stored twice", 2, history.size)
        assertTrue(history.last().endsWith("stage=2 renderer=Rive"))
    }
}
