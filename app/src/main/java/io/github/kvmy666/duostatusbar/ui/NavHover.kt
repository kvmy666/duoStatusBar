package io.github.kvmy666.duostatusbar.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Pure hover-index logic for the four-tab bottom selector.
 *
 * `liveIndex = ((centerX / itemWidthPx) - 0.5f).roundToInt().coerceIn(0, lastIndex)`
 *
 * Kept pure (no Compose state) so it can be unit-tested without instrumentation.
 */
fun hoverIndex(centerX: Float, itemWidthPx: Float, count: Int): Int {
    if (itemWidthPx <= 0f || count <= 0) return 0
    val lastIndex = count - 1
    return ((centerX / itemWidthPx) - 0.5f).roundToInt().coerceIn(0, lastIndex)
}

/** Pure selected/hover color policy. idleColor is the caller's idle tab color so the policy
 *  has a single owner and cannot diverge from the UI (see DuoShell tab tint). */
fun navTabColor(isHovered: Boolean, isSelected: Boolean, isMoving: Boolean, idleColor: Color): Color = when {
    isHovered && isMoving -> DuoColors.NavAccent
    isSelected && !isMoving -> DuoColors.NavAccent
    else -> idleColor
}
