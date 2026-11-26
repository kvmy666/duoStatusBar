package io.github.kvmy666.duostatusbar.hook

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8: `XposedBridge.hookAllMethods` only walks the methods a class *declares*, never a superclass.
 * The vendor status icon containers declare `onViewAdded`/`onViewRemoved` and do not declare
 * `addView`, so the hook targets are resolved from the declared names instead of assumed.
 */
class IconContainerHooksTest {

    /** The vendor shape: both arrival methods, plus `addView` which is not a target. */
    private open class BothMethods {
        @Suppress("unused") fun onViewAdded(view: View) {}
        @Suppress("unused") fun onViewRemoved(view: View) {}
        @Suppress("unused") fun addView(view: View) {}
    }

    /** A ROM that only overrides the add path. */
    private class AddOnly {
        @Suppress("unused") fun onViewAdded(view: View) {}
    }

    /** A class that inherits the methods instead of declaring them. */
    private class InheritsBoth : BothMethods()

    /** A class with no arrival methods at all. */
    private class NoTargets

    @Test
    fun `the vendor shape declares onViewAdded and onViewRemoved, not addView`() {
        assertEquals(
            listOf("onViewAdded", "onViewRemoved"),
            IconContainerHooks.declaredTargets(BothMethods::class.java)
        )
    }

    @Test
    fun `only the declared arrival method is reported`() {
        assertEquals(listOf("onViewAdded"), IconContainerHooks.declaredTargets(AddOnly::class.java))
    }

    @Test
    fun `a class with no declared targets has none`() {
        assertTrue(IconContainerHooks.declaredTargets(NoTargets::class.java).isEmpty())
        assertTrue(IconContainerHooks.declaredTargets(InheritsBoth::class.java).isEmpty())
    }

    @Test
    fun `addView is never a target`() {
        assertFalse("addView" in IconContainerHooks.METHOD_NAMES)
    }
}
