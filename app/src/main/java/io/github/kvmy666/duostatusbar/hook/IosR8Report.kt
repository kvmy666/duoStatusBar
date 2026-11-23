package io.github.kvmy666.duostatusbar.hook

/**
 * R8: the one diagnostic line for the V2 native owners (`.pm/R8-P0.md`).
 *
 * Always emits exactly one line so a dump names the stacked cluster, the order source and the
 * hide-extras count. Every read is guarded, so a report never throws and never hides the other
 * sources.
 */
internal object IosR8Report : IosDiagnosticSource {

    override fun diagnosticLines(): List<String> {
        val stacked = runCatching { IosStackedSim.stackedActive }.getOrDefault(false)
        val dataSub = runCatching { IosStackedSim.dataSubId }.getOrDefault(0)
        val hiddenSub = runCatching { IosStackedSim.hiddenSubId }.getOrDefault(0)
        val order = runCatching { IosIconOrder.source }.getOrDefault("stock")
        val extras = runCatching { IosExtras.hiddenCount() }.getOrDefault(0)
        val extrasViews = runCatching { IosExtras.hiddenViewCount }.getOrDefault(0)
        return listOf(
            "ios v2 r8: stacked=${if (stacked) "on" else "off"} dataSub=$dataSub " +
                "hiddenSub=$hiddenSub order=$order extrasHidden=$extras extrasViews=$extrasViews"
        )
    }
}
