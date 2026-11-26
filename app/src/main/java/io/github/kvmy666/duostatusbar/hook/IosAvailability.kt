package io.github.kvmy666.duostatusbar.hook

/**
 * Whether iOS mode can actually own the bar on this device right now (R6/D13/D14).
 *
 * The saved `iosMode` setting only says what the user asked for. On a family without the v2 engine or
 * battery owner, or after an activation that refused, selecting [BarOwner.IOS] would suppress Duo and
 * draw nothing. This process-wide seam lets the owner decision fall back to Duo instead of leaving the
 * bar ownerless.
 *
 * [supported] mirrors "the family and both process-wide hooks look deliverable"; it stays `true` until
 * the module has read the family, which keeps every caller's default behaviour unchanged. [routeFailed]
 * records a route/activation refusal on an otherwise deliverable device and is cleared on a successful
 * activation and whenever iOS is torn down or switched off.
 */
internal object IosAvailability {

    /** True while the family + engine + battery owner look deliverable. Defaults to today's behaviour. */
    @Volatile
    var supported: Boolean = true

    /** True once an activation/route attempt refused despite [supported]. */
    @Volatile
    var routeFailed: Boolean = false

    /** iOS may own the bar only when it is deliverable and no route attempt has already refused. */
    fun effective(): Boolean = supported && !routeFailed

    /** Test seam: every test starts from "supported, no failure" without touching global settings. */
    internal fun resetForTest() {
        supported = true
        routeFailed = false
    }
}
