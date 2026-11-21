package io.github.kvmy666.duostatusbar.hook

import android.content.Context

/**
 * The single place that decides which renderer backs an iOS glyph.
 *
 * The iOS replacement never creates a Rive view inside SystemUI (IOS-R2 0c: the Rive/JNI path took
 * down the process), so [create] is the Canvas renderer and there is no native branch to reach. The
 * Canvas drawing comes from [IosGeometry] and is shared with [IosGlyphDrawable].
 *
 * [DRAWING_READY] stays the one capability switch the in-place hosts gate stock-icon suppression on,
 * so a build without a usable renderer can never hide an icon that nothing replaces (fail silent).
 */
internal object IosRenderers {

    /**
     * True while the Canvas path is shipped. The hosts gate every suppression on it; never turn it off
     * in a build that ships the glyphs without also restoring the stock-icon path.
     */
    const val DRAWING_READY = true

    /** The production renderer: always the platform-Canvas element. */
    fun create(context: Context, kind: IosIconKind): IosElement = IosCanvasView(context, kind)

    /** The fail-silent fallback renderer (Canvas), from the same [IosGeometry]. */
    fun canvas(context: Context, kind: IosIconKind): IosElement = IosCanvasView(context, kind)
}
