package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * What a live settings change needs from the host. [DESIGN], [ENGINE] and [SIZE] all mean the same
 * mechanism: tear the live element down and attach a fresh one in-process (never a System UI restart).
 */
internal enum class LiveRebuild { NONE, DESIGN, ENGINE, SIZE }

/**
 * Decides, without touching Android, whether a settings change can be applied to the live element.
 *
 * A design change always wins: the artwork is loaded once per element, so a fresh view is the only way
 * to draw the other drawing. An engine change needs the same fresh view. A size change is rebuilt live
 * only when the user asked for live apply and the change did not come from a rotation: [applySavedSize]
 * means the per-orientation size is drawn by the existing in-place geometry path, so rebuilding there
 * would tear down a working element on every rotation. No element means nothing to rebuild.
 */
internal object LiveApply {

    fun plan(
        previousDesign: String?,
        freshDesign: String?,
        previousEngine: String?,
        freshEngine: String?,
        previousSize: Int,
        freshSize: Int,
        liveApply: Boolean,
        hasElement: Boolean,
        applySavedSize: Boolean
    ): LiveRebuild {
        if (!hasElement) return LiveRebuild.NONE
        if (DuoPrefs.normalizeDesignStyle(previousDesign) !=
            DuoPrefs.normalizeDesignStyle(freshDesign)
        ) {
            return LiveRebuild.DESIGN
        }
        if (DuoPrefs.normalizeEngine(previousEngine) != DuoPrefs.normalizeEngine(freshEngine)) {
            return LiveRebuild.ENGINE
        }
        if (previousSize != freshSize && liveApply && !applySavedSize) return LiveRebuild.SIZE
        return LiveRebuild.NONE
    }
}
