package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import org.robolectric.RobolectricTestRunner
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

    /** The measured container shape, plus the optional animation switch the hook suppresses. */
    class FakeContainer(context: Context) : LinearLayout(context) {
        @JvmField
        var mEnableAnimation = true
        val animationStates = ArrayList<Boolean>()

        fun setEnableAnimation(enabled: Boolean) {
            animationStates.add(enabled)
            mEnableAnimation = enabled
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
        IosIconOrder.flushForTest()

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
        IosIconOrder.flushForTest()
        IosIconOrder.restore()
        IosIconOrder.flushForTest()

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
        IosIconOrder.flushForTest()

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
        IosIconOrder.flushForTest()

        assertOrder(listOf(homeStock[1], homeStock[2], homeStock[0], homeStock[3], homeStock[4]), order(home))
        assertOrder(listOf(qsStock[1], qsStock[2], qsStock[0], qsStock[3], qsStock[4]), order(qs))
    }

    @Test
    fun `activate without a captured slot list leaves the stock order alone`() {
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.activate()
        IosIconOrder.flushForTest()

        assertOrder(stockOrder, order(container))
        assertTrue(IosIconOrder.isActive)
    }

    @Test
    fun `nothing moves while iOS is off`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile"))
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.onIconAdded(container)
        IosIconOrder.flushForTest()

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
        IosIconOrder.activate()
        IosIconOrder.flushForTest()
        IosIconOrder.flushForTest()

        assertOrder(listOf(stock[1], stock[2], stock[0], stock[3], stock[4]), order(container))
        assertTrue(container.mEnableAnimation)
        assertTrue(container.animationStates.contains(false))
        assertEquals(true, container.animationStates.last())
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
        IosIconOrder.flushForTest()
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
        IosIconOrder.flushForTest()

        assertOrder(listOf(mobile, wifi), order(container))
    }

    @Test
    fun `the first layout adopts a container once and later layouts schedule no passes`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val container = stockContainer()
        val before = order(container)
        val expected = listOf(before[1], before[2], before[0], before[3], before[4])

        // No container is registered when activation happens; the first layout adopts it and
        // schedules exactly one pass.
        IosIconOrder.activate()
        IosIconOrder.onContainerLayoutForTest(container)
        IosIconOrder.flushForTest()

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
            IosIconOrder.flushForTest()
        }
        assertEquals(0, IosIconOrder.passesForTest())
        assertEquals(0, IosIconOrder.movesForTest())
        assertOrder(expected, order(container))
    }

    @Test
    fun `the constructor sighting registers without scheduling`() {
        IosIconOrder.seedSlotListForTest(listOf("wifi", "mobile", "battery"))
        val container = stockContainer()
        val stockOrder = order(container)

        IosIconOrder.activate()
        IosIconOrder.onContainerSeenForTest(container)
        IosIconOrder.flushForTest()

        assertEquals(1, IosIconOrder.containerCountForTest())
        assertEquals(0, IosIconOrder.passesForTest())
        assertEquals(0, IosIconOrder.movesForTest())
        assertOrder(stockOrder, order(container))
    }
}
