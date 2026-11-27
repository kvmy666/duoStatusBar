package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context
import android.os.Build

/**
 * The single resolver for "which ROM family is this" (R15).
 *
 * Before this the hook, the icon host and the diagnostic dump each assembled the same call by hand, so a
 * change to the inputs (a profile list, a marker read) could reach one path and not the others. The hook
 * keeps its pre-context fallback field (see `DuoHook.rom`) because no context exists that early; every
 * context-bound caller goes through [resolve].
 */
internal object RomResolver {

    /**
     * R17D: the markers read once at install time from the host SystemUI classloader.
     *
     * That loader is the only place the Oplus SystemUI classes are visible before a context exists. It
     * is recorded here so every context resolver (hook start, icon host, Diag, iOS report) merges the
     * same record: a context read that cannot repeat the marker must not contradict the family the
     * one-shot iOS hooks were installed for.
     */
    @Volatile
    private var installMarkers: RomMarkers = RomMarkers.UNKNOWN

    /** R17D: records the install-time markers for every later [resolve] call. */
    fun noteInstallMarkers(markers: RomMarkers) {
        installMarkers = markers
    }

    fun resolve(context: Context): RomAdapter = resolve(
        Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty(), Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty(), Build.VERSION.SDK_INT,
        mergeMarkers(RomMarkerSource.read(context), installMarkers),
        RomProfiles.load(context)
    )

    /**
     * R17D: merges the context read with the install-time markers.
     *
     * The install marker only fills a context read that could not repeat it (blank [RomMarkers.oplusMarker]);
     * every other field, including a real context marker, is owned by the context read. The filled record
     * reports [RomMarkers.read] so the source/class it already carries can still prove non-MIUI, while a
     * marker alone (like the install-only UNKNOWN read) proves nothing about MIUI.
     */
    internal fun mergeMarkers(read: RomMarkers, install: RomMarkers): RomMarkers =
        if (read.oplusMarker.isBlank() && install.oplusMarker.isNotBlank()) {
            read.copy(oplusMarker = install.oplusMarker, read = true)
        } else {
            read
        }

    internal fun resolve(
        manufacturer: String,
        brand: String,
        product: String,
        display: String,
        api: Int,
        markers: RomMarkers,
        profiles: List<RomAdapter> = emptyList()
    ): RomAdapter =
        RomDetection.forThisRom(manufacturer, brand, product, display, profiles, api, markers)
}
