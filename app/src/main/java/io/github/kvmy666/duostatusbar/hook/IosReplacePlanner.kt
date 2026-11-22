package io.github.kvmy666.duostatusbar.hook

import android.view.View

/**
 * How one measured stock icon is replaced in place (IOS-REPLACE).
 *
 * [IMAGE_DRAWABLE] installs our [IosGlyphDrawable] on an `ImageView` leaf; [OUTER_DRAW] hooks the
 * composite outer's proven `dispatchDraw`; [LEAF_DRAW] hooks a custom drawing leaf's `onDraw`;
 * [OVERLAY] is the approved hide-in-place + [IosCanvasView] fallback; [SKIP] leaves the stock drawing
 * untouched. See the measured replacement map (device dumps) §0.
 */
internal enum class IosReplaceMechanism { IMAGE_DRAWABLE, OUTER_DRAW, LEAF_DRAW, OVERLAY, SKIP }

/**
 * One pure replacement decision: family + mechanism + the measured names the runtime resolves.
 *
 * [leafIds] and [siblingIds] are resource entry names, never hex ids: the same layout ships with
 * different `0x7f…` values on different builds (A1 UNVERIFIED 1, A2 §1).
 *
 * [hookClasses] is only used by [IosReplaceMechanism.LEAF_DRAW] (fully-qualified leaf classes) and
 * [drawMethod] by `LEAF_DRAW`/`OUTER_DRAW`. [transXField] is the measured OPLUS composite field the
 * outer draw replicates; a missing field is not fatal and falls back to no translation (A1 §UNVERIFIED 10).
 */
internal data class IosReplacePlan(
    val family: IosSkinFamily,
    val mechanism: IosReplaceMechanism,
    val fallback: IosReplaceMechanism = IosReplaceMechanism.SKIP,
    val leafIds: List<String> = emptyList(),
    val siblingIds: List<String> = emptyList(),
    /**
     * Siblings to blank only when an [IosReplaceMechanism.OUTER_DRAW] record falls back to the leaf
     * swap. The OPLUS composite draws its in/out/type siblings in the same outer, so a leaf-only
     * fallback would leave them over our glyph (A1 §wifi/mobile).
     */
    val fallbackSiblingIds: List<String> = emptyList(),
    val hookClasses: List<String> = emptyList(),
    val drawMethod: String? = null,
    val transXField: String? = null,
    /**
     * True when a missing named battery leaf may fall back to the outer's first `ImageView`
     * (`mBatteryIconView` has no id). This is a shape fact, not a family fact: the AOSP-shaped
     * `*BatteryMeterView` family keeps its measured family name (UNKNOWN for a vendor subclass).
     */
    val allowFirstImageView: Boolean = false
)

/**
 * The replacement table, copied from the measured evidence in the measured Oplus/HyperOS device dumps
 * (merged in the measured replacement map, device dumps, §1-§3).
 *
 * The [planFor] overload taking Android types only reads the outer view and delegates to the pure
 * string/kind overload, so every row can be pinned by a unit test without a device. A family or class
 * that was not measured returns [IosReplaceMechanism.SKIP]; nothing is guessed.
 */
internal object IosReplacePlanner {

    private const val MIUI_HOLLOW_BATTERY =
        "com.android.systemui.statusbar.views.MiuiHollowBatteryMeterIconView"
    private const val MIUI_BATTERY =
        "com.android.systemui.statusbar.views.MiuiBatteryMeterIconView"

    /** Reads [outer]'s measured anatomy and returns its replacement plan. Never changes the view. */
    fun planFor(outer: View): IosReplacePlan = try {
        val family = IosSkinPlanner.planFor(outer).family
        val className = outer.javaClass.simpleName.ifBlank { outer.javaClass.name }
        val idName = if (outer.id == View.NO_ID) {
            null
        } else {
            try {
                outer.resources.getResourceEntryName(outer.id)
            } catch (_: Throwable) {
                null
            }
        }
        val kind = try {
            StockIconKey.kindOf(outer)
        } catch (_: Throwable) {
            StockIconKind.OTHER
        }
        planFor(family, className, idName, kind)
    } catch (_: Throwable) {
        IosReplacePlan(IosSkinFamily.UNKNOWN, IosReplaceMechanism.SKIP)
    }

    /**
     * The test seam: [family] comes from [IosSkinPlanner], [className] is the outer's simple name,
     * [idName] its resource entry name and [kind] the stock icon kind.
     *
     * Special class rows are consulted only when [IosSkinPlanner] returned [IosSkinFamily.UNKNOWN]:
     * `OplusModernStatusBarWifiView` also *contains* `StatusBarWifiView`, and its measured mechanism is
     * the composite `OUTER_DRAW`, not the older leaf swap.
     */
    internal fun planFor(
        family: IosSkinFamily,
        className: String,
        idName: String?,
        kind: StockIconKind
    ): IosReplacePlan {
        // A container / dot / inner signal is not a drawing icon: never invent a plan for it.
        if (kind == StockIconKind.IGNORED) return skip(family)

        val cls = className.lowercase()
        val id = idName?.lowercase()

        if (family == IosSkinFamily.UNKNOWN) {
            // Generic, shape-proven rows for a family the skin planner did not recognise. Each shape
            // is one the decoded AOSP/OEM trees prove (IOS-REPLACE-map §1-§3): an OPLUS/HyperOS-shaped
            // named ImageView leaf, an AOSP `*BatteryMeterView` whose first ImageView is the drawing
            // leaf, or an opaque Compose/stat icon. Anything with no proven shape stays SKIP/stock.
            val composeBattery = id == "battery_composable_view" ||
                (cls.contains("composeview") &&
                    (id?.contains("battery") == true || kind == StockIconKind.BATTERY))
            return when {
                // `TrBatteryMeterView` (Infinix X6887) and the AOSP-shaped `BatteryMeterView`
                // descendants share the dump-proven `battery_icon` entry name. Keep the measured
                // family (UNKNOWN for a vendor subclass) and let the shape flag, not the family,
                // enable the runtime's first-ImageView fallback (`mBatteryIconView` has no id).
                cls.contains("batterymeterview") && kind == StockIconKind.BATTERY ->
                    image(family, listOf("battery_icon"), allowFirstImageView = true)
                cls.contains("statusbarwifiview") && kind == StockIconKind.WIFI ->
                    image(family, listOf("wifi_signal"), listOf("wifi_activity"))
                cls.contains("statusbarmobileview") && kind == StockIconKind.CELLULAR ->
                    image(
                        family,
                        listOf("mobile_signal"),
                        listOf("mobile_type", "mobile_left_mobile_inout")
                    )
                // Opaque Compose hosts expose no View-side drawing leaf; the approved overlay is the
                // only in-place mechanism (IOS-INV §2.4). Never overlay a kind we do not replace.
                cls.contains("singlebindablestatusbarcomposeiconview") ||
                    composeBattery ->
                    if (kind.replaced) overlay(family) else skip(family)
                // A self-drawing opaque icon has no leaf to swap; overlay only when it is one of ours.
                cls.contains("singlebindablestatusbariconview") ||
                    cls.contains("staticonview") ->
                    if (kind.replaced) overlay(family) else skip(family)
                else -> skip(family)
            }
        }

        return when (family) {
            IosSkinFamily.OPLUS_BATTERY -> image(
                family,
                listOf("battery_icon_view"),
                listOf("battery_charge_icon", "battery_charge_reverse")
            )
            IosSkinFamily.OPLUS_WIFI -> outerDraw(
                family,
                leafId = "wifi_signal",
                method = "dispatchDraw",
                transXField = "transXForCoord",
                fallbackSiblingIds = listOf("wifi_left", "wifi_inout")
            )
            IosSkinFamily.OPLUS_MOBILE -> outerDraw(
                family,
                leafId = "mobile_signal",
                method = "dispatchDraw",
                transXField = "transXForCoord",
                fallbackSiblingIds = listOf(
                    "mobile_type",
                    "data_inout",
                    "mobile_roaming",
                    "mobile_volte",
                    "mobile_in",
                    "mobile_out"
                )
            )
            IosSkinFamily.HYPEROS_BATTERY -> IosReplacePlan(
                family = family,
                mechanism = IosReplaceMechanism.LEAF_DRAW,
                fallback = IosReplaceMechanism.OVERLAY,
                leafIds = listOf("hollow_battery_image", "battery_image"),
                siblingIds = listOf("battery_charge_out_image"),
                hookClasses = listOf(MIUI_HOLLOW_BATTERY, MIUI_BATTERY),
                drawMethod = "onDraw"
            )
            IosSkinFamily.HYPEROS_WIFI -> image(
                family,
                listOf("wifi_signal"),
                // C1 delta from the map owner: the in/out and standard/voWiFi siblings are hidden
                // only when present; every id is still resolved by name at runtime.
                listOf("wifi_activity", "wifi_standard", "vowifi", "inout_container")
            )
            IosSkinFamily.HYPEROS_MOBILE -> image(
                family,
                listOf("mobile_signal"),
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
                )
            )
            // `mBatteryIconView` is the public final ImageView child; `battery_icon` is the entry name
            // measured on the AOSP-like dumps when it exists, with the first ImageView as the runtime
            // fallback (the measured replacement map, device dumps, §3).
            IosSkinFamily.SAMSUNG_BATTERY ->
                image(family, listOf("battery_icon"), allowFirstImageView = true)
            IosSkinFamily.SAMSUNG_SBICON ->
                if (kind.replaced) image(family, listOf("icon_view")) else skip(family)
            IosSkinFamily.COMPOSE_BATTERY,
            IosSkinFamily.COMPOSE_ICON,
            IosSkinFamily.OPAQUE_STAT_ICON ->
                if (kind.replaced) overlay(family) else skip(family)
            IosSkinFamily.UNKNOWN -> skip(family)
        }
    }

    private fun image(
        family: IosSkinFamily,
        leafIds: List<String>,
        siblingIds: List<String> = emptyList(),
        allowFirstImageView: Boolean = false
    ): IosReplacePlan = IosReplacePlan(
        family = family,
        mechanism = IosReplaceMechanism.IMAGE_DRAWABLE,
        fallback = IosReplaceMechanism.OVERLAY,
        leafIds = leafIds,
        siblingIds = siblingIds,
        allowFirstImageView = allowFirstImageView
    )

    private fun outerDraw(
        family: IosSkinFamily,
        leafId: String,
        method: String,
        transXField: String,
        fallbackSiblingIds: List<String> = emptyList()
    ): IosReplacePlan = IosReplacePlan(
        family = family,
        mechanism = IosReplaceMechanism.OUTER_DRAW,
        fallback = IosReplaceMechanism.IMAGE_DRAWABLE,
        leafIds = listOf(leafId),
        fallbackSiblingIds = fallbackSiblingIds,
        drawMethod = method,
        transXField = transXField
    )

    private fun overlay(family: IosSkinFamily): IosReplacePlan = IosReplacePlan(
        family = family,
        mechanism = IosReplaceMechanism.OVERLAY,
        fallback = IosReplaceMechanism.SKIP
    )

    private fun skip(family: IosSkinFamily): IosReplacePlan =
        IosReplacePlan(family, IosReplaceMechanism.SKIP)
}
