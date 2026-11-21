package io.github.kvmy666.duostatusbar.hook

import android.content.Context

/**
 * The single place that decides which renderer backs an iOS glyph.
 *
 * [DRAWING_READY] is the one capability switch the design wave flips once `ios.riv` has landed and
 * [IosRiveView] actually draws. While it is false the host must gate **all** stock-icon suppression on
 * it, so a renderer that cannot draw can never hide an icon that nothing replaces (fail silent, rule 5).
 */
internal object IosRenderers {

    /**
     * True now that `ios.riv` and the real [IosRiveView]/[IosCanvasView] exist. The switch was flipped
     * only after the rule-10 binary parity check (`tools/check-rive-shipped.sh`) and the reference
     * render/overlay evidence. Never turn it off in a build that ships the scene without also restoring
     * the stock-icon path; the host gates every suppression on it.
     */
    const val DRAWING_READY = true

    /** The primary renderer (Rive), with an internal Canvas fallback. */
    fun create(context: Context, kind: IosIconKind): IosElement = IosRiveView(context, kind)

    /** The fail-silent fallback renderer (Canvas), from the same [IosGeometry]. */
    fun canvas(context: Context, kind: IosIconKind): IosElement = IosCanvasView(context, kind)
}

/**
 * The crash breaker the host consults before every iOS Rive creation (C3).
 *
 * Kept as an interface so the host can be tested without touching the settings provider; the one
 * production implementation is [IosGuard], whose own keys stay separate from [DuoGuard] (M14).
 */
internal interface IosRiveBreaker {
    fun riveAllowed(): Boolean
    fun noteRiveAttempt()
    fun clearRiveAttempts()
}
