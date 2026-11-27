package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the failure boundary of [RomMarkerSource]: a read that proves nothing must come back as
 * [RomMarkers.UNKNOWN] and must never be mistaken for proof of a non-MIUI SystemUI.
 *
 * The throwing wrapper is the deterministic case: it forces the PackageManager failure path, so the
 * exact UNKNOWN result can be asserted. The bare application context has no installed
 * `com.android.systemui`, so its outcome depends on the Robolectric package table; only the invariant
 * that proof requires a real read is asserted there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RomMarkerSourceTest {

    @Test
    fun `a package manager failure reads as unknown and proves nothing`() {
        val context = RuntimeEnvironment.getApplication()
        val failing = object : android.content.ContextWrapper(context) {
            override fun getPackageManager(): android.content.pm.PackageManager =
                throw SecurityException("denied")
        }

        assertEquals(RomMarkers.UNKNOWN, RomMarkerSource.read(failing))
        assertFalse(RomMarkerSource.read(failing).provesNonMiui)
    }

    @Test
    fun `an application context never proves non-miui without a read`() {
        val markers = RomMarkerSource.read(RuntimeEnvironment.getApplication())

        // Either the package table has no SystemUI (UNKNOWN) or a read returned usable evidence; an
        // empty record must not be reported as proof of non-MIUI.
        assertTrue(
            "proof of non-MIUI requires a successful read with positive evidence",
            !markers.provesNonMiui || markers.read
        )
        if (!markers.read) assertEquals(RomMarkers.UNKNOWN, markers)
    }

    @Test
    fun `an oplus marker is a read but not proof of a non-miui systemui`() {
        // R17D: the marker is a positive Oplus proof. It is a field of the read record, but with no
        // source path or application class it still says nothing about MIUI either way.
        val markers = RomMarkers.of(
            oplusMarker =
                "class:com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView"
        )
        assertTrue("a marker is a read field", markers.read)
        assertTrue(markers.hasOplusMarker)
        assertTrue(markers.provesFamily("coloros"))
        assertFalse("a marker-only record is not non-MIUI proof", markers.provesNonMiui)
    }

    @Test
    fun `a package manager failure reads no oplus marker`() {
        val context = RuntimeEnvironment.getApplication()
        val failing = object : android.content.ContextWrapper(context) {
            override fun getPackageManager(): android.content.pm.PackageManager =
                throw SecurityException("denied")
        }

        val markers = RomMarkerSource.read(failing)
        assertEquals(RomMarkers.UNKNOWN, markers)
        assertEquals("", markers.oplusMarker)
        assertFalse(markers.hasOplusMarker)
    }
}
