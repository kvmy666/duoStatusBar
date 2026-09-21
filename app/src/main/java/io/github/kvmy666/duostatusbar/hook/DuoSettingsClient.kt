package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

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
    val revision: Long
) {
    companion object {
        val DEFAULT = ModuleSettings(
            enabled = false,
            useRive = true,
            showPercent = true,
            sizePercent = 100,
            offsetX = 0,
            revision = 0L
        )
    }
}

internal object DuoSettingsClient {

    private const val TAG = "DuoSB"

    /** Sent by the app after a write, so the module re-reads without polling (NFR-2). */
    const val ACTION_SETTINGS_CHANGED = "io.github.kvmy666.duostatusbar.SETTINGS_CHANGED"

    private val uri: Uri get() = Uri.parse("content://${DuoPrefs.AUTHORITY}")

    fun read(context: Context): ModuleSettings = try {
        val started = android.os.SystemClock.elapsedRealtime()
        val result = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                ModuleSettings.DEFAULT
            } else {
                ModuleSettings(
                    enabled = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_ENABLED)) == 1,
                    useRive = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_USE_RIVE)) == 1,
                    showPercent = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_SHOW_PERCENT)) == 1,
                    sizePercent = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_SIZE_PERCENT)),
                    offsetX = cursor.getInt(cursor.getColumnIndexOrThrow(DuoPrefs.COL_OFFSET_X)),
                    revision = cursor.getLong(cursor.getColumnIndexOrThrow(DuoPrefs.COL_REVISION))
                )
            }
        } ?: ModuleSettings.DEFAULT
        // Logged because this is a synchronous binder call from System UI's boot path: if it is ever slow,
        // it is slow there, and that deserves a number rather than a guess.
        Log.i(TAG, "settings read in ${android.os.SystemClock.elapsedRealtime() - started} ms (rev ${result.revision})")
        result
    } catch (t: Throwable) {
        Log.w(TAG, "settings unreadable (${t.javaClass.simpleName}: ${t.message}) - using defaults")
        ModuleSettings.DEFAULT
    }

    /** Tells the app what the module actually did, for the diagnostics screen. Never throws. */
    fun report(context: Context, status: String) {
        try {
            val extras = Bundle().apply { putString("status", status) }
            context.contentResolver.call(uri, "status", null, extras)
        } catch (t: Throwable) {
            Log.w(TAG, "status report failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
