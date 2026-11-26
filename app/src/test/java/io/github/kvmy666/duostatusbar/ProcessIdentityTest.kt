package io.github.kvmy666.duostatusbar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The module reports its own process identity on every line. The real identity comes from the Android
 * runtime; the tests force it, so the L prefix can be asserted without a device.
 */
class ProcessIdentityTest {

    @Before
    fun setUp() {
        ProcessIdentity.resetForTest()
        L.clearRecentForTest()
    }

    @After
    fun tearDown() {
        ProcessIdentity.resetForTest()
        L.clearRecentForTest()
    }

    @Test
    fun `a forced main identity is what the module reports`() {
        ProcessIdentity.forceForTest("com.android.systemui", 4321)
        assertEquals("com.android.systemui", ProcessIdentity.processName())
        assertEquals(4321, ProcessIdentity.pid())
        assertTrue(ProcessIdentity.isMainSystemUi())
        assertEquals("process=com.android.systemui pid=4321 ", ProcessIdentity.prefix())
    }

    @Test
    fun `a side process is never the main systemui process`() {
        ProcessIdentity.forceForTest("com.android.systemui:edgelighting", 99)
        assertFalse(ProcessIdentity.isMainSystemUi())

        ProcessIdentity.forceForTest("com.oplus.aiunit:worker43", 100)
        assertFalse(ProcessIdentity.isMainSystemUi())
    }

    @Test
    fun `reset clears the forced identity`() {
        ProcessIdentity.forceForTest("com.android.systemui", 4321)
        ProcessIdentity.resetForTest()
        assertNotEquals("com.android.systemui", ProcessIdentity.processName())
        assertNotEquals(4321, ProcessIdentity.pid())
    }

    @Test
    fun `every level carries the process and pid prefix in the recent ring`() {
        ProcessIdentity.forceForTest("com.android.systemui", 777)
        L.i("info-line")
        L.w("warn-line")
        L.d("debug-line")
        L.e("error-line")
        L.e("where", IllegalStateException("boom"))

        val lines = L.recentText().lines().filter { it.isNotBlank() }
        assertEquals(5, lines.size)
        for (line in lines) {
            assertTrue("missing identity prefix in: $line", line.contains("process=com.android.systemui pid=777 "))
        }
    }
}
