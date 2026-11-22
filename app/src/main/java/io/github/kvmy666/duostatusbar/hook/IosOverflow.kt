package io.github.kvmy666.duostatusbar.hook

import kotlin.math.roundToInt

/**
 * R4A item 5: the host-side overlay box for a scaled glyph.
 *
 * An overlay canvas used to be sized to the stock slot and scaled inside it, so a >100 % glyph was
 * clipped to the slot. The host now enlarges the canvas to the transformed glyph box and draws the
 * artboard at its identity size inside it; this pure function is that box.
 *
 * [baseLeft]/[baseTop]/[baseWidth]/[baseHeight] are the unscaled slot box, [dxPx] is the user's
 * horizontal offset and [scale] the user's scale. The result is centred on the base box (so a scale
 * grows the glyph both ways), shifted by [dxPx]. Size is at least 1 px so a malformed scale can never
 * produce a zero-sized layout.
 */
internal fun overlayBox(
    baseLeft: Int,
    baseTop: Int,
    baseWidth: Int,
    baseHeight: Int,
    dxPx: Float,
    scale: Float
): IntArray {
    val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
    val safeDx = if (dxPx.isFinite()) dxPx else 0f
    val width = (baseWidth * safeScale).roundToInt().coerceAtLeast(1)
    val height = (baseHeight * safeScale).roundToInt().coerceAtLeast(1)
    val left = baseLeft - (width - baseWidth) / 2 + safeDx.roundToInt()
    val top = baseTop - (height - baseHeight) / 2
    return intArrayOf(left, top, width, height)
}
