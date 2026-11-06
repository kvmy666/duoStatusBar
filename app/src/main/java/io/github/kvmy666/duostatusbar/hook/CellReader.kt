package io.github.kvmy666.duostatusbar.hook

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.github.kvmy666.duostatusbar.L
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * The cellular platform reads behind the Duo element.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 */
internal object CellReader {

    /** `Settings.Global.MOBILE_DATA`. Hidden from the SDK stub, so the name is spelled out. */
    const val MOBILE_DATA_SETTING = "mobile_data"

    /**
     * Reflective method handles are cached for the process. Every connectivity/RSSI refresh used to
     * look up `createForSubscriptionId`, the per-RAT signal getter and `ServiceState.getNrState` again
     * on SystemUI's main thread; the lookup itself is the expensive part, not the call.
     */
    private val methodCache = ConcurrentHashMap<String, Optional<Method>>()

    private fun method(cls: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        val key = cls.name + "#" + name + parameterTypes.joinToString(",") { it.name }
        return methodCache.computeIfAbsent(key) {
            Optional.ofNullable(runCatching { cls.getMethod(name, *parameterTypes) }.getOrNull())
        }.orElse(null)
    }

    /** Cellular spheres 0..4. */
    fun cellLevel(context: Context, airplane: Boolean, current: Int): Int = try {
        if (airplane) 0
        else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val raw = tm?.signalStrength?.level ?: return current
            SignalMapping.cellDots(raw, maxSignalLevels(), serviceInService(tm))
        }
    } catch (t: Throwable) {
        L.w("cellLevel: ${t.message}")
        current
    }

    /**
     * Per-SIM cellular levels (0..4), in SIM-slot order, or an empty list when there is only one active
     * subscription. The dual-SIM display splits the four spheres into two pairs, one per line, so it needs
     * both levels at once (the user's request; iOS shows both carriers' signal strength).
     */
    fun cellLevels(context: Context, airplane: Boolean): List<Int> =
        cellSignals(context, airplane).map { it.level }

    /**
     * Active subscriptions with their SIM slot, id and four-dot level. The slot is preserved because
     * the UI's sim1/sim2 choice is a slot choice, not an active-list position.
     */
    fun cellSignals(context: Context, airplane: Boolean): List<SimSignal> =
        read(context, airplane).samples.map {
            SimSignal(slot = it.slot, subscriptionId = it.subscriptionId, level = it.dots)
        }

    /**
     * One refresh's cellular snapshot: one sample per active subscription plus the default line.
     *
     * The legacy reader returned only mapped dots, so a report could not tell a raw radio value from a
     * mapping failure or a different SIM's line. This reads each subscription once, keeps the raw
     * index/count/RAT/metric, and marks a failed per-subscription read `src=unavailable` instead of
     * silently reporting 0 dots. [fallbackDots] is the caller's current value, used when the default
     * read fails (FR-21).
     */
    @SuppressLint("MissingPermission")
    fun read(context: Context, airplane: Boolean, fallbackDots: Int = -1): CellReading {
        val count = maxSignalLevels()
        val defaultSub = defaultSubscriptionId()
        if (airplane) {
            return CellReading(
                samples = emptyList(),
                count = count,
                defaultRaw = -1,
                defaultMaxIndex = SignalMapping.cellMaxIndex(-1, count),
                defaultDots = 0,
                defaultSub = defaultSub,
                defaultSrc = SignalMapping.SRC_DEFAULT,
                airplane = true
            )
        }
        val default = readDefault(context, count, fallbackDots)
        val samples = try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            sm?.activeSubscriptionInfoList.orEmpty()
                .sortedBy { it.simSlotIndex }
                .map {
                    sampleFor(
                        context = context,
                        slot = it.simSlotIndex,
                        subId = it.subscriptionId,
                        count = count,
                        fallbackDots = default.dots
                    )
                }
        } catch (t: Throwable) {
            L.w("cellSignals: ${t.message}")
            emptyList()
        }
        return CellReading(
            samples = samples,
            count = count,
            defaultRaw = default.raw,
            defaultMaxIndex = default.maxIndex,
            defaultDots = default.dots,
            defaultSub = defaultSub,
            defaultSrc = default.src,
            airplane = false
        )
    }

    /** The default line's raw/dots and whether the read succeeded or kept the caller's value. */
    private data class DefaultSignal(
        val raw: Int,
        val maxIndex: Int,
        val dots: Int,
        val src: String
    )

    private fun readDefault(context: Context, count: Int, fallbackDots: Int): DefaultSignal {
        val fallback = fallbackDots.coerceIn(0, 4)
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val ss = tm?.signalStrength
            if (ss == null) {
                DefaultSignal(-1, SignalMapping.cellMaxIndex(-1, count), fallback, SignalMapping.SRC_STALE)
            } else {
            val raw = ss.level
            val maxIndex = SignalMapping.cellMaxIndex(raw, count)
            DefaultSignal(
                raw,
                maxIndex,
                SignalMapping.cellDots(raw, count, serviceInService(tm)),
                SignalMapping.SRC_DEFAULT
            )
            }
        } catch (t: Throwable) {
            L.w("cellLevel: ${t.message}")
            DefaultSignal(-1, SignalMapping.cellMaxIndex(-1, count), fallback, SignalMapping.SRC_STALE)
        }
    }

    /** One subscription's sample; a failed per-sub read is marked unavailable, never reported as 0. */
    @SuppressLint("MissingPermission")
    private fun sampleFor(
        context: Context,
        slot: Int,
        subId: Int,
        count: Int,
        fallbackDots: Int
    ): CellSample = try {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val perSub = base?.let { tm ->
            method(tm.javaClass, "createForSubscriptionId", Int::class.javaPrimitiveType!!)
                ?.invoke(tm, subId) as? TelephonyManager
        }
        val ss = perSub?.signalStrength
        if (ss == null) {
            unavailable(slot, subId, count, fallbackDots)
        } else {
            val raw = ss.level
            val maxIndex = SignalMapping.cellMaxIndex(raw, count)
            val (dbm, metric) = signalMetric(ss)
            CellSample(
                slot = slot,
                subscriptionId = subId,
                raw = raw,
                count = count,
                maxIndex = maxIndex,
                rat = ratOf(ss),
                dbm = dbm,
                metric = metric,
                dots = SignalMapping.cellDots(raw, count, serviceInService(perSub)),
                src = SignalMapping.SRC_PER_SUB,
                airplane = false
            )
        }
    } catch (t: Throwable) {
        L.w("cellLevel[$subId]: ${t.message}")
        unavailable(slot, subId, count, fallbackDots)
    }

    private fun unavailable(slot: Int, subId: Int, count: Int, fallbackDots: Int): CellSample =
        CellSample(
            slot = slot,
            subscriptionId = subId,
            raw = -1,
            count = count,
            maxIndex = SignalMapping.cellMaxIndex(-1, count),
            rat = "-",
            dbm = -999,
            metric = "-",
            dots = fallbackDots.coerceIn(0, 4),
            src = SignalMapping.SRC_UNAVAILABLE,
            airplane = false
        )

    /**
     * The primary signal's dBm and metric. Kept to one lookup per refresh: the RAT-specific getter
     * (`getRsrp`/`getSsRsrp`/...) is preferred, and `getDbm` is the guarded fallback.
     */
    private fun signalMetric(ss: android.telephony.SignalStrength): Pair<Int, String> {
        val primary = try {
            ss.cellSignalStrengths.firstOrNull()
        } catch (_: Throwable) {
            null
        } ?: return -999 to "-"
        val name = primary.javaClass.simpleName
        val candidates = when {
            name.contains("Lte") -> listOf("getRsrp" to "rsrp", "getRssi" to "rssi")
            name.contains("Nr") -> listOf("getSsRsrp" to "rsrp", "getRsrp" to "rsrp")
            name.contains("Wcdma") || name.contains("Cdma") -> listOf("getRscp" to "rscp", "getRssi" to "rssi")
            name.contains("Gsm") -> listOf("getRssi" to "rssi")
            else -> emptyList()
        }
        for ((getter, metric) in candidates) {
            val value = readDbm(primary, getter)
            if (value != null) return value to metric
        }
        val generic = readDbm(primary, "getDbm")
        return (generic ?: -999) to (if (generic != null) "dbm" else "-")
    }

    private fun readDbm(primary: Any, getter: String): Int? = try {
        val value = method(primary.javaClass, getter)?.invoke(primary) as? Int
        // CellInfo.UNAVAILABLE is Int.MAX_VALUE; any non-negative value is a sentinel, not dBm.
        if (value == null || value > 0 || value < -200) null else value
    } catch (_: Throwable) {
        null
    }

    private fun ratOf(ss: android.telephony.SignalStrength): String = try {
        val name = ss.cellSignalStrengths.firstOrNull()?.javaClass?.simpleName ?: "-"
        name.removePrefix("CellSignalStrength").ifEmpty { "-" }
    } catch (_: Throwable) {
        "-"
    }

    /** The default data subscription; -1 when it cannot be read. */
    @SuppressLint("MissingPermission")
    private fun defaultSubscriptionId(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId()
    } catch (_: Throwable) {
        -1
    }

    /**
     * Whether the radio is registered to a network ("in service"). Guarded: any failure returns true so
     * a read that cannot determine service keeps the previous behaviour (raw 0 -> first bar) instead of
     * blanking a working bar. No new permissions: ServiceState comes from TelephonyManager.getServiceState(),
     * already reflected for NR state; CellInfo is not consulted.
     *
     * Fallback when ServiceState is unavailable: a known data/voice network type means registered.
     * Fallback when even that is unavailable: true (preserve the old "raw 0 = first bar" behaviour).
     */
    fun hasService(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        serviceInService(tm)
    } catch (_: Throwable) {
        true
    }

    /** Service-state check for one TelephonyManager (default or per-subscription). */
    private fun serviceInService(tm: TelephonyManager?): Boolean {
        if (tm == null) return true
        return try {
            val serviceState = method(tm.javaClass, "getServiceState")?.invoke(tm)
            val state = serviceState?.let {
                method(it.javaClass, "getState")?.invoke(it) as? Int
            }
            if (state != null) {
                // ServiceState.STATE_IN_SERVICE == 0.
                state == 0
            } else {
                val data = tm.dataNetworkType
                val voice = tm.voiceNetworkType
                data != TelephonyManager.NETWORK_TYPE_UNKNOWN ||
                    voice != TelephonyManager.NETWORK_TYPE_UNKNOWN
            }
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Number of levels the platform draws for cellular bars. Current SystemUI uses 5; older/other
     * devices may use 4. The value is only used to scale into Duo's four dots, never shown as a bar.
     *
     * Cached once per process: reflection + a log line belongs at startup, not on every refresh.
     */
    @Volatile
    private var cachedLevels: Int? = null

    fun maxSignalLevels(): Int {
        cachedLevels?.let { return it }
        synchronized(this) {
            cachedLevels?.let { return it }
            val reflected = try {
                // Public on current platform docs but absent from this compile SDK's stub; reflect so
                // the module still builds and falls back to the historical four levels.
                val cls = Class.forName("android.telephony.CellSignalStrength")
                (cls.getMethod("getNumSignalStrengthLevels").invoke(null) as? Int)?.coerceIn(1, 8)
            } catch (t: Throwable) {
                L.w("maxSignalLevels: ${t.message}")
                null
            }
            val levels = reflected ?: 4
            L.i("cell levels -> $levels source=${if (reflected != null) "reflection" else "fallback"}")
            cachedLevels = levels
            return levels
        }
    }

    /** Active subscription ids in SIM-slot order; empty when unavailable. */
    @SuppressLint("MissingPermission")
    fun subscriptionIds(context: Context): List<Int> = try {
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        sm?.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }.map { it.subscriptionId }
    } catch (t: Throwable) {
        L.w("subscriptionIds: ${t.message}")
        emptyList()
    }

    /**
     * The legacy line, frozen by `docs/rom-contract.md`. It still carries the selected dots and the
     * already-mapped per-subscription dots; the new `cell sample`/`cell pick` lines carry the raw
     * evidence that this line cannot.
     */
    fun diagnostic(
        context: Context,
        airplane: Boolean,
        level: Int,
        levels: List<Int>,
        generation: String
    ): String = legacyLine(maxSignalLevels(), subscriptionIds(context), airplane, level, levels, generation)

    /** The same legacy line, built from the snapshot already read so a refresh does not read twice. */
    fun diagnostic(
        reading: CellReading,
        level: Int,
        levels: List<Int>,
        generation: String
    ): String = legacyLine(reading.count, reading.subs, reading.airplane, level, levels, generation)

    private fun legacyLine(
        count: Int,
        subs: List<Int>,
        airplane: Boolean,
        level: Int,
        levels: List<Int>,
        generation: String
    ): String = "cell raw level=$level levels=$levels generation=${generation.ifEmpty { "off" }}" +
        " max=$count subs=$subs airplane=$airplane"

    /**
     * The cellular generation the phone is on ("5G"/"4G"/"3G"/"2G"), or empty when there is no
     * service. Empty is a real answer here (unknown type, or mobile data switched off), so it is
     * returned as-is rather than masked by the last value; only a failed read keeps the caller's
     * value (FR-21).
     *
     * Data off is empty on purpose. The voice radio stays registered, so the network type and NR
     * state would still say 5G/4G; the middle slot is the data-path label, and with data off it goes.
     */
    @SuppressLint("MissingPermission")
    fun networkGeneration(context: Context, airplane: Boolean, current: String): String = try {
        if (airplane) ""
        else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val data = tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            val type = if (data != TelephonyManager.NETWORK_TYPE_UNKNOWN) data
            else tm?.voiceNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            DuoMapping.networkGeneration(
                type,
                nrConnected = isNrConnected(tm),
                dataEnabled = isMobileDataEnabled(context)
            )
        }
    } catch (t: Throwable) {
        L.w("networkGeneration: ${t.message}")
        current
    }

    /**
     * Whether the user has mobile data switched on for the default data line.
     *
     * Unreadable means "still on": blanking a working 5G/4G label is worse than keeping it (FR-21).
     * The settings key is only the fallback; `TelephonyManager.isDataEnabled()` is the toggle itself,
     * including the per-SIM switch the quick-settings tile writes.
     */
    @SuppressLint("MissingPermission")
    fun isMobileDataEnabled(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm != null) tm.isDataEnabled else mobileDataSetting(context)
    } catch (t: Throwable) {
        L.w("isMobileDataEnabled: ${t.message}")
        mobileDataSetting(context)
    }

    /**
     * `Settings.Global.MOBILE_DATA` (`"mobile_data"`). Hidden from the SDK stub, so the name is
     * spelled out. Defaults to on when the setting cannot be read.
     */
    private fun mobileDataSetting(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, MOBILE_DATA_SETTING, 1) == 1
    } catch (_: Throwable) {
        true
    }

    /**
     * Whether the radio is actually on NR (5G).
     *
     * On 5G NSA - which is what most carriers run - `getDataNetworkType()` reports LTE even while the
     * stock bar shows 5G, which is why the element said 4G on a 5G phone. `ServiceState.getNrState()`
     * is what the stock OxygenOS bar itself reads (`OplusMobileSignalExImpl`), so it is read here the
     * same way. Both calls are hidden/reflected and guarded: unreadable means "not NR", which only
     * costs a 5G label, never the status bar.
     */
    private fun isNrConnected(tm: TelephonyManager?): Boolean = try {
        if (tm == null) false
        else {
            val serviceState = method(tm.javaClass, "getServiceState")?.invoke(tm)
            val nrState = serviceState?.let {
                method(it.javaClass, "getNrState")?.invoke(it) as? Int
            }
            // ServiceState.NR_STATE_CONNECTED (2) / NR_STATE_NOT_RESTRICTED (3).
            nrState == 2 || nrState == 3
        }
    } catch (t: Throwable) {
        false
    }
}
