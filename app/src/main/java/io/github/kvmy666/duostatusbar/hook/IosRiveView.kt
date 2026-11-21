package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.ViewModelInstance
import app.rive.runtime.kotlin.renderers.Renderer
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RiveInit

/**
 * The iOS glyph as a real Rive surface over `ios.riv`.
 *
 * Lifecycle is deliberately the proven Duo one:
 * * [RiveInit.ensure] loads the native runtime by absolute path — never the library initialiser (C1).
 * * The builder pins the renderer type, artboard and state machine; `start()` returns true once the
 *   runtime and asset are usable, **before** the view model instance exists (the host needs
 *   [canDraw] at that point, and a Rive instance binds only after attach).
 * * The state machine is started explicitly, idle animations loop (M1), and the renderer is paused
 *   3 s after the last change (C8). The host owns the bounds and never resizes this view (C2).
 * * Writes go through [IosBinder] and its lock, so they serialize with `advance`.
 *
 * Fail silent: if the runtime, asset or instance never becomes usable, the view falls back to an
 * internal [IosCanvasView] child instead of leaving the suppressed stock slot blank (C9/rule 5).
 *
 * The Canvas child is created as a *preview* in [start], after the Rive view so it paints on top. It is
 * created before the host suppresses a stock icon, and the first [render] snapshot is drawn into it in
 * the same UI turn, before the first frame; the Rive builder's default artboard is therefore never
 * shown after suppression. On the first bind the preview is removed and Rive takes over; on a bind
 * failure the same preview becomes the permanent Canvas fallback (never a second child). If the preview
 * itself fails, [canDraw] stays false until the view-model instance binds, so the host cannot suppress
 * an icon over the bare default artboard. The preview is not unit-tested: [start] needs the Rive
 * runtime, which does not load on the JVM ([IosRenderersTest]); the drawing it hosts is proven by the
 * Canvas renderer tests.
 */
internal class IosRiveView(
    context: Context,
    val kind: IosIconKind
) : FrameLayout(context), IosElement {

    private var rive: RiveAnimationView? = null
    private var viewModelInstance: ViewModelInstance? = null
    private var binding: IosBinding? = null
    private var fallback: IosCanvasView? = null
    private var preview: IosCanvasView? = null

    private var started = false
    private var built = false
    private var polls = 0
    private var renderActive = true
    private var pendingVisual: IosVisual? = null
    private var lastVisual: IosVisual? = null

    private val handler = Handler(Looper.getMainLooper())

    private val idleStop = Runnable {
        try {
            val renderer = rive?.artboardRenderer
            if (renderer?.isPlaying == true) {
                renderer.stop()
                L.i("iOS Rive renderer paused (idle)")
            }
        } catch (t: Throwable) {
            L.w("iOS idle stop: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override val ui: View get() = this

    override val rendererName: String
        get() = if (fallback != null || preview != null) "Canvas" else "Rive"

    /** True only when Rive itself is live; the survival timer clears the breaker on this. */
    override val isReady: Boolean get() = viewModelInstance != null && fallback == null

    /**
     * The host checks this before hiding a stock icon. True while the Canvas preview is drawing in
     * place of the unbound Rive artboard, once the Canvas fallback exists, or once the Rive view-model
     * instance is bound. `built` alone is not enough: a failed preview must not let the host suppress
     * the stock icon while only the Rive default artboard exists.
     */
    override val canDraw: Boolean get() = preview != null || fallback != null || (built && viewModelInstance != null)

    override fun start(): Boolean {
        if (started) return canDraw
        started = true
        return try {
            if (!RiveInit.ensure(context)) {
                L.e("iOS Rive runtime unavailable - not creating a Rive view")
                return false
            }
            val bytes = loadRiveBytes() ?: return false

            val builder = RiveAnimationView.Builder(context)
                .setRendererType(RendererType.Canvas)
                .setResource(bytes)
                .setArtboardName(artboardName())
                .setStateMachineName(STATE_MACHINE)
                .setFit(Fit.CONTAIN)
                .setAlignment(Alignment.CENTER)
                .setAutoplay(true)
                .setAutoBind(true)
                .setShouldLoadCDNAssets(false)
                .setTouchPassThrough(true)
            val view = IosGuardedRiveView(builder)
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            rive = view
            built = true
            startPreview()
            pollForInstance()
            true
        } catch (t: Throwable) {
            L.e("iOS Rive start failed: ${t.javaClass.simpleName}: ${t.message}")
            built = false
            false
        }
    }

    /**
     * Adds the Canvas preview after the Rive view, so it paints over the builder's default artboard.
     * The preview owns the visible drawing until the view-model instance binds (F1); the first [render]
     * snapshot is drawn into it before the first frame, so the default artboard is never shown after
     * the host suppresses the stock icon. It is never resized; it simply fills this container like the
     * Rive child (C2).
     */
    private fun startPreview() {
        try {
            val canvas = IosCanvasView(context, kind)
            if (!canvas.start()) return
            addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            preview = canvas
        } catch (t: Throwable) {
            L.w("iOS preview start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * The artboard is per glyph so each stock slot gets a tight `Fit.CONTAIN`; every artboard runs the
     * state machine `Ios` and shares the same 12 property names.
     */
    private fun artboardName(): String = when (kind) {
        IosIconKind.BATTERY -> "Ios"
        IosIconKind.WIFI -> "IosWifi"
        IosIconKind.CELL -> "IosCell"
    }

    private fun loadRiveBytes(): ByteArray? = try {
        val moduleContext = context.createPackageContext(MODULE_PACKAGE, 0)
        moduleContext.resources.openRawResource(R.raw.ios).use { it.readBytes() }
    } catch (t: Throwable) {
        L.e("iOS Rive asset unreadable: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    private fun pollForInstance() {
        postDelayed({
            if (!started || rive == null || fallback != null) return@postDelayed
            if (viewModelInstance != null) return@postDelayed
            try {
                val machine = rive?.stateMachines?.firstOrNull()
                val found = machine?.viewModelInstance
                if (found != null) {
                    viewModelInstance = found
                    binding = IosBinder.bind(found, rive?.file?.lock)
                    rive?.play(STATE_MACHINE, Loop.LOOP, Direction.AUTO, true, true)
                    renderActive = true
                    scheduleIdleStop()
                    L.i(
                        "iOS view ready kind=$kind (machines=${rive?.stateMachines?.size}, " +
                            "playing=${rive?.playingStateMachines?.size})"
                    )
                    val first = pendingVisual ?: lastVisual
                    pendingVisual = null
                    removePreview()
                    first?.let { render(it) }
                } else if (++polls < MAX_POLLS) {
                    pollForInstance()
                } else {
                    L.w("iOS no view model instance after $MAX_POLLS polls - using the Canvas fallback")
                    fallbackToCanvas()
                }
            } catch (t: Throwable) {
                L.w("iOS instance poll: ${t.javaClass.simpleName}: ${t.message}")
                if (++polls < MAX_POLLS) pollForInstance() else fallbackToCanvas()
            }
        }, POLL_MS)
    }

    /** Drops the preview once Rive is bound; the preview never coexists with a live Rive drawing. */
    private fun removePreview() {
        val canvas = preview ?: return
        preview = null
        try {
            removeView(canvas)
            canvas.teardown()
        } catch (t: Throwable) {
            L.w("iOS preview remove: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override fun render(v: IosVisual) {
        fallback?.let { it.render(v); return }
        val target = binding
        if (target == null) {
            // No instance yet: keep the last request for the bind and draw it now, so the suppressed
            // slot shows the real snapshot instead of the Rive builder's default artboard (F1).
            pendingVisual = v
            preview?.render(v)
            return
        }
        if (v == lastVisual) return
        lastVisual = v
        try {
            val failures = target.apply(v)
            if (failures > 0) {
                // A property that did not bind can make the Rive glyph show the wrong row (e.g. a
                // failed cellRole leaves the DATA bars in the OTHER slot). Never trust it: swap to the
                // Canvas glyph, which reads the immutable snapshot directly.
                L.w("iOS $failures of ${IosBinder.IOS_PROPERTY_COUNT} properties did not bind - Canvas fallback")
                fallbackToCanvas()
                fallback?.render(v)
                return
            }
            if (renderActive) {
                startRenderer()
                scheduleIdleStop()
            }
        } catch (t: Throwable) {
            L.e("iOS render failed: ${t.javaClass.simpleName}: ${t.message}")
            fallbackToCanvas()
            fallback?.render(v)
        }
    }

    override fun setRenderActive(active: Boolean) {
        renderActive = active
        preview?.setRenderActive(active)
        fallback?.setRenderActive(active)
        if (active) {
            if (binding != null) {
                startRenderer()
                scheduleIdleStop()
            }
        } else {
            handler.removeCallbacks(idleStop)
            try {
                rive?.artboardRenderer?.stop()
            } catch (t: Throwable) {
                L.w("iOS renderer stop: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun startRenderer() {
        try {
            rive?.artboardRenderer?.start()
        } catch (t: Throwable) {
            L.w("iOS renderer start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun scheduleIdleStop() {
        handler.removeCallbacks(idleStop)
        if (!renderActive) return
        handler.postDelayed(idleStop, IDLE_MS)
    }

    /**
     * Replaces the Rive drawing with the shared Canvas glyph. Called only after the host has already
     * suppressed the stock icon, so it must always produce something (C9).
     */
    private fun fallbackToCanvas() {
        if (fallback != null) return
        try {
            val preview = this.preview
            if (preview != null) {
                // The preview already paints the live snapshot; reuse it as the permanent fallback
                // rather than adding a second Canvas child (F1).
                fallback = preview
                this.preview = null
                handler.removeCallbacks(idleStop)
                val dead = rive
                if (dead != null) {
                    removeView(dead)
                    try {
                        dead.stop()
                    } catch (t: Throwable) {
                        L.w("iOS dead Rive stop: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
                rive = null
                viewModelInstance = null
                binding = null
                preview.setRenderActive(renderActive)
                val visual = lastVisual ?: pendingVisual
                pendingVisual = null
                visual?.let { preview.render(it) }
                return
            }
            val dead = rive
            val canvas = IosCanvasView(context, kind)
            if (!canvas.start()) {
                L.e("iOS Canvas fallback could not start")
                if (dead != null) removeView(dead)
                rive = null
                viewModelInstance = null
                binding = null
                // Report honestly: neither renderer can draw now (the host already suppressed the
                // stock icon; this path is unreachable while IosCanvasView.start() is infallible).
                started = false
                built = false
                return
            }
            canvas.setRenderActive(renderActive)
            addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            fallback = canvas
            handler.removeCallbacks(idleStop)
            if (dead != null) {
                removeView(dead)
                try {
                    dead.stop()
                } catch (t: Throwable) {
                    L.w("iOS dead Rive stop: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
            rive = null
            viewModelInstance = null
            binding = null
            lastVisual?.let { canvas.render(it) } ?: pendingVisual?.let { canvas.render(it) }
        } catch (t: Throwable) {
            L.e("iOS Canvas fallback failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override fun teardown() {
        try {
            handler.removeCallbacks(idleStop)
            rive?.stop()
            preview?.teardown()
            fallback?.teardown()
            removeAllViews()
        } catch (_: Throwable) {
        }
        rive = null
        preview = null
        fallback = null
        viewModelInstance = null
        binding = null
        pendingVisual = null
        lastVisual = null
        started = false
        built = false
        renderActive = false
        polls = 0
    }

    companion object {
        private const val MODULE_PACKAGE = "io.github.kvmy666.duostatusbar"
        private const val STATE_MACHINE = "Ios"
        private const val POLL_MS = 100L
        private const val MAX_POLLS = 25
        private const val IDLE_MS = 3_000L
    }
}

/**
 * A [RiveAnimationView] that waits for Rive's canvas worker before the Surface is released; the same
 * guard as `DuoRiveView`, copied because that one is private and Duo's file is frozen.
 */
private class IosGuardedRiveView(builder: RiveAnimationView.Builder) : RiveAnimationView(builder) {

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        awaitCanvasWorker()
        super.onSurfaceTextureAvailable(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        awaitCanvasWorker()
        return super.onSurfaceTextureDestroyed(surface)
    }

    private fun awaitCanvasWorker() {
        val current = renderer ?: return
        if (!current.hasCppObject) return
        if (!current.isAttached && !current.isPlaying) return
        current.isAttached = false
        try {
            current.stop()
        } catch (t: Throwable) {
            L.w("iOS rive stop before surface release: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            val gate = IosCanvasWorkerGate()
            gate.make()
            gate.delete()
        } catch (t: Throwable) {
            L.w("iOS rive worker drain: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}

/** Exists only so `Renderer.delete` can wait for the shared Canvas worker (see `DuoRiveView`). */
private class IosCanvasWorkerGate : Renderer(RendererType.Canvas, false) {
    override fun draw() {}
    override fun advance(elapsed: Float) {}
}
