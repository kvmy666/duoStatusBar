package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3A A6: the status-bar touch hook exists only while at least one configured action needs it
 * (ring gesture, indicator gesture or the download ring tap). No actions means no touch hook at all,
 * and turning the last action off unhooks exactly once.
 */
class GestureFeatureTest {

    private val noActions = ModuleSettings.DEFAULT.copy(
        enabled = true,
        downloadProgressEnabled = false,
        tapAction = "no_action",
        doubleTapAction = "no_action",
        longPressAction = "no_action",
        indicatorsTapAction = "no_action",
        indicatorsDoubleTapAction = "no_action",
        indicatorsLongPressAction = "no_action"
    )

    private fun set(settings: ModuleSettings): FeatureSet =
        featureSetFor(settings, adbOverride = null, iosDrawingReady = true, otherOrientationEnabled = false)

    private fun touch(settings: ModuleSettings): Boolean =
        DuoRegistration.STATUS_TOUCH in duoRegistrationsFor(set(settings))

    @Test
    fun `no configured action means no touch hook`() {
        assertFalse(touch(noActions))
        assertFalse(DuoFeature.RING_GESTURES in set(noActions).duo)
        assertFalse(DuoFeature.INDICATOR_GESTURES in set(noActions).duo)
    }

    @Test
    fun `any ring or indicator action installs the touch hook`() {
        val ringActions = listOf(
            noActions.copy(tapAction = "flashlight"),
            noActions.copy(doubleTapAction = "screenshot"),
            noActions.copy(longPressAction = "assistant")
        )
        for (settings in ringActions) assertTrue("ring action must install touch", touch(settings))

        val indicatorActions = listOf(
            noActions.copy(indicatorsTapAction = "flashlight"),
            noActions.copy(indicatorsDoubleTapAction = "screenshot"),
            noActions.copy(indicatorsLongPressAction = "assistant")
        )
        for (settings in indicatorActions) {
            assertTrue("indicator action must install touch", touch(settings))
        }
    }

    @Test
    fun `the download ring tap alone installs the touch hook`() {
        val download = noActions.copy(downloadProgressEnabled = true)
        assertTrue(DuoFeature.DOWNLOAD in set(download).duo)
        assertTrue(touch(download))
    }

    @Test
    fun `turning the last action off unhooks the touch hook exactly once`() {
        val uninstalls = HashMap<DuoRegistration, Int>()
        val registrations = RegistrationSet<DuoRegistration, DuoRegistration>(
            installer = { it },
            uninstaller = { uninstalls[it] = (uninstalls[it] ?: 0) + 1 }
        )
        val on = set(noActions.copy(tapAction = "flashlight"))
        val off = set(noActions)

        registrations.apply(duoRegistrationsFor(on))
        assertTrue(DuoRegistration.STATUS_TOUCH in registrations.installed())

        registrations.apply(duoRegistrationsFor(off))
        assertFalse(DuoRegistration.STATUS_TOUCH in registrations.installed())
        assertEquals(1, uninstalls[DuoRegistration.STATUS_TOUCH])

        registrations.apply(duoRegistrationsFor(off))
        assertEquals("a repeated off apply must not unhook twice", 1, uninstalls[DuoRegistration.STATUS_TOUCH])
    }

    @Test
    fun `iOS and none never install the touch hook`() {
        val withAction = noActions.copy(tapAction = "flashlight")
        val ios = featureSetFor(
            withAction.copy(iosMode = true), null, iosDrawingReady = true, otherOrientationEnabled = false
        )
        val off = featureSetFor(
            withAction.copy(enabled = false), null, iosDrawingReady = true, otherOrientationEnabled = false
        )
        assertFalse(DuoRegistration.STATUS_TOUCH in duoRegistrationsFor(ios))
        assertFalse(DuoRegistration.STATUS_TOUCH in duoRegistrationsFor(off))
    }
}
