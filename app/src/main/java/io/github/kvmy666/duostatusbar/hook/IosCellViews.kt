package io.github.kvmy666.duostatusbar.hook

import android.view.View

/**
 * R3 A2: the inventory with every unreadable cellular row removed, so only the real SIM slots reach
 * the plan and the per-icon report.
 *
 * The visible QS/shade strip also carries carrier-group rows (`mobile_in`/`mobile_out`/
 * `mobile_roaming`) whose `getSubId()` is 0. The mapping needs exactly the two SIM slots; feeding it
 * those rows made it see more than two slots and resolve [IosCellPlan.None], leaving both towers stock
 * on the overlay surfaces. When no cellular slot exposes an id, the full list is kept: that is the
 * existing single unreadable-SIM fallback, not a reason to drop the icon. Same rule as
 * [IosReplaceHost.mappedCells], shared here so the two hosts cannot drift.
 */
internal fun mappedCellInventory(
    inventory: List<StockIcon>,
    subIdOf: (View) -> Int
): List<StockIcon> {
    val cells = inventory.filter { it.kind == StockIconKind.CELLULAR }
    if (cells.isEmpty()) return inventory
    val mapped = cells.filter { subIdOf(it.view) > 0 }.ifEmpty { cells }
    return inventory.filter { icon ->
        icon.kind != StockIconKind.CELLULAR || mapped.any { it.view === icon.view }
    }
}
