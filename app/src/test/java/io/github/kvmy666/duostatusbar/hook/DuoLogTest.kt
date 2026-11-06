package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuoLogTest {

    @Before
    fun reset() {
        L.clearRecentForTest()
    }

    private fun lastLine(): String {
        val text = L.recentText()
        // recentText prepends a millisecond timestamp; strip it for schema checks.
        return text.substringAfter(' ')
    }

    @Test
    fun `exact schema and order`() {
        DuoLog.event(
            ui = "oneplus",
            surface = "shade",
            stage = "resolve",
            clazz = "Foo",
            id = "bar",
            hook = "m",
            result = "ok",
            reason = "R",
            detail = "d",
            build = "b"
        )
        val expected = "ui=oneplus surface=shade stage=resolve class=Foo id=bar hook=m result=ok reason=R detail=d build=b"
        assertEquals(expected, lastLine())
    }

    @Test
    fun `newlines flattened`() {
        DuoLog.event(ui = "u", surface = "s", stage = "st", result = "ok", detail = "a\nb\rc\rd")
        val line = lastLine()
        assertTrue(!line.contains('\n'))
        assertTrue(!line.contains('\r'))
        assertTrue(line.contains("detail=a b c d"))
    }

    @Test
    fun `long detail truncated to 240`() {
        val longDetail = "a".repeat(300)
        DuoLog.event(ui = "u", surface = "s", stage = "st", result = "ok", detail = longDetail)
        val line = lastLine()
        val detailValue = line.substringAfter("detail=").substringBefore(" build=")
        assertEquals(240, detailValue.length)
    }

    @Test
    fun `secret redaction`() {
        DuoLog.event(
            ui = "u", surface = "s", stage = "st", result = "ok",
            detail = "ssid=MyWifi token=abc password=hunter2 user@example.com"
        )
        val line = lastLine()
        assertTrue(line.contains("ssid=[redacted]"))
        assertTrue(line.contains("token=[redacted]"))
        assertTrue(line.contains("password=[redacted]"))
        assertTrue(line.contains("[redacted]"))
        assertTrue(!line.contains("MyWifi"))
        assertTrue(!line.contains("hunter2"))
    }

    @Test
    fun `stable reason constants`() {
        assertEquals("RESOLVE_NO_CANDIDATE", DuoLog.RESOLVE_NO_CANDIDATE)
        assertEquals("HOOK_ABSENT", DuoLog.HOOK_ABSENT)
        assertEquals("HOOK_INSTALL_FAILED", DuoLog.HOOK_INSTALL_FAILED)
        assertEquals("ATTACH_ZERO_WIDTH", DuoLog.ATTACH_ZERO_WIDTH)
        assertEquals("PANEL_HOOK_ABSENT", DuoLog.PANEL_HOOK_ABSENT)
        assertEquals("PANEL_ALIAS_UNVERIFIED", DuoLog.PANEL_ALIAS_UNVERIFIED)
        assertEquals("SURFACE_DISABLED", DuoLog.SURFACE_DISABLED)
        assertEquals("RESTORE_FAILED", DuoLog.RESTORE_FAILED)
        assertEquals("PROFILE_ASSET_MISSING", DuoLog.PROFILE_ASSET_MISSING)
    }
}