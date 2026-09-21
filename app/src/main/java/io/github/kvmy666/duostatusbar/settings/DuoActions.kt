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
 * Actions that need extra data (`open_app`, `launch_shortcut`, `open_snapper_history`) are deliberately
 * absent: they cannot be expressed by a key alone, so offering them here would produce actions that silently
 * do nothing.
 */
object DuoActions {

    const val AUTO_EXPAND_PACKAGE = "io.github.kvmy666.autoexpand"

    val ALL: List<Pair<String, String>> = listOf(
        "no_action" to "No Action",
        "toggle_flashlight" to "Toggle Flashlight",
        "toggle_wifi" to "Toggle Wi-Fi",
        "toggle_bluetooth" to "Toggle Bluetooth",
        "toggle_mobile_data" to "Toggle Mobile Data",
        "toggle_airplane_mode" to "Toggle Airplane Mode",
        "toggle_dnd" to "Toggle Do Not Disturb",
        "toggle_auto_rotate" to "Toggle Auto Rotate",
        "toggle_power_saver" to "Toggle Power Saver",
        "open_camera" to "Open Camera",
        "open_recents" to "Open Recents",
        "take_screenshot" to "Take Screenshot",
        "lock_screen" to "Lock Screen",
        "show_notifications" to "Show Notifications",
        "show_quick_settings" to "Show Quick Settings",
        "media_play_pause" to "Media: Play / Pause",
        "media_next" to "Media: Next Track",
        "media_prev" to "Media: Previous Track",
        "volume_up" to "Volume Up",
        "volume_down" to "Volume Down",
        "volume_mute" to "Volume Mute / Unmute",
        "brightness_up" to "Brightness Up",
        "brightness_down" to "Brightness Down",
        "cycle_ringer" to "Cycle Ringer Mode"
    )

    fun label(key: String): String = ALL.firstOrNull { it.first == key }?.second ?: key

    fun isAutoExpandInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(AUTO_EXPAND_PACKAGE, 0)
        true
    } catch (_: Throwable) {
        false
    }
}
