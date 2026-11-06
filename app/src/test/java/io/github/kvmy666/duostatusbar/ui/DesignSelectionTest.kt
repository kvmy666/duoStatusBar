package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure design-selection policy: every input normalizes to exactly one valid option.
 */
class DesignSelectionTest {

    @Test
    fun `valid new stays new`() {
        assertEquals(DuoPrefs.DESIGN_NEW, DuoPrefs.normalizeDesignStyle(DuoPrefs.DESIGN_NEW))
    }

    @Test
    fun `valid old stays old`() {
        assertEquals(DuoPrefs.DESIGN_OLD, DuoPrefs.normalizeDesignStyle(DuoPrefs.DESIGN_OLD))
    }

    @Test
    fun `invalid value normalizes to classic`() {
        assertEquals(DuoPrefs.DESIGN_OLD, DuoPrefs.normalizeDesignStyle("bogus"))
    }

    @Test
    fun `empty value normalizes to classic`() {
        assertEquals(DuoPrefs.DESIGN_OLD, DuoPrefs.normalizeDesignStyle(""))
    }

    @Test
    fun `null value normalizes to classic`() {
        assertEquals(DuoPrefs.DESIGN_OLD, DuoPrefs.normalizeDesignStyle(null))
    }

    @Test
    fun `exactly one option is selected for any input`() {
        for (raw in listOf(null, "", "bogus", DuoPrefs.DESIGN_OLD, DuoPrefs.DESIGN_NEW)) {
            val normalized = DuoPrefs.normalizeDesignStyle(raw)
            val oldSelected = normalized == DuoPrefs.DESIGN_OLD
            val newSelected = normalized == DuoPrefs.DESIGN_NEW
            assertTrue(oldSelected xor newSelected)
        }
    }
}
