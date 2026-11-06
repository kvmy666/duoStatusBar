package io.github.kvmy666.duostatusbar.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCodecTest {

    @Test
    fun `schema and version constants`() {
        assertEquals("duo-settings/1", BackupCodec.SCHEMA)
        assertEquals("duo-settings/1", BackupCodec.schema)
        assertEquals(1, BackupCodec.VERSION)
        assertEquals(1, BackupCodec.version)
    }

    @Test
    fun `encode decode round trip types`() {
        val values = mapOf<String, Any?>(
            "a" to true,
            "b" to 42,
            "c" to 7L,
            "d" to "hello"
        )
        val json = BackupCodec.encode(values)
        val root = JSONObject(json)
        assertEquals("duo-settings/1", root.getString("schema"))
        assertEquals(1, root.getInt("version"))
        assertTrue(root.has("exportedAt"))
        val decoded = BackupCodec.decode(json)
        assertTrue(decoded.isSuccess)
        assertEquals(values, decoded.values)
    }

    @Test
    fun `bad json returns BAD_JSON`() {
        val r = BackupCodec.decode("{not json")
        assertFalse(r.isSuccess)
        assertEquals(BackupCodec.BAD_JSON, r.error)
        assertEquals(BackupCodec.BAD_JSON, r.code)
    }

    @Test
    fun `empty input returns EMPTY`() {
        val r = BackupCodec.decode("")
        assertFalse(r.isSuccess)
        assertEquals(BackupCodec.EMPTY, r.error)
    }

    @Test
    fun `wrong schema returns UNSUPPORTED_SCHEMA`() {
        val json = """{"schema":"other","version":1,"values":{"x":1}}"""
        val r = BackupCodec.decode(json)
        assertEquals(BackupCodec.UNSUPPORTED_SCHEMA, r.error)
    }

    @Test
    fun `wrong version returns UNSUPPORTED_VERSION`() {
        val json = """{"schema":"duo-settings/1","version":2,"values":{"x":1}}"""
        val r = BackupCodec.decode(json)
        assertEquals(BackupCodec.UNSUPPORTED_VERSION, r.error)
    }

    @Test
    fun `empty values returns EMPTY`() {
        val json = """{"schema":"duo-settings/1","version":1,"values":{}}"""
        val r = BackupCodec.decode(json)
        assertEquals(BackupCodec.EMPTY, r.error)
    }

    @Test
    fun `unknown top level keys ignored`() {
        val json = """{"schema":"duo-settings/1","version":1,"foo":5,"values":{"x":1}}"""
        val r = BackupCodec.decode(json)
        assertTrue(r.isSuccess)
        assertEquals(mapOf("x" to 1), r.values)
    }

    @Test
    fun `unsupported value type returns BAD_VALUE`() {
        val json = """{"schema":"duo-settings/1","version":1,"values":{"x":1.5}}"""
        val r = BackupCodec.decode(json)
        assertEquals(BackupCodec.BAD_VALUE, r.error)
    }

    @Test
    fun `sanitize keeps allowed columns and app keys`() {
        val input = mapOf<String, Any?>(
            DuoPrefs.COL_ENABLED to true,
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED to false,
            "hide_stock_icons" to true,
            "check_updates" to false,
            "landscape_set" to true,
            "revision" to 5L,
            "last_dump" to "d",
            "last_status" to "s",
            "status_history" to setOf("a"),
            "last_fallback" to "f",
            "root_allowed" to true,
            "log_sent_at" to 1L,
            "update_notified" to "v",
            "update_check_at" to 2L,
            "unknown_key" to 1
        )
        val out = BackupCodec.sanitize(input)
        assertTrue(out.containsKey(DuoPrefs.COL_ENABLED))
        assertTrue(out.containsKey(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED))
        assertEquals(true, out["hide_stock_icons"])
        assertEquals(false, out["check_updates"])
        assertEquals(true, out["landscape_set"])
        assertFalse(out.containsKey("revision"))
        assertFalse(out.containsKey("last_dump"))
        assertFalse(out.containsKey("last_status"))
        assertFalse(out.containsKey("status_history"))
        assertFalse(out.containsKey("last_fallback"))
        assertFalse(out.containsKey("root_allowed"))
        assertFalse(out.containsKey("log_sent_at"))
        assertFalse(out.containsKey("update_notified"))
        assertFalse(out.containsKey("update_check_at"))
        assertFalse(out.containsKey("unknown_key"))
    }

    @Test
    fun `sanitize allows the download progress switch in both orientations`() {
        val input = mapOf<String, Any?>(
            DuoPrefs.COL_DOWNLOAD_PROGRESS to false,
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DOWNLOAD_PROGRESS to true
        )
        val out = BackupCodec.sanitize(input)
        assertEquals(false, out[DuoPrefs.COL_DOWNLOAD_PROGRESS])
        assertEquals(true, out[DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DOWNLOAD_PROGRESS])
    }

    @Test
    fun `sanitize allows the burn-in switch in both orientations`() {
        val input = mapOf<String, Any?>(
            DuoPrefs.COL_BURN_IN to true,
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_BURN_IN to false
        )
        val out = BackupCodec.sanitize(input)
        assertEquals(true, out[DuoPrefs.COL_BURN_IN])
        assertEquals(false, out[DuoPrefs.LAND_PREFIX + DuoPrefs.COL_BURN_IN])
    }

    @Test
    fun `sanitize allows the hidden stock icon list in both orientations`() {
        val input = mapOf<String, Any?>(
            DuoPrefs.COL_HIDDEN_STOCK_ICONS to "alarm,battery",
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDDEN_STOCK_ICONS to "clock"
        )
        val out = BackupCodec.sanitize(input)
        assertEquals("alarm,battery", out[DuoPrefs.COL_HIDDEN_STOCK_ICONS])
        assertEquals("clock", out[DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDDEN_STOCK_ICONS])
    }

    @Test
    fun `sanitize excludes transient even if in portrait columns`() {
        // revision is in PORTRAIT_COLUMNS but must be excluded
        val input = mapOf<String, Any?>("revision" to 9L)
        val out = BackupCodec.sanitize(input)
        assertTrue(out.isEmpty())
    }
}
