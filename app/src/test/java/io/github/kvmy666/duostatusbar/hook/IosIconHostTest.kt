package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * IOS-B1: the per-stock-icon host, tested off the phone.
 *
 * The first test is the fail-silent promise the owner cares about: with the capability seam closed,
 * attach changes no stock pixel at all. The rest pin the real path with an injected fake element — glyph
 * bounds, drawable suppression and restore, visibility follow, idle pause, mid-attach failure and the
 * SIM id gate — independently of the renderer implementation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosIconHostTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosBroadcastHub.resetForTest()
        IosSurfaceThrash.clearForTest()
        IosSurfaceOwners.clearForTest()
    }

    @After
    fun tearDown() {
        IosBroadcastHub.resetForTest()
        IosSurfaceThrash.clearForTest()
        IosSurfaceOwners.clearForTest()
    }

    // ---- stock views named exactly as the measured families ----

    /** Samsung family: a plain ImageView leaf, suppressed by CLEAR_DRAWABLE (IOS-INV §2.5). */
    private class BatteryMeterView(context: Context) : FrameLayout(context) {
        val icon: ImageView = ImageView(context)

        init {
            addView(icon)
        }
    }

    private class WifiIconView(context: Context) : ImageView(context)

    private class MobileIconView(context: Context) : LinearLayout(context)

    private class ModernStatusBarMobileView(context: Context) : LinearLayout(context)

    /**
     * Public on purpose: [IosIconHost] reads `getSubId()` reflectively, and a private class's public
     * method is not invokable from outside its file.
     */
    class ModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) : LinearLayout(context) {
        fun getSubId(): Int = sub
    }

    // ---- measured OPLUS anatomy (the measured OPLUS view tree, device dump) ----

    /** 0×0 outer; the real content is its `wifi_group` child. */
    private class OplusModernStatusBarWifiView(context: Context) : FrameLayout(context)

    /** 0×0 outer with a readable SIM id; the real content is its `mobile_group` child. */
    class OplusModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) :
        FrameLayout(context) {
        fun getSubId(): Int = sub
    }

    /** Battery outer that owns `battery_icon_view` (82×51) inside an 86×63 slot. */
    private class StatBatteryMeterView(context: Context) : FrameLayout(context)

    /**
     * A view whose id resolves to an entry name without adding resources to the app, so BY_ID leaf
     * resolution can be exercised for the HyperOS mobile family.
     */
    private open class NamedView(context: Context, entry: String) : View(context) {
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

    /** A named ViewGroup, so the measured OPLUS content groups resolve by id. */
    private open class NamedGroup(context: Context, entry: String) : FrameLayout(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    /** A named ImageView leaf, so the Wi-Fi family resolves its `wifi_signal` drawing surface. */
    private class NamedImageView(context: Context, entry: String) : ImageView(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    /** HyperOS Wi-Fi anatomy: an outer view whose `wifi_signal` leaf carries the stock drawing. */
    private class ModernStatusBarWifiView(context: Context) : FrameLayout(context) {
        val signal: ImageView = NamedImageView(context, "wifi_signal")

        init {
            addView(signal)
        }
    }

    /** The injected drawing: records every call, so the host's decisions are observable. */
    private class FakeElement(
        context: Context,
        val kind: IosIconKind,
        private val startResult: Boolean = true,
        private val drawable: Boolean = true
    ) : View(context), IosElement {

        val renders = ArrayList<IosVisual>()
        val active = ArrayList<Boolean>()
        var started = false
        var tornDown = 0

        override val ui: View get() = this
        override val rendererName: String get() = "fake"
        override val isReady: Boolean get() = started
        override val canDraw: Boolean get() = drawable

        override fun start(): Boolean {
            started = startResult
            return startResult
        }

        override fun render(v: IosVisual) {
            renders.add(v)
        }

        override fun setRenderActive(active: Boolean) {
            this.active.add(active)
        }

        override fun teardown() {
            tornDown++
        }
    }

    // ---- helpers ----

    private class BatteryTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val battery: BatteryMeterView,
        val original: Drawable
    )

    private fun batteryTree(): BatteryTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = BatteryMeterView(context)
        val original = ColorDrawable(Color.RED)
        battery.icon.setImageDrawable(original)
        strip.addView(battery, LinearLayout.LayoutParams(80, 40))
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 40)
        battery.layout(0, 5, 80, 45)
        return BatteryTree(root, strip, battery, original)
    }

    private class RetryTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val battery: BatteryMeterView,
        val original: Drawable
    )

    /** A battery strip whose stock icon is still 0×0, as a first hook call can see it. */
    private fun unlaidBatteryTree(): RetryTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = BatteryMeterView(context)
        val original = ColorDrawable(Color.RED)
        battery.icon.setImageDrawable(original)
        strip.addView(battery, LinearLayout.LayoutParams(80, 40))
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 40)
        return RetryTree(root, strip, battery, original)
    }

    // ---- R3 A1 usable-copy resolution (a real window, so isShown() is meaningful) ----

    private class SurfaceCopy(
        val strip: NamedGroup,
        val battery: BatteryMeterView,
        val original: Drawable
    )

    /** An activity-backed root, so a child strip can report `isShown` like a real shade surface. */
    private fun windowRoot(): FrameLayout {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        return root
    }

    /** Adds one `system_icons` copy (a battery strip) under [root] and returns its stock facts. */
    private fun addSurfaceCopy(root: FrameLayout): SurfaceCopy {
        val strip = NamedGroup(root.context, "system_icons")
        val battery = BatteryMeterView(root.context)
        val original = ColorDrawable(Color.RED)
        battery.icon.setImageDrawable(original)
        strip.addView(battery, LinearLayout.LayoutParams(80, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 40))
        return SurfaceCopy(strip, battery, original)
    }

    private fun layoutSurface(copy: SurfaceCopy, left: Int) {
        copy.strip.layout(left, 0, left + 200, 40)
        copy.battery.layout(0, 5, 80, 45)
    }

    private fun wifiTree(): WifiTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val wifi = ModernStatusBarWifiView(context)
        val original = ColorDrawable(Color.GREEN)
        wifi.signal.setImageDrawable(original)
        strip.addView(wifi, LinearLayout.LayoutParams(40, 40))
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 40)
        wifi.layout(0, 0, 40, 40)
        return WifiTree(root, strip, wifi, original)
    }

    private class WifiTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val wifi: ModernStatusBarWifiView,
        val original: Drawable
    )

    private fun wifiSample(validated: Boolean, dots: Int = 2): WifiSample = WifiSample(
        rssi = -55,
        calc5 = 4,
        stockBars = 4,
        dots = dots,
        on = true,
        active = true,
        validated = validated,
        source = "active"
    )

    private fun host(
        canvasRenderers: (Context, IosIconKind) -> IosElement = { c, k -> FakeElement(c, k) },
        drawingReady: Boolean = true,
        batterySource: (Context) -> IosBatteryReading? = { IosBatteryReading(50, false, 0) },
        cellSource: (Context) -> CellReading = { noCellReading() },
        wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
            { c, level, on, active, validated -> WifiReader.sample(c, level, on, active, validated) },
        isMainBar: Boolean = true,
        sizeOwner: IosSizeOwner = IosSizeOwner(),
        surface: String = "overlay",
        idleDelayMs: Long = 3_000L
    ): IosIconHost = IosIconHost(
        context = context,
        sizeOwner = sizeOwner,
        isMainBar = isMainBar,
        canvasRenderers = canvasRenderers,
        drawingReady = drawingReady,
        surface = surface,
        batterySource = batterySource,
        cellSource = cellSource,
        wifiSource = wifiSource,
        idleDelayMs = idleDelayMs
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

    private fun twoSimReading(): CellReading = CellReading(
        samples = listOf(
            CellSample(
                slot = 0,
                subscriptionId = 5,
                raw = 3,
                count = 4,
                maxIndex = 4,
                rat = "LTE",
                dbm = -90,
                metric = "level",
                dots = 3,
                src = SignalMapping.SRC_PER_SUB,
                airplane = false
            ),
            CellSample(
                slot = 1,
                subscriptionId = 6,
                raw = 2,
                count = 4,
                maxIndex = 4,
                rat = "LTE",
                dbm = -100,
                metric = "level",
                dots = 2,
                src = SignalMapping.SRC_PER_SUB,
                airplane = false
            )
        ),
        count = 4,
        defaultRaw = 3,
        defaultMaxIndex = 4,
        defaultDots = 3,
        defaultSub = 5,
        defaultSrc = SignalMapping.SRC_PER_SUB,
        airplane = false
    )

    private fun oneSimReading(): CellReading =
        twoSimReading().copy(samples = twoSimReading().samples.take(1), defaultSub = 5)

    /** The QS/shade reading: two mapped SIMs, with the measured subscription ids 2 (data) and 1. */
    private fun qsTwoSimReading(): CellReading {
        val base = twoSimReading()
        return base.copy(
            samples = listOf(
                base.samples[0].copy(subscriptionId = 2),
                base.samples[1].copy(subscriptionId = 1)
            ),
            defaultSub = 2
        )
    }

    private class QsCellTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val carriers: List<NamedView>,
        val first: OplusModernStatusBarMobileViewWithSub,
        val firstGroup: NamedGroup,
        val firstSignal: NamedImageView,
        val second: OplusModernStatusBarMobileViewWithSub,
        val secondGroup: NamedGroup,
        val secondSignal: NamedImageView
    )

    /**
     * R3 A2: the visible QS/shade strip. Twelve carrier-group rows (`mobile_in`/`mobile_out`/
     * `mobile_roaming`, all `getSubId()` = 0 and with no `mobile_signal` leaf) share the strip with the
     * two measured OPLUS SIM outers (subIds 2 and 1).
     */
    private fun qsCellTree(): QsCellTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)

        val carriers = ArrayList<NamedView>()
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

        fun mobile(sub: Int): Triple<OplusModernStatusBarMobileViewWithSub, NamedGroup, NamedImageView> {
            val outer = OplusModernStatusBarMobileViewWithSub(context, sub)
            val group = NamedGroup(context, "mobile_group")
            val signal = NamedImageView(context, "mobile_signal")
            outer.addView(group, FrameLayout.LayoutParams(60, 63))
            group.addView(signal, FrameLayout.LayoutParams(60, 51))
            strip.addView(outer, LinearLayout.LayoutParams(60, 63))
            return Triple(outer, group, signal)
        }

        val (first, firstGroup, firstSignal) = mobile(2)
        val (second, secondGroup, secondSignal) = mobile(1)

        root.addView(strip, FrameLayout.LayoutParams(300, 63))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 300, 63)
        carriers.forEachIndexed { index, carrier ->
            carrier.layout(index * 10, 0, 10 + index * 10, 20)
        }
        // Measured OPLUS outers are 0×0; the visible groups carry the size and translation.
        first.layout(0, 0, 0, 0)
        firstGroup.layout(0, 0, 60, 63)
        firstGroup.translationX = 130f
        firstSignal.layout(2, 6, 62, 57)
        second.layout(0, 0, 0, 0)
        secondGroup.layout(0, 0, 60, 63)
        secondGroup.translationX = 200f
        secondSignal.layout(2, 6, 62, 57)
        return QsCellTree(
            root, strip, carriers, first, firstGroup, firstSignal, second, secondGroup, secondSignal
        )
    }

    private class CellTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val first: ModernStatusBarMobileViewWithSub,
        val second: ModernStatusBarMobileViewWithSub,
        val firstLeaf: NamedView,
        val secondLeaf: NamedView
    )

    private fun cellTree(firstSub: Int = 5, secondSub: Int = 6): CellTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val first = ModernStatusBarMobileViewWithSub(context, firstSub)
        val second = ModernStatusBarMobileViewWithSub(context, secondSub)
        val firstLeaf = NamedView(context, "mobile_signal")
        val secondLeaf = NamedView(context, "mobile_signal")
        first.addView(firstLeaf)
        second.addView(secondLeaf)
        strip.addView(first, LinearLayout.LayoutParams(60, 40))
        strip.addView(second, LinearLayout.LayoutParams(60, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 40)
        first.layout(0, 0, 60, 40)
        second.layout(60, 0, 120, 40)
        return CellTree(root, strip, first, second, firstLeaf, secondLeaf)
    }

    private class OplusTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val wifi: OplusModernStatusBarWifiView,
        val wifiGroup: NamedGroup,
        val wifiSignal: NamedImageView,
        val mobile: OplusModernStatusBarMobileViewWithSub,
        val mobileGroup: NamedGroup,
        val mobileSignal: NamedImageView,
        val battery: StatBatteryMeterView,
        val batteryIcon: NamedImageView
    )

    /**
     * The measured OxygenOS tree: each outer is 0×0, its visible group carries the real content, and
     * the two groups sit at different `translationX` values (OOS positions them that way).
     */
    private fun oplusTree(): OplusTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)

        val wifi = OplusModernStatusBarWifiView(context)
        val wifiGroup = NamedGroup(context, "wifi_group")
        val wifiSignal = NamedImageView(context, "wifi_signal")
        wifi.addView(wifiGroup, FrameLayout.LayoutParams(73, 63))
        wifiGroup.addView(wifiSignal, FrameLayout.LayoutParams(70, 51))

        val mobile = OplusModernStatusBarMobileViewWithSub(context, 5)
        val mobileGroup = NamedGroup(context, "mobile_group")
        val mobileSignal = NamedImageView(context, "mobile_signal")
        mobile.addView(mobileGroup, FrameLayout.LayoutParams(60, 63))
        mobileGroup.addView(mobileSignal, FrameLayout.LayoutParams(60, 51))

        val battery = StatBatteryMeterView(context)
        val batteryIcon = NamedImageView(context, "battery_icon_view")
        battery.addView(batteryIcon, FrameLayout.LayoutParams(82, 51))

        strip.addView(wifi)
        strip.addView(mobile)
        strip.addView(battery)
        root.addView(strip, FrameLayout.LayoutParams(100, 63))

        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 63)
        wifi.layout(0, 0, 0, 0)
        wifiGroup.layout(0, 0, 73, 63)
        wifiGroup.translationX = 7f
        wifiSignal.layout(3, 6, 73, 57)
        mobile.layout(0, 0, 0, 0)
        mobileGroup.layout(0, 0, 60, 63)
        mobileGroup.translationX = 12f
        mobileSignal.layout(2, 6, 62, 57)
        battery.layout(0, 0, 86, 63)
        batteryIcon.layout(3, 6, 85, 57)
        return OplusTree(
            root, strip, wifi, wifiGroup, wifiSignal, mobile, mobileGroup, mobileSignal,
            battery, batteryIcon
        )
    }

    // ---- capability gate ----

    @Test
    fun `a closed gate leaves every stock pixel untouched`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = BatteryMeterView(context)
        val batteryDrawable = ColorDrawable(Color.RED)
        battery.icon.setImageDrawable(batteryDrawable)
        val wifi = WifiIconView(context)
        val wifiDrawable = ColorDrawable(Color.GREEN)
        wifi.setImageDrawable(wifiDrawable)
        val mobile = MobileIconView(context)
        strip.addView(battery, LinearLayout.LayoutParams(80, 40))
        strip.addView(wifi, LinearLayout.LayoutParams(40, 40))
        strip.addView(mobile, LinearLayout.LayoutParams(40, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(800, 0, 1000, 40)
        battery.layout(0, 0, 80, 40)
        wifi.layout(90, 0, 130, 40)
        mobile.layout(140, 0, 180, 40)
        battery.alpha = 0.75f

        val batteryParams = battery.layoutParams
        val wifiParams = wifi.layoutParams
        val mobileParams = mobile.layoutParams
        val batterySize = Pair(battery.width, battery.height)
        val wifiSize = Pair(wifi.width, wifi.height)
        val mobileSize = Pair(mobile.width, mobile.height)

        var built = 0
        val gated = host(
            drawingReady = false,
            canvasRenderers = { c, k -> built++; FakeElement(c, k) }
        )
        assertFalse(gated.attach(root))
        assertFalse(gated.attached)
        // The explicit test entry is gated too, so it cannot be used to bypass the capability promise.
        assertFalse(gated.attachIn(strip, root))
        assertFalse(gated.attached)
        assertEquals(0, built)

        assertEquals(1, root.childCount)
        assertEquals(3, strip.childCount)
        assertStock(battery, View.VISIBLE, 0.75f, batteryParams, batteryDrawable)
        assertStock(wifi, View.VISIBLE, 1f, wifiParams, wifiDrawable)
        assertSame(mobileParams, mobile.layoutParams)
        assertEquals(View.VISIBLE, mobile.visibility)
        assertEquals(1f, mobile.alpha)
        assertNull(mobile.background)
        assertEquals(batterySize, Pair(battery.width, battery.height))
        assertEquals(wifiSize, Pair(wifi.width, wifi.height))
        assertEquals(mobileSize, Pair(mobile.width, mobile.height))
    }

    @Test
    fun `the real renderers suppress only after one of them started`() {
        val tree = batteryTree()
        // The gate is opened through the constructor seam. Rive may be unavailable on the JVM, in
        // which case the real Canvas renderer takes over; either way the stock drawing is replaced by
        // a glyph that can draw, never left blank.
        val host = IosIconHost(
            context = context,
            drawingReady = true,
            batterySource = { IosBatteryReading(50, false, 0) },
            cellSource = { noCellReading() }
        )
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertTrue(host.attached)
        assertEquals(2, tree.root.childCount)
        // AOSP battery leaves are suppressed with a cleared drawable; others with alpha 0. Either
        // way the stock drawing is gone and the glyph took its place.
        assertTrue(
            "stock battery drawing suppressed",
            tree.battery.alpha == 0f || tree.battery.icon.drawable == null
        )
        assertEquals(80, tree.battery.width)
        host.teardown()
        assertEquals(1f, tree.battery.alpha)
        assertSame(tree.original, tree.battery.icon.drawable)
    }

    private fun assertStock(
        view: View,
        visibility: Int,
        alpha: Float,
        params: ViewGroup.LayoutParams?,
        drawable: Drawable?
    ) {
        assertEquals(visibility, view.visibility)
        assertEquals(alpha, view.alpha)
        assertSame(params, view.layoutParams)
        if (view is ImageView) assertSame(drawable, view.drawable)
    }

    // ---- size owner ----

    @Test
    fun `size owner reuses the main pin and caps by the extra bar height`() {
        val owner = IosSizeOwner()
        owner.pin(IosIconKind.BATTERY, 80, 40)

        // Same height: exactly the pinned size.
        assertEquals(Pair(80, 40), owner.sizeFor(IosIconKind.BATTERY, 80, 40))
        // Shorter extra bar: both dimensions scale with its height (80 * 30 / 40 = 60).
        assertEquals(Pair(60, 30), owner.sizeFor(IosIconKind.BATTERY, 60, 30))
        // Taller extra bar never grows the glyph.
        assertEquals(Pair(80, 40), owner.sizeFor(IosIconKind.BATTERY, 100, 60))
        // Rounding stays integral and non-negative.
        assertEquals(Pair(66, 33), owner.sizeFor(IosIconKind.BATTERY, 50, 33))
    }

    @Test
    fun `size owner falls back to this bar and survives zero or negative sizes`() {
        val owner = IosSizeOwner()
        assertEquals(Pair(70, 50), owner.sizeFor(IosIconKind.WIFI, 70, 50))

        owner.pin(IosIconKind.WIFI, 0, 0) // not a measurement: ignored
        assertEquals(Pair(70, 50), owner.sizeFor(IosIconKind.WIFI, 70, 50))

        owner.pin(IosIconKind.WIFI, 70, 50)
        assertEquals(Pair(0, 30), owner.sizeFor(IosIconKind.WIFI, 0, 30))
        assertEquals(Pair(0, 0), owner.sizeFor(IosIconKind.WIFI, -5, -9))
    }

    // ---- full path ----

    @Test
    fun `full path draws at the stock bounds and restores the exact drawing on teardown`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { IosBatteryReading(57, false, 0) }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertTrue(host.attached)
        assertEquals(1, created.size)
        val glyph = created[0]

        val params = glyph.layoutParams as FrameLayout.LayoutParams
        assertEquals(80, params.width)
        assertEquals(40, params.height)
        assertEquals(900, params.leftMargin)
        assertEquals(5, params.topMargin)
        assertEquals(View.VISIBLE, glyph.visibility)

        // CLEAR_DRAWABLE: the Samsung leaf is cleared, not hidden or resized.
        assertNull(tree.battery.icon.drawable)
        assertEquals(View.VISIBLE, tree.battery.visibility)
        assertEquals(80, tree.battery.width)
        assertEquals(40, tree.battery.height)

        assertTrue(glyph.renders.any { it.part == IosPart.BATTERY })
        // One transparent layer was added to the overlay parent as its last child.
        assertEquals(2, tree.root.childCount)
        assertTrue(tree.root.getChildAt(1) is FrameLayout)

        val stockParams = tree.battery.layoutParams
        host.teardown()

        assertFalse(host.attached)
        assertSame(tree.original, tree.battery.icon.drawable)
        assertSame(stockParams, tree.battery.layoutParams)
        assertEquals(1, tree.root.childCount)
        assertNull(glyph.parent)
        assertTrue(glyph.tornDown > 0)

        // Idempotent: a second teardown is a no-op.
        host.teardown()
        assertEquals(1, tree.root.childCount)
        assertSame(tree.original, tree.battery.icon.drawable)
    }

    @Test
    fun `percent inside reaches the rendered battery and defaults off`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        assertTrue(host.attachIn(tree.strip, tree.root))

        val glyph = created[0]
        assertEquals(
            false,
            glyph.renders.last { it.part == IosPart.BATTERY }.percentInside
        )

        host.refreshSettings(ModuleSettings.DEFAULT.copy(iosPercentInside = true))

        assertTrue(glyph.renders.last { it.part == IosPart.BATTERY }.percentInside)

        host.refreshSettings(ModuleSettings.DEFAULT)

        assertFalse(glyph.renders.last { it.part == IosPart.BATTERY }.percentInside)
        host.teardown()
    }

    // ---- visibility ----

    @Test
    fun `glyph follows the stock visibility without touching the stock layout`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        assertTrue(host.attachIn(tree.strip, tree.root))
        val glyph = created[0]
        val stockParams = tree.battery.layoutParams

        tree.battery.visibility = View.GONE
        host.refresh()
        assertEquals(View.GONE, glyph.visibility)
        assertSame(stockParams, tree.battery.layoutParams)
        assertEquals(80, tree.battery.width)
        assertEquals(40, tree.battery.height)

        tree.battery.visibility = View.VISIBLE
        host.refresh()
        assertEquals(View.VISIBLE, glyph.visibility)
        assertSame(stockParams, tree.battery.layoutParams)
        assertEquals(80, tree.battery.width)
        assertEquals(40, tree.battery.height)

        host.teardown()
        assertEquals(View.VISIBLE, tree.battery.visibility)
    }

    // ---- idle pause (C8) ----

    @Test
    fun `screen off pauses every glyph and screen on resumes it`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        assertTrue(host.attachIn(tree.strip, tree.root))

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(false), created[0].active)

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(false, true), created[0].active)

        host.teardown()
        assertEquals(listOf(false, true), created[0].active)
    }

    // ---- fail silent ----

    @Test
    fun `a renderer that cannot start leaves no glyph and no suppression`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        // The Canvas renderer is the only one; this test pins the "nothing could draw" case.
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k, startResult = false).also { created += it } }
        )

        assertFalse(host.attachIn(tree.strip, tree.root))
        assertFalse(host.attached)
        assertEquals(1, created.size)
        assertNull(created[0].parent)
        assertTrue(created[0].tornDown > 0)

        assertSame(tree.original, tree.battery.icon.drawable)
        assertEquals(1f, tree.battery.alpha)
        assertEquals(View.VISIBLE, tree.battery.visibility)
        assertEquals(1, tree.root.childCount)
        assertEquals(1, tree.strip.childCount)
    }

    // ---- battery read failure (M5) ----

    @Test
    fun `a battery that was never read is dropped and the stock drawing restored`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { null }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        val glyph = created[0]

        // No snapshot ever existed, so there is nothing to stand in for the stock drawing: the glyph
        // is gone and the ImageView the skin cleared is restored exactly (rule 5).
        assertTrue(glyph.renders.isEmpty())
        assertNull(glyph.parent)
        assertTrue(glyph.tornDown > 0)
        assertSame(tree.original, tree.battery.icon.drawable)
        assertEquals(1f, tree.battery.alpha)
        assertEquals(View.VISIBLE, tree.battery.visibility)

        host.teardown()
        assertSame(tree.original, tree.battery.icon.drawable)
        assertEquals(1f, tree.battery.alpha)
        assertEquals(View.VISIBLE, tree.battery.visibility)
    }

    @Test
    fun `a null battery read after a good one keeps the last snapshot`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        var reading: IosBatteryReading? = IosBatteryReading(57, false, 0)
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { reading }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        val glyph = created[0]
        assertEquals(1, glyph.renders.size)

        reading = null
        host.refresh()

        // M7: keep the last snapshot; the record, its glyph and the suppression stay untouched.
        assertEquals(1, glyph.renders.size)
        assertTrue(glyph.parent != null)
        assertEquals(View.VISIBLE, glyph.visibility)
        assertNull(tree.battery.icon.drawable)
        assertEquals(1f, tree.battery.alpha)
        assertEquals(View.VISIBLE, tree.battery.visibility)

        host.teardown()
        assertSame(tree.original, tree.battery.icon.drawable)
    }

    @Test
    fun `a battery read that returns after a null renders again`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        var reading: IosBatteryReading? = IosBatteryReading(57, false, 0)
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { reading }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        val glyph = created[0]

        reading = null
        host.refresh()
        assertEquals(1, glyph.renders.size)

        reading = IosBatteryReading(60, false, 0)
        host.refresh()

        assertEquals(2, glyph.renders.size)
        assertEquals(0.6f, glyph.renders.last().batteryLevel, 0.0001f)
        assertTrue(glyph.parent != null)
        assertNull(tree.battery.icon.drawable)

        host.teardown()
        assertSame(tree.original, tree.battery.icon.drawable)
    }

    // ---- Wi-Fi validation (M6) ----

    @Test
    fun `unvalidated wifi drops the record and restores the stock drawing`() {
        val tree = wifiTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            wifiSource = { _, _, _, _, _ -> wifiSample(validated = false) }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        val glyph = created[0]

        // The mapping hides a connected-but-unvalidated network; nothing was drawn, so the stock
        // Wi-Fi drawing must be restored rather than suppressed into a hole (M6).
        assertTrue(glyph.renders.isEmpty())
        assertNull(glyph.parent)
        assertTrue(glyph.tornDown > 0)
        assertEquals(1f, tree.wifi.signal.alpha)
        assertSame(tree.original, tree.wifi.signal.drawable)
        assertEquals(View.VISIBLE, tree.wifi.visibility)

        host.teardown()
        assertEquals(1f, tree.wifi.signal.alpha)
        assertSame(tree.original, tree.wifi.signal.drawable)
    }

    @Test
    fun `validated wifi keeps the record and renders the level`() {
        val tree = wifiTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            wifiSource = { _, _, _, _, _ -> wifiSample(validated = true, dots = 2) }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        val glyph = created[0]

        assertTrue(glyph.parent != null)
        val visual = glyph.renders.first { it.part == IosPart.WIFI }
        assertEquals(2f, visual.wifiLevel, 0.0001f)
        assertEquals(0f, tree.wifi.signal.alpha)

        host.teardown()
        assertEquals(1f, tree.wifi.signal.alpha)
    }

    // ---- cellular SIM identity ----

    @Test
    fun `cellular without getSubId stays stock when two SIMs are active`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val mobile = ModernStatusBarMobileView(context)
        val leaf = NamedView(context, "mobile_signal")
        leaf.alpha = 1f
        mobile.addView(leaf)
        strip.addView(mobile, LinearLayout.LayoutParams(60, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 40)
        mobile.layout(0, 0, 60, 40)

        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { null },
            cellSource = { twoSimReading() }
        )

        assertFalse(host.attachIn(strip, root))
        assertTrue(created.isEmpty())
        assertEquals(1f, leaf.alpha)
        assertEquals(1, root.childCount)
        assertEquals(View.VISIBLE, mobile.visibility)
        host.teardown()
    }

    @Test
    fun `one mobile view cannot span two SIMs so both stay stock`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val mobile = ModernStatusBarMobileViewWithSub(context, 5)
        val leaf = NamedView(context, "mobile_signal")
        leaf.alpha = 1f
        mobile.addView(leaf)
        strip.addView(mobile, LinearLayout.LayoutParams(60, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(900, 0, 1000, 40)
        mobile.layout(0, 0, 60, 40)

        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )

        assertFalse(host.attachIn(strip, root))
        assertTrue(created.isEmpty())
        assertEquals(1f, leaf.alpha)
        assertEquals(1, root.childCount)
        assertEquals(View.VISIBLE, mobile.visibility)

        host.teardown()
        assertEquals(1f, leaf.alpha)
    }

    @Test
    fun `two mobile views draw one stacked cluster at the data slot`() {
        val tree = cellTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.count { it.kind == IosIconKind.CELL })
        val glyph = created.first { it.kind == IosIconKind.CELL }
        assertEquals(1, created.count { it.kind == IosIconKind.CELL && it.parent != null })
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(0f, tree.secondLeaf.alpha)

        val visual = glyph.renders.last { it.part == IosPart.CELL }
        assertEquals(IosCellMode.DUAL, visual.cellMode)
        assertEquals(IosCellRole.BOTH, visual.cellRole)
        // R4A item 2: raw 3 of 4 is full (4 dots) and raw 2 of 4 is 3 dots on the iOS artboard.
        assertEquals(4f, visual.cellBars, 0.0001f)
        assertEquals(3f, visual.cellDots, 0.0001f)

        // One block anchored to the DATA slot, not the two-slot union.
        val params = glyph.layoutParams as FrameLayout.LayoutParams
        assertEquals(0, params.leftMargin)
        assertEquals(0, params.topMargin)
        assertEquals(60, params.width)
        assertEquals(40, params.height)

        host.teardown()
    }

    @Test
    fun `the cluster follows one two one SIM transitions`() {
        val tree = cellTree()
        val created = ArrayList<FakeElement>()
        var reading = oneSimReading()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { reading }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        var live = created.filter { it.kind == IosIconKind.CELL && it.parent != null }
        assertEquals(1, live.size)
        val single = live[0]
        assertEquals(IosCellMode.SINGLE, single.renders.last { it.part == IosPart.CELL }.cellMode)
        assertEquals(60, (single.layoutParams as FrameLayout.LayoutParams).width)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)

        reading = twoSimReading()
        host.refresh()
        live = created.filter { it.kind == IosIconKind.CELL && it.parent != null }
        assertEquals(1, live.size)
        assertTrue("a new cluster replaced the single", live[0] !== single)
        assertEquals(IosCellMode.DUAL, live[0].renders.last { it.part == IosPart.CELL }.cellMode)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(0f, tree.secondLeaf.alpha)

        reading = oneSimReading()
        host.refresh()
        live = created.filter { it.kind == IosIconKind.CELL && it.parent != null }
        assertEquals(1, live.size)
        assertEquals(IosCellMode.SINGLE, live[0].renders.last { it.part == IosPart.CELL }.cellMode)
        assertEquals(60, (live[0].layoutParams as FrameLayout.LayoutParams).width)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)

        host.teardown()
    }

    @Test
    fun `a gone second slot falls back to a single over the visible slot`() {
        val tree = cellTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(0f, tree.secondLeaf.alpha)

        tree.second.visibility = View.GONE
        host.refresh()

        val live = created.filter { it.kind == IosIconKind.CELL && it.parent != null }
        assertEquals(1, live.size)
        val visual = live[0].renders.last { it.part == IosPart.CELL }
        assertEquals(IosCellMode.SINGLE, visual.cellMode)
        assertEquals(IosCellRole.DATA, visual.cellRole)
        assertEquals(60, (live[0].layoutParams as FrameLayout.LayoutParams).width)
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)

        host.teardown()
    }

    @Test
    fun `two SIMs with an unreadable second slot id draw no glyph`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val first = ModernStatusBarMobileViewWithSub(context, 5)
        val second = ModernStatusBarMobileView(context)
        val firstLeaf = NamedView(context, "mobile_signal")
        val secondLeaf = NamedView(context, "mobile_signal")
        first.addView(firstLeaf)
        second.addView(secondLeaf)
        strip.addView(first, LinearLayout.LayoutParams(60, 40))
        strip.addView(second, LinearLayout.LayoutParams(60, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 40)
        first.layout(0, 0, 60, 40)
        second.layout(60, 0, 120, 40)

        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )

        assertFalse(host.attachIn(strip, root))
        assertTrue(created.isEmpty())
        assertEquals(1f, firstLeaf.alpha)
        assertEquals(1f, secondLeaf.alpha)
        assertEquals(1, root.childCount)
        assertEquals(View.VISIBLE, first.visibility)
        assertEquals(View.VISIBLE, second.visibility)
        host.teardown()
    }

    @Test
    fun `two SIMs with duplicate slot ids draw no glyph`() {
        val tree = cellTree(firstSub = 5, secondSub = 5)
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )

        assertFalse(host.attachIn(tree.strip, tree.root))
        assertTrue(created.isEmpty())
        assertEquals(1f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)
        assertEquals(1, tree.root.childCount)
        host.teardown()
    }

    @Test
    fun `cluster teardown restores both leaves and removes the one element`() {
        val tree = cellTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )
        assertTrue(host.attachIn(tree.strip, tree.root))
        val glyph = created.first { it.kind == IosIconKind.CELL }

        host.teardown()

        assertEquals(1f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)
        assertNull(glyph.parent)
        assertTrue(glyph.tornDown > 0)
        assertEquals(1, tree.root.childCount)
        assertFalse(host.attached)
    }

    // ---- R3 A2: QS/shade carrier rows on the overlay surface ----

    @Test
    fun `a QS strip with subId-0 carrier rows still draws one BOTH block for the two mapped SIMs`() {
        val qs = qsCellTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { null },
            cellSource = { qsTwoSimReading() }
        )

        val carrierLayouts = qs.carriers.map { it.layoutParams }
        assertTrue(host.attachIn(qs.strip, qs.root))

        // A2: the 12 unreadable carrier rows are not part of the plan, so the two SIM slots map.
        val cellGlyphs = created.filter { it.kind == IosIconKind.CELL }
        assertEquals("one BOTH block, never two single rows", 1, cellGlyphs.size)
        val visual = cellGlyphs[0].renders.last { it.part == IosPart.CELL }
        assertEquals(IosCellMode.DUAL, visual.cellMode)
        assertEquals(IosCellRole.BOTH, visual.cellRole)

        // Both mapped OPLUS SIMs are replaced; their content groups and every carrier row stay as is.
        assertEquals(View.INVISIBLE, qs.first.visibility)
        assertEquals(View.INVISIBLE, qs.second.visibility)
        assertTrue(qs.firstGroup.visibility == View.VISIBLE && qs.firstSignal.visibility == View.VISIBLE)
        assertTrue(qs.secondGroup.visibility == View.VISIBLE && qs.secondSignal.visibility == View.VISIBLE)
        assertTrue(
            "carrier rows stay stock",
            qs.carriers.all { it.visibility == View.VISIBLE && it.alpha == 1f }
        )
        assertEquals(carrierLayouts, qs.carriers.map { it.layoutParams })

        host.teardown()

        assertEquals(View.VISIBLE, qs.first.visibility)
        assertEquals(View.VISIBLE, qs.second.visibility)
        assertTrue(
            "carrier rows are untouched on teardown",
            qs.carriers.all { it.visibility == View.VISIBLE && it.alpha == 1f }
        )
        assertEquals(carrierLayouts, qs.carriers.map { it.layoutParams })
    }

    @Test
    fun `the QS overlay dump reports exactly the two mapped CELL lines`() {
        val qs = qsCellTree()
        val host = host(
            batterySource = { null },
            cellSource = { qsTwoSimReading() }
        )
        assertTrue(host.attachIn(qs.strip, qs.root))

        val cells = host.diagnosticLines().filter { " CELL " in it }
        assertEquals(2, cells.size)
        assertEquals(
            "only the two mapped SIM slots may be reported",
            setOf("2", "1"),
            cells.map { it.substringAfter("subId=").trim() }.toSet()
        )
        assertTrue("no carrier row may leak into the dump", cells.none { it.contains("subId=-") })

        host.teardown()
    }

    // ---- replaced surface, attach retry, invisible slots ----

    @Test
    fun `a replaced root tears down the old layer and adopts the new strip`() {
        val first = batteryTree()
        val second = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })

        assertTrue(host.attachIn(first.strip, first.root))
        assertEquals(1, created.size)
        val oldGlyph = created[0]
        assertNull(first.battery.icon.drawable)
        assertEquals(2, first.root.childCount)

        assertTrue(host.attachIn(second.strip, second.root))

        // Old tree: exact stock restored and its layer removed.
        assertSame(first.original, first.battery.icon.drawable)
        assertEquals(1, first.root.childCount)
        assertNull(oldGlyph.parent)
        assertTrue(oldGlyph.tornDown > 0)
        // New tree: stock suppressed and the layer lives in the new root.
        assertNull(second.battery.icon.drawable)
        assertEquals(2, second.root.childCount)
        assertTrue(second.root.getChildAt(1) is FrameLayout)
        assertEquals(2, created.size)
        assertTrue(created[1].parent != null)

        host.teardown()
    }

    @Test
    fun `an attach that sees 0x0 icons retries after layout`() {
        val tree = unlaidBatteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })

        assertFalse(host.attachIn(tree.strip, tree.root))
        assertTrue(created.isEmpty())
        assertEquals(1, tree.root.childCount)

        tree.battery.layout(0, 5, 80, 45)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        assertTrue(host.attached)
        assertEquals(1, created.size)
        assertTrue(created[0].parent != null)
        assertNull(tree.battery.icon.drawable)

        host.teardown()
    }

    @Test
    fun `a failed attach is not retried after teardown`() {
        val tree = unlaidBatteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })

        assertFalse(host.attachIn(tree.strip, tree.root))
        host.teardown()

        tree.battery.layout(0, 5, 80, 45)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        assertFalse(host.attached)
        assertTrue(created.isEmpty())
        assertEquals(1, tree.root.childCount)
        assertSame(tree.original, tree.battery.icon.drawable)
    }

    @Test
    fun `an invisible second cell slot stays stock and the visible one is single`() {
        val tree = cellTree()
        tree.second.visibility = View.INVISIBLE
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))

        val cellGlyphs = created.filter { it.kind == IosIconKind.CELL }
        assertEquals(1, cellGlyphs.size)
        val visual = cellGlyphs[0].renders.last { it.part == IosPart.CELL }
        assertEquals(IosCellMode.SINGLE, visual.cellMode)
        assertEquals(IosCellRole.DATA, visual.cellRole)
        assertEquals(60, (cellGlyphs[0].layoutParams as FrameLayout.LayoutParams).width)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)
        assertEquals(View.INVISIBLE, tree.second.visibility)

        host.teardown()
        assertEquals(1f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)
        assertEquals(View.INVISIBLE, tree.second.visibility)
    }

    @Test
    fun `a cell visibility change switches single and cluster without layout churn`() {
        val tree = cellTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { twoSimReading() }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.count { it.kind == IosIconKind.CELL && it.parent != null })
        assertEquals(0f, tree.secondLeaf.alpha)

        // OEM hides the second slot: the cluster becomes one row over the visible slot.
        tree.second.visibility = View.INVISIBLE
        tree.second.layout(60, 1, 120, 41)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        var live = created.filter { it.kind == IosIconKind.CELL && it.parent != null }
        assertEquals(1, live.size)
        assertEquals(IosCellMode.SINGLE, live[0].renders.last { it.part == IosPart.CELL }.cellMode)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(1f, tree.secondLeaf.alpha)
        assertEquals(2, created.count { it.kind == IosIconKind.CELL })

        // Repeated layout passes must not multiply glyphs or refreshes.
        repeat(4) { i -> tree.second.layout(60, 2 + i, 120, 42 + i) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(2, created.count { it.kind == IosIconKind.CELL })

        // OEM reveals it again: the plan returns to the stacked cluster. Measure first so the
        // LinearLayout lays its children out at their real sizes; a bare layout() would reuse the
        // never-measured 0×0 dimensions and the host would not see a drawable second slot.
        tree.second.visibility = View.VISIBLE
        tree.strip.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY)
        )
        tree.strip.layout(0, 1, 200, 41)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        live = created.filter { it.kind == IosIconKind.CELL && it.parent != null }
        assertEquals(1, live.size)
        assertEquals(IosCellMode.DUAL, live[0].renders.last { it.part == IosPart.CELL }.cellMode)
        assertEquals(0f, tree.firstLeaf.alpha)
        assertEquals(0f, tree.secondLeaf.alpha)
        assertEquals(3, created.count { it.kind == IosIconKind.CELL })

        host.teardown()
    }

    // ---- adoption and loss after attach ----

    @Test
    fun `an icon added after attach is adopted and suppressed`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            wifiSource = { _, _, _, _, _ -> wifiSample(validated = true) }
        )
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)

        val wifi = ModernStatusBarWifiView(context)
        val original = ColorDrawable(Color.GREEN)
        wifi.signal.setImageDrawable(original)
        tree.strip.addView(wifi, LinearLayout.LayoutParams(40, 40))
        wifi.layout(0, 0, 40, 40)

        host.onStockIconAdded(wifi)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))

        assertEquals(2, created.size)
        val glyph = created[1]
        assertEquals(IosIconKind.WIFI, glyph.kind)
        assertTrue(glyph.renders.any { it.part == IosPart.WIFI })
        assertTrue(glyph.parent != null)
        assertEquals(0f, wifi.signal.alpha)

        host.teardown()
        assertEquals(1f, wifi.signal.alpha)
        assertSame(original, wifi.signal.drawable)
    }

    @Test
    fun `a removed icon drops its glyph and restores the stock drawing`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        val glyph = created[0]

        tree.strip.removeView(tree.battery)
        host.onStockIconRemoved(tree.battery)

        assertTrue(glyph.tornDown > 0)
        assertNull(glyph.parent)
        assertSame(tree.original, tree.battery.icon.drawable)

        // A refresh must not resurrect a record whose stock view is gone.
        host.refresh()
        assertEquals(1, created.size)
        assertSame(tree.original, tree.battery.icon.drawable)
        assertEquals(1f, tree.battery.alpha)
    }

    @Test
    fun `unvalidated wifi is re-adopted after validation`() {
        val tree = wifiTree()
        val created = ArrayList<FakeElement>()
        var validated = false
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            wifiSource = { _, _, _, _, _ -> wifiSample(validated = validated) }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        val dropped = created[0]
        assertNull(dropped.parent)
        assertSame(tree.original, tree.wifi.signal.drawable)

        validated = true
        host.refresh()

        // reconcile re-adopts and renders a new glyph in one pass.
        assertEquals(2, created.size)
        val glyph = created[1]
        assertTrue(glyph.parent != null)
        assertTrue(glyph.renders.isNotEmpty())
        assertEquals(0f, tree.wifi.signal.alpha)

        host.teardown()
        assertEquals(1f, tree.wifi.signal.alpha)
    }

    @Test
    fun `a battery read that was never available is re-adopted when it recovers`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        var reading: IosBatteryReading? = null
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { reading }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        assertNull(created[0].parent)
        assertSame(tree.original, tree.battery.icon.drawable)

        reading = IosBatteryReading(57, false, 0)
        host.refresh()

        assertEquals(2, created.size)
        val glyph = created[1]
        assertTrue(glyph.parent != null)
        assertTrue(glyph.renders.isNotEmpty())
        assertNull(tree.battery.icon.drawable)

        host.teardown()
    }

    // ---- measured OPLUS content geometry (iOS-LAG step 2) ----

    @Test
    fun `the OPLUS outers are hidden and glyphs use the translated content bounds`() {
        val tree = oplusTree()
        val created = ArrayList<FakeElement>()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            cellSource = { oneSimReading() },
            wifiSource = { _, _, _, _, _ -> wifiSample(validated = true, dots = 2) }
        )

        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(3, created.size)

        val wifi = created.first { it.kind == IosIconKind.WIFI }
        val cell = created.first { it.kind == IosIconKind.CELL }
        val battery = created.first { it.kind == IosIconKind.BATTERY }

        // wifi_group is at translationX=7 (900 + 7), mobile_group at 12 (900 + 12): the two glyphs no
        // longer share one leftMargin as they did when offsetIn ignored translationX.
        val wifiParams = wifi.layoutParams as FrameLayout.LayoutParams
        assertEquals(907, wifiParams.leftMargin)
        assertEquals(0, wifiParams.topMargin)
        assertEquals(73, wifiParams.width)
        assertEquals(63, wifiParams.height)

        val cellParams = cell.layoutParams as FrameLayout.LayoutParams
        assertEquals(912, cellParams.leftMargin)
        assertEquals(0, cellParams.topMargin)
        assertEquals(60, cellParams.width)
        assertEquals(63, cellParams.height)
        assertTrue("Wi-Fi and cell must not share a left margin", wifiParams.leftMargin != cellParams.leftMargin)

        // Battery content is the 82×51 icon, not the 86×63 outer.
        val batteryParams = battery.layoutParams as FrameLayout.LayoutParams
        assertEquals(903, batteryParams.leftMargin)
        assertEquals(6, batteryParams.topMargin)
        assertEquals(82, batteryParams.width)
        assertEquals(51, batteryParams.height)

        val wifiGroupParams = tree.wifiGroup.layoutParams
        val mobileGroupParams = tree.mobileGroup.layoutParams
        val batteryIconParams = tree.batteryIcon.layoutParams

        // INVISIBLE_KEEP_SLOT: the stock outer is hidden, but the slot and every child are untouched.
        assertEquals(View.INVISIBLE, tree.wifi.visibility)
        assertEquals(View.INVISIBLE, tree.mobile.visibility)
        assertEquals(View.INVISIBLE, tree.battery.visibility)
        assertTrue(tree.wifiGroup.visibility == View.VISIBLE && tree.wifiSignal.visibility == View.VISIBLE)
        assertTrue(tree.mobileGroup.visibility == View.VISIBLE && tree.mobileSignal.visibility == View.VISIBLE)
        assertTrue(tree.batteryIcon.visibility == View.VISIBLE)
        assertEquals(1f, tree.wifiGroup.alpha)
        assertEquals(1f, tree.mobileGroup.alpha)
        assertEquals(1f, tree.batteryIcon.alpha)
        assertSame(wifiGroupParams, tree.wifiGroup.layoutParams)
        assertSame(mobileGroupParams, tree.mobileGroup.layoutParams)
        assertSame(batteryIconParams, tree.batteryIcon.layoutParams)
        assertEquals(73, tree.wifiGroup.width)
        assertEquals(63, tree.wifiGroup.height)
        assertEquals(60, tree.mobileGroup.width)
        assertEquals(63, tree.mobileGroup.height)
        assertEquals(82, tree.batteryIcon.width)
        assertEquals(51, tree.batteryIcon.height)

        host.teardown()
        assertEquals(View.VISIBLE, tree.wifi.visibility)
        assertEquals(View.VISIBLE, tree.mobile.visibility)
        assertEquals(View.VISIBLE, tree.battery.visibility)
    }

    @Test
    fun `attachedAlive is true only for the same in-tree window root`() {
        val first = FrameLayout(context)
        val second = FrameLayout(context)
        // A strip still in the tree (parent != null) and in the same window root is alive even when
        // the window itself transiently detached during a shade close (R4 item 1).
        assertTrue(IosAttachDecision.alive(true, true, true, first, first))
        assertFalse(IosAttachDecision.alive(false, true, true, first, first))
        assertFalse("a strip removed from its parent is not alive", IosAttachDecision.alive(true, false, true, first, first))
        assertFalse("a detached layer is not alive", IosAttachDecision.alive(true, true, false, first, first))
        assertFalse(IosAttachDecision.alive(true, true, true, first, second))
        assertFalse(IosAttachDecision.alive(true, true, true, null, first))
        assertFalse(IosAttachDecision.alive(true, true, true, first, null))
    }

    @Test
    fun `keepHidden keeps a live host only on the same window with an attached layer`() {
        assertTrue(IosAttachDecision.keepHidden(isAttached = true, sameWindow = true, layerAttached = true))
        assertFalse("nothing to keep", IosAttachDecision.keepHidden(false, true, true))
        assertFalse("a new window/identity rebuilds", IosAttachDecision.keepHidden(true, false, true))
        assertFalse("a detached overlay layer cannot keep stock hidden", IosAttachDecision.keepHidden(true, true, false))
    }

    @Test
    fun `a parented strip survives a transient window detach`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        try {
            assertTrue(host.attachIn(tree.strip, tree.root))
            assertFalse("the synthetic tree is not attached to a window", tree.strip.isAttachedToWindow)
            assertTrue(
                "a strip still parented in the same root is alive, even without a window",
                host.attachedAlive(tree.root)
            )
            // Re-applying the same state reuses the same drawable instead of rebuilding it.
            assertTrue(host.attachIn(tree.strip, tree.root))
            assertEquals(1, created.size)

            tree.root.removeView(tree.strip)
            assertFalse("a strip removed from its parent is not alive", host.attachedAlive(tree.root))
        } finally {
            host.teardown()
        }
    }

    // attachedIn() additionally requires strip.isAttachedToWindow. Robolectric never attaches this
    // synthetic tree to a window, so the pure predicate above pins the same condition; the host call
    // sites (keyguard/header/window) are exercised through the anti-thrash test below.

    @Test
    fun `anti-thrash refuses repeated attach teardown on the same window`() {
        // Two same-window containers replace each other, so each call costs one teardown + one attach.
        // (An oscillation between two usable copies of one id is now absorbed by the R3 A1 retarget
        // policy instead, so this pins genuine window-level churn.)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = FrameLayout(activity)
        activity.setContentView(window)
        val first = FrameLayout(activity)
        val second = FrameLayout(activity)
        for (root in listOf(first, second)) {
            val strip = NamedGroup(activity, "system_icons")
            val battery = BatteryMeterView(activity)
            battery.icon.setImageDrawable(ColorDrawable(Color.RED))
            strip.addView(battery, FrameLayout.LayoutParams(80, 40))
            root.addView(strip, FrameLayout.LayoutParams(100, 40))
            window.addView(root, FrameLayout.LayoutParams(100, 40))
        }
        window.layout(0, 0, 1000, 100)
        first.layout(0, 0, 100, 40)
        second.layout(100, 0, 200, 40)
        for (root in listOf(first, second)) {
            val strip = root.getChildAt(0) as ViewGroup
            strip.layout(0, 0, 100, 40)
            strip.getChildAt(0).layout(0, 5, 80, 45)
        }

        val host = host()
        assertTrue("both containers resolve their strip", host.canAttachSurface(first, "system_icons"))
        assertTrue(host.canAttachSurface(second, "system_icons"))
        assertFalse("a missing strip is not attachable", host.canAttachSurface(window, "not_here"))
        assertTrue(host.attachSurface("first", first, "system_icons"))

        // Alternate the containers in the same window until the guard's window fills (8 events).
        val roots = listOf(second, first)
        var result = true
        var flips = 0
        while (result && flips < 12) {
            result = host.attachSurface("flip", roots[flips % 2], "system_icons")
            flips++
        }
        assertFalse("the guard trips within the cycle bound", result)
        assertFalse("a tripped guard leaves nothing attached", host.attached)

        // The same window is backed off: no new attach is attempted.
        assertFalse(host.attachSurface("flip", first, "system_icons"))
        assertFalse(host.attached)

        // B1: the ROM re-adds/re-removes icons inside the hide loop, so icon churn must not clear the
        // trip. The backed-off host stays refused and detached after both callbacks.
        val stock = (first.getChildAt(0) as ViewGroup).getChildAt(0)
        host.onStockIconAdded(stock)
        host.onStockIconRemoved(stock)
        assertTrue("icon churn must not clear the trip", host.isThrashTrippedForTest())
        assertFalse(host.attachSurface("after-icon-churn", first, "system_icons"))
        assertFalse(host.attached)
    }

    // ---- R3 A1: usable copy + re-target throttle ----

    @Test
    fun `an all-overlay surface attaches the shown copy and never the hidden one`() {
        val root = windowRoot()
        // The hidden copy is first in DFS order, which is exactly the copy the old lookup adopted.
        val hidden = addSurfaceCopy(root)
        val shown = addSurfaceCopy(root)
        hidden.strip.visibility = View.INVISIBLE
        root.layout(0, 0, 1000, 100)
        layoutSurface(hidden, 0)
        layoutSurface(shown, 200)

        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        try {
            assertTrue(host.attachSurface("all-overlay", root, "system_icons"))
            assertTrue(host.attached)
            assertEquals(1, created.size)
            assertTrue(created[0].parent != null)
            assertSame(
                "the hidden first copy is never adopted",
                hidden.original,
                hidden.battery.icon.drawable
            )
            assertEquals(View.INVISIBLE, hidden.strip.visibility)
            assertNull("the shown copy is suppressed", shown.battery.icon.drawable)
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `a shown copy retargets a hidden attachment once and throttles the next call`() {
        val root = windowRoot()
        val first = addSurfaceCopy(root)
        val second = addSurfaceCopy(root)
        second.strip.visibility = View.INVISIBLE
        root.layout(0, 0, 1000, 100)
        layoutSurface(first, 0)
        layoutSurface(second, 200)

        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        try {
            assertTrue(host.attachSurface("retarget", root, "system_icons"))
            assertEquals(1, created.size)
            assertNull(first.battery.icon.drawable)

            // The first copy collapses and the second becomes the usable one.
            first.strip.visibility = View.INVISIBLE
            second.strip.visibility = View.VISIBLE

            assertTrue(host.attachSurface("retarget", root, "system_icons"))
            assertEquals("one re-target, one new glyph", 2, created.size)
            assertSame("the old copy is restored exactly", first.original, first.battery.icon.drawable)
            assertNull("the new copy is suppressed", second.battery.icon.drawable)
            val live = created[1]
            assertTrue(live.parent != null)

            // The caller's 500 ms cadence must not re-attach inside the 1 s re-target throttle.
            assertTrue(host.attachSurface("retarget", root, "system_icons"))
            assertEquals("an immediate second call is throttled", 2, created.size)
            assertSame("the live glyph is kept", live, created[1])
            assertNull(second.battery.icon.drawable)
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `a hidden surface with no usable copy keeps its live attachment`() {
        val root = windowRoot()
        val copy = addSurfaceCopy(root)
        root.layout(0, 0, 1000, 100)
        layoutSurface(copy, 0)

        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        try {
            assertTrue(host.attachSurface("hidden", root, "system_icons"))
            assertEquals(1, created.size)
            val live = created[0]

            copy.strip.visibility = View.INVISIBLE

            assertTrue(host.attachSurface("hidden", root, "system_icons"))
            assertTrue("a merely hidden surface is kept, not torn down", host.attached)
            assertEquals(1, created.size)
            assertSame(live, created[0])
            assertTrue(live.parent != null)
            assertNull("the live suppression is kept", copy.battery.icon.drawable)
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `a real settings change resets the retarget throttle`() {
        val root = windowRoot()
        val first = addSurfaceCopy(root)
        val second = addSurfaceCopy(root)
        second.strip.visibility = View.INVISIBLE
        root.layout(0, 0, 1000, 100)
        layoutSurface(first, 0)
        layoutSurface(second, 200)

        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        try {
            assertTrue(host.attachSurface("settings", root, "system_icons"))
            first.strip.visibility = View.INVISIBLE
            second.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("settings", root, "system_icons"))
            assertEquals(2, created.size)

            // Swap back immediately: the throttle keeps the current (now hidden) attachment.
            second.strip.visibility = View.INVISIBLE
            first.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("settings", root, "system_icons"))
            assertEquals(2, created.size)
            assertSame("the usable copy stays stock while throttled", first.original, first.battery.icon.drawable)
            assertNull("the hidden attachment is kept", second.battery.icon.drawable)

            host.refreshSettings(ModuleSettings.DEFAULT.copy(revision = ModuleSettings.DEFAULT.revision + 1L))

            assertTrue(host.attachSurface("settings", root, "system_icons"))
            assertEquals("a real settings change unlocks the re-target", 3, created.size)
            assertNull(first.battery.icon.drawable)
            assertSame(second.original, second.battery.icon.drawable)
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `teardown resets the retarget throttle for the next call`() {
        val root = windowRoot()
        val first = addSurfaceCopy(root)
        val second = addSurfaceCopy(root)
        second.strip.visibility = View.INVISIBLE
        root.layout(0, 0, 1000, 100)
        layoutSurface(first, 0)
        layoutSurface(second, 200)

        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })
        try {
            assertTrue(host.attachSurface("teardown", root, "system_icons"))
            first.strip.visibility = View.INVISIBLE
            second.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("teardown", root, "system_icons"))
            assertEquals(2, created.size)

            // Swap back immediately: throttled, so the host stays on the hidden copy.
            second.strip.visibility = View.INVISIBLE
            first.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("teardown", root, "system_icons"))
            assertEquals(2, created.size)

            host.teardown()
            assertFalse(host.attached)

            // The timestamp was reset, so a fresh attach and the next swap may re-target at once.
            assertTrue(host.attachSurface("teardown", root, "system_icons"))
            assertEquals(3, created.size)
            first.strip.visibility = View.INVISIBLE
            second.strip.visibility = View.VISIBLE
            assertTrue(host.attachSurface("teardown", root, "system_icons"))
            assertEquals("teardown reset the throttle", 4, created.size)
            assertNull(second.battery.icon.drawable)
            assertSame(first.original, first.battery.icon.drawable)
        } finally {
            host.teardown()
        }
    }

    @Test
    fun `repeated attach on the same strip keeps the same element and one layer`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        val host = host(canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } })

        assertFalse("nothing is attached before the first attach", host.attachedAlive(tree.root))
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, created.size)
        val glyph = created[0]
        assertEquals(2, tree.root.childCount)

        repeat(3) { assertTrue(host.attachIn(tree.strip, tree.root)) }

        assertEquals(1, created.size)
        assertSame(glyph, created[0])
        assertEquals(0, glyph.tornDown)
        assertEquals(2, tree.root.childCount)
        assertTrue(tree.root.getChildAt(1) is FrameLayout)

        // A strip removed from its parent is never alive (the pure predicate above pins the tree and
        // window comparison); the host still owns the record and teardown restores the stock drawing.
        tree.root.removeView(tree.strip)
        assertFalse(host.attachedAlive(tree.root))
        host.teardown()
        assertFalse(host.attached)
        assertSame(tree.original, tree.battery.icon.drawable)
    }

    // ---- idle window and Rive survival ----

    @Test
    fun `the renderer pauses after the idle window and wakes on a change`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        var reading = IosBatteryReading(50, false, 0)
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { reading },
            idleDelayMs = 100L
        )
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertFalse(created[0].active.contains(true))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
        assertEquals(false, created[0].active.lastOrNull())

        reading = IosBatteryReading(57, false, 0)
        host.refresh()
        assertEquals(true, created[0].active.lastOrNull())

        host.teardown()
    }

    @Test
    fun `canvas failing leaves the stock icon untouched`() {
        val tree = batteryTree()
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k, startResult = false) }
        )

        assertFalse(host.attachIn(tree.strip, tree.root))
        assertFalse(host.attached)
        assertSame(tree.original, tree.battery.icon.drawable)
        assertEquals(1f, tree.battery.alpha)
        assertEquals(View.VISIBLE, tree.battery.visibility)
        assertEquals(1, tree.root.childCount)
    }

    @Test
    fun `screen off is not un-paused by a battery change`() {
        val tree = batteryTree()
        val created = ArrayList<FakeElement>()
        var reading = IosBatteryReading(50, false, 0)
        val host = host(
            canvasRenderers = { c, k -> FakeElement(c, k).also { created += it } },
            batterySource = { reading }
        )
        assertTrue(host.attachIn(tree.strip, tree.root))

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, created[0].active.lastOrNull())

        reading = IosBatteryReading(57, false, 0)
        host.refresh()
        assertEquals(false, created[0].active.lastOrNull())

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, created[0].active.lastOrNull())

        host.teardown()
    }
}

private val NEXT_NAMED_ID = AtomicInteger(0x7f0f0001)
