package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The consent dialog and its Settings toggle must be localized: every new automatic-report string exists
 * in English and in the new Arabic resource file, and the countdown still formats a number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsentResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun arabic(): Resources {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale("ar"))
        }
        return context.createConfigurationContext(config).resources
    }

    @Test
    fun `every consent string exists in english`() {
        listOf(
            R.string.consent_title,
            R.string.consent_question,
            R.string.consent_yes,
            R.string.consent_no,
            R.string.settings_auto_report,
            R.string.settings_auto_report_detail
        ).forEach { id ->
            assertTrue(context.getString(id).isNotBlank())
        }
    }

    @Test
    fun `the arabic consent strings are real translations`() {
        val ar = arabic()
        listOf(
            R.string.consent_title,
            R.string.consent_question,
            R.string.consent_yes,
            R.string.consent_no,
            R.string.settings_auto_report,
            R.string.settings_auto_report_detail
        ).forEach { id ->
            val english = context.getString(id)
            val arabic = ar.getString(id)
            assertTrue("missing Arabic for ${context.resources.getResourceEntryName(id)}", arabic.isNotBlank())
            assertFalse("Arabic still equals English for ${context.resources.getResourceEntryName(id)}", arabic == english)
        }
        assertTrue(ar.getString(R.string.consent_question).contains("Duo Status Bar"))
    }

    @Test
    fun `the five second countdown formats`() {
        assertEquals("5…", context.getString(R.string.consent_countdown, 5))
        // Arabic formats the digit with its own numerals (٥) but the countdown marker survives.
        assertTrue(arabic().getString(R.string.consent_countdown, 5).endsWith("…"))
    }
}
