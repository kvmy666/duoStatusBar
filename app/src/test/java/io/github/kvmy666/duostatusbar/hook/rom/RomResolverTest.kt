package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R15: one resolver is the single entry point for "which family is this ROM", so the hook, the icon
 * host and the diagnostic dump cannot drift apart on the inputs (brand vs. profiles vs. markers).
 */
class RomResolverTest {

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
}
