package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R17D: the Oplus marker is the proof that a ColorOS/realme SystemUI is the Oplus family even when its
 * package path and application class look like AOSP.
 *
 * Measured classes (evidence: `.pm/evidence/u1127/new-duo-report-20261127-175643/diagnostics.txt:91-92`
 * and `.pm/evidence/u1127/duo-report-20261127-150754/diagnostics.txt:108-109`):
 *  - `...StatBatteryMeterView` is the realme/OnePlus battery view;
 *  - `...StatClock` is the clock;
 *  - `OplusQSSimpleHeader` is the Oplus QS header.
 * The resource ids are the Oplus spellings; they do not exist in an AOSP SystemUI.
 */
class OplusMarkerTest {

    @Test
    fun `class markers are exactly the measured Oplus classes`() {
        assertEquals(
            listOf(
                "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
                "com.oplus.systemui.statusbar.widget.StatClock",
                "com.oplus.systemui.separate.OplusQSSimpleHeader"
            ),
            OplusMarker.CLASS_MARKERS
        )
    }

    @Test
    fun `id markers are exactly the measured Oplus resource ids`() {
        assertEquals(
            listOf("oplus_qs_tile_icon_bg", "oplus_background_mask", "oplus_fake_clock_container"),
            OplusMarker.ID_MARKERS
        )
    }

    @Test
    fun `detectInClassLoader returns the first present class marker`() {
        val markers = OplusMarker.CLASS_MARKERS
        assertEquals(
            "class:${markers[0]}",
            OplusMarker.detectInClassLoader(FakeClassLoader(setOf(markers[0])))
        )
        // Every marker alone proves the family and is reported verbatim.
        assertEquals(
            "class:${markers[1]}",
            OplusMarker.detectInClassLoader(FakeClassLoader(setOf(markers[1])))
        )
        assertEquals(
            "class:${markers[2]}",
            OplusMarker.detectInClassLoader(FakeClassLoader(setOf(markers[2])))
        )
        // When more than one resolves, the list order decides which marker is reported.
        assertEquals(
            "class:${markers[0]}",
            OplusMarker.detectInClassLoader(FakeClassLoader(setOf(markers[2], markers[0])))
        )
    }

    @Test
    fun `detectInClassLoader returns empty for absent markers and a null loader`() {
        assertEquals("", OplusMarker.detectInClassLoader(FakeClassLoader(emptySet())))
        assertEquals("", OplusMarker.detectInClassLoader(null))
    }

    /**
     * A minimal loader that resolves exactly [present] and nothing else. The null parent makes the
     * lookup deterministic: `Class.forName` cannot fall back to a bootstrap copy of an Oplus class.
     */
    private class FakeClassLoader(private val present: Set<String>) : ClassLoader(null) {
        protected override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name in present) return String::class.java
            throw ClassNotFoundException(name)
        }
    }
}
