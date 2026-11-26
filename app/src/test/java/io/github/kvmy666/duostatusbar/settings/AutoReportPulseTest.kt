package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import io.github.kvmy666.duostatusbar.ui.DuoReport
import io.github.kvmy666.duostatusbar.ui.DuoReportInputs
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One pulse: consent gate, one ZIP for new problems, one summary for counter bumps, the daily cap, and
 * reporter failures that become retryable fingerprints. All collection/sending is faked; the cost and
 * the privacy of the real ZIP writer are measured here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoReportPulseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val identity = AutoReportIdentity("2.1.2", "OnePlus", "CPH2747", "oxygenos")
    private val attach = DetectedProblem("attach", "attach")

    private class FakeStore(var state: AutoReportState = AutoReportState(consent = ConsentState.YES)) :
        AutoReportStateStore {
        override fun load(): AutoReportState = state
        override fun save(state: AutoReportState) {
            this.state = state
        }
    }

    private class FakeCollector(
        var problems: List<DetectedProblem> = emptyList(),
        var failure: Throwable? = null
    ) {
        var calls = 0
        fun collect(): AutoReportCollected {
            calls++
            failure?.let { throw it }
            return AutoReportCollected(
                report = "diagnostics body",
                logs = "capture body",
                status = "status body",
                dump = "dump body",
                sections = 3,
                problems = problems
            )
        }
    }

    private class FakeZipper(var bytes: Long = 512, var failure: Throwable? = null) {
        var calls = 0
        fun zip(): ZipArtifact {
            calls++
            failure?.let { throw it }
            return ZipArtifact(null, bytes)
        }
    }

    private class FakeSender(
        var zipOutcome: SendOutcome = SendOutcome.Sent,
        var summaryOutcome: SendOutcome = SendOutcome.Sent
    ) : AutoReportSender {
        var zipCalls = 0
        var summaryCalls = 0
        val zipCaptions = mutableListOf<String>()
        val summaries = mutableListOf<String>()
        override fun sendZip(artifact: ZipArtifact, caption: String): SendOutcome {
            zipCalls++
            zipCaptions += caption
            return zipOutcome
        }

        override fun sendSummary(text: String, caption: String): SendOutcome {
            summaryCalls++
            summaries += text
            return summaryOutcome
        }
    }

    private fun pulse(
        store: FakeStore,
        collector: FakeCollector = FakeCollector(),
        zipper: FakeZipper = FakeZipper(),
        sender: FakeSender = FakeSender(),
        maxPerDay: Int = AutoReportLedger.MAX_SENDS_PER_DAY,
        now: Long = 10L * AutoReportLedger.DAY_MS
    ) = AutoReportPulse(
        store = store,
        clock = { now },
        elapsedClock = { 0L },
        identity = identity,
        collector = collector::collect,
        zipper = { zipper.zip() },
        sender = sender,
        maxPerDay = maxPerDay
    )

    @Test
    fun `consent off collects nothing and sends nothing`() {
        val store = FakeStore(AutoReportState(consent = ConsentState.NO))
        val collector = FakeCollector(problems = listOf(attach))
        val sender = FakeSender()
        val result = pulse(store, collector, sender = sender).run()
        assertEquals(PulseAction.SKIPPED_CONSENT, result.action)
        assertEquals(0, collector.calls)
        assertEquals(0, sender.zipCalls)
        assertEquals(0, sender.summaryCalls)
        assertEquals(ConsentState.NO, store.state.consent)
    }

    @Test
    fun `no problem sends nothing`() {
        val store = FakeStore()
        val sender = FakeSender()
        val result = pulse(store, sender = sender).run()
        assertEquals(PulseAction.NOTHING, result.action)
        assertEquals(0, sender.zipCalls)
        assertEquals(0, sender.summaryCalls)
        assertEquals(0, result.sends)
    }

    @Test
    fun `a new problem sends exactly one zip`() {
        val store = FakeStore()
        val sender = FakeSender()
        val result = pulse(store, FakeCollector(problems = listOf(attach)), sender = sender).run()
        assertEquals(PulseAction.ZIP_SENT, result.action)
        assertEquals(1, sender.zipCalls)
        assertEquals(0, sender.summaryCalls)
        assertEquals(1, result.sends)
        assertTrue(store.state.sent.any { it.startsWith("attach|attach|") })
        assertTrue(store.state.pending.isEmpty())
        assertTrue(sender.zipCaptions.first().contains("attach|attach"))
    }

    @Test
    fun `a repeat sends one summary then a quiet pulse sends nothing`() {
        val store = FakeStore()
        val sender = FakeSender()
        pulse(store, FakeCollector(problems = listOf(attach)), sender = sender).run()
        val repeat = pulse(store, FakeCollector(problems = listOf(attach)), sender = sender).run()
        assertEquals(PulseAction.SUMMARY_SENT, repeat.action)
        assertEquals(1, sender.zipCalls)
        assertEquals(1, sender.summaryCalls)
        assertTrue(sender.summaries.first().contains("attach|attach"))
        val quiet = pulse(store, FakeCollector(problems = emptyList()), sender = sender).run()
        assertEquals(PulseAction.NOTHING, quiet.action)
        assertEquals(1, sender.summaryCalls)
    }

    @Test
    fun `the daily cap keeps new work queued`() {
        val store = FakeStore()
        val sender = FakeSender()
        pulse(store, FakeCollector(problems = listOf(attach)), sender = sender, maxPerDay = 1).run()
        val capped = pulse(
            store,
            FakeCollector(problems = listOf(DetectedProblem("fallback", "rive"))),
            sender = sender,
            maxPerDay = 1
        ).run()
        assertEquals(PulseAction.CAPPED, capped.action)
        assertEquals(1, sender.zipCalls)
        assertTrue(store.state.pending.isNotEmpty())
        // The next day the queued problem is sent.
        val nextDay = pulse(
            store,
            FakeCollector(problems = emptyList()),
            sender = sender,
            maxPerDay = 1,
            now = 11L * AutoReportLedger.DAY_MS
        ).run()
        assertEquals(PulseAction.ZIP_SENT, nextDay.action)
    }

    @Test
    fun `a send failure becomes a reporter fingerprint retried next pulse`() {
        val store = FakeStore()
        val sender = FakeSender(zipOutcome = SendOutcome.Failed("IOException"))
        val failed = pulse(store, FakeCollector(problems = listOf(attach)), sender = sender).run()
        assertEquals(PulseAction.FAILED, failed.action)
        assertTrue(store.state.pending.any { it.fingerprint.startsWith("reporter|upload:IOException|") })
        assertEquals(2, store.state.pending.size)
        // The retry succeeds and the caption names the reporter failure too.
        sender.zipOutcome = SendOutcome.Sent
        val retried = pulse(store, FakeCollector(problems = emptyList()), sender = sender).run()
        assertEquals(PulseAction.ZIP_SENT, retried.action)
        assertTrue(sender.zipCaptions.last().contains("reporter|upload:IOException"))
        assertTrue(store.state.pending.isEmpty())
    }

    @Test
    fun `a summary send failure is retried`() {
        val store = FakeStore()
        val sender = FakeSender()
        pulse(store, FakeCollector(problems = listOf(attach)), sender = sender).run()
        sender.summaryOutcome = SendOutcome.Failed("SocketTimeoutException")
        val failed = pulse(store, FakeCollector(problems = listOf(attach)), sender = sender).run()
        assertEquals(PulseAction.FAILED, failed.action)
        sender.summaryOutcome = SendOutcome.Sent
        val retried = pulse(store, FakeCollector(problems = emptyList()), sender = sender).run()
        assertEquals(PulseAction.ZIP_SENT, retried.action)
        assertTrue(sender.zipCaptions.last().contains("reporter|upload:SocketTimeoutException"))
    }

    @Test
    fun `a failed upload does not consume the daily cap`() {
        val store = FakeStore()
        val sender = FakeSender(zipOutcome = SendOutcome.Failed("IOException"))
        val failed = pulse(
            store,
            FakeCollector(problems = listOf(attach)),
            sender = sender,
            maxPerDay = 1
        ).run()
        assertEquals(PulseAction.FAILED, failed.action)
        assertEquals("a failed send is not a send", 0, failed.sends)
        assertEquals("a failed send must not use the daily cap", 0, store.state.sendsToday)

        sender.zipOutcome = SendOutcome.Sent
        val retried = pulse(
            store,
            FakeCollector(problems = emptyList()),
            sender = sender,
            maxPerDay = 1
        ).run()
        assertEquals(PulseAction.ZIP_SENT, retried.action)
        assertEquals(1, retried.sends)
        assertEquals(1, store.state.sendsToday)
    }

    @Test
    fun `a failed summary does not consume the daily cap`() {
        val store = FakeStore()
        val sender = FakeSender()
        pulse(store, FakeCollector(problems = listOf(attach)), sender = sender, maxPerDay = 2).run()
        assertEquals(1, store.state.sendsToday)

        sender.summaryOutcome = SendOutcome.Failed("SocketTimeoutException")
        val failed = pulse(
            store,
            FakeCollector(problems = listOf(attach)),
            sender = sender,
            maxPerDay = 2
        ).run()
        assertEquals(PulseAction.FAILED, failed.action)
        assertEquals(0, failed.sends)
        assertEquals("a failed summary must not use the daily cap", 1, store.state.sendsToday)

        sender.summaryOutcome = SendOutcome.Sent
        val retried = pulse(
            store,
            FakeCollector(problems = emptyList()),
            sender = sender,
            maxPerDay = 2
        ).run()
        assertEquals(PulseAction.SUMMARY_SENT, retried.action)
        assertEquals(1, retried.sends)
        assertEquals(2, store.state.sendsToday)
    }

    @Test
    fun `three successful sends cap the day`() {
        val store = FakeStore()
        val sender = FakeSender()
        val problems = listOf(
            DetectedProblem("attach", "attach"),
            DetectedProblem("fallback", "rive"),
            DetectedProblem("hook", "panel")
        )
        problems.forEach { problem ->
            val sent = pulse(
                store,
                FakeCollector(problems = listOf(problem)),
                sender = sender
            ).run()
            assertEquals(PulseAction.ZIP_SENT, sent.action)
            assertEquals(1, sent.sends)
        }
        assertEquals(3, store.state.sendsToday)
        assertEquals(3, sender.zipCalls)

        val capped = pulse(
            store,
            FakeCollector(problems = listOf(DetectedProblem("ios_miss", "wifi"))),
            sender = sender
        ).run()
        assertEquals(PulseAction.CAPPED, capped.action)
        assertEquals(0, capped.sends)
        assertEquals("a capped pulse sends nothing", 3, sender.zipCalls)
        assertTrue(store.state.pending.isNotEmpty())
    }

    @Test
    fun `a zip failure becomes a reporter fingerprint and sends no zip`() {
        val store = FakeStore()
        val sender = FakeSender()
        val result = pulse(
            store,
            FakeCollector(problems = listOf(attach)),
            FakeZipper(failure = IllegalStateException("no dir")),
            sender
        ).run()
        assertEquals(PulseAction.FAILED, result.action)
        assertEquals(0, sender.zipCalls)
        assertTrue(store.state.pending.any { it.fingerprint.startsWith("reporter|zip:IllegalStateException|") })
    }

    @Test
    fun `a collect failure becomes a reporter fingerprint with no send`() {
        val store = FakeStore()
        val sender = FakeSender()
        val result = pulse(
            store,
            FakeCollector(failure = IllegalStateException("boom")),
            sender = sender
        ).run()
        assertEquals(PulseAction.FAILED, result.action)
        assertEquals(0, sender.zipCalls)
        assertTrue(store.state.pending.any { it.fingerprint.startsWith("reporter|collect:IllegalStateException|") })
    }

    @Test
    fun `a collector that cannot read anything reports collector error`() {
        val store = FakeStore()
        val sender = FakeSender()
        val unreadable = ProblemDetector.detect(
            ProblemInputs(
                capture = RootCapture(
                    "none",
                    listOf(
                        CaptureSection("capture route", SectionState.UNREADABLE, "no route"),
                        CaptureSection("logcat (DuoSB)", SectionState.UNREADABLE, "permission denied")
                    )
                )
            )
        )
        val result = pulse(store, FakeCollector(problems = unreadable), sender = sender).run()
        assertEquals(PulseAction.ZIP_SENT, result.action)
        assertTrue(sender.zipCaptions.first().contains("collector|capture"))
    }

    @Test
    fun `the pulse cost is reported and logged`() {
        var nanos = 0L
        val store = FakeStore()
        val sender = FakeSender()
        val pulse = AutoReportPulse(
            store = store,
            clock = { 10L * AutoReportLedger.DAY_MS },
            elapsedClock = { val value = nanos; nanos += 7; value },
            identity = identity,
            collector = {
                AutoReportCollected(
                    "diag", "logs", "status", "dump", 4,
                    listOf(attach, DetectedProblem("fallback", "rive"))
                )
            },
            zipper = { ZipArtifact(null, 1234) },
            sender = sender
        )
        val result = pulse.run()
        assertEquals(7L, result.elapsedMs)
        assertEquals(1234L, result.zipBytes)
        assertEquals(2, result.problems)
        assertEquals(4, result.sections)
        assertEquals(1, result.sends)
        assertEquals(
            "auto report pulse: ms=7 bytes=1234 problems=2 sends=1",
            result.logLine()
        )
    }

    @Test
    fun `planted private values never reach the automatic zip`() {
        // The automatic path writes through the same DuoReport writer (and C2's redaction choke point).
        val destination = File(tmp.root, "auto.zip")
        val planted = listOf(
            "ssid=HomeNetwork",
            "carrierName=Vodafone",
            "account=alice@example.com",
            "+44 20 7946 0958",
            "imei=123456789012345",
            "serial=ABC123456",
            "text='Alice sent a message'"
        ).joinToString("\n")
        DuoReport.writeZip(
            destination,
            DuoReportInputs(
                generatedAt = "2026-01-01T00:00:00",
                appVersion = "2.1.2",
                buildType = "release",
                device = mapOf("model" to "CPH2747"),
                adapter = mapOf("id" to "oxygenos"),
                diagnostics = planted,
                moduleLog = planted,
                logcat = planted,
                diagnosticsDump = planted
            )
        )
        ZipFile(destination).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(names.containsAll(listOf("manifest.json", "diagnostics.txt", "module-log.txt", "logcat.txt")))
            names.filter { it.endsWith(".txt") }.forEach { name ->
                val text = zip.getInputStream(zip.getEntry(name)).bufferedReader().use { it.readText() }
                assertFalse("$name leaked the SSID", text.contains("HomeNetwork"))
                assertFalse("$name leaked the carrier", text.contains("Vodafone"))
                assertFalse("$name leaked the email", text.contains("alice@example.com"))
                assertFalse("$name leaked the phone", text.contains("7946"))
                assertFalse("$name leaked the IMEI", text.contains("123456789012345"))
                assertFalse("$name leaked the serial", text.contains("ABC123456"))
                assertFalse("$name leaked the view text", text.contains("Alice sent a message"))
            }
        }
    }
}
