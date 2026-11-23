package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P1 engine seams: density/intrinsic scaling, the diagnostic line, gating, and install safety. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosResourceEngineTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun entry(widthDp: Float = 20f, heightDp: Float = 16f) = ResolvedEntry(
        id = 0x7f010001,
        slot = IosResourceSlot.Wifi(2),
        widthDp = widthDp,
        heightDp = heightDp,
        packageName = "com.android.systemui"
    )

    private fun rom(id: String) = RomAdapter(
        id = id,
        label = id,
        systemUiPackage = "com.android.systemui",
        containerIds = emptyList(),
        batteryId = "battery",
        notes = "test"
    )

    // ------------------------------------------------------------------------------- snapshot

    @Test
    fun `snapshot scales the stock intrinsic dp by the requested density`() {
        val snapshot = buildSnapshot(listOf(entry()), baseDensity = 160)
        val resources = context().resources

        val base = snapshot.newDrawable(0, resources, 160, false)
        assertEquals(20, base.intrinsicWidth)
        assertEquals(16, base.intrinsicHeight)

        val twice = snapshot.newDrawable(0, resources, 320, false)
        assertEquals(40, twice.intrinsicWidth)
        assertEquals(32, twice.intrinsicHeight)

        // Night only separates cache entries; the geometry is colour-independent.
        val night = snapshot.newDrawable(0, resources, 320, true)
        assertEquals(40, night.intrinsicWidth)
        assertEquals(32, night.intrinsicHeight)

        // A ConstantState mints a fresh Drawable, never the same instance.
        assertNotSame(twice, snapshot.newDrawable(0, resources, 320, false))
        assertTrue(twice is IosGlyphDrawable)
    }

    @Test
    fun `density argument zero falls back to the Resources density`() {
        val snapshot = buildSnapshot(listOf(entry()), baseDensity = 160)
        val drawable = snapshot.newDrawable(0, context().resources, 0, false)
        assertTrue(drawable.intrinsicWidth > 0)
        assertTrue(drawable.intrinsicHeight > 0)
    }

    // ----------------------------------------------------------------------------- diagnostics

    @Test
    fun `diagnostics are exactly one compact engine line`() {
        val lines = IosResourceEngine.diagnosticLines()
        assertEquals(1, lines.size)
        val line = lines.single()
        assertTrue("unexpected line: $line", line.startsWith("ios v2 resource: "))
        assertTrue(
            "unexpected fields: $line",
            Regex(
                "^ios v2 resource: family=(none|[a-z]+) active=(true|false) " +
                    "installed=(true|false) calls=\\d+ hits=\\d+ misses=\\d+ " +
                    "badPackage=\\d+ failures=\\d+ map=\\d+ maxMissNs=\\d+$"
            ).matches(line)
        )
    }

    // --------------------------------------------------------------------------------- gating

    @Test
    fun `activate ignores a non-v2 family and stays inactive`() {
        IosResourceEngine.activate(context(), rom("aosp"), synchronous = true)
        assertFalse(IosResourceEngine.isActive)
        assertTrue(IosResourceEngine.diagnosticLines().single().contains("family=none"))
    }

    // --------------------------------------------------------------------------------- install

    @Test
    fun `install is idempotent and does not throw`() {
        val loader = javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        IosResourceEngine.install(loader)
        IosResourceEngine.install(loader)
        assertEquals(1, IosResourceEngine.diagnosticLines().size)
    }

    @Test
    fun `an unmapped stock drawable is returned untouched`() {
        // Safe whether or not the Xposed hook could be installed in this JVM: with no active map the
        // hook must return the stock drawable, and with no hook the framework does it itself.
        IosResourceEngine.install(javaClass.classLoader ?: ClassLoader.getSystemClassLoader())
        val stock = context().resources.getDrawable(android.R.drawable.ic_delete, null)
        assertNotNull(stock)
        assertFalse(stock is IosGlyphDrawable)
    }
}
