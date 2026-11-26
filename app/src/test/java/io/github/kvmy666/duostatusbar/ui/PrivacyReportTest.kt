package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.Diag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * Privacy is a property of every report route: the ZIP text entries, the plain diagnostics file and
 * the builder output. A planted identity value must not survive any of them, while the ordinary
 * diagnostics (resource id, pid, version, uid:pid:tid) must.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivacyReportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val secrets = listOf(
        "HomeSweetHome",
        "ACME Telecom",
        "alice@example.com",
        "+44 20 7946 0958",
        "356789012345678",
        "ABC123SECRET",
        "Alice Account",
        "Hello secret notification",
        "Alice sent a message"
    )

    private val plantedText = """
        diagnostics
        ssid=HomeSweetHome
        carrierName=ACME Telecom
        account=Alice Account
        owner email alice@example.com
        phone +44 20 7946 0958
        imei=356789012345678
        serial=ABC123SECRET
        android.text=Hello secret notification
        text='Alice sent a message'
        0x7f0a0f26 pid=7525 version=2.1.2 uid:pid:tid=0:7525:7530
    """.trimIndent()

    private fun assertNoSecret(text: String, where: String) {
        secrets.forEach { secret ->
            assertFalse("$where leaked '$secret'", text.contains(secret))
        }
    }

    private fun inputs(diagnostics: String = plantedText) = DuoReportInputs(
        generatedAt = "2026-01-01T00:00:00Z",
        appVersion = "2.1.2 (code 112)",
        buildType = "debug",
        device = mapOf("model" to "Pixel", "sdk" to "35"),
        adapter = mapOf("id" to "aosp", "measured" to "true"),
        diagnostics = diagnostics,
        moduleLog = plantedText,
        logcat = plantedText,
        diagnosticsDump = plantedText
    )

    @Test
    fun `every zip text entry is redacted and ordinary diagnostics survive`() {
        val dest = File(tmp.root, "privacy.zip")
        DuoReport.writeZip(dest, inputs())
        ZipFile(dest).use { zf ->
            val textEntries = listOf(
                "diagnostics.txt",
                "module-log.txt",
                "logcat.txt",
                "diagnostics-dump.txt"
            )
            textEntries.forEach { name ->
                val entry = zf.getEntry(name)
                assertNotNull(name, entry)
                val text = zf.getInputStream(entry).bufferedReader().readText()
                assertNoSecret(text, name)
            }
            val diagnostics = zf.getInputStream(zf.getEntry("diagnostics.txt"))
                .bufferedReader().readText()
            assertTrue(diagnostics.contains("0x7f0a0f26"))
            assertTrue(diagnostics.contains("pid=7525"))
            assertTrue(diagnostics.contains("version=2.1.2"))
            assertTrue(diagnostics.contains("uid:pid:tid=0:7525:7530"))
            assertTrue(diagnostics.contains("ssid=[redacted]"))
        }
    }

    @Test
    fun `the diagnostics text file is redacted`() {
        val file = writeDiagnostics(context, plantedText)
        assertNotNull(file)
        val text = file!!.readText()
        assertNoSecret(text, file.name)
        assertTrue(text.contains("0x7f0a0f26"))
        assertTrue(text.contains("pid=7525"))
        assertTrue(text.contains("version=2.1.2"))
    }

    @Test
    fun `the builder output is redacted defensively`() {
        val report = buildDiagnostics(
            context = context,
            settings = io.github.kvmy666.duostatusbar.settings.DuoSettings(),
            status = "account=Alice Account pid=7525",
            history = listOf("imei=356789012345678"),
            dump = plantedText,
            moduleLoadAt = 0L
        )
        assertNoSecret(report, "buildDiagnostics")
        assertTrue(report.contains("pid=7525"))
        assertTrue(report.contains("0x7f0a0f26"))
    }

    @Test
    fun `the full report redacts the user message and the capture text`() {
        val report = buildFullReport(
            context = context,
            problem = "my account=Alice Account and email alice@example.com",
            settings = io.github.kvmy666.duostatusbar.settings.DuoSettings(),
            status = "ssid=HomeSweetHome",
            history = emptyList(),
            dump = "",
            moduleLoadAt = 0L,
            logs = plantedText
        )
        assertNoSecret(report, "buildFullReport")
        assertTrue(report.contains("0x7f0a0f26"))
    }

    @Test
    fun `the view tree dump stays class and id only`() {
        val view = TextView(context).apply {
            text = "Alice sent a message"
            contentDescription = "Hello secret notification"
            id = 0x7f0a0f26
        }
        val tree = Diag.treeText("probe", view)
        assertFalse("the view tree must not emit text=", tree.contains("text="))
        assertFalse(tree.contains("content-desc="))
        assertFalse(tree.contains("Alice"))
        assertTrue(tree.contains("TextView"))
    }
}
