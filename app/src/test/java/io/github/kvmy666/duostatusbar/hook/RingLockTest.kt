package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Camera/Compact lock the ring with `lockRing` alone. Split mode only moves the inner icons out; it must
 * never be a precondition for the ring leaving the icon strip.
 */
class RingLockTest {

    @Test
    fun `camera locks the ring without split`() {
        val camera = ModuleSettings.DEFAULT.copy(lockRing = true, splitIndicators = false)
        assertTrue(camera.ringLocked())
    }

    @Test
    fun `split with lock keeps the pinned ring`() {
        val splitLock = ModuleSettings.DEFAULT.copy(lockRing = true, splitIndicators = true)
        assertTrue(splitLock.ringLocked())
    }

    @Test
    fun `split alone does not lock the ring`() {
        val split = ModuleSettings.DEFAULT.copy(lockRing = false, splitIndicators = true)
        assertFalse(split.ringLocked())
    }

    @Test
    fun `camera off leaves the ring in the strip`() {
        val off = ModuleSettings.DEFAULT.copy(lockRing = false, splitIndicators = false)
        assertFalse(off.ringLocked())
    }
}
