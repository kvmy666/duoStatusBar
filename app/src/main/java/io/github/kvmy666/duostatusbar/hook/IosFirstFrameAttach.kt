package io.github.kvmy666.duostatusbar.hook

import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import io.github.kvmy666.duostatusbar.L

/** A second frame covers a strip whose records only become resolvable after the first layout. */
private const val MAX_ATTEMPTS = 2

/** One pending attempt per view; entries are removed when the attempt completes or the view detaches. */
private val pendingAttempts = HashMap<View, Attempt>()

/**
 * Runs one iOS attach on the first frame where a stock view is actually laid out.
 *
 * The vendor QS hooks fire while the header is being constructed, before its cluster has a box. An
 * `IosReplaceHost.attachCluster` at that point finds no replaceable record, tears the host down and
 * leaves the stock drawing to render first (R5 owner report: two `stock icons restored` lines before
 * the first `replace host attached`). Deferring the same attach to the view's first laid-out pre-draw
 * makes the first drawn frame the iOS one without re-creating hosts.
 *
 * Lifecycle:
 * - a view that is already laid out attaches immediately;
 * - otherwise an [ViewTreeObserver.OnPreDrawListener] and a [View.OnAttachStateChangeListener] wait;
 * - the listeners are removed on the first successful attach, after [MAX_ATTEMPTS] unsuccessful
 *   pre-draws, or when the view detaches. A detached pending view is left to the existing fallback
 *   path and is never retried here;
 * - scheduling the same view twice while the first attempt is pending keeps the first callback only.
 *
 * A hand-over has three outcomes: it attaches now, it is scheduled (iOS owns it from then on), or it
 * could not be scheduled and returns false. A scheduled attempt that later gives up invokes the
 * optional refusal callback once, so the caller can hand the surface to Duo instead of leaving the
 * stock drawing visible; a detached view never refuses because its surface no longer exists. A
 * worker-thread hand-over is posted, and if that posted call cannot attach or schedule it refuses on
 * the main thread instead of losing the callback.
 *
 * The pre-draw listener always returns true: this helper must never cancel a frame. Once scheduled,
 * the draw path only checks the view's size/attachment; logging and listener removal happen only
 * when the attempt starts or ends.
 *
 * The helper only touches the view tree on the main thread: a call from another thread posts itself
 * to the main looper and reports the surface as scheduled, because the vendor hooks may run there.
 */
internal object IosFirstFrameAttach {

    private val logOnce = LogOnce()

    /** True when [view] has a real box and is in a window, the point where an attach is safe. */
    internal fun isLaidOut(view: View): Boolean =
        view.width > 0 && view.height > 0 && view.isAttachedToWindow

    /**
     * Runs [attach] now when [view] is laid out, otherwise on its next laid-out pre-draw (at most
     * [MAX_ATTEMPTS] tries).
     *
     * The return value says whether iOS owns the surface: true for an immediate success or a
     * scheduled attempt, false when nothing could be scheduled (the caller keeps its existing path).
     * [onRefused] runs exactly once only when a *scheduled* attempt gives up (two failed pre-draws or
     * an attach that throws), or when a posted call cannot attach or schedule at all; the caller may
     * then hand the surface to Duo. It is never called for a successful attach, a detach or a failed
     * post.
     *
     * The view tree is only touched on the main thread: any other caller is re-posted to the main
     * looper and reported as scheduled.
     */
    fun whenLaidOut(
        view: View,
        attach: () -> Boolean,
        onRefused: (() -> Unit)? = null
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return postToMain(view, attach, onRefused)
        }
        return try {
            if (isPending(view)) {
                // A second hand-over for the same view must not attach twice; the pending attempt wins.
                true
            } else if (isLaidOut(view)) {
                val attached = attach()
                if (attached) logAttached(logOnce, view)
                attached
            } else {
                schedule(view, attach, onRefused)
            }
        } catch (t: Throwable) {
            logOnce.once("ios-first-frame:error") {
                L.w("iOS first-frame attach: ${t.javaClass.simpleName}: ${t.message}")
            }
            false
        }
    }

    /**
     * Switches a worker-thread hand-over to the main looper. The surface is accepted while the post
     * is pending; only a post that cannot be queued returns false. If the posted call cannot attach
     * or schedule, [onRefused] runs on the main thread, because nobody sees that false return. If
     * [view.post] itself fails, this returns false and the caller routes Duo immediately.
     */
    private fun postToMain(
        view: View,
        attach: () -> Boolean,
        onRefused: (() -> Unit)?
    ): Boolean = try {
        val posted = view.post {
            if (!whenLaidOut(view, attach, onRefused)) runRefusal(onRefused)
        }
        if (!posted) {
            logOnce.once("ios-first-frame:post-failed") {
                L.w("iOS first-frame attach: could not post to the main thread")
            }
        }
        posted
    } catch (t: Throwable) {
        logOnce.once("ios-first-frame:post-failed") {
            L.w("iOS first-frame attach: ${t.javaClass.simpleName}: ${t.message}")
        }
        false
    }

    /** Runs the refusal callback once for a posted call that could not attach or schedule. */
    private fun runRefusal(onRefused: (() -> Unit)?) {
        val callback = onRefused ?: return
        try {
            callback()
        } catch (t: Throwable) {
            logOnce.once("ios-first-frame:posted-refusal-failed") {
                L.w("iOS first-frame attach: posted refusal failed " +
                    "(${t.javaClass.simpleName}: ${t.message})")
            }
        }
    }

    private fun isPending(view: View): Boolean =
        synchronized(pendingAttempts) { pendingAttempts.containsKey(view) }

    private fun schedule(view: View, attach: () -> Boolean, onRefused: (() -> Unit)?): Boolean {
        val observer = try {
            view.viewTreeObserver
        } catch (t: Throwable) {
            null
        }
        if (observer == null || !observer.isAlive) {
            giveUp(view, "no live observer")
            return false
        }
        val attempt = Attempt(view, observer, attach, onRefused, logOnce)
        synchronized(pendingAttempts) {
            if (pendingAttempts.containsKey(view)) return true
            pendingAttempts[view] = attempt
        }
        val registered = try {
            observer.addOnPreDrawListener(attempt)
            view.addOnAttachStateChangeListener(attempt)
            true
        } catch (t: Throwable) {
            attempt.release(view)
            false
        }
        if (!registered) {
            giveUp(view, "listener registration failed")
            return false
        }
        logOnce.once(scheduledKey(view)) {
            L.i("iOS first-frame attach: scheduled ${view.javaClass.name}")
        }
        return true
    }

    private fun giveUp(view: View, reason: String) {
        logOnce.once(gaveUpKey(view)) {
            L.w("iOS first-frame attach: gave up ($reason) ${view.javaClass.name}")
        }
    }
}

/**
 * The pending listeners for one view. The attempt is short-lived (two frames at most) and removes
 * itself from [pendingAttempts] on attach, on a give-up and on detach. [onRefused] runs once on a
 * give-up, after the listeners are gone, so a caller can hand the surface to Duo.
 */
private class Attempt(
    private val view: View,
    private val observer: ViewTreeObserver,
    private val attach: () -> Boolean,
    private val onRefused: (() -> Unit)?,
    private val logOnce: LogOnce
) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {

    private var attempts = 0
    private var refused = false

    override fun onPreDraw(): Boolean {
        try {
            if (!IosFirstFrameAttach.isLaidOut(view)) return true
            attempts++
            val attached = try {
                attach()
            } catch (t: Throwable) {
                logOnce.once("ios-first-frame:attach-failed") {
                    L.w("iOS first-frame attach: ${t.javaClass.simpleName}: ${t.message}")
                }
                // A throwing attach is not retried: stop trying and let the caller route to Duo.
                refuse(view)
                return true
            }
            if (attached) {
                logAttached(logOnce, view)
                release(view)
            } else if (attempts >= MAX_ATTEMPTS) {
                logOnce.once(gaveUpKey(view)) {
                    L.w("iOS first-frame attach: gave up after $MAX_ATTEMPTS attempts ${view.javaClass.name}")
                }
                refuse(view)
            }
        } catch (t: Throwable) {
            // The frame still goes through; refuse so the caller can fall back to Duo.
            logOnce.once("ios-first-frame:pre-draw-failed") {
                L.w("iOS first-frame attach: ${t.javaClass.simpleName}: ${t.message}")
            }
            refuse(view)
        }
        return true
    }

    override fun onViewAttachedToWindow(v: View) = Unit

    override fun onViewDetachedFromWindow(v: View) {
        // A detached header has no surface left to hand over; never run the refusal for it.
        release(v)
    }

    /**
     * Detaches both listeners and drops the pending entry. Idempotent: called by a successful
     * attach, by a give-up, by a detach and by a failed registration.
     */
    fun release(target: View) {
        try {
            if (observer.isAlive) observer.removeOnPreDrawListener(this)
        } catch (_: Throwable) {
        }
        try {
            target.removeOnAttachStateChangeListener(this)
        } catch (_: Throwable) {
        }
        synchronized(pendingAttempts) {
            if (pendingAttempts[target] === this) pendingAttempts.remove(target)
        }
    }

    /** Releases the attempt, then tells the caller once so it can attach Duo instead. */
    private fun refuse(target: View) {
        release(target)
        if (refused) return
        refused = true
        val callback = onRefused ?: return
        try {
            callback()
        } catch (t: Throwable) {
            logOnce.once("ios-first-frame:refused-failed") {
                L.w("iOS first-frame attach: refusal callback failed " +
                    "(${t.javaClass.simpleName}: ${t.message})")
            }
        }
    }
}

private fun logAttached(logOnce: LogOnce, view: View) {
    logOnce.once(attachedKey(view)) {
        L.i("iOS first-frame attach: attached ${view.javaClass.name}")
    }
}

private fun scheduledKey(view: View): String = "ios-first-frame:scheduled:${view.javaClass.name}"

private fun attachedKey(view: View): String = "ios-first-frame:attached:${view.javaClass.name}"

private fun gaveUpKey(view: View): String = "ios-first-frame:gave-up:${view.javaClass.name}"
