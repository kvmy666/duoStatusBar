package io.github.kvmy666.duostatusbar.hook

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
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
    /** Whether the Wi-Fi radio is on: off hands the middle slot to the cellular generation (FR-06). */
    private var wifiOn = true
    /** Whether Wi-Fi is the active data path; false means the phone is really on mobile data. */
    private var wifiActive = true
    /** The cellular generation shown when Wi-Fi is off: "5G"/"4G"/"3G"/"2G", or empty. */
    private var networkText = ""
    private var registered = false
    /** When the last arrival fired, for the AOD burst guard. */
    private var lastRevealAt = 0L

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Removes the pending hide when the screen comes back before the departure has finished. */
    private val hideRunnable = Runnable { host.setElementsVisible(false) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> onBatteryChanged(intent)
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> onPowerSaveChanged()
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> onAirplaneChanged()
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> onDndChanged()
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onScreenOn()
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_CONFIGURATION_CHANGED -> onRotation()
                    WifiManager.RSSI_CHANGED_ACTION,
                    WifiManager.WIFI_STATE_CHANGED_ACTION,
                    // Which network is actually carrying data can change without the Wi-Fi radio doing
                    // anything (a captive portal drops the default route to cellular), so the active
                    // path is re-read on connectivity changes too.
                    ConnectivityManager.CONNECTIVITY_ACTION,
                    ACTION_SERVICE_STATE_CHANGED -> refresh()
                }
            } catch (t: Throwable) {
                L.e("receiver ${intent?.action}: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun onBatteryChanged(intent: Intent) {
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val target = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, level) * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
        val wasCharging = charging
        charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
        if (charging != wasCharging) L.i("charging -> $charging")
        setLevel(target)
    }

    private fun onPowerSaveChanged() {
        saver = SystemReaders.isPowerSaveOn(context)
        render()
    }

    private fun onAirplaneChanged() {
        airplane = SystemReaders.isAirplaneOn(context)
        // Re-read the network generation too: airplane on blanks it, airplane off restores the label
        // the slot may need if Wi-Fi is also off.
        refresh()
    }

    // FR-06: the middle slot shows the moon while DND/silent is on. Both signals are event-driven (no
    // polling): the zen filter, and the ringer dropping to silent.
    private fun onDndChanged() {
        val was = dnd
        dnd = SystemReaders.isDndOn(context)
        if (dnd != was) L.i("dnd -> $dnd (middle slot now shows the moon)")
        render()
    }

    // FR-25: reveal on every screen-on and every unlock. The ring re-fills from 0 with it, so the fill
    // animation is part of the arrival rather than a one-off at boot.
    //
    // Only when the device is actually interactive. The always-on display cycles doze -> suspend -> off
    // -> on, and every one of those fires SCREEN_ON: measured on the device, one lock/AOD cycle produced
    // ~25 arrivals, so the element re-arrived over and over instead of sitting still. isInteractive() is
    // false throughout the AOD, which is exactly the line between "the user woke the phone" and "the
    // panel blinked".
    private fun onScreenOn() {
        // The element comes back on ANY screen-on, the AOD's included: it belongs to the status bar, and
        // the AOD has one. What it must not do there is *arrive* - the AOD pulses several times a second,
        // and an arrival per pulse is the flashing this started as. So the display is restored
        // unconditionally and the animation only when the user actually woke the phone.
        handler.removeCallbacks(hideRunnable)
        host.setElementsVisible(true)
        visible = true
        // FR-25: the arrival animation is optional. With it off the element simply appears; the fill
        // still catches up below.
        if (host.arrivalEnabled && isInteractive() && revealAllowed()) {
            host.revealAll(host.revealMs)
            restartFill()
        } else {
            setLevel(level)
        }
    }

    // FR-25: the departure plays as the screen goes, so the element leaves with the rest of the display
    // rather than blinking out with it. Tied to the SCREEN, not the lock: the lock screen is supposed to
    // show the element (FR-03b), so locking must not dismiss it. Same AOD guard, for the same reason.
    private fun onScreenOff() {
        // The departure plays as the panel goes, then the element comes off the display entirely. It has
        // to be the whole view, not just the Rive `visible` flag: on the always-on display the bar is
        // re-laid out several times a second, and an element that is only half hidden flickers with it.
        // The AOD has its own status bar, so the element has no business being there.
        handler.removeCallbacks(hideRunnable)
        if (host.departureEnabled) {
            visible = false
            render()
            handler.postDelayed(hideRunnable, DEPART_HIDE_MS)
        } else {
            // No departure animation: take the element off the display at once, and leave `visible`
            // alone so the Rive machine never plays the Depart timeline.
            host.setElementsVisible(false)
        }
    }

    /** Rotation re-inflates the strip: hide the stock views again. */
    private fun onRotation() {
        host.reapplyHiding()
        render()
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
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(ACTION_SERVICE_STATE_CHANGED)
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
        runCatching { handler.removeCallbacks(hideRunnable) }
        runCatching { context.unregisterReceiver(receiver) }
    }

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

    /**
     * Whether the user is actually looking at the phone, as opposed to the always-on display.
     *
     * Read live rather than cached: the whole point is the AOD's rapid doze/suspend cycling, and a
     * cached answer would be stale exactly when it matters. Defaults to true when unreadable, so a
     * failure means "behave as before" rather than "never animate again".
     */
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

    /** Re-reads Wi-Fi, cellular and the network generation, then redraws. Never throws. */
    fun refresh() {
        try {
            wifiOn = SystemReaders.isWifiEnabled(context, wifiOn)
            wifiActive = SystemReaders.isWifiActive(context, wifiActive)
            wifiLevel = SystemReaders.wifiLevel(context, wifiLevel)
            cellLevel = SystemReaders.cellLevel(context, airplane, cellLevel)
            networkText = SystemReaders.networkGeneration(context, airplane, networkText)
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
        // The master animation switch also owns the ring fill: off means the level snaps.
        if (!host.animationsEnabled) {
            fill?.cancel()
            displayedLevel = target
            render()
            return
        }
        if (target == displayedLevel && fill?.isRunning != true) {
            render()
            return
        }
        fill?.cancel()
        fill = ValueAnimator.ofInt(displayedLevel, target).apply {
            // The fill follows the arrival speed, so "faster animations" means a faster fill too.
            duration = FILL_MS * host.revealMs / 1000L
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
                    visible = visible,
                    wifiOn = wifiOn,
                    wifiConnected = wifiActive && wifiLevel > 0,
                    networkText = networkText,
                    animateCharge = host.chargingEnabled
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

        /** How long the departure gets before the element is taken off the display. */
        const val DEPART_HIDE_MS = 450L

        /** `TelephonyManager.ACTION_SERVICE_STATE_CHANGED`, spelled out: the constant is not public. */
        const val ACTION_SERVICE_STATE_CHANGED = "android.intent.action.SERVICE_STATE"
    }
}
