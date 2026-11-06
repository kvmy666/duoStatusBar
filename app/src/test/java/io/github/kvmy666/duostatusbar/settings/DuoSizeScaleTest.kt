package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The size control shows a logical 0..100 scale while the module keeps the physical 50..220 value.
 * These tests pin the linear mapping and both clamps so a slider value can never leave that contract.
 */
class DuoSizeScaleTest {

    @Test
    fun `logical bounds map to the physical bounds`() {
        assertEquals(50, DuoPrefs.logicalToPhysical(0))
        assertEquals(220, DuoPrefs.logicalToPhysical(100))
    }

    @Test
    fun `mapping is linear at the midpoint`() {
        assertEquals(135, DuoPrefs.logicalToPhysical(50))
    }

    @Test
    fun `existing physical 176 displays as logical 74`() {
        assertEquals(74, DuoPrefs.physicalToLogical(176))
        assertEquals(176, DuoPrefs.logicalToPhysical(74))
    }

    @Test
    fun `old physical range 50 to 200 stays valid`() {
        assertEquals(0, DuoPrefs.physicalToLogical(50))
        assertEquals(88, DuoPrefs.physicalToLogical(200))
    }

    @Test
    fun `every logical step round-trips through the physical value`() {
        for (logical in DuoPrefs.MIN_LOGICAL_SIZE..DuoPrefs.MAX_LOGICAL_SIZE) {
            assertEquals(
                "logical=$logical",
                logical,
                DuoPrefs.physicalToLogical(DuoPrefs.logicalToPhysical(logical))
            )
        }
    }

    @Test
    fun `logical input outside 0 to 100 is clamped`() {
        assertEquals(50, DuoPrefs.logicalToPhysical(-1))
        assertEquals(220, DuoPrefs.logicalToPhysical(101))
        assertEquals(50, DuoPrefs.logicalToPhysical(Int.MIN_VALUE))
        assertEquals(220, DuoPrefs.logicalToPhysical(Int.MAX_VALUE))
    }

    @Test
    fun `physical input outside 50 to 220 is clamped`() {
        assertEquals(0, DuoPrefs.physicalToLogical(49))
        assertEquals(100, DuoPrefs.physicalToLogical(221))
        assertEquals(0, DuoPrefs.physicalToLogical(Int.MIN_VALUE))
        assertEquals(100, DuoPrefs.physicalToLogical(Int.MAX_VALUE))
    }
}
