package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class InvertCenterTest {

    @Test
    fun `enabling writes percent`() {
        assertEquals(DuoPrefs.MIDDLE_SLOT_PERCENT, invertCenterToMiddleSlot(true, DuoPrefs.MIDDLE_SLOT_AUTO))
        assertEquals(DuoPrefs.MIDDLE_SLOT_PERCENT, invertCenterToMiddleSlot(true, DuoPrefs.MIDDLE_SLOT_CELL))
    }

    @Test
    fun `disabling from percent restores auto`() {
        assertEquals(DuoPrefs.MIDDLE_SLOT_AUTO, invertCenterToMiddleSlot(false, DuoPrefs.MIDDLE_SLOT_PERCENT))
    }

    @Test
    fun `disabling preserves a non percent value`() {
        assertEquals(DuoPrefs.MIDDLE_SLOT_CELL, invertCenterToMiddleSlot(false, DuoPrefs.MIDDLE_SLOT_CELL))
        assertEquals(DuoPrefs.MIDDLE_SLOT_WIFI, invertCenterToMiddleSlot(false, DuoPrefs.MIDDLE_SLOT_WIFI))
    }

    @Test
    fun `disabling from an unknown value normalizes to auto`() {
        assertEquals(DuoPrefs.MIDDLE_SLOT_AUTO, invertCenterToMiddleSlot(false, "nope"))
    }
}