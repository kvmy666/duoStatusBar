package io.github.kvmy666.duostatusbar.hook

/**
 * Eases a signal dot into the white Do Not Disturb crescent.
 *
 * The settled snapshot is instant: a weak bar is the gray circle, a strong bar is the white moon,
 * and a fully hidden row (airplane mode, Wi-Fi off) is the one moon in the middle of that row.
 * The status-bar views call [push] on each snapshot and, while [running], once a frame, so the
 * circle fades as the crescent grows into its place. The first snapshot snaps, so attaching the
 * element does not play a fill.
 *
 * Only those opacities move. The rest of the snapshot (the ring, the middle slot, the percentage)
 * is taken from the newest target, so a battery change during a fill does not lag or restart it.
 */
internal class DotMoonMotion {
    private var from: DuoVisual? = null
    private var goal: DuoVisual? = null
    private var startedAt = 0L

    var running: Boolean = false
        private set

    /** The visual to draw at [nowMs]. Starts or continues a fill when the dot fields changed. */
    fun push(next: DuoVisual, nowMs: Long): DuoVisual {
        val origin = from
        if (origin == null) {
            from = next
            goal = next
            running = false
            return next
        }
        val current = goal
        if (current != next) {
            if (running && current != null && sameDots(current, next)) {
                goal = next
            } else {
                val shown = if (running) displayed(nowMs) else origin
                from = shown
                goal = next
                startedAt = nowMs
                running = !sameDots(shown, next)
                if (!running) {
                    from = next
                    return next
                }
            }
        }
        if (!running) return next
        val t = ((nowMs - startedAt).toFloat() / DURATION_MS).coerceIn(0f, 1f)
        if (t >= 1f) {
            running = false
            from = next
            return next
        }
        return mix(from!!, next, ease(t))
    }

    private fun displayed(nowMs: Long): DuoVisual {
        val origin = from ?: return goal!!
        val target = goal ?: return origin
        val t = ((nowMs - startedAt).toFloat() / DURATION_MS).coerceIn(0f, 1f)
        return mix(origin, target, ease(t))
    }

    companion object {
        const val DURATION_MS = 280L

        /** The crescent's path is 36.92 units across; [RingGeometry.DOT_DIAMETER] fits one inside. */
        const val DOT_SCALE = RingGeometry.DOT_DIAMETER / 36.92f

        /** Centre of the one DND moon shown when both dot rows are hidden, from the ring centre. */
        const val CENTER_X = RingGeometry.CENTER_MOON_X
        const val CENTER_Y = RingGeometry.CENTER_MOON_Y

        fun sameDots(a: DuoVisual, b: DuoVisual): Boolean =
            a.cell1Opacity == b.cell1Opacity &&
                a.cell2Opacity == b.cell2Opacity &&
                a.cell3Opacity == b.cell3Opacity &&
                a.cell4Opacity == b.cell4Opacity &&
                a.moon1Opacity == b.moon1Opacity &&
                a.moon2Opacity == b.moon2Opacity &&
                a.moon3Opacity == b.moon3Opacity &&
                a.moon4Opacity == b.moon4Opacity &&
                a.centerMoonOpacity == b.centerMoonOpacity

        /** [t] is already eased. Dot and moon opacities blend; everything else comes from [to]. */
        fun mix(from: DuoVisual, to: DuoVisual, t: Float): DuoVisual {
            fun at(a: Float, b: Float) = a + (b - a) * t
            return to.copy(
                cell1Opacity = at(from.cell1Opacity, to.cell1Opacity),
                cell2Opacity = at(from.cell2Opacity, to.cell2Opacity),
                cell3Opacity = at(from.cell3Opacity, to.cell3Opacity),
                cell4Opacity = at(from.cell4Opacity, to.cell4Opacity),
                moon1Opacity = at(from.moon1Opacity, to.moon1Opacity),
                moon2Opacity = at(from.moon2Opacity, to.moon2Opacity),
                moon3Opacity = at(from.moon3Opacity, to.moon3Opacity),
                moon4Opacity = at(from.moon4Opacity, to.moon4Opacity),
                centerMoonOpacity = at(from.centerMoonOpacity, to.centerMoonOpacity)
            )
        }

        fun ease(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
    }
}
