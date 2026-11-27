package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.ui.duoControlsEnabled
import io.github.kvmy666.duostatusbar.ui.iosOwnsBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContextImpl
import java.io.File

/**
 * OWNER DECISION (Duo 2.1.6, overriding AGENTS.md rule 6 for this one case): a brand-new install
 * starts in Duo mode with the single-SIM classic design — enabled, not iOS, old design. The first
 * open materialises that into the store, so every reader sees it. An existing store is never
 * changed, including the legacy store whose file exists but has no `enabled` key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoPrefsFirstRunTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun storeFile(): File =
        File(context.applicationInfo.dataDir, "shared_prefs/duo_settings.xml")

    /**
     * Runs before any [DuoPrefs] call, so a test that wants a first install really starts with no file.
     *
     * Robolectric instantiates [io.github.kvmy666.duostatusbar.DuoApp], and its `onCreate` opens the
     * store, so the shared-preferences cache is already populated (and, on the first open, seeded)
     * before a test runs. [ShadowContextImpl.reset] drops that in-memory cache so the next open sees
     * the file state this test sets up; deleting the file then makes it a true first install.
     */
    @Before
    fun deleteStoreBeforeAnyRead() {
        ShadowContextImpl.reset()
        storeFile().delete()
    }

    @Test
    fun `a brand-new install starts in classic Duo mode`() {
        val settings = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertTrue("first install must be enabled", settings.enabled)
        assertFalse("first install must not be iOS", settings.iosMode)
        assertEquals(DuoPrefs.DESIGN_OLD, settings.designStyle)

        // Materialised, not just a read default: the store itself now carries the three portrait keys.
        assertTrue("the prefs file must exist after the seed", storeFile().exists())
        val p = context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
        assertTrue(p.contains(DuoPrefs.COL_ENABLED))
        assertTrue(p.getBoolean(DuoPrefs.COL_ENABLED, false))
        assertEquals(DuoPrefs.DESIGN_OLD, p.getString(DuoPrefs.COL_DESIGN_STYLE, null))

        // Portrait only: no landscape key and no landscape_set were written.
        assertFalse(p.contains(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED))
        assertFalse(p.contains(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_MODE))
        assertFalse(p.contains(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DESIGN_STYLE))
        assertFalse(p.contains("landscape_set"))

        // The seed is not user activity: no revision bump and no history line.
        assertEquals(0L, DuoPrefs.revision(context))
        assertTrue(DuoPrefs.statusHistory(context).isEmpty())
    }

    @Test
    fun `landscape on a fresh store mirrors portrait without landscape keys`() {
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)

        assertTrue("landscape reads the seeded portrait copy", landscape.enabled)
        assertFalse(landscape.iosMode)
        assertEquals(DuoPrefs.DESIGN_OLD, landscape.designStyle)

        val p = context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
        assertFalse(p.contains("landscape_set"))
        assertFalse(p.contains(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED))
    }

    @Test
    fun `an existing store missing enabled keeps the old false default`() {
        // Bypass the accessor on purpose: this simulates a store written by an older build (or a
        // status/fallback/dump route) before this feature existed.
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit()
            .putString("last_status", "x")
            .commit()
        assertTrue(storeFile().exists())

        val settings = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertFalse("an existing store must keep today's false", settings.enabled)
        val p = context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
        assertFalse("the seed must not add the key to an existing store", p.contains(DuoPrefs.COL_ENABLED))
    }

    @Test
    fun `a stored false value is preserved exactly`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit()
            .putBoolean(DuoPrefs.COL_ENABLED, false)
            .commit()

        val settings = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertFalse(settings.enabled)
        assertFalse(
            context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
                .getBoolean(DuoPrefs.COL_ENABLED, true)
        )
    }

    @Test
    fun `a stored true iOS new-design store is preserved exactly`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit()
            .putBoolean(DuoPrefs.COL_ENABLED, true)
            .putBoolean(DuoPrefs.COL_IOS_MODE, true)
            .putString(DuoPrefs.COL_DESIGN_STYLE, DuoPrefs.DESIGN_NEW)
            .commit()

        val settings = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertTrue(settings.enabled)
        assertTrue(settings.iosMode)
        assertEquals(DuoPrefs.DESIGN_NEW, settings.designStyle)
        val p = context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
        assertTrue(p.getBoolean(DuoPrefs.COL_IOS_MODE, false))
        assertEquals(DuoPrefs.DESIGN_NEW, p.getString(DuoPrefs.COL_DESIGN_STYLE, null))
    }

    @Test
    fun `a status write on a fresh store seeds before it writes`() {
        DuoPrefs.writeStatus(context, "stage=1 renderer=Canvas")

        assertTrue("the direct-write route must also seed", DuoPrefs.read(context, DuoOrientation.PORTRAIT).enabled)
        assertEquals("stage=1 renderer=Canvas", DuoPrefs.status(context))
    }

    @Test
    fun `a fallback write on a fresh store seeds before it writes`() {
        DuoPrefs.writeFallback(context, "")

        assertTrue("the fallback route must also seed", DuoPrefs.read(context, DuoOrientation.PORTRAIT).enabled)
    }

    @Test
    fun `shouldSeedFirstRun only accepts a missing file without the key`() {
        assertTrue(DuoPrefs.shouldSeedFirstRun(fileExisted = false, enabledKeyExists = false))
        assertFalse(DuoPrefs.shouldSeedFirstRun(fileExisted = true, enabledKeyExists = false))
        assertFalse(DuoPrefs.shouldSeedFirstRun(fileExisted = false, enabledKeyExists = true))
        assertFalse(DuoPrefs.shouldSeedFirstRun(fileExisted = true, enabledKeyExists = true))
    }

    @Test
    fun `first-run settings map to the Duo mode`() {
        val settings = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        // The UI's pure mapper is the mode authority: enabled && !iOS owns the bar.
        assertTrue(duoControlsEnabled(settings, iosAvailable = true))
        assertFalse(iosOwnsBar(settings, iosAvailable = true))
        assertTrue(settings.enabled && !settings.iosMode)
        assertEquals(DuoPrefs.DESIGN_OLD, settings.designStyle)
    }
}
