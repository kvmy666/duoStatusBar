package io.github.kvmy666.duostatusbar.hook

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
    /** What the ring is actually drawn at; it chases [level] so the fill animates instead of jumping. */
    private var displayedLevel = 0
    private var fill: ValueAnimator? = null
    /** 0 = the Wi-Fi owns the middle slot, 1 = the airplane/moon does; tweened, never jumped. */
    private var middleBlend = 0f
    private var middleFade: ValueAnimator? = null
    private var charging = false
    private var saver = false
    private var airplane = false
    /** FR-25: false plays the departure as the screen goes off. */
    private var visible = true
    private var dnd = false
    private var wifiLevel = 3
    private var cellLevel = 4
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        val target = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, level) * 100 / scale
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                        charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
                        setLevel(target)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        saver = SystemReaders.isPowerSaveOn(context)
                        render()
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        airplane = SystemReaders.isAirplaneOn(context)
                        animateMiddleSlot()
                    }
                    // FR-06: the middle slot shows the moon while DND/silent is on. Both signals are
                    // event-driven (no polling): the zen filter, and the ringer dropping to silent.
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val was = dnd
                        dnd = SystemReaders.isDndOn(context)
                        if (dnd != was) L.i("dnd -> $dnd (middle slot now shows the moon)")
                        animateMiddleSlot()
                    }
                    // FR-25: reveal on every screen-on and every unlock. The ring re-fills from 0 with
                    // it, so the fill animation is part of the arrival rather than a one-off at boot.
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        visible = true
                        host.revealAll(host.revealMs)
                        restartFill()
                    }
                    // FR-25: the departure plays as the screen goes, so the element leaves with the rest
                    // of the display rather than blinking out with it. Tied to the SCREEN, not the lock:
                    // the lock screen is supposed to show the element (FR-03b), so locking must not
                    // dismiss it.
                    Intent.ACTION_SCREEN_OFF -> {
                        visible = false
                        render()
                    }
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
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            saver = SystemReaders.isPowerSaveOn(context)
            airplane = SystemReaders.isAirplaneOn(context)
            dnd = SystemReaders.isDndOn(context)
            refresh()
            L.i("state monitor up: level=$level charging=$charging saver=$saver airplane=$airplane dnd=$dnd")
        } catch (t: Throwable) {
            L.e("monitor start failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { fill?.cancel() }
        runCatching { middleFade?.cancel() }
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

    /**
     * Animates the ring from where it is to [target] rather than snapping (user feedback: transitions
     * should be animated). Runs from 0 on first attach, so the element fills up on the reveal.
     */
    private fun setLevel(target: Int) {
        level = target
        if (target == displayedLevel && fill?.isRunning != true) {
            render()
            return
        }
        fill?.cancel()
        fill = ValueAnimator.ofInt(displayedLevel, target).apply {
            duration = FILL_MS
            addUpdateListener {
                displayedLevel = it.animatedValue as Int
                render()
            }
            start()
        }
    }

    /** Draws the ring from empty again, then fills to the current level - the reveal's fill half. */
    private fun restartFill() {
        displayedLevel = 0
        setLevel(level)
    }

    /**
     * Crossfades the middle slot between the Wi-Fi and whichever of airplane/DND owns it (FR-06/FR-16).
     *
     * Without this the slot cuts: the host used to write the Wi-Fi's opacities to 0 and the plane's
     * to 1 in the same frame, so the Wi-Fi vanished and the plane popped rather than the Wi-Fi hiding
     * and the airplane coming in. The airplane's `airplaneState` is set from the current flags on the
     * next render, so the state machine's collapse-and-grow starts on the same frame this does.
     */
    private fun animateMiddleSlot() {
        val target = if (airplane || dnd) 1f else 0f
        if (target == middleBlend && middleFade?.isRunning != true) {
            render()
            return
        }
        middleFade?.cancel()
        middleFade = ValueAnimator.ofFloat(middleBlend, target).apply {
            duration = MIDDLE_MS
            addUpdateListener {
                middleBlend = it.animatedValue as Float
                render()
            }
            start()
        }
    }

    private fun render() {
        // Every element the host owns - the main bar's and, on the lock screen, the keyguard bar's.
        if (host.duo == null) return
        try {
            host.render(
                DuoMapping.visual(
                    level = displayedLevel,
                    charging = charging,
                    saver = saver,
                    showPercent = host.showPercent,
                    wifiLevel = wifiLevel,
                    cellLevel = cellLevel,
                    airplane = airplane,
                    dnd = dnd,
                    middleBlend = middleBlend,
                    visible = visible
                )
            )
        } catch (t: Throwable) {
            L.e("render: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private companion object {
        const val TAG = "DuoSB"

        /** How long the ring takes to fill to a new percentage (4x slower per user feedback). */
        const val FILL_MS = 2_400L

        /** The middle slot's hand-over. Matches the 12-frame (200 ms) PlaneMorph in scene.rml. */
        const val MIDDLE_MS = 200L
    }
}
