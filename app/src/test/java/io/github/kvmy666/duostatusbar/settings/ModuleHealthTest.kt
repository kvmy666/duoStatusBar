package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The health decision is what tells apart the three "no report" lives a user can be in: never injected,
 * updated-but-not-restarted, and loaded-but-unable-to-report (the One UI 8 provider case). It is a pure
 * function so the rule is pinned off the phone — a wrong answer here hides the recovery button.
 */
class ModuleHealthTest {

    // A clock and a heartbeat the module would have written just now.
    private val now = 10_000_000L
    private val fresh = now - 1_000L

    @Test
    fun `never loaded when there is no load stamp`() {
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(0L, 1_000L, true))
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(0L, 0L, false))
    }

    @Test
    fun `needs a restart when the apk is newer than the last load`() {
        assertEquals(ModuleState.NEEDS_RESTART, ModuleHealthCheck.decide(100L, 200L, false, fresh, now))
        assertEquals(ModuleState.NEEDS_RESTART, ModuleHealthCheck.decide(100L, 200L, true, fresh, now))
    }

    @Test
    fun `not reporting when loaded after the update but no status arrived`() {
        assertEquals(ModuleState.NOT_REPORTING, ModuleHealthCheck.decide(200L, 100L, true, fresh, now))
    }

    @Test
    fun `ok when loaded after the update and reporting`() {
        assertEquals(ModuleState.OK, ModuleHealthCheck.decide(200L, 100L, false, fresh, now))
    }

    @Test
    fun `no heartbeat means the module is not running now`() {
        // The unscoped case: last_load is old and no heartbeat was ever written, so the stored status
        // (non-blank) and the stale dump must not be reported as a healthy module.
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(200L, 100L, false, 0L, now))
    }

    @Test
    fun `a stale heartbeat means the module is not running now`() {
        val stale = now - DuoPrefs.HEARTBEAT_STALE_MS - 1
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(200L, 100L, false, stale, now))
    }

    // The Infinix case: the module loaded (fresh heartbeat) but the user's own switch is off. That is a
    // choice, not a failure, so the app must not show "not running" or recovery advice.
    @Test
    fun `loaded with a fresh heartbeat and the switch off is disabled`() {
        assertEquals(ModuleState.DISABLED, ModuleHealthCheck.decide(200L, 100L, false, fresh, now, false))
    }

    @Test
    fun `a module that never loaded stays never loaded when the switch is off`() {
        // Setup steps, not "turn it on": no load stamp means LSPosed never injected the module.
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(0L, 100L, false, fresh, now, false))
    }

    @Test
    fun `a stale heartbeat stays never loaded when the switch is off`() {
        val stale = now - DuoPrefs.HEARTBEAT_STALE_MS - 1
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(200L, 100L, false, stale, now, false))
    }

    @Test
    fun `an apk update still needs a restart when the switch is off`() {
        // The running code is the old build regardless of the switch, so the restart advice stands.
        assertEquals(ModuleState.NEEDS_RESTART, ModuleHealthCheck.decide(100L, 200L, false, fresh, now, false))
    }

    @Test
    fun `switched on with a blank status is not reporting`() {
        assertEquals(ModuleState.NOT_REPORTING, ModuleHealthCheck.decide(200L, 100L, true, fresh, now, true))
    }
}
