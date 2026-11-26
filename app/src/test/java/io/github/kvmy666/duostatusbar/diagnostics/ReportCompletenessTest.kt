package io.github.kvmy666.duostatusbar.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report completeness header is the promise that no section is silently empty: every required
 * section appears once, with a state and a reason. Capture sections take their state/reason from
 * [RootCapture] (C1), app-side inputs are derived with an explicit reason.
 */
class ReportCompletenessTest {

    private val captureSections = listOf(
        CaptureSection("capture route", SectionState.PRESENT, "", "root via /system/bin/su"),
        CaptureSection("logcat (DuoSB)", SectionState.PRESENT, "", "DuoSB line"),
        CaptureSection("LSPosed log files", SectionState.PRESENT, "", "modules_1.log"),
        CaptureSection("LSPosed newest modules log", SectionState.PRESENT, "", "banner"),
        CaptureSection("safe mode", SectionState.PRESENT, "", "counter=0\nsafeMode=no"),
        CaptureSection("LSPosed config (enabled / scope)", SectionState.PRESENT, "", "found"),
        CaptureSection("framework manager", SectionState.PRESENT, "", "manager installed"),
        CaptureSection("module.prop", SectionState.PRESENT, "", "version=2.1.2"),
        CaptureSection("/data/adb layout", SectionState.PRESENT, "", "lspd"),
        CaptureSection("root framework", SectionState.PRESENT, "", "lspd"),
        CaptureSection("kernel modules", SectionState.EMPTY, "no output"),
        CaptureSection("installed module package", SectionState.PRESENT, "", "package"),
        CaptureSection("crash records", SectionState.UNREADABLE, "Permission denied"),
        CaptureSection("tombstones", SectionState.EMPTY, "no matching tombstone"),
        CaptureSection("logcat crash buffer", SectionState.SKIPPED, "no root: root access was not granted"),
        CaptureSection("breadcrumb", SectionState.PRESENT, "", "ts|step=attach"),
        CaptureSection("systemui deaths", SectionState.PRESENT, "", "deaths=2\nuptime=100s"),
        CaptureSection("breadcrumb (app fallback)", SectionState.PRESENT, "", "ts|step=attach"),
        CaptureSection("app-side facts (no privilege)", SectionState.PRESENT, "", "MODEL=Pixel")
    )

    private fun capture() = RootCapture("root via /system/bin/su", captureSections)

    private fun sections(
        status: String = "process=com.android.systemui pid=7525 · owner=duo",
        history: List<String> = listOf("1700000000000 attached"),
        dump: String = "Duo Status Bar diagnostic dump\n" +
            "=== module log (from the module, no root needed) ===\nmodule line\n",
        moduleLoad: String = "2026-01-01 00:00:00",
        capture: RootCapture? = capture(),
        captureText: String = "",
        captureFailure: String? = null
    ) = ReportCompleteness.build(
        status = status,
        history = history,
        dump = dump,
        moduleLoad = moduleLoad,
        capture = capture,
        captureText = captureText,
        captureFailure = captureFailure
    )

    @Test
    fun `every required section appears once with a state and a non-blank reason`() {
        val out = sections()
        val names = out.map { it.name }
        assertEquals("a section may not be listed twice", names.size, names.distinct().size)
        ReportCompleteness.REQUIRED.forEach { required ->
            assertTrue("missing section: $required", required in names)
        }
        out.forEach {
            assertNotEquals("reason must not be the render placeholder", "-", it.reason)
            assertTrue("section ${it.name} has no reason", it.reason.isNotBlank())
        }
    }

    @Test
    fun `header renders the published line format`() {
        val text = ReportCompleteness.render(sections())
        assertTrue(text.startsWith(ReportCompleteness.HEADER))
        val body = text.lineSequence().drop(1).filter { it.isNotBlank() }.toList()
        assertEquals(ReportCompleteness.REQUIRED.size, body.size)
        body.forEach { line ->
            assertTrue(line, Regex("""^section=.+ state=(present|empty|unreadable|skipped|missing) reason=.+$""").matches(line))
        }
    }

    @Test
    fun `capture sections keep the collector state and reason`() {
        val byName = sections().associateBy { it.name }
        assertEquals(ReportSectionState.PRESENT, byName.getValue("capture route").state)
        assertEquals(ReportSectionState.PRESENT, byName.getValue("LSPosed/framework logs").state)
        assertEquals(ReportSectionState.PRESENT, byName.getValue("safe mode").state)
        assertEquals(ReportSectionState.UNREADABLE, byName.getValue("crash records").state)
        assertEquals("Permission denied", byName.getValue("crash records").reason)
        assertEquals(ReportSectionState.EMPTY, byName.getValue("tombstones").state)
        assertEquals(ReportSectionState.SKIPPED, byName.getValue("crash buffer").state)
        assertEquals(ReportSectionState.PRESENT, byName.getValue("systemui deaths/uptime").state)
    }

    @Test
    fun `missing app side inputs say why`() {
        val byName = sections(
            status = "",
            history = emptyList(),
            dump = "",
            moduleLoad = "never (LSPosed has not injected the module into System UI)",
            capture = null,
            captureText = ""
        ).associateBy { it.name }

        assertEquals(ReportSectionState.MISSING, byName.getValue("module status").state)
        assertTrue(byName.getValue("module status").reason.contains("module never reported"))
        assertEquals(ReportSectionState.EMPTY, byName.getValue("diagnostic dump").state)
        assertTrue(byName.getValue("diagnostic dump").reason.contains("dump absent"))
        assertEquals(ReportSectionState.MISSING, byName.getValue("module log").state)
        assertEquals(ReportSectionState.EMPTY, byName.getValue("history").state)
        assertEquals(ReportSectionState.MISSING, byName.getValue("module load").state)
        assertEquals(ReportSectionState.MISSING, byName.getValue("capture route").state)
        assertTrue(byName.getValue("capture route").reason.contains("no capture"))
    }

    @Test
    fun `a failed capture is unreadable everywhere with the exception`() {
        val byName = sections(
            capture = null,
            captureText = "",
            captureFailure = "IllegalStateException: boom"
        ).associateBy { it.name }

        assertEquals(ReportSectionState.UNREADABLE, byName.getValue("capture route").state)
        assertEquals("capture failed: IllegalStateException: boom", byName.getValue("capture route").reason)
        assertEquals(ReportSectionState.UNREADABLE, byName.getValue("crash records").state)
        assertEquals("capture failed: IllegalStateException: boom", byName.getValue("crash records").reason)
        assertEquals(ReportSectionState.UNREADABLE, byName.getValue("breadcrumb").state)
    }

    @Test
    fun `a pre-rendered capture text is understood like a RootCapture`() {
        val text = capture().render()
        val byName = sections(capture = null, captureText = text).associateBy { it.name }
        assertEquals(ReportSectionState.PRESENT, byName.getValue("capture route").state)
        assertEquals(ReportSectionState.UNREADABLE, byName.getValue("crash records").state)
        assertEquals("Permission denied", byName.getValue("crash records").reason)
        assertEquals(ReportSectionState.EMPTY, byName.getValue("tombstones").state)
    }

    @Test
    fun `breadcrumb falls back to the app copy when the privileged one is skipped`() {
        val roots = captureSections.filterNot { it.title == "breadcrumb" } +
            CaptureSection("breadcrumb", SectionState.SKIPPED, "no root: root access was not granted")
        val byName = sections(capture = RootCapture("root via su", roots)).associateBy { it.name }
        assertEquals(ReportSectionState.PRESENT, byName.getValue("breadcrumb").state)
        assertTrue(byName.getValue("breadcrumb").reason.contains("app fallback"))
    }

    @Test
    fun `the header can be parsed back into section state and reason`() {
        val text = ReportCompleteness.render(sections())
        val parsed = ReportCompleteness.parseHeader(text)
        assertEquals(ReportCompleteness.REQUIRED.size, parsed.size)
        assertTrue(parsed.getValue("module status").startsWith("present reason="))
        assertTrue(parsed.getValue("diagnostic dump").contains("present"))
    }

    @Test
    fun `the module log is taken from the last marker`() {
        val marker = "=== module log (from the module, no root needed) ==="
        val dump = "header\n$marker\nquoted old blank section\n$marker\nmodule line\n"
        assertEquals(dump.lastIndexOf(marker), ReportCompleteness.moduleLogMarkerIndex(dump))

        val byName = sections(dump = dump).associateBy { it.name }
        assertEquals(ReportSectionState.PRESENT, byName.getValue("module log").state)
        assertTrue(byName.getValue("module log").reason.contains("embedded"))
    }

    @Test
    fun `a quoted old blank marker before a real one is not mistaken for an empty log`() {
        val marker = "=== module log (from the module, no root needed) ==="
        val dump = "header\n$marker\n   \nquoted banner\n$marker\nmodule line\n"
        val byName = sections(dump = dump).associateBy { it.name }
        assertEquals(ReportSectionState.PRESENT, byName.getValue("module log").state)
    }

    @Test
    fun `a blank history is empty not missing`() {
        val byName = sections(history = emptyList()).associateBy { it.name }
        assertEquals(ReportSectionState.EMPTY, byName.getValue("history").state)
        assertFalse(byName.getValue("history").reason.isBlank())
    }
}
