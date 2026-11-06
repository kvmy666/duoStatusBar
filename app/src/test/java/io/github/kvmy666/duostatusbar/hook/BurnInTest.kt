package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class BurnInTest {

    @Test
    fun `disabled never shifts the element`() {
        assertEquals(0f, burnInShiftPx(enabled = false, epochHour = 0L), 0f)
        assertEquals(0f, burnInShiftPx(enabled = false, epochHour = 1L), 0f)
        assertEquals(0f, burnInShiftPx(enabled = false, epochHour = 4_900_000L), 0f)
    }

    @Test
    fun `odd wall clock hours shift one physical pixel left`() {
        assertEquals(-1f, burnInShiftPx(enabled = true, epochHour = 1L), 0f)
        assertEquals(-1f, burnInShiftPx(enabled = true, epochHour = 3L), 0f)
        assertEquals(-1f, burnInShiftPx(enabled = true, epochHour = 4_900_001L), 0f)
    }

    @Test
    fun `even wall clock hours return to the normal seat`() {
        assertEquals(0f, burnInShiftPx(enabled = true, epochHour = 0L), 0f)
        assertEquals(0f, burnInShiftPx(enabled = true, epochHour = 2L), 0f)
        assertEquals(0f, burnInShiftPx(enabled = true, epochHour = 4_900_000L), 0f)
    }

    @Test
    fun `the epoch hour is the wall clock divided by one hour`() {
        assertEquals(3_600_000L, BURN_IN_HOUR_MS)
        assertEquals(1L, 3_600_000L / BURN_IN_HOUR_MS)
        assertEquals(0L, 3_599_999L / BURN_IN_HOUR_MS)
    }
}
