package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The IOS-REPLACE coverage matrix (R3 A5.1).
 *
 * One entry per stock icon of every V-1 device+SDK row (`.pm/IOS-INV-report.md` §1.2 rows 1-33) and
 * per family table in `.pm/IOS-REPLACE-map.md` §1-§4. The expected mechanism/fallback/leaf ids are the
 * measured decisions; a row the docs call UNVERIFIED says so in [Row.note] and asserts the safe
 * fallback (a named leaf the host presence-checks, or SKIP/stock), never an invented class or id.
 *
 * The planner is pure, so this matrix needs no Android view and runs in the unit suite.
 */
class IosReplacePlannerMatrixTest {

    private data class Row(
        val v1: Int,
        val family: IosSkinFamily,
        val className: String,
        val idName: String?,
        val kind: StockIconKind,
        val mechanism: IosReplaceMechanism,
        val fallback: IosReplaceMechanism,
        val leafIds: List<String>,
        val note: String = "",
        /**
         * False when no measured doc backs this row. Such a row may still name the generic shape, but
         * it must resolve to a safe fallback (SKIP, OVERLAY, or a named leaf the host presence-checks).
         */
        val verified: Boolean = true,
        /** Every explicit family stays itself; the generated family is the input family. */
        val expectedFamily: IosSkinFamily = family
    )

    @Test
    fun `every V-1 row and family resolves to the measured mechanism and leaf ids`() {
        val rows = rows()
        assertTrue("the matrix must not be empty", rows.isNotEmpty())
        for (row in rows) {
            val plan = IosReplacePlanner.planFor(
                row.family,
                row.className,
                row.idName,
                row.kind
            )
            val label = "V1 row ${row.v1} ${row.family} ${row.className}/${row.idName} " +
                "${row.kind} ${row.note}"
            assertEquals(label, row.expectedFamily, plan.family)
            assertEquals(label, row.mechanism, plan.mechanism)
            assertEquals(label, row.fallback, plan.fallback)
            assertEquals(label, row.leafIds, plan.leafIds)
        }
    }

    @Test
    fun `every family in the table has at least one matrix row`() {
        val covered = rows().mapTo(HashSet()) { it.family }
        for (family in IosSkinFamily.entries) {
            assertTrue("no matrix row covers $family", family in covered)
        }
    }

    /**
     * A row the docs call UNVERIFIED may name the generic, measured shape, but it must never promise
     * more than the host can prove: stock, the approved overlay, or a named leaf the host looks up and
     * falls back from. An unverified row is never allowed to invent a class/id and still be "measured".
     */
    @Test
    fun `unverified matrix rows use a safe fallback`() {
        val unverified = rows().filter { !it.verified }
        assertTrue("the matrix must keep at least one explicitly unverified row", unverified.isNotEmpty())
        for (row in unverified) {
            val plan = IosReplacePlanner.planFor(
                row.family,
                row.className,
                row.idName,
                row.kind
            )
            val label = "unverified V1 row ${row.v1} ${row.family} ${row.className}/${row.idName}"
            assertTrue(label, safeFallback(plan))
        }
    }

    /** SKIP/OVERLAY are safe; a named leaf is safe because the host presence-checks it. */
    private fun safeFallback(plan: IosReplacePlan): Boolean = when (plan.mechanism) {
        IosReplaceMechanism.SKIP,
        IosReplaceMechanism.OVERLAY -> true
        IosReplaceMechanism.IMAGE_DRAWABLE -> plan.leafIds.isNotEmpty()
        IosReplaceMechanism.OUTER_DRAW,
        IosReplaceMechanism.LEAF_DRAW -> plan.leafIds.isNotEmpty() &&
            plan.fallback != plan.mechanism
    }

    /**
     * The unproven-shape additions are additive: an unfamiliar class only gains a plan when its
     * platform shape is proven, otherwise it stays stock.
     */
    @Test
    fun `generic battery compasses the AOSP shape and never an invented leaf`() {
        val aosp = IosReplacePlanner.planFor(
            IosSkinFamily.UNKNOWN,
            "VendorBatteryMeterView",
            "battery",
            StockIconKind.BATTERY
        )
        assertEquals(IosReplaceMechanism.IMAGE_DRAWABLE, aosp.mechanism)
        assertEquals(IosReplaceMechanism.OVERLAY, aosp.fallback)
        assertEquals(listOf("battery_icon"), aosp.leafIds)

        val noLeafShape = IosReplacePlanner.planFor(
            IosSkinFamily.UNKNOWN,
            "VendorBatteryMeterView",
            "battery",
            StockIconKind.WIFI
        )
        assertEquals(IosReplaceMechanism.SKIP, noLeafShape.mechanism)
    }

    /**
     * The first-ImageView fallback is a runtime safety net for the two measured battery shapes, not
     * a blanket rule: `TrBatteryMeterView` (UNKNOWN family) and Samsung's exact `BatteryMeterView`
     * both enable it, while a normal named-leaf family must resolve its id only.
     */
    @Test
    fun `battery first-ImageView fallback applies only to the measured shapes`() {
        val tr = IosReplacePlanner.planFor(
            IosSkinFamily.UNKNOWN,
            "TrBatteryMeterView",
            "battery",
            StockIconKind.BATTERY
        )
        assertTrue("TrBatteryMeterView uses the AOSP-shaped first ImageView", tr.allowFirstImageView)

        val samsung = IosReplacePlanner.planFor(
            IosSkinFamily.SAMSUNG_BATTERY,
            "BatteryMeterView",
            "battery",
            StockIconKind.BATTERY
        )
        assertTrue("Samsung BatteryMeterView uses mBatteryIconView", samsung.allowFirstImageView)

        val oplus = IosReplacePlanner.planFor(
            IosSkinFamily.OPLUS_BATTERY,
            "StatBatteryMeterView",
            "battery",
            StockIconKind.BATTERY
        )
        assertFalse("a named-leaf family must not fall back to the first ImageView", oplus.allowFirstImageView)
    }

    /**
     * The vivo custom battery is an AOSP `BatteryMeterView` descendant by name only: its drawing leaf
     * is the custom-drawn `VivoBatteryIconBaseView.onDraw`. The guard runs before the generic AOSP
     * battery shape, so the plan is the fail-closed SKIP/stock (reason: `vivo-custom-battery`,
     * IOS-REPLACE-map §4) and never installs an ImageView drawable the custom onDraw would ignore.
     */
    @Test
    fun `vivo custom battery is fail-closed to SKIP before the generic AOSP battery shape`() {
        for (name in listOf(
            "VivoBatteryMeterView",
            "VIVOBATTERYMETERVIEW",
            "vivoBatteryMeterView",
            "VivoBatteryIconBaseView"
        )) {
            val plan = IosReplacePlanner.planFor(
                IosSkinFamily.UNKNOWN,
                name,
                "battery",
                StockIconKind.BATTERY
            )
            assertEquals(
                "$name must stay SKIP (reason: vivo-custom-battery)",
                IosReplaceMechanism.SKIP,
                plan.mechanism
            )
            assertEquals(IosSkinFamily.UNKNOWN, plan.family)
            assertTrue("$name must not name a leaf", plan.leafIds.isEmpty())
            assertFalse("$name must not use first-ImageView", plan.allowFirstImageView)
        }

        // The guard is narrow: the measured AOSP/vendor battery shapes keep the generic rule.
        for (name in listOf("BatteryMeterView", "TrBatteryMeterView", "VendorBatteryMeterView")) {
            val plan = IosReplacePlanner.planFor(
                IosSkinFamily.UNKNOWN,
                name,
                "battery",
                StockIconKind.BATTERY
            )
            assertEquals(
                "$name keeps the generic AOSP battery shape",
                IosReplaceMechanism.IMAGE_DRAWABLE,
                plan.mechanism
            )
            assertTrue("$name keeps the first-ImageView fallback", plan.allowFirstImageView)
        }
    }

    private fun rows(): List<Row> = buildList {
        // ---------------------------------------------------------------- OxygenOS 16 (V-1 rows 1-4)
        oplus(1, "OxygenOS 16 CPH2747")
        oplus(2, "OxygenOS CPH2653")
        oplus(3, "OxygenOS PKG110")
        oplus(4, "OxygenOS PLK110")
        // The 16.99.12 build reuses the same names while the signatures/ids differ: mechanisms hold.
        oplus(1, "OxygenOS 16.99.12: ids resolved at runtime; signatures UNVERIFIED")

        // ------------------------------------------------------- OxygenOS 17 / OPD2413 (V-1 row 5)
        add(
            Row(
                5, IosSkinFamily.COMPOSE_BATTERY, "ComposeView",
                "battery_composable_view", StockIconKind.BATTERY,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "OxygenOS 17 Compose battery; no View-side leaf (V-1 row 5)"
            )
        )
        add(
            Row(
                5, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "UNVERIFIED named leaf (0x0 in V-1 row 5); host presence-check -> overlay/stock",
                verified = false
            )
        )
        add(
            Row(
                5, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                null, StockIconKind.CELLULAR,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "UNVERIFIED: no mobile_combo in the status bar (V-1 row 5) -> stock",
                verified = false
            )
        )

        // ------------------------------------------------------------- HyperOS 3 (V-1 rows 6-20)
        hyperos(6, "HyperOS 3 dada 24129PN74G SDK37: SDK36 hollow leaf")
        hyperos(7, "HyperOS kunzite 25098RA98G SDK36")
        hyperos(8, "HyperOS annibale 2510DPC44G SDK36: Wi-Fi absent")
        hyperos(9, "HyperOS annibale 2510DRK44C SDK36")
        hyperos(10, "HyperOS 3 xuanyuan 25010PN30G SDK36: no named Wi-Fi view")
        hyperos(11, "HyperOS 3 onyx 25053RT47C SDK36: no named Wi-Fi view")
        hyperos(12, "HyperOS 3 nezha 2512BPNDAG SDK36")
        hyperos(13, "HyperOS diting 22081212G SDK35: Wi-Fi off")
        add(
            Row(
                14, IosSkinFamily.HYPEROS_BATTERY, "MiuiBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.LEAF_DRAW, IosReplaceMechanism.OVERLAY,
                listOf("hollow_battery_image", "battery_image"),
                "HyperOS vayu SDK33 flips to battery_image (V-1 row 14)"
            )
        )
        add(
            Row(
                14, IosSkinFamily.UNKNOWN, "StatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "Old HyperOS/AOSP class shape; state push UNVERIFIED (map §2 last row)",
                verified = false
            )
        )
        add(
            Row(
                14, IosSkinFamily.UNKNOWN, "StatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "Old HyperOS/AOSP class shape; state push UNVERIFIED (map §2 last row)",
                verified = false
            )
        )
        add(
            Row(
                15, IosSkinFamily.SAMSUNG_BATTERY, "BatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "AOSP strip battery legacy 0x0; first ImageView fallback (V-1 row 15)"
            )
        )
        add(
            Row(
                15, IosSkinFamily.COMPOSE_ICON, "SingleBindableStatusBarComposeIconView",
                null, StockIconKind.WIFI,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "Compose icons replace by overlay only (V-1 row 15)"
            )
        )
        add(
            Row(
                15, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "UNVERIFIED leaf absence on this strip; host falls back to overlay/stock",
                verified = false
            )
        )
        hyperos(16, "HyperOS 3 degas 2406APNFAG SDK36: no wifi_combo")
        hyperos(17, "HyperOS 3 fog 220333QBI SDK36: Wi-Fi off")
        hyperos(18, "HyperOS 3 lhasa 2608BPX34C SDK37: flat battery_image leaf")
        add(
            Row(
                19, IosSkinFamily.COMPOSE_BATTERY, "ComposeView",
                null, StockIconKind.BATTERY,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "UNVERIFIED id-less Compose battery host (V-1 row 19); selected as battery only",
                verified = false
            )
        )
        add(
            Row(
                19, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal")
            )
        )
        add(
            Row(
                19, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal")
            )
        )
        add(
            Row(
                20, IosSkinFamily.COMPOSE_BATTERY, "ComposeView",
                "battery_composable_view", StockIconKind.BATTERY,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "HyperOS rodin Compose battery (V-1 row 20)"
            )
        )
        add(
            Row(
                20, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "Home bar leaf absent; shade copy proves the name (map §6 UNVERIFIED 7)",
                verified = false
            )
        )
        add(
            Row(
                20, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "Home bar leaf absent; shade copy proves the name (map §6 UNVERIFIED 7)",
                verified = false
            )
        )

        // ------------------------------------------------------- HyperOS label / no tree (21-22)
        add(
            Row(
                21, IosSkinFamily.UNKNOWN, "NoTreeView",
                null, StockIconKind.BATTERY,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "V-1 row 21 has no status-bar tree -> stock"
            )
        )
        add(
            Row(
                22, IosSkinFamily.SAMSUNG_BATTERY, "BatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "Lineage gale legacy battery (V-1 row 22)"
            )
        )
        add(
            Row(
                22, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                null, StockIconKind.WIFI,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "Wi-Fi off and no named view (V-1 row 22) -> stock"
            )
        )
        add(
            Row(
                22, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "UNVERIFIED: only a shade copy shows a real size (V-1 row 22)",
                verified = false
            )
        )

        // -------------------------------------------------------------- AOSP/Pixel (23-25)
        add(
            Row(
                23, IosSkinFamily.COMPOSE_BATTERY, "ComposeView",
                null, StockIconKind.BATTERY,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "Pixel 9 Pro XL Compose battery; id-less host (V-1 row 23)"
            )
        )
        add(
            Row(
                23, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                null, StockIconKind.WIFI,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "No Wi-Fi view in the tree (V-1 row 23) -> stock"
            )
        )
        add(
            Row(
                23, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                null, StockIconKind.CELLULAR,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "No mobile view in the tree (V-1 row 23) -> stock"
            )
        )
        add(
            Row(
                24, IosSkinFamily.COMPOSE_BATTERY, "ComposeView",
                "battery_composable_view", StockIconKind.BATTERY,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "Pixel 6a bluejay Compose battery (V-1 row 24)"
            )
        )
        add(
            Row(
                24, IosSkinFamily.COMPOSE_ICON, "SingleBindableStatusBarComposeIconView",
                null, StockIconKind.WIFI,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "UNVERIFIED whether this Compose icon is Wi-Fi/cell/both (V-1 row 24)",
                verified = false
            )
        )
        add(
            Row(
                24, IosSkinFamily.COMPOSE_ICON, "SingleBindableStatusBarComposeIconView",
                null, StockIconKind.CELLULAR,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "Same Compose icon; overlay is the only in-place mechanism (V-1 row 24)"
            )
        )
        add(
            Row(
                25, IosSkinFamily.COMPOSE_BATTERY, "ComposeView",
                "battery_composable_view", StockIconKind.BATTERY,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "Nothing A059 measured Compose battery (V-1 row 25)"
            )
        )
        add(
            Row(
                25, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "UNVERIFIED: A059 has no wifi_signal id; host falls back (V-1 row 25)",
                verified = false
            )
        )
        add(
            Row(
                25, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "UNVERIFIED: A059 has no mobile_signal id; host falls back (V-1 row 25)",
                verified = false
            )
        )

        // -------------------------------------------------------------- Infinix (26-28)
        add(
            Row(
                26, IosSkinFamily.HYPEROS_BATTERY, "MiuiBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.LEAF_DRAW, IosReplaceMechanism.OVERLAY,
                listOf("hollow_battery_image", "battery_image"),
                "Infinix X6891 ships Miui battery classes (V-1 row 26)"
            )
        )
        add(
            Row(
                26, IosSkinFamily.UNKNOWN, "StatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "Old class shape; Wi-Fi off in this dump (V-1 row 26)"
            )
        )
        add(
            Row(
                26, IosSkinFamily.UNKNOWN, "StatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "Old class shape; push UNVERIFIED (V-1 row 26)",
                verified = false
            )
        )
        add(
            Row(
                27, IosSkinFamily.UNKNOWN, "TrBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "UNVERIFIED push; bare 0x0 ImageView, no battery_icon (V-1 row 27)",
                verified = false
            )
        )
        add(
            Row(
                27, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                null, StockIconKind.WIFI,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "No Wi-Fi view in the tree (V-1 row 27) -> stock"
            )
        )
        add(
            Row(
                27, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "SIM2 is the only real slot (V-1 row 27)"
            )
        )
        add(
            Row(
                28, IosSkinFamily.UNKNOWN, "TrBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "Infinix X6887 dump-proven battery_icon leaf (V-1 row 28)"
            )
        )
        add(
            Row(
                28, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "Infinix X6887 WiFi leaf proved (V-1 row 28)"
            )
        )
        add(
            Row(
                28, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "Infinix X6887 modern mobile leaf proved (V-1 row 28)"
            )
        )
        add(
            Row(
                28, IosSkinFamily.UNKNOWN, "StatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "X6887 third mobile view uses the older class (V-1 row 28)"
            )
        )

        // -------------------------------------------------------------- Samsung (29-33)
        samsung(29, "Samsung a53x SM-A5360 SDK36: no mobile view")
        samsung(30, "Samsung m3q SM-S948N SDK36")
        samsung(31, "Samsung a56x SM-A566B SDK36: Wi-Fi off")
        samsung(32, "Samsung pa3q SM-S938B SDK36/37")
        samsung(33, "Samsung b0s SM-S908B SDK36")

        // ------------------------------------------- Other families in the map coverage table
        // Motorola is AOSP-shaped on SDK35: battery first ImageView + the named Modern views.
        add(
            Row(
                0, IosSkinFamily.SAMSUNG_BATTERY, "BatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "Motorola (map §3): AOSP-shaped battery, UNVERIFIED ROM",
                verified = false
            )
        )
        add(
            Row(
                0, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "Motorola (map §3): named Wi-Fi leaf, UNVERIFIED ROM",
                verified = false
            )
        )
        add(
            Row(
                0, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "Motorola (map §3): named mobile leaf, UNVERIFIED ROM",
                verified = false
            )
        )
        // Nothing OS 15 decoded View tree reuses the AOSP shapes.
        add(
            Row(
                0, IosSkinFamily.SAMSUNG_BATTERY, "BatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "Nothing OS 15 AOSP View build (map §4); measured A059 is Compose"
            )
        )
        add(
            Row(
                0, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "Nothing OS 15 AOSP View build (map §4)"
            )
        )
        add(
            Row(
                0, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "Nothing OS 15 AOSP View build (map §4)"
            )
        )
        // vivo OriginOS (SDK36): custom self-drawing battery stays stock; only the generic named
        // Wi-Fi/mobile leaves are allowed (map §4 marks them UNVERIFIED, so the plan is the proven
        // shape and the host falls back when the leaf is absent).
        add(
            Row(
                0, IosSkinFamily.UNKNOWN, "VivoBatteryIconBaseView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "vivo custom onDraw battery has no proven leaf (map §4) -> stock"
            )
        )
        add(
            Row(
                0, IosSkinFamily.UNKNOWN, "StatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("wifi_signal"),
                "vivo generic wifi_signal swap is UNVERIFIED (map §4)",
                verified = false
            )
        )
        add(
            Row(
                0, IosSkinFamily.UNKNOWN, "StatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"),
                "vivo generic mobile_signal swap is UNVERIFIED (map §4)",
                verified = false
            )
        )
        // Honor MagicOS SDK31 and OPPO ColorOS SDK28 are below the module minSdk 33 and have no
        // measured class row in the map. There is deliberately no invented `Honor*`/`ColorOs*` row:
        // a class name nobody measured would be reported as truth and could never be checked.
        // OOS `StatIconView` draws itself and has no children: overlay only when it is a replaced kind.
        add(
            Row(
                0, IosSkinFamily.OPAQUE_STAT_ICON, "StatIconView",
                "wifi", StockIconKind.WIFI,
                IosReplaceMechanism.OVERLAY, IosReplaceMechanism.SKIP, emptyList(),
                "OxygenOS opaque StatIconView; overlay only for a replaced kind (V-1 §2.2)"
            )
        )
        add(
            Row(
                0, IosSkinFamily.OPAQUE_STAT_ICON, "StatIconView",
                "clock", StockIconKind.OTHER,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "Opaque non-replaced icon stays stock (V-1 §2.2)"
            )
        )
        // Samsung `SingleBindableStatusBarIconView` has the proven icon_view leaf.
        add(
            Row(
                0, IosSkinFamily.SAMSUNG_SBICON, "SingleBindableStatusBarIconView",
                null, StockIconKind.WIFI,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("icon_view"),
                "Samsung SBIcon icon_view leaf (V-1 §2.2)"
            )
        )
        add(
            Row(
                0, IosSkinFamily.SAMSUNG_SBICON, "SingleBindableStatusBarIconView",
                "clock", StockIconKind.OTHER,
                IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                "Non-replaced SBIcon stays stock"
            )
        )
        // A generic, unfamiliar `*BatteryMeterView` gains the same AOSP-shaped `battery_icon` leaf.
        // The exact class is not itself measured; `.pm/IOS-REPLACE-map.md` §3 proves the shape only
        // for the AOSP/Samsung `BatteryMeterView`, so this row stays UNVERIFIED and asserts the safe
        // named-leaf fallback instead of claiming a measured family.
        add(
            Row(
                0, IosSkinFamily.UNKNOWN, "VendorBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"),
                "Generic AOSP battery shape; no OEM-specific guess (UNVERIFIED)",
                verified = false
            )
        )
    }

    /** Rows 1-4 and the 16.99.12 build: the measured OPLUS composites. */
    private fun MutableList<Row>.oplus(v1: Int, note: String) {
        add(
            Row(
                v1, IosSkinFamily.OPLUS_BATTERY, "StatBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon_view"), note
            )
        )
        add(
            Row(
                v1, IosSkinFamily.OPLUS_WIFI, "OplusModernStatusBarWifiView",
                "wifi_combo", StockIconKind.WIFI,
                IosReplaceMechanism.OUTER_DRAW, IosReplaceMechanism.IMAGE_DRAWABLE,
                listOf("wifi_signal"), note
            )
        )
        add(
            Row(
                v1, IosSkinFamily.OPLUS_MOBILE, "OplusModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.OUTER_DRAW, IosReplaceMechanism.IMAGE_DRAWABLE,
                listOf("mobile_signal"), note
            )
        )
    }

    /**
     * One HyperOS row: the battery is always `MiuiBatteryMeterView`; Wi-Fi is either the named
     * `ModernStatusBarWifiView` (absent/off in rows where the dump has none) or stock.
     */
    private fun MutableList<Row>.hyperos(v1: Int, note: String) {
        add(
            Row(
                v1, IosSkinFamily.HYPEROS_BATTERY, "MiuiBatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.LEAF_DRAW, IosReplaceMechanism.OVERLAY,
                listOf("hollow_battery_image", "battery_image"), note
            )
        )
        if (note.contains("absent") || note.contains("no named") || note.contains("no wifi")) {
            add(
                Row(
                    v1, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                    null, StockIconKind.WIFI,
                    IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                    "$note; UNVERIFIED which view draws Wi-Fi -> stock",
                    verified = false
                )
            )
        } else if (note.contains("Wi-Fi off")) {
            add(
                Row(
                    v1, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                    null, StockIconKind.WIFI,
                    IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                    "$note; no icon to replace -> stock"
                )
            )
        } else {
            add(
                Row(
                    v1, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                    "wifi_combo", StockIconKind.WIFI,
                    IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                    listOf("wifi_signal"), note
                )
            )
        }
        add(
            Row(
                v1, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                "mobile_combo", StockIconKind.CELLULAR,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("mobile_signal"), note
            )
        )
    }

    /** One Samsung row: battery + Modern Wi-Fi/mobile; Wi-Fi off/invalid dumps stay stock. */
    private fun MutableList<Row>.samsung(v1: Int, note: String) {
        add(
            Row(
                v1, IosSkinFamily.SAMSUNG_BATTERY, "BatteryMeterView",
                "battery", StockIconKind.BATTERY,
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                listOf("battery_icon"), note
            )
        )
        if (note.contains("Wi-Fi off")) {
            add(
                Row(
                    v1, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                    null, StockIconKind.WIFI,
                    IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                    "$note; no Wi-Fi icon -> stock"
                )
            )
        } else {
            add(
                Row(
                    v1, IosSkinFamily.HYPEROS_WIFI, "ModernStatusBarWifiView",
                    "wifi_combo", StockIconKind.WIFI,
                    IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                    listOf("wifi_signal"), "$note (Samsung shares the Modern view class)"
                )
            )
        }
        if (note.contains("no mobile")) {
            add(
                Row(
                    v1, IosSkinFamily.UNKNOWN, "StatusBarIconView",
                    null, StockIconKind.CELLULAR,
                    IosReplaceMechanism.SKIP, IosReplaceMechanism.SKIP, emptyList(),
                    "$note; no mobile view -> stock"
                )
            )
        } else {
            add(
                Row(
                    v1, IosSkinFamily.HYPEROS_MOBILE, "ModernStatusBarMobileView",
                    "mobile_combo", StockIconKind.CELLULAR,
                    IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.OVERLAY,
                    listOf("mobile_signal"), "$note (Samsung shares the Modern view class)"
                )
            )
        }
    }
}
