package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import io.github.kvmy666.duostatusbar.L

/**
 * Watches the state the Duo element shows and pushes it into the drawing.
 *
 * Receiver-driven rather than polled (NFR-2): battery, power-save, airplane, rotation and screen
 * events are broadcasts; Wi-Fi and cellular are read through [SystemReaders]. Nothing polls, and no
 * hook name is guessed (FR-26); if a read fails the element keeps its last value (FR-21).
 */
internal class DuoStateMonitor(private val context: Context, private val host: DuoIconHost) {

    private var level = 100
    private var charging = false
    private var saver = false
    private var airplane = false
    private var wifiLevel = 3
    private var cellLevel = 4
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, level) * 100 / scale
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                        charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
                        render()
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        saver = SystemReaders.isPowerSaveOn(context)
                        render()
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        airplane = SystemReaders.isAirplaneOn(context)
                        render()
                    }
                    // FR-25: reveal on every screen-on and every unlock.
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> host.duo?.reveal()
                    // Rotation re-inflates the strip: hide the stock views again.
                    Intent.ACTION_CONFIGURATION_CHANGED -> {
                        host.reapplyHiding()
                        render()
                    }
                    WifiManager.RSSI_CHANGED_ACTION,
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> refresh()
                }
            } catch (t: Throwable) {
                L.e("receiver ${intent?.action}: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            saver = SystemReaders.isPowerSaveOn(context)
            airplane = SystemReaders.isAirplaneOn(context)
            refresh()
            L.i("state monitor up: level=$level charging=$charging saver=$saver airplane=$airplane")
        } catch (t: Throwable) {
            L.e("monitor start failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    /** Re-reads Wi-Fi and cellular, then redraws. Never throws. */
    fun refresh() {
        try {
            wifiLevel = SystemReaders.wifiLevel(context, wifiLevel)
            cellLevel = SystemReaders.cellLevel(context, airplane, cellLevel)
            render()
        } catch (t: Throwable) {
            L.w("refresh: ${t.message}")
        }
    }

    private fun render() {
        val view = host.duo ?: return
        try {
            view.render(
                DuoMapping.visual(
                    level = level,
                    charging = charging,
                    saver = saver,
                    showPercent = host.showPercent,
                    wifiLevel = wifiLevel,
                    cellLevel = cellLevel,
                    airplane = airplane
                )
            )
        } catch (t: Throwable) {
            L.e("render: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private companion object {
        const val TAG = "DuoSB"
    }
}
