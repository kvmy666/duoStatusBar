package io.github.kvmy666.duostatusbar.settings

import android.content.Context

/**
 * The last settings write: when it happened and which keys changed (bounded to [MAX_KEYS]). The
 * computation is pure; [record] is the only writer and is called exactly once from [DuoPrefs.write].
 * Nothing here runs on a hot path.
 */
internal object SettingsHistory {
    const val MAX_KEYS = 12
    const val KEY_LAST_CHANGE_AT = "last_change_at"
    const val KEY_LAST_CHANGE_KEYS = "last_change_keys"
    const val ABSENT = "not recorded (build predates history)"

    data class Record(val at: Long, val keys: List<String>)

    /**
     * The changed setting columns, in declaration order, bounded to [MAX_KEYS]. Landscape keys carry
     * the same [DuoPrefs.LAND_PREFIX] as the stored copy so a report cannot confuse the two rows.
     */
    fun changedKeys(
        previous: DuoSettings,
        next: DuoSettings,
        orientation: DuoOrientation = DuoOrientation.PORTRAIT
    ): List<String> {
        val prefix = if (orientation == DuoOrientation.LANDSCAPE) DuoPrefs.LAND_PREFIX else ""
        val keys = ArrayList<String>(MAX_KEYS)
        fun add(column: String, changed: Boolean) {
            if (changed && keys.size < MAX_KEYS) keys.add(prefix + column)
        }
        add(DuoPrefs.COL_ENABLED, previous.enabled != next.enabled)
        add(DuoPrefs.COL_USE_RIVE, previous.useRive != next.useRive)
        add(DuoPrefs.COL_SHOW_PERCENT, previous.showPercent != next.showPercent)
        add(DuoPrefs.COL_SIZE_PERCENT, previous.sizePercent != next.sizePercent)
        add(DuoPrefs.COL_OFFSET_X, previous.offsetX != next.offsetX)
        add(DuoPrefs.COL_PERCENT_HEIGHT, previous.percentHeight != next.percentHeight)
        add(DuoPrefs.COL_SPLIT_INDICATORS, previous.splitIndicators != next.splitIndicators)
        add(
            DuoPrefs.COL_INDICATORS_OFFSET_X,
            previous.indicatorsOffsetX != next.indicatorsOffsetX
        )
        add(DuoPrefs.COL_LOCK_RING, previous.lockRing != next.lockRing)
        add(DuoPrefs.COL_LIVE_APPLY, previous.liveApply != next.liveApply)
        add(DuoPrefs.COL_CLOCK_FONT, previous.systemClockFont != next.systemClockFont)
        add(DuoPrefs.COL_REVEAL_MS, previous.revealMs != next.revealMs)
        add(DuoPrefs.COL_ANIMATIONS, previous.animationsEnabled != next.animationsEnabled)
        add(DuoPrefs.COL_ARRIVAL, previous.arrivalEnabled != next.arrivalEnabled)
        add(DuoPrefs.COL_DEPARTURE, previous.departureEnabled != next.departureEnabled)
        add(DuoPrefs.COL_CHARGING, previous.chargingEnabled != next.chargingEnabled)
        add(
            DuoPrefs.COL_SHOW_CHARGING_BOLT,
            previous.showChargingBolt != next.showChargingBolt
        )
        add(DuoPrefs.COL_TAP, previous.tapAction != next.tapAction)
        add(DuoPrefs.COL_DOUBLE_TAP, previous.doubleTapAction != next.doubleTapAction)
        add(DuoPrefs.COL_LONG_PRESS, previous.longPressAction != next.longPressAction)
        add(
            DuoPrefs.COL_INDICATORS_TAP,
            previous.indicatorsTapAction != next.indicatorsTapAction
        )
        add(
            DuoPrefs.COL_INDICATORS_DOUBLE_TAP,
            previous.indicatorsDoubleTapAction != next.indicatorsDoubleTapAction
        )
        add(
            DuoPrefs.COL_INDICATORS_LONG_PRESS,
            previous.indicatorsLongPressAction != next.indicatorsLongPressAction
        )
        add(DuoPrefs.COL_ICON_COLOR, previous.iconColor != next.iconColor)
        add(DuoPrefs.COL_HIDE_OTHER_ICONS, previous.hideOtherIcons != next.hideOtherIcons)
        add(
            DuoPrefs.COL_HIDDEN_STOCK_ICONS,
            previous.hiddenStockIcons != next.hiddenStockIcons
        )
        add(DuoPrefs.COL_DESIGN_STYLE, previous.designStyle != next.designStyle)
        add(DuoPrefs.COL_SHOW_IN_QS, previous.showInQs != next.showInQs)
        add(DuoPrefs.COL_SHOW_IN_SHADE, previous.showInShade != next.showInShade)
        add(DuoPrefs.COL_SHOW_AIRPLANE, previous.showAirplane != next.showAirplane)
        add(DuoPrefs.COL_DND_MODE, previous.dndMode != next.dndMode)
        add(DuoPrefs.COL_SIM_CHOICE, previous.simChoice != next.simChoice)
        add(DuoPrefs.COL_WIFI_DOTS, previous.wifiDots != next.wifiDots)
        add(DuoPrefs.COL_EDGE_PADDING, previous.edgePadding != next.edgePadding)
        add(DuoPrefs.COL_ENGINE_MODE, previous.engineMode != next.engineMode)
        add(DuoPrefs.COL_MIDDLE_SLOT, previous.middleSlot != next.middleSlot)
        add(DuoPrefs.COL_SHOW_WIFI_GLYPH, previous.showWifiGlyph != next.showWifiGlyph)
        add(DuoPrefs.COL_SHOW_CELL_DOTS, previous.showCellDots != next.showCellDots)
        add(DuoPrefs.COL_EVENT_MASK, previous.eventMask != next.eventMask)
        add(
            DuoPrefs.COL_DOWNLOAD_PROGRESS,
            previous.downloadProgressEnabled != next.downloadProgressEnabled
        )
        add(DuoPrefs.COL_BURN_IN, previous.burnInEnabled != next.burnInEnabled)
        add(DuoPrefs.COL_IOS_MODE, previous.iosMode != next.iosMode)
        add(
            DuoPrefs.COL_IOS_BATTERY_OFFSET,
            previous.iosBatteryOffset != next.iosBatteryOffset
        )
        add(DuoPrefs.COL_IOS_BATTERY_SCALE, previous.iosBatteryScale != next.iosBatteryScale)
        add(DuoPrefs.COL_IOS_WIFI_OFFSET, previous.iosWifiOffset != next.iosWifiOffset)
        add(DuoPrefs.COL_IOS_WIFI_SCALE, previous.iosWifiScale != next.iosWifiScale)
        add(DuoPrefs.COL_IOS_CELL_OFFSET, previous.iosCellOffset != next.iosCellOffset)
        add(DuoPrefs.COL_IOS_CELL_SCALE, previous.iosCellScale != next.iosCellScale)
        add(DuoPrefs.COL_SIM_DISPLAY, previous.simDisplay != next.simDisplay)
        add(DuoPrefs.COL_IOS_PERCENT_INSIDE, previous.iosPercentInside != next.iosPercentInside)
        add(DuoPrefs.COL_HIDE_EXTRAS, previous.hideExtras != next.hideExtras)
        return keys
    }

    /** Pure record: the timestamp plus the bounded changed-key list. */
    fun record(
        previous: DuoSettings,
        next: DuoSettings,
        at: Long,
        orientation: DuoOrientation = DuoOrientation.PORTRAIT
    ): Record = Record(at, changedKeys(previous, next, orientation))

    /**
     * The one call [DuoPrefs.write] makes: computes the record, stores `last_change_at` and
     * `last_change_keys` in the settings store, and returns it.
     */
    fun record(
        context: Context,
        previous: DuoSettings,
        next: DuoSettings,
        at: Long = System.currentTimeMillis(),
        orientation: DuoOrientation = DuoOrientation.PORTRAIT
    ): Record {
        val record = record(previous, next, at, orientation)
        write(context, record)
        return record
    }

    fun write(context: Context, record: Record) {
        DuoPrefs.prefs(context)
            .edit()
            .putLong(KEY_LAST_CHANGE_AT, record.at)
            .putString(KEY_LAST_CHANGE_KEYS, record.keys.joinToString(","))
            .apply()
    }

    /** The last recorded change, or null when this build has never recorded one. */
    fun read(context: Context): Record? {
        val prefs = DuoPrefs.prefs(context)
        val at = prefs.getLong(KEY_LAST_CHANGE_AT, 0L)
        val keys = prefs.getString(KEY_LAST_CHANGE_KEYS, "").orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (at <= 0L && keys.isEmpty()) return null
        return Record(at, keys)
    }

    /** `<timestamp> keys=<…> rev=<revision>`, or [ABSENT] when nothing was ever recorded. */
    fun render(record: Record?, revision: Long, formatAt: (Long) -> String): String =
        if (record == null) {
            ABSENT
        } else {
            "${formatAt(record.at)} keys=${record.keys.joinToString(",").ifBlank { "none" }} " +
                "rev=$revision"
        }
}
