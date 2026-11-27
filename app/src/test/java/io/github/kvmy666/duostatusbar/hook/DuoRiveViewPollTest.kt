package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single late poll is offered exactly once, after the normal budget is spent. */
class DuoRiveViewPollTest {

    @Test
    fun `the one late poll is offered once after the normal budget`() {
        assertFalse(DuoRiveView.lateRetryDue(polls = 24, maxPolls = 25, lateTried = false))
        assertTrue(DuoRiveView.lateRetryDue(polls = 25, maxPolls = 25, lateTried = false))
        assertFalse(DuoRiveView.lateRetryDue(polls = 25, maxPolls = 25, lateTried = true))
        assertFalse(DuoRiveView.lateRetryDue(polls = 26, maxPolls = 25, lateTried = true))
    }
}
