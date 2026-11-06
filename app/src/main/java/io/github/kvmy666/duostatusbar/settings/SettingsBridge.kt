package io.github.kvmy666.duostatusbar.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kvmy666.duostatusbar.L

/**
 * The settings channel that does not depend on the ContentProvider being visible.
 *
 * On most ROMs the module reads settings from the app's provider, which works. On One UI 8 the provider
 * is invisible to System UI (`Unknown authority …`), so the module read nothing and stayed off. This
 * bridge carries the **same row** the provider would return as a broadcast between the two processes:
 *
 *  - app → System UI: a settings "push" (on every write, on app start, and on request),
 *  - System UI → app: a status/dump/fallback "push", so the app still shows real diagnostics.
 *
 * The module registers its receiver with [PERMISSION], a signature permission this app defines and
 * holds, so only this app can push settings; other apps cannot drive the status bar.
 */
internal object SettingsBridge {

    const val ACTION_SETTINGS_PUSH = "io.github.kvmy666.duostatusbar.action.SETTINGS_PUSH"
    const val ACTION_SETTINGS_REQUEST = "io.github.kvmy666.duostatusbar.action.SETTINGS_REQUEST"
    const val ACTION_STATUS_PUSH = "io.github.kvmy666.duostatusbar.action.STATUS_PUSH"
    const val ACTION_DUMP_PUSH = "io.github.kvmy666.duostatusbar.action.DUMP_PUSH"
    const val ACTION_FALLBACK_PUSH = "io.github.kvmy666.duostatusbar.action.FALLBACK_PUSH"
    const val ACTION_STOCK_ICONS_PUSH = "io.github.kvmy666.duostatusbar.action.STOCK_ICONS_PUSH"

    /** The provider row, serialized as `ArrayList<Any>` (ints, longs, strings). */
    const val EXTRA_VALUES = "values"
    const val EXTRA_STATUS = "status"
    const val EXTRA_DUMP = "dump"
    const val EXTRA_FALLBACK = "fallback"

    /** The live stock-icon list as the compact JSON from [StockIconInventory]. */
    const val EXTRA_STOCK_ICONS = "stock_icons"

    const val PERMISSION = "io.github.kvmy666.duostatusbar.permission.SETTINGS"
    const val SYSTEMUI = "com.android.systemui"

    /** Publishes one full settings row to System UI. Never throws. */
    fun push(context: Context) {
        try {
            val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
            val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
            val row = DuoSettingsProvider.rowFor(portrait, DuoPrefs.revision(context), landscape)
            context.sendBroadcast(
                Intent(ACTION_SETTINGS_PUSH).setPackage(SYSTEMUI)
                    .putExtra(EXTRA_VALUES, ArrayList<Any>(row.toList()))
            )
            L.i("settings bridge: pushed rev ${DuoPrefs.revision(context)}")
        } catch (t: Throwable) {
            L.w("settings bridge push: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}

/** Push the settings once after a reboot, so the module has them without waiting for the app to open. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SettingsBridge.push(context.applicationContext)
        }
    }
}

/** System UI asks for the settings (it has no row yet); the app answers with a push. */
class SettingsRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SettingsBridge.ACTION_SETTINGS_REQUEST) {
            SettingsBridge.push(context.applicationContext)
        }
    }
}

/** System UI reports its status/dump/fallback; the app stores them for the About screen. */
class StatusBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        try {
            when (intent.action) {
                SettingsBridge.ACTION_STATUS_PUSH ->
                    intent.getStringExtra(SettingsBridge.EXTRA_STATUS)
                        ?.let { DuoPrefs.writeStatus(app, it) }
                SettingsBridge.ACTION_DUMP_PUSH ->
                    intent.getStringExtra(SettingsBridge.EXTRA_DUMP)
                        ?.let { DuoPrefs.writeDump(app, it) }
                SettingsBridge.ACTION_FALLBACK_PUSH ->
                    intent.getStringExtra(SettingsBridge.EXTRA_FALLBACK)
                        ?.let { DuoPrefs.writeFallback(app, it) }
                SettingsBridge.ACTION_STOCK_ICONS_PUSH ->
                    intent.getStringExtra(SettingsBridge.EXTRA_STOCK_ICONS)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { DuoPrefs.writeStockIcons(app, it) }
            }
        } catch (t: Throwable) {
            L.w("status bridge: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
