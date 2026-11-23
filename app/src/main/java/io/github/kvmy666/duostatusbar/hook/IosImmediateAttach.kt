package io.github.kvmy666.duostatusbar.hook

import android.view.View

/**
 * R6 item 4: decide whether a `StatusIconContainer.addView` belongs to the status-bar window.
 *
 * The same container class is used by the keyguard bar and the shade headers. Styling the main bar
 * from one of those would adopt the wrong window, so the container must walk up to the exact
 * status-bar root that [DuoHook] captured (`TYPE_STATUS_BAR`); the shade root is deliberately never an
 * answer. Pure and Android-free apart from the parent walk, so it is unit-testable without a device.
 */
internal object IosImmediateAttach {

    fun isMainBarContainer(container: View?, statusBarRoot: View?): Boolean {
        if (container == null || statusBarRoot == null) return false
        var current: View? = container
        while (current != null) {
            if (current === statusBarRoot) return true
            current = current.parent as? View
        }
        return false
    }
}
