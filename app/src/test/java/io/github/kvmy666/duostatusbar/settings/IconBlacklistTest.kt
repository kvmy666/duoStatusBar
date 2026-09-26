package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #4: the secure `icon_blacklist` is a single comma-separated value shared with the user and any
 * other app, so the merge and remove rules must never drop an entry that is not ours. A wrong value here
 * would silently hide the wrong icon (or nothing at all) on the device, which is why the maths is pinned
 * here instead.
 */
class IconBlacklistTest {

    @Test
    fun `blank, null and the literal null all mean nothing is hidden`() {
        assertTrue(IconBlacklist.parse(null).isEmpty())
        assertTrue(IconBlacklist.parse("").isEmpty())
        assertTrue(IconBlacklist.parse("null").isEmpty())
        assertTrue(IconBlacklist.parse("  ").isEmpty())
    }

    @Test
    fun `hiding adds our slots without touching existing entries`() {
        assertEquals(
            "rotate,wifi,mobile,battery",
            IconBlacklist.withKeys("rotate")
        )
    }

    @Test
    fun `hiding is idempotent`() {
        assertEquals(
            "wifi,mobile,battery",
            IconBlacklist.withKeys("wifi,mobile,battery")
        )
    }

    @Test
    fun `showing removes only our slots and keeps everything else`() {
        assertEquals(
            "rotate,volume",
            IconBlacklist.withoutKeys("rotate,wifi,mobile,battery,volume")
        )
    }

    @Test
    fun `showing leaves a blacklist that never had our slots unchanged`() {
        assertEquals("rotate", IconBlacklist.withoutKeys("rotate"))
        assertEquals("", IconBlacklist.withoutKeys(null))
    }

    @Test
    fun `whitespace, duplicates and case are handled`() {
        assertEquals(
            "wifi,mobile,battery",
            IconBlacklist.withKeys(" wifi , wifi ")
        )
        assertTrue(IconBlacklist.hasKeys("wifi, mobile ,battery"))
    }

    @Test
    fun `hasKeys is false until every slot is present`() {
        assertFalse(IconBlacklist.hasKeys("wifi,mobile"))
        assertTrue(IconBlacklist.hasKeys("wifi,mobile,battery"))
    }
}
