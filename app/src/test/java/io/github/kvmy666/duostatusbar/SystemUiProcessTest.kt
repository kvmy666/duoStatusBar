package io.github.kvmy666.duostatusbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #5: the module was loaded into SystemUI yet reported "never injected". The cause was an
 * identity check that only looked at the package name, which some ROMs report as `system`. These
 * cases pin that the process name is enough on its own.
 */
class SystemUiProcessTest {

    @Test
    fun `a normal systemui process is the target`() {
        assertTrue(SystemUiProcess.isTarget("com.android.systemui", "com.android.systemui"))
    }

    @Test
    fun `a rom that reports the system package is still the target`() {
        assertTrue(SystemUiProcess.isTarget("system", "com.android.systemui"))
    }

    @Test
    fun `the settings app and other processes are never hooked`() {
        assertFalse(
            SystemUiProcess.isTarget(
                "io.github.kvmy666.duostatusbar",
                "io.github.kvmy666.duostatusbar"
            )
        )
        assertFalse(SystemUiProcess.isTarget("android", "system_server"))
        assertFalse(SystemUiProcess.isTarget(null, null))
    }
}
