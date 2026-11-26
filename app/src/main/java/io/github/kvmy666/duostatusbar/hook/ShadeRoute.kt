package io.github.kvmy666.duostatusbar.hook

import android.view.View

/**
 * R10: decides whether a present shade header is usable.
 *
 * vivo's `ShadeHeaderController` keeps `combined_qs_header` in the tree while it is GONE/INVISIBLE,
 * so a non-null header used to block the live QS row. When the header is attached, VISIBLE and shown,
 * the header route is kept (OxygenOS early-attach behaviour is unchanged); otherwise the attach falls
 * back to the shade window. Pure so the decision is unit-tested instead of debugged on a phone.
 */
internal object ShadeRoute {
    fun shouldTryWindow(attached: Boolean, visibility: Int, shown: Boolean): Boolean =
        !attached || visibility != View.VISIBLE || !shown
}
