package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.Build
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection

/**
 * The debug-only evidence collector (issue: support Android 14 / ColorOS / One UI).
 *
 * A foreign ROM cannot be supported from a screenshot. This gathers, in one text block, everything needed
 * to add or correct a ROM adapter without another round-trip: the build identity, the resolved adapter, the
 * SystemUI package and uid, the window facts, **every candidate id** the module might use and whether it
 * resolves, the status bar's whole view tree with ids and bounds, and the live reader values.
 *
 * It is deliberately free of any state-changing call: it only reads. The result is written to both log
 * sinks through [L] (logcat, and LSPosed's log where the ROM filters logcat) and pushed to the settings app
 * over the provider, so the user's "Save status to a file" button ships the whole thing as a .txt.
 *
 * Gated by [BuildConfig.DEBUG] at the call site: release builds keep the compact status line only.
 */
internal object Diag {

    /** Every id any adapter mentions, plus the AOSP and OEM spellings worth probing on an unknown ROM. */
    private val CANDIDATE_IDS = listOf(
        "system_icons",
        "system_icons_container",
        "statusIcons",
        "status_icons",
        "status_bar",
        "status_bar_contents",
        "status_bar_end_side_content",
        "status_bar_end_side_container",
        "status_bar_start_side_content",
        "battery",
        "clock",
        "clock_for_fake",
        "shade_header_system_icons",
        "notificationIcons",
        "notification_icon_area"
    )

    private const val MAX_TREE_DEPTH = 14
    private const val FLAG_HARDWARE_ACCELERATED = 0x01000000

    /**
     * Builds the dump. [root] is the status-bar window (may be null before it is found), [element] the
     * current drawing. Never throws: a diagnostic that dies is worse than no diagnostic.
     */
    fun collect(
        context: Context,
        root: View?,
        stage: Int,
        settings: ModuleSettings?,
        element: DuoElement?
    ): String = try {
        val rom = RomDetection.forThisRom(
            Build.MANUFACTURER.orEmpty(),
            Build.BRAND.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.DISPLAY.orEmpty()
        )
        buildString {
            appendLine("Duo Status Bar diagnostic dump")
            appendLine("version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, debug=${BuildConfig.DEBUG})")

            appendLine("build:")
            appendLine("  MANUFACTURER=${Build.MANUFACTURER} BRAND=${Build.BRAND} MODEL=${Build.MODEL}")
            appendLine("  DEVICE=${Build.DEVICE} PRODUCT=${Build.PRODUCT} HARDWARE=${Build.HARDWARE}")
            appendLine("  DISPLAY=${Build.DISPLAY} ID=${Build.ID}")
            appendLine("  SDK_INT=${Build.VERSION.SDK_INT} RELEASE=${Build.VERSION.RELEASE} INCREMENTAL=${Build.VERSION.INCREMENTAL}")

            appendLine("rom adapter:")
            appendLine("  id=${rom.id} label=${rom.label}")
            appendLine("  systemUiPackage=${rom.systemUiPackage}")
            appendLine("  containerIds=${rom.containerIds}")
            appendLine("  batteryId=${rom.batteryId} clockId=${rom.clockId}")
            appendLine("  notes=${rom.notes}")

            appendLine("systemui:")
            appendLine("  uid=${Process.myUid()} pid=${Process.myPid()}")
            appendPackageInfo(context, this, "com.android.systemui")

            appendLine("window:")
            appendWindowFacts(context, this, root)

            appendLine("container probes (id -> view):")
            for (name in CANDIDATE_IDS) {
                val id = try {
                    context.resources.getIdentifier(name, "id", rom.systemUiPackage)
                } catch (_: Throwable) {
                    0
                }
                if (id == 0) {
                    appendLine("  $name -> no such id")
                    continue
                }
                val found = root?.findViewById<View>(id)
                if (found == null) {
                    appendLine("  $name (0x${id.toString(16)}) -> id exists, view not in this window")
                } else {
                    appendLine("  $name (0x${id.toString(16)}) -> ${found.javaClass.name} " +
                            "${found.width}x${found.height} vis=${found.visibility}")
                }
            }

            appendLine("view tree (status bar window):")
            if (root == null) {
                appendLine("  (no status bar window attached yet)")
            } else {
                dumpTree(root, this, 0)
            }

            appendLine("readers:")
            appendReaders(context, this)

            appendLine("module:")
            appendLine("  stage=$stage attempts=${DuoGuard(context).attempts()}")
            appendLine("  renderer=${element?.rendererName ?: "none"} ready=${element?.isReady ?: false}")
            settings?.let {
                appendLine("  settings: enabled=${it.enabled} useRive=${it.useRive} showPercent=${it.showPercent}")
                appendLine("    size=${it.sizePercent}% offset=${it.offsetX}dp live=${it.liveApply} rev=${it.revision}")
                appendLine("    animations=${it.animationsEnabled} arrival=${it.arrivalEnabled} " +
                        "departure=${it.departureEnabled} charging=${it.chargingEnabled}")
                appendLine("    clockFont=${it.systemClockFont} revealMs=${it.revealMs}")
            }
        }
    } catch (t: Throwable) {
        "Duo Status Bar diagnostic dump failed: ${t.javaClass.simpleName}: ${t.message}"
    }

    /** Logs the dump through [L] (both sinks) once, so a log capture has it too. */
    fun log(dump: String) {
        for (line in dump.lines()) L.i(line)
    }

    private fun appendPackageInfo(context: Context, sb: StringBuilder, pkg: String) {
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(pkg, 0)
            sb.appendLine("  $pkg versionName=${info.versionName} versionCode=${info.longVersionCode} uid=${info.applicationInfo?.uid}")
            sb.appendLine("  sourceDir=${info.applicationInfo?.sourceDir}")
        } catch (t: Throwable) {
            sb.appendLine("  $pkg package info unavailable: ${t.javaClass.simpleName}")
        }
    }

    private fun appendWindowFacts(context: Context, sb: StringBuilder, root: View?) {
        try {
            sb.appendLine("  density=${context.resources.displayMetrics.density} " +
                    "densityDpi=${context.resources.displayMetrics.densityDpi}")
            if (root == null) {
                sb.appendLine("  status bar window: not attached")
                return
            }
            val lp = root.layoutParams
            val flags = if (lp is WindowManager.LayoutParams) lp.flags else 0
            sb.appendLine("  status bar window: ${root.javaClass.name}")
            sb.appendLine("  hardwareAccelerated=${root.isHardwareAccelerated} " +
                    "FLAG_HARDWARE_ACCELERATED=${flags and FLAG_HARDWARE_ACCELERATED != 0}")
            sb.appendLine("  attached=${root.isAttachedToWindow} size=${root.width}x${root.height}")
        } catch (t: Throwable) {
            sb.appendLine("  window facts unavailable: ${t.javaClass.simpleName}")
        }
    }

    private fun appendReaders(context: Context, sb: StringBuilder) {
        try {
            val airplane = SystemReaders.isAirplaneOn(context)
            sb.appendLine("  wifiEnabled=${SystemReaders.isWifiEnabled(context, false)} " +
                    "wifiActive=${SystemReaders.isWifiActive(context, false)} " +
                    "wifiLevel=${SystemReaders.wifiLevel(context, -1)}")
            sb.appendLine("  cellLevel=${SystemReaders.cellLevel(context, airplane, -1)} " +
                    "generation=${SystemReaders.networkGeneration(context, airplane, "?")}")
            sb.appendLine("  airplane=$airplane saver=${SystemReaders.isPowerSaveOn(context)} " +
                    "dnd=${SystemReaders.isDndOn(context)}")
        } catch (t: Throwable) {
            sb.appendLine("  readers unavailable: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** A depth-limited walk: enough to see the strip and its siblings, never the whole window. */
    private fun dumpTree(view: View, sb: StringBuilder, depth: Int) {
        if (depth > MAX_TREE_DEPTH) return
        val indent = "  ".repeat(depth + 1)
        sb.append(indent)
            .append(view.javaClass.simpleName)
            .append(" id=").append(idName(view))
            .append(' ').append(view.width).append('x').append(view.height)
            .append(" vis=").append(view.visibility)
        val location = IntArray(2)
        try {
            view.getLocationInWindow(location)
            sb.append(" @").append(location[0]).append(',').append(location[1])
        } catch (_: Throwable) {
        }
        sb.appendLine()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) dumpTree(view.getChildAt(i), sb, depth + 1)
        }
    }

    private fun idName(view: View): String {
        if (view.id == View.NO_ID) return "-"
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            "0x${view.id.toString(16)}"
        }
    }
}
