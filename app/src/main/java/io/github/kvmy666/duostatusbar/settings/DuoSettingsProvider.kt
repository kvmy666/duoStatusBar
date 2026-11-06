package io.github.kvmy666.duostatusbar.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import io.github.kvmy666.duostatusbar.L

/**
 * The one hand-off point between this app and the module that runs inside System UI.
 *
 * Read path (module → app): `query(<AUTHORITY>)` returns the settings as a single row whose columns are
 * [DuoPrefs.COLUMNS], including a revision the module can compare against to notice changes.
 *
 * Write path (module → app): `call("status", …)` stores a short self-report that the diagnostics screen
 * shows. It goes the other way because the module cannot write into this app's files, and because "what the
 * module thinks it is doing" is the single most useful thing to see when the status bar looks wrong.
 *
 * Exported without a permission deliberately — see [DuoPrefs] for why that is the only thing that works here.
 */
class DuoSettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return try {
            val portrait = DuoPrefs.read(ctx, DuoOrientation.PORTRAIT)
            val landscape = DuoPrefs.read(ctx, DuoOrientation.LANDSCAPE)
            MatrixCursor(DuoPrefs.COLUMNS).apply {
                addRow(rowFor(portrait, DuoPrefs.revision(ctx), landscape))
            }
        } catch (t: Throwable) {
            L.w("query failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = try {
        when (method) {
            METHOD_STATUS -> {
                val ctx = context
                val status = extras?.getString(EXTRA_STATUS).orEmpty()
                if (ctx != null && status.isNotEmpty()) DuoPrefs.writeStatus(ctx, status)
                L.i("module status: $status")
                Bundle().apply { putBoolean(EXTRA_OK, true) }
            }
            METHOD_DUMP -> {
                val ctx = context
                val dump = extras?.getString(EXTRA_DUMP).orEmpty()
                if (ctx != null && dump.isNotEmpty()) DuoPrefs.writeDump(ctx, dump)
                L.i("module diagnostic dump stored (${dump.length} chars)")
                Bundle().apply { putBoolean(EXTRA_OK, true) }
            }
            METHOD_FALLBACK -> {
                // The module had to fall back (e.g. Rive could not draw). Stored so the app can alert the
                // user to send the log; an empty reason clears it.
                val ctx = context
                val reason = extras?.getString(EXTRA_FALLBACK).orEmpty()
                if (ctx != null) DuoPrefs.writeFallback(ctx, reason)
                L.i("module fallback: ${reason.ifEmpty { "none (cleared)" }}")
                Bundle().apply { putBoolean(EXTRA_OK, true) }
            }
            METHOD_STOCK_ICONS -> {
                // The live icon list built from the module's own view tree; the settings screen renders it.
                val ctx = context
                val json = extras?.getString(EXTRA_STOCK_ICONS).orEmpty()
                if (ctx != null && json.isNotEmpty()) DuoPrefs.writeStockIcons(ctx, json)
                L.i("module stock icon inventory stored (${json.length} chars)")
                Bundle().apply { putBoolean(EXTRA_OK, true) }
            }
            else -> Bundle().apply { putBoolean(EXTRA_OK, false) }
        }
    } catch (t: Throwable) {
        L.w("call $method failed: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "DuoSB"
        const val METHOD_STATUS = "status"
        const val EXTRA_STATUS = "status"
        /** Debug-only: the full diagnostic dump, stored for the shareable report file. */
        const val METHOD_DUMP = "dump"
        const val EXTRA_DUMP = "dump"
        /** The module's fallback reason (Rive failed); drives the app's "send the log" alert. */
        const val METHOD_FALLBACK = "fallback"
        const val EXTRA_FALLBACK = "fallback"
        /** The live stock-icon inventory; drives the per-icon list on the settings screen. */
        const val METHOD_STOCK_ICONS = "stock_icons"
        const val EXTRA_STOCK_ICONS = "stock_icons"
        const val EXTRA_OK = "ok"

        /**
         * The single row the module reads: one value per column in [DuoPrefs.COLUMNS], booleans as 1/0.
         * Portrait columns come first; landscape repeats them (except the shared revision) so the module
         * can pick a set from the status bar's own orientation.
         *
         * [landscape] defaults to [settings] so a caller that only has one copy still publishes a full row.
         * Pure on purpose. The provider's own plumbing needs a framework context, which unit tests cannot
         * convincingly supply — but the *shape* of what crosses the uid boundary is exactly the part that
         * breaks silently (a reordered column means the module reads the size as the offset), so it is
         * separated out and tested directly.
         */
        internal fun rowFor(
            settings: DuoSettings,
            revision: Long,
            landscape: DuoSettings = settings
        ): Array<Any> {
            val portrait = encoded(settings, revision)
            val land = encoded(landscape, revision)
            val tail = ArrayList<Any>(DuoPrefs.LANDSCAPE_COLUMNS.size)
            for (i in portrait.indices) {
                if (DuoPrefs.PORTRAIT_COLUMNS[i] == DuoPrefs.COL_REVISION) continue
                tail.add(land[i])
            }
            return (portrait.toList() + tail).toTypedArray()
        }

        /** One orientation's values, in [DuoPrefs.PORTRAIT_COLUMNS] order. */
        private fun encoded(settings: DuoSettings, revision: Long): Array<Any> = arrayOf(
            if (settings.enabled) 1 else 0,
            if (settings.useRive) 1 else 0,
            if (settings.showPercent) 1 else 0,
            settings.sizePercent,
            settings.offsetX,
            if (settings.liveApply) 1 else 0,
            if (settings.systemClockFont) 1 else 0,
            revision,
            settings.tapAction,
            settings.doubleTapAction,
            settings.longPressAction,
            DuoPrefs.nearestReveal(settings.revealMs),
            if (settings.animationsEnabled) 1 else 0,
            if (settings.arrivalEnabled) 1 else 0,
            if (settings.departureEnabled) 1 else 0,
            if (settings.chargingEnabled) 1 else 0,
            settings.iconColor,
            if (settings.hideOtherIcons) 1 else 0,
            if (!settings.showAirplane && !DuoPrefs.dndInMiddle(settings.dndMode)) 1 else 0,
            settings.simChoice,
            settings.percentHeight.coerceIn(DuoPrefs.MIN_PERCENT_HEIGHT, DuoPrefs.MAX_PERCENT_HEIGHT),
            if (settings.splitIndicators) 1 else 0,
            settings.indicatorsOffsetX.coerceIn(
                -DuoPrefs.MAX_INDICATORS_OFFSET,
                DuoPrefs.MAX_INDICATORS_OFFSET
            ),
            if (settings.wifiDots) 1 else 0,
            if (settings.showAirplane) 1 else 0,
            if (DuoPrefs.dndInMiddle(settings.dndMode)) 1 else 0,
            settings.edgePadding.coerceIn(DuoPrefs.MIN_EDGE_PADDING, DuoPrefs.MAX_EDGE_PADDING),
            DuoPrefs.normalizeDndMode(settings.dndMode),
            if (settings.showChargingBolt) 1 else 0,
            if (settings.lockRing) 1 else 0,
            settings.indicatorsTapAction,
            settings.indicatorsDoubleTapAction,
            settings.indicatorsLongPressAction,
            if (settings.showInQs) 1 else 0,
            if (settings.showInShade) 1 else 0,
            DuoPrefs.normalizeEngine(settings.engineMode),
            DuoPrefs.normalizeMiddleSlot(settings.middleSlot),
            if (settings.showWifiGlyph) 1 else 0,
            if (settings.showCellDots) 1 else 0,
            settings.eventMask and DuoPrefs.EVENT_ALL,
            if (settings.downloadProgressEnabled) 1 else 0,
            if (settings.burnInEnabled) 1 else 0,
            DuoPrefs.joinHiddenStockIcons(
                DuoPrefs.parseHiddenStockIcons(settings.hiddenStockIcons)
            ),
            DuoPrefs.normalizeDesignStyle(settings.designStyle)
        )
    }
}
