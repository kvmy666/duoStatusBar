package io.github.kvmy666.duostatusbar.hook

/**
 * R8: the status-icon arrival hooks.
 *
 * `XposedBridge.hookAllMethods` only walks the methods a class *declares* - it never searches a
 * superclass. The vendor status icon containers declare `onViewAdded`/`onViewRemoved` and do not
 * declare `addView` (REV1 R8: OnePlus `StatusIconContainer` at 434/1064, Xiaomi
 * `MiuiStatusIconContainer` at 549/3247; Samsung/Motorola/Nothing declare no `addView` at all), so
 * the old `addView` hook installed no callback while its class counter still logged a success.
 * `onViewAdded`/`onViewRemoved` are the real arrival hooks, and a container that declares neither
 * keeps the layout pass as its fallback.
 */
internal object IconContainerHooks {
    val CLASS_NAMES = listOf(
        "com.android.systemui.statusbar.phone.StatusIconContainer",
        "com.android.systemui.statusbar.views.MiuiStatusIconContainer"
    )
    val METHOD_NAMES = listOf("onViewAdded", "onViewRemoved")

    /** The [METHOD_NAMES] this class declares itself, in [METHOD_NAMES] order. */
    fun declaredTargets(cls: Class<*>): List<String> =
        METHOD_NAMES.filter { name -> cls.declaredMethods.any { it.name == name } }
}
