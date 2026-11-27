package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app ships English only: every automatic-report string resolves from res/values with the exact
 * copy the dialog and the Settings toggle show, and the locked No label carries the seconds left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsentResourcesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every consent string exists in english`() {
        listOf(
            R.string.consent_title,
            R.string.consent_question,
            R.string.consent_yes,
            R.string.consent_no,
            R.string.consent_no_countdown,
            R.string.settings_auto_report,
            R.string.settings_auto_report_detail
        ).forEach { id ->
            assertTrue(context.getString(id).isNotBlank())
        }
        assertEquals("Help fix bugs", context.getString(R.string.consent_title))
        assertEquals("Yes (Recommended)", context.getString(R.string.consent_yes))
        assertEquals("No", context.getString(R.string.consent_no))
        assertEquals("Automatic problem reports", context.getString(R.string.settings_auto_report))
        assertTrue(
            "the question must still say what is collected",
            context.getString(R.string.consent_question).contains("diagnostic logs")
        )
    }

    @Test
    fun `the locked no label counts down from five to one`() {
        assertEquals("No (%1\$d)", context.getString(R.string.consent_no_countdown))
        (5 downTo 1).forEach { seconds ->
            assertEquals(
                "No ($seconds)",
                context.getString(R.string.consent_no_countdown, seconds)
            )
        }
        assertEquals("No", context.getString(R.string.consent_no))
    }
}
