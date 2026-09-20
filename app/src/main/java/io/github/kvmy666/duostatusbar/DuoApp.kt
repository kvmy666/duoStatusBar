package io.github.kvmy666.duostatusbar

import android.app.Application

class DuoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // See RiveInit: rive-android 10.x requires this explicit call; it is also what the
        // SystemUI-side integration will use.
        RiveInit.ensure(this)
    }
}
