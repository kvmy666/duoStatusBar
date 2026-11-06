package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import io.github.kvmy666.duostatusbar.L

/** One Wi-Fi read: the measured RSSI, both bar scales, the three-arc result and its source. */
internal data class WifiSample(
    /** `WifiInfo.INVALID_RSSI` (-127) when no RSSI could be read. */
    val rssi: Int,
    /** `WifiManager.calculateSignalLevel(rssi, 5)`, the platform's own five-step value. */
    val calc5: Int,
    /** The count actually fed to [DuoMapping.wifiBars] (five-step shifted to four stock bars). */
    val stockBars: Int,
    /** Duo's Wi-Fi glyph level 0..3. */
    val dots: Int,
    val on: Boolean,
    val active: Boolean,
    val validated: Boolean,
    /** `active`, `connectionInfo`, `stale` or `off`. */
    val source: String
)

/**
 * The Wi-Fi platform reads behind the Duo element.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 */
internal object WifiReader {

    private const val INVALID_RSSI = -127

    /**
     * Wi-Fi 0..3, or the previous value when Wi-Fi cannot be queried.
     *
     * The level comes from `WifiManager.calculateSignalLevel`, the platform's own RSSI-to-bars mapping,
     * rather than fixed dBm thresholds: the local thresholds were strict enough that a genuinely strong
     * link read as 2 of 3 (user-reported). The RSSI is taken from the *active* network's `WifiInfo` where
     * possible, so it matches the icon the ROM itself draws, and `connectionInfo` is only the fallback.
     */
    fun wifiLevel(context: Context, current: Int): Int =
        sample(context, current, currentOn = true, currentActive = true, currentValidated = true).dots

    /**
     * The same read, with the evidence the visual mapping cannot carry: the RSSI, the platform's
     * five-step value, the post-shift stock bar count and where the RSSI came from.
     *
     * [currentLevel]/[currentOn]/[currentActive]/[currentValidated] are the caller's last values, kept
     * when the platform cannot be queried (FR-21), and the sample is marked `src=stale` so a report
     * does not mistake it for a fresh read.
     */
    fun sample(
        context: Context,
        currentLevel: Int,
        currentOn: Boolean,
        currentActive: Boolean,
        currentValidated: Boolean
    ): WifiSample = try {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) {
            stale(currentLevel, currentOn, currentActive, currentValidated)
        } else {
            val on = wifi.isWifiEnabled
            // One active-network snapshot for active/validated/RSSI. The three separate helpers
            // each did their own activeNetwork/getNetworkCapabilities binder call per refresh.
            val caps = try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            } catch (_: Throwable) {
                null
            }
            val active = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: currentActive
            val validated = if (active) {
                caps != null &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                false
            }
            if (!on) {
                WifiSample(INVALID_RSSI, 0, 0, 0, on = false, active = false, validated = false, source = "off")
            } else {
                val activeRssi = try {
                    (caps?.transportInfo as? android.net.wifi.WifiInfo)?.rssi
                } catch (_: Throwable) {
                    null
                }
                // The fallback read is only needed when the active network did not expose a WifiInfo.
                val connectionRssi = if (activeRssi == null) {
                    try {
                        wifi.connectionInfo?.rssi
                    } catch (_: Throwable) {
                        null
                    }
                } else {
                    null
                }
                val rssi = activeRssi ?: connectionRssi
                val source = if (activeRssi != null) "active" else "connectionInfo"
                when {
                    rssi == null -> stale(currentLevel, on, active, validated)
                    rssi == INVALID_RSSI ->
                        WifiSample(INVALID_RSSI, 0, 0, 0, on, active, validated, source)
                    else -> {
                        // The stock icon draws four bars; Duo draws three. `calculateSignalLevel` uses
                        // the AOSP thresholds, which on OxygenOS report one bar fewer than the icon
                        // shows (measured: stock 3 bars read as 2). Shift to the stock count, then
                        // compress.
                        val calc5 = WifiManager.calculateSignalLevel(rssi, 5)
                        val stockBars = SignalMapping.stockWifiBars(calc5)
                        WifiSample(
                            rssi = rssi,
                            calc5 = calc5,
                            stockBars = stockBars,
                            dots = DuoMapping.wifiBars(stockBars),
                            on = on,
                            active = active,
                            validated = validated,
                            source = source
                        )
                    }
                }
            }
        }
    } catch (t: Throwable) {
        L.w("wifiLevel: ${t.message}")
        stale(currentLevel, currentOn, currentActive, currentValidated)
    }

    private fun stale(
        currentLevel: Int,
        on: Boolean,
        active: Boolean,
        validated: Boolean
    ): WifiSample = WifiSample(
        rssi = INVALID_RSSI,
        calc5 = 0,
        stockBars = 0,
        dots = currentLevel.coerceIn(0, 3),
        on = on,
        active = active,
        validated = validated,
        source = "stale"
    )

    /** One bounded Wi-Fi diagnostic line per refresh. */
    fun sampleLine(sample: WifiSample): String =
        "wifi sample rssi=${sample.rssi} calc5=${sample.calc5} stockBars=${sample.stockBars}" +
            " dots=${sample.dots} on=${sample.on} active=${sample.active}" +
            " validated=${sample.validated} src=${sample.source}"

    /** Whether the Wi-Fi radio is on at all — distinct from "connected", which is a signal level. */
    fun isWifiEnabled(context: Context, current: Boolean): Boolean = try {
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled ?: current
    } catch (t: Throwable) {
        L.w("isWifiEnabled: ${t.message}")
        current
    }

    /**
     * Whether Wi-Fi is the network the phone is actually using for data.
     *
     * This is the difference between "Wi-Fi is connected" and "Wi-Fi is carrying traffic": a captive
     * portal or an internet-less AP leaves Wi-Fi connected while Android routes everything over mobile
     * data, and the stock bar shows the cellular icon. The element must follow the active path, or it
     * shows a Wi-Fi glyph while the user is plainly on 4G/5G (user-reported bug). Read from the default
     * network's transport; unreadable keeps the caller's value.
     */
    fun isWifiActive(context: Context, current: Boolean): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: current
    } catch (t: Throwable) {
        L.w("isWifiActive: ${t.message}")
        current
    }

    /**
     * Whether the active Wi-Fi network actually has internet (validated), not merely a link.
     *
     * A captive portal or an internet-less AP leaves Wi-Fi connected; Android routes over cellular and the
     * stock bar shows the cellular icon. The element follows the validated state so it never shows a live
     * Wi-Fi glyph while the phone is really on mobile data.
     */
    fun isWifiValidated(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (t: Throwable) {
        L.w("isWifiValidated: ${t.message}")
        false
    }
}
