package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
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
import org.junit.Assert.assertNotNull
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
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
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
        IosReadings.resetForTest()
    }

    @After
    fun tearDown() {
        clearHooks()
        IosDrawHookInstaller.releaseWhenEmpty()
        IosReadings.resetForTest()
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

    open class NamedGroup(context: Context, entry: String?) : FrameLayout(context) {
        private var fake: Resources? = null

        init {
            if (entry == null) {
                id = View.NO_ID
            } else {
                id = NEXT_NAMED_ID.getAndIncrement()
                fake = FakeResources(context.resources, mapOf(id to entry))
            }
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
        entry: String?,
        private val sub: Int
    ) : NamedGroup(context, entry) {
        fun getSubId(): Int = sub
    }

    private class SingleBindableStatusBarIconView(context: Context) :
        NamedFrameLayout(context, "wifi_combo")

    /** HyperOS older (vayu) / AOSP-shaped classes: the UNKNOWN-family leaf-swap path. */
    private class StatusBarWifiView(context: Context) : NamedFrameLayout(context, "wifi_combo")

    class StatusBarMobileView(context: Context, private val sub: Int) :
        NamedFrameLayout(context, "mobile_combo") {
        fun getSubId(): Int = sub
    }

    /** vivo OriginOS: the outer is an AOSP `BatteryMeterView` descendant with a custom-drawn leaf. */
    private class VivoBatteryMeterView(context: Context) : NamedFrameLayout(context, "battery")

    /** vivo's real battery leaf has no id; the AOSP battery shape would grab it as the first ImageView. */
    private class VivoBatteryIconBaseView(context: Context) : ImageView(context)

    /** vivo's named mobile setter leaf: an ImageView with the measured `mobile_signal` id. */
    private class VivoMobileSignal(context: Context) : NamedImageView(context, "mobile_signal")

    /** Samsung One UI: `wifi_signal` sits inside a `wifi_iconset` FrameLayout that must stay visible. */
    private class WifiIconSet(context: Context) : NamedGroup(context, "wifi_iconset")

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
        isMainBar: Boolean = true,
        cellSource: (Context) -> CellReading = { noCellReading() },
        wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
            { _, _, _, _, _ -> wifiSample() }
    ): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        isMainBar = isMainBar,
        surface = surface,
        batterySource = { IosBatteryReading(50, false, 0) },
        cellSource = cellSource,
        wifiSource = wifiSource
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

    private fun offWifiSample(): WifiSample =
        wifiSample().copy(on = false, active = false, validated = false)

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

    private fun twoSimReading(firstSub: Int = 1, secondSub: Int = 2): CellReading = CellReading(
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

    /** One keyguard/shade-style `system_icons` strip with a battery, Wi-Fi and one SIM. */
    private class SurfaceIcons(
        val root: FrameLayout,
        val strip: NamedGroup,
        val batteryLeaf: NamedImageView,
        val wifiLeaf: NamedImageView,
        val mobileLeaf: NamedImageView,
        val batteryOriginal: Drawable,
        val wifiOriginal: Drawable,
        val mobileOriginal: Drawable
    )

    private fun surfaceIcons(): SurfaceIcons {
        val root = windowRoot()
        val strip = NamedGroup(root.context, "system_icons")

        val battery = BatteryMeterView(root.context)
        val batteryLeaf = NamedImageView(root.context, "battery_icon")
        val batteryOriginal = ColorDrawable(Color.RED)
        batteryLeaf.setImageDrawable(batteryOriginal)
        battery.addView(batteryLeaf, FrameLayout.LayoutParams(79, 39))

        val wifi = ModernStatusBarWifiView(root.context)
        val wifiLeaf = NamedImageView(root.context, "wifi_signal")
        val wifiOriginal = ColorDrawable(Color.GREEN)
        wifiLeaf.setImageDrawable(wifiOriginal)
        wifi.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))

        val mobile = ModernStatusBarMobileViewWithSub(root.context, 4)
        val mobileLeaf = NamedImageView(root.context, "mobile_signal")
        val mobileOriginal = ColorDrawable(Color.CYAN)
        mobileLeaf.setImageDrawable(mobileOriginal)
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))

        strip.addView(battery, LinearLayout.LayoutParams(80, 48))
        strip.addView(wifi, LinearLayout.LayoutParams(24, 24))
        strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        root.addView(strip, FrameLayout.LayoutParams(300, 50))
        strip.layout(0, 0, 300, 50)
        battery.layout(0, 0, 80, 48)
        batteryLeaf.layout(0, 0, 79, 39)
        wifi.layout(100, 0, 124, 24)
        wifiLeaf.layout(0, 0, 24, 24)
        mobile.layout(140, 0, 160, 20)
        mobileLeaf.layout(0, 0, 20, 20)
        return SurfaceIcons(
            root, strip, batteryLeaf, wifiLeaf, mobileLeaf,
            batteryOriginal, wifiOriginal, mobileOriginal
        )
    }

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
        assertEquals("R6: mobile_type is the native label anchor, not a blanked sibling", 255, mobileType.drawable.alpha)

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

    // --------------------------------------------------------------------- Revision-2 coverage

    @Test
    fun `hyperos older StatusBarWifiView and StatusBarMobileView replace leaves and leave the battery stock`() {
        val tree = tree()
        val battery = MiuiBatteryMeterView(context)
        val batteryLeaf = NamedImageView(context, "battery_image").apply {
            setImageDrawable(ColorDrawable(Color.YELLOW))
            visibility = View.GONE
        }
        val batteryOriginal = batteryLeaf.drawable
        battery.addView(batteryLeaf, FrameLayout.LayoutParams(0, 0))

        val wifi = StatusBarWifiView(context)
        val wifiLeaf = NamedImageView(context, "wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        val wifiOriginal = wifiLeaf.drawable
        val wifiActivity = NamedImageView(context, "wifi_activity").apply {
            setImageDrawable(ColorDrawable(Color.GRAY))
        }
        val wifiActivityOriginal = wifiActivity.drawable
        wifi.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))
        wifi.addView(wifiActivity, FrameLayout.LayoutParams(6, 6))

        val mobile = StatusBarMobileView(context, 7)
        val mobileLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        val mobileOriginal = mobileLeaf.drawable
        val mobileType = NamedImageView(context, "mobile_type").apply {
            setImageDrawable(ColorDrawable(Color.MAGENTA))
        }
        val mobileTypeOriginal = mobileType.drawable
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))
        mobile.addView(mobileType, FrameLayout.LayoutParams(6, 6))

        tree.strip.addView(battery, LinearLayout.LayoutParams(100, 60))
        tree.strip.addView(wifi, LinearLayout.LayoutParams(24, 24))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        tree.layout()
        battery.layout(0, 0, 100, 60)
        batteryLeaf.layout(0, 0, 0, 0)
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
        // The battery has no sized Miui leaf, so it stays stock (named reason "no-leaf"); one record
        // each for Wi-Fi and cellular, and no overlay draws an icon twice.
        assertEquals(2, host.recordCountForTest())
        assertEquals(
            setOf(IosIconKind.WIFI, IosIconKind.CELL),
            host.recordKindsForTest().toSet()
        )
        assertSame(batteryOriginal, batteryLeaf.drawable)
        assertEquals(View.GONE, batteryLeaf.visibility)
        assertTrue(wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, wifiActivity.drawable.alpha)
        assertEquals("R6: mobile_type is the native label anchor, not a blanked sibling", 255, mobileType.drawable.alpha)
        assertEquals(0, countCanvasViews(tree.root))

        host.teardown()
        assertSame(batteryOriginal, batteryLeaf.drawable)
        assertSame(wifiOriginal, wifiLeaf.drawable)
        assertSame(wifiActivityOriginal, wifiActivity.drawable)
        assertSame(mobileOriginal, mobileLeaf.drawable)
        assertSame(mobileTypeOriginal, mobileType.drawable)

        assertNoThrash(
            host,
            attach = { host.attachIn(tree.strip, tree.root) },
            teardown = { host.teardown() },
            stock = {
                assertSame(batteryOriginal, batteryLeaf.drawable)
                assertSame(wifiOriginal, wifiLeaf.drawable)
                assertSame(wifiActivityOriginal, wifiActivity.drawable)
                assertSame(mobileOriginal, mobileLeaf.drawable)
                assertSame(mobileTypeOriginal, mobileType.drawable)
                assertEquals(0, countCanvasViews(tree.root))
            }
        )
    }

    @Test
    fun `vivo generic wifi and VivoMobileSignal replace their leaves and restore exactly`() {
        val tree = tree()
        val wifi = ModernStatusBarWifiView(context)
        val wifiLeaf = NamedImageView(context, "wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        val wifiOriginal = wifiLeaf.drawable
        val wifiActivity = NamedImageView(context, "wifi_activity").apply {
            setImageDrawable(ColorDrawable(Color.GRAY))
        }
        val wifiActivityOriginal = wifiActivity.drawable
        wifi.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))
        wifi.addView(wifiActivity, FrameLayout.LayoutParams(6, 6))

        val mobile = ModernStatusBarMobileViewWithSub(context, 9)
        val mobileLeaf = VivoMobileSignal(context).apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        val mobileOriginal = mobileLeaf.drawable
        val mobileType = NamedImageView(context, "mobile_type").apply {
            setImageDrawable(ColorDrawable(Color.MAGENTA))
        }
        val mobileTypeOriginal = mobileType.drawable
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))
        mobile.addView(mobileType, FrameLayout.LayoutParams(6, 6))

        tree.strip.addView(wifi, LinearLayout.LayoutParams(24, 24))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        tree.layout()
        wifi.layout(0, 0, 24, 24)
        wifiLeaf.layout(0, 0, 24, 24)
        wifiActivity.layout(18, 18, 24, 24)
        mobile.layout(30, 0, 50, 20)
        mobileLeaf.layout(0, 0, 20, 20)
        mobileType.layout(14, 14, 20, 20)

        assertEquals(
            IosReplaceMechanism.IMAGE_DRAWABLE,
            IosReplacePlanner.planFor(wifi).mechanism
        )
        assertEquals(
            IosReplaceMechanism.IMAGE_DRAWABLE,
            IosReplacePlanner.planFor(mobile).mechanism
        )
        assertEquals(listOf("mobile_signal"), IosReplacePlanner.planFor(mobile).leafIds)

        val host = host(cellSource = { oneSimReading(9) })
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(2, host.recordCountForTest())
        assertTrue(wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, wifiActivity.drawable.alpha)
        assertEquals("R6: mobile_type is the native label anchor, not a blanked sibling", 255, mobileType.drawable.alpha)
        assertEquals(0, countCanvasViews(tree.root))

        host.teardown()
        assertSame(wifiOriginal, wifiLeaf.drawable)
        assertSame(wifiActivityOriginal, wifiActivity.drawable)
        assertSame(mobileOriginal, mobileLeaf.drawable)
        assertSame(mobileTypeOriginal, mobileType.drawable)

        assertNoThrash(
            host,
            attach = { host.attachIn(tree.strip, tree.root) },
            teardown = { host.teardown() },
            stock = {
                assertSame(wifiOriginal, wifiLeaf.drawable)
                assertSame(wifiActivityOriginal, wifiActivity.drawable)
                assertSame(mobileOriginal, mobileLeaf.drawable)
                assertSame(mobileTypeOriginal, mobileType.drawable)
                assertEquals(0, countCanvasViews(tree.root))
            }
        )
    }

    /**
     * R3 A5.1: `VivoBatteryMeterView` is an AOSP `BatteryMeterView` descendant by name only; its
     * drawing leaf is the custom `VivoBatteryIconBaseView.onDraw`. The planner's `vivo-custom-battery`
     * guard therefore returns SKIP before the generic AOSP battery shape (map §4: vivo battery is
     * SKIP/stock), so the host has nothing to adopt and leaves the exact stock drawing untouched.
     */
    @Test
    fun `vivo custom battery is not adopted by the generic AOSP battery shape`() {
        val tree = tree()
        val battery = VivoBatteryMeterView(context)
        val customLeaf = VivoBatteryIconBaseView(context).apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val original = customLeaf.drawable
        battery.addView(customLeaf, FrameLayout.LayoutParams(84, 56))
        tree.strip.addView(battery, LinearLayout.LayoutParams(100, 60))
        tree.layout()
        battery.layout(0, 0, 100, 60)
        customLeaf.layout(0, 0, 84, 56)

        // The measured leaf row is SKIP, and the vendor outer now resolves to the same SKIP.
        assertEquals(
            IosReplaceMechanism.SKIP,
            IosReplacePlanner.planFor(
                IosSkinFamily.UNKNOWN,
                "VivoBatteryIconBaseView",
                "battery",
                StockIconKind.BATTERY
            ).mechanism
        )
        assertEquals(
            IosReplaceMechanism.SKIP,
            IosReplacePlanner.planFor(battery).mechanism
        )

        val host = host()
        // A SKIP-only strip never adopts anything, restores exactly and never thrashes over 20 cycles.
        assertNoThrash(
            host,
            expectedAttach = false,
            attach = { host.attachIn(tree.strip, null) },
            teardown = { host.teardown() },
            stock = {
                assertSame(original, customLeaf.drawable)
                assertEquals(View.VISIBLE, customLeaf.visibility)
            }
        )

        assertFalse(
            "a SKIP-only vivo battery strip has nothing to adopt",
            host.attachIn(tree.strip, null)
        )
        val records = host.recordCountForTest()
        assertEquals("the stock battery must never be blanked", View.VISIBLE, customLeaf.visibility)
        host.teardown()
        assertSame("the SKIP path leaves the exact stock drawable", original, customLeaf.drawable)
        assertEquals("vivo battery must stay stock (map §4)", 0, records)
        IosDiagnostics.clearAttempt("fixture")
    }

    @Test
    fun `samsung wifi_iconset named wifi and mobile replace without hiding the ancestor`() {
        val tree = tree()
        val wifi = ModernStatusBarWifiView(context)
        val iconSet = WifiIconSet(context)
        val wifiLeaf = NamedImageView(context, "wifi_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        val wifiOriginal = wifiLeaf.drawable
        val vowifi = NamedImageView(context, "vowifi").apply {
            setImageDrawable(ColorDrawable(Color.GRAY))
        }
        val vowifiOriginal = vowifi.drawable
        iconSet.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))
        iconSet.addView(vowifi, FrameLayout.LayoutParams(8, 8))
        wifi.addView(iconSet, FrameLayout.LayoutParams(32, 24))

        val mobile = ModernStatusBarMobileViewWithSub(context, 3)
        val mobileLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        val mobileOriginal = mobileLeaf.drawable
        val mobileType = NamedImageView(context, "mobile_type").apply {
            setImageDrawable(ColorDrawable(Color.MAGENTA))
        }
        val mobileTypeOriginal = mobileType.drawable
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))
        mobile.addView(mobileType, FrameLayout.LayoutParams(6, 6))

        tree.strip.addView(wifi, LinearLayout.LayoutParams(32, 24))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        tree.layout()
        wifi.layout(0, 0, 32, 24)
        iconSet.layout(0, 0, 32, 24)
        wifiLeaf.layout(0, 0, 24, 24)
        vowifi.layout(24, 16, 32, 24)
        mobile.layout(40, 0, 60, 20)
        mobileLeaf.layout(0, 0, 20, 20)
        mobileType.layout(14, 14, 20, 20)

        val wifiPlan = IosReplacePlanner.planFor(wifi)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, wifiPlan.mechanism)
        assertEquals(listOf("wifi_signal"), wifiPlan.leafIds)
        assertTrue("vowifi is suppressed only when it is a sibling", "vowifi" in wifiPlan.siblingIds)

        val host = host(cellSource = { oneSimReading(3) })
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(2, host.recordCountForTest())
        assertTrue(wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(mobileLeaf.drawable is IosCellDrawable)
        // `wifi_iconset` is the leaf's ancestor: it must never be hidden or blanked.
        assertEquals(View.VISIBLE, iconSet.visibility)
        assertEquals(0, vowifi.drawable.alpha)
        assertEquals("R6: mobile_type is the native label anchor, not a blanked sibling", 255, mobileType.drawable.alpha)
        assertEquals(0, countCanvasViews(tree.root))

        host.teardown()
        assertSame(wifiOriginal, wifiLeaf.drawable)
        assertSame(vowifiOriginal, vowifi.drawable)
        assertSame(mobileOriginal, mobileLeaf.drawable)
        assertSame(mobileTypeOriginal, mobileType.drawable)
        assertEquals(View.VISIBLE, iconSet.visibility)

        assertNoThrash(
            host,
            attach = { host.attachIn(tree.strip, tree.root) },
            teardown = { host.teardown() },
            stock = {
                assertSame(wifiOriginal, wifiLeaf.drawable)
                assertSame(vowifiOriginal, vowifi.drawable)
                assertSame(mobileOriginal, mobileLeaf.drawable)
                assertSame(mobileTypeOriginal, mobileType.drawable)
                assertEquals(View.VISIBLE, iconSet.visibility)
                assertEquals(0, countCanvasViews(tree.root))
            }
        )
    }

    @Test
    fun `id-less compose battery and icon stay stock or use the approved overlay`() {
        val tree = tree()
        val battery = ComposeView(context, null)
        val icon = SingleBindableStatusBarComposeIconView(context, null, 1)
        tree.strip.addView(battery, LinearLayout.LayoutParams(55, 27))
        tree.strip.addView(icon, LinearLayout.LayoutParams(52, 49))
        tree.layout()
        battery.layout(0, 0, 55, 27)
        icon.layout(100, 0, 152, 49)

        val host = host(cellSource = { oneSimReading(1) })
        val attached = host.attachIn(tree.strip, tree.root)
        if (attached) {
            // If a future kind path reaches an id-less Compose host, only the approved overlay may own
            // it: INVISIBLE keeps the slot, one Canvas element draws, and restore puts the views back.
            assertEquals(View.INVISIBLE, battery.visibility)
            assertEquals(View.INVISIBLE, icon.visibility)
            assertTrue("one overlay per Compose host", countCanvasViews(tree.root) >= 1)
            host.teardown()
        }
        assertEquals(View.VISIBLE, battery.visibility)
        assertEquals(View.VISIBLE, icon.visibility)
        assertEquals(1f, battery.alpha, 0f)
        assertEquals(1f, icon.alpha, 0f)
        assertEquals(0, countCanvasViews(tree.root))

        assertNoThrash(
            host,
            expectedAttach = attached,
            attach = { host.attachIn(tree.strip, tree.root) },
            teardown = { host.teardown() },
            stock = {
                assertEquals(View.VISIBLE, battery.visibility)
                assertEquals(View.VISIBLE, icon.visibility)
                assertEquals(0, countCanvasViews(tree.root))
            }
        )
    }

    @Test
    fun `QS strip with twelve carrier rows and two mapped SIMs adopts one BOTH block`() {
        val tree = tree()
        val first = ModernStatusBarMobileViewWithSub(context, 1)
        val second = ModernStatusBarMobileViewWithSub(context, 2)
        val firstLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.GREEN))
        }
        val secondLeaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.RED))
        }
        val firstOriginal = firstLeaf.drawable
        val secondOriginal = secondLeaf.drawable
        first.addView(firstLeaf, FrameLayout.LayoutParams(20, 20))
        second.addView(secondLeaf, FrameLayout.LayoutParams(20, 20))
        tree.strip.addView(first, LinearLayout.LayoutParams(20, 20))
        tree.strip.addView(second, LinearLayout.LayoutParams(20, 20))

        val carriers = ArrayList<NamedView>()
        for (i in 0 until 12) {
            val name = when (i % 3) {
                0 -> "mobile_in"
                1 -> "mobile_out"
                else -> "mobile_roaming"
            }
            val carrier = NamedView(context, name)
            carriers.add(carrier)
            tree.strip.addView(carrier, LinearLayout.LayoutParams(10, 20))
        }
        tree.layout()
        first.layout(0, 0, 20, 20)
        second.layout(30, 0, 50, 20)
        firstLeaf.layout(0, 0, 20, 20)
        secondLeaf.layout(0, 0, 20, 20)
        carriers.forEachIndexed { index, carrier ->
            carrier.layout(60 + index * 10, 0, 70 + index * 10, 20)
        }
        val otherParams = second.layoutParams
        val otherWidth = otherParams.width
        val otherHeight = otherParams.height

        val host = host(cellSource = { twoSimReading(1, 2) })
        assertTrue(host.attachIn(tree.strip, tree.root))
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(first))
        assertNull(host.cellRoleForTest(second))
        assertEquals(View.GONE, second.visibility)
        assertEquals(0, second.layoutParams.width)
        assertTrue("carrier-group rows stay stock", carriers.all { it.visibility == View.VISIBLE })
        assertEquals(0, countCanvasViews(tree.root))

        host.teardown()
        assertSame(firstOriginal, firstLeaf.drawable)
        assertSame(secondOriginal, secondLeaf.drawable)
        assertEquals(View.VISIBLE, second.visibility)
        assertEquals(otherWidth, second.layoutParams.width)
        assertEquals(otherHeight, second.layoutParams.height)
        assertTrue("carrier-group rows are untouched", carriers.all { it.visibility == View.VISIBLE })

        assertNoThrash(
            host,
            attach = { host.attachIn(tree.strip, tree.root) },
            teardown = { host.teardown() },
            stock = {
                assertSame(firstOriginal, firstLeaf.drawable)
                assertSame(secondOriginal, secondLeaf.drawable)
                assertEquals(View.VISIBLE, second.visibility)
                assertEquals(otherWidth, second.layoutParams.width)
                assertTrue(carriers.all { it.visibility == View.VISIBLE })
                assertEquals(0, countCanvasViews(tree.root))
            }
        )
    }

    @Test
    fun `keyguard system_icons strip replaces and restores every icon without a double draw`() {
        val surface = surfaceIcons()
        val host = host(
            surface = "keyguard",
            isMainBar = false,
            cellSource = { oneSimReading(4) }
        )
        assertTrue(host.attachSurface("keyguard", surface.root, "system_icons"))
        assertEquals(3, host.recordCountForTest())
        assertEquals(
            setOf(IosIconKind.BATTERY, IosIconKind.WIFI, IosIconKind.CELL),
            host.recordKindsForTest().toSet()
        )
        assertTrue(surface.batteryLeaf.drawable is IosBatteryDrawable)
        assertTrue(surface.wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(surface.mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, countCanvasViews(surface.root))

        host.teardown()
        assertSame(surface.batteryOriginal, surface.batteryLeaf.drawable)
        assertSame(surface.wifiOriginal, surface.wifiLeaf.drawable)
        assertSame(surface.mobileOriginal, surface.mobileLeaf.drawable)
        assertEquals(0, countCanvasViews(surface.root))

        assertNoThrash(
            host,
            attach = { host.attachSurface("keyguard", surface.root, "system_icons") },
            teardown = { host.teardown() },
            stock = {
                assertSame(surface.batteryOriginal, surface.batteryLeaf.drawable)
                assertSame(surface.wifiOriginal, surface.wifiLeaf.drawable)
                assertSame(surface.mobileOriginal, surface.mobileLeaf.drawable)
                assertEquals(0, countCanvasViews(surface.root))
            }
        )
    }

    @Test
    fun `shade system_icons strip replaces and restores every icon without a double draw`() {
        val surface = surfaceIcons()
        val host = host(
            surface = "shade header",
            isMainBar = false,
            cellSource = { oneSimReading(4) }
        )
        assertTrue(host.attachSurface("shade header", surface.root, "system_icons"))
        assertEquals(3, host.recordCountForTest())
        assertTrue(surface.batteryLeaf.drawable is IosBatteryDrawable)
        assertTrue(surface.wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(surface.mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, countCanvasViews(surface.root))

        host.teardown()
        assertSame(surface.batteryOriginal, surface.batteryLeaf.drawable)
        assertSame(surface.wifiOriginal, surface.wifiLeaf.drawable)
        assertSame(surface.mobileOriginal, surface.mobileLeaf.drawable)
        assertEquals(0, countCanvasViews(surface.root))

        assertNoThrash(
            host,
            attach = { host.attachSurface("shade header", surface.root, "system_icons") },
            teardown = { host.teardown() },
            stock = {
                assertSame(surface.batteryOriginal, surface.batteryLeaf.drawable)
                assertSame(surface.wifiOriginal, surface.wifiLeaf.drawable)
                assertSame(surface.mobileOriginal, surface.mobileLeaf.drawable)
                assertEquals(0, countCanvasViews(surface.root))
            }
        )
    }

    @Test
    fun `a hidden shade strip on the same window keeps every record`() {
        val surface = surfaceIcons()
        val host = host(
            surface = "shade header",
            isMainBar = false,
            cellSource = { oneSimReading(4) }
        )
        assertTrue(host.attachSurface("shade header", surface.root, "system_icons"))
        val count = host.recordCountForTest()
        val batteryInstalled = surface.batteryLeaf.drawable
        assertTrue(count > 0)
        assertTrue(batteryInstalled is IosBatteryDrawable)

        // A panel close removes the header from the tree while the shade window object survives. R6
        // keeps the live host for the same window instead of restoring stock and re-adopting.
        (surface.strip.parent as? ViewGroup)?.removeView(surface.strip)
        assertTrue("the same window keeps the host", host.attachSurface("shade header", surface.root, "system_icons"))
        assertEquals("no record was rebuilt", count, host.recordCountForTest())
        assertSame("the drawing is untouched", batteryInstalled, surface.batteryLeaf.drawable)
        assertTrue(host.attached)
        assertEquals(0, countCanvasViews(surface.root))

        host.teardown()
        assertSame(surface.batteryOriginal, surface.batteryLeaf.drawable)
    }

    @Test
    fun `the measured shade close and open keep the same records`() {
        val surface = surfaceIcons()
        val host = host(
            surface = "shade header",
            isMainBar = false,
            cellSource = { oneSimReading(4) }
        )
        assertTrue(host.attachSurface("shade header", surface.root, "system_icons"))
        val count = host.recordCountForTest()
        val batteryInstalled = surface.batteryLeaf.drawable
        val wifiInstalled = surface.wifiLeaf.drawable
        assertTrue(count > 0)

        // The measured QS close/open: the header is only hidden, not re-inflated. The host must keep
        // every record and visual across both attaches (attaches=1, teardowns=0 in the power line).
        surface.strip.visibility = View.INVISIBLE
        assertTrue("hide keeps the host", host.attachSurface("shade header", surface.root, "system_icons"))
        assertEquals("no record was rebuilt on close", count, host.recordCountForTest())
        assertSame(batteryInstalled, surface.batteryLeaf.drawable)
        assertSame(wifiInstalled, surface.wifiLeaf.drawable)

        surface.strip.visibility = View.VISIBLE
        assertTrue("show keeps the host", host.attachSurface("shade header", surface.root, "system_icons"))
        assertEquals("no record was rebuilt on open", count, host.recordCountForTest())
        assertSame(batteryInstalled, surface.batteryLeaf.drawable)
        assertSame(wifiInstalled, surface.wifiLeaf.drawable)
        assertTrue(host.attached)
        assertEquals(0, countCanvasViews(surface.root))

        // A ROM that inflates a NEW header view necessarily re-adopts once (a removed view cannot be
        // owned), and that attach happens inside the same pre-draw, not a posted runnable.
        host.teardown()
        assertSame(surface.batteryOriginal, surface.batteryLeaf.drawable)
    }

    // ------------------------------------------------------- R5A item 1: mobile_type label anchor

    private fun dataLabel() {
        IosReadings.resetForTest(
            data = { DataLabelSample("5G", mobileActive = true, source = DataLabelReader.SRC_ACTIVE) }
        )
    }

    private fun assertMobileTypeLabel(
        host: IosReplaceHost,
        mobile: View,
        type: NamedImageView,
        typeWidth: Int,
        typeHeight: Int
    ) {
        assertEquals("WIFI records: ${host.diagnosticLines()}",
            1, host.recordKindsForTest().count { it == IosIconKind.WIFI })
        assertTrue("the WIFI record is a label", host.labelSlotForTest())
        assertEquals(IosReplaceMechanism.NATIVE_LABEL, host.mechanismForTest(IosIconKind.WIFI))
        assertNull("R6 never paints the label as an overlay", host.overlayForTest(IosIconKind.WIFI))
        assertSame("anchored to the data SIM's mobile_type", type, host.recordOuterForTest(IosIconKind.WIFI))
        assertEquals(IosWifiSlot.DataType("5G"), host.visualForTest(type)?.wifiSlot)
        // Non-OPLUS families replace the `mobile_type` drawable; the session path (OPLUS OUTER_DRAW)
        // would leave it alone. Both are native, never an overlay canvas.
        assertTrue("the native label owns the type drawable", type.drawable is IosGlyphDrawable)
        // C2: the stock type keeps its laid-out size/visibility.
        assertEquals(typeWidth, type.width)
        assertEquals(typeHeight, type.height)
        assertEquals(View.VISIBLE, type.visibility)
        assertEquals(View.VISIBLE, mobile.visibility)
    }

    private class LabelTree(
        val tree: Tree,
        val mobile: ViewGroup,
        val type: NamedImageView,
        val typeDrawable: Drawable
    )

    /** A measured mobile outer with a real `mobile_type` child; no Wi-Fi view in the strip. */
    private fun labelTree(
        mobile: ViewGroup,
        typeWidth: Int,
        typeHeight: Int,
        typeParent: ViewGroup = mobile
    ): LabelTree {
        val tree = tree()
        val type = NamedImageView(context, "mobile_type")
        val typeDrawable = ColorDrawable(Color.MAGENTA)
        type.setImageDrawable(typeDrawable)
        typeParent.addView(type, FrameLayout.LayoutParams(typeWidth, typeHeight))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(80, 60))
        tree.layout()
        mobile.layout(0, 0, 80, 60)
        type.layout(10, 10, 10 + typeWidth, 10 + typeHeight)
        return LabelTree(tree, mobile, type, typeDrawable)
    }

    @Test
    fun `OPLUS data label anchors to mobile_type inside mobile_group`() {
        dataLabel()
        val mobile = OplusModernStatusBarMobileViewWithSub(context, 5)
        val group = NamedGroup(context, "mobile_group")
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        group.addView(leaf, FrameLayout.LayoutParams(60, 51))
        mobile.addView(group, FrameLayout.LayoutParams(60, 63))
        val label = labelTree(mobile, 24, 12, typeParent = group)
        // Parent-first layout, then the type last: group.layout re-lays children from their measured
        // size (0 here), so the type must be given its real frame after the group.
        group.layout(0, 0, 60, 63)
        leaf.layout(0, 0, 60, 51)
        label.type.layout(10, 10, 34, 22)

        val host = host(cellSource = { oneSimReading(5) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))
        assertMobileTypeLabel(host, mobile, label.type, 24, 12)
        host.teardown()
        assertSame(label.typeDrawable, label.type.drawable)
    }

    @Test
    fun `HyperOS newer data label anchors to mobile_type`() {
        dataLabel()
        val mobile = ModernStatusBarMobileViewWithSub(context, 7)
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val label = labelTree(mobile, 20, 10)
        leaf.layout(0, 0, 20, 20)

        val host = host(cellSource = { oneSimReading(7) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))
        assertMobileTypeLabel(host, mobile, label.type, 20, 10)
        host.teardown()
        assertSame(label.typeDrawable, label.type.drawable)
    }

    @Test
    fun `HyperOS older AOSP data label anchors to mobile_type`() {
        dataLabel()
        val mobile = StatusBarMobileView(context, 7)
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val label = labelTree(mobile, 20, 10)
        leaf.layout(0, 0, 20, 20)

        val host = host(cellSource = { oneSimReading(7) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))
        assertMobileTypeLabel(host, mobile, label.type, 20, 10)
        host.teardown()
        assertSame(label.typeDrawable, label.type.drawable)
    }

    @Test
    fun `Samsung data label anchors to mobile_type`() {
        dataLabel()
        val mobile = ModernStatusBarMobileViewWithSub(context, 3)
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val label = labelTree(mobile, 20, 10)
        leaf.layout(0, 0, 20, 20)

        val host = host(cellSource = { oneSimReading(3) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))
        assertMobileTypeLabel(host, mobile, label.type, 20, 10)
        host.teardown()
        assertSame(label.typeDrawable, label.type.drawable)
    }

    @Test
    fun `vivo data label anchors to mobile_type`() {
        dataLabel()
        val mobile = ModernStatusBarMobileViewWithSub(context, 9)
        val leaf = VivoMobileSignal(context).apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val label = labelTree(mobile, 20, 10)
        leaf.layout(0, 0, 20, 20)

        val host = host(cellSource = { oneSimReading(9) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))
        assertMobileTypeLabel(host, mobile, label.type, 20, 10)
        host.teardown()
        assertSame(label.typeDrawable, label.type.drawable)
    }

    @Test
    fun `a mobile_group without mobile_type draws no label`() {
        dataLabel()
        val tree = tree()
        val mobile = ModernStatusBarMobileViewWithSub(context, 1)
        val group = NamedGroup(context, "mobile_group")
        mobile.addView(group, FrameLayout.LayoutParams(14, 49))
        tree.strip.addView(mobile, LinearLayout.LayoutParams(52, 49))
        tree.layout()
        mobile.layout(0, 0, 52, 49)
        group.layout(0, 0, 14, 49)

        val host = host(cellSource = { oneSimReading(1) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        host.attachIn(tree.strip, tree.root)

        assertFalse(host.recordKindsForTest().contains(IosIconKind.WIFI))
        assertNull("no label is drawn without a mobile_type", host.overlayForTest(IosIconKind.WIFI))
        // Whatever the host did with the cellular row, it never became a WIFI label.
        assertEquals(0, host.recordKindsForTest().count { it == IosIconKind.WIFI })
        host.teardown()
    }

    @Test
    fun `a GONE mobile_type on a non-OPLUS family draws no label and no overlay`() {
        dataLabel()
        val mobile = ModernStatusBarMobileViewWithSub(context, 7)
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val label = labelTree(mobile, 20, 10)
        leaf.layout(0, 0, 20, 20)
        label.type.visibility = View.GONE

        val host = host(cellSource = { oneSimReading(7) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))

        assertFalse("no label without a usable mobile_type", host.recordKindsForTest().contains(IosIconKind.WIFI))
        assertNull("no overlay in any case", host.overlayForTest(IosIconKind.WIFI))
        assertEquals(0, countCanvasViews(label.tree.root))
        assertSame("the stock type drawable is untouched", label.typeDrawable, label.type.drawable)
        val absent = host.diagnosticLines().first { it.contains(" label ") }
        assertTrue(absent, absent.contains("anchor=absent"))
        assertTrue(absent, absent.contains("reason=no-mobile-type"))

        host.teardown()
    }

    @Test
    fun `a non-ImageView mobile_type fails closed with no label and no overlay`() {
        dataLabel()
        val mobile = ModernStatusBarMobileViewWithSub(context, 7)
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val type = NamedGroup(context, "mobile_type")
        mobile.addView(type, FrameLayout.LayoutParams(20, 10))
        val tree = tree()
        tree.strip.addView(mobile, LinearLayout.LayoutParams(80, 60))
        tree.layout()
        mobile.layout(0, 0, 80, 60)
        leaf.layout(0, 0, 20, 20)
        type.layout(10, 10, 30, 20)

        val host = host(cellSource = { oneSimReading(7) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(tree.strip, tree.root))

        // The stock type is not an ImageView, so the drawable swap is impossible. R6 fails closed: the
        // stock type stays visible exactly as the ROM drew it, and no overlay replaces it.
        assertFalse("no label without a drawable anchor", host.recordKindsForTest().contains(IosIconKind.WIFI))
        assertNull("no overlay in any case", host.overlayForTest(IosIconKind.WIFI))
        assertEquals(0, countCanvasViews(tree.root))
        assertEquals(View.VISIBLE, type.visibility)
        assertEquals(20, type.width)
        assertEquals(10, type.height)
        val absent = host.diagnosticLines().first { it.contains(" label ") }
        assertTrue(absent, absent.contains("anchor=absent"))
        assertTrue(absent, absent.contains("reason=no-native-anchor"))

        host.teardown()
    }

    @Test
    fun `a transparent mobile_type on the swap path reports anchor-hidden`() {
        dataLabel()
        val mobile = ModernStatusBarMobileViewWithSub(context, 7)
        val leaf = NamedImageView(context, "mobile_signal").apply {
            setImageDrawable(ColorDrawable(Color.CYAN))
        }
        mobile.addView(leaf, FrameLayout.LayoutParams(20, 20))
        val label = labelTree(mobile, 20, 10)
        label.type.alpha = 0f
        leaf.layout(0, 0, 20, 20)

        val host = host(cellSource = { oneSimReading(7) }, wifiSource = { _, _, _, _, _ -> offWifiSample() })
        assertTrue(host.attachIn(label.tree.strip, label.tree.root))

        // The ImageView itself makes the label invisible; the dump must not claim a shown box.
        assertEquals(1, host.recordKindsForTest().count { it == IosIconKind.WIFI })
        assertNull("no overlay in any case", host.overlayForTest(IosIconKind.WIFI))
        assertEquals(0, countCanvasViews(label.tree.root))
        val line = host.diagnosticLines().first { it.contains(" label ") }
        assertTrue(line, line.contains("anchor=absent"))
        assertTrue(line, line.contains("reason=anchor-hidden"))
        assertEquals(0f, label.type.alpha, 0f)

        host.teardown()
    }

    // ------------------------------------------------------------------------------- helpers

    /**
     * R3 A5.1 no-thrash: a normal open/close is spaced by seconds, so 20 attach/teardown cycles must
     * all adopt, restore the exact stock state and never fill the surface guard.
     */
    private fun assertNoThrash(
        host: IosReplaceHost,
        expectedAttach: Boolean = true,
        attach: () -> Boolean,
        teardown: () -> Unit,
        stock: () -> Unit
    ) {
        // A focused test may have just attached/teardown'd; space the loop from that event too.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        repeat(20) { cycle ->
            assertEquals("cycle $cycle: attach", expectedAttach, attach())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            teardown()
            assertFalse("cycle $cycle: the guard tripped", host.isThrashTrippedForTest())
            assertEquals("cycle $cycle: records left behind", 0, host.recordCountForTest())
            stock()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        }
    }

    /**
     * A root whose strip can report `isShown` like a real keyguard/shade surface. The Activity is
     * retained by the content view, so the window stays alive for the whole fixture.
     */
    private fun windowRoot(): FrameLayout {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.layout(0, 0, 1000, 200)
        return root
    }

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
