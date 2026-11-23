package io.github.kvmy666.duostatusbar.hook

/**
 * How iOS mode is delivered on the current ROM.
 *
 * [V2] is the system-native path (resource engine + battery hook), [HOST] is the pre-R7
 * attach/replace host, and [NONE] is not iOS mode at all. The route is pure so the matrix is
 * unit-tested instead of being discovered on a device.
 */
internal enum class IosRoute { NONE, V2, HOST }

/**
 * The families the R7 v2 engine covers.
 *
 * Family ids are exactly the ones [io.github.kvmy666.duostatusbar.hook.rom.RomAdapter] uses.
 * OxygenOS and Samsung are fully covered (resource map + battery); every other family keeps the
 * existing host until its own evidence is complete.
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
 * The single route decision for iOS mode (P4 consumes it; P1 pins the matrix in tests).
 *
 * V2 requires iOS mode, a covered family, and **both** process-wide hooks installed. Anything else
 * that is iOS mode is the old host; iOS off is [IosRoute.NONE].
 */
internal fun iosRoute(
    iosRuntime: Boolean,
    familyId: String,
    engineInstalled: Boolean,
    batteryInstalled: Boolean
): IosRoute = when {
    !iosRuntime -> IosRoute.NONE
    IosV2Profile.engineFamily(familyId) && engineInstalled && batteryInstalled -> IosRoute.V2
    else -> IosRoute.HOST
}

/**
 * R7 P5: the route after the v2 owners are stopped without leaving iOS mode.
 *
 * Stopping the owners is not the same event as a teardown: the HOST fall-through releases the
 * resource map/battery/placement and then keeps serving iOS through the pre-R7 host. Only an explicit
 * [IosRoute.NONE] write (leaving iOS) may clear the route, so this is the identity.
 */
internal fun routeAfterStoppingV2Owners(route: IosRoute): IosRoute = route

/**
 * R7 P5: the boot path's route when the synchronous v2 activation did not take.
 *
 * [routeAfterActivationFailure] is what the failed activation left behind: [IosRoute.NONE] after a
 * thrown activation (`deactivateIosV2`), or [IosRoute.V2] after the engine refused to build a map.
 * The boot HOST block that follows starts the pre-R7 owner in both cases, so the route - and the
 * diagnostics line - is always HOST; reporting the leftover NONE was the bug this pins.
 */
internal fun bootFallbackRoute(routeAfterActivationFailure: IosRoute): IosRoute = IosRoute.HOST

/**
 * Zero double-ownership invariant (R7 P4): while [route] is [IosRoute.V2] no pre-R7 host may exist.
 *
 * The V2 path never calls `ensureIosMain`/`ensureIosExtra`, so `iosMainHost` stays null and
 * `iosExtraHosts` stays empty; a non-zero count here is a routing bug that would let two owners
 * fight for the same icon. Pure, so the P4 tests pin it without a SystemUI process.
 */
internal fun iosV2HasNoHosts(
    route: IosRoute,
    iosMainHostPresent: Boolean,
    extraHostCount: Int
): Boolean = route != IosRoute.V2 || (!iosMainHostPresent && extraHostCount == 0)
