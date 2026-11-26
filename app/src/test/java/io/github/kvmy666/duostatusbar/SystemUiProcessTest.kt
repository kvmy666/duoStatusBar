package io.github.kvmy666.duostatusbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The process gate. `isTarget` must accept only the main SystemUI process, identified by its process
 * name (the package name is unreliable: HyperOS reports `system`, OnePlus keyguard class-loaders report
 * `com.oplus.keyguard.*`), and reject every proxy/side process.
 */
class SystemUiProcessTest {

    @Test
    fun `the main systemui process is the target`() {
        assertTrue(SystemUiProcess.isTarget("com.android.systemui", "com.android.systemui"))
    }

    @Test
    fun `a rom that reports the system package is still the target`() {
        assertTrue(SystemUiProcess.isTarget("system", "com.android.systemui"))
    }

    @Test
    fun `owner keyguard class loaders in the main process are the target`() {
        assertTrue(
            SystemUiProcess.isTarget(
                "com.oplus.keyguard.personality.clocks",
                "com.android.systemui"
            )
        )
        assertTrue(
            SystemUiProcess.isTarget("com.oplus.keyguard.clock.base", "com.android.systemui")
        )
    }

    @Test
    fun `a missing or blank process name falls back to the exact package`() {
        assertTrue(SystemUiProcess.isTarget("com.android.systemui", null))
        assertTrue(SystemUiProcess.isTarget("com.android.systemui", ""))
        assertTrue(SystemUiProcess.isTarget("com.android.systemui", "   "))
        assertFalse(SystemUiProcess.isTarget("system", null))
        assertFalse(SystemUiProcess.isTarget("system", ""))
        assertFalse(SystemUiProcess.isTarget("com.oplus.keyguard.clock.base", ""))
    }

    @Test
    fun `every colon side process is rejected even when the package matches`() {
        for (suffix in listOf(":edgelighting", ":screenshot", ":worker43", ":x", ":")) {
            assertFalse(
                "side process $suffix must not be a target",
                SystemUiProcess.isTarget(
                    "com.android.systemui",
                    "com.android.systemui$suffix"
                )
            )
        }
    }

    @Test
    fun `other packages are never hooked`() {
        assertFalse(
            SystemUiProcess.isTarget(
                "io.github.kvmy666.duostatusbar",
                "io.github.kvmy666.duostatusbar"
            )
        )
        assertFalse(SystemUiProcess.isTarget("android", "system_server"))
        assertFalse(SystemUiProcess.isTarget("com.oplus.aiunit", "com.oplus.aiunit"))
        assertFalse(SystemUiProcess.isTarget("com.oplus.aiunit", "com.oplus.aiunit:worker43"))
        assertFalse(SystemUiProcess.isTarget(null, null))
        assertFalse(SystemUiProcess.isTarget(null, "com.android.systemui:edgelighting"))
    }
}
