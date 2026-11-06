package io.github.kvmy666.duostatusbar.hook.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventArbiterTest {

    @Test
    fun `newest preempts alarm then flashlight`() {
        var now = 0L
        val arbiter = EventArbiter(clock = { now })
        arbiter.offer(LiveEventType.ALARM, source = "alarm")
        now = 10L
        arbiter.offer(LiveEventType.FLASHLIGHT, source = "torch")
        assertEquals(LiveEventType.FLASHLIGHT, arbiter.active?.type)
    }

    @Test
    fun `disabled event is ignored`() {
        var now = 0L
        val arbiter = EventArbiter(
            isEnabled = { it != LiveEventType.MIC },
            clock = { now },
        )
        arbiter.offer(LiveEventType.ALARM)
        val before = arbiter.active
        val disabledResult = arbiter.offer(LiveEventType.MIC)
        assertNull(disabledResult)
        assertEquals(before, arbiter.active)
        assertEquals(LiveEventType.ALARM, arbiter.active?.type)
    }

    @Test
    fun `transient expires at exact boundary while active persists`() {
        var now = 0L
        val arbiter = EventArbiter(clock = { now })
        arbiter.offer(LiveEventType.SCREENSHOT) // 1000ms transient
        // before boundary
        now = 999L
        assertEquals(LiveEventType.SCREENSHOT, arbiter.tick(now)?.type)
        // exact boundary expires
        now = 1000L
        assertNull(arbiter.tick(now))
        // while-active persists
        arbiter.offer(LiveEventType.FLASHLIGHT) // null duration
        now = 10_000L
        assertEquals(LiveEventType.FLASHLIGHT, arbiter.tick(now)?.type)
    }

    @Test
    fun `end type only matches and end clears`() {
        val arbiter = EventArbiter(clock = { 0L })
        arbiter.offer(LiveEventType.ALARM)
        arbiter.end(LiveEventType.FLASHLIGHT)
        assertEquals(LiveEventType.ALARM, arbiter.active?.type)
        arbiter.end(LiveEventType.ALARM)
        assertNull(arbiter.active)
        arbiter.offer(LiveEventType.VOLUME)
        arbiter.end()
        assertNull(arbiter.active)
    }

    @Test
    fun `same type replacement resets timer`() {
        var now = 0L
        val arbiter = EventArbiter(clock = { now })
        arbiter.offer(LiveEventType.SCREENSHOT, now = 0L) // ends 1000
        now = 500L
        arbiter.offer(LiveEventType.SCREENSHOT, now = 500L) // ends 1500
        now = 1000L
        assertEquals(LiveEventType.SCREENSHOT, arbiter.tick(now)?.type)
        now = 1500L
        assertNull(arbiter.tick(now))
    }

    @Test
    fun `priority field ordering sanity`() {
        assertTrue(LiveEventType.ALARM.priority > LiveEventType.FLASHLIGHT.priority)
        assertTrue(LiveEventType.FLASHLIGHT.priority > LiveEventType.SCREENSHOT.priority)
        assertTrue(LiveEventType.SCREENSHOT.priority > LiveEventType.MIC.priority)
        assertTrue(LiveEventType.MIC.priority > LiveEventType.LOCATION.priority)
        assertTrue(LiveEventType.LOCATION.priority > LiveEventType.VOLUME.priority)
        assertTrue(LiveEventType.VOLUME.priority > LiveEventType.UNLOCK.priority)
    }

    @Test
    fun `offer default duration follows type`() {
        val arbiter = EventArbiter(clock = { 0L })
        val event = arbiter.offer(LiveEventType.VOLUME)
        assertEquals(1200L, event?.durationMs)
        assertEquals(1200L, event?.endsAtMillis)
    }

    @Test
    fun `clear removes active`() {
        val arbiter = EventArbiter(clock = { 0L })
        arbiter.offer(LiveEventType.UNLOCK)
        arbiter.clear()
        assertNull(arbiter.active)
    }

    @Test
    fun `only dispatch type one is the flashlight state`() {
        assertEquals(true, flashlightStateFromDispatch(1, true))
        assertEquals(false, flashlightStateFromDispatch(1, false))
        // Availability (2), night-light (6) and error (0) must not become a torch event.
        assertNull(flashlightStateFromDispatch(2, true))
        assertNull(flashlightStateFromDispatch(6, true))
        assertNull(flashlightStateFromDispatch(0, false))
    }
}
