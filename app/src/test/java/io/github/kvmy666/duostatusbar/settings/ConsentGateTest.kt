package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consent is off until answered, is never asked again after an answer, and cannot be given in one tap:
 * both buttons are disabled for five visible seconds.
 */
class ConsentGateTest {

    @Test
    fun `default is off and the dialog is shown`() {
        val state = AutoReportState()
        assertEquals(ConsentState.UNANSWERED, state.consent)
        assertTrue(ConsentGate.shouldShow(state.consent))
        assertFalse(ConsentGate.enabled(state.consent))
    }

    @Test
    fun `an answered yes or no is never asked again`() {
        assertFalse(ConsentGate.shouldShow(ConsentState.YES))
        assertFalse(ConsentGate.shouldShow(ConsentState.NO))
    }

    @Test
    fun `only an explicit yes enables automatic reports`() {
        assertTrue(ConsentGate.enabled(ConsentState.YES))
        assertFalse(ConsentGate.enabled(ConsentState.NO))
        assertFalse(ConsentGate.enabled(ConsentState.UNANSWERED))
    }

    @Test
    fun `countdown starts at five and the buttons enable at zero`() {
        assertEquals(5, ConsentGate.COUNTDOWN_SECONDS)
        assertEquals(5, ConsentGate.remaining(0))
        assertEquals(4, ConsentGate.remaining(1))
        assertEquals(1, ConsentGate.remaining(4))
        assertEquals(0, ConsentGate.remaining(5))
        assertFalse(ConsentGate.buttonsEnabled(0))
        assertFalse(ConsentGate.buttonsEnabled(4))
        assertTrue(ConsentGate.buttonsEnabled(5))
        assertTrue(ConsentGate.buttonsEnabled(99))
        assertEquals(0, ConsentGate.remaining(99))
    }
}
