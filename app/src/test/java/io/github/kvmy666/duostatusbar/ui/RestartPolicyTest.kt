package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The restart handshake, decided without Android.
 *
 * The device log for the safe-mode loop showed why each rule exists: the app gave up after 2.5 s while
 * the new SystemUI was still installing, and the root fallback then killed that fresh process. These
 * tests pin the three guards — always wait for a stamp, debounce repeated taps, and never kill a young
 * SystemUI — so they cannot silently regress.
 */
class RestartPolicyTest {

    @Test
    fun `before zero must still wait and a positive stamp counts`() {
        var now = 0L
        var reads = 0
        val sleeps = mutableListOf<Long>()
        val reloaded = RestartPolicy.waitForReload(
            before = 0L,
            readStamp = {
                reads++
                if (reads >= 4) 123L else 0L
            },
            now = { now },
            timeoutMs = RestartPolicy.RELOAD_WAIT_MS,
            sleep = { ms ->
                sleeps += ms
                now += ms
            }
        )
        assertTrue("a stamp > 0 must count when before == 0", reloaded)
        assertTrue("before == 0 must not skip the wait", sleeps.isNotEmpty())
    }

    @Test
    fun `an existing positive stamp ends a before-zero wait immediately`() {
        var sleeps = 0
        val reloaded = RestartPolicy.waitForReload(
            before = 0L,
            readStamp = { 42L },
            now = { 0L },
            timeoutMs = RestartPolicy.RELOAD_WAIT_MS,
            sleep = { sleeps++ }
        )
        assertTrue(reloaded)
        assertEquals("the stamp is already newer than zero", 0, sleeps)
    }

    @Test
    fun `a stamp not newer than before never counts and the wait times out`() {
        var now = 0L
        var sleeps = 0
        val reloaded = RestartPolicy.waitForReload(
            before = 500L,
            readStamp = { 500L },
            now = { now },
            timeoutMs = 1_000L,
            sleep = { ms ->
                sleeps++
                now += ms
            }
        )
        assertFalse(reloaded)
        assertTrue("it must have actually waited", sleeps > 0)
    }

    @Test
    fun `a newer stamp ends the wait`() {
        var now = 0L
        var reads = 0
        val reloaded = RestartPolicy.waitForReload(
            before = 500L,
            readStamp = {
                reads++
                if (reads >= 3) 501L else 500L
            },
            now = { now },
            timeoutMs = RestartPolicy.RELOAD_WAIT_MS,
            sleep = { now += it }
        )
        assertTrue(reloaded)
    }

    @Test
    fun `the default reload wait is ten seconds`() {
        assertEquals(10_000L, RestartPolicy.RELOAD_WAIT_MS)
    }

    @Test
    fun `debounce ignores a repeat inside ten seconds`() {
        assertTrue(RestartPolicy.shouldDebounce(10_999L, 1_000L))
        assertEquals(10_000L, RestartPolicy.RESTART_DEBOUNCE_MS)
    }

    @Test
    fun `debounce allows the first request and one at or after ten seconds`() {
        assertFalse("no previous request", RestartPolicy.shouldDebounce(5_000L, 0L))
        assertFalse("exactly ten seconds later", RestartPolicy.shouldDebounce(11_000L, 1_000L))
        assertFalse("well after the window", RestartPolicy.shouldDebounce(30_000L, 1_000L))
    }

    @Test
    fun `youngRefused rejects a process younger than fifteen seconds`() {
        assertEquals(15L, RestartPolicy.MIN_SYSTEMUI_AGE_S)
        assertTrue(RestartPolicy.youngRefused(0L))
        assertTrue(RestartPolicy.youngRefused(14L))
        assertFalse(RestartPolicy.youngRefused(15L))
        assertFalse(RestartPolicy.youngRefused(120L))
    }

    @Test
    fun `youngRefused keeps an unknown age permissive`() {
        assertFalse("an unknown age must not block the fallback", RestartPolicy.youngRefused(null))
    }
}
