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

    fun resolve(context: Context): RomAdapter = resolve(
        Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty(), Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty(), Build.VERSION.SDK_INT, RomMarkerSource.read(context),
        RomProfiles.load(context)
    )

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
