package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * IOS-B1: the pure family table and the exact suppress/re-assert/restore contract.
 *
 * The planner tests pin which measured class/id maps to which strategy, so a vendor name cannot drift
 * into the wrong family. The view tests pin the two promises the owner cares about: the stock slot keeps
 * its layout (no resize, C2), and switching the skin off puts the exact alpha/drawable/visibility back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosSkinTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ---- measured stock class names (only names from the measured iOS inventory, device dumps) ----

    private class MiuiBatteryMeterView(context: Context) : LinearLayout(context)
    private class BatteryMeterView(context: Context) : FrameLayout(context)
    private class SingleBindableStatusBarComposeIconView(context: Context) : LinearLayout(context)
    private class StatIconView(context: Context) : View(context)

    /**
     * A view that hides behind a resource entry name, so BY_ID resolution can be tested without adding
     * resources to the app. Everything else behaves like a plain View.
     */
    private open class NamedView(context: Context, private val entry: String) : View(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    /** Only the synthetic test ids are named; a miss falls back to the real table. */
    @Suppress("DEPRECATION")
    private class FakeResources(
        parent: Resources,
        private val names: Map<Int, String>
    ) : Resources(parent.assets, parent.displayMetrics, parent.configuration) {
        override fun getResourceEntryName(id: Int): String =
            names[id] ?: super.getResourceEntryName(id)
    }

    // ---- planner table ----

    @Test
    fun `planner maps the Oplus battery family`() {
        val plan = IosSkinPlanner.planFor(
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
            "battery",
            StockIconKind.BATTERY,
            emptyList()
        )
        assertEquals(IosSkinFamily.OPLUS_BATTERY, plan.family)
        assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, plan.strategy)
        assertTrue(plan.leafIds.isEmpty())
        assertEquals(IosLeafSource.OUTER, plan.leafSource)
        assertEquals(listOf("battery_icon_view"), plan.geometryIds)
    }

    @Test
    fun `planner maps the Oplus wifi and mobile families before the generic Modern names`() {
        val wifi = IosSkinPlanner.planFor(
            "OplusModernStatusBarWifiView", "wifi_combo", StockIconKind.WIFI, emptyList()
        )
        assertEquals(IosSkinFamily.OPLUS_WIFI, wifi.family)
        assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, wifi.strategy)
        assertTrue(wifi.leafIds.isEmpty())
        assertEquals(IosLeafSource.OUTER, wifi.leafSource)
        assertEquals(listOf("wifi_group", "wifi_signal"), wifi.geometryIds)

        val mobile = IosSkinPlanner.planFor(
            "OplusModernStatusBarMobileView", "mobile_combo", StockIconKind.CELLULAR, emptyList()
        )
        assertEquals(IosSkinFamily.OPLUS_MOBILE, mobile.family)
        assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, mobile.strategy)
        assertTrue(mobile.leafIds.isEmpty())
        assertEquals(IosLeafSource.OUTER, mobile.leafSource)
        assertEquals(listOf("mobile_group", "mobile_signal"), mobile.geometryIds)
    }

    @Test
    fun `the three Oplus plans hide the outer and keep the measured content ids`() {
        val battery = IosSkinPlanner.planFor(
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
            "battery",
            StockIconKind.BATTERY,
            emptyList()
        )
        val wifi = IosSkinPlanner.planFor(
            "OplusModernStatusBarWifiView", "wifi_combo", StockIconKind.WIFI, emptyList()
        )
        val mobile = IosSkinPlanner.planFor(
            "OplusModernStatusBarMobileView", "mobile_combo", StockIconKind.CELLULAR, emptyList()
        )
        for (plan in listOf(battery, wifi, mobile)) {
            assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, plan.strategy)
            assertEquals(IosLeafSource.OUTER, plan.leafSource)
            assertTrue(plan.leafIds.isEmpty())
        }
        assertEquals(listOf("battery_icon_view"), battery.geometryIds)
        assertEquals(listOf("wifi_group", "wifi_signal"), wifi.geometryIds)
        assertEquals(listOf("mobile_group", "mobile_signal"), mobile.geometryIds)
    }

    @Test
    fun `HyperOS and Compose plans keep their leaf strategy and empty geometry ids`() {
        val hyperBattery = IosSkinPlanner.planFor(
            "MiuiBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()
        )
        val hyperWifi = IosSkinPlanner.planFor(
            "ModernStatusBarWifiView", "wifi_combo", StockIconKind.WIFI, emptyList()
        )
        val compose = IosSkinPlanner.planFor(
            "SingleBindableStatusBarComposeIconView",
            "battery_composable_view",
            StockIconKind.BATTERY,
            emptyList()
        )
        assertEquals(IosSkinStrategy.ALPHA, hyperBattery.strategy)
        assertEquals(IosSkinStrategy.ALPHA, hyperWifi.strategy)
        assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, compose.strategy)
        assertEquals(IosLeafSource.OUTER, compose.leafSource)
        assertTrue(hyperBattery.geometryIds.isEmpty())
        assertTrue(hyperWifi.geometryIds.isEmpty())
        assertTrue(compose.geometryIds.isEmpty())
    }

    @Test
    fun `planner maps HyperOS battery and suppresses both drawing variants`() {
        val plan = IosSkinPlanner.planFor(
            "MiuiBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()
        )
        assertEquals(IosSkinFamily.HYPEROS_BATTERY, plan.family)
        assertEquals(IosSkinStrategy.ALPHA, plan.strategy)
        assertEquals(listOf("hollow_battery_image", "battery_image"), plan.leafIds)
        assertEquals(IosLeafSource.BY_ID, plan.leafSource)
    }

    @Test
    fun `planner maps HyperOS wifi and mobile`() {
        val wifi = IosSkinPlanner.planFor(
            "ModernStatusBarWifiView", "wifi_combo", StockIconKind.WIFI, emptyList()
        )
        assertEquals(IosSkinFamily.HYPEROS_WIFI, wifi.family)
        assertEquals(IosSkinStrategy.ALPHA, wifi.strategy)
        assertEquals(listOf("wifi_signal"), wifi.leafIds)
        assertEquals(IosLeafSource.BY_ID, wifi.leafSource)

        val mobile = IosSkinPlanner.planFor(
            "ModernStatusBarMobileView", "mobile_combo", StockIconKind.CELLULAR, emptyList()
        )
        assertEquals(IosSkinFamily.HYPEROS_MOBILE, mobile.family)
        assertEquals(IosSkinStrategy.ALPHA, mobile.strategy)
        assertEquals(listOf("mobile_signal_container", "mobile_signal"), mobile.leafIds)
        assertEquals(IosLeafSource.BY_ID, mobile.leafSource)
    }

    @Test
    fun `planner maps Samsung battery to the first ImageView`() {
        val plan = IosSkinPlanner.planFor(
            "BatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()
        )
        assertEquals(IosSkinFamily.SAMSUNG_BATTERY, plan.family)
        assertEquals(IosSkinStrategy.CLEAR_DRAWABLE, plan.strategy)
        assertTrue(plan.leafIds.isEmpty())
        assertEquals(IosLeafSource.FIRST_IMAGE_VIEW, plan.leafSource)
    }

    @Test
    fun `planner maps Samsung SingleBindable icon by id`() {
        val plan = IosSkinPlanner.planFor(
            "SingleBindableStatusBarIconView", "battery", StockIconKind.BATTERY, emptyList()
        )
        assertEquals(IosSkinFamily.SAMSUNG_SBICON, plan.family)
        assertEquals(IosSkinStrategy.ALPHA, plan.strategy)
        assertEquals(listOf("icon_view"), plan.leafIds)
        assertEquals(IosLeafSource.BY_ID, plan.leafSource)
    }

    @Test
    fun `planner maps Compose battery and Compose icon, with the icon taking precedence`() {
        val battery = IosSkinPlanner.planFor(
            "ComposeView", "battery_composable_view", StockIconKind.BATTERY, emptyList()
        )
        assertEquals(IosSkinFamily.COMPOSE_BATTERY, battery.family)
        assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, battery.strategy)
        assertEquals(IosLeafSource.OUTER, battery.leafSource)

        val icon = IosSkinPlanner.planFor(
            "SingleBindableStatusBarComposeIconView",
            "battery_composable_view",
            StockIconKind.BATTERY,
            emptyList()
        )
        assertEquals(IosSkinFamily.COMPOSE_ICON, icon.family)
        assertEquals(IosSkinStrategy.INVISIBLE_KEEP_SLOT, icon.strategy)
        assertEquals(IosLeafSource.OUTER, icon.leafSource)
    }

    @Test
    fun `planner skips opaque StatIconView and unknown classes`() {
        val opaque = IosSkinPlanner.planFor(
            "StatIconView", null, StockIconKind.OTHER, emptyList()
        )
        assertEquals(IosSkinFamily.OPAQUE_STAT_ICON, opaque.family)
        assertEquals(IosSkinStrategy.SKIP, opaque.strategy)

        val unknown = IosSkinPlanner.planFor(
            "FrameLayout", null, StockIconKind.OTHER, emptyList()
        )
        assertEquals(IosSkinFamily.UNKNOWN, unknown.family)
        assertEquals(IosSkinStrategy.SKIP, unknown.strategy)
    }

    @Test
    fun `battery families are ordered Miui and Stat before the exact Samsung name`() {
        assertEquals(
            IosSkinFamily.HYPEROS_BATTERY,
            IosSkinPlanner.planFor("MiuiBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()).family
        )
        assertEquals(
            IosSkinFamily.OPLUS_BATTERY,
            IosSkinPlanner.planFor("StatBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()).family
        )
        assertEquals(
            IosSkinFamily.SAMSUNG_BATTERY,
            IosSkinPlanner.planFor("BatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()).family
        )
    }

    @Test
    fun `planner only matches the exact BatteryMeterView simple name`() {
        val samsung = IosSkinPlanner.planFor(
            "BatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()
        )
        assertEquals(IosSkinFamily.SAMSUNG_BATTERY, samsung.family)
        assertEquals(IosSkinStrategy.CLEAR_DRAWABLE, samsung.strategy)

        // IOS-INV §2.5: Infinix `TrBatteryMeterView` is unmeasured; its stock drawing stays in place.
        val infinix = IosSkinPlanner.planFor(
            "TrBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()
        )
        assertEquals(IosSkinFamily.UNKNOWN, infinix.family)
        assertEquals(IosSkinStrategy.SKIP, infinix.strategy)
    }

    @Test
    fun `planner still gives Miui and Stat names their own families`() {
        assertEquals(
            IosSkinFamily.HYPEROS_BATTERY,
            IosSkinPlanner.planFor("MiuiBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()).family
        )
        assertEquals(
            IosSkinFamily.OPLUS_BATTERY,
            IosSkinPlanner.planFor("StatBatteryMeterView", "battery", StockIconKind.BATTERY, emptyList()).family
        )
    }

    // ---- ALPHA ----

    @Test
    fun `alpha suppression re-asserts after an OEM write and restores the exact values`() {
        val outer = MiuiBatteryMeterView(context)
        val hollow = NamedView(context, "hollow_battery_image").apply { alpha = 0.4f }
        val normal = NamedView(context, "battery_image").apply { alpha = 1f }
        hollow.layoutParams = LinearLayout.LayoutParams(30, 40)
        normal.layoutParams = LinearLayout.LayoutParams(31, 41)
        outer.addView(hollow)
        outer.addView(normal)
        val hollowLp = hollow.layoutParams
        val normalLp = normal.layoutParams

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        assertTrue(skin.isSuppressing)
        assertEquals(0f, hollow.alpha, 0f)
        assertEquals(0f, normal.alpha, 0f)

        hollow.alpha = 0.9f
        skin.reassert()
        assertEquals(0f, hollow.alpha, 0f)

        skin.restore()
        assertFalse(skin.isSuppressing)
        assertEquals(0.4f, hollow.alpha, 0f)
        assertEquals(1f, normal.alpha, 0f)

        assertSame(hollowLp, hollow.layoutParams)
        assertSame(normalLp, normal.layoutParams)
        assertEquals(30, hollow.layoutParams.width)
        assertEquals(40, hollow.layoutParams.height)
        assertEquals(31, normal.layoutParams.width)
        assertEquals(41, normal.layoutParams.height)
    }

    @Test
    fun `suppressing twice captures once and restore is idempotent`() {
        val outer = MiuiBatteryMeterView(context)
        val leaf = NamedView(context, "battery_image").apply { alpha = 0.25f }
        outer.addView(leaf)

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        leaf.alpha = 0.8f
        assertTrue(skin.suppress(outer))
        assertEquals(0f, leaf.alpha, 0f)

        skin.restore()
        assertEquals(0.25f, leaf.alpha, 0f)
        skin.restore()
        assertEquals(0.25f, leaf.alpha, 0f)
    }

    // ---- CLEAR_DRAWABLE ----

    @Test
    fun `clear drawable suppresses rebinds and restores the original instance`() {
        val outer = BatteryMeterView(context)
        val leaf = ImageView(context).apply {
            setImageDrawable(ColorDrawable(0xFF00FF00.toInt()))
            layoutParams = FrameLayout.LayoutParams(20, 21)
        }
        outer.addView(leaf)
        val original = leaf.drawable
        val lp = leaf.layoutParams

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        assertNull(leaf.drawable)

        leaf.setImageDrawable(ColorDrawable(0xFFFF0000.toInt()))
        skin.reassert()
        assertNull(leaf.drawable)

        skin.restore()
        assertSame(original, leaf.drawable)
        assertSame(lp, leaf.layoutParams)
        assertEquals(20, leaf.layoutParams.width)
        assertEquals(21, leaf.layoutParams.height)
    }

    // ---- INVISIBLE_KEEP_SLOT ----

    @Test
    fun `invisible keeps the slot and follows the OEM intent`() {
        val outer = SingleBindableStatusBarComposeIconView(context).apply {
            visibility = View.VISIBLE
            layoutParams = ViewGroup.LayoutParams(50, 60)
        }
        val lp = outer.layoutParams

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        assertEquals(View.INVISIBLE, outer.visibility)

        outer.visibility = View.VISIBLE
        skin.reassert()
        assertEquals(View.INVISIBLE, outer.visibility)

        outer.visibility = View.GONE
        skin.reassert()
        assertEquals(View.GONE, outer.visibility)

        skin.restore()
        assertEquals(View.GONE, outer.visibility)
        assertSame(lp, outer.layoutParams)
        assertEquals(50, outer.layoutParams.width)
        assertEquals(60, outer.layoutParams.height)
    }

    @Test
    fun `invisible restore returns a still-hidden slot to its intended visibility`() {
        val outer = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.VISIBLE }

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        assertEquals(View.INVISIBLE, outer.visibility)

        skin.restore()
        assertEquals(View.VISIBLE, outer.visibility)
    }

    @Test
    fun `invisible refuses a slot the OEM already made INVISIBLE`() {
        val outer = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.INVISIBLE }

        val skin = IosSkin()
        assertFalse(skin.suppress(outer))
        assertFalse(skin.isSuppressing)
        assertEquals(View.INVISIBLE, outer.visibility)
    }

    @Test
    fun `invisible refuses a slot the OEM already made GONE`() {
        val outer = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.GONE }

        val skin = IosSkin()
        assertFalse(skin.suppress(outer))
        assertFalse(skin.isSuppressing)
        assertEquals(View.GONE, outer.visibility)
    }

    @Test
    fun `invisible suppresses a visible slot and restore brings it back`() {
        val outer = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.VISIBLE }

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        assertTrue(skin.isSuppressing)
        assertEquals(View.INVISIBLE, outer.visibility)

        skin.restore()
        assertFalse(skin.isSuppressing)
        assertEquals(View.VISIBLE, outer.visibility)
    }

    @Test
    fun `invisible never resurrects a GONE view and leaves an untouched view alone`() {
        val gone = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.GONE }
        val skin = IosSkin()
        assertFalse(skin.suppress(gone))
        assertEquals(View.GONE, gone.visibility)
        skin.restore()
        assertEquals(View.GONE, gone.visibility)

        val untouched = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.VISIBLE }
        IosSkin().restore()
        assertEquals(View.VISIBLE, untouched.visibility)
    }

    // ---- oemVisible ----

    @Test
    fun `oemVisible follows the outer for alpha families`() {
        val outer = MiuiBatteryMeterView(context)
        val leaf = NamedView(context, "battery_image")
        outer.addView(leaf)

        val skin = IosSkin()
        assertTrue("no target is not a hidden stock", skin.oemVisible)
        assertTrue(skin.suppress(outer))
        assertTrue(skin.oemVisible)

        outer.visibility = View.INVISIBLE
        assertFalse(skin.oemVisible)

        outer.visibility = View.VISIBLE
        assertTrue(skin.oemVisible)

        skin.restore()
        assertTrue(skin.oemVisible)
    }

    @Test
    fun `oemVisible tracks the intended visibility for invisible-keep-slot`() {
        val outer = SingleBindableStatusBarComposeIconView(context).apply { visibility = View.VISIBLE }

        val skin = IosSkin()
        assertTrue(skin.suppress(outer))
        // Our own INVISIBLE is not the OEM's intent: the glyph must stay visible.
        assertEquals(View.INVISIBLE, outer.visibility)
        assertTrue(skin.oemVisible)

        outer.visibility = View.GONE
        skin.reassert()
        assertFalse(skin.oemVisible)

        outer.visibility = View.VISIBLE
        skin.reassert()
        assertTrue(skin.oemVisible)

        skin.restore()
        assertTrue(skin.oemVisible)
    }

    // ---- SKIP ----

    @Test
    fun `skip family changes nothing and returns false`() {
        val outer = StatIconView(context).apply {
            alpha = 0.5f
            visibility = View.VISIBLE
            layoutParams = ViewGroup.LayoutParams(10, 11)
        }
        val lp = outer.layoutParams

        val skin = IosSkin()
        assertFalse(skin.suppress(outer))
        assertFalse(skin.isSuppressing)
        assertEquals(0.5f, outer.alpha, 0f)
        assertEquals(View.VISIBLE, outer.visibility)
        assertSame(lp, outer.layoutParams)
        assertEquals(10, outer.layoutParams.width)
        assertEquals(11, outer.layoutParams.height)
    }

    @Test
    fun `unknown family changes nothing and returns false`() {
        val outer = FrameLayout(context).apply {
            alpha = 0.7f
            visibility = View.VISIBLE
        }

        val skin = IosSkin()
        assertFalse(skin.suppress(outer))
        assertEquals(0.7f, outer.alpha, 0f)
        assertEquals(View.VISIBLE, outer.visibility)
    }

    private companion object {
        private val NEXT_ID = AtomicInteger(20_000)
    }
}
