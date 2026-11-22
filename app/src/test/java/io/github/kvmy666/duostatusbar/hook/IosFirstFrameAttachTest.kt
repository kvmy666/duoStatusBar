package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** R5: the shade/QS attach waits for the surface's first laid-out frame instead of rebuilding on stock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosFirstFrameAttachTest {

    private lateinit var activity: Activity
    private lateinit var root: FrameLayout

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        root = FrameLayout(activity)
        activity.setContentView(root)
    }

    /** A deterministic attach callback that counts every invocation. */
    private fun counting(calls: AtomicInteger, result: Boolean): () -> Boolean = {
        calls.incrementAndGet()
        result
    }

    /** A deterministic attach callback that counts invocations and then throws. */
    private fun throwing(calls: AtomicInteger): () -> Boolean = {
        calls.incrementAndGet()
        throw IllegalStateException("attach failed")
    }

    /** Adds an attached view with no box yet, the state in which the vendor hooks hand it over. */
    private fun unlaidOutView(): View {
        val view = View(activity)
        root.addView(view, FrameLayout.LayoutParams(120, 48))
        assertEquals("the view must wait for layout", 0, view.width)
        assertTrue("the activity root must be attached", view.isAttachedToWindow)
        return view
    }

    @Test
    fun `an already laid out view attaches immediately exactly once`() {
        val view = unlaidOutView()
        view.layout(0, 0, 120, 48)
        val calls = AtomicInteger()

        val accepted = IosFirstFrameAttach.whenLaidOut(view, counting(calls, true))

        assertTrue(accepted)
        assertEquals(1, calls.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("no second attach from a later pre-draw", 1, calls.get())
    }

    @Test
    fun `a zero-area view attaches on its first laid-out pre-draw only`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        assertTrue(IosFirstFrameAttach.whenLaidOut(view, counting(calls, true)))
        assertEquals("not called before layout", 0, calls.get())

        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
    }

    @Test
    fun `two failed attempts remove the listeners`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        assertTrue(IosFirstFrameAttach.whenLaidOut(view, counting(calls, false)))

        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(2, calls.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("the helper gives up after two attempts", 2, calls.get())
    }

    @Test
    fun `detaching a pending view cancels the scheduled attach`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        val observer = view.viewTreeObserver
        assertTrue(IosFirstFrameAttach.whenLaidOut(view, counting(calls, true)))

        root.removeView(view)
        observer.dispatchOnPreDraw()
        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("a detached view is never attached", 0, calls.get())
    }

    @Test
    fun `repeated scheduling on one view attaches once`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        assertTrue(IosFirstFrameAttach.whenLaidOut(view, counting(calls, true)))
        assertTrue(IosFirstFrameAttach.whenLaidOut(view, counting(calls, true)))

        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
    }

    @Test
    fun `a background call posts to the main thread and attaches once`() {
        val view = unlaidOutView()
        view.layout(0, 0, 120, 48)
        val calls = AtomicInteger()
        val accepted = AtomicBoolean(false)
        val worker = Thread {
            accepted.set(IosFirstFrameAttach.whenLaidOut(view, counting(calls, true)))
        }
        worker.start()
        worker.join()

        assertTrue("the surface stays accepted while the post is pending", accepted.get())
        assertEquals("nothing runs on the worker thread", 0, calls.get())

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls.get())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("no second attach from a later idle", 1, calls.get())
    }

    @Test
    fun `a posted hand-over that cannot attach refuses on the main thread`() {
        val view = unlaidOutView()
        view.layout(0, 0, 120, 48)
        val calls = AtomicInteger()
        val refused = AtomicInteger()
        val accepted = AtomicBoolean(false)
        val worker = Thread {
            accepted.set(
                IosFirstFrameAttach.whenLaidOut(
                    view,
                    counting(calls, false),
                    onRefused = { refused.incrementAndGet() }
                )
            )
        }
        worker.start()
        worker.join()

        assertTrue("the post is accepted for IOS routing", accepted.get())
        assertEquals("nothing runs on the worker thread", 0, calls.get())
        assertEquals(0, refused.get())

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls.get())
        assertEquals(1, refused.get())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("refused exactly once", 1, refused.get())
    }

    @Test
    fun `a scheduled give-up runs the refusal callback exactly once`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        val refused = AtomicInteger()
        assertTrue(
            IosFirstFrameAttach.whenLaidOut(
                view,
                counting(calls, false),
                onRefused = { refused.incrementAndGet() }
            )
        )

        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
        assertEquals("not refused before the attempt is exhausted", 0, refused.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(2, calls.get())
        assertEquals(1, refused.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("refused exactly once", 1, refused.get())
    }

    @Test
    fun `a successful scheduled attach never runs the refusal callback`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        val refused = AtomicInteger()
        assertTrue(
            IosFirstFrameAttach.whenLaidOut(
                view,
                counting(calls, true),
                onRefused = { refused.incrementAndGet() }
            )
        )

        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
        assertEquals(0, refused.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0, refused.get())
    }

    @Test
    fun `a throwing scheduled attach runs the refusal callback exactly once`() {
        val view = unlaidOutView()
        val calls = AtomicInteger()
        val refused = AtomicInteger()
        assertTrue(
            IosFirstFrameAttach.whenLaidOut(
                view,
                throwing(calls),
                onRefused = { refused.incrementAndGet() }
            )
        )

        view.layout(0, 0, 120, 48)
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, calls.get())
        assertEquals(1, refused.get())
        view.viewTreeObserver.dispatchOnPreDraw()
        assertEquals("no retry after a throwing attach", 1, calls.get())
        assertEquals("refused exactly once", 1, refused.get())
    }
}
