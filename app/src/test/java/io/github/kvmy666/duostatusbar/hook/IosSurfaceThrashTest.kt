package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * IOS-R2 0b: the attach/teardown guard belongs to the surface, not to a host instance.
 *
 * The shade fired a layout pass per frame and built a new host each time; an instance-owned guard was
 * reset before it could trip. This test trips the surface through host A, then proves a fresh host B
 * on the same surface is still blocked while a different surface is unaffected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosSurfaceThrashTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosSurfaceThrash.clearForTest()
        IosSurfaceOwners.clearForTest()
    }

    @After
    fun tearDown() {
        IosSurfaceThrash.clearForTest()
        IosSurfaceOwners.clearForTest()
    }

    private class StatBatteryMeterView(context: Context) : FrameLayout(context)

    private class NamedImageView(context: Context, entry: String) : ImageView(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    @Suppress("DEPRECATION")
    private class FakeResources(
        parent: Resources,
        private val names: Map<Int, String>
    ) : Resources(parent.assets, parent.displayMetrics, parent.configuration) {
        override fun getResourceEntryName(id: Int): String =
            names[id] ?: super.getResourceEntryName(id)
    }

    private class Tree(val root: FrameLayout, val strip: LinearLayout)

    private fun tree(): Tree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val outer = StatBatteryMeterView(context)
        val leaf = NamedImageView(context, "battery_icon_view")
        leaf.setImageDrawable(ColorDrawable(Color.RED))
        outer.addView(leaf, FrameLayout.LayoutParams(79, 48))
        strip.addView(outer, LinearLayout.LayoutParams(90, 48))
        root.addView(strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 48)
        outer.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 48)
        return Tree(root, strip)
    }

    @Test
    fun `a tripped surface blocks a fresh host but not another surface`() {
        val first = tree()
        val second = tree()
        val hostA = IosReplaceHost(
            context,
            classLoader = null,
            surface = "main bar",
            batterySource = { IosBatteryReading(50, false, 0) }
        )
        assertTrue(hostA.attachIn(first.strip, null))

        // Alternate strips on the same surface: each attach is one lifecycle event.
        val strips = listOf(first.strip, second.strip)
        var attached = true
        var flip = 1
        while (attached && flip < 12) {
            attached = hostA.attachIn(strips[flip % 2], null)
            flip++
        }
        assertFalse("eight lifecycle events trip the surface", attached)
        assertTrue(hostA.isThrashTrippedForTest())

        // A brand-new host object inherits the trip: it did not reset the counters or the trip.
        val hostB = IosReplaceHost(
            context,
            classLoader = null,
            surface = "main bar",
            batterySource = { IosBatteryReading(50, false, 0) }
        )
        assertTrue(hostB.isThrashTrippedForTest())
        assertFalse(hostB.attachIn(first.strip, null))

        // A different surface has its own state.
        val other = tree()
        val hostC = IosReplaceHost(
            context,
            classLoader = null,
            surface = "extra",
            batterySource = { IosBatteryReading(50, false, 0) }
        )
        assertFalse(hostC.isThrashTrippedForTest())
        assertTrue(hostC.attachIn(other.strip, null))

        // clearForTest isolates the process-wide registry between tests.
        IosSurfaceThrash.clearForTest()
        assertFalse(hostB.isThrashTrippedForTest())
        assertTrue(hostB.attachIn(first.strip, null))
    }

    /** Feeds one event exactly as noteLifecycle does: only a rapid event reaches the guard. */
    private fun feed(surface: String, attach: Boolean, now: Long): Boolean {
        if (!IosSurfaceThrash.lifecycleEvent(surface, attach, now)) return false
        if (!IosSurfaceThrash.lifecycle(surface).record(now)) return false
        IosSurfaceThrash.trip(surface, null)
        return true
    }

    @Test
    fun `eight rapid attach teardown cycles trip the surface`() {
        var now = 1_000L
        var tripped = false
        repeat(8) {
            tripped = feed("rapid", attach = true, now = now) || tripped
            now += 100
            tripped = feed("rapid", attach = false, now = now) || tripped
            now += 100
        }
        assertTrue("eight rapid cycles trip", tripped)
        assertTrue(IosSurfaceThrash.isTripped("rapid"))
        assertTrue(IosSurfaceThrash.lifecycle("rapid").isTripped())
    }

    @Test
    fun `eight slow open close cycles never trip the surface`() {
        var now = 1_000L
        repeat(8) {
            assertFalse(feed("slow", attach = true, now = now))
            now += 2_000
            assertFalse(feed("slow", attach = false, now = now))
            now += 2_000
        }
        assertFalse("normal open/close is not churn", IosSurfaceThrash.isTripped("slow"))
        assertEquals(0, IosSurfaceThrash.lifecycle("slow").count())
    }

    @Test
    fun `a new host inherits rapid churn while another surface does not`() {
        var now = 1_000L
        repeat(8) {
            feed("main bar", attach = true, now)
            now += 100
            feed("main bar", attach = false, now)
            now += 100
        }
        assertTrue(IosSurfaceThrash.isTripped("main bar"))

        val same = IosReplaceHost(
            context,
            classLoader = null,
            surface = "main bar",
            batterySource = { IosBatteryReading(50, false, 0) }
        )
        assertTrue("a fresh host inherits the surface trip", same.isThrashTrippedForTest())

        val other = IosReplaceHost(
            context,
            classLoader = null,
            surface = "extra",
            batterySource = { IosBatteryReading(50, false, 0) }
        )
        assertFalse("another surface has its own state", other.isThrashTrippedForTest())
    }

    @Test
    fun `a fresh trip blocks the same window immediately and expires after the cooldown`() {
        val t = tree()
        val now = 5_000L
        IosSurfaceThrash.trip("cooldown", t.root, now)
        assertTrue(IosSurfaceThrash.isTripped("cooldown"))

        assertTrue("the same window is blocked inside the cooldown", IosSurfaceThrash.shouldBlock("cooldown", t.strip, now + 1))
        assertFalse(
            "a probe is allowed once the cooldown elapses",
            IosSurfaceThrash.shouldBlock("cooldown", t.strip, now + 10_000L)
        )
        assertFalse("the probe reset the trip", IosSurfaceThrash.isTripped("cooldown"))
    }

    @Test
    fun `a trip without a window never blocks`() {
        val t = tree()
        val now = 5_000L
        IosSurfaceThrash.trip("no-root", null, now)
        assertFalse(IosSurfaceThrash.shouldBlock("no-root", t.strip, now + 1))
        assertFalse(IosSurfaceThrash.isTripped("no-root"))
    }

    @Test
    fun `a different window is allowed immediately`() {
        val first = tree()
        val second = tree()
        val now = 5_000L
        IosSurfaceThrash.trip("window", first.root, now)
        assertFalse(IosSurfaceThrash.shouldBlock("window", second.strip, now + 1))
        assertFalse(IosSurfaceThrash.isTripped("window"))
    }

    private companion object {
        val NEXT_ID = AtomicInteger(0x7f0f1001)
    }
}
