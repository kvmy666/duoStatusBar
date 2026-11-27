package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17A: every field report under `.pm/evidence/u1127` is replayed through [ProblemDetector] as a
 * compact redacted fixture. The expected `type|location` set is asserted exactly, so a fingerprint can
 * only fire when the report really showed that failure. A detector change that turns an informational
 * line (a stale `attached=false`, a panel fallback, an old crash record, an iOS miss) back into a
 * trigger fails here before it can reach a user.
 */
class AutoReportReplayTest {

    private val dir = "replay/u1127"

    @Test
    fun `all 19 field reports replay to their exact trigger sets`() {
        val ids = read("$dir/index.txt").lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("the evidence index must list every report once", 19, ids.size)

        ids.forEach { id ->
            val text = read("$dir/$id.txt")
            assertTrue("fixture $id is empty", text.isNotBlank())
            val fixture = Fixture.parse(text)
            val actual = ProblemDetector.detect(fixture.inputs)
                .map { "${it.type}|${it.location}" }
                .distinct()
                .sorted()
            assertEquals("$id triggers", fixture.expect.sorted(), actual)
        }
    }

    private fun read(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
        assertNotNull("missing fixture: $path", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    private data class Fixture(val inputs: ProblemInputs, val expect: List<String>) {
        companion object {
            fun parse(text: String): Fixture {
                val headers = LinkedHashMap<String, String>()
                val expect = ArrayList<String>()
                val blocks = LinkedHashMap<String, MutableList<String>>()
                var current: MutableList<String>? = null
                var inHeaders = true

                text.lineSequence().forEach { line ->
                    when {
                        line.startsWith("--- ") -> {
                            inHeaders = false
                            val name = line.removePrefix("---").trim()
                            current = blocks.getOrPut(name) { mutableListOf() }
                        }
                        inHeaders && line.startsWith("expect:") -> {
                            val value = line.removePrefix("expect:").trim()
                            if (value.isNotEmpty()) expect += value
                        }
                        inHeaders && line.contains(':') -> {
                            val key = line.substringBefore(':').trim().lowercase()
                            val value = line.substringAfter(':').trim()
                            if (key.isNotEmpty()) headers[key] = value
                        }
                        current != null -> current!!.add(line)
                    }
                }

                return Fixture(
                    inputs = ProblemInputs(
                        status = blocks["status"].orEmpty().joinToString("\n").trim(),
                        dump = blocks["dump"].orEmpty().joinToString("\n").trim(),
                        moduleLog = blocks["moduleLog"].orEmpty().joinToString("\n").trim(),
                        moduleState = state(headers["modulestate"]),
                        moduleEnabled = bool(headers["moduleenabled"]),
                        duoEnabled = bool(headers["duoenabled"]),
                        iosActive = bool(headers["iosactive"]),
                        updatePending = bool(headers["updatepending"]),
                        capture = capture(blocks["capture"].orEmpty()),
                        captureFailure = headers["capturefailure"]
                    ),
                    expect = expect
                )
            }

            private fun bool(value: String?): Boolean =
                value.equals("true", ignoreCase = true)

            private fun state(value: String?): ModuleState? = when (value?.trim()?.uppercase()) {
                "OK" -> ModuleState.OK
                "NEVER_LOADED" -> ModuleState.NEVER_LOADED
                "NEEDS_RESTART" -> ModuleState.NEEDS_RESTART
                "DISABLED" -> ModuleState.DISABLED
                "NOT_REPORTING" -> ModuleState.NOT_REPORTING
                "IOS_ACTIVE" -> ModuleState.IOS_ACTIVE
                "IOS_UNSUPPORTED" -> ModuleState.IOS_UNSUPPORTED
                else -> null
            }

            private fun capture(lines: List<String>): RootCapture {
                val sections = ArrayList<CaptureSection>()
                var title: String? = null
                var body = StringBuilder()

                fun flush() {
                    val name = title ?: return
                    sections += section(name, body.toString())
                    body = StringBuilder()
                }

                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("=== ") && trimmed.endsWith(" ===")) {
                        flush()
                        title = trimmed.removePrefix("=== ").removeSuffix(" ===").trim()
                    } else if (title != null) {
                        body.appendLine(line)
                    }
                }
                flush()
                return RootCapture("fixture", sections)
            }

            private fun section(title: String, raw: String): CaptureSection {
                val lines = raw.trim('\n').lines()
                val header = lines.firstOrNull().orEmpty()
                if (!header.startsWith("state=")) {
                    return CaptureSection(title, SectionState.PRESENT, "", raw.trim('\n'))
                }
                val state = runCatching {
                    SectionState.valueOf(header.substringAfter("state=").substringBefore(' ').trim().uppercase())
                }.getOrDefault(SectionState.PRESENT)
                val reason = Regex("""reason=(.*)""")
                    .find(header)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "-" }
                    .orEmpty()
                val body = lines.drop(1).joinToString("\n").trim('\n')
                return CaptureSection(title, state, reason, body)
            }
        }
    }
}
