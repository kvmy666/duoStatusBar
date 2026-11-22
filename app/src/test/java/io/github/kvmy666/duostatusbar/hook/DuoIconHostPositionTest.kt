package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R4 item 8: the horizontal seat is one absolute value, never an accumulating delta.
 *
 * These tests drive the real [applySeat] write seam that [DuoIconHost.place] uses, on a real
 * Robolectric [View], so they prove the `translationX` write itself cannot drift — not only the pure
 * [seatX] formula.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoIconHostPositionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `reapplying the same state a hundred times leaves the same translation`() {
        val view = View(context)
        repeat(100) { applySeat(view, 41.5f, 1f, 12, rtl = false) }
        assertEquals(41.5f + 1f + 12f, view.translationX, 0f)
    }

    @Test
    fun `a fold or unfold recomputes a new absolute seat and never adds it`() {
        val view = View(context)
        val viewWidth = 40
        val holdPx = 10
        // The same right-edge formula place() uses; only the parent width changes (fold vs unfold).
        fun seatFor(parentWidth: Int): Float =
            normalSeatTranslationX(parentWidth, viewWidth, 0, 0, rtl = false).toFloat()

        repeat(50) { applySeat(view, seatFor(400), 0f, holdPx, rtl = false) }
        assertEquals("the folded seat did not drift", 360f + holdPx, view.translationX, 0f)

        applySeat(view, seatFor(300), 0f, holdPx, rtl = false)
        assertEquals("the new width produces the new absolute seat", 260f + holdPx, view.translationX, 0f)
        assertNotEquals(
            "the hold is not added to the previous translation",
            360f + holdPx + holdPx,
            view.translationX,
            0f
        )
    }

    @Test
    fun `an RTL flip mirrors only the hold`() {
        val view = View(context)
        applySeat(view, 100f, 3f, 12, rtl = false)
        assertEquals(115f, view.translationX, 0f)

        applySeat(view, 100f, 3f, 12, rtl = true)
        assertEquals("seat and burn-in are unchanged; only the hold flips", 91f, view.translationX, 0f)
    }

    @Test
    fun `burn-in is folded in, not accumulated`() {
        val view = View(context)
        repeat(100) { applySeat(view, 20f, 1f, 0, rtl = false) }
        assertEquals("a hundred reapplies move by exactly the burn-in nudge", 21f, view.translationX, 0f)

        applySeat(view, 20f, 0f, 0, rtl = false)
        assertEquals("the next burn-in hour restores the base seat", 20f, view.translationX, 0f)
    }
}
