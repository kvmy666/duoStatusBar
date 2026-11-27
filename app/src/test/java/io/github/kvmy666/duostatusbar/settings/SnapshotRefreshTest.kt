package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R16-A2: every report path asks the module once and waits a bounded time for a *new* dump/status.
 * The rules are pure so a phone with no module can never make the app wait for the full timeout for a
 * module that was never installed, and a stale answer is returned as stale, never as fresh.
 */
class SnapshotRefreshTest {

    private val previous = FreshSnapshot(
        status = "process=com.android.systemui pid=1 · owner=ios · loaded",
        dump = "old dump",
        dumpAt = 1_000L,
        refreshed = false
    )

    @Test
    fun `a newer dump timestamp proves the dump is fresh`() {
        var now = 0L
        val out = SnapshotRefresh.poll(
            previous = previous,
            request = {},
            readStatus = { previous.status },
            readDump = { previous.dump },
            readDumpAt = { 1_001L },
            now = { now },
            sleep = { now += it }
        )

        assertTrue(out.refreshed)
        assertEquals(1_001L, out.dumpAt)
    }

    @Test
    fun `a changed dump is fresh even when the old build wrote no timestamp`() {
        var now = 0L
        val out = SnapshotRefresh.poll(
            previous = previous.copy(dumpAt = 0L),
            request = {},
            readStatus = { "process=com.android.systemui pid=2 · owner=ios · loaded" },
            readDump = { "new dump" },
            readDumpAt = { 0L },
            now = { now },
            sleep = { now += it }
        )

        assertTrue(out.refreshed)
        assertEquals("new dump", out.dump)
    }

    @Test
    fun `a fresh status alone is a refresh and keeps the stored dump`() {
        var now = 0L
        val out = SnapshotRefresh.poll(
            previous = previous,
            request = {},
            readStatus = { "process=com.android.systemui pid=2 · owner=duo · loaded" },
            readDump = { previous.dump },
            readDumpAt = { previous.dumpAt },
            now = { now },
            sleep = { now += it }
        )

        assertTrue(out.refreshed)
        assertEquals("process=com.android.systemui pid=2 · owner=duo · loaded", out.status)
        assertEquals(previous.dump, out.dump)
    }

    @Test
    fun `a timeout returns the previous values and not refreshed`() {
        var now = 0L
        var requests = 0
        var sleeps = 0
        val out = SnapshotRefresh.poll(
            previous = previous,
            request = { requests++ },
            readStatus = { previous.status },
            readDump = { previous.dump },
            readDumpAt = { previous.dumpAt },
            now = { now },
            sleep = {
                sleeps++
                now += it
            },
            timeoutMs = 300L,
            pollMs = 100L
        )

        assertEquals(previous, out)
        assertFalse(out.refreshed)
        assertEquals("the module is asked exactly once", 1, requests)
        assertTrue("it must actually have waited", sleeps > 0)
    }

    @Test
    fun `a throwing request read or sleep never escapes the poll`() {
        val out = SnapshotRefresh.poll(
            previous = previous,
            request = { throw IllegalStateException("no module") },
            readStatus = { throw IllegalArgumentException("read") },
            readDump = { throw IllegalArgumentException("read") },
            readDumpAt = { throw IllegalArgumentException("read") },
            now = { 0L },
            sleep = { throw InterruptedException("sleep") },
            timeoutMs = 200L,
            pollMs = 100L
        )

        assertEquals(previous, out)
        assertFalse(out.refreshed)
    }

    @Test
    fun `a never-loaded module is not requested`() {
        assertFalse(SnapshotRefresh.shouldRequest(0L, 0L, ""))
        assertTrue(SnapshotRefresh.shouldRequest(1L, 0L, ""))
        assertTrue(SnapshotRefresh.shouldRequest(0L, 1L, ""))
        assertTrue(SnapshotRefresh.shouldRequest(0L, 0L, "process=com.android.systemui"))
    }
}
