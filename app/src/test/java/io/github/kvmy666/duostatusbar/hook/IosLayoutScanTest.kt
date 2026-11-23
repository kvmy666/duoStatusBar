package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R6 item 5: the per-layout inventory scan throttle. */
class IosLayoutScanTest {

    @Test
    fun `a deferred icon always scans`() {
        assertTrue(IosLayoutScan.shouldScan(deferredCount = 1, layoutsSinceScan = 0))
        assertTrue(IosLayoutScan.shouldScan(deferredCount = 3, layoutsSinceScan = 7))
    }

    @Test
    fun `no deferred icon scans every eighth layout only`() {
        assertFalse(IosLayoutScan.shouldScan(deferredCount = 0, layoutsSinceScan = 0))
        assertFalse(IosLayoutScan.shouldScan(deferredCount = 0, layoutsSinceScan = 7))
        assertTrue(IosLayoutScan.shouldScan(deferredCount = 0, layoutsSinceScan = 8))
        assertTrue(IosLayoutScan.shouldScan(deferredCount = 0, layoutsSinceScan = 9))
    }
}
