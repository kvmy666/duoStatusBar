package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R7 P5 end-to-end handler test: the same decision the `loadDrawable` hook makes, against a synthetic
 * snapshot and real framework resources.
 *
 * It covers the four outcomes that matter for system safety: a mapped id in its measured package is
 * replaced with the right glyph, an id from another package is left stock, an unknown id is left
 * stock, and our own drawable creation re-entering the hook is left stock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosResourceHandlerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun entry(id: Int, slot: IosResourceSlot, pkg: String = "android") =
        ResolvedEntry(id, slot, widthDp = 20f, heightDp = 16f, packageName = pkg)

    private fun snapshot(pkg: String = "android"): IosResourceEngine.Snapshot = buildSnapshot(
        listOf(
            entry(android.R.drawable.ic_delete, IosResourceSlot.Wifi(2), pkg),
            entry(android.R.drawable.ic_menu_add, IosResourceSlot.Cell(3), pkg),
            entry(android.R.drawable.ic_menu_info_details, IosResourceSlot.Label("5G"), pkg)
        ),
        baseDensity = 160,
        defaultTint = Color.BLACK
    )

    @Test
    fun `a mapped id in its package becomes the expected glyph`() {
        val snap = snapshot()
        val resources = context.resources

        val wifi = IosResourceEngine.handleForTest(
            snap, resources, android.R.drawable.ic_delete, 160, night = false
        ) as IosGlyphDrawable
        assertEquals(IosIconKind.WIFI, wifi.kind)
        assertEquals(2f, wifi.visualForTest!!.wifiLevel, 0.0001f)

        val cell = IosResourceEngine.handleForTest(
            snap, resources, android.R.drawable.ic_menu_add, 160, night = false
        ) as IosGlyphDrawable
        assertEquals(IosIconKind.CELL, cell.kind)
        assertEquals(3f, cell.visualForTest!!.cellBars, 0.0001f)

        val label = IosResourceEngine.handleForTest(
            snap, resources, android.R.drawable.ic_menu_info_details, 160, night = false
        ) as IosGlyphDrawable
        assertEquals(IosIconKind.WIFI, label.kind)
        assertEquals(IosWifiSlot.DataType("5G"), label.visualForTest!!.wifiSlot)
    }

    @Test
    fun `an id from another package is left stock`() {
        val snap = snapshot(pkg = "com.android.systemui")
        val result = IosResourceEngine.handleForTest(
            snap, context.resources, android.R.drawable.ic_delete, 160, night = false
        )
        assertNull(result)
    }

    @Test
    fun `an unknown id is left stock`() {
        val result = IosResourceEngine.handleForTest(
            snapshot(), context.resources, android.R.drawable.ic_menu_save, 160, night = false
        )
        assertNull(result)
    }

    @Test
    fun `our own creation re-entering the hook is left stock`() {
        val result = IosResourceEngine.handleForTest(
            snapshot(), context.resources, android.R.drawable.ic_delete, 160, night = false,
            guarded = true
        )
        assertNull(result)
        // The guard is thread-local and restored, so the very next call still replaces.
        assertTrue(
            IosResourceEngine.handleForTest(
                snapshot(), context.resources, android.R.drawable.ic_delete, 160, night = false
            ) is IosGlyphDrawable
        )
    }
}
