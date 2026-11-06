package io.github.kvmy666.duostatusbar.hook

import android.content.Intent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class DownloadGradient(
    val colors: List<Int>,
    val angle: Int,
) {
    init {
        require(colors.size in 2..5)
    }

    val normalizedAngle: Int = ((angle % 360) + 360) % 360

    /** Five fixed Rive stops; unused stops sit at 1 with the final colour. */
    fun riveColors(): List<Int> = colors + List(5 - colors.size) { colors.last() }

    fun rivePositions(): List<Float> {
        val active = colors.indices.map { it.toFloat() / colors.lastIndex }
        return active + List(5 - active.size) { 1f }
    }

    fun points(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val radians = normalizedAngle * PI.toFloat() / 180f
        val dx = cos(radians)
        val dy = sin(radians)
        val halfExtent =
            (right - left) / 2f * abs(dx) + (bottom - top) / 2f * abs(dy)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        return floatArrayOf(
            centerX - dx * halfExtent,
            centerY - dy * halfExtent,
            centerX + dx * halfExtent,
            centerY + dy * halfExtent,
        )
    }
}

/**
 * The download the punch-hole module is currently showing, as a broadcast from inside System UI.
 *
 * Both modules run in that process and cannot see each other's classes, so this is the hand-off.
 * The action strings are the contract; the same literals live in
 * `eu.hxreborn.phdp.xposed.hook.DuoDownloadPublisher`. A broadcast with [active] false is the idle
 * ring — no download ink.
 */
internal object DownloadProgress {

    const val ACTION = "io.github.kvmy666.duostatusbar.action.DOWNLOAD"
    const val ACTION_QUERY = "io.github.kvmy666.duostatusbar.action.DOWNLOAD_QUERY"

    const val EXTRA_ACTIVE = "active"
    const val EXTRA_PROGRESS = "progress"
    const val EXTRA_CLOCKWISE = "clockwise"
    const val EXTRA_COLOR = "color"
    const val EXTRA_OPACITY = "opacity"
    const val EXTRA_SHOW_LABEL = "showLabel"
    const val EXTRA_GRADIENT_ENABLED = "gradientEnabled"
    const val EXTRA_GRADIENT_COLORS = "gradientColors"
    const val EXTRA_GRADIENT_ANGLE = "gradientAngle"

    const val MIN_GRADIENT_COLORS = 2
    const val MAX_GRADIENT_COLORS = 5

    data class State(
        val active: Boolean = false,
        val progress: Int = 0,
        val clockwise: Boolean = true,
        val color: Int = DownloadWash.COLOR,
        val opacity: Float = 1f,
        val gradient: DownloadGradient? = null,
        /** When true, [progress] replaces the battery percentage in its seat. */
        val showLabel: Boolean = false,
    ) {
        /**
         * Progress that replaces the battery percentage while this download owns the ring. An
         * active download beats charging: the user sees the download number and can tap the ring
         * to switch back to the charging/battery view.
         */
        fun displayedPercent(): Int? = progress.takeIf { active && showLabel }

        /** The wash for the ring's current gap. Inactive is [DownloadWash.IDLE]. */
        fun toWash(charging: Boolean, showPercent: Boolean): DownloadWash = DownloadWash.of(
            progress = progress,
            clockwise = clockwise,
            charging = charging,
            showPercent = showPercent,
            active = active,
            color = color,
            opacity = opacity,
            gradient = gradient,
        )
    }

    /**
     * User-visible choice for one active download. A new download starts in download mode; each ring
     * tap alternates between that snapshot and the ordinary battery ring. Progress broadcasts do not
     * reset the choice, because Punch Hole publishes many of them during one download.
     */
    class DisplaySelection {
        private var active = false
        private var showDownload = true

        fun update(isActive: Boolean) {
            if (isActive != active) showDownload = true
            active = isActive
        }

        fun toggle(): Boolean {
            if (!active) return false
            showDownload = !showDownload
            return true
        }

        fun apply(state: State): State =
            if (state.active && !showDownload) state.copy(active = false, showLabel = false) else state

        val showingDownload: Boolean get() = active && showDownload
    }

    @Volatile
    var current: State = State()

    fun from(intent: Intent): State = State(
        active = intent.getBooleanExtra(EXTRA_ACTIVE, false),
        progress = intent.getIntExtra(EXTRA_PROGRESS, 0).coerceIn(0, 100),
        clockwise = intent.getBooleanExtra(EXTRA_CLOCKWISE, true),
        color = intent.getIntExtra(EXTRA_COLOR, DownloadWash.COLOR),
        opacity = intent.getFloatExtra(EXTRA_OPACITY, 1f).coerceIn(0f, 1f),
        gradient = gradientFrom(intent),
        showLabel = intent.getBooleanExtra(EXTRA_SHOW_LABEL, false),
    )

    private fun gradientFrom(intent: Intent): DownloadGradient? {
        if (!intent.getBooleanExtra(EXTRA_GRADIENT_ENABLED, false)) return null
        val colors =
            intent.getIntArrayExtra(EXTRA_GRADIENT_COLORS)
                ?.take(MAX_GRADIENT_COLORS)
                .orEmpty()
        if (colors.size < MIN_GRADIENT_COLORS) return null
        return DownloadGradient(colors, intent.getIntExtra(EXTRA_GRADIENT_ANGLE, 0))
    }

    fun trace(state: State): String =
        "active=${state.active} progress=${state.progress} clockwise=${state.clockwise} " +
            "color=0x${Integer.toHexString(state.color)} opacity=${state.opacity} " +
            "gradient=${state.gradient?.colors?.size ?: 0}@${state.gradient?.normalizedAngle ?: 0} " +
            "showLabel=${state.showLabel}"
}
