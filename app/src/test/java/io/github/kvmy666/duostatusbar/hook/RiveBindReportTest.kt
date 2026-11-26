package io.github.kvmy666.duostatusbar.hook

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * B3: the structured Rive bind failure. A HyperOS/Vector report must name the reason code and the
 * surface/device facts, not just "Rive did not bind".
 */
class RiveBindReportTest {

    @Before
    fun setUp() {
        RiveBindReport.resetForTest()
    }

    @After
    fun tearDown() {
        RiveBindReport.resetForTest()
    }

    @Test
    fun `the last failure is kept as reason and detail`() {
        assertNull(RiveBindReport.last())

        RiveBindReport.record("runtime", "RiveInit.ensure=false")
        assertEquals(RiveBindFailure("runtime", "RiveInit.ensure=false"), RiveBindReport.last())

        RiveBindReport.record("poll", "no view model instance after 25 polls")
        assertEquals("poll", RiveBindReport.last()?.reason)
        assertEquals("no view model instance after 25 polls", RiveBindReport.last()?.detail)
        assertEquals("poll", RiveBindReport.reason())
    }

    @Test
    fun `recordIfAbsent keeps the first reason`() {
        RiveBindReport.recordIfAbsent("asset", "raw unreadable")
        RiveBindReport.recordIfAbsent("bind", "element not bound")
        assertEquals("asset", RiveBindReport.reason())
    }

    @Test
    fun `the diag line is one line with reason and a tokenised detail`() {
        val line = riveBindDiagLine(
            RiveBindFailure("poll", "no view model\ninstance after 25 polls")
        )
        assertEquals(
            "rive bind: reason=poll detail=no_view_model_instance_after_25_polls",
            line
        )
    }

    @Test
    fun `the one-shot log line carries every measured fact`() {
        val line = riveFailureLogLine(
            RiveBindFailure("runtime", "RiveInit.ensure=false"),
            polls = 0,
            attached = true,
            hardware = true,
            width = 49,
            height = 61,
            renderer = "Canvas",
            selinux = "enforcing",
            stage = 2
        )
        assertEquals(
            "rive bind failed: reason=runtime detail=RiveInit.ensure=false polls=0 " +
                "attached=true hardware=true size=49x61 renderer=Canvas " +
                "selinux=enforcing stage=2",
            line
        )
    }

    @Test
    fun `a missing detail still renders a readable token`() {
        assertEquals(
            "rive bind: reason=render detail=none",
            riveBindDiagLine(RiveBindFailure("render", "  "))
        )
    }
}
