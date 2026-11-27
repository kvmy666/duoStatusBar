package io.github.kvmy666.duostatusbar.hook

import kotlin.math.ceil

/**
 * Which iOS glyph a stock resource name stands for.
 *
 * [HIDDEN] is not a glyph: the entry is a Wi-Fi badge or activity arrow that the "Hide extra icons"
 * toggle collapses with a 0x0 gate drawable (`.pm/R8-P0.md` decision 4). Its stock drawable is
 * captured and restored when the toggle is off.
 */
internal enum class IosResourceKind { WIFI, CELL, LABEL, HIDDEN }

/**
 * One stock drawable resource that the v2 engine replaces.
 *
 * [value] is the **already mapped** glyph value: Wi-Fi/cell dot count (0..4) for [IosResourceKind.WIFI]
 * and [IosResourceKind.CELL], unused (0) for [IosResourceKind.LABEL]. The level tables live in
 * [IosResourceNames.wifiDots]/[IosResourceNames.cellDots] and are applied while building the specs, so
 * the engine only has to paint the result.
 *
 * [rawId] is used only for the measured framework ids that have no name in the tree (Motorola Wi-Fi,
 * which is DORMANT (R13) data); every other entry is resolved by [name] in [packageName] at runtime
 * (ids differ between builds — `.pm/evidence/r7/build/A-oxygenos-names.md:25-30`).
 */
internal data class IosResourceSpec(
    val name: String,
    val kind: IosResourceKind,
    val value: Int,
    val label: String = "",
    val packageName: String = "",
    val rawId: Int = 0
) {
    companion object {
        /**
         * Blank means "resolve in the live ROM's SystemUI package" (R7 P5). A hard-coded
         * `com.android.systemui` would pre-empt `rom.systemUiPackage` on ROMs that report another
         * package (HyperOS reports `system`), even though only the v2 families build a map today.
         */
        const val DEFAULT_PACKAGE = ""
    }
}

/**
 * The per-ROM resource name tables, taken verbatim from the measured trees:
 * `.pm/evidence/r7/build/A-oxygenos-names.md` (OxygenOS) and `B-other-roms-names.md` (the rest).
 *
 * OxygenOS, ColorOS/realme (the same measured Oplus names) and Samsung are live in the v2 route; the
 * HyperOS/Nothing/Motorola/vivo tables are complete so a later route flip needs no new evidence, but
 * they are not activated. No activity, arrow, or AOSP `ic_no_internet_*`/`ic_*mobiledata` entry is
 * mapped anywhere: those are not the bar's leaves on these ROMs (A:40-41, A:59-63, A:136-138).
 */
internal object IosResourceNames {

    /**
     * Stock Wi-Fi bars (0..[top]) -> iOS arcs (0..3).
     *
     * The non-4 `top` branch exists for the DORMANT (R13) HyperOS/vivo tables; no live route passes
     * `top == 3`.
     */
    fun wifiDots(level: Int, top: Int): Int {
        if (level <= 0 || top <= 0) return 0
        if (top == 4) return SignalMapping.wifiDotsForStockBars(level)
        return ceil(level * 3.0 / top).toInt().coerceIn(1, 3)
    }

    /** Stock cell bars (0..[top]) -> iOS dots (1..4). */
    fun cellDots(level: Int, top: Int): Int {
        if (level < 0 || top <= 0) return 0
        return SignalMapping.iosDots(level, top)
    }

    /** The specs for [familyId]; an unknown or host-only family has none. */
    fun specs(familyId: String): List<IosResourceSpec> = when (familyId) {
        "oxygenos", "coloros" -> oxygenos()
        "samsung" -> samsung()
        "hyperos" -> hyperos()
        "nothing" -> nothing()
        "motorola" -> motorola()
        "vivo" -> vivo()
        else -> emptyList()
    }

    /**
     * The [IosResourceKind.HIDDEN] entries of [familyId] (empty for every non-Oplus-live family;
     * ColorOS shares the OxygenOS table).
     *
     * No-gap proof: these ids land on the `wifi_left`/`wifi_inout`/`data_inout` leaves, which are
     * `wrap_content` with no min width or padding, so a 0x0 gate collapses the slot
     * (`.pm/R8-P0.md` decision 4).
     */
    fun hiddenSpecs(familyId: String): List<IosResourceSpec> =
        specs(familyId).filter { it.kind == IosResourceKind.HIDDEN }

    // ------------------------------------------------------------------------------ builders

    private fun wifi(name: String, level: Int, top: Int): IosResourceSpec =
        IosResourceSpec(name, IosResourceKind.WIFI, wifiDots(level, top))

    private fun cell(name: String, level: Int, top: Int): IosResourceSpec =
        IosResourceSpec(name, IosResourceKind.CELL, cellDots(level, top))

    /** Explicit no-service resources: the glyph is CELL 0, not the mapped level-0 (one dot) icon. */
    private fun cellZero(name: String): IosResourceSpec =
        IosResourceSpec(name, IosResourceKind.CELL, 0)

    private fun label(name: String, text: String): IosResourceSpec =
        IosResourceSpec(name, IosResourceKind.LABEL, 0, label = text)

    /** A badge/arrow that the hide-extras gate collapses; the engine captures the stock drawable. */
    private fun hidden(name: String): IosResourceSpec =
        IosResourceSpec(name, IosResourceKind.HIDDEN, 0)

    private fun hidden(names: List<String>): List<IosResourceSpec> = names.map { hidden(it) }

    private fun wifiLevels(prefix: String, from: Int, to: Int, top: Int): List<IosResourceSpec> =
        (from..to).map { wifi("$prefix$it", it, top) }

    private fun cellLevels(prefix: String, from: Int, to: Int, top: Int): List<IosResourceSpec> =
        (from..to).map { cell("$prefix$it", it, top) }

    private fun labels(names: List<String>, text: String): List<IosResourceSpec> =
        names.map { label(it, text) }

    // ------------------------------------------------------------------------------ OxygenOS

    private fun oxygenos(): List<IosResourceSpec> = buildList {
        addAll(wifiLevels("stat_signal_wifi_signal_", 0, 4, top = 4))

        // 4-bar default.
        addAll(cellLevels("stat_signal_signal_lte_single_", 0, 4, top = 4))
        // No-voice set: the zero level is spelled differently from 1..4.
        add(cell("stat_signal_single_novoice_0", 0, top = 4))
        addAll(cellLevels("stat_signal_signal_novoice_", 1, 4, top = 4))
        // Soft-SIM set.
        addAll(cellLevels("stat_signal_soft_signal_", 0, 4, top = 4))
        // 5-bar sets (latent/carrier-only but measured).
        addAll(cellLevels("stat_sys_signal_5_bar_", 0, 5, top = 5))
        (0..5).forEach { add(cell("stat_sys_signal_5_bar_${it}_fully", it, top = 5)) }
        addAll(cellLevels("stat_sys_signal_5_bar_sprint_", 0, 5, top = 5))
        (0..5).forEach { add(cell("stat_sys_signal_5_bar_sprint_${it}_fully", it, top = 5)) }
        // Explicit no-service resources (do not map `stat_signal_signal_null_lte`: it is the no-SIM glyph).
        add(cellZero("stat_signal_noservice_lte"))
        add(cellZero("stat_signal_soft_noservice"))

        addAll(
            labels(
                listOf(
                    "stat_signal_connected_lte_big",
                    "stat_signal_connected_lte_plus_big",
                    "stat_signal_connected_soft_lte_big",
                    "stat_signal_connected_soft_lte_plus_big",
                    "stat_sys_data_fully_connected_sprint_lte"
                ),
                "LTE"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_signal_connected_4g_lte_big",
                    "stat_signal_connected_4gp_lte_big",
                    "stat_signal_connected_big_4g",
                    "stat_signal_connected_big_4g_plus",
                    "stat_signal_connected_soft_4g_lte_big",
                    "stat_signal_connected_soft_4gp_lte_big",
                    "stat_sys_data_fully_connected_tmo_4g_lte",
                    "stat_sys_data_fully_connected_tmo_4g_plus_lte"
                ),
                "4G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_signal_connected_5g",
                    "stat_signal_connected_4_5g_lte_big",
                    "stat_signal_connected_big_5g",
                    "stat_signal_connected_soft_5g",
                    "stat_sys_data_fully_connected_5g_uc",
                    "stat_sys_data_fully_connected_5g_e",
                    "stat_sys_data_fully_connected_5g_a",
                    "stat_sys_data_fully_connected_5_dot_5g",
                    "stat_sys_data_fully_connected_sprint_5g",
                    "stat_sys_data_fully_connected_tmo_5g"
                ),
                "5G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_signal_connected_4_5g_plus",
                    "stat_sys_data_fully_connected_5g_plus",
                    "stat_sys_data_fully_connected_5g_plus_plus",
                    "stat_sys_data_fully_connected_5g_uwb_global"
                ),
                "5G+"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_signal_connected_3g_lte_big",
                    "stat_signal_connected_3gp_lte_big",
                    "stat_signal_connected_h_lte_big",
                    "stat_signal_connected_h_p_lte_big",
                    "stat_signal_connected_soft_3g_lte_big",
                    "stat_signal_connected_soft_3gp_lte_big",
                    "stat_signal_connected_soft_h_lte_big",
                    "stat_signal_connected_soft_h_p_lte_big",
                    "stat_sys_data_fully_connected_3g",
                    "stat_sys_data_fully_connected_h",
                    "stat_sys_data_fully_connected_h_plus",
                    "stat_sys_data_fully_connected_sprint_3g"
                ),
                "3G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_signal_connected_e_lte_big",
                    "stat_signal_connected_soft_e_lte_big",
                    "stat_sys_data_fully_connected_e"
                ),
                "E"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_signal_connected_2g_lte_big",
                    "stat_signal_connected_g_lte_big",
                    "stat_signal_connected_soft_2g_lte_big",
                    "stat_signal_connected_soft_g_lte_big",
                    "stat_sys_data_fully_connected_2g",
                    "stat_sys_data_fully_connected_g"
                ),
                "G"
            )
        )

        // R8 "Hide extra icons": the measured Wi-Fi 6 badge (`wifi_left`), Wi-Fi activity arrows
        // (`wifi_inout`) and cell activity arrows (`data_inout`). No `_6_double`/`_7_double` exists.
        addAll(
            hidden(
                listOf(
                    "stat_signal_wifi_6",
                    "stat_signal_wifi_6e",
                    "stat_signal_wifi_7",
                    "stat_signal_wifi_double",
                    "stat_signal_wifi_h_passpoint",
                    "stat_signal_wifi_passpoint",
                    "stat_signal_wifi_r_passpoint",
                    "stat_signal_activity_wifi_none",
                    "stat_signal_activity_wifi_in",
                    "stat_signal_activity_wifi_out",
                    "stat_signal_activity_wifi_inout",
                    "stat_signal_activity_default_public",
                    "stat_signal_activity_in_public",
                    "stat_signal_activity_out_public",
                    "stat_signal_activity_inout_public",
                    "stat_signal_activity_soft_default_public",
                    "stat_signal_activity_soft_in_public",
                    "stat_signal_activity_soft_out_public",
                    "stat_signal_activity_soft_inout_public"
                )
            )
        )
    }

    // ------------------------------------------------------------------------------- Samsung

    private fun samsung(): List<IosResourceSpec> = buildList {
        addAll(wifiLevels("stat_sys_wifi_signal_", 0, 4, top = 4))
        // R7 P5: `stat_sys_wifi_captive_0..4` stays stock. Replacing it would remove Samsung's own
        // captive-portal indication instead of merely restyling the bars.

        addAll(cellLevels("stat_sys_signal_", 0, 4, top = 4))
        addAll(cellLevels("stat_sys_signal_5level_", 0, 5, top = 5))
        addAll(cellLevels("stat_sys_signal_chn_", 0, 5, top = 5))
        addAll(cellLevels("stat_sys_signal_sprint_", 0, 5, top = 5))
        addAll(cellLevels("stat_sys_signal_w16_chn_", 0, 5, top = 5))
        add(cellZero("stat_sys_signal_null"))

        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_lte",
                    "stat_sys_data_connected_lte_att",
                    "stat_sys_data_connected_lte_kt",
                    "stat_sys_data_connected_lte_plus",
                    "stat_sys_data_connected_lte_plus_kt",
                    "stat_sys_data_4glte_na"
                ),
                "LTE"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_4g",
                    "stat_sys_data_connected_4g_att",
                    "stat_sys_data_connected_4g_chn",
                    "stat_sys_data_connected_4g_lte_ltn",
                    "stat_sys_data_connected_4g_tmo",
                    "stat_sys_data_connected_4g_uscc",
                    "stat_sys_data_connected_4g_vzw",
                    "stat_sys_data_connected_4_half_g",
                    "stat_sys_data_4g_na",
                    "stat_sys_data_connected_4g_plus",
                    "stat_sys_data_connected_4g_plus_chn",
                    "stat_sys_data_4g_plus_na",
                    "stat_sys_data_fully_connected_4_half_g_amx",
                    "stat_sys_data_fully_connected_4_half_g_plus_amx",
                    "ic_4g_lte_mobiledata",
                    "ic_4g_lte_plus_mobiledata"
                ),
                "4G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_5g",
                    "stat_sys_data_connected_5g_one_shaped",
                    "stat_sys_data_connected_5g_one_shaped_chn",
                    "stat_sys_data_connected_5g_available",
                    "stat_sys_data_connected_5g_available_kt",
                    "stat_sys_data_connected_5g_connected_kt",
                    "stat_sys_data_connected_5g_att",
                    "stat_sys_data_connected_5g_tmo",
                    "stat_sys_data_connected_5g_uscc",
                    "stat_sys_data_connected_5g_vzw",
                    "stat_sys_data_5g_na",
                    "stat_sys_data_connected_5ga_chn",
                    "stat_sys_data_connected_5ge_att",
                    "stat_sys_data_5ge_na",
                    "stat_sys_data_connected_5g_uwb_vzw",
                    "ic_5g_e_mobiledata"
                ),
                "5G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_5g_plus_connected",
                    "stat_sys_data_connected_5g_plus_one_shaped",
                    "stat_sys_data_connected_5g_plus_enlarged",
                    "stat_sys_data_connected_5g_plus_uscc",
                    "stat_sys_data_5g_plus_na",
                    "stat_sys_data_5guc_na",
                    "stat_sys_data_connected_5g_plus_att"
                ),
                "5G+"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_3g",
                    "stat_sys_data_connected_3g_chn",
                    "stat_sys_data_connected_3g_kt",
                    "stat_sys_data_connected_3g_vzw",
                    "stat_sys_data_connected_3g_plus",
                    "stat_sys_data_connected_3g_plus_chn",
                    "stat_sys_data_connected_3g_tmo",
                    "stat_sys_data_3g_na"
                ),
                "3G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_e",
                    "stat_sys_data_connected_e_chn",
                    "stat_sys_data_connected_e_vzw"
                ),
                "E"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_g",
                    "stat_sys_data_connected_g_chn",
                    "stat_sys_data_connected_g_vzw",
                    "stat_sys_data_connected_2g",
                    "stat_sys_data_connected_2g_chn",
                    "stat_sys_data_connected_2g_kt"
                ),
                "G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_h",
                    "stat_sys_data_connected_h_chn",
                    "stat_sys_data_connected_h_vzw",
                    "stat_sys_data_connected_h_plus",
                    "stat_sys_data_connected_h_plus_chn",
                    "stat_sys_data_connected_h_plus_vzw",
                    "stat_sys_data_h_na",
                    "stat_sys_data_h_plus_na"
                ),
                "3G"
            )
        )
        addAll(
            labels(
                listOf(
                    "stat_sys_data_connected_1x",
                    "stat_sys_data_connected_1x_chn",
                    "stat_sys_data_connected_1x_vzw"
                ),
                "G"
            )
        )
    }

    // ------------------------------------------------------------------------------- HyperOS

    /**
     * DORMANT (R13): measured data only. No production route reads this table: [IosV2Profile.engineFamily]
     * is false and `IosResourceEngine.activate` refuses. Kept so an R13 route flip needs no new evidence.
     */
    private fun hyperos(): List<IosResourceSpec> = buildList {
        addAll(wifiLevels("stat_sys_wifi_signal_", 0, 3, top = 3))
        addAll(wifiLevels("stat_sys_wifi_signal_unavailable_", 0, 3, top = 3))

        val variants = listOf(
            "",
            "_darkmode",
            "_no_voice",
            "_no_voice_darkmode",
            "_no_voice_tint",
            "_tint"
        )
        for (n in 0..4) {
            for (variant in variants) add(cell("stat_sys_signal_$n$variant", n, top = 4))
        }
        // HyperOS draws the data type as text (`MobileTypeDrawable`), so there is no LABEL entry.
    }

    // ------------------------------------------------------------------------------- Nothing

    /**
     * DORMANT (R13): measured data only. No production route reads this table: [IosV2Profile.engineFamily]
     * is false and `IosResourceEngine.activate` refuses. Kept so an R13 route flip needs no new evidence.
     */
    private fun nothing(): List<IosResourceSpec> = buildList {
        addAll(wifiLevels("ic_wifi_", 0, 4, top = 4))
        (0..4).forEach { add(wifi("ic_wifi_${it}_error", it, top = 4)) }
        // No CELL: Nothing draws `NTSignalDrawable`.

        addAll(
            labels(
                listOf("nt_ic_lte_mobiledata", "nt_ic_lte_plus_mobiledata"),
                "LTE"
            )
        )
        addAll(
            labels(
                listOf("nt_ic_4g_mobiledata", "nt_ic_4g_plus_mobiledata"),
                "4G"
            )
        )
        addAll(
            labels(
                listOf(
                    "nt_ic_5g_mobiledata",
                    "nt_ic_5g_e_mobiledata",
                    "nt_ic_4_dot_5g_mobiledata"
                ),
                "5G"
            )
        )
        addAll(
            labels(
                listOf("nt_ic_5g_plus_mobiledata", "nt_ic_5g_plus_plus_mobiledata"),
                "5G+"
            )
        )
        addAll(
            labels(
                listOf(
                    "nt_ic_3g_mobiledata",
                    "nt_ic_1x_mobiledata",
                    "nt_ic_h_mobiledata",
                    "nt_ic_h_plus_mobiledata"
                ),
                "3G"
            )
        )
        addAll(labels(listOf("nt_ic_e_mobiledata"), "E"))
        addAll(labels(listOf("nt_ic_g_mobiledata"), "G"))
    }

    // ------------------------------------------------------------------------------ Motorola

    /**
     * DORMANT (R13): measured data only. No production route reads this table: [IosV2Profile.engineFamily]
     * is false and `IosResourceEngine.activate` refuses. Kept so an R13 route flip needs no new evidence.
     */
    private fun motorola(): List<IosResourceSpec> = buildList {
        // Measured framework Wi-Fi ids; no names exist in the tree (B:633-639).
        (0..4).forEach { level ->
            add(
                IosResourceSpec(
                    name = "",
                    kind = IosResourceKind.WIFI,
                    value = wifiDots(level, top = 4),
                    packageName = "android",
                    rawId = 0x01080965 + level
                )
            )
        }
        // No CELL: Motorola draws AOSP `SignalDrawable`.

        addAll(
            labels(
                listOf(
                    "ic_lte_mobiledata",
                    "ic_lte_plus_mobiledata",
                    "ic_4g_lte_mobiledata",
                    "ic_4g_lte_plus_mobiledata"
                ),
                "LTE"
            )
        )
        addAll(
            labels(listOf("ic_4g_mobiledata", "ic_4g_plus_mobiledata"), "4G")
        )
        addAll(
            labels(listOf("ic_5g_mobiledata", "ic_5g_e_mobiledata"), "5G")
        )
        addAll(
            labels(
                listOf("ic_5g_plus_mobiledata", "ic_5g_plus_mobiledata_default"),
                "5G+"
            )
        )
        addAll(
            labels(
                listOf("ic_3g_mobiledata", "ic_h_mobiledata", "ic_h_plus_mobiledata"),
                "3G"
            )
        )
        addAll(labels(listOf("ic_e_mobiledata"), "E"))
        addAll(
            labels(
                listOf("ic_g_mobiledata", "ic_1x_mobiledata"),
                "G"
            )
        )
    }

    // ---------------------------------------------------------------------------------- vivo

    /**
     * DORMANT (R13): measured data only. No production route reads this table: [IosV2Profile.engineFamily]
     * is false and `IosResourceEngine.activate` refuses. Kept so an R13 route flip needs no new evidence.
     */
    private fun vivo(): List<IosResourceSpec> = buildList {
        addAll(wifiLevels("vivo_wifi_signal_", 0, 3, top = 3))
        add(wifi("vivo_wifi_signal_empty", 0, top = 3))
        add(wifi("vivo_wifi_unavailable", 0, top = 3))
        addAll(cellLevels("vivo_signal_strength_", 0, 4, top = 4))
        // No LABEL: vivo's own type path is UNVERIFIED and its resource ids are unmapped.
    }
}
