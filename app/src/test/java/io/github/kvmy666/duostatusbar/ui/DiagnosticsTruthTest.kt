package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * F-1 reporting truth: the report must say when the module loaded, and it must apply the same SDK gate
 * to the ROM adapter as the rest of the app so `measured` does not claim a version that was never read.
 */
class DiagnosticsTruthTest {

    @Test
    fun `explicit module load time wins over every fallback`() {
        assertEquals(
            formatTimestamp(1_000L),
            moduleLoadLine(1_000L, "status", listOf("history"), 2_000L, 3_000L, 4_000L)
        )
    }

    @Test
    fun `last load stamp is used when the caller has no explicit time`() {
        assertEquals(
            formatTimestamp(2_000L),
            moduleLoadLine(0L, "status", listOf("history"), 2_000L, 3_000L, 4_000L)
        )
    }

    @Test
    fun `a stale last load says the module is not running now`() {
        val stale = "${formatTimestamp(2_000L)} (last load; module not running now)"
        // No heartbeat at all: the module has not proven it is alive since that load.
        assertEquals(
            stale,
            moduleLoadLine(0L, "status", listOf("history"), 2_000L, 0L, 4_000L)
        )
        // An old heartbeat is just as stale even though a load stamp exists.
        assertEquals(
            stale,
            moduleLoadLine(
                0L, "status", listOf("history"), 2_000L,
                3_000L, 3_000L + DuoPrefs.HEARTBEAT_STALE_MS + 1L
            )
        )
    }

    @Test
    fun `fresh heartbeat is labelled as the heartbeat`() {
        assertEquals(
            "${formatTimestamp(3_000L)} (heartbeat)",
            moduleLoadLine(0L, "", emptyList(), 0L, 3_000L, 3_000L + 1_000L)
        )
    }

    @Test
    fun `stale heartbeat says the module is not running now`() {
        val now = 3_000L + DuoPrefs.HEARTBEAT_STALE_MS + 1L
        assertEquals(
            "${formatTimestamp(3_000L)} (heartbeat), module not running now",
            moduleLoadLine(0L, "", emptyList(), 0L, 3_000L, now)
        )
    }

    @Test
    fun `a module that reported without a stamp is unknown, never never`() {
        assertEquals(
            "unknown (module reported, no load stamp)",
            moduleLoadLine(0L, "attached", emptyList(), 0L, 0L, 5_000L)
        )
        assertEquals(
            "unknown (module reported, no load stamp)",
            moduleLoadLine(0L, "", listOf("1700000000000 attached"), 0L, 0L, 5_000L)
        )
    }

    @Test
    fun `no stamps and no report still says never`() {
        assertEquals(
            "never (LSPosed has not injected the module into System UI)",
            moduleLoadLine(0L, "", emptyList(), 0L, 0L, 5_000L)
        )
    }

    @Test
    fun `reportRom selects an sdk-gated measured profile only inside its range`() {
        val profiles = listOf(
            RomAdapter(
                id = "gated",
                label = "Gated test profile",
                systemUiPackage = "com.android.systemui",
                containerIds = listOf("system_icons"),
                batteryId = "battery",
                notes = "test",
                apiMin = 36,
                apiMax = 36,
                match = listOf("gatedrom"),
                measured = true
            )
        )

        val at36 = reportRom("Acme", "gatedrom", "product", "display", profiles, 36)
        assertEquals("gated", at36.id)
        assertEquals(true, at36.measured)

        // Outside the measured range the profile must not be accepted, so it falls through to AOSP.
        val at37 = reportRom("Acme", "gatedrom", "product", "display", profiles, 37)
        assertEquals("aosp", at37.id)
        assertFalse(at37.measured)
    }
}
