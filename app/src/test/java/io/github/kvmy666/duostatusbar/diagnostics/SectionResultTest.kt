package io.github.kvmy666.duostatusbar.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stable section API C2/D consume: every section is rendered, even an empty one. */
class SectionResultTest {

    @Test
    fun `render keeps every section with its state and reason`() {
        val capture = RootCapture(
            "rootKind=magisk · route=root",
            listOf(
                CaptureSection("logcat", SectionState.PRESENT, "", "DuoSB | hi"),
                CaptureSection("tombstones", SectionState.UNREADABLE, "Permission denied"),
                CaptureSection("breadcrumb", SectionState.SKIPPED, "no root"),
                CaptureSection("logcat crash buffer", SectionState.EMPTY, "no output")
            )
        )
        val text = capture.render()
        assertTrue("the outer header must be the summary", text.startsWith("=== capture summary ==="))
        assertFalse(
            "the outer header must not duplicate the capture route section",
            text.contains("=== capture route ===")
        )
        assertTrue(text.contains("=== logcat ==="))
        assertTrue(text.contains("state=present reason=-"))
        assertTrue(text.contains("DuoSB | hi"))
        assertTrue(text.contains("=== tombstones ==="))
        assertTrue(text.contains("state=unreadable reason=Permission denied"))
        assertTrue(text.contains("=== breadcrumb ==="))
        assertTrue(text.contains("state=skipped reason=no root"))
        assertTrue(text.contains("=== logcat crash buffer ==="))
        assertTrue(text.contains("state=empty reason=no output"))
        assertTrue("the route must be at the top", text.contains("route=root"))
        assertEquals("all four sections must be rendered", 4, text.split("state=").size - 1)
    }
}
