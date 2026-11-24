package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * R3 A3: the live order swap on a `StatusIconContainer`-shaped LinearLayout.
 *
 * The tests pin what the owner cares about: the same view instances move (identity, visibility and
 * drawable untouched), a GONE mobile still counts as mobile, every container is handled, restore
 * returns the exact stock order, and nothing changes while iOS is off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosIconOrderRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A stock icon view that reports its slot like `StatusBarIconView.getSlot()`. */
    class FakeIcon(context: Context, private val slot: String) : View(context) {
        fun getSlot(): String = slot
    }

    /** Test-local base with the real cast target's simple name. */
    open class StatusBarIconView(context: Context) : View(context)

    /** A real icon subclass hidden behind a different own class name. */
    class FakeStatusBarIconSubclass(context: Context) : StatusBarIconView(context)

    /** The measured container shape, plus the optional animation switch the hook suppresses. */
    open class FakeContainer(context: Context) : LinearLayout(context) {
        @JvmField
        var mEnableAnimation = true
        val animationStates = ArrayList<Boolean>()

        fun setEnableAnimation(enabled: Boolean) {
            animationStates.add(enabled)
            mEnableAnimation = enabled
        }
    }

    /**
     * The production layout hook shape: the container's own `onLayout` calls the production path,
     * exactly like the `afterHookedMethod` callback. The order is captured immediately after that
     * call, so a synchronous remove/add from inside `onLayout` is visible.
     */
    class LayoutHookContainer(context: Context) : LinearLayout(context) {
        var observedDuringLayout: List<View>? = null
        var layoutCalls = 0

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            IosIconOrder.onContainerLayoutForTest(this)
            observedDuringLayout = (0 until childCount).map { getChildAt(it) }
            layoutCalls++
        }
    }

    /**
     * A container that rejects the stock-order Wi-Fi re-insert once (the restore target index) and
     * accepts the fallback re-insert and any later retry.
     */
    class FailingRestoreContainer(context: Context) : FakeContainer(context) {
        var rejectedStockMove = false

        override fun addView(child: View?, index: Int) {
            if (!rejectedStockMove && index == 0 && child != null &&
                StockIconKey.slotTagOf(child) == "wifi"
            ) {
                rejectedStockMove = true
                throw IllegalStateException("rejects the stock restore move")
            }
            super.addView(child, index)
        }
    }

    /** A view with no slot method, resolved through the `wifi_combo`/`mobile_combo` id fallback. */
    class IdIcon(context: Context, entry: String) : View(context) {
        private val fake: Resources

        init {
            id = NEXT_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake
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
        val NEXT_ID = AtomicInteger(10_000)
    }

    @Before
    fun setUp() {
        IosIconOrder.clearForTest()
        IosIconOrder.markInstalledForTest()
    }

    @After
    fun tearDown() = IosIconOrder.clearForTest()

    private fun order(container: ViewGroup): List<View> =
        (0 until container.childCount).map { container.getChildAt(it) }

    /** Runs the posted main-thread passes, where the layout path schedules its move. */
    private fun flushMainThread() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun layout(container: View, width: Int = 300, height: Int = 60) {
        container.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, width, height)
    }

    private fun assertOrder(expected: List<View>, actual: List<View>) {
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, view -> assertSame(view, actual[index]) }
    }

    private fun stockContainer(
        container: FakeContainer = FakeContainer(context)
    ): FakeContainer {
        container.addView(FakeIcon(context, "wifi"))
        container.addView(FakeIcon(context, "mobile"))
        container.addView(FakeIcon(context, "mobile"))
        container.addView(FakeIcon(context, "battery"))
        container.addView(FakeIcon(context, "dynamic_info"))
        return container
    }

    @Test
    fun `activate swaps mobile then wifi on the same instances and leaves the rest alone`() {
        val slots = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val container = stockContainer()
        val before = order(container)
        val wifi = before[0]
        val mobile = before[1]
        val goneMobile = before[2]
        val battery = before[3]
        val info = before[4]
        val wifiDrawable = ColorDrawable(Color.RED)
        val mobileDrawable = ColorDrawable(Color.BLUE)
        wifi.background = wifiDrawable
        mobile.background = mobileDrawable
        goneMobile.visibility = View.GONE

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()

        assertOrder(listOf(mobile, goneMobile, wifi, battery, info), order(container))
        assertEquals(before.size, container.childCount)
        assertSame(wifiDrawable, wifi.background)
        assertSame(mobileDrawable, mobile.background)
        assertEquals(View.GONE, goneMobile.visibility)
        assertEquals(listOf("mobile", "wifi", "battery"), slots)
    }

    @Test
    fun `restore puts wifi before the first mobile and returns the captured slot order`() {
        val slots = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()
        IosIconOrder.restore()
        flushMainThread()

        assertOrder(stockOrder, order(container))
        assertEquals(listOf("wifi", "mobile", "battery"), slots)
    }

    @Test
    fun `a failed restore move keeps the iOS order and the guard active until the retry`() {
        val slots = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val container = stockContainer(FailingRestoreContainer(context))
        val stockOrder = order(container)
        val iosOrder = listOf(stockOrder[1], stockOrder[2], stockOrder[0], stockOrder[3], stockOrder[4])

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()
        assertOrder(iosOrder, order(container))
        assertEquals(listOf("mobile", "wifi", "battery"), slots)

        // The stock-order re-insert is rejected once. Restore must not clear the guard or the
        // permuted slot list, and the child order must stay the consistent iOS order.
        IosIconOrder.restore()
        flushMainThread()

        assertTrue("the restore failed, so iOS still owns the bar", IosIconOrder.isActive)
        assertEquals("the slot list must stay permuted", listOf("mobile", "wifi", "battery"), slots)
        assertOrder(iosOrder, order(container))

        // The container accepts the move now; the next restore must complete.
        IosIconOrder.restore()
        flushMainThread()

        assertFalse(IosIconOrder.isActive)
        assertOrder(stockOrder, order(container))
        assertEquals(listOf("wifi", "mobile", "battery"), slots)
    }

    @Test
    fun `a GONE mobile still counts as the last mobile child`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val container = FakeContainer(context)
        val wifi = FakeIcon(context, "wifi")
        val visible = FakeIcon(context, "mobile")
        val gone = FakeIcon(context, "mobile")
        val battery = FakeIcon(context, "battery")
        gone.visibility = View.GONE
        container.addView(wifi)
        container.addView(visible)
        container.addView(gone)
        container.addView(battery)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()

        assertOrder(listOf(visible, gone, wifi, battery), order(container))
    }

    @Test
    fun `every registered container is moved`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val home = stockContainer()
        val qs = stockContainer()
        val homeStock = order(home)
        val qsStock = order(qs)

        IosIconOrder.onIconAdded(home)
        IosIconOrder.onIconAdded(qs)
        IosIconOrder.activate()
        flushMainThread()

        assertOrder(listOf(homeStock[1], homeStock[2], homeStock[0], homeStock[3], homeStock[4]), order(home))
        assertOrder(listOf(qsStock[1], qsStock[2], qsStock[0], qsStock[3], qsStock[4]), order(qs))
    }

    @Test
    fun `activate without a captured slot list leaves the stock order alone`() {
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()

        assertOrder(stockOrder, order(container))
        assertTrue(IosIconOrder.isActive)
    }

    @Test
    fun `nothing moves while iOS is off`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.onIconAdded(container)
        flushMainThread()

        assertOrder(stockOrder, order(container))
        assertTrue(!IosIconOrder.isActive)
    }

    @Test
    fun `activation is idempotent and restores the animation switch`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val container = stockContainer()
        val stock = order(container)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()
        val passes = IosIconOrder.passesForTest()
        val moves = IosIconOrder.movesForTest()
        assertEquals(1, passes)

        IosIconOrder.activate()
        flushMainThread()

        assertOrder(listOf(stock[1], stock[2], stock[0], stock[3], stock[4]), order(container))
        assertEquals("a repeated activate must not schedule another pass", passes, IosIconOrder.passesForTest())
        assertEquals("a repeated activate must not move anything", moves, IosIconOrder.movesForTest())
        assertTrue(container.mEnableAnimation)
        assertTrue(container.animationStates.contains(false))
        assertEquals(true, container.animationStates.last())
    }

    @Test
    fun `repeated activate on an unchanged adopted container schedules no pass`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val container = stockContainer()
        val stock = order(container)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()

        assertEquals(1, IosIconOrder.passesForTest())
        assertEquals(1, IosIconOrder.movesForTest())
        assertOrder(listOf(stock[1], stock[2], stock[0], stock[3], stock[4]), order(container))

        IosIconOrder.resetCountersForTest()
        IosIconOrder.activate()
        flushMainThread()

        assertEquals(0, IosIconOrder.passesForTest())
        assertEquals(0, IosIconOrder.movesForTest())
        assertOrder(listOf(stock[1], stock[2], stock[0], stock[3], stock[4]), order(container))
    }

    @Test
    fun `a repeat activate recovers a container reordered without an addView`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val container = stockContainer()
        val stock = order(container)
        val iosOrder = listOf(stock[1], stock[2], stock[0], stock[3], stock[4])

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()
        assertEquals(1, IosIconOrder.passesForTest())
        assertEquals(1, IosIconOrder.movesForTest())
        assertOrder(iosOrder, order(container))

        // The ROM rebuilds the children in stock order without going through addView, so no icon
        // hook fires and no later layout follows. The slot list is unchanged.
        IosIconOrder.resetCountersForTest()
        container.removeView(stock[0])
        container.addView(stock[0], 0)
        assertOrder(stock, order(container))

        IosIconOrder.activate()
        flushMainThread()

        assertOrder(iosOrder, order(container))
        assertEquals("exactly one recovery pass", 1, IosIconOrder.passesForTest())
        assertEquals("exactly one recovery move", 1, IosIconOrder.movesForTest())

        // The recovered bar is correct now, so the next apply adds no work at all.
        IosIconOrder.resetCountersForTest()
        IosIconOrder.activate()
        flushMainThread()
        assertEquals(0, IosIconOrder.passesForTest())
        assertEquals(0, IosIconOrder.movesForTest())
        assertOrder(iosOrder, order(container))
    }

    @Test
    fun `release unregisters containers and a later install accepts them again`() {
        IosIconOrder.markInstalledForTest()
        val container = stockContainer()
        IosIconOrder.onIconAdded(container)
        assertEquals(1, IosIconOrder.containerCountForTest())

        IosIconOrder.release()

        assertFalse(IosIconOrder.isInstalledForTest())
        assertEquals(0, IosIconOrder.containerCountForTest())

        // A retained addView callback after release must not mutate the container registry.
        IosIconOrder.onIconAdded(container)
        assertEquals(0, IosIconOrder.containerCountForTest())

        // A later iOS session accepts the container and can move it again.
        val stock = order(container)
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        IosIconOrder.markInstalledForTest()
        IosIconOrder.onIconAdded(container)
        assertEquals(1, IosIconOrder.containerCountForTest())
        IosIconOrder.activate()
        flushMainThread()
        assertOrder(
            listOf(stock[1], stock[2], stock[0], stock[3], stock[4]),
            order(container)
        )
    }

    @Test
    fun `the wifi_combo and mobile_combo fallback identifies a view without a slot method`() {
        val wifi = IdIcon(context, "wifi_combo")
        val mobile = IdIcon(context, "mobile_combo")
        assertEquals("wifi", StockIconKey.slotTagOf(wifi))
        assertEquals("mobile", StockIconKey.slotTagOf(mobile))

        val container = FakeContainer(context)
        container.addView(wifi)
        container.addView(mobile)
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        flushMainThread()

        assertOrder(listOf(mobile, wifi), order(container))
    }

    @Test
    fun `the first layout adopts a container once and later layouts schedule no passes`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = stockContainer()
        activity.setContentView(container)
        val before = order(container)
        val expected = listOf(before[1], before[2], before[0], before[3], before[4])

        // No container is registered when activation happens; the first layout adopts it and
        // schedules exactly one pass.
        IosIconOrder.activate()
        IosIconOrder.onContainerLayoutForTest(container)
        flushMainThread()

        assertOrder(expected, order(container))
        assertEquals(1, IosIconOrder.containerCountForTest())
        assertEquals(1, IosIconOrder.passesForTest())
        assertEquals(1, IosIconOrder.movesForTest())
        val line = IosR8Report.diagnosticLines().single()
        assertTrue(line, line.contains("orderMoves=1"))
        assertTrue(line, line.contains("orderPasses=1"))

        // The proof: an already-adopted, already-ordered container does no work per layout.
        IosIconOrder.resetCountersForTest()
        repeat(25) {
            IosIconOrder.onContainerLayoutForTest(container)
            flushMainThread()
        }
        assertEquals(0, IosIconOrder.passesForTest())
        assertEquals(0, IosIconOrder.movesForTest())
        assertOrder(expected, order(container))
    }

    @Test
    fun `onLayout only registers and the posted pass reorders after layout returns`() {
        val slots = IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = LayoutHookContainer(activity)
        container.addView(FakeIcon(activity, "wifi"))
        container.addView(FakeIcon(activity, "mobile"))
        container.addView(FakeIcon(activity, "battery"))
        val stock = order(container)

        // No container is registered when activation happens; activation refuses and leaves the
        // slots in stock order. The layout callback is the only way the surface becomes known.
        IosIconOrder.activate()
        assertTrue(IosIconOrder.isActive)
        activity.setContentView(container)
        layout(container)

        // The layout callback must not remove/add a live child (AGENTS-V2 3.3 #14).
        val duringLayout = container.observedDuringLayout
        assertTrue("the container must have laid out", duringLayout != null)
        assertOrder(stock, duringLayout!!)
        assertOrder(stock, order(container))

        // Only the posted pass, after layout returned, may move Wi-Fi.
        flushMainThread()
        assertOrder(listOf(stock[1], stock[0], stock[2]), order(container))
        assertEquals(listOf("mobile", "wifi", "battery"), slots)
    }

    @Test
    fun `repeated layouts while a pass is pending queue one pass`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = LayoutHookContainer(activity)
        container.addView(FakeIcon(activity, "wifi"))
        container.addView(FakeIcon(activity, "mobile"))
        container.addView(FakeIcon(activity, "battery"))

        IosIconOrder.activate()
        activity.setContentView(container)
        layout(container)
        layout(container, width = 320)
        layout(container, width = 340)
        flushMainThread()

        assertTrue("the container must have laid out repeatedly", container.layoutCalls >= 2)
        assertEquals("one coalesced pass", 1, IosIconOrder.passesForTest())
        assertEquals(1, IosIconOrder.movesForTest())
    }

    @Test
    fun `the constructor sighting registers without scheduling`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.activate()
        IosIconOrder.onContainerSeenForTest(container)
        flushMainThread()

        assertEquals(1, IosIconOrder.containerCountForTest())
        assertEquals(0, IosIconOrder.passesForTest())
        assertEquals(0, IosIconOrder.movesForTest())
        assertOrder(stockOrder, order(container))
    }

    /** A live `StatusBarIconList.Slot` stand-in: the name is read through `getName()`. */
    class NamedSlot(private val name: String) {
        fun getName(): String = name
        override fun toString(): String = "Slot($name)"
    }

    /** The OPLUS Wi-Fi composite: a real child that is not a `StatusBarIconView`. */
    class WifiCompositeView(context: Context) : View(context) {
        fun getSlot(): String = "wifi"
    }

    /** A plain status icon child: the class `IconManager` is allowed to cast. */
    class PlainIconView(context: Context, private val slot: String) : View(context) {
        fun getSlot(): String = slot
    }

    private fun namedSlots(vararg names: String): MutableList<Any> =
        IosIconOrder.seedSlotEntriesForTest(names.map { NamedSlot(it) })

    private fun namedContainer(names: List<String>): FakeContainer {
        val container = FakeContainer(context)
        names.forEach { name ->
            if (name == "wifi") container.addView(WifiCompositeView(context))
            else container.addView(PlainIconView(context, name))
        }
        return container
    }

    private fun slotNames(slots: List<Any>): List<String> =
        slots.map { (it as NamedSlot).getName() }

    private fun childTags(container: ViewGroup): List<String?> =
        (0 until container.childCount).map { StockIconKey.slotTagOf(container.getChildAt(it)) }

    /** Every child tag must line up with the same-named slot, in the live slot-list order. */
    private fun assertChildrenMatchSlots(container: ViewGroup, slots: List<Any>, case: String) {
        val tags = childTags(container)
        val expected = slotNames(slots).filter { it in tags }
        assertEquals("child order must follow the slot list: $case", expected, tags)
    }

    /** Emulates `IconManager`: the slot name gives the index, the index gives the cast target. */
    private fun iconManagerChild(container: ViewGroup, slots: List<Any>, slot: String): View {
        val index = slotNames(slots).indexOf(slot)
        assertTrue("slot $slot must be in the list", index >= 0)
        return container.getChildAt(index)
    }

    @Test
    fun `activation moves the wifi composite before the slot list so IconManager never casts it`() {
        val slots = namedSlots("wifi", "mobile", "mobile", "battery")
        val container = namedContainer(slotNames(slots))
        IosIconOrder.onIconAdded(container)

        IosIconOrder.activate()
        // Deliberately no looper idle / flush: this is the main-thread state IconManager sees.
        val mobileChild = iconManagerChild(container, slots, "mobile")
        assertTrue(
            "IconManager would cast ${mobileChild.javaClass.simpleName}",
            mobileChild is PlainIconView
        )
        assertChildrenMatchSlots(container, slots, "first activation")
    }

    @Test
    fun `child order follows every add and remove around wifi across repeated activate and restore`() {
        val slots = namedSlots("battery", "wifi", "mobile")
        val container = namedContainer(slotNames(slots))
        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        assertChildrenMatchSlots(container, slots, "initial activation")

        // A plain slot added before Wi-Fi.
        slots.add(1, NamedSlot("alarm"))
        container.addView(PlainIconView(context, "alarm"), 1)
        IosIconOrder.onIconAdded(container)
        assertChildrenMatchSlots(container, slots, "add before wifi")

        // A plain slot added after Wi-Fi (and after the last mobile).
        slots.add(NamedSlot("vpn"))
        container.addView(PlainIconView(context, "vpn"))
        IosIconOrder.onIconAdded(container)
        assertChildrenMatchSlots(container, slots, "add after wifi")

        // A second activation is a no-op for order but must keep the invariant.
        IosIconOrder.activate()
        assertChildrenMatchSlots(container, slots, "second activation")

        // Remove the plain slot before Wi-Fi.
        val alarmIndex = slotNames(slots).indexOf("alarm")
        slots.removeAt(alarmIndex)
        container.removeViewAt(alarmIndex)
        IosIconOrder.onIconAdded(container)
        assertChildrenMatchSlots(container, slots, "remove before wifi")

        // Remove the plain slot after Wi-Fi.
        val vpnIndex = slotNames(slots).indexOf("vpn")
        slots.removeAt(vpnIndex)
        container.removeViewAt(vpnIndex)
        IosIconOrder.onIconAdded(container)
        assertChildrenMatchSlots(container, slots, "remove after wifi")

        IosIconOrder.activate()
        assertChildrenMatchSlots(container, slots, "third activation")

        IosIconOrder.restore()
        assertEquals(listOf("battery", "wifi", "mobile"), slotNames(slots))
        assertEquals(listOf("battery", "wifi", "mobile"), childTags(container))
    }

    @Test
    fun `the IconManager guard accepts a subclass of a StatusBarIconView base class`() {
        assertTrue(IosIconOrder.isIconManagerChildClass(FakeStatusBarIconSubclass(context)))
        assertTrue(IosIconOrder.isIconManagerChildClass(FakeStatusBarIconSubclass(context).javaClass))
        assertFalse(IosIconOrder.isIconManagerChildClass(FakeIcon(context, "wifi")))
        assertFalse(IosIconOrder.isIconManagerChildClass(WifiCompositeView(context)))
    }
}
