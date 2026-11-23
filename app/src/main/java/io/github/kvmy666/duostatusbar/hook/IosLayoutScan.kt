package io.github.kvmy666.duostatusbar.hook

/**
 * R6 item 5: throttle the per-layout inventory scan.
 *
 * [IosReplaceHost.onLayoutChanged] walks the whole stock strip to find deferred icons whose content
 * area changed. On a shade pull that runs for every layout frame. The scan is only useful while an
 * icon is deferred (a real retry is pending) or occasionally as a cheap safety net, so it runs when
 * [deferredCount] is non-zero or every [SCAN_EVERY_LAYOUTS] layouts. The per-record reposition loop
 * (overlay bounds and the native label shift) still runs on every layout.
 */
internal object IosLayoutScan {

    const val SCAN_EVERY_LAYOUTS = 8

    fun shouldScan(deferredCount: Int, layoutsSinceScan: Int): Boolean =
        deferredCount > 0 || layoutsSinceScan >= SCAN_EVERY_LAYOUTS
}
