package io.github.kvmy666.duostatusbar.diagnostics

import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealth
import io.github.kvmy666.duostatusbar.settings.ModuleMode
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.SettingsHistory
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
          counters failures=0 retries=1 firstFailure=none orderPasses=3 orderMoves=1 extrasHidden=2 engineCalls=9 engineHits=7 stackedSwaps=0 guardAttempts=1
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
        captureFailure: String? = null
    ) = ReportFacts.perPieceStatus(
        status = status,
        dump = dump,
        settings = settings,
        health = health,
        lastChange = lastChange,
        revision = revision,
        captureText = captureText,
        captureFailure = captureFailure,
        formatTimestamp = { "2026-01-01 00:00:00" }
    )

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
