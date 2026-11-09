package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.events.LiveEventType
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * The element drawn with plain Android Canvas — no Rive, no native code, nothing that can fault.
 *
 * It exists for two reasons:
 *
 *  1. **Stage 1** of the on-device test: inserting into `system_icons` and hiding the stock icons can
 *     be verified with zero native risk, separately from Rive.
 *  2. **Fallback**: if Rive fails to come up, the status bar still shows a right-looking element
 *     instead of a hole (FR-21).
 *
 * It draws what is known exactly — ring with the top gap, the track, the battery colour and the
 * percentage (or the bolt while charging). The eight signal dots and the crescents that fill them
 * use the same seats as the Rive ellipses, so this fallback agrees with the live element there.
 * The Wi-Fi glyph is drawn here too, from the same seats and opacities the Rive path binds — it used
 * to be Rive-only, which left the middle slot empty (no Wi-Fi icon) whenever this fallback ran
 * (reported: "wifi icon not show" at stage 1). The airplane glyph still remains Rive-only. The
 * mapping is shared with the Rive path via [DuoMapping] so the ring and the colour can never
 * disagree between the two.
 *
 * Canvas angles start at 3 o'clock, Rive trim fractions at 12 o'clock, hence [+TRIM_ORIGIN].
 */
internal class DuoCanvasView(
    context: Context,
    part: DuoPart = DuoPart.ALL,
    designStyle: String = DuoPrefs.DESIGN_OLD
) : View(context), DuoElement {

    private val classic = DuoPrefs.normalizeDesignStyle(designStyle) == DuoPrefs.DESIGN_OLD

    private var incoming: DuoVisual? = null

    /**
     * Whether this view is on screen. A hidden Canvas copy still receives snapshots (so it is current
     * when revealed), but it must not invalidate or run the dot-fill tick while nobody can see it.
     */
    private var renderActive = true

    private var visual: DuoVisual = part.apply(
        DuoMapping.visual(
            level = 100, charging = false, saver = false, showPercent = true,
            wifiLevel = 3, cellLevel = 4, airplane = false
        )
    )

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wifiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val eventStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arcBounds = RectF()

    /** Grows a gray signal dot into the white Do Not Disturb crescent. See [DotMoonMotion]. */
    private val motion = DotMoonMotion()
    /** When the bolt first appeared, so the Canvas clone can pulse when ChargeIn settles (1.6 s). */
    private var chargeStartedAt = 0L
    private var wasCharging = false
    private var flashlightStartedAt = 0L
    private var wasFlashlightVisible = false
    private var ticking = false
    private val tick = Runnable {
        ticking = false
        step()
    }

    override var part: DuoPart = part
        set(value) {
            if (field == value) return
            field = value
            val current = incoming ?: return
            incoming = null
            render(current)
        }

    override val ui: View get() = this
    override val rendererName: String get() = "Canvas"
    override val isReady: Boolean get() = true

    override fun start(): Boolean = true

    /** Canvas draws from the first frame, so readiness is never deferred. */
    override fun onReady(action: () -> Unit) = action()

    override fun onFailed(action: () -> Unit) = Unit

    override fun render(v: DuoVisual) {
        try {
            if (v == incoming) return
            incoming = v
            if (renderActive) step()
        } catch (t: Throwable) {
            L.w("canvas render: ${t.message}")
        }
    }

    override fun setRenderActive(active: Boolean) {
        if (renderActive == active) return
        renderActive = active
        if (active) {
            step()
        } else {
            removeCallbacks(tick)
            ticking = false
        }
    }

    /** Draws the current fill frame and keeps invalidating until a dot has finished becoming a moon. */
    private fun step() {
        val next = incoming ?: return
        val now = SystemClock.uptimeMillis()
        if (next.charging && !wasCharging) chargeStartedAt = now
        wasCharging = next.charging
        val flashVisible = next.eventVisible && next.eventType == LiveEventType.FLASHLIGHT.riveId
        if (flashVisible && !wasFlashlightVisible) flashlightStartedAt = now
        wasFlashlightVisible = flashVisible
        visual = part.apply(motion.push(next, now))
        invalidate()
        val pulseRunning = visual.charging &&
            (now - chargeStartedAt) in ChargeMotion.SETTLE_START_MS..ChargeMotion.SETTLE_END_MS
        val flashRampRunning = flashVisible && (now - flashlightStartedAt) < FLASHLIGHT_RAMP_MS
        if ((motion.running || pulseRunning || flashRampRunning) && !ticking) {
            ticking = true
            postOnAnimation(tick)
        }
    }

    /** The arrival itself is Rive's to run. The dot fill above is the only motion this view owns. */
    override fun reveal(ms: Int) = Unit

    override fun teardown() {
        removeCallbacks(tick)
        ticking = false
        renderActive = false
    }

    override fun onDraw(canvas: Canvas) {
        try {
            drawElement(canvas)
        } catch (t: Throwable) {
            // A drawing failure must never repeat: draw nothing rather than throw every frame.
            L.w("canvas onDraw: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun drawElement(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        // Rive lays the 120x136 artboard into this view with Fit.CONTAIN, so the fallback must use
        // the same scale: one design unit = width/120 px. The old /103 scale drew the element ~16%
        // larger and left no room for the battery line below the ring centre (P2a parity).
        val scale = width / RingGeometry.ARTBOARD_SIZE
        val side = width.toFloat()
        val cx = width / 2f
        val cy = height * RingGeometry.RING_CENTER_Y / RingGeometry.ARTBOARD_HEIGHT
        val drawRing = visual.ringOpacity > 0.5f
        val drawIndicators = visual.indicatorsOpacity > 0.5f
        // A live event owns the middle slot for its lifetime. The ring and the signal dots stay;
        // only the normal middle occupant gives way, matching the Rive middleFadeGroup. An event type
        // without Canvas artwork leaves the middle intact instead of showing an empty slot.
        val hasEventArt = visual.eventType == LiveEventType.ALARM.riveId ||
            visual.eventType == LiveEventType.FLASHLIGHT.riveId ||
            visual.eventType == LiveEventType.SCREENSHOT.riveId
        val drawEvent = drawIndicators && visual.eventVisible && hasEventArt
        val drawMiddle = drawIndicators && !drawEvent

        // The static ring's battery: a gray track for the full bottom span, then the white level line
        // on top from the level-derived start to the fixed end. Download wash keeps its old ring path
        // and paint order until P3 re-authors it.
        if (drawRing) {
            val lineRadius = RingGeometry.BATTERY_LINE_RADIUS * scale
            arcBounds.set(cx - lineRadius, cy - lineRadius, cx + lineRadius, cy + lineRadius)
            // Gray track first: full span BATTERY_LINE_START..BATTERY_LINE_END at trackOpacity.
            ring.strokeWidth = RingGeometry.BATTERY_LINE_STROKE * scale
            ring.color = withAlpha(visual.fgColor, visual.trackOpacity)
            ring.shader = null
            ring.alpha = 255
            canvas.drawArc(
                arcBounds,
                TRIM_ORIGIN + 360f * RingGeometry.BATTERY_LINE_START,
                RingGeometry.BATTERY_LINE_SWEEP_DEG,
                false,
                ring
            )
            // White level line on top, from the level-derived start to the fixed end.
            ring.strokeWidth = RingGeometry.BATTERY_LINE_STROKE * scale
            ring.color = visual.tint
            ring.shader = null
            ring.alpha = 255
            // The battery line's start follows the level (see RingGeometry.batteryLineStart): it runs
            // from a moving start to the fixed end BATTERY_LINE_END (0.6193), growing from left to right.
            // At 0 % the sweep is zero (empty); at 100 % it is the full 86.2 deg.
            canvas.drawArc(
                arcBounds,
                TRIM_ORIGIN + 360f * visual.batteryLineStart,
                (RingGeometry.BATTERY_LINE_END - visual.batteryLineStart) * 360f,
                false,
                ring
            )
            // Download wash, on top of the battery line, from the same caps. Opacity 0 draws nothing.
            if (visual.downloadOpacity > 0.01f) {
                ring.strokeWidth = RingGeometry.RING_STROKE * scale
                val gradient = visual.downloadGradient
                ring.shader =
                    gradient?.let {
                        val points = it.points(
                            arcBounds.left,
                            arcBounds.top,
                            arcBounds.right,
                            arcBounds.bottom,
                        )
                        LinearGradient(
                            points[0],
                            points[1],
                            points[2],
                            points[3],
                            it.colors.toIntArray(),
                            null,
                            Shader.TileMode.CLAMP,
                        )
                    }
                if (gradient == null) {
                    ring.color = withAlpha(visual.downloadColor, visual.downloadOpacity)
                } else {
                    ring.alpha = (visual.downloadOpacity * 255f).toInt().coerceIn(0, 255)
                }
                val downloadLeft = visual.downloadLeftEnd * 360f
                if (downloadLeft > MIN_SWEEP) {
                    canvas.drawArc(
                        arcBounds,
                        TRIM_ORIGIN + 360f * visual.downloadLeftOffset,
                        downloadLeft,
                        false,
                        ring
                    )
                }
                val downloadRight = visual.downloadRightEnd * 360f
                if (downloadRight > MIN_SWEEP) {
                    canvas.drawArc(
                        arcBounds,
                        TRIM_ORIGIN + 360f * visual.downloadRightOffset,
                        downloadRight,
                        false,
                        ring
                    )
                }
                ring.shader = null
                ring.alpha = 255
            }
        }

        // FR-06: the DND crescent takes the middle slot (0, 17 design units below the ring centre).
        if (drawMiddle && visual.middleMode == DuoMapping.MIDDLE_DND) {
            drawMoon(
                canvas,
                cx,
                cy + (visual.middleY + DND_SLOT_Y * visual.middleScale) * scale,
                scale * visual.middleScale,
                1f
            )
        }

        // FR-06: the Wi-Fi glyph takes the middle slot while the radio is connected. Drawn from the
        // same seats and opacities the Rive path binds, so the fallback shows the same thing.
        if (drawMiddle && visual.middleMode == DuoMapping.MIDDLE_WIFI) {
            drawWifi(canvas, cx, cy, scale)
        }

        // Eight signal dots, four per side: indices 0..3 are cell1..cell4 (weakest bottom ->
        // strongest top) on the right, then the same four mirrored on the left, from the shared
        // RingGeometry table the Rive scene is authored from. The left deliberately reads the same
        // opacities as the right (SIM1 right / SIM2 left is UNVERIFIED until the owner confirms it).
        // Gray circles come first, then the white crescents that fill them; a strong bar's circle
        // opacity falls as its moon grows. They belong to the ring, so a split layout keeps them.
        if (drawRing) {
            val cells = floatArrayOf(
                visual.cell1Opacity, visual.cell2Opacity, visual.cell3Opacity, visual.cell4Opacity
            )
            val sides = if (classic) intArrayOf(1) else intArrayOf(1, -1)
            for (side in sides) {
                for (i in cells.indices) {
                    if (cells[i] <= 0f) continue
                    moonPaint.color = withAlpha(visual.fgColor, cells[i])
                    canvas.drawCircle(
                        cx + side * RingGeometry.CELL_DOT_POSITIONS[i][0] * scale,
                        cy + RingGeometry.CELL_DOT_POSITIONS[i][1] * scale,
                        RingGeometry.DOT_RADIUS * scale,
                        moonPaint
                    )
                }
            }
            val moons = floatArrayOf(
                visual.moon1Opacity, visual.moon2Opacity, visual.moon3Opacity, visual.moon4Opacity
            )
            for (side in sides) {
                for (i in moons.indices) {
                    if (moons[i] <= 0f) continue
                    drawMoon(
                        canvas,
                        cx + side * RingGeometry.CELL_DOT_POSITIONS[i][0] * scale,
                        cy + RingGeometry.CELL_DOT_POSITIONS[i][1] * scale,
                        scale * DotMoonMotion.DOT_SCALE * moons[i],
                        moons[i]
                    )
                }
            }
            if (visual.centerMoonOpacity > 0f) {
                drawMoon(
                    canvas,
                    cx + DotMoonMotion.CENTER_X * scale,
                    cy + DotMoonMotion.CENTER_Y * scale,
                    scale * DotMoonMotion.DOT_SCALE * visual.centerMoonOpacity,
                    visual.centerMoonOpacity
                )
            }
        }

        // The Canvas event clone. One shape per event type for now; the icon is drawn in the same
        // seat as the Rive eventGroup, and the event type selects the artwork, not the motion.
        if (drawEvent) {
            drawEventIcon(canvas, cx, cy, scale)
        }

        // FR-06: with Wi-Fi off the slot shows the cellular generation. Same face and weight as the
        // Rive label; the ring's percentage is drawn below and does not overlap it.
        if (drawMiddle && visual.middleMode == DuoMapping.MIDDLE_NETWORK && visual.networkText.isNotEmpty()) {
            label.color = visual.fgColor
            // Same 80-unit box as the Rive `network` Text: "5G" keeps its 30-unit size, while a
            // longer clock such as "12:34 PM" shrinks just enough to stay on one line inside the ring.
            val baseSize = NETWORK_FONT_SIZE * scale * visual.middleScale
            label.textSize = baseSize
            val width = label.measureText(visual.networkText)
            val maxWidth = NETWORK_MAX_WIDTH * scale * visual.middleScale
            if (width > maxWidth && width > 0f) {
                label.textSize = (baseSize * maxWidth / width).coerceAtLeast(1f)
            }
            canvas.drawText(
                visual.networkText,
                cx,
                cy + (visual.middleY + NETWORK_SLOT_Y * visual.middleScale) * scale -
                    (label.descent() + label.ascent()) / 2f,
                label
            )
        }

        if (!drawRing) return
        if (visual.boltOpacity > 0f) {
            drawBolt(canvas, cx, cy, side)
            return
        }
        val text = visual.percentText.trim()
        if (text.isEmpty() || visual.percentOpacity <= 0f) return
        label.color = visual.fgColor
        label.textSize = visual.percentFontSize * scale
        // The Rive node is placed by its top; the canvas centres the glyphs, so add half the box.
        // Drawing it at the ring centre put it straight on top of the 4G/5G label.
        val percentCenter = visual.percentY + RingGeometry.PERCENT_BOX_HALF
        canvas.drawText(
            text,
            cx,
            cy + percentCenter * scale - (label.descent() + label.ascent()) / 2f,
            label
        )
    }

    /** Lightning bolt, unit coordinates scaled to the ring, shown while charging. */


    private fun drawScreenshot(canvas: Canvas, color: Int) {
        eventStrokePaint.color = color
        eventStrokePaint.strokeWidth = 4f
        // Four corner brackets.
        canvas.drawLine(-14f, -21f, -23f, -21f, eventStrokePaint)
        canvas.drawLine(-23f, -21f, -23f, -12f, eventStrokePaint)
        canvas.drawLine(14f, -21f, 23f, -21f, eventStrokePaint)
        canvas.drawLine(23f, -21f, 23f, -12f, eventStrokePaint)
        canvas.drawLine(-23f, 12f, -23f, 21f, eventStrokePaint)
        canvas.drawLine(-23f, 21f, -14f, 21f, eventStrokePaint)
        canvas.drawLine(23f, 12f, 23f, 21f, eventStrokePaint)
        canvas.drawLine(23f, 21f, 14f, 21f, eventStrokePaint)
        eventStrokePaint.strokeWidth = 3.5f
        // Camera body with the small top hump.
        val body = Path().apply {
            moveTo(-13f, -3f); lineTo(-6f, -3f); lineTo(-3f, -7f); lineTo(3f, -7f)
            lineTo(6f, -3f); lineTo(13f, -3f); lineTo(13f, 15f); lineTo(-13f, 15f)
            close()
        }
        canvas.drawPath(body, eventStrokePaint)
        canvas.drawCircle(0f, 6f, 6.5f, eventStrokePaint)
        eventPaint.color = color
        canvas.drawCircle(0f, 6f, 2f, eventPaint)
    }

    private fun drawAlarm(canvas: Canvas, color: Int) {
        eventStrokePaint.color = color
        eventStrokePaint.strokeWidth = 4f
        canvas.drawLine(-7f, 16f, -12f, 24f, eventStrokePaint)
        canvas.drawLine(7f, 16f, 12f, 24f, eventStrokePaint)
        eventPaint.color = color
        canvas.drawCircle(-12f, -20f, 7f, eventPaint)
        canvas.drawCircle(12f, -20f, 7f, eventPaint)
        eventStrokePaint.strokeWidth = 4.5f
        canvas.drawCircle(0f, 0f, 18f, eventStrokePaint)
        eventStrokePaint.strokeWidth = 3f
        canvas.drawLine(0f, 0f, 0f, -10f, eventStrokePaint)
        canvas.drawLine(0f, 0f, 9f, 0f, eventStrokePaint)
        eventPaint.color = color
        canvas.drawCircle(0f, 0f, 2f, eventPaint)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val path = boltPath
        val elapsed = SystemClock.uptimeMillis() - chargeStartedAt
        val pulse = if (visual.charging &&
            elapsed in ChargeMotion.SETTLE_START_MS..ChargeMotion.SETTLE_END_MS
        ) {
            val t = (elapsed - ChargeMotion.SETTLE_START_MS) /
                (ChargeMotion.SETTLE_END_MS - ChargeMotion.SETTLE_START_MS).toFloat()
            1f + ChargeMotion.SETTLE_SCALE * kotlin.math.sin(t * Math.PI).toFloat()
        } else {
            1f
        }
        val scale = size * BOLT_SCALE * pulse
        val seatY = (-49.5f + visual.boltY) * (size / RingGeometry.ARTBOARD_SIZE)
        boltPaint.color = visual.tint
        canvas.save()
        canvas.translate(cx - scale / 2f, cy + seatY - scale / 2f)
        canvas.scale(scale, scale)
        canvas.drawPath(path, boltPaint)
        canvas.restore()
    }

    /**
     * The Wi-Fi glyph in the middle slot: the dot plus the two arcs, from the same seats and opacities
     * the Rive path binds (outer ellipse r 31.1, mid r 18.15, both ±43.1° about 12 o'clock, dot 3.2
     * above the Wi-Fi centre). The dot is drawn while the radio is connected; the arcs fade in with
     * the signal exactly as Rive's `wifiMidOpacity` / `wifiOuterOpacity` do.
     */
    private fun drawWifi(canvas: Canvas, cx: Float, cy: Float, k: Float) {
        val m = visual.middleScale
        val wx = cx + WIFI_CENTER_X * k
        val wy = cy + (visual.middleY + WIFI_SLOT_Y * m) * k
        if (visual.wifiLevel > 0) {
            wifiPaint.style = Paint.Style.FILL
            wifiPaint.color = withAlpha(visual.fgColor, 1f)
            canvas.drawCircle(wx, wy - WIFI_DOT_LIFT * k * m, WIFI_DOT_RADIUS * k * m, wifiPaint)
        }
        wifiPaint.style = Paint.Style.STROKE
        wifiPaint.strokeWidth = WIFI_STROKE * k * m
        wifiPaint.strokeCap = Paint.Cap.ROUND
        drawWifiArc(canvas, wx, wy, WIFI_MID_RADIUS * m, k, visual.wifiMidOpacity)
        drawWifiArc(canvas, wx, wy, WIFI_OUTER_RADIUS * m, k, visual.wifiOuterOpacity)
    }

    /**
     * Draws the live-event artwork in the event slot. The Rive scene owns the motion (alarm bounce,
     * flashlight press); this fallback owns only the same final geometry, so an event never leaves
     * the middle slot blank when the native renderer is unavailable.
     */
    private fun drawEventIcon(canvas: Canvas, cx: Float, cy: Float, k: Float) {
        val color = withAlpha(visual.fgColor, 1f)
        canvas.save()
        canvas.translate(
            cx + EVENT_SLOT_X * k,
            cy + (visual.middleY + EVENT_SLOT_Y * visual.middleScale) * k
        )
        val eventScale = if (visual.eventType == LiveEventType.ALARM.riveId) {
            EVENT_ICON_SCALE
        } else {
            1f
        }
        canvas.scale(
            k * visual.middleScale * eventScale,
            k * visual.middleScale * eventScale
        )
        val isFlashlight = visual.eventType == LiveEventType.FLASHLIGHT.riveId
        if (isFlashlight) {
            canvas.save()
            canvas.scale(2.321f, 2.321f)
            val progress = ((SystemClock.uptimeMillis() - flashlightStartedAt).toFloat() /
                FLASHLIGHT_RAMP_MS).coerceIn(0f, 1f)
            eventPaint.color = android.graphics.Color.argb(
                (0x66 * progress).toInt(), 255, 255, 255
            )
            canvas.drawPath(flashlightBeamPath, eventPaint)
        }
        if (visual.eventType == LiveEventType.ALARM.riveId) {
            drawAlarm(canvas, color)
            canvas.restore()
            return
        }
        if (visual.eventType == LiveEventType.SCREENSHOT.riveId) {
            drawScreenshot(canvas, color)
            canvas.restore()
            return
        }
        val path = when (visual.eventType) {
            LiveEventType.FLASHLIGHT.riveId -> flashlightPath
            else -> {
                canvas.restore()
                return
            }
        }
        eventPaint.color = color
        canvas.drawPath(path, eventPaint)
        if (isFlashlight) {
            val progress = ((SystemClock.uptimeMillis() - flashlightStartedAt).toFloat() /
                FLASHLIGHT_RAMP_MS).coerceIn(0f, 1f)
            eventPaint.color = 0xFF121A2A.toInt()
            eventStrokePaint.color = 0xFF121A2A.toInt()
            eventStrokePaint.strokeWidth = 1.2f
            canvas.drawLine(-5.7f, -7.3f, 5.7f, -7.3f, eventStrokePaint)
            eventStrokePaint.strokeWidth = 3f
            val track = RectF(-1.6f, -2.1f, 1.6f, 3.5f)
            canvas.drawRoundRect(track, 1.6f, 1.6f, eventPaint)
            eventPaint.color = android.graphics.Color.argb(
                ((1f - progress) * 255).toInt(), 255, 184, 107
            )
            canvas.drawCircle(0f, 2.2f - 3.0f * progress, 0.9f, eventPaint)
            eventPaint.color = android.graphics.Color.argb(
                (progress * 255).toInt(), 255, 255, 255
            )
            canvas.drawCircle(0f, 2.2f - 3.0f * progress, 0.9f, eventPaint)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawWifiArc(canvas: Canvas, x: Float, y: Float, radius: Float, k: Float, opacity: Float) {
        if (opacity <= 0f) return
        arcBounds.set(x - radius * k, y - radius * k, x + radius * k, y + radius * k)
        wifiPaint.color = withAlpha(visual.fgColor, opacity)
        canvas.drawArc(arcBounds, WIFI_ARC_START, WIFI_ARC_SWEEP, false, wifiPaint)
    }

    /** The Do Not Disturb crescent, scaled by [scale] around [x], [y]. */
    private fun drawMoon(canvas: Canvas, x: Float, y: Float, scale: Float, opacity: Float) {
        moonPaint.color = withAlpha(visual.fgColor, opacity)
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        canvas.drawPath(moonPath, moonPaint)
        canvas.restore()
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF) * factor
        return ((alpha.toInt().coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
    }

    private companion object {
        const val TAG = "DuoSB"
        const val TRIM_ORIGIN = 270f  // Rive fraction 0 == 12 o'clock == canvas 270 degrees
        const val MIN_SWEEP = 0.5f
        const val BOLT_SCALE = 0.55f
        const val FLASHLIGHT_RAMP_MS = 500L
        val boltPath = Path().apply {
            moveTo(0.58f, 0.02f); lineTo(0.24f, 0.56f); lineTo(0.45f, 0.56f)
            lineTo(0.36f, 0.98f); lineTo(0.76f, 0.40f); lineTo(0.53f, 0.40f)
            close()
        }

        /** Middle slot, design units below the ring centre — the Wi-Fi centre the moon replaces. */
        const val DND_SLOT_Y = 17f

        /**
         * The Wi-Fi glyph, copied from `scene.rml` (`wifiLayer1`, `wifiLayer2`, `wifiDot`) so the
         * fallback and the live element occupy the same pixels. Trim 0.2394 of the circumference is
         * 86.2°, centred on 12 o'clock, which is canvas 226.9° clockwise.
         */
        const val WIFI_CENTER_X = -0.5f
        const val WIFI_SLOT_Y = 17f
        const val WIFI_OUTER_RADIUS = 31.1f
        const val WIFI_MID_RADIUS = 18.15f
        const val WIFI_STROKE = 7.1f
        const val WIFI_ARC_START = 226.9f
        const val WIFI_ARC_SWEEP = 86.2f
        const val WIFI_DOT_RADIUS = 5.5f
        const val WIFI_DOT_LIFT = 3.2f

        /** The live-event slot, relative to the ring centre, matching Rive's eventGroup. */
        const val EVENT_SLOT_X = -0.5f
        /** Matches Rive's eventGroup local Y, inside the scaled middle/event slot. */
        const val EVENT_SLOT_Y = 17f

        /** Alarm artwork is authored at 2x; screenshot/flashlight paths are already final-size. */
        const val EVENT_ICON_SCALE = 2.0f

        /** Alarm silhouette from `scene.rml` (`eventAlarmIcon`). */
        val alarmPath = Path().apply {
            moveTo(-8f, -2f); lineTo(-6f, -8f); lineTo(-2f, -10f); lineTo(2f, -10f)
            lineTo(6f, -8f); lineTo(8f, -2f); lineTo(9f, 4f); lineTo(8f, 8f)
            lineTo(4f, 10f); lineTo(-4f, 10f); lineTo(-8f, 8f); lineTo(-9f, 4f)
            close()
        }

        /** Owned vertical torch matching the QS affordance: cap bar, shoulders, handle, switch. */
        val flashlightPath = Path().apply {
            moveTo(-4.6f, -11f); lineTo(4.6f, -11f); lineTo(4.6f, -7.3f); lineTo(5.7f, -7.3f)
            lineTo(3.2f, -3.1f); lineTo(3.2f, 9f)
            cubicTo(3.2f, 10.1f, 2.5f, 11f, 1.4f, 11f)
            cubicTo(0.5f, 11f, 0f, 11f, -1.4f, 11f)
            cubicTo(-2.5f, 11f, -3.2f, 10.1f, -3.2f, 9f)
            lineTo(-3.2f, -3.1f); lineTo(-5.7f, -7.3f); lineTo(-4.6f, -7.3f)
            close()
        }
        /** Cone of light from the cap: narrow at the mouth, wider at the top. */
        val flashlightBeamPath = Path().apply {
            moveTo(-3f, -11f); lineTo(3f, -11f); lineTo(12f, -17f); lineTo(-12f, -17f)
            close()
        }
        val flashlightBeamLeft = Path().apply { moveTo(-3f, -11f); lineTo(-10f, -25f) }
        val flashlightBeamRight = Path().apply { moveTo(3f, -11f); lineTo(10f, -25f) }

        /** Outline camera body; the lens is a separate stroked circle in `drawEventIcon`. */
        val screenshotBodyPath = Path().apply {
            moveTo(-19f, -9f); lineTo(-7f, -9f); lineTo(-4f, -13f); lineTo(4f, -13f)
            lineTo(7f, -9f); lineTo(19f, -9f); lineTo(19f, 11f); lineTo(-19f, 11f)
            close()
        }

        /** The cellular/clock label's centre, matching the Rive text node (group-relative y 1). */
        const val NETWORK_SLOT_Y = 1f
        const val NETWORK_FONT_SIZE = 30f
        /** The Rive `network` Text's width; a longer clock shrinks to this, "5G" does not. */
        const val NETWORK_MAX_WIDTH = 80f

        /**
         * The DND crescent, generated from the device's own `drawable/stat_sys_dnd` by
         * `tools/dnd-moon-to-rive.py --android`. Same geometry as the Rive path, so the fallback and
         * the real element cannot disagree (this project does not ship a second, hand-drawn moon).
         */
        val moonPath = Path().apply {
            moveTo(-1.839f, -16.341f)
            cubicTo(-1.539f, -16.791f, -1.509f, -17.361f, -1.809f, -17.841f)
            cubicTo(-2.109f, -18.291f, -2.649f, -18.531f, -3.189f, -18.441f)
            cubicTo(-11.859f, -16.881f, -18.459f, -9.291f, -18.459f, -0.141f)
            cubicTo(-18.459f, 10.119f, -10.119f, 18.459f, 0.141f, 18.459f)
            cubicTo(9.291f, 18.459f, 16.881f, 11.859f, 18.441f, 3.159f)
            cubicTo(18.531f, 2.649f, 18.291f, 2.079f, 17.841f, 1.809f)
            cubicTo(17.361f, 1.509f, 16.791f, 1.509f, 16.341f, 1.839f)
            cubicTo(14.211f, 3.369f, 11.601f, 4.239f, 8.751f, 4.239f)
            cubicTo(1.551f, 4.239f, -4.269f, -1.581f, -4.269f, -8.781f)
            cubicTo(-4.269f, -11.601f, -3.369f, -14.181f, -1.869f, -16.341f)
            cubicTo(-1.869f, -16.341f, -1.839f, -16.341f, -1.839f, -16.341f)
            close()
        }
    }
}
