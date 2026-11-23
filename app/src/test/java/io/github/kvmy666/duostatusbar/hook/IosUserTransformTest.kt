package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue 7: the stored percent values become a draw offset and a scale at the glyph's own width. */
class IosUserTransformTest {

    private val delta = 1e-4f
    private val identity = IosUserTransform()

    @Test
    fun `the default transform is identity`() {
        val transform = IosUserTransform()
        assertEquals(0f, transform.dxPx, delta)
        assertEquals(1f, transform.scale, delta)
        assertEquals(identity, transform)
    }

    @Test
    fun `the module defaults keep today's pixels for every glyph`() {
        for (kind in IosIconKind.values()) {
            val transform = ModuleSettings.DEFAULT.iosTransform(kind, 100f)
            assertEquals(0f, transform.dxPx, delta)
            assertEquals(1f, transform.scale, delta)
        }
    }

    @Test
    fun `percent values map to pixels and scale`() {
        val larger = iosUserTransform(25, 150, 40f)
        assertEquals(10f, larger.dxPx, delta)
        assertEquals(1.5f, larger.scale, delta)

        val smaller = iosUserTransform(-25, 50, 40f)
        assertEquals(-10f, smaller.dxPx, delta)
        assertEquals(0.5f, smaller.scale, delta)

        val unchanged = iosUserTransform(0, 100, 0f)
        assertEquals(0f, unchanged.dxPx, delta)
        assertEquals(1f, unchanged.scale, delta)
    }

    @Test
    fun `out-of-range values are clamped to the stored bounds`() {
        val high = iosUserTransform(999, 999, 100f)
        assertEquals(25f, high.dxPx, delta)
        assertEquals(2f, high.scale, delta)

        val low = iosUserTransform(-999, 0, 100f)
        assertEquals(-25f, low.dxPx, delta)
        assertEquals(0.5f, low.scale, delta)
    }

    @Test
    fun `each glyph reads its own stored fields`() {
        val settings = ModuleSettings.DEFAULT.copy(iosWifiOffset = 10, iosWifiScale = 200)

        val wifi = settings.iosTransform(IosIconKind.WIFI, 100f)
        assertEquals(10f, wifi.dxPx, delta)
        assertEquals(2f, wifi.scale, delta)

        assertEquals(identity, settings.iosTransform(IosIconKind.BATTERY, 100f))
        assertEquals(identity, settings.iosTransform(IosIconKind.CELL, 100f))
    }
}
