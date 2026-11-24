package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3A A6: the QS and shade surfaces are optional. Turning one off must drop its live slot, and the
 * drop path restores that surface's stock icons (`DuoIconHost.dropSlot` calls `slot.stock.restore()`).
 * The rules live in [desiredSurfaces]/[surfacesToDrop], which the host and this test share.
 */
class SurfaceFeatureTest {

    private val both = ModuleSettings.DEFAULT.copy(showInQs = true, showInShade = true)
    private val bothLive = setOf(SURFACE_QS, SURFACE_SHADE)

    @Test
    fun `both surfaces are enabled by default`() {
        assertEquals(bothLive, desiredSurfaces(both))
        assertTrue(surfaceEnabled(SURFACE_QS, both))
        assertTrue(surfaceEnabled(SURFACE_SHADE, both))
    }

    @Test
    fun `turning QS off drops its live slot and keeps the shade`() {
        val qsOff = both.copy(showInQs = false)
        assertEquals(setOf(SURFACE_QS), surfacesToDrop(bothLive, qsOff))
        assertFalse(surfaceEnabled(SURFACE_QS, qsOff))
        assertTrue(surfaceEnabled(SURFACE_SHADE, qsOff))

        // Dropping the slot is what restores its stock icons; the host removes it from `extras`, so a
        // repeated settings apply cannot drop it twice.
        assertEquals(emptySet<String>(), surfacesToDrop(bothLive - SURFACE_QS, qsOff))
    }

    @Test
    fun `turning the shade off drops its live slot and keeps QS`() {
        val shadeOff = both.copy(showInShade = false)
        assertEquals(setOf(SURFACE_SHADE), surfacesToDrop(bothLive, shadeOff))
        assertFalse(surfaceEnabled(SURFACE_SHADE, shadeOff))
        assertTrue(surfaceEnabled(SURFACE_QS, shadeOff))
    }

    @Test
    fun `turning a surface on recreates exactly that slot`() {
        val qsOnlyLive = setOf(SURFACE_QS)
        assertEquals(emptySet<String>(), surfacesToDrop(qsOnlyLive, both))
    }

    @Test
    fun `both off drops both and creates none`() {
        val off = both.copy(showInQs = false, showInShade = false)
        assertTrue(desiredSurfaces(off).isEmpty())
        assertEquals(bothLive, surfacesToDrop(bothLive, off))
    }

    @Test
    fun `non-extra slots are never dropped by the surface switch`() {
        assertEquals(emptySet<String>(), surfacesToDrop(listOf("keyguard bar", "oneplus qs"), both))
        assertTrue(surfaceEnabled("keyguard bar", both.copy(showInQs = false, showInShade = false)))
    }
}
