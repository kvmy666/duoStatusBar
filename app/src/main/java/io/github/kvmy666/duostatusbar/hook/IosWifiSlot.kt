package io.github.kvmy666.duostatusbar.hook

/**
 * The occupant of the Wi-Fi status-bar slot: WIFI | DATA_TYPE(label) | NONE; R4A populates this,
 * R4B renders it.
 */
internal sealed class IosWifiSlot {
    data object Wifi : IosWifiSlot()
    data class DataType(val label: String) : IosWifiSlot()
    data object None : IosWifiSlot()
}
