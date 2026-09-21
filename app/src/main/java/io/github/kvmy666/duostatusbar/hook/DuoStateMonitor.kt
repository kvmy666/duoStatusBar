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
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
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
    private var charging = false
    private var saver = false
    private var airplane = false
    /** FR-25: false plays the departure as the screen goes off. */
    private var visible = true
    private var dnd = false
    private var wifiLevel = 3
    private var cellLevel = 4
    private var registered = false
    /** When the last arrival fired, for the AOD burst guard. */
    private var lastRevealAt = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        val target = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, level) * 100 / scale
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                        val wasCharging = charging
                        charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
                        if (charging != wasCharging) L.i("charging -> $charging")
                        setLevel(target)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        saver = SystemReaders.isPowerSaveOn(context)
                        render()
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        airplane = SystemReaders.isAirplaneOn(context)
                        render()
                    }
                    // FR-06: the middle slot shows the moon while DND/silent is on. Both signals are
                    // event-driven (no polling): the zen filter, and the ringer dropping to silent.
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val was = dnd
                        dnd = SystemReaders.isDndOn(context)
                        if (dnd != was) L.i("dnd -> $dnd (middle slot now shows the moon)")
                        render()
                    }
                    // FR-25: reveal on every screen-on and every unlock. The ring re-fills from 0 with
                    // it, so the fill animation is part of the arrival rather than a one-off at boot.
                    //
                    // Only when the device is actually interactive. The always-on display cycles
                    // doze -> suspend -> off -> on, and every one of those fires SCREEN_ON: measured on
                    // the device, one lock/AOD cycle produced ~25 arrivals, so the element re-arrived
                    // over and over instead of sitting still. isInteractive() is false throughout the
                    // AOD, which is exactly the line between "the user woke the phone" and "the panel
                    // blinked".
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> if (isInteractive() && revealAllowed()) {
                        visible = true
                        host.revealAll(host.revealMs)
                        restartFill()
                    }
                    // FR-25: the departure plays as the screen goes, so the element leaves with the rest
                    // of the display rather than blinking out with it. Tied to the SCREEN, not the lock:
                    // the lock screen is supposed to show the element (FR-03b), so locking must not
                    // dismiss it. Same AOD guard, for the same reason.
                    Intent.ACTION_SCREEN_OFF -> if (isInteractive()) {
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
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * Whether the user is actually looking at the phone, as opposed to the always-on display.
     *
     * Read live rather than cached: the whole point is the AOD's rapid doze/suspend cycling, and a
     * cached answer would be stale exactly when it matters. Defaults to true when unreadable, so a
     * failure means "behave as before" rather than "never animate again".
     */
    /**
     * Rate-limits arrivals to one per [REVEAL_DEBOUNCE_MS].
     *
     * `isInteractive()` is the principled filter, but it is not perfect: the AOD still produced ~6
     * arrivals per cycle after it (measured), because some of the doze transitions report interactive
     * for a moment. A status bar element that re-arrives six times while the phone is in a pocket is a
     * bug whatever the reason, so a burst is collapsed to a single arrival as well.
     */
    private fun revealAllowed(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRevealAt < REVEAL_DEBOUNCE_MS) return false
        lastRevealAt = now
        return true
    }

    private fun isInteractive(): Boolean = try {
        // The display's own state, not PowerManager.isInteractive(): during the AOD the panel pulses
        // DOZE -> ON -> DOZE and isInteractive() reports true for those moments, which is how a single
        // AOD cycle still produced 3 arrivals after the first fix. Only STATE_ON means the user is
        // actually looking at the phone.
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displays?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            display.state == Display.STATE_ON
        } else {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            power?.isInteractive ?: true
        }
    } catch (t: Throwable) {
        L.w("isInteractive: ${t.javaClass.simpleName}: ${t.message}")
        true
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

        /** A burst of SCREEN_ON within this window is one arrival, not several. */
        const val REVEAL_DEBOUNCE_MS = 900L
    }
}
