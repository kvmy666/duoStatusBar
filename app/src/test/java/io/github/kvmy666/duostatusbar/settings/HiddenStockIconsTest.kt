package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-icon hiding storage is a CSV of stable keys. These tests pin the normalization the
 * persistence layer relies on, so two spellings of the same set cannot produce two different rows.
 */
class HiddenStockIconsTest {

    @Test
    fun `parse trims drops blanks dedupes and sorts`() {
        assertEquals(
            listOf("alarm", "battery", "clock"),
            DuoPrefs.parseHiddenStockIcons(" clock, battery,,alarm,battery , ")
        )
    }

    @Test
    fun `join writes the canonical csv`() {
        assertEquals(
            "alarm,battery,clock",
            DuoPrefs.joinHiddenStockIcons(listOf("clock", " alarm ", "battery", "alarm", ""))
        )
    }

    @Test
    fun `csv round trips unchanged through parse and join`() {
        val csv = "alarm,battery,clock"
        assertEquals(csv, DuoPrefs.joinHiddenStockIcons(DuoPrefs.parseHiddenStockIcons(csv)))
        assertEquals(
            DuoPrefs.parseHiddenStockIcons(csv),
            DuoPrefs.parseHiddenStockIcons(DuoPrefs.joinHiddenStockIcons(DuoPrefs.parseHiddenStockIcons(csv)))
        )
    }

    @Test
    fun `duplicates collapse to one key`() {
        assertEquals(
            listOf("battery", "wifi"),
            DuoPrefs.parseHiddenStockIcons("wifi,battery,wifi,battery")
        )
        assertEquals("wifi", DuoPrefs.joinHiddenStockIcons(listOf("wifi", "wifi", " wifi ")))
    }

    @Test
    fun `empty and blank values mean hide nothing`() {
        assertEquals(emptyList<String>(), DuoPrefs.parseHiddenStockIcons(""))
        assertEquals(emptyList<String>(), DuoPrefs.parseHiddenStockIcons(null))
        assertEquals(emptyList<String>(), DuoPrefs.parseHiddenStockIcons("  , ,"))
        assertEquals("", DuoPrefs.joinHiddenStockIcons(emptyList()))
        assertTrue(DuoPrefs.joinHiddenStockIcons(DuoPrefs.parseHiddenStockIcons(" , ")).isEmpty())
    }
}
