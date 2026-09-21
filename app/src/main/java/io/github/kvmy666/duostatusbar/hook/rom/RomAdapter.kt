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
    /** Where the facts came from — "measured" is a device, "unverified" is a guess to be replaced. */
    val notes: String
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
        val haystack = listOf(manufacturer, brand, product, display).joinToString(" ").lowercase()
        return when {
            haystack.contains("oneplus") || haystack.contains("oppo") || haystack.contains("oplus") ->
                colorOs(haystack)
            haystack.contains("xiaomi") || haystack.contains("redmi") || haystack.contains("poco") ->
                hyperOs()
            else -> aosp()
        }
    }

    /**
     * Measured on the OnePlus 15 (CPH2747), Android 16 / OxygenOS 16.0.9.400: the strip is a `LinearLayout`
     * with the AOSP id `system_icons`, holding `statusIcons` (the stock icons) and `battery` (83 × 61 px).
     */
    private fun colorOs(haystack: String): RomAdapter {
        val oos = haystack.contains("oxygen") || haystack.contains("oneplus")
        return RomAdapter(
            id = "coloros",
            label = if (oos) "OxygenOS / ColorOS" else "ColorOS",
            systemUiPackage = AOSP,
            containerIds = listOf("system_icons", "system_icons_container", "status_bar_end_side_content"),
            batteryId = "battery",
            notes = "measured on OxygenOS 16 (CPH2747): system_icons -> LinearLayout, battery 83x61 px"
        )
    }

    /**
     * No Xiaomi device has been tested. The ids are the AOSP ones plus the spellings seen in MIUI trees, and
     * the adapter says so: if the strip resolves, good; if not, the log says which ids were tried.
     */
    private fun hyperOs(): RomAdapter = RomAdapter(
        id = "hyperos",
        label = "HyperOS / MIUI (unverified)",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icons_container", "statusIcons"),
        batteryId = "battery",
        notes = "unverified: no Xiaomi device available to measure; AOSP ids first, then MIUI spellings"
    )

    private fun aosp(): RomAdapter = RomAdapter(
        id = "aosp",
        label = "AOSP / close to stock",
        systemUiPackage = AOSP,
        containerIds = listOf("system_icons", "system_icons_container"),
        batteryId = "battery",
        notes = "AOSP baseline: the strip is a LinearLayout with id system_icons"
    )
}
