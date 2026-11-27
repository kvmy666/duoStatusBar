package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomMarkers
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

    // A fresh snapshot proves the module answered this report even when an old build writes no
    // heartbeat; the load line must not reuse the stale-heartbeat "not running now" wording.
    @Test
    fun `a live contact load line never says the module stopped`() {
        assertEquals(
            formatTimestamp(2_000L),
            moduleLoadLine(0L, "", emptyList(), 2_000L, 0L, 4_000L, liveContact = true)
        )
        assertEquals(
            "${formatTimestamp(3_000L)} (heartbeat)",
            moduleLoadLine(
                0L, "", emptyList(), 0L, 3_000L,
                3_000L + DuoPrefs.HEARTBEAT_STALE_MS + 1L,
                liveContact = true
            )
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

    // R16-B: an installed update is one clear state. It must name both builds and never reuse the
    // "never loaded" or "module not running now" wording from the stale-heartbeat path.
    @Test
    fun `update pending names the running and installed builds`() {
        assertEquals(
            "update installed; System UI still runs 2.1.1; " +
                "restart System UI or reboot to load 2.1.2",
            updatePendingLine("2.1.1", "2.1.2")
        )
        assertEquals(
            "update installed; System UI still runs the previous build; " +
                "restart System UI or reboot to load 2.1.2",
            updatePendingLine(null, "2.1.2")
        )
    }

    @Test
    fun `update pending never claims the module was not loaded`() {
        val line = updatePendingLine("2.1.1", "2.1.2")
        assertFalse(line.contains("never", ignoreCase = true))
        assertFalse(line.contains("module not running now", ignoreCase = true))
    }

    @Test
    fun `the dump version is the first version line`() {
        val dump = "Duo Status Bar diagnostic dump\n" +
            "version: 2.1.1 (code 111, debug=false)\n" +
            "module log quoting version: 1.0.0\n"
        assertEquals("2.1.1", dumpVersion(dump))
        assertEquals(null, dumpVersion("no version here"))
    }

    @Test
    fun `reportRom selects an sdk-gated measured profile only inside its range`() {
        val marker = RomMarkers.of(
            systemUiSourceDir = "/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk"
        )
        val profiles = listOf(
            RomAdapter(
                id = "hyperos",
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

        // Inside the range *and* with the family's proven marker the measured profile is accepted.
        val at36 = reportRom("Acme", "gatedrom", "product", "display", profiles, 36, marker)
        assertEquals("hyperos", at36.id)
        assertEquals(true, at36.measured)

        // Without the proven marker the profile must not be selected even inside its range.
        val noMarker = reportRom("Acme", "gatedrom", "product", "display", profiles, 36, RomMarkers.UNKNOWN)
        assertEquals("aosp", noMarker.id)
        assertFalse(noMarker.measured)

        // Outside the measured range the profile must not be accepted, so it falls through to AOSP.
        val at37 = reportRom("Acme", "gatedrom", "product", "display", profiles, 37, marker)
        assertEquals("aosp", at37.id)
        assertFalse(at37.measured)
    }
}
