package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageRetryTest {

    @Test
    fun `first retry is unchanged at 1500 ms`() {
        assertEquals("attempt 1 must stay identical to the old budget", 1_500L, StageRetry.delayFor(0))
    }

    @Test
    fun `budget covers at least fifteen seconds`() {
        assertTrue("observed recovery was ~14 s after the first failure", StageRetry.budgetMs >= 15_000L)
        assertEquals(16_500L, StageRetry.budgetMs)
    }

    @Test
    fun `five retries are scheduled`() {
        assertEquals(5, StageRetry.retryCount)
    }

    @Test
    fun `every scheduled delay is positive and the next one is absent`() {
        var sum = 0L
        for (retry in 0 until StageRetry.retryCount) {
            val delay = StageRetry.delayFor(retry)
            assertNotNull("retry $retry must have a delay", delay)
            assertTrue("retry $retry delay must be > 0", delay!! > 0L)
            sum += delay
        }
        assertNull("no delay after the budget is exhausted", StageRetry.delayFor(StageRetry.retryCount))
        assertEquals("budget is the sum of every delay", StageRetry.budgetMs, sum)
    }
}
