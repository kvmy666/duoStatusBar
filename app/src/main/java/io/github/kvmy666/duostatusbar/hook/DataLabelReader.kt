package io.github.kvmy666.duostatusbar.hook

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.github.kvmy666.duostatusbar.L
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** R4A item 3: the active data path's label plus whether the active network is cellular. */
internal data class DataLabelSample(
    /** `5G`/`5G+`/`LTE`/`4G`/`3G`/`E`/`G`; empty when data is off or the type is unknown. */
    val label: String,
    /** True only when the active network is cellular and not Wi-Fi. */
    val mobileActive: Boolean,
    /** `active` when the connectivity read succeeded, `unavailable` otherwise. */
    val source: String
)

/**
 * The iOS data-type label source (R4A item 3, state only; R4B draws the glyph).
 *
 * Reads the active network's transports, then the default data subscription's network type / NR
 * state / NR frequency range, all guarded so a status bar that cannot query telephony keeps drawing
 * (fail silent). The pure type -> label table lives in [SignalMapping.dataTypeLabel].
 */
internal object DataLabelReader {

    const val SRC_ACTIVE = "active"
    const val SRC_UNAVAILABLE = "unavailable"

    /** Same per-process handle cache as [CellReader]: the lookup, not the call, is the cost. */
    private val methodCache = ConcurrentHashMap<String, Optional<Method>>()

    private fun method(cls: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        val key = cls.name + "#" + name + parameterTypes.joinToString(",") { it.name }
        return methodCache.computeIfAbsent(key) {
            Optional.ofNullable(runCatching { cls.getMethod(name, *parameterTypes) }.getOrNull())
        }.orElse(null)
    }

    /** One guarded snapshot; never throws and never returns null. */
    @SuppressLint("MissingPermission")
    fun read(context: Context): DataLabelSample = try {
        val caps = activeCapabilities(context)
        val mobileActive = caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        val base = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (t: Throwable) {
            L.w("dataLabel telephony: ${t.message}")
            null
        }
        val tm = perDataSub(base)
        val dataEnabled = try {
            tm?.isDataEnabled ?: true
        } catch (_: Throwable) {
            true
        }
        val type = try {
            tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        } catch (_: Throwable) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
        val serviceState = try {
            tm?.let { method(it.javaClass, "getServiceState")?.invoke(it) }
        } catch (_: Throwable) {
            null
        }
        val nrConnected = nrState(serviceState)?.let { it == 2 || it == 3 } ?: false
        // ServiceState.NR_FREQUENCY_RANGE_MMWAVE == 4.
        val mmWave = nrFrequencyRange(serviceState) == 4

        DataLabelSample(
            label = SignalMapping.dataTypeLabel(type, nrConnected, mmWave, dataEnabled),
            mobileActive = mobileActive,
            source = if (caps != null) SRC_ACTIVE else SRC_UNAVAILABLE
        )
    } catch (t: Throwable) {
        L.w("dataLabel: ${t.message}")
        DataLabelSample("", false, SRC_UNAVAILABLE)
    }

    private fun activeCapabilities(context: Context): NetworkCapabilities? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    } catch (_: Throwable) {
        null
    }

    /** The default data subscription's own manager; the base manager when it cannot be resolved. */
    @SuppressLint("MissingPermission")
    private fun perDataSub(base: TelephonyManager?): TelephonyManager? {
        if (base == null) return null
        val subId = try {
            SubscriptionManager.getDefaultDataSubscriptionId()
        } catch (_: Throwable) {
            -1
        }
        if (subId <= 0) return base
        return try {
            method(base.javaClass, "createForSubscriptionId", Int::class.javaPrimitiveType!!)
                ?.invoke(base, subId) as? TelephonyManager
        } catch (_: Throwable) {
            null
        } ?: base
    }

    private fun nrState(serviceState: Any?): Int? = try {
        serviceState?.let { method(it.javaClass, "getNrState")?.invoke(it) as? Int }
    } catch (_: Throwable) {
        null
    }

    private fun nrFrequencyRange(serviceState: Any?): Int? = try {
        serviceState?.let { method(it.javaClass, "getNrFrequencyRange")?.invoke(it) as? Int }
    } catch (_: Throwable) {
        null
    }
}
