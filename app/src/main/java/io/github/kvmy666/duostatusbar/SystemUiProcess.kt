package io.github.kvmy666.duostatusbar

/**
 * Decides whether a loaded process is the one the module hooks.
 *
 * Matching only the package name missed SystemUI on HyperOS / Android 16 (issue #5): there the
 * SystemUI process is reported with `packageName = "system"` while its process name is still
 * `com.android.systemui`. LSPosed still ran `MainHook` inside it, but the package-name branch did
 * not match, so every hook was skipped and the status bar never changed — the app then correctly
 * reported "the module was never injected".
 *
 * The process name is the stable identity across OEM builds, so it is checked as well. Only the main
 * SystemUI process is a target: any `com.android.systemui:<suffix>` (One UI's `:edgelighting` and
 * `:screenshot`) is a side process with no status bar, and installing there produced reports and a
 * stamp from a process that never owned the bar. A missing/blank process name can only fall back to
 * the exact package name. A pure function, pinned by a unit test.
 */
internal object SystemUiProcess {

    const val PACKAGE = "com.android.systemui"

    fun isTarget(packageName: String?, processName: String?): Boolean {
        if (processName.isNullOrBlank()) return packageName == PACKAGE
        return processName == PACKAGE
    }
}
