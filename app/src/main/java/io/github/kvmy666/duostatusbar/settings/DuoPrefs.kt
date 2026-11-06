package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings

/**
 * The user's settings, and the contract that carries them into System UI (FR-03/16/17).
 *
 * Why a provider rather than the obvious `Settings.Global`: this app is a normal app, so it cannot write
 * global settings (`WRITE_SECURE_SETTINGS` is a privileged permission it will never hold). The module, on
 * the other hand, runs *inside* System UI and can read anything — so the app owns the values and publishes
 * them, and the module reads them:
 *
 *     app  --writes--> SharedPreferences --publishes--> exported ContentProvider
 *                                                              |
 *                                          module (System UI) reads via ContentResolver
 *
 * The provider is exported without a permission on purpose: System UI is a different uid and holds no
 * signature permission of ours, so requiring one would make the channel unusable. What it exposes is
 * non-sensitive — whether the element is on, which renderer, its size and offset. Nothing here is a secret,
 * and a rogue reader can only learn that the module is installed.
 *
 * `duo_statusbar_stage` in `Settings.Global` remains the **developer override and kill switch**, and it wins
 * when it is present (see the module's `DuoGuard`): `adb shell settings put global duo_statusbar_stage 0`
 * must always be able to switch the module off regardless of what this app says.
 */
data class DuoSettings(
    /** FR-03: the master switch. */
    val enabled: Boolean = false,
    /** Prefer Rive; false means "always use the Canvas drawing" (no native code in the path). */
    val useRive: Boolean = true,
    /** FR-16: show the battery percentage in the ring's top gap. */
    val showPercent: Boolean = true,
    /**
     * FR-03/17: element size as a percentage of the measured slot. This is the **physical** value the
     * module draws with, kept in the original 50..220 range so old settings and the provider contract
     * stay valid. The app shows the equivalent logical 0..100 value through
     * [DuoPrefs.physicalToLogical] / [DuoPrefs.logicalToPhysical].
     */
    val sizePercent: Int = 100,
    /** FR-17: horizontal nudge inside the slot, in dp, from the drag editor. */
    val offsetX: Int = 0,
    /**
     * How far the battery percentage is raised. 0 is the original seat in the ring's top gap;
     * 100 is fully raised, clear of a center punch-hole. The ring does not move.
     */
    val percentHeight: Int = 100,
    /**
     * When true, the icons that sit inside the ring (Wi-Fi, airplane, Do Not Disturb) are
     * drawn beside it instead, so the ring can stay on a camera cutout. Off is the original element.
     */
    val splitIndicators: Boolean = false,
    /** Horizontal nudge for that icon cluster, in dp. Independent of [offsetX], which moves the ring. */
    val indicatorsOffsetX: Int = 0,
    /**
     * When true, and [splitIndicators] is on, the ring is centered on the status bar and [offsetX] nudges
     * it from that center. The clock changing width does not move it. Off leaves the ring in the icon strip.
     */
    val lockRing: Boolean = false,
    /**
     * Whether a size/position change reaches the running status bar immediately. Off means the value
     * is saved but only applied on the next start (the Restart System UI button), which is the safe
     * path: resizing the Rive view while System UI runs is what used to take it down.
     */
    val liveApply: Boolean = true,
    /**
     * Whether the status-bar clock is redrawn in the phone's own system font. The module never hides
     * the clock; this only swaps its typeface so it matches the element's digits.
     */
    val systemClockFont: Boolean = true,
    /**
     * FR-25: how long the arrival takes, in ms. One Rive timeline played at five speeds, so this is a
     * choice from [DuoPrefs.REVEAL_CHOICES] rather than a free number.
     */
    val revealMs: Int = 1000,
    /**
     * The Animations section (FR-25). `animationsEnabled` is the master switch; when it is off none of
     * the decorative motion plays. The three switches below pick which of them are allowed when the
     * master is on. Off means the change is instant, not that the feature stops working.
     */
    val animationsEnabled: Boolean = true,
    val arrivalEnabled: Boolean = true,
    val departureEnabled: Boolean = true,
    val chargingEnabled: Boolean = true,
    /**
     * Whether the charging bolt is drawn at all. Independent of [chargingEnabled], which only decides
     * whether the bolt *travels*; this one removes it, so "no charging icon" is a choice and not a side
     * effect of turning the animation off.
     */
    val showChargingBolt: Boolean = true,
    /**
     * FR-05/18: what a gesture on the element asks Auto Expand to do. Its action keys, or `no_action`.
     * The defaults mean the element consumes no touches at all — no gestures, no conflicts.
     */
    val tapAction: String = "no_action",
    val doubleTapAction: String = "no_action",
    val longPressAction: String = "no_action",
    /**
     * The icon cluster gets its own actions while [splitIndicators] is on. These are ignored in the
     * original one-piece layout. Missing stored values inherit the ring actions so an existing split
     * setup keeps behaving exactly as it did before this distinction was added.
     */
    val indicatorsTapAction: String = "no_action",
    val indicatorsDoubleTapAction: String = "no_action",
    val indicatorsLongPressAction: String = "no_action",
    /**
     * How the element picks its black/white foreground: `"auto"` follows the status bar itself (captured
     * from SystemUI's own icon tint, with system day/night as a fallback), `"black"` and `"white"` are
     * manual overrides.
     */
    val iconColor: String = "auto",
    /**
     * Whether the status icons Duo does *not* replace (silent, vibrate, alarm, clock…) stay hidden.
     * True is the classic look: only the ring. False leaves them visible beside the ring.
     * This remains the legacy all-or-nothing switch; per-icon hiding uses [hiddenStockIcons].
     */
    val hideOtherIcons: Boolean = false,
    /**
     * The stock status-bar icons the user chose to hide, stored as a CSV of stable keys
     * (for example `"alarm,battery,clock"`). Empty means none. The format is canonical: keys are
     * trimmed, blanks dropped, duplicates collapsed and the list sorted by
     * [DuoPrefs.joinHiddenStockIcons], so reading and writing cannot produce two spellings of the
     * same set. [hideOtherIcons] stays the separate "hide all" flag.
     */
    val hiddenStockIcons: String = "",
    /**
     * Which ring design is drawn: [DESIGN_OLD] is the classic 112 px ring in `duo_old.riv`,
     * [DESIGN_NEW] is the 104 px ring in `duo.riv`. Both draw through the same state machine and
     * bindings; the old drawing is optically scaled by [DuoPrefs.OLD_OPTICAL_SCALE] so the two
     * designs have the same on-screen ring size.
     */
    val designStyle: String = DESIGN_OLD,
    /** Whether Duo is drawn in the Quick Settings surface at all. Off leaves that header stock. */
    val showInQs: Boolean = true,
    /** Whether Duo is drawn in the full notification-shade header. Off leaves that header stock. */
    val showInShade: Boolean = true,
    /**
     * Whether Airplane mode may replace Wi-Fi and the 5G/4G label in the middle of the ring.
     * On by default, which is the original priority.
     */
    val showAirplane: Boolean = true,
    /**
     * How Do Not Disturb is drawn while it is on. [DND_MIDDLE] puts the moon in the ring,
     * [DND_DOTS] turns the four signal dots into that same moon, and [DND_OFF] shows neither.
     * Independent of [showAirplane].
     */
    val dndMode: String = "middle",
    /**
     * Which cellular line the four spheres show on a dual-SIM phone: `"auto"` (the default data line),
     * `"sim1"` or `"sim2"`. Single-SIM phones ignore it.
     */
    val simChoice: String = "auto",
    /**
     * When true, the four signal dots stay cellular while airplane mode is off. In airplane mode
     * they show Wi-Fi strength, and they disappear if Wi-Fi is off too.
     */
    val wifiDots: Boolean = false,
    /**
     * How much of the icon's width stays in the status-bar strip, as a percent.
     * 100 keeps the slot that sits between the screen edge and icons Duo does not replace
     * (sound, vibrate, alarm). 0 gives that space back so those icons sit on the edge.
     * The drawing does not move; only the empty slot shrinks.
     */
    val edgePadding: Int = 100,
    /**
     * Which rendering engine draws the element: `"auto"` lets the module decide (Rive when it can),
     * `"rive"` forces the native Rive path, `"canvas"` forces the pure-Canvas fallback with no native
     * code in the path.
     */
    val engineMode: String = DuoPrefs.ENGINE_AUTO,
    /**
     * Which occupant the middle of the ring shows: "auto" (the original priority), "wifi", "cell"
     * (the cellular generation), "percent" (the battery level as a number), "clock" or "charging".
     */
    val middleSlot: String = DuoPrefs.MIDDLE_SLOT_AUTO,
    /** Whether the Wi-Fi glyph inside the ring is drawn at all. */
    val showWifiGlyph: Boolean = true,
    /** Whether the four cellular dots inside the ring are drawn at all. */
    val showCellDots: Boolean = true,
    /**
     * Per-event toggles packed into a bitmask. Bit set means the event is enabled.
     * See [DuoPrefs.EVENT_ALARM] and friends; [DuoPrefs.EVENT_ALL] enables everything.
     */
    val eventMask: Int = DuoPrefs.EVENT_ALL,
    /**
     * Whether the Punch Hole download progress may replace the ring's display. Off leaves the
     * element exactly as it was before this setting: charging and battery behave normally.
     */
    val downloadProgressEnabled: Boolean = true,
    /**
     * Shift the whole element one physical pixel every hour to spread wear on an OLED panel.
     * Off is the original, fixed seat.
     */
    val burnInEnabled: Boolean = false
) {
    companion object {
        /** The classic ring drawing (`res/raw/duo_old.riv`), the pre-existing look. */
        const val DESIGN_OLD = "old"
        /** The current ring drawing (`res/raw/duo.riv`). */
        const val DESIGN_NEW = "new"

        /** A stored design this build understands. Anything else keeps the classic design. */
        fun normalizeDesignStyle(raw: String?): String = when (raw) {
            DESIGN_NEW -> DESIGN_NEW
            else -> DESIGN_OLD
        }
    }
}

/**
 * Which copy of [DuoSettings] is in force. Portrait and landscape are stored apart; the phone's current
 * orientation picks the copy the settings screen edits and the copy the status bar draws.
 *
 * Anything that is not a [DuoSettings] field (update checks, the Shizuku icon blacklist) stays one value
 * for the whole phone — those are not part of the element.
 */
enum class DuoOrientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        fun of(orientation: Int): DuoOrientation =
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) LANDSCAPE else PORTRAIT

        fun of(context: Context): DuoOrientation = of(context.resources.configuration.orientation)
    }
}

object DuoPrefs {

    const val AUTHORITY = "io.github.kvmy666.duostatusbar.settings"
    const val ACTION_SETTINGS_CHANGED = "io.github.kvmy666.duostatusbar.SETTINGS_CHANGED"

    /**
     * Sent by the app when the user taps "Restart System UI". The module lives inside System UI, so it is
     * the only side that can restart it: it kills its own process and Android brings System UI straight
     * back. Needed because the size only takes effect on a fresh start (resizing it live is what used to
     * take System UI down).
     */
    const val ACTION_RESTART_SYSTEMUI = "io.github.kvmy666.duostatusbar.RESTART_SYSTEMUI"

    /**
     * Sent by the app just before it builds a bug report. The module replies with a fresh diagnostic
     * dump, so the report shows the bar as it is *at that moment* — the one-shot boot dump cannot show
     * the shade expanded, which is exactly what the shade-position report needs (Issue #1).
     */
    const val ACTION_DIAGNOSTICS_REQUEST = "io.github.kvmy666.duostatusbar.DIAGNOSTICS_REQUEST"

    const val COL_ENABLED = "enabled"
    const val COL_USE_RIVE = "use_rive"
    const val COL_SHOW_PERCENT = "show_percent"
    const val COL_SIZE_PERCENT = "size_percent"
    const val COL_OFFSET_X = "offset_x"
    const val COL_PERCENT_HEIGHT = "percent_height"
    const val COL_SPLIT_INDICATORS = "split_indicators"
    const val COL_INDICATORS_OFFSET_X = "indicators_offset_x"
    const val COL_LIVE_APPLY = "live_apply"
    const val COL_CLOCK_FONT = "clock_font"
    const val COL_REVISION = "revision"
    const val COL_TAP = "tap_action"
    const val COL_DOUBLE_TAP = "double_tap_action"
    const val COL_LONG_PRESS = "long_press_action"
    const val COL_INDICATORS_TAP = "indicators_tap_action"
    const val COL_INDICATORS_DOUBLE_TAP = "indicators_double_tap_action"
    const val COL_INDICATORS_LONG_PRESS = "indicators_long_press_action"
    const val COL_REVEAL_MS = "reveal_ms"
    const val COL_ANIMATIONS = "animations_enabled"
    const val COL_ARRIVAL = "arrival_enabled"
    const val COL_DEPARTURE = "departure_enabled"
    const val COL_CHARGING = "charging_enabled"
    const val COL_SHOW_CHARGING_BOLT = "show_charging_bolt"
    const val COL_ICON_COLOR = "icon_color"
    const val COL_HIDE_OTHER_ICONS = "hide_other_icons"
    /** Optional columns; missing means true so older settings apps keep drawing both surfaces. */
    const val COL_SHOW_IN_QS = "show_in_qs"
    const val COL_SHOW_IN_SHADE = "show_in_shade"
    const val COL_NETWORK_ONLY = "network_only"
    const val COL_SHOW_AIRPLANE = "show_airplane"
    const val COL_SHOW_DND = "show_dnd"
    /** "off", "middle", or "dots". Missing means the older [COL_SHOW_DND] switch. */
    const val COL_DND_MODE = "dnd_mode"

    /** Do Not Disturb is not drawn. */
    const val DND_OFF = "off"
    /** The moon replaces the middle of the ring. The original behaviour. */
    const val DND_MIDDLE = "middle"
    /** The four signal dots become moons and the middle of the ring is left alone. */
    const val DND_DOTS = "dots"

    /** A stored mode this build understands. Anything else is the original middle moon. */
    fun normalizeDndMode(raw: String?): String = when (raw) {
        DND_OFF, DND_MIDDLE, DND_DOTS -> raw
        else -> DND_MIDDLE
    }

    const val ENGINE_AUTO = "auto"
    const val ENGINE_RIVE = "rive"
    const val ENGINE_CANVAS = "canvas"

    /** A stored engine mode this build understands. Anything else falls back to "auto". */
    fun normalizeEngine(raw: String?): String = when (raw) {
        ENGINE_AUTO, ENGINE_RIVE, ENGINE_CANVAS -> raw
        else -> ENGINE_AUTO
    }

    const val MIDDLE_SLOT_AUTO = "auto"
    const val MIDDLE_SLOT_WIFI = "wifi"
    const val MIDDLE_SLOT_CELL = "cell"
    const val MIDDLE_SLOT_PERCENT = "percent"
    const val MIDDLE_SLOT_CLOCK = "clock"
    const val MIDDLE_SLOT_CHARGING = "charging"

    /** A stored middle-slot choice this build understands. Anything else falls back to "auto". */
    fun normalizeMiddleSlot(raw: String?): String = when (raw) {
        MIDDLE_SLOT_AUTO, MIDDLE_SLOT_WIFI, MIDDLE_SLOT_CELL, MIDDLE_SLOT_PERCENT,
        MIDDLE_SLOT_CLOCK, MIDDLE_SLOT_CHARGING -> raw
        else -> MIDDLE_SLOT_AUTO
    }

    /** True when Do Not Disturb may take the middle of the ring. */
    fun dndInMiddle(mode: String): Boolean = mode == DND_MIDDLE

    /**
     * Parses [DuoSettings.hiddenStockIcons] into its canonical key list: each comma-separated entry
     * is trimmed, blanks are dropped, duplicates collapse, and the result is sorted so the persisted
     * CSV has one stable order. A null or blank value is an empty list.
     */
    fun parseHiddenStockIcons(csv: String?): List<String> =
        csv.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    /**
     * Joins [keys] into the canonical CSV [parseHiddenStockIcons] reads back. The same normalization
     * applies, so an empty or all-blank collection becomes `""` (hide nothing).
     */
    fun joinHiddenStockIcons(keys: Iterable<String>): String =
        parseHiddenStockIcons(keys.joinToString(",")).joinToString(",")
    const val COL_SIM_CHOICE = "sim_choice"
    const val COL_WIFI_DOTS = "wifi_dots"
    const val COL_EDGE_PADDING = "edge_padding"
    /** Centers the ring on the status bar so a wider clock cannot push it off a camera cutout. */
    const val COL_LOCK_RING = "lock_ring"
    const val COL_ENGINE_MODE = "engine_mode"
    const val COL_MIDDLE_SLOT = "middle_slot"
    const val COL_SHOW_WIFI_GLYPH = "show_wifi_glyph"
    const val COL_SHOW_CELL_DOTS = "show_cell_dots"
    /** Prefixed onto every landscape column. Portrait keeps the original names, so old installs stay put. */
    const val LAND_PREFIX = "land_"

    /** Per-event toggle bits packed into [DuoSettings.eventMask]. */
    const val EVENT_ALARM = 1 shl 0
    const val EVENT_FLASHLIGHT = 1 shl 1
    const val EVENT_SCREENSHOT = 1 shl 2
    const val EVENT_MIC = 1 shl 3
    const val EVENT_LOCATION = 1 shl 4
    const val EVENT_VOLUME = 1 shl 5
    const val EVENT_UNLOCK = 1 shl 6
    const val EVENT_CALL = 1 shl 7
    const val EVENT_ALL = (1 shl 8) - 1

    /** True when [bit] is set in [mask]. */
    fun eventEnabled(mask: Int, bit: Int): Boolean = (mask and bit) != 0

    const val COL_EVENT_MASK = "event_mask"
    /** Whether the Punch Hole download progress may take over the ring; appended, never reordered. */
    const val COL_DOWNLOAD_PROGRESS = "download_progress"
    /** Whether the element alternates its seat by one pixel each hour; appended after the download switch. */
    const val COL_BURN_IN = "burn_in"
    /** Per-icon stock hiding, as a canonical CSV; appended last so existing columns never move. */
    const val COL_HIDDEN_STOCK_ICONS = "hidden_stock_icons"
    /** The classic/new ring design; appended after the per-icon list, never reordered. */
    const val COL_DESIGN_STYLE = "design_style"

    /** The classic drawing, kept on old installs unless the user picks the new ring. */
    const val DESIGN_OLD = DuoSettings.DESIGN_OLD
    /** The current drawing. */
    const val DESIGN_NEW = DuoSettings.DESIGN_NEW

    /** Alias of [DuoSettings.normalizeDesignStyle] for the hook side. Unknown means classic. */
    fun normalizeDesignStyle(raw: String?): String = DuoSettings.normalizeDesignStyle(raw)

    /** The optical factor applied to the classic ring so 112 px draws at the new drawing's 104 px. */
    const val OLD_OPTICAL_SCALE = 0.9286f
    /**
     * The portrait columns, in the order a module has always read them. Landscape repeats this list
     * (except [COL_REVISION], which is shared) under [LAND_PREFIX].
     */
    val PORTRAIT_COLUMNS = arrayOf(
        COL_ENABLED, COL_USE_RIVE, COL_SHOW_PERCENT, COL_SIZE_PERCENT, COL_OFFSET_X,
        COL_LIVE_APPLY, COL_CLOCK_FONT, COL_REVISION,
        COL_TAP, COL_DOUBLE_TAP, COL_LONG_PRESS, COL_REVEAL_MS,
        COL_ANIMATIONS, COL_ARRIVAL, COL_DEPARTURE, COL_CHARGING,
        COL_ICON_COLOR, COL_HIDE_OTHER_ICONS, COL_NETWORK_ONLY, COL_SIM_CHOICE,
        COL_PERCENT_HEIGHT, COL_SPLIT_INDICATORS, COL_INDICATORS_OFFSET_X,
        COL_WIFI_DOTS, COL_SHOW_AIRPLANE, COL_SHOW_DND, COL_EDGE_PADDING, COL_DND_MODE,
        COL_SHOW_CHARGING_BOLT, COL_LOCK_RING,
        COL_INDICATORS_TAP, COL_INDICATORS_DOUBLE_TAP, COL_INDICATORS_LONG_PRESS,
        COL_SHOW_IN_QS, COL_SHOW_IN_SHADE, COL_ENGINE_MODE, COL_MIDDLE_SLOT,
        COL_SHOW_WIFI_GLYPH, COL_SHOW_CELL_DOTS, COL_EVENT_MASK, COL_DOWNLOAD_PROGRESS, COL_BURN_IN,
        COL_HIDDEN_STOCK_ICONS, COL_DESIGN_STYLE
    )

    /** Landscape columns appended after [PORTRAIT_COLUMNS]. An older module ignores names it does not know. */
    val LANDSCAPE_COLUMNS: Array<String> = PORTRAIT_COLUMNS
        .filter { it != COL_REVISION }
        .map { LAND_PREFIX + it }
        .toTypedArray()

    /** The column set the module expects; kept in one place so both sides cannot drift. */
    val COLUMNS: Array<String> = PORTRAIT_COLUMNS + LANDSCAPE_COLUMNS

    private const val PREFS = "duo_settings"
    private const val KEY_REVISION = "revision"
    private const val KEY_STATUS = "last_status"
    private const val KEY_HISTORY = "status_history"
    private const val KEY_DUMP = "last_dump"
    private const val KEY_STOCK_ICONS = "last_stock_icons"
    private const val KEY_HIDE_STOCK_ICONS = "hide_stock_icons"
    private const val KEY_FALLBACK = "last_fallback"
    private const val KEY_CHECK_UPDATES = "check_updates"
    private const val KEY_UPDATE_NOTIFIED = "update_notified"
    private const val KEY_UPDATE_CHECK_AT = "update_check_at"
    private const val KEY_LOG_SENT_AT = "log_sent_at"
    private const val KEY_ROOT_ALLOWED = "root_allowed"
    /** Set the first time landscape is saved. Until then landscape reads as a copy of portrait. */
    private const val KEY_LANDSCAPE_SET = "landscape_set"
    private const val HISTORY_LIMIT = 20

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun prefix(orientation: DuoOrientation): String =
        if (orientation == DuoOrientation.LANDSCAPE) LAND_PREFIX else ""

    /**
     * The settings for [orientation]. Defaults to the phone's current orientation, which is what the
     * settings screen edits. Landscape that has never been saved returns the portrait copy, so rotating
     * for the first time does not jump back to factory defaults.
     */
    fun read(context: Context, orientation: DuoOrientation = DuoOrientation.of(context)): DuoSettings {
        val p = prefs(context)
        if (orientation == DuoOrientation.LANDSCAPE && !p.getBoolean(KEY_LANDSCAPE_SET, false)) {
            return readStored(p, prefix(DuoOrientation.PORTRAIT))
        }
        return readStored(p, prefix(orientation))
    }

    private fun readStored(p: android.content.SharedPreferences, prefix: String): DuoSettings = DuoSettings(
        enabled = p.getBoolean(prefix + COL_ENABLED, false),
        useRive = p.getBoolean(prefix + COL_USE_RIVE, true),
        showPercent = p.getBoolean(prefix + COL_SHOW_PERCENT, true),
        sizePercent = p.getInt(prefix + COL_SIZE_PERCENT, 100).coerceIn(MIN_SIZE, MAX_SIZE),
        offsetX = p.getInt(prefix + COL_OFFSET_X, 0).let { stored ->
            // Centered (locked) mode keeps the existing symmetric range (uncapped here; the UI
            // clamps to half the screen). Normal mode is right-anchored: 0 is the right edge.
            if (p.getBoolean(prefix + COL_LOCK_RING, false)) {
                stored
            } else {
                stored.coerceIn(-MAX_UNLOCKED_RING_OFFSET, 0)
            }
        },
        percentHeight = p.getInt(prefix + COL_PERCENT_HEIGHT, DEFAULT_PERCENT_HEIGHT),
        splitIndicators = p.getBoolean(prefix + COL_SPLIT_INDICATORS, false),
        indicatorsOffsetX = p.getInt(prefix + COL_INDICATORS_OFFSET_X, 0),
        lockRing = p.getBoolean(prefix + COL_LOCK_RING, false),
        liveApply = p.getBoolean(prefix + COL_LIVE_APPLY, true),
        systemClockFont = p.getBoolean(prefix + COL_CLOCK_FONT, true),
        revealMs = nearestReveal(p.getInt(prefix + COL_REVEAL_MS, DEFAULT_REVEAL_MS)),
        animationsEnabled = p.getBoolean(prefix + COL_ANIMATIONS, true),
        arrivalEnabled = p.getBoolean(prefix + COL_ARRIVAL, true),
        departureEnabled = p.getBoolean(prefix + COL_DEPARTURE, true),
        chargingEnabled = p.getBoolean(prefix + COL_CHARGING, true),
        showChargingBolt = p.getBoolean(prefix + COL_SHOW_CHARGING_BOLT, true),
        tapAction = p.getString(prefix + COL_TAP, "no_action") ?: "no_action",
        doubleTapAction = p.getString(prefix + COL_DOUBLE_TAP, "no_action") ?: "no_action",
        longPressAction = p.getString(prefix + COL_LONG_PRESS, "no_action") ?: "no_action",
        indicatorsTapAction = p.getString(prefix + COL_INDICATORS_TAP, null)
            ?: p.getString(prefix + COL_TAP, "no_action") ?: "no_action",
        indicatorsDoubleTapAction = p.getString(prefix + COL_INDICATORS_DOUBLE_TAP, null)
            ?: p.getString(prefix + COL_DOUBLE_TAP, "no_action") ?: "no_action",
        indicatorsLongPressAction = p.getString(prefix + COL_INDICATORS_LONG_PRESS, null)
            ?: p.getString(prefix + COL_LONG_PRESS, "no_action") ?: "no_action",
        iconColor = p.getString(prefix + COL_ICON_COLOR, "auto") ?: "auto",
        hideOtherIcons = p.getBoolean(prefix + COL_HIDE_OTHER_ICONS, false),
        hiddenStockIcons = joinHiddenStockIcons(
            parseHiddenStockIcons(p.getString(prefix + COL_HIDDEN_STOCK_ICONS, ""))
        ),
        designStyle = normalizeDesignStyle(p.getString(prefix + COL_DESIGN_STYLE, DESIGN_OLD)),
        showInQs = p.getBoolean(prefix + COL_SHOW_IN_QS, true),
        showInShade = p.getBoolean(prefix + COL_SHOW_IN_SHADE, true),
        // The old "network icons only" switch hid both statuses. A phone that still has only that
        // key keeps both out of the slot; once either new key is saved, it speaks for itself.
        showAirplane = p.getBoolean(prefix + COL_SHOW_AIRPLANE, !p.getBoolean(prefix + COL_NETWORK_ONLY, false)),
        dndMode = p.getString(prefix + COL_DND_MODE, null)?.let { normalizeDndMode(it) }
            ?: if (p.getBoolean(prefix + COL_SHOW_DND, !p.getBoolean(prefix + COL_NETWORK_ONLY, false))) {
                DND_MIDDLE
            } else {
                DND_OFF
            },
        simChoice = p.getString(prefix + COL_SIM_CHOICE, "auto") ?: "auto",
        wifiDots = p.getBoolean(prefix + COL_WIFI_DOTS, false),
        edgePadding = p.getInt(prefix + COL_EDGE_PADDING, DEFAULT_EDGE_PADDING),
        engineMode = normalizeEngine(
            p.getString(
                prefix + COL_ENGINE_MODE,
                if (p.getBoolean(prefix + COL_USE_RIVE, true)) ENGINE_AUTO else ENGINE_CANVAS
            )
        ),
        middleSlot = normalizeMiddleSlot(p.getString(prefix + COL_MIDDLE_SLOT, MIDDLE_SLOT_AUTO)),
        showWifiGlyph = p.getBoolean(prefix + COL_SHOW_WIFI_GLYPH, true),
        showCellDots = p.getBoolean(prefix + COL_SHOW_CELL_DOTS, true),
        eventMask = p.getInt(prefix + COL_EVENT_MASK, EVENT_ALL) and EVENT_ALL,
        downloadProgressEnabled = p.getBoolean(prefix + COL_DOWNLOAD_PROGRESS, true),
        burnInEnabled = p.getBoolean(prefix + COL_BURN_IN, false)
    )

    /** Writes the settings for [orientation] and bumps the revision the module compares against. */
    fun write(
        context: Context,
        settings: DuoSettings,
        orientation: DuoOrientation = DuoOrientation.of(context)
    ): Long {
        val p = prefs(context)
        val next = revision(context) + 1
        val effectiveEngine = when {
            settings.engineMode == ENGINE_AUTO && !settings.useRive -> ENGINE_CANVAS
            else -> normalizeEngine(settings.engineMode)
        }
        val clamped = settings.copy(
            sizePercent = settings.sizePercent.coerceIn(MIN_SIZE, MAX_SIZE),
            offsetX = if (settings.lockRing) {
                settings.offsetX
            } else {
                settings.offsetX.coerceIn(-MAX_UNLOCKED_RING_OFFSET, 0)
            },
            percentHeight = settings.percentHeight.coerceIn(MIN_PERCENT_HEIGHT, MAX_PERCENT_HEIGHT),
            indicatorsOffsetX = settings.indicatorsOffsetX.coerceIn(
                -MAX_INDICATORS_OFFSET,
                MAX_INDICATORS_OFFSET
            ),
            edgePadding = settings.edgePadding.coerceIn(MIN_EDGE_PADDING, MAX_EDGE_PADDING),
            revealMs = nearestReveal(settings.revealMs),
            dndMode = normalizeDndMode(settings.dndMode),
            engineMode = effectiveEngine,
            useRive = effectiveEngine != ENGINE_CANVAS,
            hiddenStockIcons = joinHiddenStockIcons(parseHiddenStockIcons(settings.hiddenStockIcons)),
            designStyle = normalizeDesignStyle(settings.designStyle)
        )
        val editor = p.edit()
        editor.writeFields(prefix(orientation), clamped)
        if (orientation == DuoOrientation.LANDSCAPE) editor.putBoolean(KEY_LANDSCAPE_SET, true)
        editor.putLong(KEY_REVISION, next).apply()
        return next
    }

    private fun android.content.SharedPreferences.Editor.writeFields(prefix: String, settings: DuoSettings) {
        putBoolean(prefix + COL_ENABLED, settings.enabled)
        putBoolean(prefix + COL_USE_RIVE, settings.engineMode != ENGINE_CANVAS)
        putBoolean(prefix + COL_SHOW_PERCENT, settings.showPercent)
        putInt(prefix + COL_SIZE_PERCENT, settings.sizePercent)
        putInt(prefix + COL_OFFSET_X, settings.offsetX)
        putInt(prefix + COL_PERCENT_HEIGHT, settings.percentHeight)
        putBoolean(prefix + COL_SPLIT_INDICATORS, settings.splitIndicators)
        putInt(prefix + COL_INDICATORS_OFFSET_X, settings.indicatorsOffsetX)
        putBoolean(prefix + COL_LOCK_RING, settings.lockRing)
        putBoolean(prefix + COL_LIVE_APPLY, settings.liveApply)
        putBoolean(prefix + COL_CLOCK_FONT, settings.systemClockFont)
        putInt(prefix + COL_REVEAL_MS, settings.revealMs)
        putBoolean(prefix + COL_ANIMATIONS, settings.animationsEnabled)
        putBoolean(prefix + COL_ARRIVAL, settings.arrivalEnabled)
        putBoolean(prefix + COL_DEPARTURE, settings.departureEnabled)
        putBoolean(prefix + COL_CHARGING, settings.chargingEnabled)
        putBoolean(prefix + COL_SHOW_CHARGING_BOLT, settings.showChargingBolt)
        putString(prefix + COL_TAP, settings.tapAction)
        putString(prefix + COL_DOUBLE_TAP, settings.doubleTapAction)
        putString(prefix + COL_LONG_PRESS, settings.longPressAction)
        putString(prefix + COL_INDICATORS_TAP, settings.indicatorsTapAction)
        putString(prefix + COL_INDICATORS_DOUBLE_TAP, settings.indicatorsDoubleTapAction)
        putString(prefix + COL_INDICATORS_LONG_PRESS, settings.indicatorsLongPressAction)
        putString(prefix + COL_ICON_COLOR, settings.iconColor)
        putBoolean(prefix + COL_HIDE_OTHER_ICONS, settings.hideOtherIcons)
        putBoolean(prefix + COL_SHOW_IN_QS, settings.showInQs)
        putBoolean(prefix + COL_SHOW_IN_SHADE, settings.showInShade)
        // Kept so a module that predates the two switches still hides both when neither is allowed
        // in the middle. Dots mode does not take that place, so it counts as Do Not Disturb off here.
        putBoolean(prefix + COL_NETWORK_ONLY, !settings.showAirplane && !dndInMiddle(settings.dndMode))
        putBoolean(prefix + COL_SHOW_AIRPLANE, settings.showAirplane)
        putBoolean(prefix + COL_SHOW_DND, dndInMiddle(settings.dndMode))
        putString(prefix + COL_DND_MODE, settings.dndMode)
        putString(prefix + COL_SIM_CHOICE, settings.simChoice)
        putBoolean(prefix + COL_WIFI_DOTS, settings.wifiDots)
        putInt(prefix + COL_EDGE_PADDING, settings.edgePadding)
        putString(prefix + COL_ENGINE_MODE, normalizeEngine(settings.engineMode))
        putString(prefix + COL_MIDDLE_SLOT, normalizeMiddleSlot(settings.middleSlot))
        putBoolean(prefix + COL_SHOW_WIFI_GLYPH, settings.showWifiGlyph)
        putBoolean(prefix + COL_SHOW_CELL_DOTS, settings.showCellDots)
        putInt(prefix + COL_EVENT_MASK, settings.eventMask and EVENT_ALL)
        putBoolean(prefix + COL_DOWNLOAD_PROGRESS, settings.downloadProgressEnabled)
        putBoolean(prefix + COL_BURN_IN, settings.burnInEnabled)
        putString(prefix + COL_HIDDEN_STOCK_ICONS, settings.hiddenStockIcons)
        putString(prefix + COL_DESIGN_STYLE, normalizeDesignStyle(settings.designStyle))
    }

    fun revision(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_REVISION, 0L)

    /**
     * Issue #4: whether the user asked Shizuku to hide the phone's own Wi-Fi/cellular/battery icons
     * through the secure `icon_blacklist`. Stored separately from [DuoSettings] on purpose: the module
     * never needs this value, so it must not become part of the app↔module provider contract.
     */
    fun hideStockIcons(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_STOCK_ICONS, false)

    fun writeHideStockIcons(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_STOCK_ICONS, value)
            .apply()
    }

    /**
     * Whether the user granted Duo root for diagnostics. Kept separate from [DuoSettings] because the
     * module never needs it (it is an app-side capture setting, not part of the element's contract).
     *
     * Root is only ever used to *read* the system log into a bug report the user explicitly sends; the
     * capture never runs `su` before this flag is set, so no root prompt appears uninvited.
     */
    fun rootAllowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ROOT_ALLOWED, false)

    fun writeRootAllowed(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ROOT_ALLOWED, value)
            .apply()
    }

    /**
     * The stock status-bar icons the module last published, as the compact JSON from
     * [StockIconInventory]. Empty means the module has not reported a live bar yet.
     */
    fun readStockIcons(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STOCK_ICONS, "") ?: ""

    fun writeStockIcons(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STOCK_ICONS, json)
            .apply()
    }

    /**
     * The last moment the module had to fall back (empty when everything is working). Set by the module
     * when Rive cannot draw and the simple Canvas element is used instead; the app turns it into the
     * "please send the log" alert. Cleared at each module load and when the user dismisses the alert.
     */
    fun fallback(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FALLBACK, "") ?: ""

    fun writeFallback(context: Context, reason: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (reason.isEmpty()) p.edit().remove(KEY_FALLBACK).apply()
        else p.edit().putString(KEY_FALLBACK, reason).apply()
    }

    /** Whether the background update check is enabled (on by default; the user can switch it off). */
    fun checkUpdates(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CHECK_UPDATES, true)

    fun writeCheckUpdates(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHECK_UPDATES, value)
            .apply()
    }

    /**
     * When a log was last sent to the developer, so the send button can apply a hidden cooldown and the
     * bot cannot be spammed by repeated taps. Wall-clock ms, or 0 when never.
     */
    fun logSentAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LOG_SENT_AT, 0L)

    fun writeLogSentAt(context: Context, at: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LOG_SENT_AT, at)
            .apply()
    }

    /** The release version already announced, so the same update is not notified over and over. */
    fun updateNotified(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UPDATE_NOTIFIED, "") ?: ""

    fun writeUpdateNotified(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UPDATE_NOTIFIED, version)
            .apply()
    }

    /** Wall-clock ms of the last successful update check, so the 1/day cadence holds across restarts. */
    fun lastUpdateCheck(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATE_CHECK_AT, 0L)

    fun writeLastUpdateCheck(context: Context, at: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_UPDATE_CHECK_AT, at)
            .apply()
    }

    /** What the module last reported about itself, for the diagnostics screen. */
    fun status(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUS, "") ?: ""

    fun writeStatus(context: Context, status: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // A cheap rolling history: appended only when it changes, so a burst of identical reports (the
        // element is re-checked on every layout change) does not turn into a wall of the same line.
        val previous = p.getString(KEY_STATUS, "")
        if (previous == status) return
        val history = (statusHistory(context) + "${System.currentTimeMillis()} $status")
            .takeLast(HISTORY_LIMIT)
        p.edit()
            .putString(KEY_STATUS, status)
            .putStringSet(KEY_HISTORY, history.toSet())
            .apply()
    }

    /**
     * The last few module self-reports, oldest first. Kept because a bug report with the *sequence* of what
     * the module thought it was doing is worth far more than the final state.
     */
    fun statusHistory(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_HISTORY, emptySet())
            .orEmpty()
            .sorted()

    /**
     * The debug diagnostic dump the module last sent (build identity, id probes, view tree, readers).
     * Empty in release builds, where the module never sends one.
     */
    fun dump(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DUMP, "") ?: ""

    /** Maximum number of UTF-16 code units kept for [KEY_DUMP] before truncation. */
    const val DUMP_MAX_CHARS = 2 * 1024 * 1024

    /** Marker appended when a dump had to be truncated to [DUMP_MAX_CHARS]. */
    const val DUMP_TRUNCATION_MARKER = "\n[truncated 2MB]"

    /**
     * Caps [dump] at [DUMP_MAX_CHARS] code units, cutting at a code-point boundary so a UTF-16
     * surrogate pair is never split, and appends [DUMP_TRUNCATION_MARKER] when it had to be cut.
     * Inputs that already fit are returned unchanged.
     */
    internal fun truncateDump(dump: String): String {
        if (dump.length <= DUMP_MAX_CHARS) return dump
        var cut = DUMP_MAX_CHARS
        // Never split a supplementary character: if the cut falls between a high and a low surrogate,
        // move it back one code unit so the pair stays intact.
        if (cut > 0 && cut < dump.length &&
            Character.isHighSurrogate(dump[cut - 1]) &&
            Character.isLowSurrogate(dump[cut])
        ) {
            cut -= 1
        }
        return dump.substring(0, cut) + DUMP_TRUNCATION_MARKER
    }

    fun writeDump(context: Context, dump: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DUMP, truncateDump(dump))
            .apply()
    }

    /**
     * When the module last ran inside SystemUI (wall clock ms), or 0 if it never has.
     *
     * Read straight from `Settings.Global`, which a normal app may read without a permission. It is the
     * one fact that separates "LSPosed never injected the module" from "the module is switched off", and
     * the About screen says which one it is instead of showing a blank report.
     */
    fun moduleLoadTime(context: Context): Long = try {
        Settings.Global.getLong(context.contentResolver, "duo_statusbar_last_load", 0L)
    } catch (_: Throwable) {
        0L
    }

    /**
     * When the module last proved it is still running (wall clock ms), or 0 if it never wrote one. Unlike
     * [moduleLoadTime] this stays fresh while the process lives, so the app can tell "the module is here"
     * from "it was just unscoped and System UI restarted without it" — the case where a report used to ship
     * the previous run's dump and status.
     */
    fun heartbeat(context: Context): Long = try {
        Settings.Global.getLong(context.contentResolver, "duo_statusbar_heartbeat", 0L)
    } catch (_: Throwable) {
        0L
    }

    /** A heartbeat older than this means the module is not running now. Three missed beats at 20 s. */
    const val HEARTBEAT_STALE_MS = 90_000L

    /**
     * FR-25: the arrivals the Rive file can play. The file holds one timeline at five speeds, so a value
     * between two of these is snapped to the nearest rather than silently ignored.
     */
    val REVEAL_CHOICES = intArrayOf(500, 750, 1000, 1250, 1500)
    const val DEFAULT_REVEAL_MS = 1000
    const val MIN_REVEAL_MS = 500
    const val MAX_REVEAL_MS = 1500

    /** Snaps a requested arrival to the nearest the file can actually play. */
    fun nearestReveal(ms: Int): Int =
        REVEAL_CHOICES.minByOrNull { kotlin.math.abs(it - ms) } ?: DEFAULT_REVEAL_MS

    // Raised from 200 to 220 so the logical 0..100 scale can reach 10 % past the old ceiling. The
    // persisted [DuoSettings.sizePercent] stays physical: old 50..200 values remain legal and keep
    // their pixels.
    /** The physical size range the module draws with and the provider publishes. */
    const val MIN_SIZE = 50
    const val MAX_SIZE = 220

    /** The logical 0..100 scale shown by the size control. */
    const val MIN_LOGICAL_SIZE = 0
    const val MAX_LOGICAL_SIZE = 100

    private const val LOGICAL_SIZE_RANGE = MAX_LOGICAL_SIZE - MIN_LOGICAL_SIZE
    private const val PHYSICAL_SIZE_RANGE = MAX_SIZE - MIN_SIZE

    /**
     * Maps the control's logical 0..100 value to the physical size persisted for the module:
     * `physical = 50 + 1.7 * logical`, rounded to the nearest integer. Out-of-range input is
     * clamped, so 0 is the old smallest (50 %, still visible) and 100 is 220 %.
     */
    fun logicalToPhysical(logical: Int): Int {
        val clamped = logical.coerceIn(MIN_LOGICAL_SIZE, MAX_LOGICAL_SIZE)
        val physical = MIN_SIZE +
            (clamped * PHYSICAL_SIZE_RANGE + LOGICAL_SIZE_RANGE / 2) / LOGICAL_SIZE_RANGE
        return physical.coerceIn(MIN_SIZE, MAX_SIZE)
    }

    /**
     * The inverse of [logicalToPhysical], rounded to the nearest logical step. Used to display an
     * existing physical size in the control: 176 % shows as 74 %, and old 50..200 values stay
     * meaningful. Out-of-range input is clamped to the physical range.
     */
    fun physicalToLogical(physical: Int): Int {
        val clamped = physical.coerceIn(MIN_SIZE, MAX_SIZE)
        val logical = MIN_LOGICAL_SIZE +
            ((clamped - MIN_SIZE) * LOGICAL_SIZE_RANGE + PHYSICAL_SIZE_RANGE / 2) / PHYSICAL_SIZE_RANGE
        return logical.coerceIn(MIN_LOGICAL_SIZE, MAX_LOGICAL_SIZE)
    }

    const val MAX_UNLOCKED_RING_OFFSET = 200
    const val MAX_INDICATORS_OFFSET = 200

    /** 0 keeps the percentage in its original seat; 100 raises it by the full punch-hole clearance. */
    const val MIN_PERCENT_HEIGHT = 0
    const val MAX_PERCENT_HEIGHT = 100
    const val DEFAULT_PERCENT_HEIGHT = 100

    /** 100 keeps the icon's full slot. 0 lets the other status icons sit against the screen edge. */
    const val MIN_EDGE_PADDING = 0
    const val MAX_EDGE_PADDING = 100
    const val DEFAULT_EDGE_PADDING = 100

    data class BackupRestoreResult(
        val restored: Int = 0,
        val warnings: List<String> = emptyList(),
        val error: String? = null
    ) {
        val count: Int get() = restored
        val restoredCount: Int get() = restored
        val applied: Int get() = restored
        val success: Boolean get() = error == null
    }

    /** Reads every stored value, filters it through [BackupCodec.sanitize], and encodes it as JSON. */
    fun backupJson(context: Context): String {
        val all = prefs(context).all
        return BackupCodec.encode(BackupCodec.sanitize(all))
    }

    /**
     * Decodes and sanitizes [json], applies the known values, clamps each orientation once, bumps the
     * revision, and reports how many values were restored. Transient/permission-like keys are never
     * restored because [BackupCodec.sanitize] drops them.
     */
    fun restoreJson(context: Context, json: String): BackupRestoreResult {
        val decoded = BackupCodec.decode(json)
        if (decoded.error != null || decoded.values == null) {
            return BackupRestoreResult(
                restored = 0,
                warnings = decoded.warnings,
                error = decoded.error
            )
        }
        val sanitized = BackupCodec.sanitize(decoded.values!!)
        val store = prefs(context)
        val snapshot = store.all
        // Reject type changes before touching the preferences. A String written into a boolean key
        // makes the next read throw ClassCastException and can leave the app unable to open settings.
        for ((key, value) in sanitized) {
            val old = snapshot[key] ?: continue
            if (value == null) continue
            val compatible = when (old) {
                is Boolean -> value is Boolean
                is Int, is Long -> value is Int || value is Long
                is String -> value is String
                else -> true
            }
            if (!compatible) {
                return BackupRestoreResult(
                    restored = 0,
                    warnings = decoded.warnings,
                    error = "backup_type_mismatch:$key"
                )
            }
        }
        val editor = store.edit()
        var restored = 0
        for ((key, value) in sanitized) {
            when (value) {
                is Boolean -> { editor.putBoolean(key, value); restored++ }
                is Int -> { editor.putInt(key, value); restored++ }
                is Long -> { editor.putLong(key, value); restored++ }
                is String -> { editor.putString(key, value); restored++ }
                null -> { editor.remove(key); restored++ }
            }
        }
        editor.apply()
        val readFailure = runCatching {
            read(context, DuoOrientation.PORTRAIT)
            read(context, DuoOrientation.LANDSCAPE)
        }.exceptionOrNull()
        if (readFailure != null) {
            val rollback = store.edit().clear()
            for ((key, value) in snapshot) {
                when (value) {
                    is Boolean -> rollback.putBoolean(key, value)
                    is Int -> rollback.putInt(key, value)
                    is Long -> rollback.putLong(key, value)
                    is String -> rollback.putString(key, value)
                }
            }
            rollback.apply()
            return BackupRestoreResult(
                restored = 0,
                warnings = decoded.warnings,
                error = "backup_restore_failed"
            )
        }
        // Preserve legacy behaviour: read/write each orientation once so values are clamped.
        runCatching { write(context, read(context, DuoOrientation.PORTRAIT), DuoOrientation.PORTRAIT) }
        runCatching { write(context, read(context, DuoOrientation.LANDSCAPE), DuoOrientation.LANDSCAPE) }
        return BackupRestoreResult(
            restored = restored,
            warnings = decoded.warnings,
            error = null
        )
    }
}


typealias BackupRestoreResult = DuoPrefs.BackupRestoreResult
