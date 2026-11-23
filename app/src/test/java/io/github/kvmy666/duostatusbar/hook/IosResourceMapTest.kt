package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1 map contract: sorted primitive lookup, package guard, drawable kinds, and the hot miss path.
 *
 * The miss test is the R7 risk R1/R2 check (`.pm/R7-P0-map.md:466-471`): every notification icon
 * reaches `loadDrawable`, so one million non-mapped ids must not allocate a megabyte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosResourceMapTest {

    private fun entry(
        id: Int,
        slot: IosResourceSlot,
        widthDp: Float = 20f,
        heightDp: Float = 16f,
        pkg: String = "com.android.systemui"
    ) = ResolvedEntry(id, slot, widthDp, heightDp, pkg)

    private fun sample(): IosResourceEngine.Snapshot = buildSnapshot(
        listOf(
            entry(0x7f010003, IosResourceSlot.Wifi(2)),
            entry(0x7f010001, IosResourceSlot.Cell(3)),
            entry(0x7f010002, IosResourceSlot.Label("5G"))
        ),
        baseDensity = 160
    )

    @Test
    fun `unsorted entries are published with sorted ids`() {
        val snapshot = sample()
        assertEquals(listOf(0x7f010001, 0x7f010002, 0x7f010003), snapshot.ids.toList())
        assertEquals(3, snapshot.size)
    }

    @Test
    fun `binary search finds mapped ids and misses everything else`() {
        val snapshot = sample()
        assertEquals(0, snapshot.indexOf(0x7f010001))
        assertEquals(1, snapshot.indexOf(0x7f010002))
        assertEquals(2, snapshot.indexOf(0x7f010003))
        assertTrue(snapshot.indexOf(0x7f099999) < 0)
        assertTrue(snapshot.indexOf(0x01080000) < 0)
    }

    @Test
    fun `package mismatch is rejected before replacement`() {
        val snapshot = sample()
        assertTrue(snapshot.accepts(0, "com.android.systemui"))
        assertFalse(snapshot.accepts(0, "android"))
        assertEquals("com.android.systemui", snapshot.packageName)
    }

    @Test
    fun `slot kind maps to the right iOS drawable`() {
        val snapshot = sample()
        assertEquals(IosIconKind.CELL, snapshot.kindAt(0))
        assertEquals(IosIconKind.WIFI, snapshot.kindAt(1)) // LABEL is drawn on the Wi-Fi artboard
        assertEquals(IosIconKind.WIFI, snapshot.kindAt(2))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val cell = snapshot.newDrawable(0, context.resources, 160, false) as IosGlyphDrawable
        val label = snapshot.newDrawable(1, context.resources, 160, false) as IosGlyphDrawable
        val wifi = snapshot.newDrawable(2, context.resources, 160, false) as IosGlyphDrawable
        assertEquals(IosIconKind.CELL, cell.kind)
        assertEquals(IosIconKind.WIFI, label.kind)
        assertEquals(IosIconKind.WIFI, wifi.kind)
    }

    @Test
    fun `miss path allocates less than one megabyte for a million lookups`() {
        val snapshot = sample()

        // Android unit tests compile against android.jar, which has no java.lang.management, so the
        // JDK 21 ThreadMXBean is accessed reflectively at runtime. Only the two probe calls are
        // reflective; the measured loop is a direct primitive binary search.
        val factoryClass = try {
            Class.forName("java.lang.management.ManagementFactory")
        } catch (_: Throwable) {
            null
        }
        val beanClass = try {
            Class.forName("com.sun.management.ThreadMXBean")
        } catch (_: Throwable) {
            null
        }
        assumeTrue("ThreadMXBean is unavailable", factoryClass != null && beanClass != null)
        val bean = try {
            factoryClass!!.getMethod("getThreadMXBean").invoke(null)
        } catch (_: Throwable) {
            null
        }
        assumeTrue(
            "current-thread allocation measurement is unavailable",
            bean != null && beanClass!!.isInstance(bean)
        )
        val allocatedBytes = beanClass!!.getMethod("getCurrentThreadAllocatedBytes")

        // Warm up the lookup so class loading/JIT do not land inside the measured window.
        var warm = 0L
        repeat(1_000_000) { warm += snapshot.indexOf(0x7f099999) }
        val before = (allocatedBytes.invoke(bean) as Number).toLong()
        var misses = 0L
        repeat(1_000_000) { misses += snapshot.indexOf(0x7f099999) }
        val after = (allocatedBytes.invoke(bean) as Number).toLong()
        assertTrue("warm-up lookup disappeared: $warm", warm < 0L)
        assertTrue("lookups disappeared: $misses", misses < 0L)
        assertTrue(
            "one million miss lookups allocated ${after - before} bytes (limit 1048576)",
            after - before < 1_048_576L
        )
    }

    @Test
    fun `a null-free package for every entry is recorded`() {
        val snapshot = buildSnapshot(
            listOf(
                entry(0x01080965, IosResourceSlot.Wifi(0), pkg = "android"),
                entry(0x7f010001, IosResourceSlot.Cell(1), pkg = "com.android.systemui")
            ),
            baseDensity = 160
        )
        assertEquals("android", snapshot.packageAt(0))
        assertEquals("com.android.systemui", snapshot.packageAt(1))
        assertNotNull(snapshot.slotAt(1))
    }
}
