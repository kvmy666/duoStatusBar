package io.github.kvmy666.duostatusbar.hook

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The one vector implementation of the iOS glyphs.
 *
 * Both runtime consumers call [draw]: [IosCanvasView] paints it into a normal view, and
 * [IosGlyphDrawable] paints it into a [android.graphics.drawable.Drawable] that replaces the stock
 * drawable in place. The shapes and colours are the ones the Rive scene `rive/ios/scene.rml` repeats
 * from [IosGeometry]; nothing is invented here. A change to one consumer therefore cannot drift from
 * the other (`IosDrawableParityTest`).
 *
 * Every glyph is laid out in artboard units and fitted with `min(w/W, h/H)`, centred, exactly like
 * Rive's `Fit.CONTAIN` + `Alignment.CENTER` and like the old [IosCanvasView] drawing.
 *
 * Dual SIM has two artboards besides the full block: when a per-slot row draws only the compressed
 * data bars or only the other SIM's dots, the renderer uses the row artboard added to
 * [IosGeometry.Cell] so the row fills its stock slot. The full artboard remains the design source for
 * the stacked `BOTH` block.
 */
internal object IosGlyphRenderer {

    /** The artboard width for [kind] in the state described by [visual]. */
    fun artboardWidth(kind: IosIconKind, visual: IosVisual): Float = when (kind) {
        IosIconKind.BATTERY -> IosGeometry.Battery.ARTBOARD_W
        IosIconKind.WIFI -> {
            val slot = visual.wifiSlot
            if (slot is IosWifiSlot.DataType) {
                IosGeometry.DataLabel.artboardWidth(slot.label)
                    ?: IosGeometry.Wifi.ARTBOARD_W
            } else {
                IosGeometry.Wifi.ARTBOARD_W
            }
        }
        IosIconKind.CELL -> when {
            visual.cellMode == IosCellMode.SINGLE -> IosGeometry.Cell.ARTBOARD_W
            visual.cellRole == IosCellRole.BOTH -> IosGeometry.Cell.ARTBOARD_W
            else -> IosGeometry.Cell.ROW_ARTBOARD_W
        }
    }

    /** The artboard height for [kind] in the state described by [visual]. */
    fun artboardHeight(kind: IosIconKind, visual: IosVisual): Float = when (kind) {
        IosIconKind.BATTERY -> IosGeometry.Battery.ARTBOARD_H
        IosIconKind.WIFI -> IosGeometry.Wifi.ARTBOARD_H
        IosIconKind.CELL -> when {
            visual.cellMode == IosCellMode.SINGLE -> IosGeometry.Cell.ARTBOARD_H
            visual.cellRole == IosCellRole.BOTH -> IosGeometry.Cell.ARTBOARD_H
            visual.cellRole == IosCellRole.DATA -> IosGeometry.Cell.DATA_ROW_ARTBOARD_H
            else -> IosGeometry.Cell.OTHER_ROW_ARTBOARD_H
        }
    }

    /**
     * Draws [kind] into the `width` x `height` box, scaled with `min(w/W, h/H)` and centred.
     *
     * [alpha] multiplies every drawn colour's own alpha. [overrideTint] replaces the dynamic tint for
     * the battery NORMAL fill, the neutral remainder/cap, the knocked-out percentage, Wi-Fi and
     * cellular; the fixed battery state colours (LOW red, SAVER yellow, CHARGING green,
     * PLUGGED_NOT_CHARGING grey) always stay [IosVisual.batteryFillColor]. The charging bolt is white
     * per ref 3, independent of the tint.
     * A hidden visual, a zero/negative box or a non-positive scale draws nothing.
     */
    fun draw(
        canvas: Canvas,
        kind: IosIconKind,
        visual: IosVisual,
        width: Int,
        height: Int,
        alpha: Float = 1f,
        overrideTint: Int? = null
    ) {
        if (!visual.visible || width <= 0 || height <= 0) return

        val artboardW = artboardWidth(kind, visual)
        val artboardH = artboardHeight(kind, visual)
        val scale = min(width / artboardW, height / artboardH)
        if (scale <= 0f) return

        val dx = (width - artboardW * scale) / 2f
        val dy = (height - artboardH * scale) / 2f

        // Sanitise here so both consumers are safe: non-finite components fall back to identity, and
        // a non-positive scale draws nothing. A NaN/infinite visual must never reach Canvas.
        val rawUser = visual.userTransform
        val user = IosUserTransform(
            dxPx = if (rawUser.dxPx.isFinite()) rawUser.dxPx else 0f,
            scale = if (rawUser.scale.isFinite()) rawUser.scale else 1f
        )
        if (user.scale <= 0f) return

        val saved = canvas.save()
        try {
            // Only a real transform needs a clip; identity runs exactly the old draw path (no extra
            // save/clip). The box is this draw call's width x height, i.e. the drawable/view bounds.
            if (user.dxPx != 0f || user.scale != 1f) {
                canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
            }
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            // User transform, applied once here after the CONTAIN fit so identity is byte-for-byte
            // the old draw. The scale is centred on the artboard; dxPx is a device-pixel shift, so
            // divide by both the fit scale and the user scale to keep the shift in device pixels
            // (independent of the fit and of the user scale).
            if (user.scale != 1f) {
                canvas.scale(user.scale, user.scale, artboardW / 2f, artboardH / 2f)
            }
            if (user.dxPx != 0f) {
                canvas.translate(user.dxPx / (scale * user.scale), 0f)
            }
            when (kind) {
                IosIconKind.BATTERY -> drawBattery(canvas, visual, alpha, overrideTint)
                IosIconKind.WIFI -> drawWifi(canvas, visual, alpha, overrideTint)
                IosIconKind.CELL -> drawCell(canvas, visual, alpha, overrideTint)
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    // ------------------------------------------------------------------------------- battery

    private fun drawBattery(canvas: Canvas, v: IosVisual, alpha: Float, overrideTint: Int?) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val b = IosGeometry.Battery
        val body = RectF(b.BODY_LEFT, b.BODY_TOP, b.BODY_LEFT + b.BODY_W, b.BODY_TOP + b.BODY_H)

        // The dynamic fill is the tint for NORMAL; every fixed state colour is kept as-is.
        val bodyFill = if (v.batteryState == IosBatteryState.NORMAL) {
            overrideTint ?: v.batteryFillColor
        } else {
            v.batteryFillColor
        }
        val tint = overrideTint ?: v.tint

        // Uncharged remainder: one rounded body at half alpha. It is NEUTRAL ink (the bar tint), not
        // the fixed state colour: the charged fill keeps the state colour, the remainder stays grey.
        val remainder = Path().apply {
            addRoundRect(body, b.BODY_RADIUS, b.BODY_RADIUS, Path.Direction.CW)
        }
        fill.color = withAlpha(tint, b.REMAINDER_ALPHA, alpha)
        canvas.drawPath(remainder, fill)

        // Charged part: clipped by the body, so the left corners stay round and the split is straight.
        canvas.save()
        canvas.clipPath(remainder)
        fill.color = withAlpha(bodyFill, 1f, alpha)
        canvas.drawRect(
            b.BODY_LEFT,
            b.BODY_TOP,
            b.BODY_LEFT + b.fillWidth(v.batteryLevel),
            b.BODY_TOP + b.BODY_H,
            fill
        )
        canvas.restore()

        // Cap nub: the measured right-hand half of a superellipse (n=1.8), 9.5 x 26.0, not a
        // circle or capsule. It is NEUTRAL ink like the uncharged remainder (the bar tint), not the
        // dynamic fill nor the fixed state colour. Its flat base sits against the body.
        val cap = Path().apply {
            val points = b.CAP_POINTS
            moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            close()
        }
        // PM rule (2026-11-22): at a full battery the cap is full ink, not the inactive half alpha.
        val capFactor = if (v.batteryLevel >= 1f) 1f else b.CAP_ALPHA
        fill.color = withAlpha(tint, capFactor, alpha)
        canvas.drawPath(cap, fill)

        if (v.percentInside) {
            // R3B: the percentage is drawn last so it knocks out of both the fill and the remainder.
            // The ink is the bar background colour, which keeps it readable in light, dark and
            // coloured bars. No large bolt and no outline in this mode; a charging bolt follows the
            // digits at the inline size.
            drawPercentInside(canvas, v, tint, fill, alpha)
        } else if (v.boltOpacity > 0f) {
            // Ref 3 shows a plain white bolt. Draw its outline first (2x the measured 4.5 stroke),
            // then fill white; there is no dynamic tint fill.
            val bolt = IosDigits.largeBoltPath()
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * b.BOLT_OUTLINE
                strokeJoin = Paint.Join.ROUND
                color = withAlpha(Color.BLACK, 0.55f, v.boltOpacity * alpha)
            }
            canvas.drawPath(bolt, outline)
            fill.color = withAlpha(b.BOLT_FILL, v.boltOpacity, alpha)
            canvas.drawPath(bolt, fill)
        }
    }

    /**
     * Draws the percentage and the optional inline bolt inside the battery body.
     *
     * The digits are FILLED OUTLINES (v4): each glyph is one even-odd [Path], so its counters are
     * holes, not strokes. They are drawn last — after the charged fill and the neutral remainder —
     * in [IosDigits.backgroundColour] so they knock out of both layers. Glyph origins use the
     * measured per-digit advance and bearing; the group is centred on the body. The caller's [fill]
     * is reused for the inline bolt (one paint, not one per digit).
     */
    private fun drawPercentInside(
        canvas: Canvas,
        v: IosVisual,
        tint: Int,
        fill: Paint,
        alpha: Float
    ) {
        val b = IosGeometry.Battery
        val pct = (v.batteryLevel * 100f).roundToInt().coerceIn(0, 100)
        val text = pct.toString()
        val colour = IosDigits.backgroundColour(tint)
        val centreX = b.BODY_LEFT + b.BODY_W / 2f
        val centreY = b.BODY_TOP + b.BODY_H / 2f
        val textTop = centreY - IosDigits.CAP_HEIGHT / 2f
        val hasBolt = v.boltOpacity > 0f
        val textWidth = IosDigits.textInkWidth(text)
        val group = IosDigits.groupWidth(text, withBolt = hasBolt)
        val left = centreX - group / 2f

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(colour, 1f, alpha)
        }
        // [left] is the first glyph's ink edge, so undo its bearing to get the pen origin.
        var pen = left - IosDigits.bearing(IosDigits.glyph(text.first()))
        for (ch in text) {
            val glyph = IosDigits.glyph(ch)
            canvas.drawPath(IosDigits.buildPath(glyph, pen + IosDigits.bearing(glyph), textTop), ink)
            pen += glyph.advance
        }

        if (hasBolt) {
            val boltX = left + textWidth + IosDigits.INLINE_BOLT_GAP + IosDigits.INLINE_BOLT_W / 2f
            fill.color = withAlpha(colour, v.boltOpacity, alpha)
            canvas.drawPath(IosDigits.inlineBoltPath(boltX, centreY), fill)
        }
    }

    // ----------------------------------------------------------------------------------- wifi

    private fun drawWifi(canvas: Canvas, v: IosVisual, alpha: Float, overrideTint: Int?) {
        when (val slot = v.wifiSlot) {
            // NONE: the host wins the slot for something else; draw no glyph at all.
            IosWifiSlot.None -> return
            is IosWifiSlot.DataType -> {
                // An unsupported label draws nothing: never silently fall back to Wi-Fi ink.
                if (IosGeometry.DataLabel.supports(slot.label)) {
                    drawDataLabel(canvas, slot.label, alpha, overrideTint ?: v.tint)
                }
                return
            }
            IosWifiSlot.Wifi -> Unit
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val tint = overrideTint ?: v.tint
        val w = IosGeometry.Wifi
        val level = v.wifiLevel.roundToInt().coerceIn(0, 3)
        val cx = w.CENTRE_X
        val cy = w.CENTRE_Y

        arc(
            canvas, stroke, cx, cy,
            radius = w.OUTER_RADIUS,
            thickness = w.OUTER_STROKE,
            trimEnd = w.OUTER_TRIM_END,
            trimOffset = w.OUTER_TRIM_OFFSET,
            colour = withAlpha(tint, w.OUTER_ALPHA[level], alpha)
        )
        arc(
            canvas, stroke, cx, cy,
            radius = w.INNER_RADIUS,
            thickness = w.INNER_STROKE,
            trimEnd = w.INNER_TRIM_END,
            trimOffset = w.INNER_TRIM_OFFSET,
            colour = withAlpha(tint, w.INNER_ALPHA[level], alpha)
        )

        // The dot is not part of the level ramp; it stays at full tint.
        val dot = Path().apply {
            val points = w.DOT_POINTS
            moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            close()
        }
        fill.color = withAlpha(tint, 1f, alpha)
        canvas.drawPath(dot, fill)
    }

    /**
     * Draws a data-type label ("LTE", "4G", ...) in the Wi-Fi artboard: letters and plus are our own
     * round vector strokes, the 3/4/5 digits are the measured [IosDigits] filled even-odd contours
     * scaled to [IosGeometry.DataLabel.CAP_HEIGHT]. There is no font and no bitmap. The group ink
     * starts at the artboard margin and is centred by the caller's CONTAIN fit; the cap box top is
     * [IosGeometry.DataLabel.TOP]. An unsupported label is not drawn here (the dispatch in [drawWifi]
     * checks [IosGeometry.DataLabel.supports] first).
     */
    private fun drawDataLabel(canvas: Canvas, label: String, alpha: Float, tint: Int) {
        val d = IosGeometry.DataLabel
        val first = label.firstOrNull() ?: return
        val colour = withAlpha(tint, 1f, alpha)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = d.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = colour
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colour
        }

        val top = d.TOP
        var pen = IosGeometry.Wifi.ARTBOARD_MARGIN - d.bearing(first)
        for (ch in label) {
            val glyphLeft = pen + d.bearing(ch)
            if (d.isDigit(ch)) {
                val saved = canvas.save()
                try {
                    canvas.translate(glyphLeft, top)
                    canvas.scale(d.DIGIT_SCALE, d.DIGIT_SCALE)
                    canvas.drawPath(IosDigits.buildPath(IosDigits.glyph(ch), 0f, 0f), fill)
                } finally {
                    canvas.restoreToCount(saved)
                }
            } else {
                for (points in d.strokes(ch)) {
                    if (points.size < 4) continue
                    val path = Path().apply {
                        moveTo(glyphLeft + points[0], top + points[1])
                        var i = 2
                        while (i < points.size) {
                            lineTo(glyphLeft + points[i], top + points[i + 1])
                            i += 2
                        }
                    }
                    canvas.drawPath(path, stroke)
                }
            }
            pen += d.advance(ch)
        }
    }

    private fun arc(
        canvas: Canvas,
        stroke: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        thickness: Float,
        trimEnd: Float,
        trimOffset: Float,
        colour: Int
    ) {
        val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        stroke.color = colour
        stroke.strokeWidth = thickness
        // Rive fractions run clockwise from 12 o'clock; Android's 0deg is 3 o'clock.
        val start = -90f + trimOffset * 360f
        canvas.drawArc(bounds, start, trimEnd * 360f, false, stroke)
    }

    // ----------------------------------------------------------------------------------- cell

    private fun drawCell(canvas: Canvas, v: IosVisual, alpha: Float, overrideTint: Int?) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val c = IosGeometry.Cell
        val tint = overrideTint ?: v.tint

        if (v.cellMode == IosCellMode.SINGLE) {
            // SINGLE draws the four tall bars in the full artboard; the dot row is hidden. The
            // baseline is the named SINGLE_BAR_BOTTOM (96.5), so bar 4's top is exactly TOP.
            drawBars(canvas, fill, v, c.SINGLE_BAR_HEIGHTS, c.BAR_W, c.SINGLE_BAR_BOTTOM, tint, alpha)
            return
        }

        when (v.cellRole) {
            // A per-slot DATA row uses the compressed bar row artboard, with no dots.
            IosCellRole.DATA -> drawBars(
                canvas, fill, v, c.DUAL_BAR_HEIGHTS, c.DUAL_BAR_W, c.TOP + c.DUAL_ROW_H, tint, alpha
            )
            // A per-slot OTHER row uses the dot row artboard: translate the absolute dot coordinates
            // to the top of that artboard and draw no bars.
            IosCellRole.OTHER -> {
                val saved = canvas.save()
                canvas.translate(0f, c.otherRowOffsetY())
                drawDots(canvas, fill, v, tint, alpha)
                canvas.restoreToCount(saved)
            }
            // The stacked block keeps the full artboard: bars on top, dots below.
            IosCellRole.BOTH -> {
                drawBars(
                    canvas, fill, v, c.DUAL_BAR_HEIGHTS, c.DUAL_BAR_W, c.TOP + c.DUAL_ROW_H, tint, alpha
                )
                drawDots(canvas, fill, v, tint, alpha)
            }
        }
    }

    private fun drawBars(
        canvas: Canvas,
        fill: Paint,
        v: IosVisual,
        heights: FloatArray,
        columnW: Float,
        barBottom: Float,
        tint: Int,
        alpha: Float
    ) {
        val c = IosGeometry.Cell
        val bars = v.cellBars.coerceIn(0f, 4f)
        for (i in 0 until 4) {
            val active = i < bars
            fill.color = withAlpha(tint, if (active) 1f else c.INACTIVE_ALPHA, alpha)
            val left = c.barLeft(i) + (c.BAR_W - columnW) / 2f
            val top = barBottom - heights[i]
            val radius = columnW / 2f
            canvas.drawRoundRect(left, top, left + columnW, barBottom, radius, radius, fill)
        }
    }

    private fun drawDots(canvas: Canvas, fill: Paint, v: IosVisual, tint: Int, alpha: Float) {
        val c = IosGeometry.Cell
        val dots = v.cellDots.coerceIn(0f, 4f)
        for (i in 0 until 4) {
            val active = i < dots
            fill.color = withAlpha(tint, if (active) 1f else c.INACTIVE_ALPHA, alpha)
            val cx = c.dotCenterX(i)
            val cy = c.dotCenterY()
            canvas.drawOval(
                cx - c.DOT_W / 2f,
                cy - c.DOT_H / 2f,
                cx + c.DOT_W / 2f,
                cy + c.DOT_H / 2f,
                fill
            )
        }
    }

    /** `colour` with its own alpha multiplied by [factor] and then the drawable's [alpha]. */
    private fun withAlpha(colour: Int, factor: Float, alpha: Float): Int {
        val a = (Color.alpha(colour) * factor.coerceIn(0f, 1f) * alpha.coerceIn(0f, 1f))
            .roundToInt()
            .coerceIn(0, 255)
        return Color.argb(a, Color.red(colour), Color.green(colour), Color.blue(colour))
    }
}
