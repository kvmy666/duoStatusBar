package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W1: the iOS mode gate and its own Rive breaker. The breaker must never share a counter with Duo —
 * an iOS death counted twice would otherwise block Duo on the next boot (and vice versa) — while the
 * stage gate must leave the OFF path byte-identical when the iOS column is false.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Mirrors the private constants: 2 processes may die, and the count stays armed for 2 minutes. */
    private val maxAttempts = 2
    private val crashWindowMs = 120_000L

    @Before
    fun clearBreakers() {
        Settings.Global.putInt(context.contentResolver, DuoGuard.KEY_ATTEMPTS, 0)
        Settings.Global.putLong(context.contentResolver, DuoGuard.KEY_ATTEMPT_AT, 0L)
        Settings.Global.putInt(context.contentResolver, IosGuard.KEY_ATTEMPTS, 0)
        Settings.Global.putLong(context.contentResolver, IosGuard.KEY_ATTEMPT_AT, 0L)
    }

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
    fun `iOS breaker keys differ from the Duo keys and there is no iOS stage key`() {
        assertNotEquals(DuoGuard.KEY_ATTEMPTS, IosGuard.KEY_ATTEMPTS)
        assertNotEquals(DuoGuard.KEY_ATTEMPT_AT, IosGuard.KEY_ATTEMPT_AT)
        assertNotEquals(DuoGuard.KEY_ATTEMPTS, IosGuard.KEY_ATTEMPT_AT)
        assertNotEquals(IosGuard.KEY_ATTEMPTS, DuoGuard.KEY_ATTEMPT_AT)
        assertNotEquals(DuoGuard.KEY_STAGE, IosGuard.KEY_ATTEMPTS)
        assertNotEquals(DuoGuard.KEY_STAGE, IosGuard.KEY_ATTEMPT_AT)
    }

    @Test
    fun `a dead Duo breaker does not block iOS and a dead iOS breaker does not block Duo`() {
        putDuoAttempts(maxAttempts)
        putIosAttempts(0)

        assertTrue(IosGuard(context).riveAllowed())
        assertFalse(DuoGuard(context).riveAllowed())

        putIosAttempts(maxAttempts)
        assertFalse(IosGuard(context).riveAllowed())
        assertFalse(DuoGuard(context).riveAllowed())

        IosGuard(context).clearRiveAttempts()
        assertTrue(IosGuard(context).riveAllowed())
    }

    @Test
    fun `a stale iOS count is allowed and cleared`() {
        putIosAttempts(maxAttempts, ageMs = crashWindowMs + 1_000L)

        val guard = IosGuard(context)
        assertTrue(guard.riveAllowed())
        assertEquals(0, guard.attempts())
    }

    @Test
    fun `noteRiveAttempt counts once per instance and once more for a new instance`() {
        val first = IosGuard(context)
        first.noteRiveAttempt()
        first.noteRiveAttempt()
        assertEquals(1, first.attempts())

        val second = IosGuard(context)
        second.noteRiveAttempt()
        assertEquals(2, second.attempts())
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

    /** A dead Duo breaker: full count and a fresh timestamp. */
    private fun putDuoAttempts(value: Int) {
        Settings.Global.putInt(context.contentResolver, DuoGuard.KEY_ATTEMPTS, value)
        Settings.Global.putLong(context.contentResolver, DuoGuard.KEY_ATTEMPT_AT, System.currentTimeMillis())
    }

    private fun putIosAttempts(value: Int, ageMs: Long = 0L) {
        Settings.Global.putInt(context.contentResolver, IosGuard.KEY_ATTEMPTS, value)
        Settings.Global.putLong(context.contentResolver, IosGuard.KEY_ATTEMPT_AT, System.currentTimeMillis() - ageMs)
    }
}
