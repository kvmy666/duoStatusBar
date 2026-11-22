package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure replacement table in [IosReplacePlanner]: every measured row, the two unmeasured-backed
 * special classes, UNKNOWN and the ignored/skip cases.
 *
 * No Android view is created here; this is the test seam the runtime passes its measured family/kind
 * through, so the table is pinned without a device.
 */
class IosReplacePlannerTest {

    // ------------------------------------------------------------------------------- OPLUS rows

    @Test
    fun `oplus battery replaces the leaf and hides the two bolt siblings`() {
        val plan = plan(IosSkinFamily.OPLUS_BATTERY, "StatBatteryMeterView", StockIconKind.BATTERY)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, plan.fallback)
        assertEquals(listOf("battery_icon_view"), plan.leafIds)
        assertEquals(listOf("battery_charge_icon", "battery_charge_reverse"), plan.siblingIds)
        assertTrue(plan.hookClasses.isEmpty())
        assertNull(plan.drawMethod)
        assertNull(plan.transXField)
    }

    @Test
    fun `oplus wifi and mobile use the proven outer draw with a leaf fallback`() {
        val wifi = plan(
            IosSkinFamily.OPLUS_WIFI,
            "OplusModernStatusBarWifiView",
            StockIconKind.WIFI
        )
        assertEquals(IosReplaceMechanism.OUTER_DRAW, wifi.mechanism)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, wifi.fallback)
        assertEquals(listOf("wifi_signal"), wifi.leafIds)
        assertTrue(wifi.siblingIds.isEmpty())
        assertEquals(listOf("wifi_left", "wifi_inout"), wifi.fallbackSiblingIds)
        assertEquals("dispatchDraw", wifi.drawMethod)
        assertEquals("transXForCoord", wifi.transXField)

        val mobile = plan(
            IosSkinFamily.OPLUS_MOBILE,
            "OplusModernStatusBarMobileView",
            StockIconKind.CELLULAR
        )
        assertEquals(IosReplaceMechanism.OUTER_DRAW, mobile.mechanism)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, mobile.fallback)
        assertEquals(listOf("mobile_signal"), mobile.leafIds)
        assertTrue(mobile.siblingIds.isEmpty())
        assertEquals(
            listOf(
                "mobile_type",
                "data_inout",
                "mobile_roaming",
                "mobile_volte",
                "mobile_in",
                "mobile_out"
            ),
            mobile.fallbackSiblingIds
        )
        assertEquals("dispatchDraw", mobile.drawMethod)
        assertEquals("transXForCoord", mobile.transXField)
    }

    // ------------------------------------------------------------------------------- HYPEROS rows

    @Test
    fun `hyperos battery hooks both custom leaf classes on their proven onDraw`() {
        val plan = plan(IosSkinFamily.HYPEROS_BATTERY, "MiuiBatteryMeterView", StockIconKind.BATTERY)
        assertEquals(IosReplaceMechanism.LEAF_DRAW, plan.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, plan.fallback)
        assertEquals(listOf("hollow_battery_image", "battery_image"), plan.leafIds)
        assertEquals(listOf("battery_charge_out_image"), plan.siblingIds)
        assertEquals(
            listOf(
                "com.android.systemui.statusbar.views.MiuiHollowBatteryMeterIconView",
                "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView"
            ),
            plan.hookClasses
        )
        assertEquals("onDraw", plan.drawMethod)
        assertNull(plan.transXField)
    }

    @Test
    fun `hyperos wifi and mobile replace the image leaf and blank their siblings`() {
        val wifi = plan(IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView", StockIconKind.WIFI)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, wifi.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, wifi.fallback)
        assertEquals(listOf("wifi_signal"), wifi.leafIds)
        assertEquals(
            listOf("wifi_activity", "wifi_standard", "vowifi", "inout_container"),
            wifi.siblingIds
        )

        val mobile = plan(
            IosSkinFamily.HYPEROS_MOBILE,
            "ModernStatusBarMobileView",
            StockIconKind.CELLULAR
        )
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, mobile.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, mobile.fallback)
        assertEquals(listOf("mobile_signal"), mobile.leafIds)
        assertEquals(
            listOf(
                "mobile_type",
                "mobile_left_mobile_inout",
                "mobile_special_5G",
                "mobile_satellite",
                "mobile_vowifi",
                "mobile_type_container",
                "inout_container",
                "mobile_roaming",
                "mobile_volte"
            ),
            mobile.siblingIds
        )
    }

    // ------------------------------------------------------------------------------ SAMSUNG rows

    @Test
    fun `samsung battery and icon view replace their measured leaf`() {
        val battery = plan(IosSkinFamily.SAMSUNG_BATTERY, "BatteryMeterView", StockIconKind.BATTERY)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, battery.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, battery.fallback)
        assertEquals(listOf("battery_icon"), battery.leafIds)
        assertTrue(battery.allowFirstImageView)

        val icon = plan(
            IosSkinFamily.SAMSUNG_SBICON,
            "SingleBindableStatusBarIconView",
            StockIconKind.WIFI
        )
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, icon.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, icon.fallback)
        assertEquals(listOf("icon_view"), icon.leafIds)
    }

    @Test
    fun `a non-replaced samsung icon view is skipped`() {
        val plan = plan(
            IosSkinFamily.SAMSUNG_SBICON,
            "SingleBindableStatusBarIconView",
            StockIconKind.OTHER
        )
        assertEquals(IosReplaceMechanism.SKIP, plan.mechanism)
    }

    // ----------------------------------------------------------------------- Compose / opaque rows

    @Test
    fun `compose and opaque families use the approved overlay fallback`() {
        val composeBattery = IosReplacePlanner.planFor(
            IosSkinFamily.COMPOSE_BATTERY,
            "androidx.compose.ui.platform.ComposeView",
            "battery_composable_view",
            StockIconKind.BATTERY
        )
        assertEquals(IosReplaceMechanism.OVERLAY, composeBattery.mechanism)
        assertEquals(IosReplaceMechanism.SKIP, composeBattery.fallback)
        assertTrue(composeBattery.leafIds.isEmpty())

        val composeIcon = plan(
            IosSkinFamily.COMPOSE_ICON,
            "SingleBindableStatusBarComposeIconView",
            StockIconKind.CELLULAR
        )
        assertEquals(IosReplaceMechanism.OVERLAY, composeIcon.mechanism)

        val opaque = plan(IosSkinFamily.OPAQUE_STAT_ICON, "StaticIconView", StockIconKind.WIFI)
        assertEquals(IosReplaceMechanism.OVERLAY, opaque.mechanism)
        assertEquals(IosReplaceMechanism.SKIP, opaque.fallback)
    }

    // --------------------------------------------------------------------- special class fallbacks

    @Test
    fun `infinix battery class is the dump-proven battery icon leaf`() {
        val plan = plan(IosSkinFamily.UNKNOWN, "TrBatteryMeterView", StockIconKind.BATTERY)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, plan.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, plan.fallback)
        assertEquals(listOf("battery_icon"), plan.leafIds)
        // The shape enables the first-ImageView fallback; it does not relabel a vendor subclass as
        // Samsung. The measured family stays UNKNOWN.
        assertEquals(IosSkinFamily.UNKNOWN, plan.family)
        assertTrue(plan.allowFirstImageView)
    }

    @Test
    fun `older status bar wifi and mobile classes replace their named leaves`() {
        val wifi = plan(IosSkinFamily.UNKNOWN, "StatusBarWifiView", StockIconKind.WIFI)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, wifi.mechanism)
        assertEquals(listOf("wifi_signal"), wifi.leafIds)
        assertEquals(listOf("wifi_activity"), wifi.siblingIds)

        val mobile = plan(IosSkinFamily.UNKNOWN, "StatusBarMobileView", StockIconKind.CELLULAR)
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, mobile.mechanism)
        assertEquals(listOf("mobile_signal"), mobile.leafIds)
        assertEquals(listOf("mobile_type", "mobile_left_mobile_inout"), mobile.siblingIds)
    }

    @Test
    fun `the oplus composite is not mistaken for the older status bar class`() {
        // `OplusModernStatusBarWifiView` contains `StatusBarWifiView`; the measured OPLUS family must
        // win because its proven mechanism is the composite dispatchDraw.
        val plan = plan(
            IosSkinFamily.OPLUS_WIFI,
            "OplusModernStatusBarWifiView",
            StockIconKind.WIFI
        )
        assertEquals(IosReplaceMechanism.OUTER_DRAW, plan.mechanism)
        assertTrue(plan.siblingIds.isEmpty())
    }

    // ------------------------------------------------------------------ unknown / ignored families

    @Test
    fun `an unmeasured family or class is skipped`() {
        assertEquals(
            IosReplaceMechanism.SKIP,
            plan(IosSkinFamily.UNKNOWN, "SomeVendorSignalView", StockIconKind.WIFI).mechanism
        )
        assertEquals(
            IosReplaceMechanism.SKIP,
            plan(IosSkinFamily.UNKNOWN, "ClockView", StockIconKind.OTHER).mechanism
        )
    }

    @Test
    fun `an ignored kind never yields a plan`() {
        for (family in IosSkinFamily.entries) {
            val plan = plan(family, "SomeView", StockIconKind.IGNORED)
            assertEquals(family.name, IosReplaceMechanism.SKIP, plan.mechanism)
        }
    }

    @Test
    fun `a special class with the wrong kind is skipped`() {
        assertEquals(
            IosReplaceMechanism.SKIP,
            plan(IosSkinFamily.UNKNOWN, "TrBatteryMeterView", StockIconKind.WIFI).mechanism
        )
        assertEquals(
            IosReplaceMechanism.SKIP,
            plan(IosSkinFamily.UNKNOWN, "StatusBarWifiView", StockIconKind.BATTERY).mechanism
        )
    }

    @Test
    fun `percentage digit and status dot ids are never a leaf or sibling`() {
        val forbidden = listOf("battery_percentage", "battery_digit", "status_bar_dot")
        for (family in IosSkinFamily.entries) {
            for (kind in listOf(StockIconKind.BATTERY, StockIconKind.WIFI, StockIconKind.CELLULAR)) {
                val plan = plan(family, "MeasuredView", kind)
                for (id in plan.leafIds + plan.siblingIds) {
                    for (token in forbidden) {
                        assertTrue("$family/$kind leaked $id", !id.lowercase().contains(token))
                    }
                }
            }
        }
    }

    private fun plan(
        family: IosSkinFamily,
        className: String,
        kind: StockIconKind
    ): IosReplacePlan = IosReplacePlanner.planFor(family, className, idName = null, kind = kind)
}
