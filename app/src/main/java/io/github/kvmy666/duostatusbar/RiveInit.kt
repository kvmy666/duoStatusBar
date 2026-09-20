package io.github.kvmy666.duostatusbar

import android.content.Context
import android.util.Log

/**
 * Starts the Rive runtime.
 *
 * rive-android 10.x does not initialise itself: its AAR manifest removes the AndroidX-Startup entry
 * (`<meta-data android:name="app.rive.runtime.kotlin.RiveInitializer" … tools:node="remove"/>`) and
 * the version has no `Rive.init()`. The supported entry point is the public `RiveInitializer`, which
 * implements `androidx.startup.Initializer`.
 *
 * Reflection is used on purpose — and not only here: the same call will be made from inside the
 * SystemUI process in Phase 3, where our classes are loaded into a process whose classpath we do not
 * control, so a reflective, dependency-free call is the portable form. It is also wrapped, because a
 * Rive failure must never be able to take a system process down (FR-21).
 */
internal object RiveInit {

    private const val INITIALIZER = "app.rive.runtime.kotlin.RiveInitializer"
    private var done = false

    /** Idempotent: safe to call from an Application and again from a view factory. */
    fun ensure(context: Context) {
        if (done) return
        try {
            val initializer = Class.forName(INITIALIZER).getDeclaredConstructor().newInstance()
            initializer.javaClass.getMethod("create", Context::class.java).invoke(initializer, context)
            done = true
            Log.i("DuoSB", "Rive runtime initialised")
        } catch (t: Throwable) {
            Log.e("DuoSB", "Rive init failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
