package io.github.kvmy666.duostatusbar.hook

/**
 * How iOS mode is delivered on the current ROM.
 *
 * [V2] is the only iOS route: the system-native resource engine + battery hook. There is no pre-R7
 * host fallback any more; [NONE] is not iOS mode at all. The route is pure so the matrix is
 * unit-tested instead of being discovered on a device.
 */
internal enum class IosRoute { NONE, V2 }

/**
 * The families the R7 v2 engine covers.
 *
 * Family ids are exactly the ones [io.github.kvmy666.duostatusbar.hook.rom.RomAdapter] uses.
 * OxygenOS and Samsung are fully covered (resource map + battery); every other family keeps the
 * stock bar because iOS mode is not delivered there.
 */
internal object IosV2Profile {

    /** Every family id the module knows, in the same spelling as `RomAdapter.id`. */
    val FAMILIES = listOf(
        "oxygenos",
        "samsung",
        "hyperos",
        "nothing",
        "motorola",
        "vivo",
        "aosp",
        "coloros",
        "honor"
    )

    /** The resource engine owns Wi-Fi/cell/type replacement only on these families. */
    fun engineFamily(id: String): Boolean = id == "oxygenos" || id == "samsung"

    /** The battery hook is needed whenever the engine is (one owner per icon). */
    fun batteryFamily(id: String): Boolean = engineFamily(id)

    /** Data-type placement (OxygenOS `mobile_type` child) is measured only here. */
    fun typePlacementFamily(id: String): Boolean = id == "oxygenos"

    /**
     * The `mobile -> wifi` slot permutation needs both a measured `StatusBarIconList` **and** a
     * measured `StatusIconContainer`; only OxygenOS has both. Permuting the slots without moving the
     * same child would desynchronise slot/view indices, so every other V2 family keeps stock order.
     */
    fun orderFamily(id: String): Boolean = id == "oxygenos"
}

/**
 * The single route decision for iOS mode.
 *
 * V2 requires iOS mode, a covered family, and **both** process-wide hooks installed. Anything else
 * that is iOS mode is unsupported, so the stock bar is kept and the route is [IosRoute.NONE];
 * iOS off is [IosRoute.NONE] too.
 */
internal fun iosRoute(
    iosRuntime: Boolean,
    familyId: String,
    engineInstalled: Boolean,
    batteryInstalled: Boolean
): IosRoute = when {
    !iosRuntime -> IosRoute.NONE
    IosV2Profile.engineFamily(familyId) && engineInstalled && batteryInstalled -> IosRoute.V2
    else -> IosRoute.NONE
}
