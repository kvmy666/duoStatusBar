package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings search matcher: a row is shown when the query appears in its label or its detail, and a
 * blank query shows everything. The About card also lists the automatic-report detail, so the words a
 * user types for reports ("automatic", "logs") find the card and its switch.
 */
class SettingsSearchTest {

    private val label = "Automatic problem reports"
    private val detail =
        "Collect logs and send them automatically when a problem is detected."

    @Test
    fun `a label match finds the row`() {
        assertTrue(settingsMatches("automatic", label, detail))
    }

    @Test
    fun `a detail only match is still found`() {
        assertTrue(settingsMatches("logs", label, detail))
    }

    @Test
    fun `a miss is filtered out`() {
        assertFalse(settingsMatches("wifi", label, detail))
    }

    @Test
    fun `a blank query shows every row`() {
        assertTrue(settingsMatches("", label, detail))
        assertTrue(settingsMatches("   ", label, detail))
    }
}
