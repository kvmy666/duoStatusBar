package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R4 item 7: one shared broadcast receiver, a bounded refresh rate, and the power counters line.
 *
 * The hub tests need a real (Robolectric) Context so registration can be observed; the counter and
 * rate-guard tests are pure and use an injected clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosEventsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosBroadcastHub.resetForTest()
        IosReadings.resetForTest()
    }

    @After
    fun tearDown() {
        IosBroadcastHub.resetForTest()
        IosReadings.resetForTest()
    }

    // ------------------------------------------------------------------------ rate guard

    @Test
    fun `the rate guard never returns less than the remaining minimum spacing`() {
        assertEquals("no previous refresh keeps the debounce", 120L, nextRefreshDelayMs(0L, 5_000L, 120L, 250L))
        assertEquals("the first burst is spaced by the minimum", 250L, nextRefreshDelayMs(1_000L, 1_000L, 120L, 250L))
        assertEquals(150L, nextRefreshDelayMs(1_000L, 1_100L, 120L, 250L))
        assertEquals("the debounce wins when it is longer than the remaining spacing", 120L, nextRefreshDelayMs(1_000L, 1_200L, 120L, 250L))
        assertEquals("past the spacing window only the debounce remains", 120L, nextRefreshDelayMs(1_000L, 1_300L, 120L, 250L))
        assertEquals("a longer debounce is not shortened", 300L, nextRefreshDelayMs(1_000L, 1_000L, 300L, 250L))
    }

    @Test
    fun `a sustained burst cannot exceed four refreshes a second`() {
        val lastRefreshAt = 1_000L
        var earliest = Long.MAX_VALUE
        // Every request in the first 250 ms after a refresh must resolve to the spacing boundary.
        for (now in lastRefreshAt..lastRefreshAt + 240L step 10L) {
            val at = now + nextRefreshDelayMs(lastRefreshAt, now, 120L, MIN_REFRESH_SPACING_MS)
            earliest = minOf(earliest, at)
        }
        assertTrue(
            "no burst request may run before lastRefreshAt + 250 ms",
            earliest >= lastRefreshAt + MIN_REFRESH_SPACING_MS
        )
    }

    // ------------------------------------------------------------------------ counters

    @Test
    fun `counters keep a permanent total and a rolling one-minute window`() {
        var now = 0L
        val counters = IosPowerCounters({ now })
        counters.onAttach()
        counters.onRefresh()
        counters.onRefresh()
        counters.onInvalidate()
        counters.onInvalidate()
        counters.onSuppressedRefresh()
        now = 30_000L
        counters.onRefresh()

        val first = counters.line("main bar", 1)
        assertTrue(first, first.contains("attaches=1"))
        assertTrue(first, first.contains("refreshes=3/min=3"))
        assertTrue(first, first.contains("invalidates=2/min=2"))
        assertTrue(first, first.contains("suppressed=1 receivers=1"))

        now = 60_000L
        val later = counters.line("main bar", 1)
        assertTrue("totals are permanent", later.contains("refreshes=3"))
        assertTrue("only the 30 s sample stays in the window", later.contains("refreshes=3/min=1"))
        assertTrue("the two invalidates are now outside the window", later.contains("invalidates=2/min=0"))
    }

    // ------------------------------------------------------------------------ hub

    @Test
    fun `the hub registers exactly one receiver and invalidates once per event`() {
        val seen = ArrayList<String>()
        val target = object : IosBroadcastTarget {
            override fun onIosBroadcast(action: String) {
                seen.add(action)
            }
        }

        IosBroadcastHub.subscribe(context, target)
        IosBroadcastHub.subscribe(context, target)
        assertEquals("a second subscribe must not add a receiver", 1, IosBroadcastHub.receiverCountForTest())

        val before = IosReadings.generationForTest()
        IosBroadcastHub.dispatchForTest(Intent.ACTION_TIME_TICK)
        assertEquals("one hub event invalidates exactly once", before + 1, IosReadings.generationForTest())
        assertEquals(listOf(Intent.ACTION_TIME_TICK), seen)

        IosBroadcastHub.dispatchForTest("io.github.kvmy666.duostatusbar.NOT_LISTENED")
        assertEquals("an unrelated action is not an invalidate", before + 1, IosReadings.generationForTest())
        assertEquals(1, seen.size)

        IosBroadcastHub.unsubscribe(target)
        assertEquals("the last unsubscribe drops the receiver", 0, IosBroadcastHub.receiverCountForTest())
        IosBroadcastHub.dispatchForTest(Intent.ACTION_TIME_TICK)
        assertEquals("no subscriber means no invalidate", before + 1, IosReadings.generationForTest())
    }

    @Test
    fun `the hub dispatches to every subscribed host`() {
        val first = ArrayList<String>()
        val second = ArrayList<String>()
        val a = object : IosBroadcastTarget {
            override fun onIosBroadcast(action: String) {
                first.add(action)
            }
        }
        val b = object : IosBroadcastTarget {
            override fun onIosBroadcast(action: String) {
                second.add(action)
            }
        }
        IosBroadcastHub.subscribe(context, a)
        IosBroadcastHub.subscribe(context, b)
        IosBroadcastHub.dispatchForTest(Intent.ACTION_SCREEN_OFF)
        assertEquals(listOf(Intent.ACTION_SCREEN_OFF), first)
        assertEquals(listOf(Intent.ACTION_SCREEN_OFF), second)
    }

    @Test
    fun `two all-overlay hosts share one hub receiver and release it on teardown`() {
        val treeA = batteryTree()
        val treeB = batteryTree()
        val hostA = overlayHost("overlay-a")
        val hostB = overlayHost("overlay-b")
        try {
            assertTrue(hostA.attachIn(treeA.strip, treeA.root))
            assertEquals("one attached delegate registers one hub receiver", 1, IosBroadcastHub.receiverCountForTest())

            assertTrue(hostB.attachIn(treeB.strip, treeB.root))
            assertEquals(
                "two attached delegates still share one process-wide receiver",
                1,
                IosBroadcastHub.receiverCountForTest()
            )

            hostA.teardown()
            assertEquals("one host remains subscribed", 1, IosBroadcastHub.receiverCountForTest())
            hostB.teardown()
            assertEquals("the last teardown releases the hub receiver", 0, IosBroadcastHub.receiverCountForTest())
        } finally {
            hostA.teardown()
            hostB.teardown()
        }
    }

    // ------------------------------------------------------------------------ all-overlay fixtures

    private class StubElement(context: Context) : IosElement {
        override val ui = View(context)
        override val rendererName = "stub"
        override var isReady = true
        override val canDraw = true

        override fun start(): Boolean = true

        override fun render(v: IosVisual) = Unit

        override fun setRenderActive(active: Boolean) = Unit

        override fun teardown() = Unit
    }

    private class BatteryMeterView(context: Context) : FrameLayout(context) {
        val icon = ImageView(context)

        init {
            addView(icon)
        }
    }

    private class Tree(val root: FrameLayout, val strip: LinearLayout)

    /** A one-battery strip in a plain overlay parent, the all-overlay delegate's attach input. */
    private fun batteryTree(): Tree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = BatteryMeterView(context)
        battery.icon.setImageDrawable(ColorDrawable(Color.RED))
        strip.addView(battery, LinearLayout.LayoutParams(80, 40))
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 40)
        battery.layout(0, 5, 80, 45)
        return Tree(root, strip)
    }

    private fun overlayHost(surface: String): IosIconHost = IosIconHost(
        context = context,
        surface = surface,
        canvasRenderers = { c, _ -> StubElement(c) },
        batterySource = { IosBatteryReading(50, false, 0) }
    )
}
