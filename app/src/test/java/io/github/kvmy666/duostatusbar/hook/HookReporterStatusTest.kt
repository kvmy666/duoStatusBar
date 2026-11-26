package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every status the app stores must identify the process, its pid and the owning mode. */
class HookReporterStatusTest {

    @Test
    fun `the status line starts with process pid and owner`() {
        val line = hookStatusText(
            process = "com.android.systemui",
            pid = 4321,
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
            process = "com.android.systemui",
            pid = 5,
            owner = BarOwner.DUO.tag,
            stage = 2,
            renderer = "Rive",
            attached = true,
            settings = settings,
            riveAttempts = 1
        )
        assertTrue(line.startsWith("process=com.android.systemui pid=5 · owner=duo · "))
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
    fun `every owner tag is rendered`() {
        for (owner in BarOwner.entries) {
            val line = hookStatusText(
                process = "p",
                pid = 1,
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
}
