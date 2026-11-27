package io.github.kvmy666.duostatusbar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The startup breadcrumb is a report-only trail: the in-process eight-line ring must end with the
 * normal boot steps in order, and every literal must actually be wired in the hook sources. A missing
 * step is a hole in the only evidence left after a native death.
 */
class BreadcrumbWiringTest {

    private class RecordingSink : BreadcrumbSink {
        val globals = ArrayList<String>()

        override fun putGlobal(value: String) {
            globals.add(value)
        }
    }

    private lateinit var sink: RecordingSink

    @Before
    fun setUp() {
        ProcessIdentity.forceForTest("com.android.systemui", 4321)
        Breadcrumb.resetForTest()
        sink = RecordingSink()
        Breadcrumb.installSinkForTest(sink)
    }

    @After
    fun tearDown() {
        Breadcrumb.resetForTest()
        ProcessIdentity.resetForTest()
    }

    private fun stepOf(value: String): String = value.substringAfter("|step=")

    @Test
    fun `a normal start keeps an ordered eight-step tail with no duplicates`() {
        val normalStart = listOf(
            "install:begin",
            "install:context",
            "install:ios-engine",
            "install:ios-engine-ok",
            "install:battery",
            "install:ios-battery-ok",
            "install:ios-placement-ok",
            "install:ios-diag-ok",
            "install:window",
            "install:app",
            "install:adopt",
            "install:start",
            "start",
            "ios:capability",
            "install:bootstrap",
            "install:done",
            "ios:capability",
            "attach:begin",
            "attach:host",
            "attach:create",
            "attach:ready",
            "rive:created"
        )
        // A repeated call is a real path (bind/adopt can be entered twice); it must not duplicate the ring.
        for (name in normalStart) {
            Breadcrumb.step(name)
            Breadcrumb.step(name)
        }

        val tail = Breadcrumb.linesForTest().map(::stepOf)
        assertEquals(
            "the ring must end with the completed startup sequence",
            listOf(
                "install:bootstrap",
                "install:done",
                "ios:capability",
                "attach:begin",
                "attach:host",
                "attach:create",
                "attach:ready",
                "rive:created"
            ),
            tail
        )
        assertEquals("a step name must not repeat in the ring: $tail", tail.size, tail.toSet().size)
        val lineFormat = Regex("""^\d+\|process=com\.android\.systemui\|pid=4321\|step=[a-z:-]+$""")
        assertFalse(
            "the value format must stay readable: ${sink.globals.last()}",
            sink.globals.any { value ->
                value.split("\n").any { !lineFormat.matches(it) }
            }
        )
        assertEquals(
            "the last written Settings.Global value must be the ordered report-visible ring tail",
            Breadcrumb.linesForTest(),
            sink.globals.last().split("\n")
        )
    }

    @Test
    fun `every startup step literal is wired in the hook sources`() {
        val hook = read("app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoHook.kt")
        val host = read("app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoIconHost.kt")
        listOf(
            "install:begin",
            "install:context",
            "install:ios-engine-ok",
            "install:ios-battery-ok",
            "install:ios-battery-skip",
            "install:ios-placement-ok",
            "install:ios-placement-skip",
            "install:ios-diag-ok",
            "ios:capability",
            "ios:v2-active"
        ).forEach {
            assertTrue(
                "DuoHook.kt is missing Breadcrumb.step(\"$it\")",
                hook.contains("Breadcrumb.step(\"$it\")")
            )
        }
        listOf(
            "attach:begin",
            "attach:host",
            "attach:create",
            "attach:ready",
            "rive:created",
            "rive:failed",
            "render:canvas",
            "rive:start",
            "rive:bind",
            "attach:failed"
        ).forEach {
            assertTrue(
                "DuoIconHost.kt is missing Breadcrumb.step(\"$it\")",
                host.contains("Breadcrumb.step(\"$it\")")
            )
        }
    }

    @Test
    fun `the start block reports the capability before its breadcrumb`() {
        val hook = read("app/src/main/java/io/github/kvmy666/duostatusbar/hook/DuoHook.kt")
        val marker = "L.guard(\"DuoHook start\")"
        val markerAt = hook.indexOf(marker)
        assertTrue("DuoHook.kt is missing $marker", markerAt >= 0)
        val startBlock = hook.substring(markerAt + marker.length)
        val report = startBlock.indexOf("reportIosCapability(ctx)")
        val breadcrumb = startBlock.indexOf("Breadcrumb.step(\"ios:capability\")")
        assertTrue("the DuoHook start block is missing reportIosCapability(ctx)", report >= 0)
        assertTrue(
            "the DuoHook start block is missing Breadcrumb.step(\"ios:capability\")",
            breadcrumb >= 0
        )
        assertTrue(
            "reportIosCapability(ctx) must run before Breadcrumb.step(\"ios:capability\") in the start block",
            report < breadcrumb
        )
    }

    /** Reads a project file from the test working directory (the app module) or its parents. */
    private fun read(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val file = File(dir, relative)
            if (file.isFile) return file.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        throw AssertionError("$relative not found from ${File(".").absolutePath}")
    }
}
