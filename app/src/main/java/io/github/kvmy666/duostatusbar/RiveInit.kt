package io.github.kvmy666.duostatusbar

import android.content.Context
import java.io.File
import java.lang.reflect.Field

/**
 * Starts the Rive runtime — including the half the library cannot do for itself in this process.
 *
 * rive-android 10.2.0 does not initialise itself: its AAR manifest removes the AndroidX-Startup entry
 * (`<meta-data android:name="…RiveInitializer" tools:node="remove"/>`). `RiveInitializer.create()` is
 * byte-for-byte `Rive.init(context, RendererType.Rive)`, and `Rive.init` is:
 *
 *     ReLinker.recursively().loadLibrary(context, "rive-android")   // looks in *the Context's* package
 *     defaultRendererType = type                                    // private static field
 *     cppInitialize()                                               // native environment
 *
 * Inside the SystemUI process line 1 cannot work: ReLinker looks in the package of the Context it is
 * handed (com.android.systemui), which ships no `librive-android.so`. It throws — and because it throws
 * there, `defaultRendererType` is **never assigned**. That leaves null in the static that `Renderer`'s
 * generated constructor reads (`type = Rive.getDefaultRendererType()`), with no null check, and the
 * null travels into native `Renderer.make()` → SIGSEGV. That is what broke SystemUI on the first Phase 3
 * attempt: not a missing try/catch (every call site is guarded), but a native fault, which no Kotlin
 * handler can intercept.
 *
 * So this class performs the same three steps itself, with the broken line replaced by something that
 * works here:
 *
 *   1. `System.load(absolutePath)` on the module's own `.so` files — Phase 0's P-03 probe proved this
 *      works inside SystemUI where `loadLibrary` does not. C++ runtime before `librive-android.so`.
 *   2. `defaultRendererType = RendererType.Canvas`, so the static is never null. `Canvas` is chosen
 *      deliberately: `RiveAnimationView` is a `TextureView`-based surface, and `Canvas` is the backend
 *      that does not depend on the device's GL path being reachable from a system process.
 *   3. `initializeCppEnvironment()`, the public wrapper around the private native `cppInitialize()`.
 *
 * `RiveInitializer` is deliberately never called: its first step can only fail here, and failing there is
 * precisely what leaves the null behind.
 *
 * Returns true only when the renderer type reads back non-null and the native environment came up, and
 * never throws: a false result makes callers use the no-native renderer instead (FR-21).
 */
internal object RiveInit {

    private const val TAG = "DuoSB"
    private const val MODULE_PACKAGE = "io.github.kvmy666.duostatusbar"
    private const val RIVE_CLASS = "app.rive.runtime.kotlin.core.Rive"
    private const val TYPE_CLASS = "app.rive.runtime.kotlin.core.RendererType"

    @Volatile
    private var result: Boolean? = null

    /** Idempotent: safe to call from an Application and again from a view factory. */
    fun ensure(context: Context): Boolean {
        result?.let { return it }
        synchronized(this) {
            result?.let { return it }
            val ok = try {
                loadNativeLibraries(context) && finishInitialisation()
            } catch (t: Throwable) {
                L.e("Rive runtime not started: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            result = ok
            return ok
        }
    }

    /** Step 1: load the module's own libraries by absolute path. */
    private fun loadNativeLibraries(context: Context): Boolean {
        val nativeDir = context.packageManager
            .getApplicationInfo(MODULE_PACKAGE, 0)
            .nativeLibraryDir
            ?: run {
                L.e("no nativeLibraryDir for $MODULE_PACKAGE")
                return false
            }
        val libs = File(nativeDir).listFiles { f -> f.name.endsWith(".so") }
        if (libs.isNullOrEmpty()) {
            L.e("no .so files in $nativeDir")
            return false
        }
        var loaded = 0
        for (lib in libs.sortedBy { if (it.name.contains("c++")) 0 else 1 }) {
            try {
                System.load(lib.absolutePath)
                loaded++
                L.i("native loaded: ${lib.name}")
            } catch (t: Throwable) {
                // "already loaded in this process" lands here on a second call and is harmless.
                L.w("native ${lib.name}: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        return loaded > 0
    }

    /** Steps 2 and 3: the half of `Rive.init` that ReLinker's failure skips. */
    private fun finishInitialisation(): Boolean {
        val riveClass = Class.forName(RIVE_CLASS)
        val instance = riveClass.getField("INSTANCE").get(null) ?: return false
        val canvas = Class.forName(TYPE_CLASS).getField("Canvas").get(null) ?: return false

        val field: Field = riveClass.getDeclaredField("defaultRendererType")
        field.isAccessible = true
        field.set(null, canvas)

        riveClass.getMethod("initializeCppEnvironment").invoke(instance)

        val readBack = riveClass.getMethod("getDefaultRendererType").invoke(instance)
        if (readBack == null) {
            L.e("defaultRendererType is still null - refusing to create a Rive view")
            return false
        }
        L.i("Rive runtime ready: defaultRendererType=$readBack")
        return true
    }
}
