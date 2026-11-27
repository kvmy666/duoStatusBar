package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context
import android.content.res.Resources

/**
 * R17D: the runtime marker that proves a SystemUI belongs to the Oplus family (ColorOS / realme /
 * OPPO) even when its APK path and application class look exactly like AOSP.
 *
 * Evidence, read off measured reports:
 *  - realme RMX5011: `.pm/evidence/u1127/new-duo-report-20261127-175643/diagnostics.txt:91,92` show
 *    `battery -> ...StatBatteryMeterView` and `clock -> ...StatClock`; the same dump shows the Oplus
 *    resource id `oplus_qs_tile_icon_bg` at `:734` (also `:711`) and `oplus_background_mask` /
 *    `oplus_fake_clock_container`.
 *  - OnePlus PLK110: `.pm/evidence/u1127/duo-report-20261127-150754/diagnostics.txt:108,109` show the
 *    same battery/clock classes; its module log lines 31-32 show `OplusQSSimpleHeader` and
 *    `OplusSplitShade` hooks installed.
 *
 * A CPH2747 OxygenOS build carries the same Oplus classes, so this marker proves the **family**, never
 * a particular OxygenOS device. It is deliberately not used to claim or measure OxygenOS: that adapter
 * stays proven by its CPH2747 identity plus the SDK 36 window (see `RomDetection.oxygenOs`). A
 * marker-selected ColorOS adapter is therefore `family=oplus via marker ... (unverified)` and never
 * `measured`.
 *
 * This object never logs; it only reports what it resolved.
 */
internal object OplusMarker {

    /**
     * Class markers, in report order: the first one that resolves is the reported marker. The order is
     * pinned by `OplusMarkerTest`.
     */
    val CLASS_MARKERS = listOf(
        "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
        "com.oplus.systemui.statusbar.widget.StatClock",
        "com.oplus.systemui.separate.OplusQSSimpleHeader"
    )

    /** Oplus-only resource ids; none of these exists in an AOSP SystemUI. */
    val ID_MARKERS = listOf(
        "oplus_qs_tile_icon_bg",
        "oplus_background_mask",
        "oplus_fake_clock_container"
    )

    /**
     * Resolves the first present class marker through [loader] and returns it as `class:<fqcn>`, or ""
     * when [loader] is null or none of the markers resolves. Each probe is isolated: a loader that
     * throws for one marker still lets the next one be tried.
     */
    fun detectInClassLoader(loader: ClassLoader?): String {
        if (loader == null) return ""
        return detectClassMarkers { name ->
            try {
                Class.forName(name, false, loader)
                true
            } catch (_: Throwable) {
                // Not present in this loader; try the next marker.
                false
            }
        }
    }

    /**
     * Pure seam behind [detectInClassLoader]: returns `class:<name>` for the first marker in
     * [CLASS_MARKERS] order for which [exists] is true, else "". No logging.
     */
    internal fun detectClassMarkers(exists: (String) -> Boolean): String {
        for (name in CLASS_MARKERS) {
            if (exists(name)) return "class:$name"
        }
        return ""
    }

    /**
     * Detects the Oplus family from the host classloader first, then from the module's own loader, and
     * finally from the installed AOSP SystemUI resource table. First hit wins. Never throws.
     */
    fun detect(context: Context): String = try {
        val loaders = listOfNotNull(context.classLoader, OplusMarker::class.java.classLoader).distinct()
        val classMarker = loaders.firstNotNullOfOrNull { loader ->
            detectInClassLoader(loader).takeIf { it.isNotEmpty() }
        }
        classMarker ?: run {
            val resources = context.packageManager.getResourcesForApplication(RomDetection.AOSP)
            detectResourceIds(resources, RomDetection.AOSP)
        }
    } catch (_: Throwable) {
        // An unreadable context/package proves nothing; the caller keeps its brand/default rule.
        ""
    }

    /**
     * Returns `id:<name>` for the first Oplus resource id that resolves in [resources] for
     * [packageName], or "" when none does. Each probe is isolated so one broken lookup cannot hide the
     * rest.
     */
    fun detectResourceIds(resources: Resources, packageName: String): String {
        for (name in ID_MARKERS) {
            try {
                if (resources.getIdentifier(name, "id", packageName) != 0) return "id:$name"
            } catch (_: Throwable) {
                // This probe proved nothing; try the next id.
            }
        }
        return ""
    }
}
