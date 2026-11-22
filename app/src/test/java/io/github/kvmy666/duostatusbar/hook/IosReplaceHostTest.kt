package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * IOS-REPLACE Phase C2: the in-place replacement host.
 *
 * Every test runs with `classLoader = null`, so the Xposed draw installer cannot attach a real hook;
 * OUTER_DRAW must fall back to the leaf swap and LEAF_DRAW to the approved overlay. That is exactly the
 * fail-silent path a build without the measured class gets, and it is the only path that can be proven
 * off the phone. The draw registry is exercised directly for the OUTER_DRAW bounds case.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosReplaceHostTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
        IosReadings.resetForTest()
    }

    @After
    fun tearDown() {
        IosDrawingHooks.clearForTest()
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        IosPushHooks.clearForTest()
        IosDrawHookInstaller.releaseWhenEmpty()
        IosReadings.resetForTest()
    }

    // ------------------------------------------------------------------------ measured stock views

    private class StatBatteryMeterView(context: Context) : FrameLayout(context)

    private class OplusModernStatusBarWifiView(context: Context) : FrameLayout(context)

    class OplusModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) :
        FrameLayout(context) {
        fun getSubId(): Int = sub
    }

    private class MiuiBatteryMeterView(context: Context) : FrameLayout(context)

    private class ModernStatusBarWifiView(context: Context) : FrameLayout(context)

    class ModernStatusBarMobileViewWithSub(context: Context, private val sub: Int) :
        LinearLayout(context) {
        fun getSubId(): Int = sub
    }

    // ------------------------------------------------------------------------ named test views

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

    open class NamedGroup(context: Context, entry: String) : FrameLayout(context) {
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

    /**
     * A leaf that refuses our drawable and puts its stock one back: the true "no progress" fight the
     * anti-thrash guard must count, unlike a legitimate OEM push.
     */
    private class NoProgressNamedImageView(
        context: Context,
        entry: String,
        private val stock: Drawable
    ) : NamedImageView(context, entry) {
        var swallowOurDrawable = false

        override fun setImageDrawable(drawable: Drawable?) {
            if (swallowOurDrawable && drawable is IosGlyphDrawable) {
                super.setImageDrawable(stock)
                return
            }
            super.setImageDrawable(drawable)
        }
    }

    /**
     * A view that throws once after a layout-params write, so [IosCollapse]'s failure path can be
     * proven to put the slot back instead of leaving a 0×0 (or GONE) view behind.
     */
    private class ThrowingLayoutView(context: Context) : View(context) {
        var failNextLayoutParams = false

        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            super.setLayoutParams(params)
            if (failNextLayoutParams) {
                failNextLayoutParams = false
                throw IllegalStateException("layout params write failed")
            }
        }
    }

    /** An opaque Compose/AOSP-style icon view: the skin refuses it, so its stock drawing must stay. */
    private class StatIconView(context: Context, entry: String) : NamedView(context, entry)

    /**
     * The measured Compose icon family: it is suppressed in place and replaced by the approved overlay,
     * which is exactly the dual-SIM overlay fallback path (item 2).
     */
    class SingleBindableStatusBarComposeIconView(
        context: Context,
        entry: String,
        private val sub: Int
    ) : NamedGroup(context, entry) {
        @Suppress("unused")
        fun getSubId(): Int = sub
    }

    // ------------------------------------------------------------------------ host + readings

    private fun host(
        sizeOwner: IosSizeOwner = IosSizeOwner(),
        isMainBar: Boolean = true,
        surface: String = "main bar",
        batterySource: (Context) -> IosBatteryReading? = { IosBatteryReading(50, false, 0) },
        cellSource: (Context) -> CellReading = { noCellReading() },
        wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
            { _, _, _, _, _ -> wifiSample(validated = true) }
    ): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        sizeOwner = sizeOwner,
        isMainBar = isMainBar,
        surface = surface,
        batterySource = batterySource,
        cellSource = cellSource,
        wifiSource = wifiSource
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

    // ------------------------------------------------------------------------ trees

    private class BatteryTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val outer: StatBatteryMeterView,
        val leaf: NamedImageView,
        val bolt: NamedImageView,
        val percent: TextView,
        val original: Drawable,
        val originalBolt: Drawable
    )

    /** The measured OPLUS battery: `battery_icon_view` 79×48, a bolt sibling and the stock percent. */
    private fun oplusBatteryTree(): BatteryTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val outer = StatBatteryMeterView(context)
        val leaf = NamedImageView(context, "battery_icon_view")
        val original = ColorDrawable(Color.RED)
        leaf.setImageDrawable(original)
        val bolt = NamedImageView(context, "battery_charge_icon")
        val originalBolt = ColorDrawable(Color.BLUE)
        bolt.setImageDrawable(originalBolt)
        val percent = TextView(context).apply { text = "42%" }
        outer.addView(leaf, FrameLayout.LayoutParams(79, 48))
        outer.addView(bolt, FrameLayout.LayoutParams(10, 10))
        outer.addView(percent, FrameLayout.LayoutParams(20, 10))
        strip.addView(outer, LinearLayout.LayoutParams(90, 48))
        root.addView(strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 48)
        outer.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 48)
        bolt.layout(80, 0, 90, 10)
        percent.layout(60, 0, 80, 10)
        return BatteryTree(root, strip, outer, leaf, bolt, percent, original, originalBolt)
    }

    private class NoProgressBatteryTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val leaf: NoProgressNamedImageView,
        val original: Drawable
    )

    /** The measured OPLUS battery with a leaf that refuses our glyph (a true no-progress fight). */
    private fun noProgressBatteryTree(): NoProgressBatteryTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val outer = StatBatteryMeterView(context)
        val original = ColorDrawable(Color.RED)
        val leaf = NoProgressNamedImageView(context, "battery_icon_view", original)
        leaf.setImageDrawable(original)
        outer.addView(leaf, FrameLayout.LayoutParams(79, 48))
        strip.addView(outer, LinearLayout.LayoutParams(90, 48))
        root.addView(strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 48)
        outer.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 48)
        return NoProgressBatteryTree(root, strip, leaf, original)
    }

    private class OplusSignalTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val wifiOuter: OplusModernStatusBarWifiView,
        val wifiLeaf: NamedImageView,
        val wifiLeft: NamedImageView,
        val mobileOuter: OplusModernStatusBarMobileViewWithSub,
        val mobileLeaf: NamedImageView,
        val mobileInout: NamedImageView
    )

    /** The measured OPLUS outers: 0×0, content in `wifi_group`/`mobile_group`. */
    private fun oplusSignalTree(subId: Int = 5): OplusSignalTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)

        val wifi = OplusModernStatusBarWifiView(context)
        val wifiGroup = NamedGroup(context, "wifi_group")
        val wifiLeaf = NamedImageView(context, "wifi_signal")
        wifiLeaf.setImageDrawable(ColorDrawable(Color.GREEN))
        val wifiLeft = NamedImageView(context, "wifi_left")
        wifiLeft.setImageDrawable(ColorDrawable(Color.GRAY))
        wifi.addView(wifiGroup, FrameLayout.LayoutParams(73, 63))
        wifiGroup.addView(wifiLeaf, FrameLayout.LayoutParams(70, 51))
        wifiGroup.addView(wifiLeft, FrameLayout.LayoutParams(10, 10))

        val mobile = OplusModernStatusBarMobileViewWithSub(context, subId)
        val mobileGroup = NamedGroup(context, "mobile_group")
        val mobileLeaf = NamedImageView(context, "mobile_signal")
        mobileLeaf.setImageDrawable(ColorDrawable(Color.CYAN))
        val mobileInout = NamedImageView(context, "data_inout")
        mobileInout.setImageDrawable(ColorDrawable(Color.MAGENTA))
        mobile.addView(mobileGroup, FrameLayout.LayoutParams(60, 63))
        mobileGroup.addView(mobileLeaf, FrameLayout.LayoutParams(60, 51))
        mobileGroup.addView(mobileInout, FrameLayout.LayoutParams(8, 8))

        strip.addView(wifi)
        strip.addView(mobile)
        root.addView(strip, FrameLayout.LayoutParams(150, 63))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 150, 63)
        wifi.layout(0, 0, 0, 0)
        wifiGroup.layout(0, 0, 73, 63)
        wifiLeaf.layout(3, 6, 73, 57)
        wifiLeft.layout(60, 0, 70, 10)
        mobile.layout(0, 0, 0, 0)
        mobileGroup.layout(0, 0, 60, 63)
        mobileLeaf.layout(2, 6, 62, 57)
        mobileInout.layout(50, 0, 58, 8)
        return OplusSignalTree(
            root, strip, wifi, wifiLeaf, wifiLeft, mobile, mobileLeaf, mobileInout
        )
    }

    private class HyperosBatteryTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val outer: MiuiBatteryMeterView,
        val hollow: NamedImageView,
        val flat: NamedImageView,
        val hollowDrawable: Drawable,
        val flatDrawable: Drawable
    )

    private fun hyperosBatteryTree(): HyperosBatteryTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val outer = MiuiBatteryMeterView(context)
        val hollow = NamedImageView(context, "hollow_battery_image")
        val hollowDrawable = ColorDrawable(Color.RED)
        hollow.setImageDrawable(hollowDrawable)
        val flat = NamedImageView(context, "battery_image")
        val flatDrawable = ColorDrawable(Color.BLUE)
        flat.setImageDrawable(flatDrawable)
        flat.visibility = View.GONE
        outer.addView(hollow, FrameLayout.LayoutParams(20, 10))
        outer.addView(flat, FrameLayout.LayoutParams(20, 10))
        strip.addView(outer, LinearLayout.LayoutParams(40, 40))
        root.addView(strip, FrameLayout.LayoutParams(200, 60))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 60)
        outer.layout(0, 0, 40, 40)
        hollow.layout(0, 0, 20, 10)
        flat.layout(0, 0, 20, 10)
        return HyperosBatteryTree(root, strip, outer, hollow, flat, hollowDrawable, flatDrawable)
    }

    private class HyperosSignalTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val wifiOuter: ModernStatusBarWifiView,
        val wifiLeaf: NamedImageView,
        val wifiActivity: NamedImageView,
        val mobileOuter: ModernStatusBarMobileViewWithSub,
        val mobileLeaf: NamedImageView,
        val mobileType: NamedImageView
    )

    private fun hyperosSignalTree(subId: Int = 7): HyperosSignalTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)

        val wifi = ModernStatusBarWifiView(context)
        val wifiLeaf = NamedImageView(context, "wifi_signal")
        wifiLeaf.setImageDrawable(ColorDrawable(Color.GREEN))
        val wifiActivity = NamedImageView(context, "wifi_activity")
        wifiActivity.setImageDrawable(ColorDrawable(Color.GRAY))
        wifi.addView(wifiLeaf, FrameLayout.LayoutParams(24, 24))
        wifi.addView(wifiActivity, FrameLayout.LayoutParams(6, 6))

        val mobile = ModernStatusBarMobileViewWithSub(context, subId)
        val mobileLeaf = NamedImageView(context, "mobile_signal")
        mobileLeaf.setImageDrawable(ColorDrawable(Color.CYAN))
        val mobileType = NamedImageView(context, "mobile_type")
        mobileType.setImageDrawable(ColorDrawable(Color.MAGENTA))
        mobile.addView(mobileLeaf, FrameLayout.LayoutParams(20, 20))
        mobile.addView(mobileType, FrameLayout.LayoutParams(6, 6))

        strip.addView(wifi, LinearLayout.LayoutParams(24, 24))
        strip.addView(mobile, LinearLayout.LayoutParams(20, 20))
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 100, 40)
        wifi.layout(0, 0, 24, 24)
        wifiLeaf.layout(0, 0, 24, 24)
        wifiActivity.layout(18, 18, 24, 24)
        mobile.layout(30, 0, 50, 20)
        mobileLeaf.layout(0, 0, 20, 20)
        mobileType.layout(14, 14, 20, 20)
        return HyperosSignalTree(root, strip, wifi, wifiLeaf, wifiActivity, mobile, mobileLeaf, mobileType)
    }

    private class DualMobileTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val first: ModernStatusBarMobileViewWithSub,
        val second: ModernStatusBarMobileViewWithSub,
        val firstLeaf: NamedImageView,
        val secondLeaf: NamedImageView,
        val firstDrawable: Drawable,
        val secondDrawable: Drawable
    )

    private fun dualMobileTree(firstSub: Int = 1, secondSub: Int = 2): DualMobileTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val first = ModernStatusBarMobileViewWithSub(context, firstSub)
        val second = ModernStatusBarMobileViewWithSub(context, secondSub)
        val firstLeaf = NamedImageView(context, "mobile_signal")
        val secondLeaf = NamedImageView(context, "mobile_signal")
        val firstDrawable = ColorDrawable(Color.GREEN)
        val secondDrawable = ColorDrawable(Color.RED)
        firstLeaf.setImageDrawable(firstDrawable)
        secondLeaf.setImageDrawable(secondDrawable)
        first.addView(firstLeaf, LinearLayout.LayoutParams(20, 20))
        second.addView(secondLeaf, LinearLayout.LayoutParams(20, 20))
        strip.addView(first, LinearLayout.LayoutParams(20, 20))
        strip.addView(second, LinearLayout.LayoutParams(20, 20))
        root.addView(strip, FrameLayout.LayoutParams(80, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 80, 40)
        first.layout(0, 0, 20, 20)
        second.layout(30, 0, 50, 20)
        firstLeaf.layout(0, 0, 20, 20)
        secondLeaf.layout(0, 0, 20, 20)
        return DualMobileTree(
            root, strip, first, second, firstLeaf, secondLeaf, firstDrawable, secondDrawable
        )
    }

    private class ZeroOuterDualMobileTree(
        val root: FrameLayout,
        val strip: LinearLayout,
        val first: OplusModernStatusBarMobileViewWithSub,
        val second: OplusModernStatusBarMobileViewWithSub,
        val firstLeaf: NamedImageView,
        val secondLeaf: NamedImageView,
        val firstDrawable: Drawable,
        val secondDrawable: Drawable
    )

    /** Two measured OPLUS mobile outers: 0×0 with a visible 60×63 `mobile_group` each. */
    private fun zeroOuterDualMobileTree(
        firstSub: Int = 1,
        secondSub: Int = 2
    ): ZeroOuterDualMobileTree {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val first = OplusModernStatusBarMobileViewWithSub(context, firstSub)
        val second = OplusModernStatusBarMobileViewWithSub(context, secondSub)
        val firstGroup = NamedGroup(context, "mobile_group")
        val secondGroup = NamedGroup(context, "mobile_group")
        val firstLeaf = NamedImageView(context, "mobile_signal")
        val secondLeaf = NamedImageView(context, "mobile_signal")
        val firstDrawable = ColorDrawable(Color.CYAN)
        val secondDrawable = ColorDrawable(Color.MAGENTA)
        firstLeaf.setImageDrawable(firstDrawable)
        secondLeaf.setImageDrawable(secondDrawable)
        firstGroup.addView(firstLeaf, FrameLayout.LayoutParams(60, 51))
        secondGroup.addView(secondLeaf, FrameLayout.LayoutParams(60, 51))
        first.addView(firstGroup, FrameLayout.LayoutParams(60, 63))
        second.addView(secondGroup, FrameLayout.LayoutParams(60, 63))
        strip.addView(first, LinearLayout.LayoutParams(0, 0))
        strip.addView(second, LinearLayout.LayoutParams(0, 0))
        root.addView(strip, FrameLayout.LayoutParams(150, 63))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 150, 63)
        first.layout(0, 0, 0, 0)
        second.layout(0, 0, 0, 0)
        firstGroup.layout(0, 0, 60, 63)
        secondGroup.layout(0, 0, 60, 63)
        firstLeaf.layout(0, 0, 60, 51)
        secondLeaf.layout(0, 0, 60, 51)
        return ZeroOuterDualMobileTree(
            root, strip, first, second, firstLeaf, secondLeaf, firstDrawable, secondDrawable
        )
    }

    // ------------------------------------------------------------------------ tests

    @Test
    fun `oplus battery becomes a battery drawable and restores exactly`() {
        val tree = oplusBatteryTree()
        val percentIcon = NamedImageView(context, "battery_percentage_view").apply {
            setImageDrawable(ColorDrawable(Color.YELLOW))
        }
        val digit = NamedImageView(context, "battery_digit").apply {
            setImageDrawable(ColorDrawable(Color.BLACK))
        }
        val dot = NamedImageView(context, "status_bar_dot").apply {
            setImageDrawable(ColorDrawable(Color.WHITE))
        }
        tree.outer.addView(percentIcon, FrameLayout.LayoutParams(6, 6))
        tree.outer.addView(digit, FrameLayout.LayoutParams(6, 6))
        tree.outer.addView(dot, FrameLayout.LayoutParams(6, 6))
        val percentDrawable = percentIcon.drawable
        val digitDrawable = digit.drawable
        val dotDrawable = dot.drawable
        val host = host()

        assertTrue(host.attachIn(tree.strip, null))
        assertTrue(host.attached)

        val installed = tree.leaf.drawable
        assertTrue(installed is IosBatteryDrawable)
        assertEquals(79, installed!!.intrinsicWidth)
        assertEquals(48, installed.intrinsicHeight)

        // The bolt sibling is blanked with an alpha-0 drawable; the system percent is untouched.
        assertNotNull(tree.bolt.drawable)
        assertEquals(0, tree.bolt.drawable.alpha)
        assertEquals("42%", tree.percent.text)
        assertEquals(View.VISIBLE, tree.percent.visibility)

        // The forbidden percent/digit/dot ids are never hidden, even when they carry that name.
        assertSame(percentDrawable, percentIcon.drawable)
        assertSame(digitDrawable, digit.drawable)
        assertSame(dotDrawable, dot.drawable)
        assertEquals(View.VISIBLE, percentIcon.visibility)
        assertEquals(View.VISIBLE, digit.visibility)
        assertEquals(View.VISIBLE, dot.visibility)

        host.teardown()

        assertFalse(host.attached)
        assertSame(tree.original, tree.leaf.drawable)
        assertSame(tree.originalBolt, tree.bolt.drawable)
        assertEquals(View.VISIBLE, tree.bolt.visibility)
        assertSame(percentDrawable, percentIcon.drawable)
        assertSame(digitDrawable, digit.drawable)
        assertSame(dotDrawable, dot.drawable)
    }

    @Test
    fun `oplus outer draw falls back to the leaf swap and its siblings are blanked`() {
        val tree = oplusSignalTree(subId = 5)
        val percentIcon = NamedImageView(context, "battery_percentage_view").apply {
            setImageDrawable(ColorDrawable(Color.YELLOW))
        }
        val digit = NamedImageView(context, "battery_digit").apply {
            setImageDrawable(ColorDrawable(Color.BLACK))
        }
        val dot = NamedImageView(context, "status_bar_dot").apply {
            setImageDrawable(ColorDrawable(Color.WHITE))
        }
        tree.wifiOuter.addView(percentIcon, FrameLayout.LayoutParams(6, 6))
        tree.wifiOuter.addView(digit, FrameLayout.LayoutParams(6, 6))
        tree.wifiOuter.addView(dot, FrameLayout.LayoutParams(6, 6))
        val percentDrawable = percentIcon.drawable
        val digitDrawable = digit.drawable
        val dotDrawable = dot.drawable

        val wifiOriginal = tree.wifiLeaf.drawable
        val mobileOriginal = tree.mobileLeaf.drawable
        val host = host(cellSource = { oneSimReading(5) })

        assertTrue(host.attachIn(tree.strip, null))

        assertTrue(tree.wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(tree.mobileLeaf.drawable is IosCellDrawable)
        assertNotNull(tree.wifiLeft.drawable)
        assertEquals(0, tree.wifiLeft.drawable.alpha)
        assertNotNull(tree.mobileInout.drawable)
        assertEquals(0, tree.mobileInout.drawable.alpha)

        // Forbidden ids are never part of fallbackSiblingIds and are not hidden.
        assertSame(percentDrawable, percentIcon.drawable)
        assertSame(digitDrawable, digit.drawable)
        assertSame(dotDrawable, dot.drawable)
        assertEquals(View.VISIBLE, percentIcon.visibility)
        assertEquals(View.VISIBLE, digit.visibility)
        assertEquals(View.VISIBLE, dot.visibility)

        host.teardown()
        assertSame(wifiOriginal, tree.wifiLeaf.drawable)
        assertSame(mobileOriginal, tree.mobileLeaf.drawable)
        assertSame(percentDrawable, percentIcon.drawable)
        assertSame(digitDrawable, digit.drawable)
        assertSame(dotDrawable, dot.drawable)
    }

    @Test
    fun `outer draw session uses the leaf rect at its offset and draws`() {
        val tree = oplusSignalTree()
        val host = host()
        // wifiLeaf is laid out at (3,6)-(73,57) inside wifiOuter: [l,t,r,b], not [x,y,w,h].
        val bounds = host.leafBoundsForTest(tree.wifiLeaf, tree.wifiOuter)
        assertArrayEquals(intArrayOf(3, 6, 73, 57), bounds)

        val visual = IosMapping.wifi(wifiSample(validated = true), Color.WHITE)
        val drawable = IosDrawables.create(IosIconKind.WIFI, visual, 70, 51)
        val session = IosDrawSession(
            drawable = drawable,
            bounds = { bounds },
            translateX = null
        )
        IosDrawingHooks.register(tree.wifiOuter, session)

        val bitmap = Bitmap.createBitmap(73, 57, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        assertTrue(IosDrawingHooks.renderForTest(tree.wifiOuter, canvas))
        // The old [x,y,w,h] return drew 67x45 here; the leaf rect pins the full 70x51 glyph.
        assertEquals(70, drawable.bounds.width())
        assertEquals(51, drawable.bounds.height())
        assertTrue("the glyph drew ink", ink(bitmap) > 0)
        val ink = inkBounds(bitmap)
        assertTrue("ink starts at the leaf offset", ink[0] >= 3 && ink[1] >= 6)
        assertTrue("ink stays inside the leaf rect", ink[2] <= 73 && ink[3] <= 57)

        IosDrawingHooks.unregister(tree.wifiOuter)
        assertFalse(IosDrawingHooks.renderForTest(tree.wifiOuter, canvas))
    }

    @Test
    fun `hyperos battery leaf draw falls back to an overlay without touching the stock leaves`() {
        val tree = hyperosBatteryTree()
        val host = host()

        assertTrue(host.attachIn(tree.strip, tree.root))

        // classLoader = null cannot install the onDraw hook, so the approved overlay path runs.
        assertSame(tree.hollowDrawable, tree.hollow.drawable)
        assertSame(tree.flatDrawable, tree.flat.drawable)
        assertEquals(View.VISIBLE, tree.hollow.visibility)
        assertEquals(View.GONE, tree.flat.visibility)
        assertEquals(2, tree.root.childCount)
        assertTrue(containsCanvasView(tree.root))

        host.teardown()
        assertSame(tree.hollowDrawable, tree.hollow.drawable)
        assertSame(tree.flatDrawable, tree.flat.drawable)
    }

    @Test
    fun `an overlay canvas grows around the slot centre at 150 percent and does not drift`() {
        val tree = hyperosBatteryTree()
        val host = host()
        assertTrue(host.attachIn(tree.strip, tree.root))

        val canvas = findCanvas(tree.root)
        assertNotNull("the battery fell back to the overlay", canvas)
        val identity = canvas!!.layoutParams as FrameLayout.LayoutParams
        val base = intArrayOf(
            identity.leftMargin,
            identity.topMargin,
            identity.width,
            identity.height
        )
        assertTrue("the fixture must be enlarged by the scale", base[2] > 1)

        host.refreshSettings(ModuleSettings.DEFAULT.copy(iosBatteryScale = 150))
        shadowOf(Looper.getMainLooper()).idle()

        val scaled = canvas.layoutParams as FrameLayout.LayoutParams
        val expected = overlayBox(base[0], base[1], base[2], base[3], dxPx = 0f, scale = 1.5f)
        assertArrayEquals(
            expected,
            intArrayOf(scaled.leftMargin, scaled.topMargin, scaled.width, scaled.height)
        )
        // The box is centred: the slot centre is preserved by the growth.
        assertEquals(
            base[0] + base[2] / 2f,
            scaled.leftMargin + scaled.width / 2f,
            1.0f
        )

        // A second refresh with the same settings must not grow/shift again.
        host.refresh()
        val again = canvas.layoutParams as FrameLayout.LayoutParams
        assertArrayEquals(
            intArrayOf(scaled.leftMargin, scaled.topMargin, scaled.width, scaled.height),
            intArrayOf(again.leftMargin, again.topMargin, again.width, again.height)
        )

        host.teardown()
    }

    @Test
    fun `allow glyph overflow clears the targets ancestors up to the strip only`() {
        val tree = oplusBatteryTree()
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        // Stock ancestors start clipping, then a scaled glyph asks for room.
        tree.root.clipChildren = true
        tree.root.clipToPadding = true
        tree.strip.clipChildren = true
        tree.strip.clipToPadding = true
        tree.outer.clipChildren = true
        tree.outer.clipToPadding = true

        // The draw target is the leaf ImageView (not a ViewGroup): the parent chain is used.
        host.allowGlyphOverflow(tree.leaf)

        assertEquals(false, tree.outer.clipChildren)
        assertEquals(false, tree.outer.clipToPadding)
        assertEquals(false, tree.strip.clipChildren)
        assertEquals(false, tree.strip.clipToPadding)
        // The window root is above the strip and must stay untouched.
        assertEquals(true, tree.root.clipChildren)
        assertEquals(true, tree.root.clipToPadding)

        host.teardown()
    }

    @Test
    fun `the wifi slot state is copied onto every visual`() {
        IosReadings.resetForTest(
            data = { DataLabelSample("5G", mobileActive = true, source = DataLabelReader.SRC_ACTIVE) }
        )
        val tree = oplusBatteryTree()
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        assertEquals(
            IosWifiSlot.DataType("5G"),
            host.visualForTest(tree.leaf)?.wifiSlot
        )

        host.teardown()
    }

    @Test
    fun `hyperos wifi and mobile replace the named leaves and blank their siblings`() {
        val tree = hyperosSignalTree(subId = 7)
        val owner = IosSizeOwner()
        val host = host(sizeOwner = owner, cellSource = { oneSimReading(7) })

        assertTrue(host.attachIn(tree.strip, null))

        val wifi = tree.wifiLeaf.drawable
        assertTrue(wifi is IosWifiDrawable)
        assertEquals(24, wifi!!.intrinsicWidth)
        assertEquals(24, wifi.intrinsicHeight)
        assertTrue(tree.mobileLeaf.drawable is IosCellDrawable)
        assertEquals(0, tree.wifiActivity.drawable.alpha)
        assertEquals(0, tree.mobileType.drawable.alpha)
        assertEquals(Pair(24, 24), owner.sizeFor(IosIconKind.WIFI, 24, 24))

        host.teardown()
        assertEquals(View.VISIBLE, tree.wifiLeaf.visibility)
        assertEquals(View.VISIBLE, tree.mobileLeaf.visibility)
    }

    @Test
    fun `an off wifi restores the stock drawable instead of a blank`() {
        val tree = hyperosSignalTree(subId = 7)
        val original = tree.wifiLeaf.drawable
        var sample = wifiSample(validated = true)
        val host = host(
            cellSource = { noCellReading() },
            wifiSource = { _, _, _, _, _ -> sample }
        )
        assertTrue(host.attachIn(tree.strip, null))
        assertTrue(tree.wifiLeaf.drawable is IosWifiDrawable)

        sample = sample.copy(on = false, active = false, validated = false)
        host.refresh()

        assertSame("the stock drawable comes back", original, tree.wifiLeaf.drawable)
        assertNotNull(tree.wifiLeaf.drawable)
        host.teardown()
    }

    @Test
    fun `an opaque stat icon never gets an overlay canvas and keeps its stock drawing`() {
        val tree = oplusBatteryTree()
        val opaque = StatIconView(context, "wifi")
        tree.strip.addView(opaque, LinearLayout.LayoutParams(24, 24))
        opaque.layout(120, 0, 144, 24)

        // The planner offers the approved overlay, but the skin refuses SKIP: stock must stay.
        assertEquals(IosReplaceMechanism.OVERLAY, IosReplacePlanner.planFor(opaque).mechanism)
        assertEquals(IosSkinStrategy.SKIP, IosSkinPlanner.planFor(opaque).strategy)

        val host = host()
        assertTrue(host.attachIn(tree.strip, null))
        assertTrue(tree.leaf.drawable is IosBatteryDrawable)
        assertEquals(View.VISIBLE, opaque.visibility)
        assertFalse("no canvas over the opaque stock", containsCanvasView(tree.root))

        host.teardown()
        assertEquals(View.VISIBLE, opaque.visibility)
    }

    @Test
    fun `the cell visual follows the data leaf tint and refreshTintForTest updates it`() {
        val tree = dualMobileTree(firstSub = 1, secondSub = 2)
        tree.firstLeaf.imageTintList = ColorStateList.valueOf(Color.RED)
        tree.secondLeaf.imageTintList = ColorStateList.valueOf(Color.BLUE)
        val host = host(cellSource = { twoSimReading(1, 2) })

        assertTrue(host.attachIn(tree.strip, null))
        host.refresh()

        assertEquals(Color.RED, host.visualTintForTest(tree.firstLeaf))
        // The other SIM is collapsed into the same BOTH record, so it has no record of its own.
        assertNull(host.visualTintForTest(tree.secondLeaf))

        tree.firstLeaf.imageTintList = ColorStateList.valueOf(Color.YELLOW)
        host.refreshTintForTest()
        assertEquals(Color.YELLOW, host.visualTintForTest(tree.firstLeaf))
        host.teardown()
    }

    @Test
    fun `dual SIM draws one BOTH block and collapses the other slot 1-2-1`() {
        val tree = dualMobileTree(firstSub = 1, secondSub = 2)
        val otherParams = tree.second.layoutParams
        val otherWidth = otherParams.width
        val otherHeight = otherParams.height
        var reading = twoSimReading(1, 2)
        val host = host(cellSource = { reading })

        assertTrue(host.attachIn(tree.strip, null))
        // Item 2: exactly one CELL record on the data view, drawing both rows.
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertEquals(View.VISIBLE, tree.first.visibility)
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0, otherParams.width)
        assertEquals(0, otherParams.height)

        // One SIM: the BOTH record is dropped and the other slot is restored exactly.
        reading = oneSimReading(1, 3)
        host.refresh()
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.DATA, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertEquals(View.VISIBLE, tree.second.visibility)
        assertEquals(otherWidth, tree.second.layoutParams.width)
        assertEquals(otherHeight, tree.second.layoutParams.height)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)

        // Dual again: one BOTH block, the other slot removed again (1 -> 2 -> 1 in layout terms).
        reading = twoSimReading(1, 2)
        host.refresh()
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(tree.first))
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0, tree.second.layoutParams.width)
        assertEquals(0, tree.second.layoutParams.height)

        host.teardown()
        assertSame(tree.firstDrawable, tree.firstLeaf.drawable)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)
        assertEquals(View.VISIBLE, tree.first.visibility)
        assertEquals(View.VISIBLE, tree.second.visibility)
        assertEquals(otherWidth, tree.second.layoutParams.width)
        assertEquals(otherHeight, tree.second.layoutParams.height)
    }

    @Test
    fun `dual 0x0 mobile outers with sized groups still collapse to one BOTH slot`() {
        val tree = zeroOuterDualMobileTree(firstSub = 1, secondSub = 2)
        val otherParams = tree.second.layoutParams
        val otherVisibility = tree.second.visibility
        val otherWidth = otherParams.width
        val otherHeight = otherParams.height
        assertEquals(View.VISIBLE, otherVisibility)
        assertEquals(0, otherWidth)
        assertEquals(0, otherHeight)

        var reading = twoSimReading(1, 2)
        val host = host(cellSource = { reading })

        assertTrue(host.attachIn(tree.strip, null))
        // One BOTH record on the data view; the 0×0 other outer is collapsed despite its bounds.
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertEquals(View.VISIBLE, tree.first.visibility)
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0, tree.second.layoutParams.width)
        assertEquals(0, tree.second.layoutParams.height)

        // One SIM: the other outer goes back to its exact original visibility and layout params.
        reading = oneSimReading(1, 3)
        host.refresh()
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.DATA, host.cellRoleForTest(tree.first))
        assertEquals(otherVisibility, tree.second.visibility)
        assertEquals(otherWidth, tree.second.layoutParams.width)
        assertEquals(otherHeight, tree.second.layoutParams.height)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)

        // Dual again: the sized child is still measurable, so the other slot collapses again.
        reading = twoSimReading(1, 2)
        host.refresh()
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(tree.first))
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0, tree.second.layoutParams.width)
        assertEquals(0, tree.second.layoutParams.height)

        host.teardown()
        assertSame(tree.firstDrawable, tree.firstLeaf.drawable)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)
        assertEquals(View.VISIBLE, tree.first.visibility)
        assertEquals(otherVisibility, tree.second.visibility)
        assertEquals(otherWidth, tree.second.layoutParams.width)
        assertEquals(otherHeight, tree.second.layoutParams.height)
    }

    @Test
    fun `a collapse that throws after the layout write restores its slot`() {
        val view = ThrowingLayoutView(context)
        view.layoutParams = FrameLayout.LayoutParams(20, 20)
        view.layout(0, 0, 20, 20)
        view.failNextLayoutParams = true

        val collapse = IosCollapse(view)
        assertFalse("the failed apply reports failure", collapse.apply())
        assertFalse(collapse.isApplied)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("the original width is back", 20, view.layoutParams.width)
        assertEquals("the original height is back", 20, view.layoutParams.height)
    }

    @Test
    fun `a restore that throws stays applied and retries its slot`() {
        val view = ThrowingLayoutView(context)
        view.layoutParams = FrameLayout.LayoutParams(20, 20)
        view.layout(0, 0, 20, 20)

        val collapse = IosCollapse(view)
        assertTrue(collapse.apply())
        assertTrue(collapse.isApplied)
        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.layoutParams.width)

        // The one-shot throw lands after this restore's layout write.
        view.failNextLayoutParams = true
        collapse.restore()
        assertTrue("a failed restore must stay retryable", collapse.isApplied)

        collapse.restore()
        assertFalse(collapse.isApplied)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(20, view.layoutParams.width)
        assertEquals(20, view.layoutParams.height)
    }

    @Test
    fun `an outer background or foreground tint write refreshes the glyph`() {
        val tree = oplusSignalTree(subId = 5)
        val host = host(cellSource = { oneSimReading(5) })
        assertTrue(host.attachIn(tree.strip, null))

        tree.mobileOuter.backgroundTintList = ColorStateList.valueOf(Color.MAGENTA)
        IosPushHooks.deliverTintForTest(tree.mobileOuter)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Color.MAGENTA, host.visualTintForTest(tree.mobileLeaf))

        tree.mobileOuter.backgroundTintList = null
        tree.mobileOuter.foregroundTintList = ColorStateList.valueOf(Color.CYAN)
        IosPushHooks.deliverTintForTest(tree.mobileOuter)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Color.CYAN, host.visualTintForTest(tree.mobileLeaf))

        host.teardown()
    }

    @Test
    fun `a second host on the same surface and strip is refused until the first releases`() {
        val tree = oplusBatteryTree()
        val first = host()
        assertTrue(first.attachIn(tree.strip, null))

        // Rule 7: the old overlay host and the replacement cannot own the same strip in parallel.
        val second = host()
        assertFalse(second.attachIn(tree.strip, null))
        assertEquals(0, second.recordCountForTest())

        // Teardown releases the claim; the replacement may then take the unchanged tree.
        first.teardown()
        assertTrue(second.attachIn(tree.strip, null))

        // A different surface and strip are independent.
        val other = oplusBatteryTree()
        val extra = host(isMainBar = false, surface = "shade header")
        assertTrue(extra.attachIn(other.strip, null))
    }

    @Test
    fun `eight legitimate OEM pushes are adopted without tripping the guard`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val tree = oplusBatteryTree()
        activity.setContentView(tree.root)
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        // Item 0d: each OEM level push is put back in the same call and is progress, not a fight.
        repeat(8) {
            tree.leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            IosPushHooks.deliverDrawablePushForTest(tree.leaf)
            assertTrue("the push hook puts ours back", tree.leaf.drawable is IosBatteryDrawable)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("legitimate pushes must not trip the guard", host.isThrashTrippedForTest())

        // The pre-draw/refresh safety net sees a successful write too and still does not count.
        repeat(8) {
            tree.leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            host.refresh()
            assertTrue("the swap puts ours back", tree.leaf.drawable is IosBatteryDrawable)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.isThrashTrippedForTest())

        host.teardown()
        assertSame(tree.original, tree.leaf.drawable)
    }

    @Test
    fun `eight no-progress re-asserts trip the guard and restore the stock drawing`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val tree = noProgressBatteryTree()
        activity.setContentView(tree.root)
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        // The OEM immediately rewrites its own drawable after every set: our glyph never stays.
        tree.leaf.swallowOurDrawable = true
        repeat(8) {
            tree.leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            host.refresh()
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(host.isThrashTrippedForTest())
        assertSame(tree.original, tree.leaf.drawable)

        // Backed off: a refresh and an icon add do not re-adopt.
        host.refresh()
        assertSame(tree.original, tree.leaf.drawable)
        host.onStockIconAdded(tree.leaf)
        assertTrue(host.isThrashTrippedForTest())
        assertSame(tree.original, tree.leaf.drawable)

        // A settings change is a real change: the next fresh attach adopts again.
        host.refreshSettings(ModuleSettings.DEFAULT.copy(revision = 1))
        assertFalse(host.isThrashTrippedForTest())
        tree.leaf.swallowOurDrawable = false
        assertTrue(host.attachIn(tree.strip, null))
        assertTrue(tree.leaf.drawable is IosBatteryDrawable)
        host.teardown()
    }

    @Test
    fun `liveness follows the window root and a second attach of the same strip does not rebuild`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val strip = LinearLayout(activity)
        val outer = StatBatteryMeterView(activity)
        val leaf = NamedImageView(activity, "battery_icon_view")
        leaf.setImageDrawable(ColorDrawable(Color.RED))
        outer.addView(leaf, FrameLayout.LayoutParams(79, 48))
        strip.addView(outer, LinearLayout.LayoutParams(90, 48))
        root.addView(strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 48)
        outer.layout(0, 0, 90, 48)
        leaf.layout(0, 0, 79, 48)

        val host = host()
        assertTrue(host.attachIn(strip, null))
        assertTrue(host.attachedIn(root))
        assertTrue(host.attachedAlive(root))
        assertFalse(host.attachedAlive(FrameLayout(context)))

        val installed = leaf.drawable
        assertTrue(host.attachIn(strip, null))
        assertSame(installed, leaf.drawable)
        assertEquals(1, host.recordCountForTest())

        host.teardown()
    }

    @Test
    fun `a compose-only strip delegates to the canvas fallback and creates no Rive view`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val compose = NamedView(context, "battery_composable_view")
        strip.addView(compose, LinearLayout.LayoutParams(40, 20))
        root.addView(strip, FrameLayout.LayoutParams(100, 40))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 100, 40)
        compose.layout(0, 0, 40, 20)

        assertEquals(
            IosReplaceMechanism.OVERLAY,
            IosReplacePlanner.planFor(compose).mechanism
        )

        val host = host()
        assertTrue(host.attachIn(strip, root))
        assertTrue(host.attached)
        assertFalse(hasRiveView(root))
        assertTrue(containsCanvasView(root))

        host.teardown()
    }

    @Test
    fun `a battery level change updates the same drawable without a layout write`() {
        var reading = IosBatteryReading(90, false, 0)
        val tree = oplusBatteryTree()
        val host = host(batterySource = { reading })
        assertTrue(host.attachIn(tree.strip, null))

        val drawable = tree.leaf.drawable as IosBatteryDrawable
        val params = tree.leaf.layoutParams
        val width = tree.leaf.width
        val height = tree.leaf.height
        val visibility = tree.leaf.visibility

        val before = render(drawable, 79, 48)
        reading = IosBatteryReading(10, false, 0)
        host.refresh()
        val after = render(drawable, 79, 48)

        assertSame("the drawable instance is reused", drawable, tree.leaf.drawable)
        assertFalse("the visual changed", before.sameAs(after))
        assertSame(params, tree.leaf.layoutParams)
        assertEquals(width, tree.leaf.width)
        assertEquals(height, tree.leaf.height)
        assertEquals(visibility, tree.leaf.visibility)

        host.teardown()
    }

    @Test
    fun `the main drawing leaf keeps its layout params size and visibility`() {
        val battery = oplusBatteryTree()
        val signal = hyperosSignalTree(subId = 7)
        // One host over a strip holding every drawing leaf under test.
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        battery.strip.removeView(battery.outer)
        signal.strip.removeView(signal.wifiOuter)
        signal.strip.removeView(signal.mobileOuter)
        strip.addView(battery.outer, LinearLayout.LayoutParams(90, 48))
        strip.addView(signal.wifiOuter, LinearLayout.LayoutParams(24, 24))
        strip.addView(signal.mobileOuter, LinearLayout.LayoutParams(20, 20))
        root.addView(strip, FrameLayout.LayoutParams(200, 63))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 63)
        battery.outer.layout(0, 0, 90, 48)
        battery.leaf.layout(0, 0, 79, 48)
        signal.wifiOuter.layout(0, 0, 24, 24)
        signal.wifiLeaf.layout(0, 0, 24, 24)
        signal.mobileOuter.layout(30, 0, 50, 20)
        signal.mobileLeaf.layout(0, 0, 20, 20)

        val leaves = listOf<View>(battery.leaf, signal.wifiLeaf, signal.mobileLeaf)
        val snapshots = leaves.map {
            Snapshot(
                layoutParams = it.layoutParams,
                width = it.width,
                height = it.height,
                visibility = it.visibility
            )
        }

        val host = host(cellSource = { oneSimReading(7) })
        assertTrue(host.attachIn(strip, null))
        host.refresh()

        for (index in leaves.indices) {
            val leaf = leaves[index]
            val snapshot = snapshots[index]
            assertSame(snapshot.layoutParams, leaf.layoutParams)
            assertEquals(snapshot.width, leaf.width)
            assertEquals(snapshot.height, leaf.height)
            assertEquals(snapshot.visibility, leaf.visibility)
        }

        host.teardown()
    }

    // ------------------------------------------------------------------------ item 6: dynamic tint

    private fun combinedSignalAndBatteryStrip(): Triple<OplusSignalTree, BatteryTree, LinearLayout> {
        val signal = oplusSignalTree(subId = 5)
        val battery = oplusBatteryTree()
        battery.strip.removeView(battery.outer)
        signal.strip.addView(battery.outer, LinearLayout.LayoutParams(90, 48))
        signal.strip.layout(0, 0, 240, 63)
        signal.root.layout(0, 0, 1000, 100)
        battery.outer.layout(120, 0, 210, 48)
        battery.leaf.layout(120, 0, 199, 48)
        return Triple(signal, battery, signal.strip)
    }

    @Test
    fun `wifi and cell tints follow the leaf white black and yellow`() {
        val tree = oplusSignalTree(subId = 5)
        tree.wifiLeaf.imageTintList = ColorStateList.valueOf(Color.WHITE)
        tree.mobileLeaf.imageTintList = ColorStateList.valueOf(Color.WHITE)
        val host = host(cellSource = { oneSimReading(5) })

        assertTrue(host.attachIn(tree.strip, null))
        assertEquals(Color.WHITE, host.visualTintForTest(tree.wifiLeaf))
        assertEquals(Color.WHITE, host.visualTintForTest(tree.mobileLeaf))

        tree.wifiLeaf.imageTintList = ColorStateList.valueOf(Color.BLACK)
        tree.mobileLeaf.imageTintList = ColorStateList.valueOf(Color.BLACK)
        host.refresh()
        assertEquals(Color.BLACK, host.visualTintForTest(tree.wifiLeaf))
        assertEquals(Color.BLACK, host.visualTintForTest(tree.mobileLeaf))

        tree.wifiLeaf.imageTintList = ColorStateList.valueOf(Color.YELLOW)
        tree.mobileLeaf.imageTintList = ColorStateList.valueOf(Color.YELLOW)
        host.refreshTintForTest()
        assertEquals(Color.YELLOW, host.visualTintForTest(tree.wifiLeaf))
        assertEquals(Color.YELLOW, host.visualTintForTest(tree.mobileLeaf))
        host.teardown()
    }

    @Test
    fun `tint falls through to the leaf color filter and then the outer tint list`() {
        val tree = oplusSignalTree(subId = 5)
        tree.wifiLeaf.imageTintList = null
        tree.wifiLeaf.colorFilter = PorterDuffColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN)
        tree.mobileLeaf.imageTintList = null
        tree.mobileOuter.backgroundTintList = ColorStateList.valueOf(Color.MAGENTA)
        val host = host(cellSource = { oneSimReading(5) })

        assertTrue(host.attachIn(tree.strip, null))
        assertEquals(Color.CYAN, host.visualTintForTest(tree.wifiLeaf))
        assertEquals(Color.MAGENTA, host.visualTintForTest(tree.mobileLeaf))
        host.teardown()
    }

    @Test
    fun `battery wifi and cell share the system tint`() {
        val (signal, battery, _) = combinedSignalAndBatteryStrip()
        signal.wifiLeaf.imageTintList = ColorStateList.valueOf(Color.YELLOW)
        signal.mobileLeaf.imageTintList = ColorStateList.valueOf(Color.YELLOW)
        battery.leaf.imageTintList = ColorStateList.valueOf(Color.YELLOW)
        val host = host(cellSource = { oneSimReading(5) })

        assertTrue(host.attachIn(signal.strip, null))
        assertEquals(Color.YELLOW, host.visualTintForTest(battery.leaf))
        assertEquals(Color.YELLOW, host.visualTintForTest(signal.wifiLeaf))
        assertEquals(Color.YELLOW, host.visualTintForTest(signal.mobileLeaf))
        host.teardown()
    }

    // -------------------------------------------------------------- item 2: overlay BOTH one block

    @Test
    fun `an unreplaceable data slot draws one BOTH overlay at the data bounds and collapses the other`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        val battery = oplusBatteryTree()
        battery.strip.removeView(battery.outer)
        val data = SingleBindableStatusBarComposeIconView(context, "mobile_combo", 1)
        val other = SingleBindableStatusBarComposeIconView(context, "mobile_combo", 2)
        strip.addView(battery.outer, LinearLayout.LayoutParams(90, 48))
        strip.addView(data, LinearLayout.LayoutParams(20, 20))
        strip.addView(other, LinearLayout.LayoutParams(20, 20))
        root.addView(strip, FrameLayout.LayoutParams(200, 63))
        root.layout(0, 0, 1000, 100)
        strip.layout(0, 0, 200, 63)
        battery.outer.layout(0, 0, 90, 48)
        battery.leaf.layout(0, 0, 79, 48)
        data.layout(30, 0, 50, 20)
        other.layout(60, 0, 80, 20)

        val host = host(cellSource = { twoSimReading(1, 2) })
        assertTrue(host.attachIn(strip, root))

        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(data))
        assertNull(host.cellRoleForTest(other))
        assertEquals(View.GONE, other.visibility)
        assertEquals(0, other.layoutParams.width)
        assertEquals(0, other.layoutParams.height)

        val canvas = findCanvas(root)
        assertNotNull("one BOTH overlay is drawn", canvas)
        val params = canvas!!.layoutParams as FrameLayout.LayoutParams
        assertEquals("anchored to the DATA slot, not the union", 30, params.leftMargin)
        assertEquals(0, params.topMargin)
        assertFalse(containsCanvasOverOther(root, other))

        host.teardown()
        assertEquals(View.VISIBLE, other.visibility)
        assertEquals(20, other.layoutParams.width)
        assertEquals(View.VISIBLE, data.visibility)
    }

    // ----------------------------------------------------- item 8-runtime: hide list in iOS mode

    @Test
    fun `a selected hidden battery is GONE with no battery record and restores on teardown`() {
        val (signal, battery, strip) = combinedSignalAndBatteryStrip()
        val key = StockIconKey.keyOf(battery.outer)
        val params = battery.outer.layoutParams
        val originalWidth = params.width
        val originalHeight = params.height
        val host = host(cellSource = { oneSimReading(5) })
        host.refreshSettings(ModuleSettings.DEFAULT.copy(hiddenStockIcons = key))

        assertTrue(host.attachIn(strip, null))
        assertEquals(View.GONE, battery.outer.visibility)
        assertEquals(0, battery.outer.layoutParams.width)
        assertEquals(0, battery.outer.layoutParams.height)
        assertFalse(host.recordKindsForTest().contains(IosIconKind.BATTERY))
        // The other replacements still attach.
        assertTrue(signal.wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(signal.mobileLeaf.drawable is IosCellDrawable)

        host.teardown()
        assertEquals(View.VISIBLE, battery.outer.visibility)
        assertEquals(originalWidth, battery.outer.layoutParams.width)
        assertEquals(originalHeight, battery.outer.layoutParams.height)
    }

    @Test
    fun `a selected hidden wifi keeps the cell and battery replacements`() {
        val (signal, battery, strip) = combinedSignalAndBatteryStrip()
        val key = StockIconKey.keyOf(signal.wifiOuter)
        val host = host(cellSource = { oneSimReading(5) })
        host.refreshSettings(ModuleSettings.DEFAULT.copy(hiddenStockIcons = key))

        assertTrue(host.attachIn(strip, null))
        assertEquals(View.GONE, signal.wifiOuter.visibility)
        assertEquals(0, signal.wifiOuter.layoutParams.width)
        assertFalse(host.recordKindsForTest().contains(IosIconKind.WIFI))
        assertTrue(host.recordKindsForTest().contains(IosIconKind.BATTERY))
        assertTrue(host.recordKindsForTest().contains(IosIconKind.CELL))
        assertTrue(signal.mobileLeaf.drawable is IosCellDrawable)
        assertTrue(battery.leaf.drawable is IosBatteryDrawable)

        host.teardown()
        assertEquals(View.VISIBLE, signal.wifiOuter.visibility)
    }

    @Test
    fun `hide other icons hides an extra alarm view but never the adopted icons`() {
        val (signal, battery, strip) = combinedSignalAndBatteryStrip()
        val alarm = NamedView(context, "alarm")
        strip.addView(alarm, LinearLayout.LayoutParams(20, 20))
        alarm.layout(210, 0, 230, 20)
        val host = host(cellSource = { oneSimReading(5) })
        host.refreshSettings(ModuleSettings.DEFAULT.copy(hideOtherIcons = true))

        assertTrue(host.attachIn(strip, null))
        assertEquals(View.GONE, alarm.visibility)
        assertEquals(0, alarm.layoutParams.width)
        assertEquals(View.VISIBLE, signal.wifiOuter.visibility)
        assertEquals(View.VISIBLE, signal.mobileOuter.visibility)
        assertEquals(View.VISIBLE, battery.outer.visibility)
        assertTrue(signal.wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(signal.mobileLeaf.drawable is IosCellDrawable)
        assertTrue(
            "kinds=${host.recordKindsForTest()} batteryVisibility=${battery.outer.visibility} " +
                "batteryW=${battery.outer.width} leafW=${battery.leaf.width} " +
                "drawable=${battery.leaf.drawable}",
            battery.leaf.drawable is IosBatteryDrawable
        )

        host.teardown()
        assertEquals(View.VISIBLE, alarm.visibility)
        assertEquals(20, alarm.layoutParams.width)
    }

    @Test
    fun `changing the hidden list back to visible restores the stock icon and re-adopts it`() {
        val (signal, _, strip) = combinedSignalAndBatteryStrip()
        val key = StockIconKey.keyOf(signal.wifiOuter)
        val host = host(cellSource = { oneSimReading(5) })
        host.refreshSettings(ModuleSettings.DEFAULT.copy(hiddenStockIcons = key))

        assertTrue(host.attachIn(strip, null))
        assertEquals(View.GONE, signal.wifiOuter.visibility)
        assertFalse(host.recordKindsForTest().contains(IosIconKind.WIFI))

        host.refreshSettings(ModuleSettings.DEFAULT)
        assertEquals(View.VISIBLE, signal.wifiOuter.visibility)
        assertTrue(signal.wifiLeaf.drawable is IosWifiDrawable)
        assertTrue(host.recordKindsForTest().contains(IosIconKind.WIFI))
        host.teardown()
    }

    // ------------------------------------------------------- user transform host seam (R2C wire)

    @Test
    fun `host maps the stored offset and scale at the glyph drawn width`() {
        val host = host()
        host.refreshSettings(
            ModuleSettings.DEFAULT.copy(iosBatteryOffset = 10, iosBatteryScale = 150)
        )
        val transformed = host.userTransformFor(IosIconKind.BATTERY, 100f)
        assertEquals(10f, transformed.dxPx, 0.0001f)
        assertEquals(1.5f, transformed.scale, 0.0001f)

        host.refreshSettings(ModuleSettings.DEFAULT)
        assertEquals(IosUserTransform.IDENTITY, host.userTransformFor(IosIconKind.BATTERY, 100f))
    }

    @Test
    fun `adopted drawable transform uses the leaf draw box and does not jump on refresh`() {
        val tree = oplusBatteryTree()
        val host = host()
        host.refreshSettings(
            ModuleSettings.DEFAULT.copy(iosBatteryOffset = 10, iosBatteryScale = 100)
        )
        assertTrue(host.attachIn(tree.strip, null))

        val leafWidth = IosGeometry.Relative.sizeFor(IosIconKind.BATTERY, 79, 48).first
        val outerWidth = IosGeometry.Relative.sizeFor(IosIconKind.BATTERY, 90, 48).first
        assertTrue("the fixture must make the two boxes differ", leafWidth != outerWidth)

        val drawable = tree.leaf.drawable as IosBatteryDrawable
        val adopted = drawable.userTransform
        // The offset is a percent of the LEAF's 100 % draw width (79 px), never the 90 px outer.
        assertEquals(0.10f * leafWidth, adopted.dxPx, 0.01f)

        host.refresh()

        assertSame("the drawable instance is reused", drawable, tree.leaf.drawable)
        assertEquals("refresh must not move the glyph", adopted, drawable.userTransform)

        host.teardown()
    }

    // ------------------------------------------------------------- sim display: forced single

    @Test
    fun `forced single collapses the other slot and auto restores it exactly`() {
        val tree = dualMobileTree(firstSub = 1, secondSub = 2)
        val otherParams = tree.second.layoutParams
        val otherWidth = otherParams.width
        val otherHeight = otherParams.height
        var reading = twoSimReading(1, 2)
        val host = host(cellSource = { reading })
        host.refreshSettings(ModuleSettings.DEFAULT.copy(simDisplay = "single"))

        assertTrue(host.attachIn(tree.strip, null))
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.DATA, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0, tree.second.layoutParams.width)
        assertEquals(0, tree.second.layoutParams.height)

        // Back to AUTO: with one active SIM the plan is a plain single row and the hidden slot returns.
        host.refreshSettings(ModuleSettings.DEFAULT.copy(simDisplay = "auto"))
        reading = oneSimReading(1, 3)
        host.refresh()
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.DATA, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertEquals(View.VISIBLE, tree.second.visibility)
        assertEquals(otherWidth, tree.second.layoutParams.width)
        assertEquals(otherHeight, tree.second.layoutParams.height)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)

        host.teardown()
    }

    private class Snapshot(
        val layoutParams: ViewGroup.LayoutParams?,
        val width: Int,
        val height: Int,
        val visibility: Int
    )

    // ------------------------------------------------------------------------ helpers

    private fun render(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun ink(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) ushr 24 != 0) count++
            }
        }
        return count
    }

    /** Inclusive ink bounding box as `[minX, minY, maxX, maxY]`; empty becomes an inverted box. */
    private fun inkBounds(bitmap: Bitmap): IntArray {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) ushr 24 != 0) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return intArrayOf(minX, minY, maxX, maxY)
    }

    private fun containsCanvasView(view: View): Boolean {
        if (view is IosCanvasView) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsCanvasView(view.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun findCanvas(view: View): IosCanvasView? {
        if (view is IosCanvasView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findCanvas(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun containsCanvasOverOther(root: View, other: View): Boolean =
        findCanvas(other) != null

    private fun hasRiveView(view: View): Boolean {
        if (view.javaClass.simpleName.contains("Rive", ignoreCase = true)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasRiveView(view.getChildAt(i))) return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------------ R3 A2/A4 additions

    /** The visible QS/shade strip: carrier-group rows with subId 0, then the two mapped SIM views. */
    private class QsCarrierTree(
        val tree: DualMobileTree,
        val carriers: List<View>
    )

    private fun qsCarrierTree(): QsCarrierTree {
        val tree = dualMobileTree(firstSub = 1, secondSub = 2)
        val carriers = ArrayList<View>()
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
        carriers.forEachIndexed { index, carrier ->
            carrier.layout(60 + index * 10, 0, 70 + index * 10, 20)
        }
        return QsCarrierTree(tree, carriers)
    }

    @Test
    fun `a QS strip with subId-0 carrier rows still adopts the two mapped SIMs as one BOTH block`() {
        val qs = qsCarrierTree()
        val tree = qs.tree
        val otherParams = tree.second.layoutParams
        val otherWidth = otherParams.width
        val otherHeight = otherParams.height
        val host = host(cellSource = { twoSimReading(1, 2) })

        assertTrue(host.attachIn(tree.strip, null))

        // A2: the 12 unreadable carrier rows are not part of the plan, so the two SIM slots map.
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.BOTH, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertEquals(View.GONE, tree.second.visibility)
        assertEquals(0, tree.second.layoutParams.width)
        assertTrue("carrier rows stay stock", qs.carriers.all { it.visibility == View.VISIBLE })

        host.teardown()
        assertSame(tree.firstDrawable, tree.firstLeaf.drawable)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)
        assertEquals(View.VISIBLE, tree.first.visibility)
        assertEquals(View.VISIBLE, tree.second.visibility)
        assertEquals(otherWidth, tree.second.layoutParams.width)
        assertEquals(otherHeight, tree.second.layoutParams.height)
        assertTrue("carrier rows are untouched on teardown", qs.carriers.all { it.visibility == View.VISIBLE })
    }

    @Test
    fun `a cell source change from 1 to 3 updates the same record on refresh`() {
        var reading = oneSimReading(5, dots = 1)
        val tree = oplusSignalTree(subId = 5)
        val host = host(cellSource = { reading })
        assertTrue(host.attachIn(tree.strip, null))

        // R4A item 2: the plan now draws the sample's iOS level. raw 1 of 4 -> 2 dots.
        assertEquals(2f, host.visualForTest(tree.mobileLeaf)?.cellBars ?: -1f)
        reading = oneSimReading(5, dots = 3)
        host.refresh()

        // raw 3 of 4 is the top bar -> 4 iOS dots (the old Duo table drew 3).
        assertEquals(4f, host.visualForTest(tree.mobileLeaf)?.cellBars ?: -1f)
        assertEquals(2, host.recordCountForTest())
        host.teardown()
    }

    /** A host that uses the process-wide [IosReadings] sources, like the runtime does. */
    private fun readingsHost(surface: String): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        sizeOwner = IosSizeOwner(),
        isMainBar = false,
        surface = surface
    )

    @Test
    fun `two surfaces draw the same Wi-Fi and cell snapshot`() {
        IosReadings.resetForTest(
            wifi = { _, _, _, _, _ -> wifiSample(validated = true, dots = 2) },
            cell = { oneSimReading(5, dots = 3) }
        )
        val firstTree = oplusSignalTree(subId = 5)
        val secondTree = oplusSignalTree(subId = 5)
        val first = readingsHost("readings one")
        val second = readingsHost("readings two")

        assertTrue(first.attachIn(firstTree.strip, null))
        assertTrue(second.attachIn(secondTree.strip, null))

        val firstWifi = first.lastWifiSampleForTest()
        val secondWifi = second.lastWifiSampleForTest()
        val firstCell = first.lastCellReadingForTest()
        val secondCell = second.lastCellReadingForTest()
        assertNotNull(firstWifi)
        assertNotNull(firstCell)
        assertSame("one Wi-Fi sample for every surface", firstWifi, secondWifi)
        assertSame("one cell reading for every surface", firstCell, secondCell)

        first.teardown()
        second.teardown()
    }

    @Test
    fun `repeated failed attachSurface calls keep the attach attempts bounded`() {
        val host = host()
        val root = FrameLayout(context)
        root.layout(0, 0, 100, 100)

        repeat(20) { host.attachSurface("missing", root, "system_icons") }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))

        assertTrue(
            "the retry budget never runs away: ${host.attachAttemptsForTest()}",
            host.attachAttemptsForTest() <= 4
        )
        assertFalse(host.attached)
    }

    @Test
    fun `an extra host receives the same settings as the main host`() {
        val main = host()
        val extra = host(isMainBar = false, surface = "shade header")
        val before = extra.userTransformFor(IosIconKind.BATTERY, 79f)
        val settings = ModuleSettings.DEFAULT.copy(
            iosBatteryScale = ModuleSettings.DEFAULT.iosBatteryScale + 20
        )

        refreshIosHostSettings(settings, listOf(main, extra))

        val after = extra.userTransformFor(IosIconKind.BATTERY, 79f)
        assertNotEquals(before, after)
        assertEquals(settings.iosTransform(IosIconKind.BATTERY, 79f), after)
    }

    @Test
    fun `percent inside reaches the live battery visual and defaults off`() {
        val tree = oplusBatteryTree()
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        // Default OFF keeps today's battery: the visual carries no inside percentage.
        assertEquals(false, host.visualForTest(tree.leaf)?.percentInside)

        host.refreshSettings(ModuleSettings.DEFAULT.copy(iosPercentInside = true))

        assertEquals(true, host.visualForTest(tree.leaf)?.percentInside)

        host.refreshSettings(ModuleSettings.DEFAULT)

        assertEquals(
            "turning it back off restores today's battery",
            false,
            host.visualForTest(tree.leaf)?.percentInside
        )
        host.teardown()
    }

    @Test
    fun `an extra host refreshed through the shared settings gets percent inside`() {
        val mainTree = oplusBatteryTree()
        val extraTree = oplusBatteryTree()
        val main = host()
        val extra = host(isMainBar = false, surface = "shade header")
        assertTrue(main.attachIn(mainTree.strip, null))
        assertTrue(extra.attachIn(extraTree.strip, null))
        assertEquals(false, extra.visualForTest(extraTree.leaf)?.percentInside)

        refreshIosHostSettings(
            ModuleSettings.DEFAULT.copy(iosPercentInside = true),
            listOf(main, extra)
        )

        assertEquals(true, main.visualForTest(mainTree.leaf)?.percentInside)
        assertEquals(true, extra.visualForTest(extraTree.leaf)?.percentInside)
        main.teardown()
        extra.teardown()
    }

    private companion object {
        val NEXT_NAMED_ID = AtomicInteger(0x7f0f0000.toInt())
    }
}
