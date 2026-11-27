package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.ModuleHealth
import io.github.kvmy666.duostatusbar.settings.ModuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-8: Home explains how to set the module up only when LSPosed has never injected it. The helpers
 * are asserted directly and the step strings are resolved from the real resources, so a renamed or
 * missing XML string fails here instead of reaching a user as a blank line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeSetupGuideTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `setup guide is shown only when the module never loaded`() {
        assertTrue(showSetupGuide(ModuleState.NEVER_LOADED))
        assertFalse(showSetupGuide(ModuleState.OK))
        assertFalse(showSetupGuide(ModuleState.NEEDS_RESTART))
        assertFalse(showSetupGuide(ModuleState.NOT_REPORTING))
        // Switched off still means it loaded, so install advice would be wrong.
        assertFalse(showSetupGuide(ModuleState.DISABLED))
    }

    @Test
    fun `the switched off hint is shown for disabled only`() {
        assertTrue(showDisabledHint(ModuleState.DISABLED))
        assertFalse(showDisabledHint(ModuleState.OK))
        assertFalse(showDisabledHint(ModuleState.NEVER_LOADED))
        assertFalse(showDisabledHint(ModuleState.NEEDS_RESTART))
        assertFalse(showDisabledHint(ModuleState.NOT_REPORTING))
    }

    @Test
    fun `three distinct steps in the documented order`() {
        assertEquals(3, HOME_SETUP_STEP_STRINGS.size)
        assertEquals(
            listOf(
                R.string.home_setup_step_1,
                R.string.home_setup_step_2,
                R.string.home_setup_step_3
            ),
            HOME_SETUP_STEP_STRINGS
        )
        assertEquals(3, HOME_SETUP_STEP_STRINGS.distinct().size)
    }

    @Test
    fun `title steps and lsposed button resolve to non blank text`() {
        val ids = listOf(R.string.home_setup_title, R.string.module_health_open_lsposed) +
            HOME_SETUP_STEP_STRINGS
        ids.forEach { id ->
            assertTrue("string resource $id is blank", context.getString(id).isNotBlank())
        }
    }

    @Test
    fun `steps keep their number in the text`() {
        val steps = HOME_SETUP_STEP_STRINGS.map { context.getString(it) }
        steps.forEachIndexed { index, text ->
            assertTrue("step ${index + 1} is not numbered", text.startsWith("${index + 1}."))
        }
    }

    @Test
    fun `switched off and restart failed strings resolve to non blank text`() {
        val ids = listOf(
            R.string.home_status_off,
            R.string.home_disabled_hint,
            R.string.module_health_disabled_title,
            R.string.module_health_disabled_text,
            R.string.settings_restart_failed
        )
        ids.forEach { id ->
            assertTrue("string resource $id is blank", context.getString(id).isNotBlank())
        }
    }

    // R16-D: an installed update is its own Home hint, not the setup guide or the switched-off line.
    @Test
    fun `update hint is shown only while an update is pending`() {
        assertTrue(showUpdateHint(ModuleHealth(ModuleState.NEEDS_RESTART, 1L, 2L)))
        listOf(
            ModuleState.OK,
            ModuleState.NEVER_LOADED,
            ModuleState.DISABLED,
            ModuleState.NOT_REPORTING,
            ModuleState.IOS_ACTIVE,
            ModuleState.IOS_UNSUPPORTED
        ).forEach { state ->
            assertFalse("$state must not show the update hint", showUpdateHint(ModuleHealth(state, 1L, 2L)))
        }
    }

    @Test
    fun `the update hint strings name both builds`() {
        val home = context.getString(R.string.home_update_hint, "2.1.2")
        assertTrue(home.isNotBlank())
        assertTrue("home hint does not name the new build: $home", home.contains("2.1.2"))
        assertEquals("Update installed", context.getString(R.string.module_health_restart_title))
        val card = context.getString(R.string.module_health_restart_text, "2.1.1", "2.1.2")
        assertTrue("card text does not name the old build: $card", card.contains("2.1.1"))
        assertTrue("card text does not name the new build: $card", card.contains("2.1.2"))
    }
}
