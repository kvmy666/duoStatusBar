package io.github.kvmy666.duostatusbar.hook

import android.annotation.SuppressLint
import android.content.Context

/**
 * The platform reads behind the Duo element, kept apart from the monitor so each one can be tested
 * and swapped per ROM (Phase 7) without touching the wiring.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 *
 * This is the thin facade the wiring has always used; the reads themselves live in [WifiReader],
 * [CellReader] and [DeviceStateReader].
 */
internal object SystemReaders {

    private const val TAG = "DuoSB"

    fun wifiLevel(context: Context, current: Int): Int = WifiReader.wifiLevel(context, current)

    fun cellLevel(context: Context, airplane: Boolean, current: Int): Int =
        CellReader.cellLevel(context, airplane, current)

    @SuppressLint("MissingPermission")
    fun cellLevels(context: Context, airplane: Boolean): List<Int> =
        CellReader.cellLevels(context, airplane)

    fun cellSignals(context: Context, airplane: Boolean): List<SimSignal> =
        CellReader.cellSignals(context, airplane)

    fun isWifiEnabled(context: Context, current: Boolean): Boolean =
        WifiReader.isWifiEnabled(context, current)

    fun isWifiActive(context: Context, current: Boolean): Boolean =
        WifiReader.isWifiActive(context, current)

    fun isWifiValidated(context: Context): Boolean = WifiReader.isWifiValidated(context)

    @SuppressLint("MissingPermission")
    fun networkGeneration(context: Context, airplane: Boolean, current: String): String =
        CellReader.networkGeneration(context, airplane, current)

    @SuppressLint("MissingPermission")
    fun isMobileDataEnabled(context: Context): Boolean = CellReader.isMobileDataEnabled(context)

    fun isAirplaneOn(context: Context): Boolean = DeviceStateReader.isAirplaneOn(context)

    fun isPowerSaveOn(context: Context): Boolean = DeviceStateReader.isPowerSaveOn(context)

    fun isDndOn(context: Context): Boolean = DeviceStateReader.isDndOn(context)
}
