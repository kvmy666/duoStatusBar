package io.github.kvmy666.duostatusbar.settings

import android.content.Context

/**
 * Auto Expand's action vocabulary, copied from its `ZoneAction.ALL`.
 *
 * Copied, not imported: the two modules are separate APKs and neither depends on the other at build time.
 * The keys *are* the contract — they travel as `zone_action_key` in Auto Expand's privileged broadcast — so
 * if its list ever changes this one must follow. Keeping it in one file (rather than as literals scattered
 * through the UI) is what makes that a one-line change.
 *
 * The offered list is status-bar-appropriate toggles (Wi-Fi, Bluetooth, mobile data, DND, airplane, power
 * saver, auto-rotate) plus "No Action". Every key here is handled by Auto Expand's privileged receiver for
 * both apps installed together: the four it handles directly (Wi-Fi/Bluetooth/mobile data/power saver) and
 * the rest through its own dispatcher. A key it cannot express stays out of this list, so the element can
 * never offer an action that silently does nothing.
 */
object DuoActions {

    const val AUTO_EXPAND_PACKAGE = "io.github.kvmy666.autoexpand"

    val ALL: List<Pair<String, String>> = listOf(
        "no_action" to "No Action",
        "toggle_wifi" to "Toggle Wi-Fi",
        "toggle_bluetooth" to "Toggle Bluetooth",
        "toggle_mobile_data" to "Toggle Mobile Data",
        "toggle_dnd" to "Toggle Do Not Disturb",
        "toggle_airplane_mode" to "Toggle Airplane Mode",
        "toggle_auto_rotate" to "Toggle Auto Rotate",
        "toggle_power_saver" to "Toggle Power Saver"
    )

    fun isAutoExpandInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(AUTO_EXPAND_PACKAGE, 0)
        true
    } catch (_: Throwable) {
        false
    }
}
