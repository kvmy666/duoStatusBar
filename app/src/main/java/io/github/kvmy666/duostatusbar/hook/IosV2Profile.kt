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
 * R4: the class family that owns the battery icon on a v2 family.
 *
 * The resource engine covers both families, but the battery is a separate owner: OxygenOS uses the
 * measured Oplus `StatBatteryMeterView`/`BatteryViewBinder`, Samsung uses the AOSP-shaped
 * `BatteryMeterView`. A hook from the other owner must never count, or Samsung would appear
 * installed after only the (absent) Oplus probe and the route would pick a battery that never binds.
 */
internal enum class IosBatteryOwner { OPLUS, AOSP }

/**
 * The families the R7 v2 engine covers.
 *
 * Family ids are exactly the ones [io.github.kvmy666.duostatusbar.hook.rom.RomAdapter] uses.
 * OxygenOS is the measured reference (resource map + Oplus battery owner). Samsung has the
 * resource map and the AOSP battery owner, but its runtime is unverified. ColorOS/realme is a
 * non-measured Oplus family: it reuses the measured Oplus resource names and battery owner, and the
 * route is only deliverable when the Oplus battery constructor hook actually installs. Unresolved
 * resource names/binders there stay stock with the existing per-piece miss/coverage logs. Every other
 * family keeps the stock bar, and its resource table is dormant (R13).
 */
internal object IosV2Profile {

    /**
     * The resource engine owns Wi-Fi/cell/type replacement only on these families.
     *
     * Every other family is DORMANT (R13) measured data, not unsupported hardware: its resource table
     * is kept so a later route flip needs no new evidence, but no live route activates it.
     */
    fun engineFamily(id: String): Boolean =
        id == "oxygenos" || id == "samsung" || id == "coloros"

    /** The battery hook is needed whenever the engine is (one owner per icon). */
    fun batteryFamily(id: String): Boolean = engineFamily(id)

    /**
     * R4: the single battery owner per family, or null when iOS battery mode is not delivered there.
     * OxygenOS and ColorOS/realme -> the measured Oplus view/binder; Samsung -> the AOSP
     * `BatteryMeterView`.
     */
    fun batteryOwner(id: String): IosBatteryOwner? = when (id) {
        "oxygenos", "coloros" -> IosBatteryOwner.OPLUS
        "samsung" -> IosBatteryOwner.AOSP
        else -> null
    }

    /**
     * R4: true only when the family's **own** battery owner installed. A constructor hook from the
     * other family never satisfies this family (the R4 rule: Samsung with only the Oplus hook is off).
     */
    fun batteryOwnerInstalled(
        id: String,
        oplusHooked: Boolean,
        aospHooked: Boolean
    ): Boolean = when (batteryOwner(id)) {
        IosBatteryOwner.OPLUS -> oplusHooked
        IosBatteryOwner.AOSP -> aospHooked
        null -> false
    }

    /**
     * R4/R8: the two-SIM cluster is a measured Oplus binder. ColorOS/realme shares the same Oplus
     * binder classes as OxygenOS; every hook is guarded and logs a per-piece miss when a class is
     * absent, so the stock bar is kept.
     */
    fun stackedFamily(id: String): Boolean = id == "oxygenos" || id == "coloros"

    /**
     * R4/R8: the hide-extras leaves are measured Oplus binders. ColorOS/realme shares the same Oplus
     * binder classes as OxygenOS; every hook is guarded and logs a per-piece miss when a class is
     * absent, so the stock bar is kept.
     */
    fun extrasFamily(id: String): Boolean = id == "oxygenos" || id == "coloros"

    /**
     * R4: the route-unavailable battery line.
     *
     * OxygenOS keeps the 2.1.1 string byte-for-byte (`v2 missing class=IosBatteryV2 - stock bar
     * kept`); every other family names its own missing owner so Samsung reports `aosp`, not the
     * generic any-class wording.
     */
    fun unavailableBatteryLine(id: String): String =
        if (id == "oxygenos") {
            "v2 missing class=IosBatteryV2 - stock bar kept"
        } else {
            val owner = batteryOwner(id)?.name?.lowercase() ?: "none"
            "v2 missing battery owner=$owner - stock bar kept"
        }

    /** Data-type placement (OxygenOS `mobile_type` child) is measured only here. */
    fun typePlacementFamily(id: String): Boolean = id == "oxygenos"

    /**
     * The order path is enabled only on OxygenOS, where the swap is measured. Samsung exposes the same
     * `StatusBarIconList`/`StatusIconContainer` class names but the permutation is UNVERIFIED there;
     * ColorOS is UNVERIFIED too, and enabling it could desynchronise slot/view indices. Every other
     * family keeps stock order.
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
