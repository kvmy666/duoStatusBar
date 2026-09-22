package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.integration.AutoExpand

/**
 * The element's own tap gestures (FR-05/18), handed off to Auto Expand - this module never implements
 * the actions itself, so the two modules cannot disagree about what a tap means.
 *
 * The detector is driven from the status-bar touch hook ([handle]), not the injected view, because the
 * status bar swallows touches before they reach that view. The view is left clickable so Auto Expand's
 * edge zones recognise it and yield; it gets no touch listener of its own, so an event is never handled
 * twice. With every action set to `no_action` the detector is dropped and the view is not clickable, so
 * touches fall straight through to whoever handled them before.
 */
internal class ElementGestures(private val context: Context) {

    private var detector: GestureDetector? = null
    private var active = false

    /** Installs (or clears) the detector for the current actions. */
    fun install(view: View, tap: String, doubleTap: String, longPress: String) {
        if (tap == AutoExpand.NO_ACTION && doubleTap == AutoExpand.NO_ACTION && longPress == AutoExpand.NO_ACTION) {
            detector = null
            view.setOnTouchListener(null)
            view.isClickable = false
            return
        }
        detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            // `onDown` must claim the gesture, or the detector never tracks it and no tap is ever
            // confirmed.
            override fun onDown(e: MotionEvent): Boolean = true
            // `onSingleTapConfirmed`, not `onSingleTapUp`: the latter fires on the FIRST tap of a
            // double-tap, so a double-tap ran the single-tap action as well (user-reported: a double
            // tap meant for power saver also toggled Wi-Fi and switched the slot to the 4G label).
            // The confirmed callback only fires once the double-tap window has passed.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                L.i("gesture: single tap")
                return AutoExpand.request(context, tap)
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                L.i("gesture: double tap")
                return AutoExpand.request(context, doubleTap)
            }
            override fun onLongPress(e: MotionEvent) {
                L.i("gesture: long press")
                AutoExpand.request(context, longPress)
            }
        })
        view.setOnTouchListener(null)
        view.isClickable = true
        L.i("gestures on: tap=$tap doubleTap=$doubleTap longPress=$longPress (driven from the status-bar touch hook)")
    }

    /**
     * Feeds a status-bar touch to the detector when it lands on the element.
     *
     * Once a gesture starts on the element it keeps receiving the stream until UP/CANCEL, so a finger
     * that drifts is not dropped mid-gesture. A DOWN that is not on the element must not reach the
     * detector at all: feeding it would start a gesture whose UP is then skipped, so the detector would
     * sit on the press and fire a long press (power saving) on a tap somewhere else entirely.
     */
    fun handle(event: MotionEvent, elementView: View?): Boolean {
        val gestureDetector = detector ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val view = elementView ?: return false
                val location = IntArray(2)
                try {
                    view.getLocationOnScreen(location)
                } catch (_: Throwable) {
                    return false
                }
                val left = location[0]
                val top = location[1]
                active = event.rawX >= left && event.rawX <= left + view.width &&
                    event.rawY >= top && event.rawY <= top + view.height
                if (!active) return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!active) return false
                active = false
            }
            else -> if (!active) return false
        }
        return try {
            gestureDetector.onTouchEvent(event)
        } catch (t: Throwable) {
            L.w("element touch: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }
}
