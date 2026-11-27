package io.github.kvmy666.duostatusbar.diagnostics

import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealth
import io.github.kvmy666.duostatusbar.settings.ModuleMode
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.SettingsHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-piece block is what makes a report actionable without another round trip: attach, Rive,
 * the iOS engine counters, battery/stacked/order/extras failures, mode, design, the last settings
 * change, process/pid, uptime and deaths. A missing fact says `missing:<why>` instead of vanishing.
 */
class ReportFactsTest {

    private val status =
        "process=com.android.systemui pid=7525 · owner=ios · stage=2 · renderer=none · " +
            "attached=true · riveAttempts=0"

    private val dump = """
        Duo Status Bar diagnostic dump
        systemui:
          uid=1000 pid=7525 process=com.android.systemui
        module:
          stage=2 attempts=0
          stageSource=app-settings
          provider=ok
          renderer=none ready=false
          ios v2 timing: activatedAt=100 mapReadyAt=200 buildMs=5 callsBeforeMap=2 calls=9 hits=7 misses=2
          ios v2 coverage family=oxygenos piece=wifi resolved=8/10 tried=a,b
          ios v2 coverage family=oxygenos piece=cell resolved=4/4 tried=none
          ios v2 runtimeMisses: family=oxygenos wifi=0 cell=0 label=0 hidden=0
          ios v2 delivery: resourcesImpl calls=11; viewIcons=ImageView:IosWifiDrawable
          hooks2 stacked=ok placement=ok extras=ok order=ok
          counters failures=0 retries=1 firstFailure=none orderPasses=3 orderMoves=1 extrasHidden=2 engineCalls=9 engineHits=7 stackedSwaps=0 guardAttempts=0
          ios v2 miss family=oxygenos piece=battery tried=StatBatteryMeterView
          ios v2 miss family=oxygenos piece=stacked tried=BatteryViewBinder
          ios v2 miss family=oxygenos piece=extras tried=IosExtrasView
    """.trimIndent()

    private val captureText = """
        === capture route ===
        state=present reason=-
        root via /system/bin/su

        === systemui deaths ===
        state=present reason=-
        deaths=2
        safeMode=no
        safeModeCounter=2
        uptime=1234s
    """.trimIndent()

    private fun facts(
        status: String = this.status,
        dump: String = this.dump,
        settings: DuoSettings? = DuoSettings(designStyle = DuoSettings.DESIGN_NEW),
        health: ModuleHealth? = ModuleHealth(ModuleState.OK, 1L, 0L, ModuleMode.IOS),
        lastChange: SettingsHistory.Record? = SettingsHistory.Record(1234L, listOf("ios_mode")),
        revision: Long = 7L,
        captureText: String = this.captureText,
        captureFailure: String? = null,
        appVersion: String = "2.1.2",
        dumpAt: Long = 0L
    ) = ReportFacts.perPieceStatus(
        status = status,
        dump = dump,
        settings = settings,
        health = health,
        lastChange = lastChange,
        revision = revision,
        captureText = captureText,
        captureFailure = captureFailure,
        formatTimestamp = { "2026-01-01 00:00:00" },
        appVersion = appVersion,
        dumpAt = dumpAt
    )

    // R16-B: with an update pending the report prints one clear state and no per-piece "missing:".
    // The dump is the real 2.1.1 header from the field report.
    @Test
    fun `needs restart prints one update pending line and no missing pieces`() {
        val staleDump = """
            Duo Status Bar diagnostic dump
            version: 2.1.1 (code 111, debug=false)
            build:
              MANUFACTURER=OnePlus BRAND=OnePlus MODEL=CPH2747
            module:
              stage=0 renderer=none attached=false
        """.trimIndent()
        val out = facts(
            status = "attached=false stage=0 renderer=none",
            dump = staleDump,
            health = ModuleHealth(
                ModuleState.NEEDS_RESTART,
                100L,
                200L,
                ModuleMode.IOS,
                runningVersion = "2.1.1"
            ),
            appVersion = "2.1.2"
        )

        val lines = out.trim().lines()
        assertEquals(3, lines.size)
        assertEquals("=== per-piece status ===", lines[0])
        assertEquals(
            "update pending: System UI still runs 2.1.1; app is 2.1.2; " +
                "restart System UI or reboot to load 2.1.2",
            lines[1]
        )
        assertEquals("mode: NEEDS_RESTART (iOS active)", lines[2])
        assertFalse(out.contains("missing:"))
        assertFalse(out.contains("attached="))
    }

    // R16-A2: the owner case. The running module is the app build, but the stored dump is the previous
    // build's. The report prints live status facts and one stale-dump line; it never derives per-piece
    // facts or mode from the old dump, and it never calls the live module NEVER_LOADED.
    @Test
    fun `a stale dump from a loaded module prints a live block and no per-piece missing lines`() {
        val liveStatus =
            "process=com.android.systemui pid=25754 · owner=ios · stage=0 · renderer=none · " +
                "attached=false · riveAttempts=0"
        val staleDump = """
            Duo Status Bar diagnostic dump
            version: 2.1.1 (code 111, debug=false)
            build:
              MANUFACTURER=OnePlus BRAND=OnePlus MODEL=CPH2747
            === module log (from the module, no root needed) ===
            1795696331873 status -> app: stage=0 · renderer=none · attached=false
        """.trimIndent()
        val out = ReportFacts.perPieceStatus(
            status = liveStatus,
            dump = staleDump,
            settings = DuoSettings(designStyle = DuoSettings.DESIGN_OLD),
            health = ModuleHealth(
                ModuleState.NEVER_LOADED,
                100L,
                50L,
                ModuleMode.IOS,
                runningVersion = "2.1.3"
            ),
            lastChange = null,
            revision = 0L,
            captureText = "",
            appVersion = "2.1.3",
            formatTimestamp = { if (it == 1795696331873L) "2026-11-26 15:32" else "unexpected:$it" }
        )

        assertTrue(out.contains("process: process=com.android.systemui pid=25754"))
        assertTrue(
            out.contains(
                "duo attach: attached=false stage=0 renderer=none owner=ios"
            )
        )
        assertTrue(
            out.contains(
                "dump: STALE diagnostic dump from 2.1.1 written 2026-11-26 15:32; " +
                    "per-piece facts unavailable"
            )
        )
        assertTrue(out.contains("mode: OK (iOS active)"))
        assertFalse("a stale dump must not produce missing: spam:\n$out", out.contains("missing:"))
        assertFalse("a live module must not be called NEVER_LOADED:\n$out", out.contains("NEVER_LOADED"))
        assertFalse("dump-derived per-piece facts must be absent", out.contains("ios engine"))
    }

    // The embedded module log can be missing or truncated; the module's own dump stamp still says
    // when the stale dump was written, so the report never needs "written at an unknown time".
    @Test
    fun `the dump timestamp names the stale dump time when the log cannot`() {
        val staleDump = """
            Duo Status Bar diagnostic dump
            version: 2.1.1 (code 111, debug=false)
        """.trimIndent()
        val out = facts(
            dump = staleDump,
            health = ModuleHealth(
                ModuleState.OK,
                100L,
                50L,
                ModuleMode.IOS,
                runningVersion = "2.1.3"
            ),
            appVersion = "2.1.3",
            dumpAt = 1795696331873L
        )
        assertTrue(
            "the dump stamp must name the time: $out",
            out.contains("dump: STALE diagnostic dump from 2.1.1 written 2026-01-01 00:00:00;")
        )
        assertFalse("the time must not be unknown: $out", out.contains("unknown time"))
    }

    @Test
    fun `the live status owner decides the mode label`() {
        val dumpWithSameBuild = dump.replace(
            "Duo Status Bar diagnostic dump",
            "Duo Status Bar diagnostic dump\nversion: 2.1.3 (code 113, debug=false)"
        )
        val out = facts(
            status = status.replace("owner=ios", "owner=duo"),
            dump = dumpWithSameBuild,
            health = ModuleHealth(
                ModuleState.OK,
                1L,
                0L,
                ModuleMode.IOS,
                runningVersion = "2.1.3"
            ),
            appVersion = "2.1.3"
        )
        assertTrue(out.contains("mode: OK (Duo active)"))
    }

    @Test
    fun `mode design last change process uptime and deaths are stated`() {
        val out = facts()
        assertTrue(out.startsWith("=== per-piece status ==="))
        assertTrue(out.contains("mode: OK (iOS active)"))
        assertTrue(out.contains("design: new"))
        assertTrue(out.contains("last settings change: 2026-01-01 00:00:00 keys=ios_mode rev=7"))
        assertTrue(out.contains("process=com.android.systemui"))
        assertTrue(out.contains("pid=7525"))
        assertTrue(out, out.contains("uptime: 1234s"))
        assertTrue(out.contains("systemui deaths this boot: deaths=2"))
    }

    @Test
    fun `duo attach and rive failure are read from the status`() {
        val withRive = status + " · riveBind=surface"
        val out = facts(status = withRive)
        assertTrue(out.contains("duo attach: attached=true stage=2"))
        assertTrue(out.contains("owner=ios"))
        assertTrue(out.contains("root=app-settings"))
        assertTrue(out.contains("rive bind: reason=surface"))
    }

    @Test
    fun `ios engine calls hits misses timing map coverage and delivery come from the dump`() {
        val out = facts()
        assertTrue(out.contains("calls=9"))
        assertTrue(out.contains("hits=7"))
        assertTrue(out.contains("misses=2"))
        assertTrue(out.contains("mapReadyAt=200"))
        assertTrue(out.contains("buildMs=5"))
        assertTrue(out.contains("activate"))
        assertTrue(out.contains("ios coverage: "))
        assertTrue(out.contains("piece=wifi resolved=8/10"))
        assertTrue(out.contains("ios delivery: resourcesImpl calls=11"))
    }

    @Test
    fun `battery stacked order and extras failure lines are named`() {
        val out = facts()
        assertTrue(out.contains("battery: ios v2 miss"))
        assertTrue(out.contains("piece=battery"))
        assertTrue(out.contains("stacked: ios v2 miss"))
        assertTrue(out.contains("extras: ios v2 miss"))
        assertTrue(out.contains("order: hooks=ok passes=3 moves=1"))
    }

    @Test
    fun `absent facts print a missing reason instead of disappearing`() {
        val out = facts(
            status = "",
            dump = "",
            settings = null,
            health = null,
            lastChange = null,
            revision = 0L,
            captureText = ""
        )
        assertTrue(out.contains("duo attach: attached=missing:"))
        assertTrue(out.contains("rive bind: missing:"))
        assertTrue(out.contains("ios engine: missing:"))
        assertTrue(out.contains("battery: missing:"))
        assertTrue(out.contains("stacked: missing:"))
        assertTrue(out.contains("order: missing:"))
        assertTrue(out.contains("extras: missing:"))
        assertTrue(out.contains("mode: missing:"))
        assertTrue(out.contains("design: missing:"))
        assertTrue(out.contains("last settings change: ${SettingsHistory.ABSENT}"))
        assertTrue(out.contains("process: missing:"))
        assertTrue(out.contains("uptime: missing:"))
        assertTrue(out.contains("systemui deaths this boot: missing:"))
    }

    @Test
    fun `a failed capture makes uptime and deaths say the capture failed`() {
        val out = facts(captureText = "", captureFailure = "IllegalStateException: boom")
        assertTrue(out.contains("uptime: missing:capture failed: IllegalStateException: boom"))
        assertTrue(out.contains("systemui deaths this boot: missing:capture failed: IllegalStateException: boom"))
    }

    @Test
    fun `an empty module status falls back to the dump systemui line`() {
        val out = facts(status = "")
        assertTrue(out.contains("process=com.android.systemui"))
        assertTrue(out.contains("pid=7525"))
        assertTrue(out.contains("stage=2"))
    }
}
