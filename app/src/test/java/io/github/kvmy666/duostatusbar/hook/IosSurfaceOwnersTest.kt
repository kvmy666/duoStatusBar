package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IOS-R2 0a: exactly one iOS host owns a surface and a strip.
 *
 * The registry is process-wide, so this pins the identity rules directly: a second token is refused on
 * either key, release frees both, and different surfaces/strips do not collide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosSurfaceOwnersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosSurfaceOwners.clearForTest()
    }

    @After
    fun tearDown() {
        IosSurfaceOwners.clearForTest()
    }

    private fun strip(): View = FrameLayout(context)

    @Test
    fun `a second host on the same surface and strip is refused`() {
        val view = strip()
        val first = Any()
        val second = Any()
        assertTrue(IosSurfaceOwners.claim("main bar", view, first))
        assertFalse(IosSurfaceOwners.claim("main bar", view, second))
        assertEquals("main bar", IosSurfaceOwners.ownerOf(view))
    }

    @Test
    fun `the surface key alone and the strip key alone both refuse a second token`() {
        val view = strip()
        val other = strip()
        val first = Any()
        val second = Any()
        assertTrue(IosSurfaceOwners.claim("main bar", view, first))
        assertFalse("same surface, different strip", IosSurfaceOwners.claim("main bar", other, second))
        assertFalse("same strip, different surface", IosSurfaceOwners.claim("keyguard bar", view, second))
        assertNull("the refused strip stays unowned", IosSurfaceOwners.ownerOf(other))
    }

    @Test
    fun `release frees the surface and its strip, and is idempotent`() {
        val view = strip()
        val first = Any()
        val second = Any()
        assertTrue(IosSurfaceOwners.claim("main bar", view, first))
        IosSurfaceOwners.release("main bar", first)
        IosSurfaceOwners.release("main bar", first)
        assertNull(IosSurfaceOwners.ownerOf(view))
        assertTrue(IosSurfaceOwners.claim("main bar", view, second))
    }

    @Test
    fun `the same token may re-claim, and different surfaces and strips coexist`() {
        val first = strip()
        val second = strip()
        val third = strip()
        val host = Any()
        assertTrue(IosSurfaceOwners.claim("main bar", first, host))
        assertTrue("idempotent re-claim", IosSurfaceOwners.claim("main bar", first, host))
        assertTrue(IosSurfaceOwners.claim("shade header", second, host))
        assertTrue(IosSurfaceOwners.claim("keyguard bar", third, host))
        assertEquals("main bar", IosSurfaceOwners.ownerOf(first))
        assertEquals("shade header", IosSurfaceOwners.ownerOf(second))
        assertEquals("keyguard bar", IosSurfaceOwners.ownerOf(third))
    }
}
