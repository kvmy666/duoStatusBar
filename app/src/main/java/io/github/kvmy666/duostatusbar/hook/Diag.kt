package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.Build
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.DeviceFacts
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.hook.rom.RomProfiles
import io.github.kvmy666.duostatusbar.hook.rom.RomResources

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
        // HyperOS / One UI spellings, so an adapter can be added from one report without a second round.
        "status_bar_icons",
        "system_icon_area",
        "systemIcons",
        "status_icons",
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
        element: DuoElement?,
        /**
         * Other windows the module draws into (the shade/keyguard window, the shade header). Issue #1
         * ("the shade element jumps from right to left when Quick Settings expands") cannot be fixed
         * without their tree, and the status-bar walk above never reaches them.
         */
        extraRoots: List<Pair<String, View?>> = emptyList(),
        /** A previously captured tree (e.g. the shade header while it was open). */
        extraSnapshot: String = ""
    ): String = try {
        val profiles = RomProfiles.load(context)
        val rom = RomDetection.forThisRom(
            Build.MANUFACTURER.orEmpty(),
            Build.BRAND.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.DISPLAY.orEmpty(),
            profiles
        )
        val fromAsset = profiles.any { it == rom }
        buildString {
            appendLine("Duo Status Bar diagnostic dump")
            appendLine("version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, debug=${BuildConfig.DEBUG})")

            appendLine("build:")
            appendLine("  MANUFACTURER=${Build.MANUFACTURER} BRAND=${Build.BRAND} MODEL=${Build.MODEL}")
            appendLine("  DEVICE=${Build.DEVICE} PRODUCT=${Build.PRODUCT} HARDWARE=${Build.HARDWARE}")
            appendLine("  DISPLAY=${Build.DISPLAY} ID=${Build.ID}")
            appendLine("  SDK_INT=${Build.VERSION.SDK_INT} RELEASE=${Build.VERSION.RELEASE} INCREMENTAL=${Build.VERSION.INCREMENTAL}")
            appendLine("  SELINUX=${DeviceFacts.selinux()} ABI=${DeviceFacts.abi()}")

            appendLine("rom adapter:")
            appendLine("  id=${rom.id} label=${rom.label}")
            appendLine("  systemUiPackage=${rom.systemUiPackage}")
            appendLine("  containerIds=${rom.containerIds}")
            appendLine("  batteryId=${rom.batteryId} clockId=${rom.clockId}")
            appendLine("  notes=${rom.notes}")
            appendLine("  shadeMeasured=${rom.shadeMeasured} coverHook=${rom.coverHook} " +
                    "panelExpansionKind=${rom.panelExpansionKind} panelHookMeasured=${rom.panelHookMeasured} " +
                    "panelOwnerAvailable=${rom.panelOwnerAvailable}")
            appendLine("  source=${if (fromAsset) "asset-profile" else "code-default"}")

            appendLine("systemui:")
            appendLine("  uid=${Process.myUid()} pid=${Process.myPid()}")
            appendPackageInfo(context, this, "com.android.systemui")

            appendLine("window:")
            appendWindowFacts(context, this, root)

            // A dump taken while the bar window is detached (report: "status bar vanishes after unlock",
            // every probe "view not in this window") otherwise looks like a functioning ROM with a missing
            // strip. Say so loudly, and lean on the shade window dumped below for whatever evidence it has.
            val barAttached = root != null && root.isAttachedToWindow &&
                root.width > 0 && root.height > 0
            if (!barAttached) {
                appendLine()
                appendLine("!!! CAPTURE INCOMPLETE: the status bar window was not attached or measured")
                appendLine("!!! when this dump was taken. The container probes and the status-bar view tree")
                appendLine("!!! below are empty for that reason. Re-take the report with the bar on screen.")
                appendLine()
            }

            appendLine("container probes (id -> view):")
            for (name in CANDIDATE_IDS) {
                val id = RomResources.id(context, rom, name)
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

            // The role-based locator, run here so an unmeasured ROM's report already contains the strip
            // it resolved (and its id/class), which is what a new RomProfile is written from. Read-only.
            appendLine("role locator (unmeasured-ROM fallback):")
            if (root == null) {
                appendLine("  (no status bar window attached yet)")
            } else {
                val role = ContainerFinder(context, rom, LogOnce()).roleScan(root)
                if (role == null) {
                    appendLine("  no role-resolved strip")
                } else {
                    appendLine("  strip=${role.javaClass.name} id=${idName(role)} " +
                            "${role.width}x${role.height} vis=${role.visibility}")
                    appendLine("  parent=${role.parent?.javaClass?.name ?: "none"}")
                }
            }

            appendLine("view tree (status bar window):")
            if (root == null) {
                appendLine("  (no status bar window attached yet)")
            } else {
                dumpTree(root, this, 0)
            }

            // Other bars the module draws into - the shade/keyguard window and the shade header. They are
            // separate windows, so the status-bar walk never reaches them; the shade element's position is
            // only visible here (Issue #1).
            if (extraSnapshot.isNotBlank()) {
                append(extraSnapshot)
            }
            for ((name, extra) in extraRoots) {
                appendLine("view tree ($name):")
                if (extra == null) {
                    appendLine("  (not captured yet)")
                } else {
                    dumpTree(extra, this, 0)
                }
            }

            appendLine("readers:")
            appendReaders(context, this, settings)

            appendLine("module:")
            appendLine("  stage=$stage attempts=${DuoGuard(context).attempts()}")
            // Where the stage came from, and whether the app's provider actually answered. Without these a
            // "stage=0" report cannot be told apart from "the provider was unreachable" - the ColorOS/realme
            // failure mode - so the next fix would be a guess.
            val override = DuoGuard(context).override()
            appendLine(
                "  stageSource=" + when {
                    override != null -> "adb-override ($override)"
                    DuoSettingsClient.providerUnreachable -> "default (provider unreachable)"
                    else -> "app-settings"
                }
            )
            appendLine("  provider=${if (DuoSettingsClient.providerUnreachable) "unreachable" else "ok"}")
            appendLine("  renderer=${element?.rendererName ?: "none"} ready=${element?.isReady ?: false}")
            settings?.let {
                appendLine("  settings: enabled=${it.enabled} useRive=${it.useRive} showPercent=${it.showPercent}")
                appendLine("    size=${it.sizePercent}% offset=${it.offsetX}dp live=${it.liveApply} rev=${it.revision}")
                appendLine("    split=${it.splitIndicators} lock=${it.lockRing} iconsOffset=${it.indicatorsOffsetX}dp edge=${it.edgePadding}%")
                appendLine("    surfaces: qs=${it.showInQs} shade=${it.showInShade}")
                appendLine("    animations=${it.animationsEnabled} arrival=${it.arrivalEnabled} " +
                        "departure=${it.departureEnabled} charging=${it.chargingEnabled}")
                appendLine("    clockFont=${it.systemClockFont} revealMs=${it.revealMs} " +
                        "showAirplane=${it.showAirplane} dndMode=${it.dndMode} wifiDots=${it.wifiDots}")
            }
        }
    } catch (t: Throwable) {
        "Duo Status Bar diagnostic dump failed: ${t.javaClass.simpleName}: ${t.message}"
    }

    /** Logs the dump through [L] (both sinks) once, so a log capture has it too. */
    fun log(dump: String) {
        for (line in dump.lines()) L.diag(line)
    }

    /**
     * Renders one named view tree to text. Used to snapshot a window (the shade header) at the moment
     * it is laid out, because by the time the app asks for a report that window has been re-laid out.
     */
    fun treeText(name: String, view: View?): String = buildString {
        appendLine("view tree ($name):")
        if (view == null) {
            appendLine("  (not captured)")
        } else {
            dumpTree(view, this, 0)
        }
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

    private fun appendReaders(context: Context, sb: StringBuilder, settings: ModuleSettings?) {
        try {
            val airplane = SystemReaders.isAirplaneOn(context)
            val wifi = WifiReader.sample(context, 0, false, false, false)
            sb.appendLine("  wifiEnabled=${wifi.on} wifiActive=${wifi.active} " +
                    "wifiValidated=${wifi.validated} wifiLevel=${wifi.dots}")
            val cells = CellReader.read(context, airplane)
            val choice = settings?.simChoice ?: "auto"
            val picked = SignalMapping.selectCell(
                choice,
                cells.samples,
                cells.defaultDots,
                cells.defaultSub,
                cells.defaultSrc
            )
            sb.appendLine("  cellLevel=${picked.dots} " +
                    "generation=${SystemReaders.networkGeneration(context, airplane, "?")} " +
                    "mobileData=${SystemReaders.isMobileDataEnabled(context)} " +
                    "cellLevels=${cells.samples.map { it.dots }}")
            // Raw evidence: the old labels above stay for the frozen dump contract; these name the
            // values those labels cannot (the raw index, the count, the chosen slot/sub and source).
            sb.appendLine("  cellRaw=${cells.defaultRaw} cellCount=${cells.count} " +
                    "cellMaxIndex=${cells.defaultMaxIndex} cellSlots=${cells.slots} " +
                    "cellSubs=${cells.subs} cellPickedSub=${picked.subscriptionId} " +
                    "cellPickedSlot=${picked.slot} cellSource=${picked.src}")
            sb.appendLine("  wifiRssi=${wifi.rssi} wifiCalc5=${wifi.calc5} " +
                    "wifiStockBars=${wifi.stockBars} wifiDots=${wifi.dots} wifiSource=${wifi.source}")
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
