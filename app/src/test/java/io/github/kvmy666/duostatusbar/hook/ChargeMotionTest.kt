package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeMotionTest {
    @Test
    fun shakeOnlyWhenChargingCrossesIntoTwenty() {
        assertTrue(ChargeMotion.shouldShake(previousLevel = 19, targetLevel = 20, charging = true))
        assertTrue(ChargeMotion.shouldShake(previousLevel = 19, targetLevel = 21, charging = true))
        assertFalse(ChargeMotion.shouldShake(previousLevel = 20, targetLevel = 20, charging = true))
        assertFalse(ChargeMotion.shouldShake(previousLevel = 20, targetLevel = 21, charging = true))
        assertFalse(ChargeMotion.shouldShake(previousLevel = 19, targetLevel = 20, charging = false))
        assertFalse(ChargeMotion.shouldShake(previousLevel = 19, targetLevel = 40, charging = true))
    }

    @Test
    fun settlePulseFollowsTheRiveTimeline() {
        assertTrue(ChargeMotion.SETTLE_END_MS > ChargeMotion.SETTLE_START_MS)
        assertTrue(ChargeMotion.SHAKE_MS in 100L..300L)
    }
}
