package io.github.kvmy666.duostatusbar.hook

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
     * the battery NORMAL fill, the neutral remainder/cap, Wi-Fi and cellular; the fixed battery state
     * colours (LOW red, SAVER yellow, CHARGING green, PLUGGED_NOT_CHARGING grey) always stay
     * [IosVisual.batteryFillColor]. The percentage is a true knockout (no tint colour). The charging
     * bolt is white per ref 3, independent of the tint.
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
            // No self-clip: a user-scaled/offset glyph may legitimately overflow this draw box. The
            // host owns the room (ancestor unclip + an overlay canvas sized to the transformed box),
            // so identity still draws exactly the old pixels and non-identity ink is not cropped.
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

        // R9 colour rule: remainder/nub use the tint at 0.30 for dark ink and 0.50 for light ink.
        val remainderAlpha = IosGeometry.remainderAlpha(tint)

        // R6 item 6 / R9: `percentInside` is a TRUE knockout in normal/green/yellow. The red critical
        // state is the one exception: its digits are solid white (not a hole). A CLEAR stroke must be
        // drawn into an isolated layer, otherwise it would erase glyphs already drawn beside the
        // battery.
        val solidDigits = v.percentInside && v.batteryState == IosBatteryState.LOW
        val knockout = v.percentInside && !solidDigits
        val layer = if (knockout) canvas.saveLayer(null, null) else -1
        try {
            // Uncharged remainder: one rounded body at the luminance alpha. It is NEUTRAL ink (the
            // bar tint), not the fixed state colour.
            val remainder = Path().apply {
                addRoundRect(body, b.BODY_RADIUS, b.BODY_RADIUS, Path.Direction.CW)
            }
            fill.color = withAlpha(tint, remainderAlpha, alpha)
            canvas.drawPath(remainder, fill)

            // Charged part: clipped by the body, so the left corners stay round and the split is
            // straight. Opaque and flush left-to-right (R9).
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

            // Separate half-capsule nub (flat left, round right), same neutral ink as the remainder;
            // full ink at 100 %.
            val nub = Path().apply {
                val points = b.CAP_POINTS
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                close()
            }
            val nubFactor = if (v.batteryLevel >= 1f) 1f else remainderAlpha
            fill.color = withAlpha(tint, nubFactor, alpha)
            canvas.drawPath(nub, fill)

            if (v.percentInside) {
                // Red critical: solid white digits. Normal/green/yellow: CLEAR stroke holes.
                drawPercentInside(canvas, v, solid = solidDigits, alpha = alpha)
            } else if (v.boltOpacity > 0f) {
                // R9: no digits -> a solid system-ink bolt (~67 x 92), centred, no outline.
                fill.color = withAlpha(tint, v.boltOpacity, alpha)
                canvas.drawPath(IosDigits.largeBoltPath(), fill)
            }
        } finally {
            if (knockout) canvas.restoreToCount(layer)
        }
    }

    /**
     * Draws the percentage digits as traced filled even-odd contours, plus the optional inline bolt.
     *
     * [solid] is the red-critical rule: solid white digits and, when present, the inline bolt is
     * solid white too (no layer). Otherwise the digit ink band and the inline bolt are CLEAR fills
     * so the real background shows through; glyph counters stay body ink because they are holes of
     * the even-odd path, not cleared areas. The digits shift left to make room for the 40 x 59 bolt
     * at the measured 5-unit gap.
     */
    private fun drawPercentInside(canvas: Canvas, v: IosVisual, solid: Boolean, alpha: Float) {
        val b = IosGeometry.Battery
        val pct = (v.batteryLevel * 100f).roundToInt().coerceIn(0, 100)
        val text = pct.toString()
        val centreX = b.BODY_LEFT + b.BODY_W / 2f
        val centreY = b.BODY_TOP + b.BODY_H / 2f
        val textTop = centreY - IosDigits.CAP_HEIGHT / 2f
        val hasBolt = v.boltOpacity > 0f
        val textWidth = IosDigits.textInkWidth(text)
        val group = IosDigits.groupWidth(text, withBolt = hasBolt)
        val left = centreX - group / 2f

        // ONE even-odd filled path for the whole percentage: a glyph is placed at its pen and the
        // pen walks the measured ink-left advance.
        val digits = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            var pen = left
            for (ch in text) {
                val glyph = IosDigits.glyph(ch)
                addPath(IosDigits.buildPath(glyph, pen, textTop))
                pen += glyph.advance
            }
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        if (solid) {
            fill.color = withAlpha(Color.WHITE, 1f, alpha)
            canvas.drawPath(digits, fill)
            if (hasBolt) {
                // Red critical keeps the inline bolt too, as solid white at the same measured seat.
                val boltX = left + textWidth + IosDigits.INLINE_BOLT_GAP + IosDigits.INLINE_BOLT_W / 2f
                val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = withAlpha(Color.WHITE, 1f, alpha)
                }
                canvas.drawPath(IosDigits.inlineBoltPath(boltX, centreY), white)
            }
            return
        }

        fill.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawPath(digits, fill)
        if (hasBolt) {
            val boltX = left + textWidth + IosDigits.INLINE_BOLT_GAP + IosDigits.INLINE_BOLT_W / 2f
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            canvas.drawPath(IosDigits.inlineBoltPath(boltX, centreY), clear)
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
     * Draws a data-type label ("LTE", "4G", ...) in the Wi-Fi artboard: every character is the
     * measured R10 [IosLabelGlyphs] set of closed cubic contours, drawn as one
     * [Path.FillType.EVEN_ODD] filled path at its pen. The ink-left pen starts at
     * [IosGeometry.Wifi.ARTBOARD_MARGIN] and advances by each measured advance; the cap box top is
     * [IosGeometry.DataLabel.TOP]. There is no font, no bitmap and no stroke. An unsupported label
     * is not drawn here (the dispatch in [drawWifi] checks [IosGeometry.DataLabel.supports] first).
     */
    private fun drawDataLabel(canvas: Canvas, label: String, alpha: Float, tint: Int) {
        val d = IosGeometry.DataLabel
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(tint, 1f, alpha)
        }
        var pen = IosGeometry.Wifi.ARTBOARD_MARGIN
        for (ch in label) {
            val glyph = IosLabelGlyphs.glyph(ch) ?: continue
            canvas.drawPath(IosLabelGlyphs.buildPath(glyph, pen, d.TOP), fill)
            pen += glyph.advance
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
            // baseline is the named SINGLE_BAR_BOTTOM (93.9), so bar 4's top is exactly TOP.
            drawBars(
                canvas, fill, v, c.SINGLE_BAR_HEIGHTS, c.BAR_W, c.SINGLE_BAR_BOTTOM,
                c.PITCH, c.CORNER_RADIUS, tint, alpha
            )
            return
        }

        when (v.cellRole) {
            // A per-slot DATA row uses the compressed bar row artboard, with no dots.
            IosCellRole.DATA -> drawBars(
                canvas, fill, v, c.DUAL_BAR_HEIGHTS, c.DUAL_BAR_W, c.TOP + c.DUAL_ROW_H,
                c.DUAL_PITCH, c.DUAL_CORNER_RADIUS, tint, alpha
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
                    canvas, fill, v, c.DUAL_BAR_HEIGHTS, c.DUAL_BAR_W, c.TOP + c.DUAL_ROW_H,
                    c.DUAL_PITCH, c.DUAL_CORNER_RADIUS, tint, alpha
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
        pitch: Float,
        radius: Float,
        tint: Int,
        alpha: Float
    ) {
        val c = IosGeometry.Cell
        val bars = v.cellBars.coerceIn(0f, 4f)
        for (i in 0 until 4) {
            val active = i < bars
            fill.color = withAlpha(tint, if (active) 1f else c.INACTIVE_ALPHA, alpha)
            val left = c.LEFT + i * pitch
            val top = barBottom - heights[i]
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
