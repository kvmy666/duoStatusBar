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
            val s = DuoPrefs.read(ctx)
            MatrixCursor(DuoPrefs.COLUMNS).apply {
                addRow(rowFor(s, DuoPrefs.revision(ctx)))
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
        const val EXTRA_OK = "ok"

        /**
         * The single row the module reads: one value per column in [DuoPrefs.COLUMNS], booleans as 1/0.
         *
         * Pure on purpose. The provider's own plumbing needs a framework context, which unit tests cannot
         * convincingly supply — but the *shape* of what crosses the uid boundary is exactly the part that
         * breaks silently (a reordered column means the module reads the size as the offset), so it is
         * separated out and tested directly.
         */
        internal fun rowFor(settings: DuoSettings, revision: Long): Array<Any> = arrayOf(
            if (settings.enabled) 1 else 0,
            if (settings.useRive) 1 else 0,
            if (settings.showPercent) 1 else 0,
            settings.sizePercent,
            settings.offsetX,
            revision,
            settings.tapAction,
            settings.doubleTapAction,
            settings.longPressAction,
            DuoPrefs.nearestReveal(settings.revealMs)
        )
    }
}
