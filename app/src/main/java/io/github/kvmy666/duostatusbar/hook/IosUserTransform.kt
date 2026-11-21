package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * Issue 7: one iOS glyph's user offset/scale. [dxPx] is a horizontal draw offset inside the glyph's
 * own bounds (the stock view is never resized, C2); [scale] multiplies today's drawn size.
 * Identity (0f, 1f) is exactly the pre-setting pixels.
 * R2B declares the same type in IosGeometry.kt; keep exactly one definition when merging.
 */
internal data class IosUserTransform(
    val dxPx: Float = 0f,
    val scale: Float = 1f
)

/**
 * Converts the stored percent values into a drawable transform. [drawnWidthPx] is the glyph's width
 * at 100 % scale; the offset is a percent of it, so 0 / 100 is identity for any width. Values are
 * clamped here as well as in DuoPrefs so a stale provider row cannot draw outside today's bounds.
 */
internal fun iosUserTransform(offsetPercent: Int, scalePercent: Int, drawnWidthPx: Float): IosUserTransform {
    val offset = offsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET)
    val scale = scalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE)
    return IosUserTransform(
        dxPx = drawnWidthPx.coerceAtLeast(0f) * offset / 100f,
        scale = scale / 100f
    )
}
