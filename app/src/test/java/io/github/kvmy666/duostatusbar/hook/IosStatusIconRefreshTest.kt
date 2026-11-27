package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C: the refresh core is top-level pure Kotlin, so a plain JUnit test never initialises Android.
 *
 * The measured problem (u1127 dump :1058) is that the map was published after 94 icon loads and the
 * bar did not reload, so the views kept the stock drawables. This test reproduces the loader order:
 * both icons load stock, then the map is ready and only the mapped/signal target reloads.
 */
class IosStatusIconRefreshTest {

    @Test
    fun `signal slots are wifi ethernet mobile and data`() {
        for (slot in listOf("wifi", "ethernet", "mobile", "mobile2", "data", "data1", "WIFI", "Mobile")) {
            assertTrue(slot, isSignalSlot(slot))
        }
        for (slot in listOf("notifications", "battery", "clock", "")) {
            assertFalse(slot, isSignalSlot(slot))
        }
        assertFalse(isSignalSlot(null))
    }

    @Test
    fun `only mapped or signal targets are refreshed and both counters add up`() {
        val refreshed = ArrayList<String>()
        val result = refreshTargets(
            listOf(
                RefreshTarget("notification", mapped = true) { refreshed.add("mapped"); true },
                RefreshTarget("mobile", mapped = false) { refreshed.add("signal"); true },
                RefreshTarget("clock", mapped = false) { refreshed.add("skipped"); true }
            )
        )

        assertEquals(RefreshResult(icons = 2, reloaded = 2, failed = 0), result)
        assertEquals(listOf("mapped", "signal"), refreshed)
    }

    @Test
    fun `a thrown reload counts failed and does not stop later targets`() {
        val after = ArrayList<String>()
        val result = refreshTargets(
            listOf(
                RefreshTarget("wifi", mapped = true) { error("boom") },
                RefreshTarget("data", mapped = true) { after.add("second"); true }
            )
        )

        assertEquals(RefreshResult(icons = 2, reloaded = 1, failed = 1), result)
        assertEquals(listOf("second"), after)
    }

    @Test
    fun `a glyph outside a signal slot is refreshed during the stock pass`() {
        // The view still holds one of our drawables (possibly wrapped), so the stock pass must reload it.
        val refreshed = ArrayList<String>()
        val result = refreshTargets(
            listOf(
                RefreshTarget("custom", mapped = false, glyph = true) { refreshed.add("glyph"); true }
            )
        )

        assertEquals(RefreshResult(icons = 1, reloaded = 1, failed = 0), result)
        assertEquals(listOf("glyph"), refreshed)
    }

    @Test
    fun `force refreshes every status target as a last resort`() {
        // No slot name, no mapped id and no glyph: only a forced pass may touch them.
        val refreshed = ArrayList<String>()
        val result = refreshTargets(
            listOf(
                RefreshTarget("custom", mapped = false) { refreshed.add("first"); true },
                RefreshTarget(null, mapped = false) { refreshed.add("second"); true }
            ),
            force = true
        )

        assertEquals(RefreshResult(icons = 2, reloaded = 2, failed = 0), result)
        assertEquals(listOf("first", "second"), refreshed)
    }

    @Test
    fun `a refresh after the map is ready replaces a previously stock icon`() {
        // A loader that served stock before the map existed and glyphs afterwards.
        class FakeLoader {
            var ready = false
            fun load(): String = if (ready) "glyph" else "stock"
        }

        val loader = FakeLoader()
        // Both icons load while the map is still being built (callsBeforeMap in the dump).
        var wifi = loader.load()
        var notification = loader.load()
        assertEquals("stock", wifi)
        assertEquals("stock", notification)

        loader.ready = true
        val result = refreshTargets(
            listOf(
                // The mapped Wi-Fi icon reloads its drawable through updateDrawable(true).
                RefreshTarget("wifi", mapped = true) { wifi = loader.load(); true },
                // An unmapped notification icon is not a signal slot, so it is left alone.
                RefreshTarget("notification", mapped = false) { notification = loader.load(); true }
            )
        )

        assertEquals("glyph", wifi)
        assertEquals("stock", notification)
        assertEquals(1, result.icons)
        assertEquals(1, result.reloaded)
        assertEquals(0, result.failed)
    }
}
