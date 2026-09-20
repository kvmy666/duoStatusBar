package io.github.kvmy666.duostatusbar.probe

import android.content.Context
import android.content.res.Configuration
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L
import java.io.File

/**
 * The textual half of the Phase 0 probes (P-01 class/method inventory, P-03 native library,
 * P-05 metrics). Pure reporting: it queries the running ROM and writes to logcat.
 *
 * FR-26 exists because of this file: no hook is ever written against a class or method name that
 * this report has not confirmed on the device first.
 */
internal object ProbeReport {

    private const val MODULE_PKG = "io.github.kvmy666.duostatusbar"

    /** Classes we believe we need. Their presence is logged, never assumed. */
    private val candidates = listOf(
        "com.android.systemui.statusbar.phone.CollapsedStatusBarFragment",
        "com.android.systemui.statusbar.phone.PhoneStatusBarView",
        "com.android.systemui.statusbar.phone.PhoneStatusBarViewController",
        "com.android.systemui.statusbar.phone.CentralSurfacesImpl",
        "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
        "com.android.systemui.statusbar.phone.StatusBarIconController",
        "com.android.systemui.statusbar.StatusBarIconView",
        "com.android.systemui.statusbar.policy.BatteryControllerImpl",
        "com.android.systemui.battery.BatteryMeterView",
        "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
        "com.android.systemui.statusbar.policy.NetworkControllerImpl",
        "com.android.systemui.statusbar.connectivity.WifiSignalController",
        "com.android.systemui.statusbar.policy.DarkIconDispatcherImpl",
        "com.android.systemui.statusbar.StatusBarStateControllerImpl",
        "com.android.systemui.qs.QSPanel",
        "com.android.systemui.qs.QuickQSPanel",
        "com.android.systemui.shade.NotificationShadeWindowView",
        "com.android.systemui.statusbar.phone.NotificationPanelViewController"
    )

    /** Only these names get dumped, so the report stays human-readable. */
    private val methodKeywords =
        Regex("icon|visib|battery|wifi|mobile|signal|airplane|dark|alpha|slot|level", RegexOption.IGNORE_CASE)

    fun inventory(lp: XC_LoadPackage.LoadPackageParam) {
        var found = 0
        for (name in candidates) {
            val cls = try {
                XposedHelpers.findClass(name, lp.classLoader)
            } catch (_: Throwable) {
                null
            }
            if (cls == null) {
                L.i("P-01 MISSING $name")
                continue
            }
            found++
            val methods = cls.declaredMethods
                .filter { methodKeywords.containsMatchIn(it.name) }
                .map { m -> m.name + "(" + m.parameterTypes.joinToString(",") { it.simpleName } + ")" }
                .distinct()
                .sorted()
            L.i("P-01 FOUND   $name [${methods.size}] ${methods.joinToString(" ")}")
        }
        L.i("P-01 inventory: $found/${candidates.size} candidate classes exist on this ROM")
    }

    /**
     * The make-or-break question for the whole project: LSPosed loads our *classes* into the
     * SystemUI process, but loading our *native* library there is a separate permission question.
     * (`useLegacyPackaging` in app/build.gradle.kts keeps librive.so extracted on disk so this
     * probe can hand System.load a real path.)
     */
    fun nativeLibrary(app: Context?) {
        if (app == null) {
            L.i("P-03 no Application context captured yet - skipped")
            return
        }
        val info = app.packageManager.getApplicationInfo(MODULE_PKG, 0)
        val dir = File(info.nativeLibraryDir ?: "")
        L.i("P-03 module apk=${info.sourceDir}")
        L.i("P-03 module nativeLibraryDir=$dir")

        val libs = dir.listFiles()?.filter { it.name.endsWith(".so") }.orEmpty()
        L.i("P-03 shared objects on disk: " + libs.joinToString { "${it.name}(${it.length()}B)" })
        if (libs.isEmpty()) {
            L.i("P-03 VERDICT: no .so extracted -> need runtime extraction or a companion module")
            return
        }

        // Order matters: librive-android.so links against libc++_shared.so, so load the C++ runtime first.
        val ordered = libs.sortedBy { if (it.name.contains("c++")) 0 else 1 }
        var loaded = 0
        for (lib in ordered) {
            try {
                System.load(lib.absolutePath)
                loaded++
                L.i("P-03 loaded ${lib.name}")
            } catch (t: Throwable) {
                L.e("P-03 load ${lib.name}", t)
            }
        }
        L.i(
            if (loaded == libs.size)
                "P-03 VERDICT: Rive can render INSIDE SystemUI ($loaded/${libs.size} libraries loaded)"
            else
                "P-03 VERDICT: only $loaded/${libs.size} libraries loaded - fallback path required"
        )
    }

    fun metrics(app: Context?) {
        if (app == null) return
        val res = app.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        val height = if (id != 0) res.getDimensionPixelSize(id) else -1
        val dm = res.displayMetrics
        val cfg: Configuration = res.configuration
        L.i(
            "P-05 status_bar_height=${height}px density=${dm.density} densityDpi=${dm.densityDpi} " +
                "screen=${dm.widthPixels}x${dm.heightPixels} orientation=${cfg.orientation} fontScale=${cfg.fontScale}"
        )
    }
}
