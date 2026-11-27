package io.github.kvmy666.duostatusbar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The last-step breadcrumb: one write per step change and a bounded in-process last-8 ring. The sink is
 * injected so the test needs no Android Settings and no filesystem (the file sink was measured
 * undeliverable and removed).
 */
class BreadcrumbTest {

    private class FakeSink : BreadcrumbSink {
        val globals = ArrayList<String>()

        override fun putGlobal(value: String) {
            globals.add(value)
        }
    }

    private lateinit var sink: FakeSink

    @Before
    fun setUp() {
        ProcessIdentity.forceForTest("com.android.systemui", 4321)
        sink = FakeSink()
        Breadcrumb.installSinkForTest(sink)
    }

    @After
    fun tearDown() {
        Breadcrumb.resetForTest()
        ProcessIdentity.resetForTest()
    }

    @Test
    fun `a step change writes once and the same step does not duplicate`() {
        Breadcrumb.step("install:ios-engine")
        assertEquals(1, sink.globals.size)

        Breadcrumb.step("install:ios-engine")
        assertEquals("the same step must not write twice", 1, sink.globals.size)

        Breadcrumb.step("start")
        assertEquals(2, sink.globals.size)
    }

    @Test
    fun `the value names the time process pid and step`() {
        Breadcrumb.step("install:battery")
        val value = sink.globals.single()
        assertTrue(
            "unexpected breadcrumb value: $value",
            Regex("""^\d+\|process=com\.android\.systemui\|pid=4321\|step=install:battery$""")
                .matches(value)
        )
    }

    @Test
    fun `the ring keeps only the last eight steps`() {
        for (i in 0 until 12) Breadcrumb.step("step-$i")

        val ring = Breadcrumb.linesForTest()
        assertEquals(8, ring.size)
        assertTrue(
            "oldest step leaked: $ring",
            ring.none { it.endsWith("|step=step-3") }
        )
        assertTrue(ring.any { it.endsWith("|step=step-4") })
        assertTrue(ring.any { it.endsWith("|step=step-11") })
    }
}
