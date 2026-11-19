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
}
