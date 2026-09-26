package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update check must not nag: it only fires when the GitHub release is genuinely newer. The repo tags
 * releases as `6-1.1.0` while the display name is `v1.1.0`, so the version has to be pulled out of either,
 * and compared numerically (1.10 > 1.9) — both pure and pinned here.
 */
class UpdateCheckerTest {

    @Test
    fun `a version is pulled out of the name and out of the version-code tag`() {
        assertEquals("1.1.0", UpdateChecker.parseVersion("v1.1.0"))
        assertEquals("1.1.0", UpdateChecker.parseVersion("6-1.1.0"))
        assertEquals("1.2", UpdateChecker.parseVersion("Duo Status Bar 1.2"))
        assertNull(UpdateChecker.parseVersion("no version here"))
    }

    @Test
    fun `a genuinely newer release is detected`() {
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.1.1", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        // Numeric, not lexicographic: 1.10 is after 1.9.
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
    }

    @Test
    fun `the same or an older release is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("v1.1.0", "1.1.0"))
    }
}
