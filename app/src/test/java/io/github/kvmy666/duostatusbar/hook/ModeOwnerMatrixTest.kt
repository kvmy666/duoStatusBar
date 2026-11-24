package io.github.kvmy666.duostatusbar.hook

import android.content.Context
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
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * R12 Step 1 evidence: the owner matrix and its component teardown.
 *
 * `OFF -> DUO -> IOS -> DUO -> OFF` must leave no cross-mode effect: the iOS components stay inert
 * while Duo owns the bar, and after iOS is switched off every view value (slot order, child order,
 * LayoutParams instance/values, scale type, clip flags, drawables, visibility, sizes) is exactly the
 * value captured before iOS was switched on.
 *
 * The production seams are the ones the existing iOS tests use: [IosIconOrder]'s slot-list/container
 * seams, [IosSlotLayout]'s registry seams, [IosStackedSim.reconcileViews], and [IosExtras]'s
 * registered leaves. Nothing here needs Xposed or a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModeOwnerMatrixTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // --------------------------------------------------------------------------- owner snapshots

    /**
     * All optional fields are quiet except the two that must stay fixed across the mode switch:
     * `showPercent` and `showCellDots`. `designStyle`/`engineMode` are pinned too, so the "mode
     * switches never mutate Duo fields" assertion is about real values, not defaults that happen to
     * match.
     */
    private val base = ModuleSettings.DEFAULT.copy(
        enabled = false,
        iosMode = false,
        showPercent = true,
        showCellDots = true,
        designStyle = DuoPrefs.DESIGN_OLD,
        engineMode = DuoPrefs.ENGINE_AUTO,
        eventMask = 0,
        downloadProgressEnabled = false,
        showInQs = false,
        showInShade = false,
        showAirplane = false,
        dndMode = DuoPrefs.DND_OFF,
        tapAction = "no_action",
        doubleTapAction = "no_action",
        longPressAction = "no_action",
        indicatorsTapAction = "no_action",
        indicatorsDoubleTapAction = "no_action",
        indicatorsLongPressAction = "no_action"
    )

    private val offSettings = base
    private val duoSettings = base.copy(enabled = true)
    private val iosSettings = base.copy(enabled = false, iosMode = true)

    private fun set(settings: ModuleSettings): FeatureSet =
        featureSetFor(settings, adbOverride = null, iosDrawingReady = true, otherOrientationEnabled = false)

    @Before
    fun setUp() {
        resetIosComponents()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        resetIosComponents()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun resetIosComponents() {
        IosIconOrder.clearForTest()
        IosSlotLayout.resetForTest()
        IosExtras.resetForTest()
        IosStackedSim.deactivate()
        IosResourceEngine.deactivate()
    }

    // ------------------------------------------------------------------ 1. pure owner matrix

    @Test
    fun `owner walks NONE DUO IOS DUO NONE with the right runtime flags`() {
        val steps = listOf(offSettings, duoSettings, iosSettings, duoSettings, offSettings)
        val sets = steps.map(::set)

        assertEquals(
            listOf(BarOwner.NONE, BarOwner.DUO, BarOwner.IOS, BarOwner.DUO, BarOwner.NONE),
            sets.map { it.owner }
        )
        assertEquals(listOf(false, true, false, true, false), sets.map { it.duoRuntime })
        assertEquals(listOf(false, false, true, false, false), sets.map { it.iosRuntime })
        assertEquals(
            listOf(DuoRenderer.NONE, DuoRenderer.RIVE, DuoRenderer.NONE, DuoRenderer.RIVE, DuoRenderer.NONE),
            sets.map { it.renderer }
        )

        // iOS owns the bar with no Duo feature; NONE carries no feature either.
        assertTrue(sets[0].duo.isEmpty())
        assertTrue(sets[2].duo.isEmpty())
        assertTrue(sets[4].duo.isEmpty())
        assertTrue(sets[1].duo.contains(DuoFeature.CELL_DOTS))
    }

    @Test
    fun `registrations are NONE-empty, IOS-panel-only, DUO-status-layout`() {
        val sets = listOf(offSettings, duoSettings, iosSettings, duoSettings, offSettings).map(::set)
        val registrations = sets.map(::duoRegistrationsFor)

        assertEquals(emptySet<DuoRegistration>(), registrations[0])
        assertEquals(setOf(DuoRegistration.PANEL_EXPANSION, DuoRegistration.STATUS_LAYOUT), registrations[1])
        assertEquals(setOf(DuoRegistration.PANEL_EXPANSION), registrations[2])
        assertEquals(setOf(DuoRegistration.PANEL_EXPANSION, DuoRegistration.STATUS_LAYOUT), registrations[3])
        assertEquals(emptySet<DuoRegistration>(), registrations[4])

        // No registration is iOS-only; the one iOS carries is the shared panel trigger.
        assertTrue(registrations[2].all { it == DuoRegistration.PANEL_EXPANSION })
        assertFalse(registrations[2].contains(DuoRegistration.STATUS_LAYOUT))
    }

    @Test
    fun `owner actions tear the other mode down and always teardown iOS on leaving`() {
        val sets = listOf(offSettings, duoSettings, iosSettings, duoSettings, offSettings).map(::set)
        val actions = sets.zipWithNext { current, next -> ownerActions(current, next) }

        assertEquals(setOf(OwnerAction.ATTACH_DUO, OwnerAction.TEARDOWN_IOS), actions[0])
        assertEquals(
            setOf(OwnerAction.STOP_DUO_MONITOR, OwnerAction.TEARDOWN_DUO, OwnerAction.ATTACH_IOS),
            actions[1]
        )
        assertEquals(setOf(OwnerAction.ATTACH_DUO, OwnerAction.TEARDOWN_IOS), actions[2])
        assertEquals(
            setOf(OwnerAction.STOP_DUO_MONITOR, OwnerAction.TEARDOWN_DUO, OwnerAction.TEARDOWN_IOS),
            actions[3]
        )

        sets.zipWithNext { current, next ->
            val action = ownerActions(current, next)
            if (next.owner == BarOwner.DUO && current.owner != BarOwner.DUO) {
                assertTrue("entering DUO must remove iOS: $current -> $next", action.contains(OwnerAction.TEARDOWN_IOS))
            }
            if (next.owner == BarOwner.IOS && current.owner != BarOwner.IOS) {
                assertTrue("entering IOS must stop Duo: $current -> $next", action.contains(OwnerAction.TEARDOWN_DUO))
            }
            if (current.iosRuntime && !next.iosRuntime) {
                assertTrue("leaving IOS always tears it down: $current -> $next", action.contains(OwnerAction.TEARDOWN_IOS))
            }
        }
    }

    @Test
    fun `the three mode snapshots keep the Duo fields bit-identical`() {
        val snapshots = listOf(offSettings, duoSettings, iosSettings)

        assertEquals(1, snapshots.map { it.showCellDots }.distinct().size)
        assertEquals(1, snapshots.map { it.showPercent }.distinct().size)
        assertEquals(1, snapshots.map { it.designStyle }.distinct().size)
        assertEquals(1, snapshots.map { it.engineMode }.distinct().size)
        assertEquals(setOf(true), snapshots.map { it.showCellDots }.toSet())
        assertEquals(setOf(true), snapshots.map { it.showPercent }.toSet())
        assertEquals(setOf(DuoPrefs.DESIGN_OLD), snapshots.map { it.designStyle }.toSet())
        assertEquals(setOf(DuoPrefs.ENGINE_AUTO), snapshots.map { it.engineMode }.toSet())

        // Only the owner inputs differ; every other field is carried over verbatim.
        assertEquals(base, offSettings)
        assertEquals(base.copy(enabled = true), duoSettings)
        assertEquals(base.copy(iosMode = true), iosSettings)

        // Resolving the owner is read-only: the same snapshot still produces the same values.
        set(duoSettings)
        assertEquals(duoSettings, duoSettings)
        assertEquals(true, duoSettings.showCellDots)
        assertEquals(DuoPrefs.DESIGN_OLD, duoSettings.designStyle)
    }

    // ------------------------------------------------------------------ 4. Duo leg (pure)

    @Test
    fun `Duo registrations carry status layout and no iOS-only registration`() {
        val duoSet = set(duoSettings)
        val iosSet = set(iosSettings)
        val registrations = duoRegistrationsFor(duoSet)

        assertEquals(BarOwner.DUO, duoSet.owner)
        assertTrue(registrations.contains(DuoRegistration.STATUS_LAYOUT))
        assertTrue(registrations.contains(DuoRegistration.PANEL_EXPANSION))
        assertFalse(registrations.any { it.tag.contains("ios", ignoreCase = true) })

        // The iOS owner adds nothing of its own: the only registration is the shared panel hook.
        assertEquals(setOf(DuoRegistration.PANEL_EXPANSION), duoRegistrationsFor(iosSet))
    }

    @Test
    fun `Duo cell dots own the bar and register the wifi and cell receivers`() {
        val duoSet = set(duoSettings)
        assertTrue(duoSet.duo.contains(DuoFeature.CELL_DOTS))
        assertTrue(duoSet.duo.contains(DuoFeature.WIFI_GLYPH))

        val actions = monitorActions(duoSet)
        assertTrue(actions.contains("android.net.wifi.RSSI_CHANGED"))
        assertTrue(actions.contains("android.net.wifi.WIFI_STATE_CHANGED"))
        assertTrue(actions.contains("android.net.conn.CONNECTIVITY_CHANGE"))
        assertTrue(actions.contains("android.intent.action.SERVICE_STATE"))
        assertTrue(actions.contains("android.intent.action.ANY_DATA_STATE"))

        // Turning cell dots off drops the cell-only receivers but keeps the Wi-Fi ones.
        val noCell = duoSet.copy(duo = duoSet.duo - DuoFeature.CELL_DOTS)
        val noCellActions = monitorActions(noCell)
        assertFalse(noCellActions.contains("android.intent.action.SERVICE_STATE"))
        assertFalse(noCellActions.contains("android.intent.action.ANY_DATA_STATE"))
        assertTrue(noCellActions.contains("android.net.wifi.RSSI_CHANGED"))

        // Neither iOS nor NONE owns a monitor.
        assertTrue(monitorActions(set(iosSettings)).isEmpty())
        assertTrue(monitorActions(set(offSettings)).isEmpty())
    }

    // ------------------------------------------------------------------ 2/3. component teardown

    @Test
    fun `OFF-DUO-IOS-DUO-OFF restores every view slot and drawable value`() {
        val fixture = Fixture()
        val before = fixture.snapshot()

        // DUO: no iOS component may be live yet.
        assertEquals(BarOwner.DUO, set(duoSettings).owner)
        fixture.assertNoIosActive()

        // IOS: every component draws and resizes.
        fixture.activateIos()
        fixture.assertIosEffects(before)

        // OFF: the exact inverse, then the second IOS cycle for idempotence.
        fixture.deactivateIos()
        fixture.assertRestored(before)
        fixture.assertNoIosActive()

        // DUO again: still nothing from iOS.
        assertEquals(BarOwner.DUO, set(duoSettings).owner)
        fixture.assertNoIosActive()

        // IOS ON/OFF a second time.
        fixture.activateIos()
        fixture.assertIosEffects(before)
        fixture.deactivateIos()
        fixture.assertRestored(before)
        fixture.assertNoIosActive()
    }

    @Test
    fun `after iOS off the resource engine is inactive and no target remains`() {
        val fixture = Fixture()
        val before = fixture.snapshot()

        fixture.activateIos()
        fixture.deactivateIos()

        assertFalse(IosResourceEngine.isActive)
        assertEquals(0, IosResourceEngine.mapSize)
        assertEquals(0, IosResourceEngine.hiddenEntries)
        assertFalse(IosExtras.hidden)
        assertEquals(0, IosExtras.hiddenViewCount)
        assertFalse(IosStackedSim.isActive)
        assertEquals(0, IosStackedSim.trackedViews)
        assertEquals(0, IosSlotLayout.registeredForTest)
        assertNull(IosSlotLayout.targetForTest(fixture.wifiOuter))
        assertNull(IosSlotLayout.targetForTest(fixture.iosContainer))
        assertFalse(IosIconOrder.isActive)
        fixture.assertRestored(before)
    }

    // ------------------------------------------------------------------------------ fixture

    /** A live `StatusBarIconList.Slot` stand-in: the name is read through `getName()`. */
    private class NamedSlot(private val name: String) {
        fun getName(): String = name
        override fun toString(): String = "Slot($name)"
    }

    /** The fake `StatusBarIconList` holder: the production `mSlots` field plus `getName()` entries. */
    private class FakeSlotHolder(names: List<String>) {
        @JvmField
        val mSlots: MutableList<Any> = names.map { NamedSlot(it) }.toMutableList()
    }

    /** The OPLUS Wi-Fi composite: a real child that is not a `StatusBarIconView`. */
    class WifiCompositeView(context: Context) : View(context) {
        fun getSlot(): String = "wifi"
    }

    /** A plain status icon child. */
    class PlainIconView(context: Context, private val slot: String) : View(context) {
        fun getSlot(): String = slot
    }

    /** The measured container shape, plus the optional animation switch the order hook suppresses. */
    private class FakeOrderContainer(context: Context) : LinearLayout(context) {
        @JvmField
        var mEnableAnimation = true

        fun setEnableAnimation(enabled: Boolean) {
            mEnableAnimation = enabled
        }
    }

    /** A SIM outer: the measured `getSubId()` identity plus one cell leaf. */
    private class FakeMobileView(context: Context, private val subId: Int) : FrameLayout(context) {
        val signal = ImageView(context)

        init {
            addView(signal, LayoutParams(60, 63))
        }

        fun getSubId(): Int = subId
    }

    private data class ViewState(
        val params: ViewGroup.LayoutParams?,
        val width: Int,
        val height: Int,
        val leftMargin: Int,
        val topMargin: Int,
        val rightMargin: Int,
        val bottomMargin: Int,
        val gravity: Int,
        val scaleType: ImageView.ScaleType?,
        val visibility: Int,
        val clipChildren: Boolean?,
        val clipToPadding: Boolean?,
        val background: Drawable?,
        val image: Drawable?,
        val children: List<View>
    )

    private data class TreeState(val views: Map<View, ViewState>, val slots: List<Any>)

    private inner class Fixture {
        val root = LinearLayout(context)

        // (a) The fake StatusBarIconList holder.
        val holder = FakeSlotHolder(listOf("wifi", "mobile", "battery", "vpn"))

        // (b) The order container: one Wi-Fi composite and three plain slot-tagged children.
        val orderContainer = FakeOrderContainer(context)
        val orderWifi = WifiCompositeView(context).apply {
            background = ColorDrawable(Color.RED)
        }
        val orderMobile = PlainIconView(context, "mobile").apply {
            background = ColorDrawable(Color.BLUE)
        }
        val orderBattery = PlainIconView(context, "battery").apply {
            background = ColorDrawable(Color.GREEN)
        }
        val orderVpn = PlainIconView(context, "vpn").apply {
            background = ColorDrawable(Color.YELLOW)
        }

        // (c) The real slot-layout tree: system_icons -> StatusIconContainer -> the measured chains.
        val iosRoot = LinearLayout(context)
        val iosContainer = LinearLayout(context)
        val mobileOuter = FrameLayout(context)
        val mobileGroup = LinearLayout(context)
        val mobileCombo = FrameLayout(context)
        val signal = ImageView(context)
        val label = ImageView(context)
        val wifiOuter = FrameLayout(context)
        val wifiGroup = LinearLayout(context)
        val wifiCombo = FrameLayout(context)
        val wifiSignal = ImageView(context)
        val battery = FrameLayout(context)
        val batteryLeaf = ImageView(context)

        // The stacked-SIM branch, plus the extras leaf.
        val stackedRoot = FrameLayout(context)
        val stackedData = FakeMobileView(context, 2)
        val stackedOther = FakeMobileView(context, 1)
        val extrasLeaf = ImageView(context)

        val stackedDataOriginal: Drawable
        val stackedOtherOriginal: Drawable
        val extrasOriginal = ColorDrawable(Color.CYAN)

        init {
            root.orientation = LinearLayout.VERTICAL

            orderContainer.orientation = LinearLayout.HORIZONTAL
            orderContainer.addView(orderWifi, LinearLayout.LayoutParams(40, 40))
            orderContainer.addView(orderMobile, LinearLayout.LayoutParams(40, 40))
            orderContainer.addView(orderBattery, LinearLayout.LayoutParams(40, 40))
            orderContainer.addView(orderVpn, LinearLayout.LayoutParams(40, 40))
            root.addView(orderContainer, LinearLayout.LayoutParams(160, 40))

            iosRoot.orientation = LinearLayout.HORIZONTAL
            iosRoot.clipChildren = true
            iosRoot.clipToPadding = true
            iosContainer.orientation = LinearLayout.HORIZONTAL
            iosContainer.clipChildren = true
            iosRoot.addView(iosContainer, LinearLayout.LayoutParams(138, 63))
            iosRoot.addView(battery, LinearLayout.LayoutParams(86, 63))

            mobileOuter.clipChildren = true
            mobileGroup.clipChildren = true
            mobileCombo.clipChildren = true
            iosContainer.addView(mobileOuter, LinearLayout.LayoutParams(63, 63))
            mobileOuter.addView(mobileGroup, FrameLayout.LayoutParams(60, 63))
            mobileGroup.addView(mobileCombo, LinearLayout.LayoutParams(60, 63))
            mobileCombo.addView(signal, FrameLayout.LayoutParams(60, 51))
            mobileCombo.addView(label, FrameLayout.LayoutParams(20, 16))
            label.visibility = View.GONE

            wifiOuter.clipChildren = true
            wifiGroup.clipChildren = true
            wifiCombo.clipChildren = true
            iosContainer.addView(wifiOuter, LinearLayout.LayoutParams(70, 63))
            wifiOuter.addView(wifiGroup, FrameLayout.LayoutParams(70, 63))
            wifiGroup.addView(wifiCombo, LinearLayout.LayoutParams(70, 51))
            wifiCombo.addView(wifiSignal, FrameLayout.LayoutParams(70, 51))

            battery.addView(batteryLeaf, FrameLayout.LayoutParams(82, 51))
            root.addView(iosRoot, LinearLayout.LayoutParams(224, 63))

            signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(part = IosPart.CELL, cellBars = 4f),
                    60, 51, relativeSize = false, captureColorFilter = true
                )
            )
            wifiSignal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(part = IosPart.WIFI, wifiLevel = 3f),
                    70, 51, relativeSize = false, captureColorFilter = true
                )
            )
            batteryLeaf.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.BATTERY,
                    IosVisual(part = IosPart.BATTERY, batteryLevel = 1f),
                    82, 51, relativeSize = false, captureColorFilter = true
                )
            )

            stackedData.signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(part = IosPart.CELL, cellBars = 4f),
                    60, 63, relativeSize = true, captureColorFilter = true
                )
            )
            stackedOther.signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(part = IosPart.CELL, cellBars = 2f),
                    60, 63, relativeSize = true, captureColorFilter = true
                )
            )
            stackedDataOriginal = stackedData.signal.drawable
            stackedOtherOriginal = stackedOther.signal.drawable
            stackedRoot.addView(stackedData, FrameLayout.LayoutParams(60, 63))
            stackedRoot.addView(stackedOther, FrameLayout.LayoutParams(60, 63))
            root.addView(stackedRoot, LinearLayout.LayoutParams(120, 63))

            extrasLeaf.setImageDrawable(extrasOriginal)
            root.addView(extrasLeaf, LinearLayout.LayoutParams(24, 24))
        }

        fun activateIos() {
            IosIconOrder.markInstalledForTest()
            IosIconOrder.captureSlotListForTest(holder)
            IosIconOrder.onIconAdded(orderContainer)
            IosIconOrder.activate()

            IosSlotLayout.reapply(ModuleSettings.DEFAULT)
            assertTrue(IosSlotLayout.registerMobile(signal, label))
            assertTrue(IosSlotLayout.registerWifi(wifiSignal))
            assertTrue(IosSlotLayout.registerBattery(battery, batteryLeaf))

            IosExtras.register(extrasLeaf)
            IosExtras.apply(true)

            IosStackedSim.activate(context, duoSettings.copy(simDisplay = DuoPrefs.SIM_DISPLAY_DUAL))
            assertTrue(IosStackedSim.reconcileViews(stackedData, 2, IosSimDisplay.AUTO))

            shadowOf(Looper.getMainLooper()).idle()
        }

        fun deactivateIos() {
            IosIconOrder.restore()
            IosSlotLayout.restoreAll()
            IosExtras.deactivate()
            IosStackedSim.deactivate()
            IosResourceEngine.deactivate()
            shadowOf(Looper.getMainLooper()).idle()
        }

        fun assertIosEffects(before: TreeState) {
            // Slot list and child order are the iOS order, with the same live instances.
            assertEquals(listOf("mobile", "wifi", "battery", "vpn"), holder.mSlots.map { (it as NamedSlot).getName() })
            assertEquals(listOf(orderMobile, orderWifi, orderBattery, orderVpn), orderChildren())
            assertSame(before.slots[1], holder.mSlots[0]) // mobile
            assertSame(before.slots[0], holder.mSlots[1]) // wifi

            assertTrue(IosIconOrder.isActive)

            // The slot layout recorded targets and a changed box.
            assertNotNull(IosSlotLayout.targetForTest(wifiOuter))
            assertNotNull(IosSlotLayout.targetForTest(iosContainer))
            val defaultWidth = IosSlotLayout.targetForTest(wifiOuter)!![0]
            IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 200))
            val scaledWidth = IosSlotLayout.targetForTest(wifiOuter)!![0]
            assertNotEquals("the iOS box must change while iOS owns the bar", defaultWidth, scaledWidth)
            assertNotEquals("the iOS box must differ from stock", before.views.getValue(wifiOuter).width, scaledWidth)

            // The extras gate is on and the stacked SIM replaced the other row.
            assertTrue(IosExtras.hidden)
            assertEquals(1, IosExtras.hiddenViewCount)
            assertTrue(IosStackedSim.isActive)
            assertTrue(IosStackedSim.stackedActive)
            assertEquals(1, IosStackedSim.hiddenSubId)
            assertEquals(View.GONE, stackedOther.visibility)
            assertEquals(0, stackedOther.layoutParams.width)
            assertEquals(0, stackedOther.layoutParams.height)
            val stacked = stackedData.signal.drawable as IosGlyphDrawable
            assertEquals(IosCellMode.DUAL, stacked.visualForTest!!.cellMode)
        }

        fun assertRestored(before: TreeState) {
            val after = snapshot()
            assertEquals("every captured view/slot value must return to its pre-iOS state", before, after)
            before.slots.zip(after.slots).forEach { (expected, actual) -> assertSame(expected, actual) }
            assertSame(stackedDataOriginal, stackedData.signal.drawable)
            assertSame(stackedOtherOriginal, stackedOther.signal.drawable)
            assertSame(extrasOriginal, extrasLeaf.drawable)
            assertTrue(orderContainer.mEnableAnimation)
        }

        fun assertNoIosActive() {
            assertFalse(IosIconOrder.isActive)
            assertEquals(0, IosSlotLayout.registeredForTest)
            assertNull(IosSlotLayout.targetForTest(wifiOuter))
            assertNull(IosSlotLayout.targetForTest(iosContainer))
            assertFalse(IosExtras.hidden)
            assertEquals(0, IosExtras.hiddenViewCount)
            assertFalse(IosStackedSim.isActive)
            assertFalse(IosStackedSim.stackedActive)
            assertEquals(0, IosStackedSim.trackedViews)
            assertFalse(IosResourceEngine.isActive)
            assertEquals(0, IosResourceEngine.mapSize)
        }

        fun snapshot(): TreeState {
            val views = LinkedHashMap<View, ViewState>()
            fun walk(view: View) {
                views[view] = stateOf(view)
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) walk(view.getChildAt(index))
                }
            }
            walk(root)
            return TreeState(views, holder.mSlots.toList())
        }

        private fun orderChildren(): List<View> =
            (0 until orderContainer.childCount).map { orderContainer.getChildAt(it) }

        private fun stateOf(view: View): ViewState {
            val params = view.layoutParams
            val margins = params as? ViewGroup.MarginLayoutParams
            val gravity = (params as? FrameLayout.LayoutParams)?.gravity
                ?: (params as? LinearLayout.LayoutParams)?.gravity
                ?: 0
            val group = view as? ViewGroup
            return ViewState(
                params = params,
                width = params?.width ?: 0,
                height = params?.height ?: 0,
                leftMargin = margins?.leftMargin ?: 0,
                topMargin = margins?.topMargin ?: 0,
                rightMargin = margins?.rightMargin ?: 0,
                bottomMargin = margins?.bottomMargin ?: 0,
                gravity = gravity,
                scaleType = (view as? ImageView)?.scaleType,
                visibility = view.visibility,
                clipChildren = group?.clipChildren,
                clipToPadding = group?.clipToPadding,
                background = view.background,
                image = (view as? ImageView)?.drawable,
                children = if (group != null) {
                    (0 until group.childCount).map { group.getChildAt(it) }
                } else {
                    emptyList()
                }
            )
        }
    }
}
