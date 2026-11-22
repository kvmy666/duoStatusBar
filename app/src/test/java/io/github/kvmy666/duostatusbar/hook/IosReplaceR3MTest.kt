package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.L
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * R3M runtime fixes that the main [IosReplaceHostTest] does not cover:
 *
 * - C11 (FIX 1): an unreachable provider must not tear the live iOS icons down.
 * - A1 (FIX 2): the caller-facing usability gate must let a host leave a hidden copy.
 * - A2/A5.3 (FIX 3): the QS dump describes the two mapped SIMs, not the subId-0 carrier rows.
 * - A5.3 (FIX 4): a strip with no recognised icon still produces one stock attempt line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosReplaceR3MTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
        IosReadings.resetForTest()
        IosDiagnostics.clearForTest()
        L.clearRecentForTest()
    }

    @After
    fun tearDown() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
        IosDrawHookInstaller.releaseWhenEmpty()
        IosReadings.resetForTest()
        IosDiagnostics.clearForTest()
        L.clearRecentForTest()
    }

    // ------------------------------------------------------------------ FIX 1 / C11 decision

    @Test
    fun `an unreachable provider keeps the live iOS icons and a reachable off still tears down`() {
        // Unreachable: stage reads OFF and the default snapshot owner is NONE, but the action is KEEP.
        val keep = settingsApplyAction(
            stage = DuoGuard.OFF,
            stageFromAdbOverride = false,
            providerUnreachable = true,
            servedFromBridge = false,
            hasElement = true
        )
        assertEquals(SettingsApplyAction.KEEP_UNREACHABLE, keep)
        assertTrue(keepIosOnUnreachable(BarOwner.NONE, keep))

        // Reachable off: a real answer must restore the stock icons.
        val off = settingsApplyAction(
            stage = DuoGuard.OFF,
            stageFromAdbOverride = false,
            providerUnreachable = false,
            servedFromBridge = false,
            hasElement = true
        )
        assertEquals(SettingsApplyAction.TEARDOWN, off)
        assertFalse(keepIosOnUnreachable(BarOwner.NONE, off))

        // A reachable other-owner answer is never "keep the iOS icons".
        assertFalse(keepIosOnUnreachable(BarOwner.DUO, SettingsApplyAction.KEEP_UNREACHABLE))
        assertFalse(keepIosOnUnreachable(BarOwner.IOS, SettingsApplyAction.KEEP_UNREACHABLE))
    }

    // ------------------------------------------------------------------ FIX 2 / A1 usability gate

    private class BatteryCopy(
        val strip: NamedGroup,
        val outer: StatBatteryMeterView,
        val leaf: NamedImageView,
        val original: Drawable
    )

    private fun batteryCopy(): BatteryCopy {
        val strip = NamedGroup(context, "system_icons")
        val outer = StatBatteryMeterView(context)
        val original = ColorDrawable(Color.RED)
        val leaf = NamedImageView(context, "battery_icon_view")
        leaf.setImageDrawable(original)
        outer.addView(leaf, FrameLayout.LayoutParams(79, 48))
        strip.addView(outer, LinearLayout.LayoutParams(90, 48))
        return BatteryCopy(strip, outer, leaf, original)
    }

    private fun layoutCopy(copy: BatteryCopy, x: Int) {
        copy.strip.layout(x, 0, x + 200, 48)
        copy.outer.layout(0, 0, 90, 48)
        copy.leaf.layout(0, 0, 79, 48)
    }

    private fun windowRoot(): FrameLayout {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return FrameLayout(activity).also { activity.setContentView(it) }
    }

    @Test
    fun `a hidden adopted copy is not usable and attachSurface retargets to the visible copy`() {
        val root = windowRoot()
        val hidden = batteryCopy().apply { strip.visibility = View.INVISIBLE }
        val shown = batteryCopy()
        root.addView(hidden.strip, FrameLayout.LayoutParams(200, 48))
        root.addView(shown.strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        layoutCopy(hidden, 0)
        layoutCopy(shown, 200)

        val host = host(surface = "retarget usable")
        try {
            assertTrue(host.attachIn(hidden.strip, null))
            assertFalse("the old guard would have short-circuited here", host.attachedUsableIn(root))

            assertTrue(host.attachSurface("retarget usable", root, "system_icons"))
            assertTrue(host.attachedUsableIn(root))
            assertSame("the hidden copy is restored exactly", hidden.original, hidden.leaf.drawable)
            assertTrue(
                "the visible copy draws the iOS battery",
                shown.leaf.drawable is IosBatteryDrawable
            )
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `a usable copy is reported usable and attachSurface tears nothing down`() {
        val root = windowRoot()
        val copy = batteryCopy()
        root.addView(copy.strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        layoutCopy(copy, 0)

        val host = host(surface = "usable")
        try {
            assertTrue(host.attachIn(copy.strip, null))
            assertTrue(host.attachedUsableIn(root))
            val installed = copy.leaf.drawable
            val records = host.recordCountForTest()

            assertTrue(host.attachSurface("usable", root, "system_icons"))
            assertEquals("no teardown/re-attach of a usable host", records, host.recordCountForTest())
            assertSame("the live drawable is kept", installed, copy.leaf.drawable)
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `alternating copies still re-target at most once per second`() {
        val root = windowRoot()
        val first = batteryCopy()
        val second = batteryCopy().apply { strip.visibility = View.INVISIBLE }
        root.addView(first.strip, FrameLayout.LayoutParams(200, 48))
        root.addView(second.strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        layoutCopy(first, 0)
        layoutCopy(second, 200)

        val host = host(surface = "alternating")
        try {
            assertTrue(host.attachSurface("alternating", root, "system_icons"))
            assertTrue(host.attachedUsableIn(root))

            // First swap: the visible copy changes, one planned re-target.
            first.strip.visibility = View.INVISIBLE
            second.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("alternating", root, "system_icons"))
            assertTrue(host.attachedUsableIn(root))

            // Immediate swap back: inside the 1 s throttle, so the hidden attachment is kept.
            second.strip.visibility = View.INVISIBLE
            first.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("alternating", root, "system_icons"))
            assertFalse("the second re-target is throttled", host.attachedUsableIn(root))
            assertSame("the shown copy stays stock while throttled", first.original, first.leaf.drawable)

            // After the throttle the host is allowed to move again.
            ShadowSystemClock.advanceBy(Duration.ofMillis(IosReplaceHost.MIN_RETARGET_INTERVAL_MS))
            assertTrue(host.attachSurface("alternating", root, "system_icons"))
            assertTrue(host.attachedUsableIn(root))
            assertSame("the previous copy is restored", second.original, second.leaf.drawable)
            assertTrue(first.leaf.drawable is IosBatteryDrawable)
        } finally {
            host.teardown()
        }
    }

    // ------------------------------------------------------------------ FIX 3 / A2 QS cell dump

    private class DualMobileTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val first: ModernStatusBarMobileViewWithSub,
        val second: ModernStatusBarMobileViewWithSub,
        val carriers: List<View>
    )

    /** The QS/shade strip: 12 subId-0 carrier rows, then the two mapped SIM slots. */
    private fun qsCarrierTree(): DualMobileTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val first = ModernStatusBarMobileViewWithSub(context, 1)
        val second = ModernStatusBarMobileViewWithSub(context, 2)
        first.addView(NamedImageView(context, "mobile_signal"), LinearLayout.LayoutParams(20, 20))
        second.addView(NamedImageView(context, "mobile_signal"), LinearLayout.LayoutParams(20, 20))
        val carriers = ArrayList<View>()
        for (i in 0 until 12) {
            val name = when (i % 3) {
                0 -> "mobile_in"
                1 -> "mobile_out"
                else -> "mobile_roaming"
            }
            val carrier = NamedView(context, name)
            carriers.add(carrier)
            strip.addView(carrier, LinearLayout.LayoutParams(10, 20))
        }
        strip.addView(first, LinearLayout.LayoutParams(20, 20))
        strip.addView(second, LinearLayout.LayoutParams(20, 20))
        root.addView(strip, FrameLayout.LayoutParams(140, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 140, 40)
        carriers.forEachIndexed { index, carrier ->
            carrier.layout(index * 10, 0, index * 10 + 10, 20)
        }
        first.layout(120, 0, 140, 20)
        second.layout(140, 0, 160, 20)
        (first.getChildAt(0) as View).layout(0, 0, 20, 20)
        (second.getChildAt(0) as View).layout(0, 0, 20, 20)
        return DualMobileTree(root, strip, first, second, carriers)
    }

    @Test
    fun `the QS dump has exactly the two mapped SIM cell lines and no carrier rows`() {
        val tree = qsCarrierTree()
        val host = host(surface = "qs cells", cellSource = { twoSimReading(1, 2) })
        try {
            assertTrue(host.attachIn(tree.strip, null))

            val cells = host.diagnosticLines().filter { " CELL " in it }
            assertEquals("one line per mapped SIM, not one per carrier row", 2, cells.size)
            assertTrue(
                "both mapped SIMs keep a positive subId: $cells",
                cells.all { Regex("subId=[1-9][0-9]*").containsMatchIn(it) }
            )
            assertFalse("no subId-0 carrier row reaches the dump", cells.any { "subId=-" in it })
        } finally {
            host.teardown()
        }
    }

    // ------------------------------------------------------------------ FIX 4 / A5.3 no-icons line

    @Test
    fun `a strip with no recognised icon still emits one stock attempt line`() {
        val root = windowRoot()
        val strip = NamedGroup(context, "system_icons")
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 100, 40)

        val host = host(surface = "no icons")
        try {
            assertFalse(host.attachSurface("no icons", root, "system_icons"))
            assertEquals(
                listOf("ios surface=no icons state=stock reason=no-icons"),
                IosDiagnostics.lines()
            )
        } finally {
            host.teardown()
        }
    }

    // ------------------------------------------------------------------ fixtures

    private fun host(
        surface: String = "main bar",
        batterySource: (Context) -> IosBatteryReading? = { IosBatteryReading(50, false, 0) },
        cellSource: (Context) -> CellReading = { noCellReading() }
    ): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        sizeOwner = IosSizeOwner(),
        isMainBar = surface == "main bar",
        surface = surface,
        batterySource = batterySource,
        cellSource = cellSource,
        wifiSource = { _, _, _, _, _ -> wifiSample() }
    )

    private fun wifiSample(): WifiSample = WifiSample(
        rssi = -55,
        calc5 = 4,
        stockBars = 4,
        dots = 2,
        on = true,
        active = true,
        validated = true,
        source = "active"
    )

    private fun noCellReading(): CellReading = CellReading(
        samples = emptyList(),
        count = 4,
        defaultRaw = -1,
        defaultMaxIndex = -1,
        defaultDots = 0,
        defaultSub = -1,
        defaultSrc = SignalMapping.SRC_UNAVAILABLE,
        airplane = false
    )

    private fun twoSimReading(firstSub: Int, secondSub: Int): CellReading = CellReading(
        samples = listOf(sample(0, firstSub, 3), sample(1, secondSub, 2)),
        count = 4,
        defaultRaw = 3,
        defaultMaxIndex = 4,
        defaultDots = 3,
        defaultSub = firstSub,
        defaultSrc = SignalMapping.SRC_PER_SUB,
        airplane = false
    )

    private fun sample(slot: Int, subId: Int, dots: Int): CellSample = CellSample(
        slot = slot,
        subscriptionId = subId,
        raw = dots,
        count = 4,
        maxIndex = 4,
        rat = "LTE",
        dbm = -90,
        metric = "level",
        dots = dots,
        src = SignalMapping.SRC_PER_SUB,
        airplane = false
    )

    private class StatBatteryMeterView(context: Context) : FrameLayout(context)

    class ModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) :
        LinearLayout(context) {
        fun getSubId(): Int = sub
    }

    private open class NamedView(context: Context, entry: String) : View(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    private class NamedImageView(context: Context, entry: String) : ImageView(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    private class NamedGroup(context: Context, entry: String) : LinearLayout(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
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

    private companion object {
        val NEXT_NAMED_ID = AtomicInteger(0x7f0d0000.toInt())
    }
}
