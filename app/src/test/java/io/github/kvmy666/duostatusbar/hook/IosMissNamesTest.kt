package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C: the bounded miss-name table is pure (no Android calls), so a plain JUnit test covers the
 * first-sight return, the count, the cap and the exact diagnostic line.
 */
class IosMissNamesTest {

    @Test
    fun `the first sight returns the name and a repeat only increments the count`() {
        val misses = IosMissNames()
        assertEquals("stat_sys_signal_null", misses.record(0x7f010001) { "stat_sys_signal_null" })
        // Only the first sight returns the name; a repeat is counted and returns null.
        assertNull(misses.record(0x7f010001) { "ignored" })
        assertEquals(listOf("stat_sys_signal_null" to 2), misses.top(8))
    }

    @Test
    fun `top sorts by count descending then name`() {
        val misses = IosMissNames()
        misses.record(1) { "b" }
        misses.record(2) { "a" }
        misses.record(2) { "a" }
        misses.record(3) { "c" }
        misses.record(3) { "c" }
        misses.record(3) { "c" }

        assertEquals(listOf("c" to 3, "a" to 2), misses.top(2))
        assertEquals(listOf("c" to 3, "a" to 2, "b" to 1), misses.top(8))
    }

    @Test
    fun `the table stops claiming slots at max`() {
        val misses = IosMissNames(max = 2)
        assertEquals("a", misses.record(1) { "a" })
        assertEquals("b", misses.record(2) { "b" })
        // A third distinct id is dropped without claiming a slot.
        assertNull(misses.record(3) { "c" })
        assertEquals(listOf("a" to 1, "b" to 1), misses.top(8))
    }

    @Test
    fun `a null or blank name is claimed but never appears in top`() {
        val misses = IosMissNames()
        assertNull(misses.record(1) { null })
        assertEquals("", misses.record(2) { "" })
        assertTrue(misses.top(8).isEmpty())
        assertEquals(0, misses.top(0).size)
    }

    @Test
    fun `the miss top line is exactly the documented shape`() {
        assertEquals(
            "ios v2 miss top: family=samsung names=a,b counts=3,1",
            missTopLine("samsung", listOf("a" to 3, "b" to 1))
        )
    }
}
