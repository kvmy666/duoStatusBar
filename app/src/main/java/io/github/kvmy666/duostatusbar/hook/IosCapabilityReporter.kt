package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.Process
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.IosCapability

/**
 * R6/D13/D14: writes the module's live iOS capability to `Settings.Global` so the app can show
 * IOS_ACTIVE / IOS_UNSUPPORTED instead of a false DISABLED.
 *
 * The wire format is owned by [IosCapability.format]/[IosCapability.parse]; this object only supplies
 * the live values ([FeatureSet.owner], the ROM family, the live [IosRoute] and [IosAvailability]) and
 * de-duplicates repeated applies so a settings drag does not churn the global setting.
 *
 * The value is derived state, never a setting: a normal app reads it without a permission and never
 * writes it. A write failure costs the report, not the bar, so it is swallowed and logged once.
 */
internal object IosCapabilityReporter {

    /** Last successfully written state, ignoring `at`, so an unchanged apply writes nothing. */
    @Volatile
    private var lastState: String? = null

    private val failureLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Builds the report from the live runtime facts. [supported] defaults to
     * [IosAvailability.effective], so a refused route reports false without a second code path.
     */
    internal fun capabilityFor(
        features: FeatureSet,
        family: String,
        route: IosRoute,
        revision: Long,
        supported: Boolean = IosAvailability.effective(),
        pid: Int = Process.myPid(),
        at: Long = System.currentTimeMillis()
    ): IosCapability = IosCapability(
        owner = features.owner.tag,
        family = family,
        supported = supported,
        route = route.name.lowercase(),
        revision = revision,
        pid = pid,
        at = at
    )

    /** The de-dupe identity: every reported field except the wall-clock `at`. */
    internal fun stateKey(capability: IosCapability): String = buildString {
        append(capability.owner).append('|')
        append(capability.family).append('|')
        append(capability.supported).append('|')
        append(capability.route).append('|')
        append(capability.revision).append('|')
        append(capability.pid)
    }

    /** The pure write decision: write only when the state (ignoring `at`) changed. */
    internal fun shouldWrite(previous: String?, capability: IosCapability): Boolean =
        previous != stateKey(capability)

    /**
     * Writes the live report, de-duplicated on [stateKey]. Success is silent (the OxygenOS log stays
     * frozen); a failure is logged once per process.
     */
    internal fun report(
        context: Context,
        features: FeatureSet,
        family: String,
        route: IosRoute,
        revision: Long
    ) {
        val capability = capabilityFor(features, family, route, revision)
        val key = stateKey(capability)
        if (!shouldWrite(lastState, capability)) return
        try {
            Settings.Global.putString(context.contentResolver, IosCapability.KEY, capability.format())
            lastState = key
        } catch (t: Throwable) {
            if (failureLogged.compareAndSet(false, true)) {
                L.w("ios capability report: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** Test seam: clear the de-dupe state and the one-shot failure gate. */
    internal fun resetForTest() {
        lastState = null
        failureLogged.set(false)
    }
}
