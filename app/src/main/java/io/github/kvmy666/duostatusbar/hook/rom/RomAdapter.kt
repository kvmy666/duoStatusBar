package io.github.kvmy666.duostatusbar.hook.rom


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
    /** Ordered probes for the shade/QS status-icon hosts; empty means none are known. */
    val shadeStatusIconIds: List<String> = emptyList(),
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
    val panelOwnerAvailable: Boolean = false
)

/**
 * Picks the adapter from build identity, as a pure function so the choice itself is unit-tested instead of
 * being debugged on a phone.
 */
internal object RomDetection {

    private const val AOSP = "com.android.systemui"

    fun forThisRom(
        manufacturer: String,
        brand: String,
        product: String,
        display: String
    ): RomAdapter {
        val haystack = haystack(manufacturer, brand, product, display)
        return when {
            haystack.contains("samsung") -> samsung()
            haystack.contains("oneplus") || haystack.contains("oxygen") -> oxygenOs()
            haystack.contains("oppo") || haystack.contains("oplus") || haystack.contains("coloros") ->
                colorOs()
            haystack.contains("xiaomi") || haystack.contains("redmi") || haystack.contains("poco") ->
                hyperOs()
            haystack.contains("vivo") || haystack.contains("iqoo") || haystack.contains("origin") ||
                haystack.contains("funtouch") -> vivo()
            haystack.contains("honor") || haystack.contains("magic") -> honor()
            haystack.contains("motorola") || haystack.contains("moto") || haystack.contains("myos") ->
                motorola()
            haystack.contains("nothing") -> nothing()
            else -> aosp()
        }
    }

    /**
     * The same choice, but a **measured** profile loaded from data (see [RomProfiles]) is accepted
     * first. Only `measured` profiles may override, so a data edit can add a device we have read but
     * can never silently replace an unverified guess — the code stays the source of truth otherwise.
     * Kept as an overload so the pure four-argument function (and every test on it) is unchanged.
     */
    fun forThisRom(
        manufacturer: String,
        brand: String,
        product: String,
        display: String,
        profiles: List<RomAdapter>
    ): RomAdapter {
        val haystack = haystack(manufacturer, brand, product, display)
        profiles.firstOrNull { profile ->
            profile.measured && profile.match.any { haystack.contains(it.lowercase()) }
        }?.let { return it }
        return forThisRom(manufacturer, brand, product, display)
    }

    private fun haystack(manufacturer: String, brand: String, product: String, display: String): String =
        listOf(manufacturer, brand, product, display).joinToString(" ").lowercase()

    /**
     * Measured on the OnePlus 15 (CPH2747), Android 16 / OxygenOS 16.0.9.400: the strip is a `LinearLayout`
     * with the AOSP id `system_icons`, holding `statusIcons` (the stock icons) and `battery` (83 × 61 px).
     *
     * The later spellings are only fallbacks for a build that renamed the strip; the measured one is first.
     */
    private fun oxygenOs(): RomAdapter = RomAdapter(
        id = "oxygenos",
        label = "OxygenOS / OnePlus",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icons_container", "status_bar_end_side_content"),
        batteryId = "battery",
        notes = "measured on OxygenOS 16 (CPH2747): system_icons -> LinearLayout, battery 83x61 px",
        match = listOf("oneplus", "oxygen"),
        measured = true,
        shadeContainerIds = listOf("quick_qs_status_icons", "shade_header_system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        shadeMeasured = true,
        coverHook = true,
        panelExpansionKind = "panel_type_5arg",
        panelHookMeasured = true,
        panelOwnerAvailable = true
    )

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
        notes = "unverified: no ColorOS 14/16 device measured; AOSP ids first, then OPPO spellings",
        match = listOf("oppo", "oplus", "coloros"),
        shadeContainerIds = listOf(
            "quick_qs_status_icons",
            "quick_status_bar_header_system_icons",
            "system_icons"
        ),
        shadeBatteryId = "battery",
        coverHook = false,
        panelExpansionKind = "none",
        panelHookMeasured = false
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
    private fun hyperOs(): RomAdapter = RomAdapter(
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
        notes = "measured on HyperOS 3 (24129PN74G, Android 17): system_icons -> " +
            "MiuiStatusBatteryContainer holding statusIcons + battery; system_icon_area is the parent",
        match = listOf("xiaomi", "redmi", "poco"),
        measured = true,
        shadeContainerIds = listOf("shade_header_system_icons", "system_icons"),
        shadeBatteryId = "battery",
        shadeMeasured = false,
        coverHook = true,
        panelExpansionKind = "fzz_3arg",
        panelHookMeasured = false
    )

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
        shadeContainerIds = listOf("shade_header_system_icons", "system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
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
        notes = "static APK MagicOS 11 old; runtime unverified",
        match = listOf("honor", "magic"),
        shadeContainerIds = listOf("hw_system_icons", "system_icon_area", "statusIcons"),
        shadeBatteryId = "battery",
        coverHook = false,
        panelExpansionKind = "none",
        panelHookMeasured = false
    )

    /** Motorola MyOS. Static MyOS 15 resource map; no runtime dump yet. */
    private fun motorola(): RomAdapter = RomAdapter(
        id = "motorola",
        label = "Motorola MyOS (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "statusIcons"),
        batteryId = "battery",
        notes = "static APK MyOS 15; runtime unverified",
        match = listOf("motorola", "moto", "myos"),
        shadeContainerIds = listOf("shade_header_system_icons", "system_icons"),
        shadeBatteryId = "batteryRemainingIcon",
        coverHook = true,
        panelExpansionKind = "listener",
        panelHookMeasured = false
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
