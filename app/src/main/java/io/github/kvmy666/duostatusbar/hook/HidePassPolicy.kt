package io.github.kvmy666.duostatusbar.hook

/**
 * PART C: the screen-off/AOD rules for the stock-icon hide pass.
 *
 * While the element views are intentionally GONE (screen off/AOD), the bar still fires layout passes.
 * Those passes must not run a hide pass, must not treat a 0x0 box as a suppress/restore loop and must
 * not restore the stock icons: off-screen hiding is intentional, not a thrash event. Showing the
 * views again is the one real change that clears the backoff and re-hides the stock icons.
 *
 * Pure: no Android imports, safe on a layout pass and unit-testable on the JVM.
 */
internal object HidePassPolicy {

    fun allowsHidePass(elementsHidden: Boolean, thrashTripped: Boolean): Boolean =
        !elementsHidden && !thrashTripped

    fun allowsStockRestore(elementsHidden: Boolean): Boolean = !elementsHidden

    fun resetOnShow(wasHidden: Boolean, nowVisible: Boolean): Boolean = wasHidden && nowVisible
}
