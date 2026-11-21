package io.github.kvmy666.duostatusbar.hook

/**
 * Per-icon user transform (R2C): horizontal offset in device pixels and a scale multiplier.
 *
 * The host only carries the value today; R2C owns the settings/UI that produce it. Identity is the
 * default and must draw exactly the old pixels (rule 6), which [IosGlyphRenderer] proves with the
 * existing parity test.
 */
internal data class IosUserTransform(
    val dxPx: Float = 0f,
    val scale: Float = 1f
) {
    companion object {
        val IDENTITY = IosUserTransform()
    }
}
