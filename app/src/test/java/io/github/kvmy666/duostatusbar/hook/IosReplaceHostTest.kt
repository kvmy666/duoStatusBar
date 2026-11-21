package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.junit.Assert.assertArrayEquals
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
    }

    @After
    fun tearDown() {
        IosDrawingHooks.clearForTest()
        IosDrawHookInstaller.releaseWhenEmpty()
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

    private open class NamedGroup(context: Context, entry: String) : FrameLayout(context) {
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

    /** An opaque Compose/AOSP-style icon view: the skin refuses it, so its stock drawing must stay. */
    private class StatIconView(context: Context, entry: String) : NamedView(context, entry)

    // ------------------------------------------------------------------------ host + readings

    private fun host(
        sizeOwner: IosSizeOwner = IosSizeOwner(),
        isMainBar: Boolean = true,
        batterySource: (Context) -> IosBatteryReading? = { IosBatteryReading(50, false, 0) },
        cellSource: (Context) -> CellReading = { noCellReading() },
        wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
            { _, _, _, _, _ -> wifiSample(validated = true) }
    ): IosReplaceHost = IosReplaceHost(
        context = context,
        classLoader = null,
        sizeOwner = sizeOwner,
        isMainBar = isMainBar,
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
    fun `each cell record follows its own leaf tint on refresh`() {
        val tree = dualMobileTree(firstSub = 1, secondSub = 2)
        tree.firstLeaf.imageTintList = ColorStateList.valueOf(Color.RED)
        tree.secondLeaf.imageTintList = ColorStateList.valueOf(Color.BLUE)
        val host = host(cellSource = { twoSimReading(1, 2) })

        assertTrue(host.attachIn(tree.strip, null))
        host.refresh()

        assertEquals(Color.RED, host.visualTintForTest(tree.firstLeaf))
        assertEquals(Color.BLUE, host.visualTintForTest(tree.secondLeaf))
        host.teardown()
    }

    @Test
    fun `dual SIM goes two records one record two records without hiding a slot`() {
        val tree = dualMobileTree(firstSub = 1, secondSub = 2)
        val host = host(cellSource = { twoSimReading(1, 2) })

        assertTrue(host.attachIn(tree.strip, null))
        assertEquals(2, host.recordCountForTest())
        assertEquals(IosCellRole.DATA, host.cellRoleForTest(tree.first))
        assertEquals(IosCellRole.OTHER, host.cellRoleForTest(tree.second))
        assertEquals(View.VISIBLE, tree.first.visibility)
        assertEquals(View.VISIBLE, tree.second.visibility)

        // One slot goes away: one Single record over the remaining slot.
        tree.second.visibility = View.GONE
        host.refresh()
        assertEquals(1, host.recordCountForTest())
        assertEquals(IosCellRole.DATA, host.cellRoleForTest(tree.first))
        assertNull(host.cellRoleForTest(tree.second))
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)

        // It returns: the Dual rows are recreated.
        tree.second.visibility = View.VISIBLE
        host.refresh()
        assertEquals(2, host.recordCountForTest())
        assertEquals(IosCellRole.OTHER, host.cellRoleForTest(tree.second))

        host.teardown()
        assertSame(tree.firstDrawable, tree.firstLeaf.drawable)
        assertSame(tree.secondDrawable, tree.secondLeaf.drawable)
    }

    @Test
    fun `a fight of re-asserts trips at eight real replacements and restores the stock drawing`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val tree = oplusBatteryTree()
        activity.setContentView(tree.root)
        val host = host()
        assertTrue(host.attachIn(tree.strip, null))

        // A no-op re-assert (ours is already installed) never counts.
        repeat(20) { host.refresh() }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(host.isThrashTrippedForTest())

        // Four real OEM replacements are under the eight-event budget.
        repeat(4) {
            tree.leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            host.refresh()
            assertTrue("the swap puts ours back", tree.leaf.drawable is IosBatteryDrawable)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("four replacements must not trip the guard", host.isThrashTrippedForTest())

        // Four more (eight total) trip it and restore the stock drawing.
        repeat(4) {
            tree.leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            host.refresh()
            assertTrue("the swap puts ours back", tree.leaf.drawable is IosBatteryDrawable)
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
        assertTrue(host.attachIn(tree.strip, null))
        assertTrue(tree.leaf.drawable is IosBatteryDrawable)
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

    private fun hasRiveView(view: View): Boolean {
        if (view.javaClass.simpleName.contains("Rive", ignoreCase = true)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasRiveView(view.getChildAt(i))) return true
            }
        }
        return false
    }

    private companion object {
        val NEXT_NAMED_ID = AtomicInteger(0x7f0f0000.toInt())
    }
}
