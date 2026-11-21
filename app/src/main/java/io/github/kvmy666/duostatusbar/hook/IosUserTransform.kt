package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * Per-user placement offset and scale for an in-place iOS glyph (R2A/R2C merged definition).
 *
 * [dxPx] is a horizontal draw offset inside the glyph's own bounds in device pixels (the stock view
 * is never resized, C2); [scale] multiplies today's drawn size around the glyph centre. Identity
 * (0f, 1f) is exactly the pre-setting pixels.
 */
internal data class IosUserTransform(
    val dxPx: Float = 0f,
    val scale: Float = 1f
) {
    companion object {
        val IDENTITY = IosUserTransform()
    }
}

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
