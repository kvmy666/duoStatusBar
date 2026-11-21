package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure backoff behind the anti-thrash guards (IOS-LAG). JVM only. */
class ThrashGuardTest {

    @Test
    fun `trips exactly at the event limit`() {
        val guard = ThrashGuard(windowMs = 10_000L, maxEvents = 8)
        for (i in 0 until 7) {
            assertFalse("event ${i + 1} must not trip", guard.record(1_000L + i))
            assertEquals(i + 1, guard.count())
        }
        assertEquals("7 event(s) in 10s", guard.describe())
        assertTrue("the eighth event trips", guard.record(1_007L))
        assertEquals(8, guard.count())
        assertTrue(guard.isTripped())
        assertEquals("8 event(s) in 10s (tripped)", guard.describe())
    }

    @Test
    fun `stays tripped without counting`() {
        val guard = ThrashGuard(windowMs = 10_000L, maxEvents = 3)
        repeat(3) { guard.record(10L) }
        assertTrue(guard.isTripped())
        assertEquals(3, guard.count())

        assertTrue(guard.record(11L))
        assertTrue(guard.record(20_000L))
        assertEquals("a tripped guard does not count", 3, guard.count())
    }

    @Test
    fun `older events expire after the window`() {
        val guard = ThrashGuard(windowMs = 10_000L, maxEvents = 8)
        repeat(5) { guard.record(100L) }
        assertFalse(guard.isTripped())
        assertEquals(5, guard.count())

        // At the boundary the window restarts: only this event is in the fresh window.
        assertFalse(guard.record(10_100L))
        assertEquals(1, guard.count())
        assertFalse(guard.isTripped())
    }

    @Test
    fun `the boundary starts a new window`() {
        val guard = ThrashGuard(windowMs = 10_000L, maxEvents = 8)
        repeat(7) { guard.record(0L) }
        assertEquals(7, guard.count())

        // Just before the boundary the eighth event still trips.
        assertTrue(guard.record(9_999L))
        assertTrue(guard.isTripped())

        val fresh = ThrashGuard(windowMs = 10_000L, maxEvents = 8)
        repeat(7) { fresh.record(0L) }
        assertFalse("at the boundary the stale window is dropped", fresh.record(10_000L))
        assertEquals(1, fresh.count())
        assertFalse(fresh.isTripped())
    }

    @Test
    fun `reset clears the window and the trip`() {
        val guard = ThrashGuard(windowMs = 10_000L, maxEvents = 2)
        assertFalse(guard.record(5L))
        assertTrue(guard.record(6L))
        assertTrue(guard.isTripped())

        guard.reset()
        assertFalse(guard.isTripped())
        assertEquals(0, guard.count())
        assertEquals("0 event(s) in 10s", guard.describe())
        assertFalse("the first event after reset opens a fresh window", guard.record(6L))
        assertEquals(1, guard.count())
    }
}
