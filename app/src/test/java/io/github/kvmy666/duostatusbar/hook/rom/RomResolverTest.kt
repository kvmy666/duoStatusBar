package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R15: one resolver is the single entry point for "which family is this ROM", so the hook, the icon
 * host and the diagnostic dump cannot drift apart on the inputs (brand vs. profiles vs. markers).
 */
class RomResolverTest {

    /** An Oplus runtime marker in the same `class:<fqcn>` form [OplusMarker] produces. */
    private val oplusMarker =
        "class:com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView"

    @Test
    fun `samsung resolves to the samsung family`() {
        val rom = RomResolver.resolve(
            "samsung", "samsung", "dm1q", "samsung/dm1q:14", 0, RomMarkers.UNKNOWN, emptyList()
        )
        assertEquals("samsung", rom.id)
    }

    @Test
    fun `oneplus resolves to oxygenos`() {
        val rom = RomResolver.resolve(
            "OnePlus", "OnePlus", "CPH2747", "OnePlus/CPH2747/OP611FL1:16", 36, RomMarkers.UNKNOWN
        )
        assertEquals("oxygenos", rom.id)
    }

    @Test
    fun `vivo resolves to vivo`() {
        val rom = RomResolver.resolve(
            "vivo", "vivo", "V2309A", "OriginOS 6", 0, RomMarkers.UNKNOWN, emptyList()
        )
        assertEquals("vivo", rom.id)
    }

    // ------------------------------------------------------- R17D: install marker merge (pure)

    @Test
    fun `the install marker fills a blank context marker`() {
        // A context read that cannot repeat the Oplus marker must not downgrade the family the install
        // proved from the host classloader.
        val merged = RomResolver.mergeMarkers(
            RomMarkers.UNKNOWN,
            RomMarkers.of(oplusMarker = oplusMarker)
        )
        assertEquals(oplusMarker, merged.oplusMarker)
        assertTrue("a filled marker must read as read", merged.read)
        assertTrue(merged.hasOplusMarker)
        // A marker alone proves nothing about MIUI: still not a non-MIUI verdict.
        assertFalse(merged.provesNonMiui)
    }

    @Test
    fun `the install marker keeps the context source and class`() {
        val read = RomMarkers.of(
            systemUiSourceDir = "/system/priv-app/SystemUI/SystemUI.apk",
            systemUiApplicationClass = "com.android.systemui.SystemUIApplication"
        )
        val merged = RomResolver.mergeMarkers(read, RomMarkers.of(oplusMarker = oplusMarker))
        assertEquals(
            "the context read owns the source dir",
            "/system/priv-app/SystemUI/SystemUI.apk",
            merged.systemUiSourceDir
        )
        assertEquals(
            "the context read owns the application class",
            "com.android.systemui.SystemUIApplication",
            merged.systemUiApplicationClass
        )
        assertEquals(oplusMarker, merged.oplusMarker)
        assertTrue(merged.read)
        assertTrue("the preserved source/class still prove non-MIUI", merged.provesNonMiui)
    }

    @Test
    fun `a context marker wins over a different install marker`() {
        val read = RomMarkers.of(oplusMarker = "class:context.Probe")
        val install = RomMarkers.of(oplusMarker = "class:install.Probe")
        val merged = RomResolver.mergeMarkers(read, install)
        assertEquals("the context marker wins", "class:context.Probe", merged.oplusMarker)
    }

    @Test
    fun `unknown plus unknown stays unknown`() {
        val merged = RomResolver.mergeMarkers(RomMarkers.UNKNOWN, RomMarkers.UNKNOWN)
        assertEquals(RomMarkers.UNKNOWN, merged)
        assertFalse(merged.read)
        assertEquals("", merged.oplusMarker)
    }
}
