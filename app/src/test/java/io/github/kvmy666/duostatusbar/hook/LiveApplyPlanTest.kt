package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The live-apply decision and the host invariant it protects: every design flip is applied by
 * replacing the one attached element, never by a restart (the API has no restart concept at all).
 */
class LiveApplyPlanTest {

    private fun plan(
        previousDesign: String = DuoPrefs.DESIGN_OLD,
        freshDesign: String = previousDesign,
        previousEngine: String = DuoPrefs.ENGINE_AUTO,
        freshEngine: String = previousEngine,
        previousSize: Int = 100,
        freshSize: Int = previousSize,
        liveApply: Boolean = true,
        hasElement: Boolean = true,
        applySavedSize: Boolean = false
    ): LiveRebuild = LiveApply.plan(
        previousDesign, freshDesign,
        previousEngine, freshEngine,
        previousSize, freshSize,
        liveApply, hasElement, applySavedSize
    )

    /**
     * The host keeps exactly one element. Applying a rebuild tears the old one down and attaches the
     * fresh one, so the model never holds two designs or two elements.
     */
    private class HostModel(design: String) {
        var design: String = design
            private set
        var attached = 1
            private set

        fun apply(result: LiveRebuild, fresh: String) {
            if (result == LiveRebuild.NONE) return
            design = fresh
            attached = 1
        }
    }

    private fun flip(host: HostModel, fresh: String) {
        val result = plan(previousDesign = host.design, freshDesign = fresh)
        assertEquals("a design flip must always rebuild live", LiveRebuild.DESIGN, result)
        host.apply(result, fresh)
        assertEquals("the attached element must draw the fresh design", fresh, host.design)
        assertEquals("exactly one element may be attached", 1, host.attached)
    }

    @Test
    fun `old to new rebuilds live`() {
        val host = HostModel(DuoPrefs.DESIGN_OLD)
        flip(host, DuoPrefs.DESIGN_NEW)
    }

    @Test
    fun `new to old rebuilds live`() {
        val host = HostModel(DuoPrefs.DESIGN_NEW)
        flip(host, DuoPrefs.DESIGN_OLD)
    }

    @Test
    fun `old new old new rebuilds live each time`() {
        val host = HostModel(DuoPrefs.DESIGN_OLD)
        for (next in listOf(
            DuoPrefs.DESIGN_NEW,
            DuoPrefs.DESIGN_OLD,
            DuoPrefs.DESIGN_NEW
        )) {
            flip(host, next)
        }
    }

    @Test
    fun `twenty rapid flips each rebuild live and never leave two elements`() {
        val host = HostModel(DuoPrefs.DESIGN_OLD)
        repeat(20) { index ->
            val fresh = if (index % 2 == 0) DuoPrefs.DESIGN_NEW else DuoPrefs.DESIGN_OLD
            assertNotEquals("every iteration is a real flip", host.design, fresh)
            flip(host, fresh)
        }
    }

    @Test
    fun `the API has no restart concept`() {
        assertEquals(
            setOf("NONE", "DESIGN", "ENGINE", "SIZE"),
            LiveRebuild.entries.map { it.name }.toSet()
        )
        assertFalse(LiveRebuild.entries.any { it.name.contains("RESTART") })
    }

    @Test
    fun `an engine change rebuilds`() {
        assertEquals(
            LiveRebuild.ENGINE,
            plan(
                previousEngine = DuoPrefs.ENGINE_AUTO,
                freshEngine = DuoPrefs.ENGINE_CANVAS
            )
        )
    }

    @Test
    fun `a size change with live apply rebuilds`() {
        assertEquals(
            LiveRebuild.SIZE,
            plan(previousSize = 100, freshSize = 140, liveApply = true)
        )
    }

    @Test
    fun `a size change with live apply off waits`() {
        assertEquals(
            LiveRebuild.NONE,
            plan(previousSize = 100, freshSize = 140, liveApply = false)
        )
    }

    @Test
    fun `rotation keeps the in-place path even with live apply off`() {
        assertEquals(
            LiveRebuild.NONE,
            plan(previousSize = 135, freshSize = 130, liveApply = false, applySavedSize = true)
        )
    }

    @Test
    fun `rotation size does not rebuild even with live apply on`() {
        assertEquals(
            LiveRebuild.NONE,
            plan(previousSize = 135, freshSize = 130, liveApply = true, applySavedSize = true)
        )
    }

    @Test
    fun `a design change is live even with live apply off`() {
        assertEquals(
            LiveRebuild.DESIGN,
            plan(
                previousDesign = DuoPrefs.DESIGN_OLD,
                freshDesign = DuoPrefs.DESIGN_NEW,
                liveApply = false
            )
        )
    }

    @Test
    fun `no element has nothing to rebuild`() {
        assertEquals(
            LiveRebuild.NONE,
            plan(
                previousDesign = DuoPrefs.DESIGN_OLD,
                freshDesign = DuoPrefs.DESIGN_NEW,
                previousEngine = DuoPrefs.ENGINE_AUTO,
                freshEngine = DuoPrefs.ENGINE_CANVAS,
                previousSize = 100,
                freshSize = 140,
                hasElement = false
            )
        )
    }

    @Test
    fun `a design change wins over an engine and size change`() {
        assertEquals(
            LiveRebuild.DESIGN,
            plan(
                previousDesign = DuoPrefs.DESIGN_OLD,
                freshDesign = DuoPrefs.DESIGN_NEW,
                previousEngine = DuoPrefs.ENGINE_AUTO,
                freshEngine = DuoPrefs.ENGINE_CANVAS,
                previousSize = 100,
                freshSize = 140
            )
        )
    }
}
