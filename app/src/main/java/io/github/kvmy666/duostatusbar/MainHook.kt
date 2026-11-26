package io.github.kvmy666.duostatusbar

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.hook.DuoHook
import io.github.kvmy666.duostatusbar.hook.ProcessState

/**
 * Module entry point — declared in `assets/xposed_init`, instantiated by LSPosed.
 *
 * The gate comes before anything else, including the "loaded" line:
 *  - a side process (`com.android.systemui:edgelighting`, `:screenshot`, any `:suffix`) and every other
 *    package emit exactly one `skipped process=… pkg=…` line and do nothing else — no device facts, no
 *    load stamp, no status/dump write, no hooks (the One UI reports came from `:edgelighting`, a process
 *    with no status bar, and they overwrote the main process's report);
 *  - in the main process exactly one class-loader load wins [ProcessState.claimInstall]; a duplicate
 *    load (LSPosed sees `system` and `com.oplus.keyguard.*` in the same pid) emits one
 *    `skipped duplicate …` line;
 *  - only the winner logs the device facts, installs, and logs completion. If the install throws, the
 *    claim is released so a later load can retry.
 *
 * This is the only entry point in the module: `IXposedHookZygoteInit`, the resources hooks and any app
 * process hook are absent (`assets/xposed_init` names this class alone).
 */
class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val process = lpparam.processName
            val pkg = lpparam.packageName

            if (!SystemUiProcess.isTarget(pkg, process)) {
                L.i("skipped process=$process pkg=$pkg")
                return
            }

            if (!ProcessState.claimInstall()) {
                L.i("skipped duplicate process=$process pkg=$pkg (already installed pid=${ProcessIdentity.pid()})")
                return
            }

            try {
                // Once per SystemUI load: the two facts that decide whether native Rive can load at all.
                // A report without them cannot tell "Rive is denied by SELinux" from "Rive crashed".
                L.i("device: selinux=${DeviceFacts.selinux()} abi=${DeviceFacts.abi()} " +
                        "sdk=${android.os.Build.VERSION.SDK_INT}")
                L.i("MainHook step: target matched - installing DuoHook")
                DuoHook(lpparam).install()
                L.i("MainHook step: DuoHook.install completed")
            } catch (t: Throwable) {
                // Partial install recovery: let the next class-loader load try once more.
                ProcessState.resetInstall()
                throw t
            }
        } catch (t: Throwable) {
            L.e("MainHook.handleLoadPackage", t)
        }
    }
}
