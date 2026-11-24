package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W1: the iOS mode gate. The stage gate must leave the OFF path byte-identical when the iOS column is
 * false, and [IosGuard.active] must honour the adb override and the drawing-ready seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosGuardTest {

    @Test
    fun `iOS mode forces Duo off for every enabled and engine combination`() {
        for (enabled in listOf(false, true)) {
            for (engine in listOf(DuoPrefs.ENGINE_CANVAS, DuoPrefs.ENGINE_AUTO, DuoPrefs.ENGINE_RIVE)) {
                for (useRive in listOf(false, true)) {
                    assertEquals(
                        DuoGuard.OFF,
                        DuoGuard.stageFor(enabled, iosMode = true, engineMode = engine, useRive = useRive)
                    )
                }
            }
        }
    }

    @Test
    fun `iOS mode off reproduces the old stage matrix`() {
        assertEquals(
            DuoGuard.OFF,
            DuoGuard.stageFor(enabled = false, iosMode = false, engineMode = DuoPrefs.ENGINE_RIVE, useRive = true)
        )
        assertEquals(
            DuoGuard.ICONS_ONLY,
            DuoGuard.stageFor(enabled = true, iosMode = false, engineMode = DuoPrefs.ENGINE_CANVAS, useRive = false)
        )
        assertEquals(
            DuoGuard.ICONS_ONLY,
            DuoGuard.stageFor(enabled = true, iosMode = false, engineMode = DuoPrefs.ENGINE_AUTO, useRive = false)
        )
        assertEquals(
            DuoGuard.ICONS_ONLY,
            DuoGuard.stageFor(enabled = true, iosMode = false, engineMode = DuoPrefs.ENGINE_CANVAS, useRive = true)
        )
        assertEquals(
            DuoGuard.RIVE,
            DuoGuard.stageFor(enabled = true, iosMode = false, engineMode = DuoPrefs.ENGINE_AUTO, useRive = true)
        )
        assertEquals(
            DuoGuard.RIVE,
            DuoGuard.stageFor(enabled = true, iosMode = false, engineMode = DuoPrefs.ENGINE_RIVE, useRive = true)
        )
    }

    @Test
    fun `iOS active gate honours the adb override`() {
        assertTrue(IosGuard.active(iosMode = true, adbOverride = null, drawingReady = true))
        assertFalse(IosGuard.active(iosMode = true, adbOverride = null, drawingReady = false))
        assertFalse(IosGuard.active(iosMode = false, adbOverride = null, drawingReady = true))
        for (override in listOf(DuoGuard.OFF, DuoGuard.ICONS_ONLY, DuoGuard.RIVE)) {
            assertFalse(IosGuard.active(true, override, drawingReady = true))
        }
        // The real scene/renderers ship in this build, so the default availability is true.
        assertTrue(IosGuard.active(iosMode = true, adbOverride = null))
    }

    @Test
    fun `a stale ios mode cannot disable duo while drawing is not ready`() {
        assertFalse(DuoGuard.effectiveIosMode(iosMode = true, drawingReady = false))
        assertTrue(DuoGuard.effectiveIosMode(iosMode = true, drawingReady = true))
        assertFalse(DuoGuard.effectiveIosMode(iosMode = false, drawingReady = true))
        assertEquals(
            DuoGuard.RIVE,
            DuoGuard.stageFor(
                enabled = true,
                iosMode = DuoGuard.effectiveIosMode(true, false),
                engineMode = DuoPrefs.ENGINE_AUTO,
                useRive = true
            )
        )
        assertEquals(
            DuoGuard.OFF,
            DuoGuard.stageFor(
                enabled = true,
                iosMode = DuoGuard.effectiveIosMode(true, true),
                engineMode = DuoPrefs.ENGINE_AUTO,
                useRive = true
            )
        )
    }

    @Test
    fun `resolved stage ignores an unavailable ios mode`() {
        assertEquals(
            DuoGuard.RIVE,
            DuoGuard.resolvedStage(true, true, DuoPrefs.ENGINE_AUTO, true, drawingReady = false)
        )
        assertEquals(
            DuoGuard.OFF,
            DuoGuard.resolvedStage(true, true, DuoPrefs.ENGINE_AUTO, true, drawingReady = true)
        )
        assertEquals(
            DuoGuard.OFF,
            DuoGuard.resolvedStage(false, false, DuoPrefs.ENGINE_AUTO, true, drawingReady = false)
        )
    }
}
