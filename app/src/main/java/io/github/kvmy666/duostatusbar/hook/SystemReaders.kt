package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log

/**
 * The platform reads behind the Duo element, kept apart from the monitor so each one can be tested
 * and swapped per ROM (Phase 7) without touching the wiring.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 */
internal object SystemReaders {

    private const val TAG = "DuoSB"

    /** Wi-Fi 0..3, or the previous value when Wi-Fi cannot be queried. */
    fun wifiLevel(context: Context, current: Int): Int = try {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        when {
            wifi == null -> current
            !wifi.isWifiEnabled -> 0
            else -> when (val rssi = wifi.connectionInfo?.rssi ?: current) {
                -127 -> 0
                in -85..-71 -> 1
                in -70..-56 -> 2
                in -55..0 -> 3
                else -> 1
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "wifiLevel: ${t.message}")
        current
    }

    /** Cellular spheres 0..4. */
    fun cellLevel(context: Context, airplane: Boolean, current: Int): Int = try {
        if (airplane) 0
        else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.signalStrength?.level?.coerceIn(0, 4) ?: current
        }
    } catch (t: Throwable) {
        Log.w(TAG, "cellLevel: ${t.message}")
        current
    }

    fun isAirplaneOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (_: Throwable) {
        false
    }

    fun isPowerSaveOn(context: Context): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false
    } catch (_: Throwable) {
        false
    }
}
