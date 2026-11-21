package io.github.kvmy666.duostatusbar.hook

import app.rive.runtime.kotlin.core.ViewModelInstance
import io.github.kvmy666.duostatusbar.L

/**
 * The one place that knows how an [IosVisual] reaches the future `ios.riv`.
 *
 * This is the iOS sibling of [DuoBinder], kept separate on purpose: sharing Duo's table would break the
 * frozen 59-property parity and make a wrong name impossible to attribute. The artboard and state
 * machine are both named `Ios`, but this binder only names properties; owning the artboard/state machine
 * is the future `IosRiveView`'s job.
 *
 * Every write is individually guarded: one renamed or missing property logs a warning naming it and the
 * rest of the snapshot still lands, so a partially-wrong file degrades instead of going blank.
 * Writes go through [IosBinding], whose lock serializes them with the renderer's `advance` (see the long
 * note in [DuoBinder] for the `ConcurrentModificationException` this prevents).
 */
internal object IosBinder {

    /** Number of properties a complete snapshot writes — used to report partial failures. */
    const val IOS_PROPERTY_COUNT = 12

    /** Every property this binder writes, in snapshot order; unit-tested against [IOS_PROPERTY_COUNT]. */
    internal val PROPERTY_NAMES: List<String> = listOf(
        "part",
        "visible",
        "tint",
        "batteryLevel",
        "batteryState",
        "batteryFillColor",
        "boltOpacity",
        "wifiLevel",
        "cellMode",
        "cellRole",
        "cellBars",
        "cellDots"
    )

    /**
     * Binds a view model for writing. [lock] must be the Rive file's own lock so writes serialize with
     * the renderer's `advance`; when it is unavailable the view model itself is used, which still
     * serializes writes with each other.
     */
    fun bind(vm: ViewModelInstance, lock: Any?): IosBinding = IosBinding(vm, lock ?: vm)

    /** Writes the whole snapshot. Returns how many properties failed to bind (0 is perfect). */
    internal fun write(vm: ViewModelInstance, v: IosVisual): Int {
        var failures = 0
        val numbers = arrayOf(
            "part" to v.part.ordinal.toFloat(),
            "batteryLevel" to v.batteryLevel,
            "batteryState" to v.batteryState.ordinal.toFloat(),
            "boltOpacity" to v.boltOpacity,
            "wifiLevel" to v.wifiLevel,
            "cellMode" to v.cellMode.ordinal.toFloat(),
            "cellRole" to v.cellRole.ordinal.toFloat(),
            "cellBars" to v.cellBars,
            "cellDots" to v.cellDots
        )
        for ((name, value) in numbers) {
            if (!write(name) { vm.getNumberProperty(name).value = value }) failures++
        }

        val colors = arrayOf(
            "tint" to v.tint,
            "batteryFillColor" to v.batteryFillColor
        )
        for ((name, value) in colors) {
            if (!write(name) { vm.getColorProperty(name).value = value }) failures++
        }

        if (!write("visible") { vm.getBooleanProperty("visible").value = v.visible }) failures++
        return failures
    }

    private inline fun write(name: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (t: Throwable) {
        L.w("ios bind $name: ${t.javaClass.simpleName}: ${t.message}")
        false
    }
}

/**
 * A view model paired with the lock its writes must take (see [IosBinder]). Created by [IosBinder.bind];
 * both surfaces hold one and write only through it.
 */
internal class IosBinding internal constructor(
    private val vm: ViewModelInstance,
    private val lock: Any
) {
    /** Writes the whole snapshot under the Rive lock. Returns how many properties failed to bind. */
    fun apply(v: IosVisual): Int = synchronized(lock) { IosBinder.write(vm, v) }
}
