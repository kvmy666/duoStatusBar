package io.github.kvmy666.duostatusbar.hook

import android.view.View

/**
 * The measured strip ids for the shade and Quick Settings surfaces, in attach priority order.
 *
 * These are the ids Duo already resolves for its own extra bars (`OnePlusShadeHooks`,
 * `attachExtraBars`, `PanelHandoff`): the notification shade header carries
 * `shade_header_system_icons`, the expanded QS header lives under `quick_qs_status_icons` (inside
 * `qs_status_bar_container_layout`), and `system_icons` is the shared fallback. No id is invented here.
 */
internal object IosSurfaceRouting {

    /** Notification shade header: the measured header strip first, then the shared fallbacks. */
    val HEADER_IDS = listOf("shade_header_system_icons", "system_icons", "quick_qs_status_icons")

    /** QS/shade window: the expanded QS header first, then the header strip and the shared strip. */
    val WINDOW_IDS = listOf("quick_qs_status_icons", "shade_header_system_icons", "system_icons")

    /** The first id in [ids] that [host] can attach to under [root], or null when none can. */
    fun firstAttachable(host: IosReplaceHost, root: View, ids: List<String>): String? =
        firstAttachable(ids) { host.canAttachSurface(root, it) }

    /** Pure ordering rule, separated so it is testable without a host or a SystemUI tree. */
    internal fun firstAttachable(ids: List<String>, canAttach: (String) -> Boolean): String? =
        ids.firstOrNull(canAttach)
}
