package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Both switches (About and Report) and the consent dialog call [AutoReportManager.answerConsent], so
 * the one consent state is whatever that call wrote. The schedule may be unavailable under Robolectric;
 * `answerConsent` writes consent first and guards the scheduling, so only the store is asserted here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoReportConsentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `answering yes stores consent and enables automatic reports`() {
        AutoReportManager.answerConsent(context, true)

        assertEquals(ConsentState.YES, AutoReportStore.consent(context))
        assertTrue(ConsentGate.enabled(AutoReportStore.consent(context)))
    }

    @Test
    fun `answering no stores consent and disables automatic reports`() {
        // Start from an explicit yes so the no below is a real transition, not the default.
        AutoReportManager.answerConsent(context, true)
        AutoReportManager.answerConsent(context, false)

        assertEquals(ConsentState.NO, AutoReportStore.consent(context))
        assertFalse(ConsentGate.enabled(AutoReportStore.consent(context)))
    }
}
