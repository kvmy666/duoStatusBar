package io.github.kvmy666.duostatusbar

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.hook.DuoHook

/**
 * Module entry point — declared in `assets/xposed_init`, instantiated by LSPosed.
 *
 * Phase 0 only installs the evidence collector (log-only). Hooks that change SystemUI arrive in
 * Phase 3, and each of those lives behind the same try/catch discipline so that a failure can
 * never take the system down (FR-21).
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            L.i("MainHook loaded into ${lpparam.packageName} (process=${lpparam.processName})")

            when (lpparam.packageName) {
                "com.android.systemui" -> DuoHook(lpparam).install()
                else -> Unit // out of scope: do nothing at all
            }
        } catch (t: Throwable) {
            L.e("MainHook.handleLoadPackage", t)
        }
    }
}
