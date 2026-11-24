package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsApplyDecisionTest {

    @Test
    fun `on with no element enables`() {
        assertEquals(
            SettingsApplyAction.ENABLE,
            settingsApplyAction(
                stage = DuoGuard.RIVE,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = false,
                iosLive = false
            )
        )
    }

    @Test
    fun `ios live with no duo element enables the duo side`() {
        // BUG D: the caller used to pass hasElement = (duo element || iosLive), so a switch back to
        // Duo while an iOS host was live read as REFRESH and only poked the null monitor. With the
        // iOS host excluded from hasElement, the pure decision is ENABLE (DuoHook.kt call site).
        assertEquals(
            SettingsApplyAction.ENABLE,
            settingsApplyAction(
                stage = DuoGuard.RIVE,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = false,
                iosLive = false
            )
        )
    }

    @Test
    fun `on with an element refreshes`() {
        assertEquals(
            SettingsApplyAction.REFRESH,
            settingsApplyAction(
                stage = DuoGuard.RIVE,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = true,
                iosLive = false
            )
        )
    }

    @Test
    fun `icons only with an element refreshes`() {
        assertEquals(
            SettingsApplyAction.REFRESH,
            settingsApplyAction(
                stage = DuoGuard.ICONS_ONLY,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = true,
                iosLive = false
            )
        )
    }

    @Test
    fun `a real off tears the element down`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = true,
                iosLive = false
            )
        )
    }

    @Test
    fun `off from an unreachable provider keeps the element`() {
        assertEquals(
            SettingsApplyAction.KEEP_UNREACHABLE,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = true,
                servedFromBridge = false,
                hasElement = true,
                iosLive = false
            )
        )
    }

    @Test
    fun `off from an unreachable provider with no element still tears down`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = true,
                servedFromBridge = false,
                hasElement = false,
                iosLive = false
            )
        )
    }

    @Test
    fun `the adb kill switch is authoritative even when unreachable`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = true,
                providerUnreachable = true,
                servedFromBridge = false,
                hasElement = true,
                iosLive = false
            )
        )
    }

    @Test
    fun `off served by the bridge tears the element down`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = true,
                servedFromBridge = true,
                hasElement = true,
                iosLive = false
            )
        )
    }

    @Test
    fun `off from an unreachable provider keeps a live ios bar`() {
        // C11 regression: with a live iOS v2 owner and an unreachable provider, hasElement is false
        // (BUG D keeps the Duo element separate) but the iOS bar must not be torn down.
        assertEquals(
            SettingsApplyAction.KEEP_UNREACHABLE,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = true,
                servedFromBridge = false,
                hasElement = false,
                iosLive = true
            )
        )
    }

    @Test
    fun `reachable rive with a live ios bar still enables the duo side`() {
        // D1 preserved: iosLive must not leak into the REFRESH/ENABLE branch; only hasElement does.
        assertEquals(
            SettingsApplyAction.ENABLE,
            settingsApplyAction(
                stage = DuoGuard.RIVE,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = false,
                iosLive = true
            )
        )
    }

    @Test
    fun `a real off still tears a live ios bar down`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = false,
                servedFromBridge = false,
                hasElement = false,
                iosLive = true
            )
        )
    }

    @Test
    fun `the adb kill switch still tears a live ios bar down`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = true,
                providerUnreachable = true,
                servedFromBridge = false,
                hasElement = false,
                iosLive = true
            )
        )
    }

    @Test
    fun `a bridge-served off still tears a live ios bar down`() {
        assertEquals(
            SettingsApplyAction.TEARDOWN,
            settingsApplyAction(
                stage = DuoGuard.OFF,
                stageFromAdbOverride = false,
                providerUnreachable = true,
                servedFromBridge = true,
                hasElement = false,
                iosLive = true
            )
        )
    }
}
