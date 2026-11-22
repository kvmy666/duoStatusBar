package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3A A6: one deterministic boot line that lists what the module actually registered. */
class BootFeatureLineTest {

    private val shared = SharedHook.entries.toSet()

    @Test
    fun `the line has the documented exact format`() {
        val featureSet = FeatureSet(
            BarOwner.DUO,
            DuoRenderer.RIVE,
            setOf(DuoFeature.PERCENT, DuoFeature.RING),
            setOf(SharedHook.WINDOW, SharedHook.APP)
        )
        assertEquals(
            "features: owner=duo renderer=rive stage=2 override=none rev=7 " +
                "active=percent,ring shared=app,window",
            featureLogLine(featureSet, 2, 7L, null)
        )
    }

    @Test
    fun `active and shared tags are sorted regardless of set order`() {
        val featureSet = FeatureSet(
            BarOwner.DUO,
            DuoRenderer.CANVAS,
            setOf(DuoFeature.EVENT_CALL, DuoFeature.AIRPLANE, DuoFeature.RING),
            setOf(SharedHook.WINDOW, SharedHook.BAR_TINT, SharedHook.APP)
        )
        assertEquals(
            "features: owner=duo renderer=canvas stage=1 override=none rev=3 " +
                "active=airplane,event_call,ring shared=app,bar_tint,window",
            featureLogLine(featureSet, 1, 3L, null)
        )
    }

    @Test
    fun `stage override and revision render their values`() {
        val featureSet = FeatureSet(BarOwner.DUO, DuoRenderer.CANVAS, emptySet(), shared)
        assertTrue(
            featureLogLine(featureSet, 0, 1L, 0)
                .contains(" stage=0 override=0 rev=1 active= shared=")
        )
        assertTrue(
            featureLogLine(featureSet, 1, 12L, 1)
                .contains(" stage=1 override=1 rev=12 active= shared=")
        )
        assertTrue(
            featureLogLine(featureSet, 2, 123L, 2)
                .contains(" stage=2 override=2 rev=123 active= shared=")
        )
        assertTrue(
            featureLogLine(featureSet, 2, 0L, null)
                .contains(" stage=2 override=none rev=0 active= shared=")
        )
    }

    @Test
    fun `the app status line carries the same fields as the boot line`() {
        val featureSet = FeatureSet(
            BarOwner.DUO,
            DuoRenderer.RIVE,
            setOf(DuoFeature.PERCENT, DuoFeature.RING),
            setOf(SharedHook.WINDOW, SharedHook.APP)
        )
        val line = featureStatus(featureSet, 2, 7L, null)
        assertEquals(featureLogLine(featureSet, 2, 7L, null), line)
        for (field in listOf(
            "owner=duo",
            "renderer=rive",
            "stage=2",
            "override=none",
            "rev=7",
            "active=percent,ring",
            "shared=app,window"
        )) {
            assertTrue("missing $field in: $line", field in line)
        }
    }

    @Test
    fun `the app status line renders an override and a live revision`() {
        val featureSet = FeatureSet(BarOwner.DUO, DuoRenderer.CANVAS, emptySet(), shared)
        val line = featureStatus(featureSet, 1, 12L, 1)
        assertEquals(featureLogLine(featureSet, 1, 12L, 1), line)
        for (field in listOf(
            "owner=duo",
            "renderer=canvas",
            "stage=1",
            "override=1",
            "rev=12",
            "active=",
            "shared="
        )) {
            assertTrue("missing $field in: $line", field in line)
        }
    }

    @Test
    fun `iOS and none have an empty active list`() {
        for (owner in listOf(BarOwner.IOS, BarOwner.NONE)) {
            val featureSet = FeatureSet(owner, DuoRenderer.NONE, emptySet(), shared)
            val line = featureLogLine(featureSet, 0, 0L, null)
            assertTrue("owner missing in: $line", line.contains("owner=${owner.tag} renderer=none"))
            assertEquals("", line.substringAfter("active=").substringBefore(" shared="))
            assertEquals(
                SharedHook.entries.map { it.tag }.sorted().joinToString(","),
                line.substringAfter("shared=")
            )
        }
    }
}
