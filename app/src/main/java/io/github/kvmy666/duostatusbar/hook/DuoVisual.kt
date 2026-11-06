package io.github.kvmy666.duostatusbar.hook


/**
 * Everything the drawing needs, derived from system state.
 *
 * Pure data + pure maths on purpose: this is the half that can go wrong silently, so it is kept
 * free of Android types and unit-tested (`DuoMappingTest`) instead of being debugged on a phone.
 */
data class DuoVisual(
    val trimLeftEnd: Float,
    val trimRightEnd: Float,
    /** The two halves' track arc lengths (trim fractions); the right one is 0 when the gap closes. */
    val leftArc: Float,
    val rightArc: Float,
    val trackOpacity: Float,
    val percentText: String,
    val percentOpacity: Float,
    val percentFontSize: Float,
    /**
     * Top of the percentage text box, in design units relative to the ring centre. [RingGeometry.PERCENT_TOP_Y]
     * is the original seat; more negative sits higher, up to a full [RingGeometry.PERCENT_LIFT].
     */
    val percentY: Float,
    /** Battery line trim start (trim fraction) for the 2 SIM bottom arc; level-derived, clamped. */
    val batteryLineStart: Float = RingGeometry.BATTERY_LINE_END,
    /**
     * Y offset applied to the middle occupant (Wi-Fi / network / DND / event) relative to the ring
     * centre. 0 in normal mode; the upper seat when inverted.
     */
    val middleY: Float = 0f,
    /** Scale applied to the middle occupant. 1.0 in normal mode; 0.55 when inverted. */
    val middleScale: Float = 1f,
    /**
     * Y offset applied to the charging bolt relative to its normal seat. 0 in normal mode; 49.5 when
     * inverted so the bolt rides up into the ring's centre seat.
     */
    val boltY: Float = 0f,
    /**
     * 1 draws the battery ring, the percentage and the bolt. 0 hides them. [DuoPart] sets this;
     * the original element leaves both groups visible.
     */
    val ringOpacity: Float = 1f,
    /** 1 draws the icons that sit inside the ring (Wi-Fi and the middle slot). 0 hides them. */
    val indicatorsOpacity: Float = 1f,
    /**
     * The cellular generation shown in the middle slot when Wi-Fi is off — "5G"/"4G"/"3G"/"2G", or
     * empty when there is no service. Empty unless the slot is actually holding it, so a hidden
     * occupant never carries stale text (FR-06).
     */
    val networkText: String,
    val boltOpacity: Float,
    val wifiOuterOpacity: Float,
    val wifiMidOpacity: Float,
    val cell1Opacity: Float,
    val cell2Opacity: Float,
    val cell3Opacity: Float,
    val cell4Opacity: Float,
    /**
     * The same four places as the signal dots, drawn as the white Do Not Disturb crescent.
     * 0 leaves the gray circle; 1 is the crescent that has filled that circle in.
     */
    val moon1Opacity: Float,
    val moon2Opacity: Float,
    val moon3Opacity: Float,
    val moon4Opacity: Float,
    /**
     * One crescent in the middle of the signal-dot row. 1 only when Do Not Disturb is on the dots
     * and that row is fully hidden (airplane mode with Wi-Fi off), so the status is still visible.
     */
    val centerMoonOpacity: Float,
    val tint: Int,
    val fgColor: Int,
    /**
     * Which occupant the middle slot holds: 0 off, 1 Wi-Fi, 2 airplane, 3 DND (FR-06/FR-16).
     *
     * The slot's whole hand-over - the arcs collapsing, the dot fading, the plane or crescent growing
     * out of it - is one Rive state machine layer, so this is the only thing the host says about it.
     */
    val middleMode: Int,
    /**
     * The two signal axes the Rive blend layers read: Wi-Fi 0-3, cellular 0-4. One number each rather
     * than six opacities, because Rive blends between pose animations and gets the per-sphere cascade
     * for free as the eased axis sweeps past each sphere's threshold.
     */
    val wifiLevel: Int,
    val cellLevel: Int,
    /**
     * Charging drives the bolt's journey - it is born in the middle slot and travels up into the ring's
     * gap - so that whole sequence is one Rive layer, and this is the only thing the host says about it.
     */
    val charging: Boolean,
    /**
     * Download stroke on top of the white ring. [downloadOpacity] 0 leaves the battery ring as it
     * was. Lengths and offsets are trim fractions for the same caps the battery fill uses.
     */
    val downloadLeftEnd: Float = 0f,
    val downloadLeftOffset: Float = RingGeometry.LEFT_START,
    val downloadRightEnd: Float = 0f,
    val downloadRightOffset: Float = RingGeometry.RIGHT_START,
    val downloadOpacity: Float = 0f,
    val downloadColor: Int = DownloadWash.COLOR,
    val downloadGradient: DownloadGradient? = null,
    /** False plays the departure (screen off); true brings the element back. */
    val visible: Boolean = true,
    /**
     * Whether the charging *journey* plays. False shows the bolt instantly, which is the user's
     * "charging animation" switch (FR-25).
     */
    val animateCharge: Boolean = true,
    /** Rive event type id (0 = none). */
    val eventType: Int = 0,
    /** Whether a live event is currently visible. */
    val eventVisible: Boolean = false
) {
    /**
     * Interpolates between two snapshots, for the FR-09 setting demos that morph one setting between
     * its off and on states.
     *
     * Every drawn quantity is a Float, so those interpolate; the things that cannot be half-way
     * (colours, the text, the boolean that drives the state machine) snap at the mid-point. Pure and
     * unit-tested, because a demo that quietly shows the wrong thing is worse than no demo.
     */
    fun lerp(other: DuoVisual, t: Float): DuoVisual {
        val f = t.coerceIn(0f, 1f)
        fun at(a: Float, b: Float) = a + (b - a) * f
        val past = f >= 0.5f
        return copy(
            trimLeftEnd = at(trimLeftEnd, other.trimLeftEnd),
            trimRightEnd = at(trimRightEnd, other.trimRightEnd),
            leftArc = at(leftArc, other.leftArc),
            rightArc = at(rightArc, other.rightArc),
            trackOpacity = at(trackOpacity, other.trackOpacity),
            percentOpacity = at(percentOpacity, other.percentOpacity),
            percentFontSize = at(percentFontSize, other.percentFontSize),
            percentY = at(percentY, other.percentY),
            batteryLineStart = at(batteryLineStart, other.batteryLineStart),
            middleY = at(middleY, other.middleY),
            middleScale = at(middleScale, other.middleScale),
            boltY = at(boltY, other.boltY),
            ringOpacity = at(ringOpacity, other.ringOpacity),
            indicatorsOpacity = at(indicatorsOpacity, other.indicatorsOpacity),
            boltOpacity = at(boltOpacity, other.boltOpacity),
            wifiOuterOpacity = at(wifiOuterOpacity, other.wifiOuterOpacity),
            wifiMidOpacity = at(wifiMidOpacity, other.wifiMidOpacity),
            cell1Opacity = at(cell1Opacity, other.cell1Opacity),
            cell2Opacity = at(cell2Opacity, other.cell2Opacity),
            cell3Opacity = at(cell3Opacity, other.cell3Opacity),
            cell4Opacity = at(cell4Opacity, other.cell4Opacity),
            moon1Opacity = at(moon1Opacity, other.moon1Opacity),
            moon2Opacity = at(moon2Opacity, other.moon2Opacity),
            moon3Opacity = at(moon3Opacity, other.moon3Opacity),
            moon4Opacity = at(moon4Opacity, other.moon4Opacity),
            centerMoonOpacity = at(centerMoonOpacity, other.centerMoonOpacity),
            downloadLeftEnd = at(downloadLeftEnd, other.downloadLeftEnd),
            downloadLeftOffset = at(downloadLeftOffset, other.downloadLeftOffset),
            downloadRightEnd = at(downloadRightEnd, other.downloadRightEnd),
            downloadRightOffset = at(downloadRightOffset, other.downloadRightOffset),
            downloadOpacity = at(downloadOpacity, other.downloadOpacity),
            downloadColor = if (past) other.downloadColor else downloadColor,
            downloadGradient = if (past) other.downloadGradient else downloadGradient,
            percentText = if (past) other.percentText else percentText,
            networkText = if (past) other.networkText else networkText,
            tint = if (past) other.tint else tint,
            fgColor = if (past) other.fgColor else fgColor,
            // The middle slot's hand-over is a Rive layer, not a tween: this only picks which occupant.
            middleMode = if (past) other.middleMode else middleMode,
            wifiLevel = if (past) other.wifiLevel else wifiLevel,
            cellLevel = if (past) other.cellLevel else cellLevel,
            charging = if (past) other.charging else charging,
            visible = if (past) other.visible else visible,
            animateCharge = if (past) other.animateCharge else animateCharge,
            eventType = if (past) other.eventType else eventType,
            eventVisible = if (past) other.eventVisible else eventVisible
        )
    }
}
