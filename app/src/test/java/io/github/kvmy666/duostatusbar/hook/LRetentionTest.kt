package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.L
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class LRetentionTest {

    @Before
    fun reset() {
        L.clearRecentForTest()
    }

    @Test
    fun `line cap keeps at most 600 lines`() {
        repeat(700) { i ->
            L.i("line-$i")
        }
        val lines = L.recentText().lines()
        assertTrue("lines=${lines.size}", lines.size <= 600)
        assertTrue(lines.size >= 599)
    }

    @Test
    fun `byte cap keeps recent text within 2 MB`() {
        L.clearRecentForTest()
        // ~4 KB per line; enough to exceed the 2 MB cap across many lines.
        val chunk = "x".repeat(4096)
        repeat(600) {
            L.i(chunk)
        }
        val bytes = L.recentText().toByteArray(StandardCharsets.UTF_8).size
        assertTrue("bytes=$bytes", bytes <= 2 * 1024 * 1024)
        assertTrue(L.recentText().isNotEmpty())
    }
}