package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * R4 item 8: the horizontal seat is one absolute value, never an accumulating delta.
 *
 * The old code wrote an absolute translation in `place()` and then `translationX += hold` at the
 * cluster call sites, so every reapply added the hold again. [seatX] is the single pure seat the
 * placement now writes.
 */
class DuoIconHostPositionTest {

    @Test
    fun `reapplying the same state a hundred times gives the identical seat`() {
        val expected = seatX(41.5f, 1f, 12, rtl = false)
        repeat(100) {
            assertEquals(expected, seatX(41.5f, 1f, 12, rtl = false))
        }
    }

    @Test
    fun `a fold or unfold recomputes a new absolute seat and never adds it`() {
        // Model the one place() writes: each pass assigns, it does not add.
        var translation = 0f
        fun apply(seat: Float) {
            translation = seatX(seat, 0f, 10, rtl = false)
        }
        apply(100f)
        repeat(50) { apply(100f) }
        assertEquals("the folded seat did not drift", 110f, translation)

        apply(-40f)
        assertEquals("the unfolded seat is the new absolute value", -30f, translation)
        assertNotEquals("the width change is not added to the old seat", 110f + 10f, translation)
    }

    @Test
    fun `an RTL flip mirrors the hold`() {
        assertEquals(12f, seatX(0f, 0f, 12, rtl = false))
        assertEquals(-12f, seatX(0f, 0f, 12, rtl = true))
    }

    @Test
    fun `burn-in is folded in, not accumulated`() {
        var translation = 0f
        repeat(100) {
            translation = seatX(20f, 1f, 0, rtl = false)
        }
        assertEquals("a hundred reapplies move by exactly the burn-in nudge", 21f, translation)

        translation = seatX(20f, 0f, 0, rtl = false)
        assertEquals("the next burn-in hour restores the base seat", 20f, translation)
    }
}
