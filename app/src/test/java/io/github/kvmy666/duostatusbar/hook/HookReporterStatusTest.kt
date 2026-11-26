package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.ProcessIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Every status the app stores must identify the process, its pid and the owning mode. */
class HookReporterStatusTest {

    @Before
    fun pinIdentity() {
        ProcessIdentity.forceForTest("com.android.systemui", 4321)
    }

    @After
    fun releaseIdentity() {
        ProcessIdentity.resetForTest()
    }

    @Test
    fun `the status line starts with process pid and owner`() {
        val line = hookStatusText(
            owner = BarOwner.IOS.tag,
            stage = 0,
            renderer = "none",
            attached = false,
            settings = null,
            riveAttempts = 0
        )
        assertTrue(
            "status does not start with identity + owner: $line",
            line.startsWith("process=com.android.systemui pid=4321 · owner=ios · ")
        )
        assertTrue(line.contains("stage=0"))
        assertTrue(line.contains("renderer=none"))
        assertTrue(line.contains("attached=false"))
        assertTrue(line.contains("riveAttempts=0"))
    }

    @Test
    fun `settings facts are appended unchanged after the identity`() {
        val settings = ModuleSettings.DEFAULT.copy(
            sizePercent = 90,
            offsetX = 3,
            splitIndicators = true,
            lockRing = true,
            indicatorsOffsetX = 4,
            edgePadding = 80,
            showPercent = false,
            revision = 12L
        )
        val line = hookStatusText(
            owner = BarOwner.DUO.tag,
            stage = 2,
            renderer = "Rive",
            attached = true,
            settings = settings,
            riveAttempts = 1
        )
        assertTrue(line.startsWith("process=com.android.systemui pid=4321 · owner=duo · "))
        assertTrue(line.contains("size=90%"))
        assertTrue(line.contains("offset=3dp"))
        assertTrue(line.contains("split=true"))
        assertTrue(line.contains("lock=true"))
        assertTrue(line.contains("icons=4dp"))
        assertTrue(line.contains("edge=80%"))
        assertTrue(line.contains("percent=false"))
        assertTrue(line.contains("rev=12"))
    }

    @Test
    fun `a rive bind reason is appended only when Rive failed to bind`() {
        val healthy = hookStatusText(
            owner = BarOwner.DUO.tag,
            stage = 2,
            renderer = "Rive",
            attached = true,
            settings = null,
            riveAttempts = 0
        )
        assertTrue("healthy status must stay unchanged: $healthy", !healthy.contains("riveBind="))

        val failed = hookStatusText(
            owner = BarOwner.DUO.tag,
            stage = 2,
            renderer = "Canvas",
            attached = true,
            settings = null,
            riveAttempts = 1,
            riveBind = "poll"
        )
        assertTrue("failed status: $failed", failed.startsWith("process=com.android.systemui pid=4321 · owner=duo · "))
        assertTrue("failed status: $failed", failed.endsWith(" · riveBind=poll"))
    }

    @Test
    fun `every owner tag is rendered`() {
        for (owner in BarOwner.entries) {
            val line = hookStatusText(
                owner = owner.tag,
                stage = 0,
                renderer = "none",
                attached = false,
                settings = null,
                riveAttempts = 0
            )
            assertTrue("owner ${owner.tag} missing: $line", line.contains("owner=${owner.tag}"))
        }
        assertEquals(3, BarOwner.entries.size)
    }

    @Test
    fun `the shared prefix is exactly what the status starts with`() {
        val prefix = hookStatusPrefix(BarOwner.DUO.tag)
        assertEquals("process=com.android.systemui pid=4321 · owner=duo · ", prefix)
        val line = hookStatusText(
            owner = BarOwner.DUO.tag,
            stage = 2,
            renderer = "Rive",
            attached = true,
            settings = null,
            riveAttempts = 0
        )
        // The log line drops exactly this prefix; L adds the identity itself, so the log keeps one copy.
        assertEquals(
            "stage=2 · renderer=Rive · attached=true · riveAttempts=0",
            line.removePrefix(prefix)
        )
    }
}
