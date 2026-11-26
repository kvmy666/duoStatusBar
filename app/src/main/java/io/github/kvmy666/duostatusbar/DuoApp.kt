package io.github.kvmy666.duostatusbar

import android.app.Application
import io.github.kvmy666.duostatusbar.settings.AutoReportManager
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import io.github.kvmy666.duostatusbar.settings.UpdateWorker

class DuoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // See RiveInit: rive-android 10.x requires this explicit call; it is also what the
        // SystemUI-side integration will use.
        RiveInit.ensure(this)
        // Keep the background update check in step with the user's toggle (idempotent).
        UpdateWorker.apply(this)
        // Same for automatic problem reports: no consent means nothing is scheduled.
        AutoReportManager.apply(this)
        // Publish the settings on the provider-independent channel, so System UI has them even on a ROM
        // where it cannot see the provider (One UI 8).
        SettingsBridge.push(this)
    }
}
