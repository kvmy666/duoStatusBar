package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * The one process-wide broadcast path for every iOS host (R4 item 7).
 *
 * Measured on the device, each live host registered its own identical 10-action receiver, so three
 * surfaces meant three copies of every event and three invalidations
 * (`.pm/device/r4/broadcasts.txt:5453-5465,8960-8972,12296-12308`). This hub keeps exactly one
 * receiver, invalidates the shared [IosReadings] snapshot once per event, and then hands the action to
 * every subscribed host on the main thread.
 *
 * The receiver is never exported: the module only listens to platform broadcasts inside SystemUI.
 */
internal interface IosBroadcastTarget {
    /** Called on the main thread for every broadcast the hub receives. */
    fun onIosBroadcast(action: String)
}

internal object IosBroadcastHub {

    /** Exactly the actions the old per-host receiver filters carried. */
    private val ACTIONS = arrayOf(
        Intent.ACTION_BATTERY_CHANGED,
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
        Intent.ACTION_SCREEN_ON,
        Intent.ACTION_SCREEN_OFF,
        Intent.ACTION_TIME_TICK,
        ConnectivityManager.CONNECTIVITY_ACTION,
        IosReplaceHost.ACTION_SERVICE_STATE,
        IosReplaceHost.ACTION_ANY_DATA_STATE,
        IosReplaceHost.ACTION_WIFI_RSSI,
        IosReplaceHost.ACTION_WIFI_STATE
    )

    private val lock = Any()
    private val targets = ArrayList<IosBroadcastTarget>()
    private var receiver: BroadcastReceiver? = null
    private var receiverContext: Context? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Adds [target] and registers the single receiver with the first context. Subscribing the same
     * host twice is a no-op, so a re-attach cannot create a second registration (or a second copy of
     * every event).
     */
    fun subscribe(context: Context, target: IosBroadcastTarget) {
        synchronized(lock) {
            if (targets.none { it === target }) targets.add(target)
            ensureRegistered(context)
        }
    }

    /** Removes [target]; the receiver is unregistered when the last host leaves. */
    fun unsubscribe(target: IosBroadcastTarget) {
        synchronized(lock) {
            targets.removeAll { it === target }
            if (targets.isEmpty()) unregister()
        }
    }

    /** Test seam: deliver one action exactly as a received broadcast would. */
    internal fun dispatchForTest(action: String) {
        dispatch(action)
    }

    /** Test seam: 1 while the shared receiver is registered, 0 otherwise. */
    internal fun receiverCountForTest(): Int = synchronized(lock) { if (receiver != null) 1 else 0 }

    /** Test seam: drops every subscriber and unregisters, so tests do not leak a receiver. */
    internal fun resetForTest() {
        synchronized(lock) {
            targets.clear()
            unregister()
        }
    }

    private fun ensureRegistered(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext ?: context
        val filter = IntentFilter().apply { ACTIONS.forEach(::addAction) }
        val next = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                dispatch(action)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(next, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                app.registerReceiver(next, filter)
            }
            receiver = next
            receiverContext = app
        } catch (_: Throwable) {
            // Fail silent: a subscription is still recorded, and the next subscribe retries.
            receiver = null
            receiverContext = null
        }
    }

    private fun unregister() {
        val current = receiver
        val context = receiverContext
        receiver = null
        receiverContext = null
        if (current != null && context != null) {
            try {
                context.unregisterReceiver(current)
            } catch (_: Throwable) {
            }
        }
    }

    private fun dispatch(action: String) {
        if (ACTIONS.none { it == action }) return
        val snapshot = synchronized(lock) { targets.toList() }
        if (snapshot.isEmpty()) return
        // Exactly one invalidation per received event, before any host refreshes.
        try {
            IosReadings.invalidate()
        } catch (_: Throwable) {
        }
        val deliver = Runnable {
            for (target in snapshot) {
                try {
                    target.onIosBroadcast(action)
                } catch (_: Throwable) {
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver.run()
        } else {
            mainHandler.post(deliver)
        }
    }
}

/**
 * Per-host power accounting for the diagnostic dump (R4 item 7).
 *
 * Pure and Android-free: the clock is injected so the rate windows are deterministic in a unit test.
 * Totals are permanent; the timestamps only back the last-60-second columns.
 */
internal class IosPowerCounters(
    private val clock: () -> Long,
    private val windowMs: Long = 60_000L
) {

    private class Series {
        var total = 0L
        private val stamps = ArrayDeque<Long>()

        fun record(now: Long) {
            total++
            stamps.addLast(now)
        }

        fun prune(now: Long, windowMs: Long) {
            while (stamps.isNotEmpty() && now - stamps.first() >= windowMs) stamps.removeFirst()
        }

        fun inWindow(): Int = stamps.size
    }

    private val attaches = Series()
    private val refreshes = Series()
    private val invalidates = Series()
    private var suppressed = 0L

    fun onAttach() = record(attaches)

    fun onRefresh() = record(refreshes)

    fun onInvalidate() = record(invalidates)

    /** A refresh request the minimum spacing pushed past the debounce (coalesced, not dropped). */
    fun onSuppressedRefresh() {
        suppressed++
    }

    /** Keeps the series bounded to the window even when no dump asks for a line for hours. */
    private fun record(series: Series) {
        val now = clock()
        series.record(now)
        series.prune(now, windowMs)
    }

    /** One compact line for the dump, prefixed with the host's surface name. */
    fun line(surface: String, receivers: Int): String {
        val now = clock()
        attaches.prune(now, windowMs)
        refreshes.prune(now, windowMs)
        invalidates.prune(now, windowMs)
        return "ios surface=$surface power: attaches=${attaches.total} " +
            "refreshes=${refreshes.total}/min=${refreshes.inWindow()} " +
            "invalidates=${invalidates.total}/min=${invalidates.inWindow()} " +
            "suppressed=$suppressed receivers=$receivers"
    }
}

/** The permanent minimum spacing between two refreshes on one host (at most four per second). */
internal const val MIN_REFRESH_SPACING_MS = 250L

/**
 * The delay before the next debounced refresh.
 *
 * [debounceMs] is the normal burst collapse. The result is never smaller than the time still needed
 * to honour [minSpacingMs] after [lastRefreshAt], so a broadcast burst cannot exceed four refreshes a
 * second. A zero/negative [lastRefreshAt] means "no refresh yet" and returns the debounce unchanged.
 */
internal fun nextRefreshDelayMs(
    lastRefreshAt: Long,
    now: Long,
    debounceMs: Long,
    minSpacingMs: Long
): Long {
    if (lastRefreshAt <= 0L) return debounceMs
    val spacingRemaining = (lastRefreshAt + minSpacingMs - now).coerceAtLeast(0L)
    return maxOf(debounceMs, spacingRemaining)
}
