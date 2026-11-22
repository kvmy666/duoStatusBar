package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/**
 * R3 A5.1 fixture matrix: synthetic view trees for every measured stock-icon family.
 *
 * Each fixture uses the OEM's own class name and the dump-proven resource entry names, exactly as the
 * host reads them on a device (no Xposed, `classLoader = null`, so OUTER_DRAW/LEAF_DRAW exercise their
 * measured fallbacks). A fixture asserts four things:
 *  1. the planner mechanism for the fixture's outer;
 *  2. the runtime adoption: our drawable/overlay, or a clean stock fallback;
 *  3. one drawing record per replaced icon (no double draw);
 *  4. teardown restores the exact original drawables/visibility/alphas.
 *
 * No real device is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosReplaceFixtureMatrixTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        clearHooks()
    }

    @After
    fun tearDown() {
        clearHooks()
        IosDrawHookInstaller.releaseWhenEmpty()
    }

    private fun clearHooks() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
    }

    // ------------------------------------------------------------------------------- OEM class stubs

    open class NamedFrameLayout(context: Context, entry: String?) : FrameLayout(context) {
        private var fake: Resources? = null

        init {
            id = if (entry == null) View.NO_ID else NEXT_NAMED_ID.getAndIncrement()
            fake = entry?.let { FakeResources(context.resources, mapOf(id to it)) }
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    private open class NamedView(context: Context, entry: String) : View(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    private open class NamedImageView(context: Context, entry: String) : ImageView(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    open class NamedGroup(context: Context, entry: String) : FrameLayout(context) {
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

    private class StatBatteryMeterView(context: Context) : NamedFrameLayout(context, "battery")

    private class OplusModernStatusBarWifiView(context: Context) :
        NamedFrameLayout(context, "wifi_combo")

    class OplusModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) :
        NamedFrameLayout(context, "mobile_combo") {
        fun getSubId(): Int = sub
    }

    private class MiuiBatteryMeterView(context: Context) : NamedFrameLayout(context, "battery")

    private class ModernStatusBarWifiView(context: Context) : NamedFrameLayout(context, "wifi_combo")

    class ModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) :
        NamedFrameLayout(context, "mobile_combo") {
        fun getSubId(): Int = sub
    }

    private class BatteryMeterView(context: Context) : NamedFrameLayout(context, "battery")

    private class TrBatteryMeterView(context: Context) : NamedFrameLayout(context, "battery")

    private class ComposeView(context: Context, entry: String?) : NamedFrameLayout(context, entry)

    class SingleBindableStatusBarComposeIconView(
        context: Context,
        entry: String,
        private val sub: Int
    ) : NamedGroup(context, entry) {
        fun getSubId(): Int = sub
    }

    private class SingleBindableStatusBarIconView(context: Context) :
        NamedFrameLayout(context, "wifi_combo")

    // ------------------------------------------------------------------------------- shared trees

    private class Tree(
        val root: FrameLayout,
        val strip: LinearLayout
    )

    private fun tree(): Tree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        root.addView(strip, FrameLayout.LayoutParams(400, 100))
        return Tree(root, strip)
    }

    private fun Tree.layout(width: Int = 400, height: Int = 100) {
        root.layout(0, 0, 1000, 200)
        strip.layout(0, 0, width, height)
    }

    // --------------------------------------------------------------------------- host + readings

    private fun host(
        surface: String = "fixture",
        cellSource: (Context) -> CellReading = { noCellReading() }
    ): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        isMainBar = true,
        surface = surface,
        batterySource = { IosBatteryReading(50, false, 0) },
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

    private fun oneSimReading(subId: Int, dots: Int = 3): CellReading = CellReading(
        samples = listOf(sample(0, subId, dots)),
        count = 4,
        defaultRaw = dots,
        defaultMaxIndex = 4,
        defaultDots = dots,
        defaultSub = subId,
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

    // ------------------------------------------------------------------------------- fixtures

    @Test
    fun `oplus battery wifi and mobile replace their leaves with one record each`() {
        val tree = tree()
        val battery = StatBatteryMeterView(context)
        val batteryLeaf = NamedImageView(context, "battery_icon_view").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val batteryOriginal = batteryLeaf.drawable
        val bolt = NamedImageView(context, "battery_charge_icon").apply {
            setImageDrawable(ColorDrawable(Color.BLUE))
        }
        val boltOriginal = bolt.drawable
        val percent = TextView(context).apply { text = "42%" }
        battery.addView(batteryLeaf, FrameLayout.LayoutParams(79, 48))
        battery.addView(bolt, FrameLayout.LayoutParams(10, 10))
        battery.addView(percent, FrameLayout.LayoutParams(20, 10))

        val wifi = OplusModernStatusBarWifiView(context)
        val wifiGroup = NamedGroup(context, "wifi_group")
        val wifiLeaf = NamedImageView(context, "wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        val wifiOriginal = wifiLeaf.drawable
        val wifiLeft = NamedImageView(context, "wifi_left").apply {
            setImageDrawable(ColorDrawable(Color.GRAY))
        }
        val wifiLeftOriginal = wifiLeft.drawable
        wifiGroup.addView(wifiLeaf, FrameLayout.LayoutParams(70, 51))
        wifiGroup.addView(wifiLeft, FrameLayout.LayoutParams(10, 10))
        wifi.addView(wifiGroup, FrameLayout.LayoutParams(73, 63))

        val mobile = OplusModernStatusBarMobileViewWithSub(context, 5)
        val mobileGroup = NamedGroup(context, "mobile_group")
        val mobileLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        val mobileOriginal = mobileLeaf.drawable
        val mobileInout = NamedImageView(context, "data_inout").apply {
            setImageDrawable(ColorDrawable(Color.MAGENTA))
        }
        val mobileInoutOriginal = mobileInout.drawable
        mobileGroup.addView(mobileLeaf, FrameLayout.LayoutParams(60, 51))
        mobileGroup.addView(mobileInout, FrameLayout.LayoutParams(8, 8))
        mobile.addView(mobileGroup, FrameLayout.LayoutParams(60, 63))

        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.strip.addView(wifi, LinearLayout.LayoutParams(73, 63))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(60, 63))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        batteryLeaf.layout(0, 0, 79, 48)
        bolt.layout(60, 0, 70, 10)
        percent.layout(40, 0, 60, 10)
        wifi.layout(100, 0, 173, 63)
        wifiGroup.layout(0, 0, 73, 63)
        wifiLeaf.layout(3, 6, 73, 57)
        wifiLeft.layout(60, 0, 70, 10)
        mobile.layout(180, 0, 240, 63)
        mobileGroup.layout(0, 0, 60, 63)
        mobileLeaf.layout(0, 6, 60, 57)
        mobileInout.layout(50, 0, 58, 8)

        assertEquals(
            IosReplaceMechanism.IMAGE_DRAWABLE,
            IosReplacePlanner.planFor(battery).mechanism
        )
        assertEquals(
            IosReplaceMechanism.OUTER_DRAW,
            IosReplacePlanner.planFor(wifi).mechanism
        )
        assertEquals(
            IosReplaceMechanism.OUTER_DRAW,
            IosReplacePlanner.planFor(mobile).mechanism
        )

        val host = host(cellSource = { oneSimReading(5) })
        assertTrue(host.attachIn(tree.strip, tree.root))

        // One record per icon, and no overlay layer: the OUTER_DRAW fallback swaps the leaves.
        assertEquals(3, host.recordCountForTest())
        assertEquals(0, countCanvasViews(tree.root))
        assertTrue(batteryLeaf.drawable is IosBatteryDrawable)
        assertTrue(wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, wifiLeft.drawable.alpha)
        assertEquals(0, mobileInout.drawable.alpha)
        assertEquals("42%", percent.text)

        host.teardown()

        assertSame(batteryOriginal, batteryLeaf.drawable)
        assertSame(boltOriginal, bolt.drawable)
        assertSame(wifiOriginal, wifiLeaf.drawable)
        assertSame(mobileOriginal, mobileLeaf.drawable)
        assertSame(wifiLeftOriginal, wifiLeft.drawable)
        assertSame(mobileInoutOriginal, mobileInout.drawable)
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(View.VISIBLE, wifi.visibility)
        assertEquals(View.VISIBLE, mobile.visibility)
    }

    @Test
    fun `hyperos battery leaf draw falls back to one overlay and wifi mobile swap leaves`() {
        val tree = tree()
        val battery = MiuiBatteryMeterView(context)
        val hollow = NamedImageView(context, "hollow_battery_image").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val hollowOriginal = hollow.drawable
        val flat = NamedImageView(context, "battery_image").apply {
            setImageDrawable(ColorDrawable(Color.BLUE))
            visibility = View.GONE
        }
        val flatOriginal = flat.drawable
        val bolt = NamedImageView(context, "battery_charge_out_image").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        battery.addView(hollow, FrameLayout.LayoutParams(79, 56))
        battery.addView(flat, FrameLayout.LayoutParams(79, 56))
        battery.addView(bolt, FrameLayout.LayoutParams(20, 56))

        val wifi = ModernStatusBarWifiView(context)
        val wifiLeaf = NamedImageView(context, "wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        val wifiOriginal = wifiLeaf.drawable
        val wifiActivity = NamedImageView(context, "wifi_activity").apply {
            setImageDrawable(ColorDrawable(Color.GRAY))
        }
        wifi.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))
        wifi.addView(wifiActivity, FrameLayout.LayoutParams(6, 6))

        val mobile = ModernStatusBarMobileViewWithSub(context, 7)
        val mobileLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.MAGENTA))
        }
        val mobileOriginal = mobileLeaf.drawable
        val mobileType = NamedImageView(context, "mobile_type").apply {
            setImageDrawable(ColorDrawable(Color.BLACK))
        }
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))
        mobile.addView(mobileType, FrameLayout.LayoutParams(6, 6))

        tree.strip.addView(battery, LinearLayout.LayoutParams(100, 60))
        tree.strip.addView(wifi, LinearLayout.LayoutParams(24, 24))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        tree.layout()
        battery.layout(0, 0, 100, 60)
        hollow.layout(0, 0, 79, 56)
        flat.layout(0, 0, 79, 56)
        bolt.layout(80, 0, 100, 56)
        wifi.layout(110, 0, 134, 24)
        wifiLeaf.layout(0, 0, 24, 24)
        wifiActivity.layout(18, 18, 24, 24)
        mobile.layout(140, 0, 160, 20)
        mobileLeaf.layout(0, 0, 20, 20)
        mobileType.layout(14, 14, 20, 20)

        assertEquals(
            IosReplaceMechanism.LEAF_DRAW,
            IosReplacePlanner.planFor(battery).mechanism
        )
        assertEquals(
            IosReplaceMechanism.IMAGE_DRAWABLE,
            IosReplacePlanner.planFor(wifi).mechanism
        )
        assertEquals(
            IosReplaceMechanism.IMAGE_DRAWABLE,
            IosReplacePlanner.planFor(mobile).mechanism
        )

        val host = host(cellSource = { oneSimReading(7) })
        assertTrue(host.attachIn(tree.strip, tree.root))

        // LEAF_DRAW cannot hook with no class loader, so the battery uses the ONE approved overlay;
        // wifi/mobile keep their leaves. Three records, one canvas: no icon is drawn twice.
        assertEquals(3, host.recordCountForTest())
        assertEquals(1, countCanvasViews(tree.root))
        assertEquals(0f, hollow.alpha)
        assertEquals(0f, flat.alpha)
        assertSame(hollowOriginal, hollow.drawable)
        assertSame(flatOriginal, flat.drawable)
        assertTrue(wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, wifiActivity.drawable.alpha)
        assertEquals(0, mobileType.drawable.alpha)

        host.teardown()

        assertSame(hollowOriginal, hollow.drawable)
        assertSame(flatOriginal, flat.drawable)
        assertEquals(1f, hollow.alpha)
        assertEquals(1f, flat.alpha)
        assertEquals(View.GONE, flat.visibility)
        assertSame(wifiOriginal, wifiLeaf.drawable)
        assertSame(mobileOriginal, mobileLeaf.drawable)
        assertEquals(0, countCanvasViews(tree.root))
    }

    @Test
    fun `samsung BatteryMeterView adopts its first ImageView and restores the same drawable`() {
        val tree = tree()
        val battery = BatteryMeterView(context)
        val leaf = ImageView(context).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        val original = leaf.drawable
        battery.addView(leaf, FrameLayout.LayoutParams(79, 37))
        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 37)

        val plan = IosReplacePlanner.planFor(battery)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        assertEquals(listOf("battery_icon"), plan.leafIds)

        val host = host()
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, host.recordCountForTest())
        assertEquals(0, countCanvasViews(tree.root))
        assertTrue(leaf.drawable is IosBatteryDrawable)

        host.teardown()
        assertSame(original, leaf.drawable)
        assertEquals(View.VISIBLE, battery.visibility)
    }

    @Test
    fun `aosp BatteryMeterView replaces its named battery_icon leaf`() {
        val tree = tree()
        val battery = BatteryMeterView(context)
        val leaf = NamedImageView(context, "battery_icon").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val original = leaf.drawable
        battery.addView(leaf, FrameLayout.LayoutParams(79, 39))
        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 39)

        val plan = IosReplacePlanner.planFor(battery)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        assertEquals(listOf("battery_icon"), plan.leafIds)

        val host = host()
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, host.recordCountForTest())
        assertTrue(leaf.drawable is IosBatteryDrawable)

        host.teardown()
        assertSame(original, leaf.drawable)
    }

    @Test
    fun `compose battery and compose icon use one overlay each and restore visibility`() {
        val tree = tree()
        val battery = ComposeView(context, "battery_composable_view")
        val icon = SingleBindableStatusBarComposeIconView(context, "mobile_combo", 1)
        tree.strip.addView(battery, LinearLayout.LayoutParams(55, 27))
        tree.strip.addView(icon, LinearLayout.LayoutParams(52, 49))
        tree.layout()
        battery.layout(0, 0, 55, 27)
        icon.layout(100, 0, 152, 49)

        assertEquals(
            IosReplaceMechanism.OVERLAY,
            IosReplacePlanner.planFor(battery).mechanism
        )
        assertEquals(
            IosReplaceMechanism.OVERLAY,
            IosReplacePlanner.planFor(icon).mechanism
        )

        val host = host(cellSource = { oneSimReading(1) })
        assertTrue(host.attachIn(tree.strip, tree.root))
        // All-overlay strips delegate to the Canvas host: no replacement records, one glyph each.
        assertEquals(0, host.recordCountForTest())
        assertEquals(2, countCanvasViews(tree.root))
        assertEquals(View.INVISIBLE, battery.visibility)
        assertEquals(View.INVISIBLE, icon.visibility)

        host.teardown()
        assertEquals(0, countCanvasViews(tree.root))
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(View.VISIBLE, icon.visibility)
    }

    @Test
    fun `infinix TrBatteryMeterView replaces the dump-proven battery_icon leaf`() {
        val tree = tree()
        val battery = TrBatteryMeterView(context)
        val leaf = NamedImageView(context, "battery_icon").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val original = leaf.drawable
        battery.addView(leaf, FrameLayout.LayoutParams(79, 39))
        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 39)

        val plan = IosReplacePlanner.planFor(battery)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, plan.fallback)
        assertEquals(listOf("battery_icon"), plan.leafIds)
        assertEquals(IosSkinFamily.UNKNOWN, plan.family)

        val host = host()
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, host.recordCountForTest())
        assertEquals(0, countCanvasViews(tree.root))
        assertTrue(leaf.drawable is IosBatteryDrawable)

        host.teardown()
        assertSame(original, leaf.drawable)
    }

    @Test
    fun `TrBatteryMeterView bare ImageView adopts via the AOSP first-ImageView fallback`() {
        val tree = tree()
        val battery = TrBatteryMeterView(context)
        val bare = ImageView(context).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        val original = bare.drawable
        battery.addView(bare, FrameLayout.LayoutParams(79, 39))
        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        bare.layout(0, 0, 79, 39)

        val plan = IosReplacePlanner.planFor(battery)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        // The generic *BatteryMeterView shape keeps its measured family (UNKNOWN for the Infinix
        // subclass); the shape flag enables the first-ImageView fallback (mBatteryIconView has no id).
        assertEquals(IosSkinFamily.UNKNOWN, plan.family)
        assertTrue(plan.allowFirstImageView)

        val host = host()
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, host.recordCountForTest())
        assertTrue(bare.drawable is IosBatteryDrawable)

        host.teardown()
        assertSame(original, bare.drawable)
    }

    @Test
    fun `TrBatteryMeterView with only a 0x0 legacy ImageView stays stock`() {
        val tree = tree()
        val battery = TrBatteryMeterView(context)
        val bare = ImageView(context).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        val original = bare.drawable
        battery.addView(bare, FrameLayout.LayoutParams(0, 0))
        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        bare.layout(0, 0, 0, 0)

        val plan = IosReplacePlanner.planFor(battery)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        // The measured X6871 legacy leaf is 0x0: there is no size to draw at, so the host must leave
        // the stock drawing untouched (V-1 row 27, IOS-INV §2.5).
        assertFalse(host().attachIn(tree.strip, tree.root))
        assertSame(original, bare.drawable)
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(0, countCanvasViews(tree.root))
    }

    @Test
    fun `motorola AOSP shapes replace battery wifi and mobile`() {
        val tree = tree()
        val battery = BatteryMeterView(context)
        val batteryLeaf = ImageView(context).apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val batteryOriginal = batteryLeaf.drawable
        battery.addView(batteryLeaf, FrameLayout.LayoutParams(79, 37))

        val wifi = ModernStatusBarWifiView(context)
        val wifiLeaf = NamedImageView(context, "wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        val wifiOriginal = wifiLeaf.drawable
        wifi.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))

        val mobile = ModernStatusBarMobileViewWithSub(context, 3)
        val mobileLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        val mobileOriginal = mobileLeaf.drawable
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))

        tree.strip.addView(battery, LinearLayout.LayoutParams(90, 48))
        tree.strip.addView(wifi, LinearLayout.LayoutParams(24, 24))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        tree.layout()
        battery.layout(0, 0, 90, 48)
        batteryLeaf.layout(0, 0, 79, 37)
        wifi.layout(100, 0, 124, 24)
        wifiLeaf.layout(0, 0, 24, 24)
        mobile.layout(130, 0, 150, 20)
        mobileLeaf.layout(0, 0, 20, 20)

        val host = host(cellSource = { oneSimReading(3) })
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(3, host.recordCountForTest())
        assertEquals(0, countCanvasViews(tree.root))
        assertTrue(batteryLeaf.drawable is IosBatteryDrawable)
        assertTrue(wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(mobileLeaf.drawable is IosCellDrawable)

        host.teardown()
        assertSame(batteryOriginal, batteryLeaf.drawable)
        assertSame(wifiOriginal, wifiLeaf.drawable)
        assertSame(mobileOriginal, mobileLeaf.drawable)
    }

    @Test
    fun `nothing A059 compose battery overlays and its unnamed wifi mobile stay stock`() {
        val tree = tree()
        val battery = ComposeView(context, "battery_composable_view")
        val wifi = ModernStatusBarWifiView(context)
        val wifiGroup = NamedGroup(context, "wifi_group")
        wifi.addView(wifiGroup, FrameLayout.LayoutParams(50, 49))
        val mobile = ModernStatusBarMobileViewWithSub(context, 1)
        val mobileGroup = NamedGroup(context, "mobile_group")
        mobile.addView(mobileGroup, FrameLayout.LayoutParams(14, 49))

        tree.strip.addView(battery, LinearLayout.LayoutParams(65, 32))
        tree.strip.addView(wifi, LinearLayout.LayoutParams(64, 49))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(52, 49))
        tree.layout()
        battery.layout(0, 0, 65, 32)
        wifi.layout(80, 0, 144, 49)
        wifiGroup.layout(0, 0, 50, 49)
        mobile.layout(160, 0, 212, 49)
        mobileGroup.layout(0, 0, 14, 49)

        assertEquals(
            IosReplaceMechanism.OVERLAY,
            IosReplacePlanner.planFor(battery).mechanism
        )
        // The planner still names the proven leaf; the host presence-check must then fall back.
        assertEquals(
            listOf("wifi_signal"),
            IosReplacePlanner.planFor(wifi).leafIds
        )
        assertEquals(
            listOf("mobile_signal"),
            IosReplacePlanner.planFor(mobile).leafIds
        )

        val host = host(cellSource = { oneSimReading(1) })
        assertTrue(host.attachIn(tree.strip, tree.root))
        // Only the Compose battery can be replaced; the two leaves the A059 build does not have are
        // left completely stock rather than blanked (IOS-REPLACE-map §4 "OVERLAY/stock").
        assertEquals(1, host.recordCountForTest())
        assertEquals(1, countCanvasViews(tree.root))
        assertEquals(View.INVISIBLE, battery.visibility)
        assertEquals(View.VISIBLE, wifi.visibility)
        assertEquals(View.VISIBLE, mobile.visibility)

        host.teardown()
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(0, countCanvasViews(tree.root))
    }

    @Test
    fun `samsung SingleBindableStatusBarIconView replaces its icon_view leaf`() {
        val tree = tree()
        val icon = SingleBindableStatusBarIconView(context)
        val leaf = NamedImageView(context, "icon_view").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val original = leaf.drawable
        icon.addView(leaf, FrameLayout.LayoutParams(24, 24))
        tree.strip.addView(icon, LinearLayout.LayoutParams(24, 24))
        tree.layout()
        icon.layout(0, 0, 24, 24)
        leaf.layout(0, 0, 24, 24)

        val plan = IosReplacePlanner.planFor(icon)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        assertEquals(listOf("icon_view"), plan.leafIds)
        assertEquals(
            IosReplaceMechanism.SKIP,
            IosReplacePlanner.planFor(
                IosSkinFamily.SAMSUNG_SBICON,
                "SingleBindableStatusBarIconView",
                null,
                StockIconKind.OTHER
            ).mechanism
        )

        val host = host()
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, host.recordCountForTest())
        assertTrue(leaf.drawable is IosWifiDrawable)

        host.teardown()
        assertSame(original, leaf.drawable)
    }

    // ------------------------------------------------------------------------------- helpers

    private fun countCanvasViews(view: View): Int {
        var count = if (view is IosCanvasView) 1 else 0
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                count += countCanvasViews(view.getChildAt(i))
            }
        }
        return count
    }

    private companion object {
        val NEXT_NAMED_ID = AtomicInteger(0x7f0f0000.toInt())
    }
}
