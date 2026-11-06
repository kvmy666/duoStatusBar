package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.SettingsBridge

/**
 * Reads the user's settings out of the app's provider, from inside System UI (FR-03/16/17).
 *
 * Only the *shared contract constants* come from [DuoPrefs] — its storage methods are never called from
 * here, because inside this process `context.getSharedPreferences` would be System UI's own storage, not the
 * app's. The values arrive over the provider, which is the only channel that crosses the uid boundary.
 *
 * Every read is guarded and falls back to [DEFAULT]: a status bar must not care whether the settings app is
 * installed, running, or being upgraded.
 */
internal data class ModuleSettings(
    val enabled: Boolean,
    val useRive: Boolean,
    val showPercent: Boolean,
    val sizePercent: Int,
    val offsetX: Int,
    val liveApply: Boolean,
    val systemClockFont: Boolean,
    val revealMs: Int,
    val revision: Long,
    val tapAction: String,
    val doubleTapAction: String,
    val longPressAction: String,
    val indicatorsTapAction: String,
    val indicatorsDoubleTapAction: String,
    val indicatorsLongPressAction: String,
    val animationsEnabled: Boolean,
    val arrivalEnabled: Boolean,
    val departureEnabled: Boolean,
    val chargingEnabled: Boolean,
    /** Whether the charging bolt is drawn at all (separate from [chargingEnabled], the animation). */
    val showChargingBolt: Boolean,
    /** "auto" follows the bar's own icon colour; "black"/"white" are manual overrides. */
    val iconColor: String,
    /** False leaves the icons Duo does not replace (silent, vibrate, alarm…) visible. */
    val hideOtherIcons: Boolean,
    /** Whether Duo is drawn in the Quick Settings surface. */
    val showInQs: Boolean,
    /** Whether Duo is drawn in the full notification-shade header. */
    val showInShade: Boolean,
    /** Whether Airplane mode may take the middle slot. Wi-Fi and 5G/4G stay eligible either way. */
    val showAirplane: Boolean,
    /**
     * How Do Not Disturb is drawn while it is on: "off", "middle", or "dots".
     * "middle" is the moon in the ring. "dots" turns the signal dots into that moon.
     */
    val dndMode: String,
    /** Which cellular line the spheres show on a dual-SIM phone: "auto", "sim1" or "sim2". */
    val simChoice: String,
    /** 0 is the original percentage seat; 100 is fully raised, clear of a center punch-hole. */
    val percentHeight: Int,
    /** True draws the inner icons beside the ring, each with its own place. */
    val splitIndicators: Boolean,
    /** Horizontal nudge for that icon cluster, in dp. The ring keeps [offsetX]. */
    val indicatorsOffsetX: Int,
    /**
     * When true with [splitIndicators], the ring is centered on the status bar and [offsetX] nudges
     * from that center. A wider clock does not move it.
     */
    val lockRing: Boolean,
    /**
     * When true, airplane mode turns the four spheres into Wi-Fi strength. They stay cellular
     * while airplane mode is off, and they disappear in airplane mode with Wi-Fi off.
     */
    val wifiDots: Boolean,
    /**
     * Percent of the icon's width kept in the strip. 100 is the full slot; 0 lets the other
     * status icons sit against the screen edge.
     */
    val edgePadding: Int,
    /**
     * Which rendering engine the user asked for: "auto", "rive" or "canvas". Parsed with
     * [DuoPrefs.normalizeEngine] so an unknown value cannot blank the bar.
     */
    val engineMode: String = DuoPrefs.ENGINE_AUTO,
    val middleSlot: String = DuoPrefs.MIDDLE_SLOT_AUTO,
    val showWifiGlyph: Boolean = true,
    val showCellDots: Boolean = true,
    val eventMask: Int = DuoPrefs.EVENT_ALL,
    /** Whether a download may replace the ring's charging/battery display at all. */
    val downloadProgressEnabled: Boolean = true,
    /** Whether the main ring alternates its seat by one pixel each wall-clock hour. */
    val burnInEnabled: Boolean = false,
    /**
     * Stock status-bar icons the user chose to hide, as the canonical CSV published by the app
     * (for example `"alarm,battery,clock"`). Empty means none; [hideOtherIcons] is the separate
     * legacy "hide all" flag.
     */
    val hiddenStockIcons: String = "",
    /** Which ring drawing to load: [DuoPrefs.DESIGN_OLD] or [DuoPrefs.DESIGN_NEW]. */
    val designStyle: String = DuoPrefs.DESIGN_OLD
) {
    /** Whether Do Not Disturb may take the middle of the ring. */
    val showDnd: Boolean get() = DuoPrefs.dndInMiddle(dndMode)

    companion object {
        val DEFAULT = ModuleSettings(
            enabled = false,
            useRive = true,
            showPercent = true,
            sizePercent = 100,
            offsetX = 0,
            liveApply = true,
            systemClockFont = true,
            revealMs = DuoPrefs.DEFAULT_REVEAL_MS,
            revision = 0L,
            tapAction = "no_action",
            doubleTapAction = "no_action",
            longPressAction = "no_action",
            indicatorsTapAction = "no_action",
            indicatorsDoubleTapAction = "no_action",
            indicatorsLongPressAction = "no_action",
            animationsEnabled = true,
            arrivalEnabled = true,
            departureEnabled = true,
            chargingEnabled = true,
            showChargingBolt = true,
            iconColor = "auto",
            hideOtherIcons = false,
            showInQs = true,
            showInShade = true,
            showAirplane = true,
            dndMode = DuoPrefs.DND_MIDDLE,
            simChoice = "auto",
            percentHeight = DuoPrefs.DEFAULT_PERCENT_HEIGHT,
            splitIndicators = false,
            indicatorsOffsetX = 0,
            lockRing = false,
            wifiDots = false,
            edgePadding = DuoPrefs.DEFAULT_EDGE_PADDING,
            engineMode = DuoPrefs.ENGINE_AUTO,
            middleSlot = DuoPrefs.MIDDLE_SLOT_AUTO,
            showWifiGlyph = true,
            showCellDots = true,
            eventMask = DuoPrefs.EVENT_ALL,
            downloadProgressEnabled = true,
            burnInEnabled = false,
            hiddenStockIcons = "",
            designStyle = DuoPrefs.DESIGN_OLD
        )
    }
}

internal object DuoSettingsClient {

    private const val TAG = "DuoSB"

    /** How often the bridge may ask the app to push, so a broken provider cannot spam it. */
    private const val BRIDGE_REQUEST_MS = 15_000L

    /**
     * Orientation reported by the status-bar window's own configuration change. Resources on the
     * application context can lag that callback, so a read during the swap trusts this when it is set.
     */
    @Volatile
    private var forcedOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    fun useOrientation(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT ||
            orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            forcedOrientation = orientation
        }
    }

    private fun effectiveOrientation(context: Context): Int {
        val forced = forcedOrientation
        return if (forced == Configuration.ORIENTATION_PORTRAIT ||
            forced == Configuration.ORIENTATION_LANDSCAPE
        ) {
            forced
        } else {
            context.resources.configuration.orientation
        }
    }

    /**
     * A column added after a module is already running. Missing means the older default, so a
     * settings app that has not been updated yet cannot blank the status bar.
     */
    private fun Cursor.optionalInt(column: String, fallback: Int = 0): Int {
        val index = getColumnIndex(column)
        return if (index < 0) fallback else getInt(index)
    }

    /** A column the running settings app may not publish yet. Missing keeps [fallback]. */
    private fun Cursor.optionalBool(column: String, fallback: Boolean): Boolean {
        val index = getColumnIndex(column)
        return if (index < 0) fallback else getInt(index) == 1
    }

    /** A string column the running settings app may not publish yet. Missing keeps [fallback]. */
    private fun Cursor.optionalString(column: String, fallback: String): String {
        val index = getColumnIndex(column)
        if (index < 0) return fallback
        return getString(index) ?: fallback
    }

    /** Sent by the app after a write, so the module re-reads without polling (NFR-2). */
    const val ACTION_SETTINGS_CHANGED = "io.github.kvmy666.duostatusbar.SETTINGS_CHANGED"

    private val uri: Uri get() = Uri.parse("content://${DuoPrefs.AUTHORITY}")

    /**
     * Whether the *last* [read] could not reach the app's provider at all, as opposed to the provider
     * answering "the module is off". The two are indistinguishable in [ModuleSettings.DEFAULT] (both are
     * `enabled=false`) but need opposite handling: "off" is the user's wish, "unreachable" must not be
     * mistaken for it. ColorOS/realme showed exactly that failure — the module loaded, read nothing and
     * fell back to `stage 0`, so the element never drew even though the app's master switch was on.
     */
    @Volatile
    var providerUnreachable: Boolean = false
        private set

    // ------------------------------------------------------------ provider-independent bridge
    // On a ROM where System UI cannot see the app's provider (One UI 8: "Unknown authority"), settings
    // arrive over a broadcast. The payload is the exact provider row, parsed by the same [fromCursor], so
    // the two channels can never drift.

    @Volatile private var bridgeRegistered = false
    @Volatile private var bridge: ModuleSettings? = null
    @Volatile private var bridgeLandscape: ModuleSettings? = null
    @Volatile private var lastRequestAt = 0L

    private val bridgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != SettingsBridge.ACTION_SETTINGS_PUSH) return
            try {
                @Suppress("UNCHECKED_CAST")
                val values = intent.getSerializableExtra(SettingsBridge.EXTRA_VALUES) as? ArrayList<Any?>
                    ?: return
                if (values.isEmpty()) {
                    L.i("settings bridge receive: values=${values.size} columns=${DuoPrefs.COLUMNS.size} using=0")
                    return
                }
                val count = bridgeColumnCount(values.size, DuoPrefs.COLUMNS.size)
                val names = DuoPrefs.COLUMNS.copyOf(count)
                val cursor = MatrixCursor(names).apply { addRow(values.take(count).toTypedArray()) }
                cursor.moveToFirst()
                bridge = fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
                bridgeLandscape = fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE)
                providerUnreachable = false
                L.i("settings bridge receive: values=${values.size} columns=${DuoPrefs.COLUMNS.size} using=$count")
            } catch (t: Throwable) {
                L.w("settings bridge receive: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** Skew-safe column count for a bridge push that may come from a different build. */
    internal fun bridgeColumnCount(valueCount: Int, columnCount: Int): Int = minOf(valueCount, columnCount)

    /** Registers the bridge receiver once. Safe to call from the read path; never throws. */
    private fun ensureBridge(context: Context) {
        if (bridgeRegistered) return
        synchronized(this) {
            if (bridgeRegistered) return
            try {
                // EXPORTED so the app (a different uid) can deliver; the signature permission still
                // restricts the sender to our own app.
                context.registerReceiver(
                    bridgeReceiver,
                    IntentFilter(SettingsBridge.ACTION_SETTINGS_PUSH),
                    SettingsBridge.PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED
                )
                bridgeRegistered = true
                L.i("settings bridge registered")
            } catch (t: Throwable) {
                L.w("settings bridge register: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** Asks the app to push the settings, rate-limited so a broken provider cannot spam it. */
    private fun requestFromApp(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRequestAt < BRIDGE_REQUEST_MS) return
        lastRequestAt = now
        try {
            context.sendBroadcast(
                Intent(SettingsBridge.ACTION_SETTINGS_REQUEST).setPackage(BuildConfig.APPLICATION_ID)
            )
            L.i("settings bridge: requested from app")
        } catch (t: Throwable) {
            L.w("settings bridge request: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * The settings for the current orientation. The provider is tried first; when it cannot be reached,
     * the last bridge push is used, and the app is asked to push if the bridge is empty too.
     */
    fun read(context: Context): ModuleSettings {
        ensureBridge(context)
        val orientation = effectiveOrientation(context)
        val fromProvider = readProvider(context, orientation)
        if (fromProvider != null) {
            providerUnreachable = false
            return fromProvider
        }
        providerUnreachable = true
        val bridged = if (orientation == Configuration.ORIENTATION_LANDSCAPE) bridgeLandscape else bridge
        if (bridged != null) {
            L.i("settings via bridge (rev ${bridged.revision})")
            return bridged
        }
        requestFromApp(context)
        return ModuleSettings.DEFAULT
    }

    /** The provider read, or null when the provider could not be reached (as opposed to "off"). */
    private fun readProvider(context: Context, orientation: Int): ModuleSettings? = try {
        val started = android.os.SystemClock.elapsedRealtime()
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            // A provider that answers always returns a row (see DuoSettingsProvider), so a null cursor is
            // "could not be reached", never "the module is off".
            L.w("settings provider returned no cursor - trying the bridge")
            null
        } else {
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    // Answered without a row: also "unreachable", not "off".
                    L.w("settings provider returned no row - trying the bridge")
                    null
                } else {
                    val result = fromCursor(c, orientation)
                    // Logged because this is a synchronous binder call from System UI's boot path: if it
                    // is ever slow, it is slow there, and that deserves a number rather than a guess.
                    val which = if (orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
                    L.i("settings read in ${android.os.SystemClock.elapsedRealtime() - started} ms (rev ${result.revision}, $which)")
                    result
                }
            }
        }
    } catch (t: Throwable) {
        L.w("settings unreadable (${t.javaClass.simpleName}: ${t.message}) - trying the bridge")
        null
    }

    /**
     * True when the orientation that is *not* current has the element switched on.
     *
     * Boot in the off orientation would otherwise install nothing, and a later rotation would have no
     * hook to apply the other set. A missing landscape column counts as the portrait value, which is
     * what an older settings app publishes.
     */
    fun otherOrientationEnabled(context: Context): Boolean {
        val landscapeNow = effectiveOrientation(context) == Configuration.ORIENTATION_LANDSCAPE
        val other = if (landscapeNow) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        try {
            val column = if (landscapeNow) DuoPrefs.COL_ENABLED else DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED
            val read = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(column)
                val resolved = if (index >= 0) index else cursor.getColumnIndexOrThrow(DuoPrefs.COL_ENABLED)
                cursor.getInt(resolved) == 1
            }
            if (read != null) return read
        } catch (t: Throwable) {
            L.w("other orientation unreadable (${t.javaClass.simpleName}: ${t.message})")
        }
        val bridged = if (other == Configuration.ORIENTATION_LANDSCAPE) bridgeLandscape else bridge
        return bridged?.enabled ?: false
    }

    /**
     * Picks [orientation]'s set out of a provider row. Landscape columns that are absent (an older
     * settings app) fall back to the portrait set, so a missing column cannot blank the bar.
     */
    internal fun fromCursor(cursor: Cursor, orientation: Int): ModuleSettings {
        val portrait = portraitSettings(cursor)
        val chosen = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            cursor.landscapeSettings(portrait)
        } else {
            portrait
        }
        return chosen
    }

    private fun portraitSettings(cursor: Cursor): ModuleSettings {
        val legacyMiddle = cursor.optionalBool(
            DuoPrefs.COL_SHOW_DND,
            cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_NETWORK_ONLY)) != 1
        )
        return ModuleSettings(
        enabled = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_ENABLED)) == 1,
        useRive = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_USE_RIVE)) == 1,
        showPercent = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_SHOW_PERCENT)) == 1,
        sizePercent = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_SIZE_PERCENT)),
        offsetX = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_OFFSET_X)),
        liveApply = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_LIVE_APPLY)) == 1,
        systemClockFont = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_CLOCK_FONT)) == 1,
        revealMs = DuoPrefs.nearestReveal(cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_REVEAL_MS))),
        revision = cursor.getLong(cursor.getColumnIndexOrThrow(DuoPrefs.COL_REVISION)),
        tapAction = cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_TAP)) ?: "no_action",
        doubleTapAction = cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_DOUBLE_TAP)) ?: "no_action",
        longPressAction = cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_LONG_PRESS)) ?: "no_action",
        indicatorsTapAction = cursor.optionalString(
            DuoPrefs.COL_INDICATORS_TAP,
            cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_TAP)) ?: "no_action"
        ),
        indicatorsDoubleTapAction = cursor.optionalString(
            DuoPrefs.COL_INDICATORS_DOUBLE_TAP,
            cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_DOUBLE_TAP)) ?: "no_action"
        ),
        indicatorsLongPressAction = cursor.optionalString(
            DuoPrefs.COL_INDICATORS_LONG_PRESS,
            cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_LONG_PRESS)) ?: "no_action"
        ),
        animationsEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_ANIMATIONS)) == 1,
        arrivalEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_ARRIVAL)) == 1,
        departureEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_DEPARTURE)) == 1,
        chargingEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_CHARGING)) == 1,
        // Optional: a settings app that predates the column keeps the bolt (the original behaviour).
        showChargingBolt = cursor.optionalBool(DuoPrefs.COL_SHOW_CHARGING_BOLT, true),
        iconColor = cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_ICON_COLOR)) ?: "auto",
        hideOtherIcons = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_HIDE_OTHER_ICONS)) == 1,
        // Optional so a settings app that predates these columns keeps drawing both surfaces.
        showInQs = cursor.optionalBool(DuoPrefs.COL_SHOW_IN_QS, true),
        showInShade = cursor.optionalBool(DuoPrefs.COL_SHOW_IN_SHADE, true),
        showAirplane = cursor.optionalBool(
            DuoPrefs.COL_SHOW_AIRPLANE,
            cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_NETWORK_ONLY)) != 1
        ),
        dndMode = DuoPrefs.normalizeDndMode(
            cursor.optionalString(
                DuoPrefs.COL_DND_MODE,
                if (legacyMiddle) DuoPrefs.DND_MIDDLE else DuoPrefs.DND_OFF
            )
        ),
        simChoice = cursor.getString(cursor.getColumnIndexOrThrow(DuoPrefs.COL_SIM_CHOICE)) ?: "auto",
        percentHeight = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_PERCENT_HEIGHT))
            .coerceIn(DuoPrefs.MIN_PERCENT_HEIGHT, DuoPrefs.MAX_PERCENT_HEIGHT),
        splitIndicators = cursor.optionalInt(DuoPrefs.COL_SPLIT_INDICATORS) == 1,
        indicatorsOffsetX = cursor.optionalInt(DuoPrefs.COL_INDICATORS_OFFSET_X).coerceIn(
            -DuoPrefs.MAX_INDICATORS_OFFSET,
            DuoPrefs.MAX_INDICATORS_OFFSET
        ),
        // Missing means the ring stays in the strip, so an older settings app cannot pin it by accident.
        lockRing = cursor.optionalBool(DuoPrefs.COL_LOCK_RING, false),
        // Optional so a settings app that predates this column cannot blank the bar:
        // a missing column is the original cellular dots.
        wifiDots = cursor.optionalInt(DuoPrefs.COL_WIFI_DOTS) == 1,
        // Missing means the full slot, so an older settings app does not suddenly pull the other icons
        // against the screen edge.
        edgePadding = cursor.optionalInt(DuoPrefs.COL_EDGE_PADDING, DuoPrefs.DEFAULT_EDGE_PADDING)
            .coerceIn(DuoPrefs.MIN_EDGE_PADDING, DuoPrefs.MAX_EDGE_PADDING),
        // Missing falls back to the legacy use_rive switch: Rive when it was on, Canvas otherwise.
        engineMode = DuoPrefs.normalizeEngine(
            cursor.optionalString(
                DuoPrefs.COL_ENGINE_MODE,
                if (cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_USE_RIVE)) == 1) DuoPrefs.ENGINE_AUTO
                else DuoPrefs.ENGINE_CANVAS
            )
        ),
        middleSlot = DuoPrefs.normalizeMiddleSlot(cursor.optionalString(DuoPrefs.COL_MIDDLE_SLOT, DuoPrefs.MIDDLE_SLOT_AUTO)),
        // Optional so an older settings app keeps both glyphs.
        showWifiGlyph = cursor.optionalBool(DuoPrefs.COL_SHOW_WIFI_GLYPH, true),
        showCellDots = cursor.optionalBool(DuoPrefs.COL_SHOW_CELL_DOTS, true),
        eventMask = cursor.optionalInt(DuoPrefs.COL_EVENT_MASK, DuoPrefs.EVENT_ALL) and DuoPrefs.EVENT_ALL,
        // Optional: a settings app that predates the column keeps the download display on.
        downloadProgressEnabled = cursor.optionalBool(DuoPrefs.COL_DOWNLOAD_PROGRESS, true),
        // Optional: missing means the original fixed seat.
        burnInEnabled = cursor.optionalBool(DuoPrefs.COL_BURN_IN, false),
        // Optional: an older settings app has no per-icon list, which means hide nothing.
        hiddenStockIcons = cursor.optionalString(DuoPrefs.COL_HIDDEN_STOCK_ICONS, ""),
        // Optional: missing or unknown keeps the classic drawing, which is what existing installs draw.
        designStyle = DuoPrefs.normalizeDesignStyle(
            cursor.optionalString(DuoPrefs.COL_DESIGN_STYLE, DuoPrefs.DESIGN_OLD)
        )
        )
    }

    /** Landscape fields, each falling back to the portrait value when that column is not in the row. */
    private fun Cursor.landscapeSettings(portrait: ModuleSettings): ModuleSettings {
        if (getColumnIndex(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED) < 0) return portrait
        fun col(base: String) = DuoPrefs.LAND_PREFIX + base
        fun int(base: String, default: Int): Int {
            val index = getColumnIndex(col(base))
            return if (index < 0) default else getInt(index)
        }
        fun bool(base: String, default: Boolean): Boolean {
            val index = getColumnIndex(col(base))
            return if (index < 0) default else getInt(index) == 1
        }
        fun str(base: String, default: String): String {
            val index = getColumnIndex(col(base))
            return if (index < 0) default else getString(index) ?: default
        }
        // The mode column is newer than the on/off switch. A landscape row that only has the
        // switch keeps that choice; one that has neither keeps the portrait mode.
        fun landscapeDndMode(portrait: ModuleSettings): String {
            val mode = getColumnIndex(col(DuoPrefs.COL_DND_MODE))
            if (mode >= 0) return DuoPrefs.normalizeDndMode(getString(mode) ?: portrait.dndMode)
            if (getColumnIndex(col(DuoPrefs.COL_SHOW_DND)) >= 0) {
                return if (bool(DuoPrefs.COL_SHOW_DND, portrait.showDnd)) DuoPrefs.DND_MIDDLE else DuoPrefs.DND_OFF
            }
            return portrait.dndMode
        }
        return portrait.copy(
            enabled = bool(DuoPrefs.COL_ENABLED, portrait.enabled),
            useRive = bool(DuoPrefs.COL_USE_RIVE, portrait.useRive),
            showPercent = bool(DuoPrefs.COL_SHOW_PERCENT, portrait.showPercent),
            sizePercent = int(DuoPrefs.COL_SIZE_PERCENT, portrait.sizePercent),
            offsetX = int(DuoPrefs.COL_OFFSET_X, portrait.offsetX),
            liveApply = bool(DuoPrefs.COL_LIVE_APPLY, portrait.liveApply),
            systemClockFont = bool(DuoPrefs.COL_CLOCK_FONT, portrait.systemClockFont),
            revealMs = DuoPrefs.nearestReveal(int(DuoPrefs.COL_REVEAL_MS, portrait.revealMs)),
            tapAction = str(DuoPrefs.COL_TAP, portrait.tapAction),
            doubleTapAction = str(DuoPrefs.COL_DOUBLE_TAP, portrait.doubleTapAction),
            longPressAction = str(DuoPrefs.COL_LONG_PRESS, portrait.longPressAction),
            indicatorsTapAction = str(DuoPrefs.COL_INDICATORS_TAP, portrait.indicatorsTapAction),
            indicatorsDoubleTapAction = str(
                DuoPrefs.COL_INDICATORS_DOUBLE_TAP,
                portrait.indicatorsDoubleTapAction
            ),
            indicatorsLongPressAction = str(
                DuoPrefs.COL_INDICATORS_LONG_PRESS,
                portrait.indicatorsLongPressAction
            ),
            animationsEnabled = bool(DuoPrefs.COL_ANIMATIONS, portrait.animationsEnabled),
            arrivalEnabled = bool(DuoPrefs.COL_ARRIVAL, portrait.arrivalEnabled),
            departureEnabled = bool(DuoPrefs.COL_DEPARTURE, portrait.departureEnabled),
            chargingEnabled = bool(DuoPrefs.COL_CHARGING, portrait.chargingEnabled),
            showChargingBolt = bool(DuoPrefs.COL_SHOW_CHARGING_BOLT, portrait.showChargingBolt),
            iconColor = str(DuoPrefs.COL_ICON_COLOR, portrait.iconColor),
            hideOtherIcons = bool(DuoPrefs.COL_HIDE_OTHER_ICONS, portrait.hideOtherIcons),
            showInQs = bool(DuoPrefs.COL_SHOW_IN_QS, portrait.showInQs),
            showInShade = bool(DuoPrefs.COL_SHOW_IN_SHADE, portrait.showInShade),
            showAirplane = bool(DuoPrefs.COL_SHOW_AIRPLANE, portrait.showAirplane),
            dndMode = landscapeDndMode(portrait),
            simChoice = str(DuoPrefs.COL_SIM_CHOICE, portrait.simChoice),
            percentHeight = int(DuoPrefs.COL_PERCENT_HEIGHT, portrait.percentHeight)
                .coerceIn(DuoPrefs.MIN_PERCENT_HEIGHT, DuoPrefs.MAX_PERCENT_HEIGHT),
            splitIndicators = bool(DuoPrefs.COL_SPLIT_INDICATORS, portrait.splitIndicators),
            indicatorsOffsetX = int(
                DuoPrefs.COL_INDICATORS_OFFSET_X,
                portrait.indicatorsOffsetX
            ).coerceIn(-DuoPrefs.MAX_INDICATORS_OFFSET, DuoPrefs.MAX_INDICATORS_OFFSET),
            lockRing = bool(DuoPrefs.COL_LOCK_RING, portrait.lockRing),
            wifiDots = bool(DuoPrefs.COL_WIFI_DOTS, portrait.wifiDots),
            edgePadding = int(DuoPrefs.COL_EDGE_PADDING, portrait.edgePadding)
                .coerceIn(DuoPrefs.MIN_EDGE_PADDING, DuoPrefs.MAX_EDGE_PADDING),
            engineMode = DuoPrefs.normalizeEngine(str(DuoPrefs.COL_ENGINE_MODE, portrait.engineMode)),
            middleSlot = DuoPrefs.normalizeMiddleSlot(str(DuoPrefs.COL_MIDDLE_SLOT, portrait.middleSlot)),
            showWifiGlyph = bool(DuoPrefs.COL_SHOW_WIFI_GLYPH, portrait.showWifiGlyph),
            showCellDots = bool(DuoPrefs.COL_SHOW_CELL_DOTS, portrait.showCellDots),
            eventMask = int(DuoPrefs.COL_EVENT_MASK, portrait.eventMask) and DuoPrefs.EVENT_ALL,
            downloadProgressEnabled = bool(
                DuoPrefs.COL_DOWNLOAD_PROGRESS,
                portrait.downloadProgressEnabled
            ),
            burnInEnabled = bool(DuoPrefs.COL_BURN_IN, portrait.burnInEnabled),
            hiddenStockIcons = str(DuoPrefs.COL_HIDDEN_STOCK_ICONS, portrait.hiddenStockIcons),
            designStyle = DuoPrefs.normalizeDesignStyle(
                str(DuoPrefs.COL_DESIGN_STYLE, portrait.designStyle)
            )
        )
    }

    /**
     * Tells the app what the module actually did, for the diagnostics screen. The provider is tried
     * first; only when it fails (the One UI 8 case) does the broadcast bridge carry the report. Never
     * throws.
     */
    fun report(context: Context, status: String) {
        if (!reportViaProvider(context, "status", "status", status)) {
            broadcastToApp(context, SettingsBridge.ACTION_STATUS_PUSH, SettingsBridge.EXTRA_STATUS, status)
        }
    }

    /**
     * Sends the debug diagnostic dump to the app so its "Save status to a file" button can ship it.
     * Separate from [report] because the dump is large and debug-only: the compact status line is the
     * release path. Never throws.
     */
    fun reportDump(context: Context, dump: String) {
        if (!reportViaProvider(context, "dump", "dump", dump)) {
            broadcastToApp(context, SettingsBridge.ACTION_DUMP_PUSH, SettingsBridge.EXTRA_DUMP, dump)
        }
    }

    /**
     * Tells the app the module had to fall back — Rive could not draw, so the simple Canvas element is
     * being used — so the app can ask the user to send the log. An empty [reason] clears the alert (sent
     * at each module load). Never throws.
     */
    fun reportFallback(context: Context, reason: String) {
        if (!reportViaProvider(context, "fallback", "fallback", reason)) {
            broadcastToApp(context, SettingsBridge.ACTION_FALLBACK_PUSH, SettingsBridge.EXTRA_FALLBACK, reason)
        }
    }

    /**
     * Publishes the live stock-icon inventory to the app (A4). The provider is tried first, exactly like
     * [report]; only when it fails (the One UI 8 case) does the broadcast bridge carry the list. Never
     * throws, because this runs inside System UI on attach and on every hiding pass.
     */
    fun reportStockIcons(context: Context, json: String) {
        if (!reportViaProvider(context, "stock_icons", "stock_icons", json)) {
            broadcastToApp(
                context,
                SettingsBridge.ACTION_STOCK_ICONS_PUSH,
                SettingsBridge.EXTRA_STOCK_ICONS,
                json
            )
        }
    }

    /** True when the provider accepted the report; false means the bridge should carry it instead. */
    private fun reportViaProvider(context: Context, method: String, key: String, value: String): Boolean =
        try {
            val extras = Bundle().apply { putString(key, value) }
            context.contentResolver.call(uri, method, null, extras) != null
        } catch (t: Throwable) {
            L.w("$method report failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }

    /** The provider-independent report path: a broadcast the app's receiver stores. Never throws. */
    private fun broadcastToApp(context: Context, action: String, key: String, value: String) {
        try {
            context.sendBroadcast(
                Intent(action).setPackage(BuildConfig.APPLICATION_ID).putExtra(key, value)
            )
        } catch (t: Throwable) {
            L.w("bridge report: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
