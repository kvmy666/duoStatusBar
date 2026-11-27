package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.LogOnce

/**
 * What a ROM is called and where its status-bar icon strip lives (FR-01/02).
 *
 * The project's rule is that nothing ROM-specific is guessed: every id here is either **measured on a
 * device** or explicitly marked unverified, and [RomAdapter.notes] says which. The runtime additionally
 * logs every id it probes and what it found (`container <name> -> <class or null>`), so an unsupported ROM
 * produces evidence to add an adapter from — not silence.
 *
 * The AOSP spelling comes first in every list on purpose: it is the name the module targets, and a ROM that
 * keeps it works without a ROM-specific entry at all.
 */
internal data class RomAdapter(
    val id: String,
    val label: String,
    val systemUiPackage: String,
    /** Ordered probes for the icon strip; the first that resolves to a view wins. */
    val containerIds: List<String>,
    /** The battery view, used only to measure the slot the element replaces. */
    val batteryId: String,
    /** The status-bar clock, used only to swap its typeface (the module never hides it). */
    val clockId: String = "clock",
    /** Where the facts came from — "measured" is a device, "unverified" is a guess to be replaced. */
    val notes: String,
    /**
     * SDK gate for measured profiles.
     */
    val apiMin: Int = 0,
    val apiMax: Int = Int.MAX_VALUE,
    /**
     * Lowercase substrings of the build identity that select this profile from an asset. Empty for the
     * code defaults, so only profiles loaded from data (see [RomProfiles]) carry a match.
     */
    val match: List<String> = emptyList(),
    /** True only when every id was read off a device. Asset data may override only when measured. */
    val measured: Boolean = false,
    /** Ordered probes for the shade/QS icon strip; empty means no shade mapping is configured. */
    val shadeContainerIds: List<String> = emptyList(),
    /** The shade/QS battery view; empty means no shade battery is configured. */
    val shadeBatteryId: String = "",
    /** True only when the shade ids were read off a device. */
    val shadeMeasured: Boolean = false,
    /**
     * Whether the exact `onShadeOrQsExpanded(Boolean)` cover hook is present on this UI (Phase-1
     * exact-token audit). Defaults to true to preserve prior behaviour.
     */
    val coverHook: Boolean = true,
    /**
     * How panel expansion is reported on this UI. One of: `panel_type_5arg`, `fzz_3arg`,
     * `zfz_3arg`, `listener`, `none`, `unknown`.
     *
     * Only `panel_type_5arg` carries a panel-type argument and can own QS vs shade. Every other
     * value (`fzz_3arg`, `zfz_3arg`, `listener`, ...) is a cover/expansion signal only and must
     * not be treated as a QS-vs-shade owner.
     */
    val panelExpansionKind: String = "panel_type_5arg",
    /** True only when [panelExpansionKind] was confirmed on a device. */
    val panelHookMeasured: Boolean = false,
    /**
     * Whether this UI exposes a panel owner that can distinguish QS from the full notification
     * shade (i.e. a 5-arg `onPanelExpansionChanged` with a `PanelType`). `true` only for
     * measured OnePlus/OxygenOS; `false` for AOSP and every other UI. Non-owners may still
     * report cover/expansion via [panelExpansionKind], but never QS-vs-shade ownership.
     */
    val panelOwnerAvailable: Boolean = false,
    /**
     * Legacy ids that still name this family (asset metadata only); keep the canonical [id] everywhere.
     * Runtime canonicalisation uses `RomFamilies.canonical`, never this list.
     */
    val aliases: List<String> = emptyList(),
    /**
     * Vendor signal-cluster containers that sit beside the icon strip and must be hidden directly.
     *
     * Some ROMs keep the Wi-Fi/cellular cluster outside `system_icons` (Honor `system_icon_area_fixed`,
     * ColorOS `notification_icon_area`), so the strip hide pass never reaches them. These are hidden
     * without sizing or fitting: the layouts are shared parents, so only visibility is changed.
     */
    val hideContainerIds: List<String> = emptyList(),
    /**
     * Vendor ids of a dual-SIM composite that must stay visible.
     *
     * Motorola's `mobile_combo_dual` is one CELLULAR view holding `mobile_group_slot1` and
     * `mobile_group_slot2`; hiding it as "one SIM" would remove both rows. The keys are the stable
     * `id:<name>` form of [StockIconKey.baseKey], so the hider can keep the whole composite.
     */
    val dualSimCompositeIds: List<String> = emptyList(),
    /**
     * The id of the keyguard bar's icon strip. The AOSP `system_icons` is the default; HyperOS's
     * keyguard layout include overrides it to `system_icons_container`.
     */
    val keyguardContainerId: String = "system_icons",
    /**
     * Ordered probes for the shade/QS battery view. Vendor rows such as vivo's QS status container use
     * their own battery ids, tried before the generic [shadeBatteryId].
     */
    val shadeBatteryIds: List<String> = emptyList(),
    /**
     * The runtime marker that selected this adapter (`class:<fqcn>` or `id:<name>`), or "" for a
     * brand/measured/AOSP adapter. Report-visible only: it never changes drawing or id resolution.
     */
    val marker: String = ""
)

/**
 * Picks the adapter from build identity, as a pure function so the choice itself is unit-tested instead of
 * being debugged on a phone.
 */
internal object RomDetection {

    internal const val AOSP = "com.android.systemui"

    /** The exact phrase the runtime log and diagnostic dump carry for an unproven brand match (R18). */
    private const val BRAND_MATCH_PREFIX = "brand-match (unverified): "

    /**
     * R17D: the exact prefix a marker-proven ColorOS note carries. It is kept beside the brand prefix so
     * the "unverified" rule never prepends `brand-match` to a marker proof: a runtime marker is positive
     * family evidence, not a brand guess.
     */
    private const val OPLUS_MARKER_PREFIX = "family=oplus via marker "

    /** The canonical family ids, exposed so tests can prove the shipped asset never drifts. */
    internal val FAMILY_IDS = RomFamilies.CANONICAL_IDS

    fun forThisRom(
        manufacturer: String,
        brand: String,
        product: String,
        display: String,
        api: Int = 0,
        markers: RomMarkers = RomMarkers.UNKNOWN
    ): RomAdapter {
        val haystack = haystack(manufacturer, brand, product, display)
        val oplusFamily = haystack.contains("oneplus") || haystack.contains("oxygen") ||
            haystack.contains("oppo") || haystack.contains("oplus") ||
            haystack.contains("coloros") || haystack.contains("realme")
        val selected = when {
            haystack.contains("samsung") -> samsung()
            // R17D: the Oplus classes/ids are the only positive proof that a SystemUI whose APK path and
            // application class look like AOSP is the Oplus family (realme RMX5011, OnePlus PLK110). A
            // OnePlus/Oxygen token keeps the unchanged OxygenOS adapter; every other Oplus-family token
            // gets the marker ColorOS data. A realme token *without* a marker falls through to AOSP.
            oplusFamily && markers.hasOplusMarker ->
                if (haystack.contains("oneplus") || haystack.contains("oxygen")) {
                    oxygenOs(api, haystack)
                } else {
                    colorOsMarker(markers.oplusMarker)
                }
            haystack.contains("oneplus") || haystack.contains("oxygen") -> oxygenOs(api, haystack)
            haystack.contains("oppo") || haystack.contains("oplus") || haystack.contains("coloros") ->
                colorOs()
            haystack.contains("xiaomi") || haystack.contains("redmi") || haystack.contains("poco") ->
                // The brand alone is not proof of HyperOS, but it is also not proof of AOSP: LineageOS
                // whyred and AOSPA sweet keep the Xiaomi/Redmi brand and are AOSP-based (evidence:
                // .pm/evidence/duo-report-20261110-201707/diagnostics.txt:11-14 and
                // duo-log-20261115-214854.txt:22-25). Only a *successful* SystemUI inspection that finds
                // no MIUI marker proves non-MIUI; an unreadable inspection keeps the old brand rule.
                if (markers.provesNonMiui) aosp() else hyperOs(api, markers)
            haystack.contains("vivo") || haystack.contains("iqoo") || haystack.contains("origin") ||
                haystack.contains("funtouch") -> vivo()
            haystack.contains("honor") || haystack.contains("magic") -> honor()
            haystack.contains("motorola") || haystack.contains("moto") || haystack.contains("myos") ->
                motorola()
            haystack.contains("nothing") -> nothing()
            else -> aosp()
        }
        // A brand-selected vendor adapter that no marker proved must say so, once, in the notes that
        // reach the runtime log and the diagnostic dump. The AOSP default is not a brand match, and an
        // adapter that already carries either proof prefix (brand or Oplus marker) is not prefixed twice.
        return if (selected.id != "aosp" && !selected.measured &&
            !selected.notes.startsWith(BRAND_MATCH_PREFIX) &&
            !selected.notes.startsWith(OPLUS_MARKER_PREFIX)
        ) {
            selected.copy(notes = BRAND_MATCH_PREFIX + selected.notes)
        } else {
            selected
        }
    }

    /**
     * The same choice, but a **measured** profile loaded from data (see [RomProfiles]) is accepted
     * first. Only `measured` profiles may override, so a data edit can add a device we have read but
     * can never silently replace an unverified guess — the code stays the source of truth otherwise.
     * Kept as an overload so the pure four-argument function (and every test on it) is unchanged.
     *
     * R18: a measured profile is accepted only when a proven family marker exists
     * ([RomMarkers.provesFamily]). The shipped measured OxygenOS profile is therefore **not** selected —
     * OxygenOS is deliberately not marker-proven (the Oplus marker does not claim it) — and the code
     * OxygenOS adapter (same ids) is used, so `source` stays `code-default`. The code adapter itself
     * reports `measured=true`
     * only on the measured CPH2747 identity within the SDK 36 window; the family proof there is the
     * identity plus the SDK window, not a marker. [RomAdapter.measured], [RomAdapter.shadeMeasured] and
     * the `panel*` flags are report/profile-selection metadata only; they do not change drawing.
     */
    fun forThisRom(
        manufacturer: String,
        brand: String,
        product: String,
        display: String,
        profiles: List<RomAdapter>,
        api: Int = 0,
        markers: RomMarkers = RomMarkers.UNKNOWN
    ): RomAdapter {
        val haystack = haystack(manufacturer, brand, product, display)
        profiles.firstOrNull { profile ->
            profile.measured && markers.provesFamily(profile.id) &&
                apiInRange(profile.apiMin, profile.apiMax, api) &&
                // Custom AOSPA/Lineage/Infinity builds keep the Xiaomi brand and would otherwise take
                // the measured HyperOS asset profile (evidence: .pm/evidence/duo-log-20261115-214854.txt:22-25).
                // Gate the measured HyperOS profile on the same proof as the code branch above: skip it
                // only when a successful read proved a non-MIUI SystemUI (UNKNOWN keeps the old rule).
                (profile.id != "hyperos" || !markers.provesNonMiui) &&
                profile.match.any { haystack.contains(it.lowercase()) }
        }?.let { return it }
        return forThisRom(manufacturer, brand, product, display, api, markers)
    }

    /**
     * Convenience overload: SDK first, then profiles. Allows callers/tests that pass the SDK before
     * the profile list.
     */
    fun forThisRom(
        manufacturer: String,
        brand: String,
        product: String,
        display: String,
        api: Int,
        profiles: List<RomAdapter>,
        markers: RomMarkers = RomMarkers.UNKNOWN
    ): RomAdapter = forThisRom(manufacturer, brand, product, display, profiles, api, markers)

    private fun haystack(manufacturer: String, brand: String, product: String, display: String): String =
        listOf(manufacturer, brand, product, display).joinToString(" ").lowercase()

    /** SDK gate helper for measured profiles; api == 0 means legacy/tests (any SDK). */
    fun apiInRange(min: Int, max: Int, api: Int): Boolean = api == 0 || (api in min..max)

    /**
     * Recognised from the OnePlus/OxygenOS brand; measured **only on the measured CPH2747 identity
     * inside the SDK 36 window** (R18 correction).
     *
     * CPH2747 has `sourceDir=/system_ext/priv-app/SystemUI/SystemUI.apk` and application class
     * `com.android.systemui.SystemUIApplication` (evidence:
     * `.pm/evidence/duo-report-20261119-200742/diagnostics.txt:56-57`, `diagnostics-dump.txt:2197`),
     * which is identical to AOSP. The Oplus class/resource marker now exists (see [OplusMarker]) but it
     * is deliberately **not** used to claim or measure OxygenOS, because the same classes ship on
     * realme/OnePlus Oplus builds; the family proof here is the CPH2747 identity
     * (`MODEL`/`PRODUCT`/`DISPLAY` = `CPH2747`, evidence:
     * `.pm/evidence/duo-report-20261119-200742/diagnostics-dump.txt:4-6`) plus the SDK 36 window — never
     * the brand alone. The ids below are the measured ones, and [measured]/[shadeMeasured] are true only
     * on that identity.
     *
     * The shipped asset profile still requires a proven marker ([RomMarkers.provesFamily]); none exists
     * for OxygenOS, so the runtime `source` remains `code-default`. [measured], [shadeMeasured],
     * [panelHookMeasured] and [panelOwnerAvailable] are report/profile-selection metadata only and do
     * not change drawing.
     */
    private fun oxygenOs(api: Int, identity: String): RomAdapter {
        val verified = api == 0 || api in 36..36
        val cph2747 = verified && identity.contains("cph2747")
        return RomAdapter(
        id = "oxygenos",
        label = "OxygenOS / OnePlus",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icons_container", "status_bar_end_side_content"),
        batteryId = "battery",
        notes = if (cph2747) "measured on OxygenOS 16 (CPH2747, SDK 36 window): system_icons -> " +
            "LinearLayout, battery 83x61 px; the family proof is the CPH2747 identity plus the SDK 36 " +
            "window, not a marker" else
            BRAND_MATCH_PREFIX + "CPH2747 SystemUI is " +
            "/system_ext/priv-app/SystemUI/SystemUI.apk with com.android.systemui.SystemUIApplication, " +
            "identical to AOSP; brand/SDK cannot prove OxygenOS and the Oplus marker is deliberately " +
            "not used for OxygenOS",
        match = listOf("oneplus", "oxygen"),
        measured = cph2747,
        shadeContainerIds = listOf("quick_qs_status_icons", "shade_header_system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        shadeMeasured = cph2747,
        coverHook = true,
        panelExpansionKind = "panel_type_5arg",
        panelHookMeasured = verified,
        panelOwnerAvailable = verified
    )
    }

    /**
     * OPPO ColorOS 14 and 16 (Android 14 / 16).
     *
     * No ColorOS device has been measured. The ids are the AOSP ones the OnePlus build keeps plus the
     * spellings seen in OPPO trees; the adapter says so, and the runtime logs which ids resolved — so a
     * report turns these guesses into measured facts. The tree-walk fallback in the host still attaches
     * when none of these match.
     */
    private fun colorOs(): RomAdapter = RomAdapter(
        id = "coloros",
        label = "ColorOS / OPPO (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf(
            "system_icons",
            "system_icons_container",
            "status_bar_end_side_content",
            "status_bar_contents",
            "statusIcons"
        ),
        batteryId = "battery",
        notes = "unverified: no ColorOS 14/16 device measured; AOSP ids first, then OPPO spellings; " +
            "vendor oppo_status_bar.xml includes SignalClusterView signal_cluster inside " +
            "notification_icon_area, outside the system_icons include",
        match = listOf("oppo", "oplus", "coloros"),
        shadeContainerIds = listOf(
            "quick_qs_status_icons",
            "quick_status_bar_header_system_icons",
            "system_icons"
        ),
        shadeBatteryId = "battery",
        coverHook = false,
        panelExpansionKind = "none",
        panelHookMeasured = false,
        hideContainerIds = listOf("signal_cluster")
    )

    /**
     * R17D: the Oplus family proven by the runtime marker ([OplusMarker]), but not measured on a device.
     *
     * Evidence: the realme RMX5011 reports `battery -> ...StatBatteryMeterView` and
     * `clock -> ...StatClock` with `EndSideContentLayout` as the strip parent, plus the Oplus ids
     * `oplus_qs_tile_icon_bg` / `oplus_background_mask` / `oplus_fake_clock_container`
     * (`.pm/evidence/u1127/new-duo-report-20261127-175643/diagnostics.txt:91,92,734`). The OnePlus
     * PLK110 reports the same classes
     * (`.pm/evidence/u1127/duo-report-20261127-150754/diagnostics.txt:108,109`) and installs
     * `OplusQSSimpleHeader` / `OplusSplitShade` hooks (its module log lines
     * 31-32). The container/battery/clock ids are the CPH2747 Oplus spellings already used by this
     * project, not new guesses.
     *
     * The family is proven, the **device** is not: [measured] and [shadeMeasured] stay false, and the
     * note keeps the "unverified" promise. The modern Oplus tree has no `signal_cluster`, so
     * [hideContainerIds] stays empty rather than inheriting the static ColorOS list.
     */
    private fun colorOsMarker(marker: String): RomAdapter = RomAdapter(
        id = "coloros",
        label = "ColorOS / realme / OPPO (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icons_container", "status_bar_end_side_content"),
        batteryId = "battery",
        clockId = "clock",
        notes = OPLUS_MARKER_PREFIX + "$marker (unverified): Oplus family proven by the runtime marker " +
            "(StatBatteryMeterView/StatClock classes, EndSideContentLayout strip, oplus_* resource ids) " +
            "on the realme RMX5011 and OnePlus PLK110 reports; no ColorOS/realme device is measured, " +
            "and the CPH2747 Oplus ids are reused",
        match = listOf("oppo", "oplus", "coloros", "realme"),
        shadeContainerIds = listOf("quick_qs_status_icons", "shade_header_system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        shadeMeasured = false,
        coverHook = true,
        panelExpansionKind = "panel_type_5arg",
        panelHookMeasured = false,
        panelOwnerAvailable = false,
        marker = marker,
        measured = false
    )

    /**
     * Samsung One UI 6 (Android 14) and later.
     *
     * No Samsung device has been measured. One UI keeps many AOSP ids (`system_icons`, `statusIcons`,
     * `battery`, `clock`) but re-hosts them in its own containers, so the AOSP names are tried first and
     * the One UI spellings after; the tree-walk fallback covers the rest. Marked unverified on purpose.
     */
    private fun samsung(): RomAdapter = RomAdapter(
        id = "samsung",
        label = "Samsung One UI (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf(
            "system_icons",
            "systemIcons",
            "statusIcons",
            "status_icons",
            "system_icons_container",
            "status_bar_contents"
        ),
        batteryId = "battery",
        notes = "unverified: no Samsung device measured; AOSP ids first, then One UI spellings " +
            "(systemIcons/status_icons); the battery/status-icons parent fallback covers the rest",
        match = listOf("samsung"),
        shadeContainerIds = listOf("shade_header_system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        coverHook = true,
        panelExpansionKind = "listener",
        panelHookMeasured = false
    )

    /**
     * Measured on the Xiaomi 24129PN74G (HyperOS 3, Android 17, SDK 37). HyperOS keeps the AOSP id
     * `system_icons` but re-hosts it: it is a `MiuiStatusBatteryContainer` that holds the stock
     * `statusIcons`, the Wi-Fi/cellular combos and the battery, sitting inside `system_icon_area`
     * (a `MiuiNotificationStatusContainer`). The module injects into `system_icons`, which is why the
     * element appears with the battery correctly.
     *
     * `system_icon_area` is the first fallback: it is the wider strip the same container lives in, so a
     * build that renames `system_icons` still attaches beside the icons. `status_bar_icons` is the
     * `FrameLayout` above both. The AOSP name stays first so a stock-AOSP Xiaomi needs no special case.
     */
    private fun hyperOs(api: Int, markers: RomMarkers): RomAdapter {
        val verified = api == 0 || api in 37..37
        // R18: the SDK range alone is not proof. Measured only when a MIUI marker also proves the
        // family (sourceDir with MiuiSystemUI, or the MiuiSystemUIApplication class).
        val measured = verified && markers.provesFamily("hyperos")
        return RomAdapter(
        id = "hyperos",
        label = "HyperOS / MIUI",
        systemUiPackage = AOSP,
        containerIds = listOf(
            "system_icons",
            "system_icon_area",
            "status_bar_icons",
            "statusIcons",
            "system_icons_container"
        ),
        batteryId = "battery",
        notes = if (measured) "measured on HyperOS 3 (24129PN74G, Android 17): system_icons -> " +
            "MiuiStatusBatteryContainer holding statusIcons + battery; system_icon_area is the parent" else
            BRAND_MATCH_PREFIX + "Xiaomi/Redmi/POCO brand matched" +
            (if (!verified) " on SDK $api, outside the measured 37 gate" else "") +
            " but no MiuiSystemUI marker proved HyperOS",
        match = listOf("xiaomi", "redmi", "poco"),
        measured = measured,
        shadeContainerIds = listOf(
            "normal_control_center_status_bar",
            "system_icon_area",
            "shade_header_system_icons",
            "system_icons"
        ),
        shadeBatteryId = "battery",
        shadeMeasured = false,
        coverHook = true,
        panelExpansionKind = "fzz_3arg",
        panelHookMeasured = false,
        keyguardContainerId = "system_icons_container"
    )
    }

    /**
     * vivo OriginOS 6 / Funtouch. Static resource map only: no device dump, so the adapter says
     * "runtime unverified" and the shade path stays unwired until a real tree confirms it.
     */
    private fun vivo(): RomAdapter = RomAdapter(
        id = "vivo",
        label = "vivo OriginOS / Funtouch (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icon_area", "statusIcons"),
        batteryId = "battery",
        notes = "static APK OriginOS 6; runtime unverified",
        match = listOf("vivo", "iqoo", "origin", "funtouch"),
        shadeContainerIds = listOf(
            "vivo_qs_status_icons",
            "vivo_qs_status_icons_right",
            "vivo_split_qs_status_icons_right",
            "shade_header_system_icons",
            "system_icons"
        ),
        shadeBatteryId = "batteryRemainingIcon",
        shadeBatteryIds = listOf("vivo_qs_realbar_battery", "vivo_split_qs_realbar_battery"),
        coverHook = true,
        panelExpansionKind = "zfz_3arg",
        panelHookMeasured = false
    )

    /**
     * Honor MagicOS. The APK is an old MagicOS 11 build, so the ids are structural evidence only and
     * the adapter explicitly says the runtime is unverified.
     */
    private fun honor(): RomAdapter = RomAdapter(
        id = "honor",
        label = "Honor MagicOS (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "hw_system_icons", "system_icon_area"),
        batteryId = "battery",
        notes = "static APK MagicOS 11 old; runtime unverified; vendor status_bar.xml puts " +
            "FrameLayout signal_cluster_container under system_icon_area_fixed, a sibling of " +
            "hw_system_icons, so the strip pass never hides it",
        match = listOf("honor", "magic"),
        shadeContainerIds = listOf("hw_system_icons", "system_icon_area", "statusIcons"),
        shadeBatteryId = "battery",
        coverHook = false,
        panelExpansionKind = "none",
        panelHookMeasured = false,
        hideContainerIds = listOf("signal_cluster_container")
    )

    /** Motorola MyOS. Static MyOS 15 resource map; no runtime dump yet. */
    private fun motorola(): RomAdapter = RomAdapter(
        id = "motorola",
        label = "Motorola MyOS (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "statusIcons"),
        batteryId = "battery",
        notes = "static APK MyOS 15; runtime unverified; vendor " +
            "status_bar_mobile_signal_group_dual_new.xml has one ModernStatusBarMobileView " +
            "mobile_combo_dual holding mobile_group_slot1 and mobile_group_slot2",
        match = listOf("motorola", "moto", "myos"),
        shadeContainerIds = listOf("shade_header_system_icons", "system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        coverHook = true,
        panelExpansionKind = "listener",
        panelHookMeasured = false,
        dualSimCompositeIds = listOf("mobile_combo_dual")
    )

    /** Nothing OS. Static Nothing OS 15 resource map; no runtime dump yet. */
    private fun nothing(): RomAdapter = RomAdapter(
        id = "nothing",
        label = "Nothing OS (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "statusIcons"),
        batteryId = "battery",
        notes = "static APK Nothing OS 15; runtime unverified",
        match = listOf("nothing"),
        shadeContainerIds = listOf("shade_header_system_icons", "system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        coverHook = true,
        panelExpansionKind = "fzz_3arg",
        panelHookMeasured = false
    )

    private fun aosp(): RomAdapter = RomAdapter(
        id = "aosp",
        label = "AOSP / close to stock",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icons_container"),
        batteryId = "battery",
        notes = "AOSP baseline: the strip is a LinearLayout with id system_icons",
        coverHook = true,
        panelExpansionKind = "panel_type_5arg",
        panelHookMeasured = false
    )
}

/**
 * Evidence about the installed SystemUI, read from its package.
 *
 * The brand strings (`Xiaomi`, `Redmi`, `POCO`) survive on custom AOSP ROMs, so they cannot select the
 * measured HyperOS ids: LineageOS whyred and AOSPA sweet were both reported as HyperOS (evidence:
 * `.pm/evidence/duo-report-20261110-201707/diagnostics.txt:11-14`,
 * `.pm/evidence/duo-log-20261115-214854.txt:22-25`). A genuine MIUI SystemUI can be told apart by:
 *  - the APK path containing `MiuiSystemUI` (proven: `.pm/evidence/duo-report-20261113-202904/
 *    diagnostics-dump.txt:18` → `sourceDir=/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk`);
 *  - the application class `com.android.systemui.MiuiSystemUIApplication` (listed in the frozen hook
 *    surface, `docs/rom-contract.md:24`, and already hooked in `DuoHook`).
 *
 * The three states are:
 *  - **UNKNOWN** (`read = false`): nothing usable was read -> keep the old brand rule.
 *  - **HyperOS**: a read that found a MIUI marker -> the measured HyperOS adapter.
 *  - **AOSP**: a read with a non-blank, non-MIUI path or class -> AOSP, even on a Xiaomi brand.
 *
 * A package record with neither a source path nor an application class proves nothing: it is treated as
 * UNKNOWN, never as proof of a non-MIUI SystemUI. A PackageManager exception, a missing context or a
 * package miss likewise says nothing about the ROM. [RomMarkerSource.read] maps both to [UNKNOWN], which
 * keeps the pre-F-2 brand behaviour instead of guessing AOSP.
 *
 * The `ro.mi.os.version.name` / `ro.miui.ui.version.code` properties mentioned in the plan were **not**
 * used: no evidence dump or document in this repo reads them, so they remain unproven.
 */
internal data class RomMarkers(
    /** True only when the SystemUI package was read *and* returned a usable field. False = UNKNOWN. */
    val read: Boolean = false,
    val systemUiApplicationClass: String = "",
    val systemUiSourceDir: String = "",
    /**
     * R17D: the Oplus runtime marker (`class:<fqcn>` or `id:<name>`). Empty means no Oplus evidence was
     * found; non-blank proves the Oplus family, but never OxygenOS and never a non-MIUI verdict by
     * itself.
     */
    val oplusMarker: String = ""
) {
    /** R17D: true when a class/resource marker proved the Oplus family on this UI. */
    val hasOplusMarker: Boolean
        get() = oplusMarker.isNotBlank()

    /**
     * True when either marker proves a HyperOS/MIUI SystemUI. The path check ignores case because the
     * APK file name is not guaranteed to keep its case across builds; the class check compares the exact
     * simple name so a look-alike such as `...MyMiuiSystemUIApplication` is not accepted.
     */
    val hasHyperOsMarker: Boolean
        get() = systemUiSourceDir.contains("MiuiSystemUI", ignoreCase = true) ||
            systemUiApplicationClass.substringAfterLast('.') == "MiuiSystemUIApplication"

    /**
     * True only when a marker actually proves [family] on this UI (R18).
     *
     * OxygenOS evidence (why the brand cannot prove it): CPH2747 has
     * `sourceDir=/system_ext/priv-app/SystemUI/SystemUI.apk` and application class
     * `com.android.systemui.SystemUIApplication` (`.pm/evidence/duo-report-20261119-200742/
     * diagnostics.txt:56-57`, `diagnostics-dump.txt:2197`) — identical to AOSP, so brand/SDK cannot
     * prove OxygenOS. The Oplus class/resource marker exists (see [OplusMarker]) but it is deliberately
     * **not** used to claim or measure OxygenOS, because the same classes ship on realme/OnePlus Oplus
     * builds; CPH2747 remains proven by its identity plus the SDK 36 window. HyperOS has a positive
     * marker:
     * the APK path contains `MiuiSystemUI` (`.pm/evidence/duo-report-20261113-202904/diagnostics.txt:55`)
     * or the class is `MiuiSystemUIApplication` (`docs/rom-contract.md:24`).
     *
     * A new measured **asset** family MUST add its proven marker here; without one its measured asset
     * profile is never selected and the code fallback stays `brand-match (unverified)`.
     *
     * R18 correction: the **code-default** OxygenOS adapter is measured only on the CPH2747 identity
     * within the SDK 36 window (see `oxygenOs`), without going through this marker gate. Because this
     * function still returns false for OxygenOS, the shipped measured OxygenOS asset profile is never
     * selected and `source` stays `code-default`. [RomAdapter.measured], [RomAdapter.shadeMeasured] and
     * the `panel*` flags are report/profile-selection metadata only.
     */
    fun provesFamily(family: String): Boolean = when (family) {
        "hyperos" -> hasHyperOsMarker
        // R17D: the Oplus marker proves the ColorOS/realme/OPPO family only. It must never make
        // OxygenOS marker-proven: the CPH2747 code default stays measured by identity + SDK 36 window,
        // so `provesFamily("oxygenos")` stays false and the shipped OxygenOS asset profile is not used.
        "coloros" -> hasOplusMarker
        else -> false
    }

    /**
     * True only when a read found positive evidence of a *different* SystemUI: a usable field was read
     * and it carries no MIUI marker. A read that returned neither field is UNKNOWN, not proof.
     */
    val provesNonMiui: Boolean
        get() = read && !hasHyperOsMarker &&
            (systemUiSourceDir.isNotBlank() || systemUiApplicationClass.isNotBlank())

    companion object {
        /** Not inspected / unreadable: keep the pre-F-2 brand behaviour. */
        val UNKNOWN = RomMarkers()

        /**
         * A package record read from the PackageManager. If no field is present the record proves
         * nothing, so the result is UNKNOWN ([read] stays false) rather than a non-MIUI verdict. R17D:
         * an Oplus marker alone is a read field, but it still proves nothing about MIUI either way.
         */
        fun of(
            systemUiApplicationClass: String = "",
            systemUiSourceDir: String = "",
            oplusMarker: String = ""
        ): RomMarkers =
            RomMarkers(
                read = systemUiSourceDir.isNotBlank() || systemUiApplicationClass.isNotBlank() ||
                    oplusMarker.isNotBlank(),
                systemUiApplicationClass = systemUiApplicationClass,
                systemUiSourceDir = systemUiSourceDir,
                oplusMarker = oplusMarker
            )
    }
}

/**
 * Reads [RomMarkers] from the SystemUI package. Never throws: on any failure (PackageManager exception, no
 * context, package miss) the result is [RomMarkers.UNKNOWN], which keeps the caller on the old brand rule
 * instead of guessing. A read that returns neither a source path nor an application class is also
 * [RomMarkers.UNKNOWN]: only positive evidence of a non-MIUI field can prove the ROM is not MIUI.
 */
internal object RomMarkerSource {

    private val logOnce = LogOnce()

    fun read(context: Context): RomMarkers = try {
        val info = context.packageManager.getApplicationInfo(RomDetection.AOSP, 0)
        RomMarkers.of(
            systemUiApplicationClass = info.className.orEmpty(),
            systemUiSourceDir = info.sourceDir.orEmpty(),
            // R17D: resolved only after the package read succeeded, so a PackageManager failure still
            // returns UNKNOWN with no marker (the failure boundary pinned by RomMarkerSourceTest).
            oplusMarker = OplusMarker.detect(context)
        )
    } catch (t: Throwable) {
        logOnce.once("marker-read") {
            L.w("RomMarkerSource.read failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        RomMarkers.UNKNOWN
    }
}
