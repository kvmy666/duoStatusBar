package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-01: the strip must be found on a ROM whose ids we never measured, by the roles it contains.
 * A wrong guess here hides a user's icons and draws nothing, so the locator is pinned in tests
 * instead of only on a phone. Ids do not resolve under Robolectric, which is exactly the
 * "unmeasured ROM" state `roleScan` exists for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContainerFinderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rom = RomDetection.forThisRom("unknown", "unknown", "unknown", "unknown")

    private fun finder(adapter: RomAdapter = rom) = ContainerFinder(context, adapter, LogOnce())

    private val activity: Activity =
        Robolectric.buildActivity(Activity::class.java).setup().get()

    /**
     * A root attached to a real Robolectric window before its children are added, so `isShown` and
     * `isAttachedToWindow` are true for them while each test still pins its own `layout(...)` sizes.
     */
    private fun attachedRoot(): FrameLayout {
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.layout(0, 0, 1080, 200)
        return root
    }

    /** A status-icons container named like the ones every ROM keeps. */
    private class FakeStatusIconContainer(context: Context) : View(context)

    /** A battery view named like the AOSP/OEM battery meters. */
    private class FakeBatteryMeterView(context: Context) : View(context)

    /** A battery *container* (HyperOS) whose name must not be mistaken for the battery view. */
    private class MiuiStatusBatteryContainer(context: Context) : LinearLayout(context)

    /** The measured QS cluster's battery host: `batteryRemainingIcon` is a container, not a meter view. */
    private class FakeBatteryRemainingIcon(context: Context) : LinearLayout(context)

    /**
     * Ids resolved by name, so the measured QS tree can be rebuilt under Robolectric (where the app's
     * resources contain none of SystemUI's ids). Production always uses [RomResources].
     */
    private class FakeIds {
        private val values = HashMap<String, Int>()

        fun id(name: String): Int = values.getOrPut(name) { View.generateViewId() }

        fun resolve(name: String): Int = values[name] ?: 0
    }

    @Test
    fun `finds the strip as the common parent of the icons and the battery`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        strip.addView(FakeBatteryMeterView(context))
        root.addView(strip)

        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `picks the lowest ancestor that holds both, not an outer wrapper`() {
        val root = FrameLayout(context)
        val wrapper = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        strip.addView(FakeBatteryMeterView(context))
        wrapper.addView(strip)
        root.addView(wrapper)

        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `an icons-only strip still resolves`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        root.addView(strip)

        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `a battery container is not mistaken for the battery view`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        // Only a *container* named "…BatteryContainer" — no battery meter view at all.
        strip.addView(MiuiStatusBatteryContainer(context))
        root.addView(strip)

        // The icons alone still pin the strip, and the container name is ignored as a battery.
        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `a gone strip is skipped`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        strip.addView(FakeBatteryMeterView(context))
        strip.visibility = View.GONE
        root.addView(strip)

        assertNull(finder().roleScan(root))
    }

    @Test
    fun `a tree with neither icons nor battery is left untouched`() {
        val root = FrameLayout(context)
        val random = LinearLayout(context)
        random.addView(View(context))
        root.addView(random)

        assertNull(finder().roleScan(root))
    }

    /**
     * CPH2747, 2026-10-27: the real cluster is `quick_qs_status_icons`, a `ConstraintLayout` holding
     * the `icons` wrapper (`statusIcons` inside it) and `batteryRemainingIcon`. At attach time every
     * view is 0x0. The old selection walked up from `statusIcons` and chose the inner `icons` wrapper,
     * so Duo was injected into a 0x0 view and the stock battery stayed on screen.
     */
    @Test
    fun `prefers quick_qs_status_icons over the zero sized icons fallback`() {
        val ids = FakeIds()
        val header = attachedRoot()
        val quick = LinearLayout(context).apply { id = ids.id("quick_qs_status_icons") }
        val icons = LinearLayout(context)
        val statusIcons = FakeStatusIconContainer(context).apply { id = ids.id("statusIcons") }
        val battery = FakeBatteryRemainingIcon(context).apply { id = ids.id("batteryRemainingIcon") }
        icons.addView(statusIcons)
        quick.addView(icons)
        quick.addView(battery)
        header.addView(quick)

        // Zero-sized, exactly like the measured tree before the shade expands.
        assertSame(quick, finder().findShadeIconsArea(header, ids::resolve))
    }

    @Test
    fun `falls back to shade_header_system_icons when the quick cluster is absent`() {
        val ids = FakeIds()
        val header = attachedRoot()
        val system = LinearLayout(context).apply { id = ids.id("shade_header_system_icons") }
        val icons = LinearLayout(context)
        val statusIcons = FakeStatusIconContainer(context).apply { id = ids.id("statusIcons") }
        icons.addView(statusIcons)
        header.addView(system)
        header.addView(icons)

        assertSame(system, finder().findShadeIconsArea(header, ids::resolve))
    }

    @Test
    fun `falls back to parent of statusIcons when neither cluster id resolves`() {
        val ids = FakeIds()
        val header = attachedRoot()
        val icons = LinearLayout(context)
        val statusIcons = FakeStatusIconContainer(context).apply { id = ids.id("statusIcons") }
        icons.addView(statusIcons)
        header.addView(icons)

        assertSame(icons, finder().findShadeIconsArea(header, ids::resolve))
    }

    @Test
    fun `no shade icon area is returned when nothing resolves`() {
        val header = attachedRoot()
        header.addView(View(context))

        assertNull(finder().findShadeIconsArea(header) { 0 })
    }

    /**
     * CPH2747, 2026-10-27: the id is not unique. `split_shade_status_bar` keeps a 0x0/GONE instance,
     * while the drawn cluster is under `qs_frame`/`header_container`. The search must walk past the
     * dead duplicate; a plain `findViewById` returned it and the retry re-checked that same 0x0 view.
     */
    @Test
    fun `the visible quick cluster is chosen over an earlier zero sized duplicate`() {
        val ids = FakeIds()
        val quickId = ids.id("quick_qs_status_icons")
        val window = attachedRoot()
        val split = LinearLayout(context).apply {
            id = quickId
            visibility = View.GONE
        }
        val wrapper = FrameLayout(context)
        val visible = LinearLayout(context).apply { id = quickId }
        // Laid out by the shade, 386x101 as measured on the device.
        visible.layout(0, 0, 386, 101)
        wrapper.addView(visible)
        window.addView(split)
        window.addView(wrapper)

        assertSame(visible, finder().findVisibleQuickQsCluster(window, ids::resolve))
    }

    /**
     * CPH2747, 2026-10-27: both `quick_qs_status_icons` instances are laid out and visible, so width
     * alone cannot pick the right one. The one under `header_container` is the shade header; the other
     * (inside `split_shade_status_bar`) must lose even when it is larger.
     */
    @Test
    fun `the quick cluster under header_container wins over a plain duplicate`() {
        val ids = FakeIds()
        val quickId = ids.id("quick_qs_status_icons")
        val headerContainerId = ids.id("header_container")
        val window = attachedRoot()
        val plain = LinearLayout(context).apply { id = quickId }
        plain.layout(0, 0, 500, 200)
        val plainWrapper = FrameLayout(context)
        plainWrapper.addView(plain)
        val headerContainer = FrameLayout(context).apply { id = headerContainerId }
        val header = LinearLayout(context).apply { id = quickId }
        header.layout(0, 0, 386, 101)
        val headerWrapper = FrameLayout(context)
        headerWrapper.addView(header)
        headerContainer.addView(headerWrapper)
        window.addView(plainWrapper)
        window.addView(headerContainer)

        assertSame(header, finder().findVisibleQuickQsCluster(window, ids::resolve))
    }

    @Test
    fun `falls back to the quick cluster under qs_frame when header_container is absent`() {
        val ids = FakeIds()
        val quickId = ids.id("quick_qs_status_icons")
        val qsFrameId = ids.id("qs_frame")
        val window = attachedRoot()
        val plain = LinearLayout(context).apply { id = quickId }
        plain.layout(0, 0, 500, 200)
        val plainWrapper = FrameLayout(context)
        plainWrapper.addView(plain)
        val qsFrame = FrameLayout(context).apply { id = qsFrameId }
        val inFrame = LinearLayout(context).apply { id = quickId }
        inFrame.layout(0, 0, 386, 101)
        qsFrame.addView(inFrame)
        window.addView(plainWrapper)
        window.addView(qsFrame)

        assertSame(inFrame, finder().findVisibleQuickQsCluster(window, ids::resolve))
    }

    @Test
    fun `falls back to the largest quick cluster when neither ancestor id matches`() {
        val ids = FakeIds()
        val quickId = ids.id("quick_qs_status_icons")
        val window = attachedRoot()
        val small = LinearLayout(context).apply { id = quickId }
        small.layout(0, 0, 351, 56)
        val large = LinearLayout(context).apply { id = quickId }
        large.layout(0, 0, 386, 101)
        val smallWrapper = FrameLayout(context)
        smallWrapper.addView(small)
        val largeWrapper = FrameLayout(context)
        largeWrapper.addView(large)
        window.addView(smallWrapper)
        window.addView(largeWrapper)

        assertSame(large, finder().findVisibleQuickQsCluster(window, ids::resolve))
    }

    @Test
    fun `only the dead quick cluster resolves to nothing`() {
        val ids = FakeIds()
        val window = attachedRoot()
        val split = LinearLayout(context).apply {
            id = ids.id("quick_qs_status_icons")
            visibility = View.GONE
        }
        window.addView(split)

        assertNull(finder().findVisibleQuickQsCluster(window, ids::resolve))
    }

    /**
     * Probes only: a profile's shade container is tried before the built-in QS order, but it must be
     * laid out, shown and attached - a 0x0 or hidden candidate falls through to the old order.
     */
    @Test
    fun `a visible profile shade candidate resolves before the built-in order`() {
        val ids = FakeIds()
        val profile = rom.copy(shadeContainerIds = listOf("profile_shade_area"))
        val header = attachedRoot()
        val profileArea = LinearLayout(context).apply { id = ids.id("profile_shade_area") }
        profileArea.layout(0, 0, 386, 101)
        header.addView(profileArea)
        val quick = LinearLayout(context).apply { id = ids.id("quick_qs_status_icons") }
        quick.layout(0, 0, 386, 101)
        header.addView(quick)

        assertSame(profileArea, finder(profile).findShadeIconsArea(header, ids::resolve))
    }

    @Test
    fun `a gone profile shade candidate is rejected and the fallback is used`() {
        val ids = FakeIds()
        val profile = rom.copy(shadeContainerIds = listOf("profile_shade_area"))
        val header = attachedRoot()
        val gone = LinearLayout(context).apply {
            id = ids.id("profile_shade_area")
            visibility = View.GONE
        }
        header.addView(gone)
        val quick = LinearLayout(context).apply { id = ids.id("quick_qs_status_icons") }
        quick.layout(0, 0, 386, 101)
        header.addView(quick)

        assertSame(quick, finder(profile).findShadeIconsArea(header, ids::resolve))
    }

    /**
     * The profile's shade container is probed across the whole window. The id is duplicated by a plain
     * copy, so the instance under `header_container` must win even though the copy is larger.
     */
    @Test
    fun `findVisibleShadeArea prefers the candidate under header_container`() {
        val ids = FakeIds()
        val profile = rom.copy(shadeContainerIds = listOf("shade_header_system_icons"))
        val areaId = ids.id("shade_header_system_icons")
        val headerContainerId = ids.id("header_container")
        val window = attachedRoot()
        val plain = LinearLayout(context).apply { id = areaId }
        plain.layout(0, 0, 700, 300)
        val plainWrapper = FrameLayout(context)
        plainWrapper.addView(plain)
        val headerContainer = FrameLayout(context).apply { id = headerContainerId }
        val inHeader = LinearLayout(context).apply { id = areaId }
        inHeader.layout(0, 0, 386, 101)
        headerContainer.addView(inHeader)
        window.addView(plainWrapper)
        window.addView(headerContainer)

        assertSame(inHeader, finder(profile).findVisibleShadeArea(window, ids::resolve))
    }

    @Test
    fun `findShadeBattery prefers the profile shade battery id`() {
        val ids = FakeIds()
        val profile = rom.copy(shadeBatteryId = "profile_battery")
        val area = attachedRoot()
        val fallback = FakeBatteryMeterView(context).apply { id = ids.id("batteryRemainingIcon") }
        val preferred = FakeBatteryRemainingIcon(context).apply { id = ids.id("profile_battery") }
        area.addView(fallback)
        area.addView(preferred)

        assertSame(preferred, finder(profile).findShadeBattery(area, ids::resolve))
    }

    /**
     * CPH2747, 2026-10-27: the drawn QS cluster is 9x63, and its immediate parent is also 9x63; the
     * wider `header` is a grandparent. It must walk past the same-size parent to the header, otherwise
     * Duo is squeezed into 9 px and Wi-Fi is drawn outside the ring.
     */
    @Test
    fun `a narrow quick cluster is hosted by its wider grandparent`() {
        val ids = FakeIds()
        val window = attachedRoot()
        val header = LinearLayout(context)
        val sameSizeParent = LinearLayout(context)
        val cluster = LinearLayout(context).apply { id = ids.id("quick_qs_status_icons") }
        val battery = FakeBatteryRemainingIcon(context).apply { id = ids.id("batteryRemainingIcon") }
        sameSizeParent.addView(cluster)
        header.addView(sameSizeParent)
        header.addView(battery)
        window.addView(header)
        // Lay the ancestors out first: each layout re-bounds its children, so the measured 9x63 has to
        // be pinned on the way back down instead of by one call on the cluster.
        header.layout(0, 0, 1020, 63)
        sameSizeParent.layout(0, 0, 9, 63)
        cluster.layout(0, 0, 9, 63)

        assertSame(header, finder().usableShadeHost(cluster))
    }

    /**
     * The shade header may not have a resolvable battery when it is normalized (it is laid out later),
     * so the wider ancestor is enough: keeping the 9 px cluster squeezed Duo and pushed Wi-Fi outside it.
     */
    @Test
    fun `a narrow cluster is hosted by its wider grandparent without a battery`() {
        val window = attachedRoot()
        val header = LinearLayout(context)
        val sameSizeParent = LinearLayout(context)
        val cluster = LinearLayout(context).apply { id = android.view.View.generateViewId() }
        sameSizeParent.addView(cluster)
        header.addView(sameSizeParent)
        window.addView(header)
        // Ancestors first, then pin the measured 9x63 back on the parent and the cluster.
        header.layout(0, 0, 1020, 63)
        sameSizeParent.layout(0, 0, 9, 63)
        cluster.layout(0, 0, 9, 63)

        assertSame(header, finder().usableShadeHost(cluster))
    }

    @Test
    fun `usableShadeHost prefers a standard ancestor over a custom one`() {
        val window = attachedRoot()
        val standard = LinearLayout(context)
        val custom = CustomContainer(context)
        val cluster = LinearLayout(context)
        custom.addView(cluster)
        standard.addView(custom)
        window.addView(standard)
        standard.layout(0, 0, 1020, 63)
        custom.layout(0, 0, 1020, 63)
        cluster.layout(0, 0, 9, 63)

        assertSame(standard, finder().usableShadeHost(cluster))
    }

    @Test
    fun `usableShadeHost keeps the cluster when no wider ancestor is on screen`() {
        // Detached: no ancestor is shown/attached, so there is no usable host and the cluster is kept.
        val root = FrameLayout(context)
        val sameSizeParent = LinearLayout(context)
        val cluster = LinearLayout(context)
        sameSizeParent.addView(cluster)
        root.addView(sameSizeParent)
        root.layout(0, 0, 9, 63)
        sameSizeParent.layout(0, 0, 9, 63)
        cluster.layout(0, 0, 9, 63)

        assertSame(cluster, finder().usableShadeHost(cluster))
    }

    /**
     * The locator returns the raw cluster; the host chooses the qs/shade slot from these original
     * dimensions before normalizing to a wider ancestor. Normalizing here made a 9x63 QS cluster look
     * like the shade header and consumed its slot.
     */
    @Test
    fun `findVisibleShadeArea returns the raw narrow cluster`() {
        val ids = FakeIds()
        val headerContainerId = ids.id("header_container")
        val window = attachedRoot()
        val headerContainer = FrameLayout(context).apply { id = headerContainerId }
        val sameSizeParent = FrameLayout(context)
        val cluster = LinearLayout(context).apply { id = ids.id("quick_qs_status_icons") }
        sameSizeParent.addView(cluster)
        headerContainer.addView(sameSizeParent)
        window.addView(headerContainer)
        headerContainer.layout(0, 0, 1020, 63)
        sameSizeParent.layout(0, 0, 9, 63)
        cluster.layout(0, 0, 9, 63)

        assertSame(cluster, finder().findVisibleShadeArea(window, ids::resolve))
    }

    /** A vendor container that rejects foreign children; only the standard ancestor is usable. */
    private class CustomContainer(context: Context) : ViewGroup(context) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
    }
}
