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

    // ---------------------------------------------------------------- iOS owner report

    private fun capability(
        owner: String,
        supported: Boolean,
        route: String = if (owner == IosCapability.OWNER_IOS) {
            IosCapability.ROUTE_V2
        } else {
            IosCapability.ROUTE_NONE
        },
        at: Long = 200L
    ) = IosCapability(
        owner = owner,
        family = "oxygenos",
        supported = supported,
        route = route,
        revision = 1L,
        pid = 7,
        at = at
    )

    @Test
    fun `a valid report whose real owner is ios is ios active`() {
        // Duo's own switch is off and no status arrives: the owner is iOS with the live v2 route.
        assertEquals(
            ModuleState.IOS_ACTIVE,
            ModuleHealthCheck.decide(
                200L, 100L, true, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = capability(IosCapability.OWNER_IOS, supported = true)
            )
        )
    }

    @Test
    fun `ios active requires the live v2 route, not just the owner tag`() {
        // The activation-refusal/fallback window can carry owner=ios with route=none. That is not iOS
        // drawing, so the state falls through to Duo's own reporting state.
        assertEquals(
            ModuleState.OK,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = capability(
                    IosCapability.OWNER_IOS,
                    supported = true,
                    route = IosCapability.ROUTE_NONE
                )
            )
        )
        assertEquals(
            ModuleState.NOT_REPORTING,
            ModuleHealthCheck.decide(
                200L, 100L, true, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = capability(
                    IosCapability.OWNER_IOS,
                    supported = true,
                    route = IosCapability.ROUTE_NONE
                )
            )
        )
    }

    @Test
    fun `an ios owner without the live route still falls through to disabled when switched off`() {
        assertEquals(
            ModuleState.DISABLED,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosMode = false,
                capability = capability(
                    IosCapability.OWNER_IOS,
                    supported = true,
                    route = IosCapability.ROUTE_NONE
                )
            )
        )
    }

    @Test
    fun `a refusal outranks the ios owner tag`() {
        // The module's fallback report: owner=ios, route=none, supported=false while the user asked for
        // iOS. This must be IOS_UNSUPPORTED, never IOS_ACTIVE (even though no status has arrived).
        assertEquals(
            ModuleState.IOS_UNSUPPORTED,
            ModuleHealthCheck.decide(
                200L, 100L, true, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = capability(
                    IosCapability.OWNER_IOS,
                    supported = false,
                    route = IosCapability.ROUTE_NONE
                )
            )
        )
    }

    @Test
    fun `ios owner is reported even before a status arrives`() {
        assertEquals(
            ModuleState.IOS_ACTIVE,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = capability(IosCapability.OWNER_IOS, supported = true)
            )
        )
    }

    @Test
    fun `asked for ios on a ui the report says is unsupported is ios unsupported`() {
        assertEquals(
            ModuleState.IOS_UNSUPPORTED,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = capability(IosCapability.OWNER_DUO, supported = false)
            )
        )
    }

    @Test
    fun `ios mode with a missing report is not disabled`() {
        // UNKNOWN must preserve today's behaviour: Duo is not claimed off and no owner is claimed.
        assertEquals(
            ModuleState.OK,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = null
            )
        )
        assertEquals(
            ModuleState.NOT_REPORTING,
            ModuleHealthCheck.decide(
                200L, 100L, true, fresh, now,
                settingsEnabled = false,
                iosMode = true,
                capability = null
            )
        )
    }

    @Test
    fun `duo reporting a supported device is ok`() {
        assertEquals(
            ModuleState.OK,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = true,
                iosMode = false,
                capability = capability(IosCapability.OWNER_DUO, supported = true)
            )
        )
    }

    @Test
    fun `a valid duo report does not hide a switched off module`() {
        assertEquals(
            ModuleState.DISABLED,
            ModuleHealthCheck.decide(
                200L, 100L, false, fresh, now,
                settingsEnabled = false,
                iosMode = false,
                capability = capability(IosCapability.OWNER_DUO, supported = true)
            )
        )
    }
}
