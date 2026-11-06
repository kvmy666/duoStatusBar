package io.github.kvmy666.duostatusbar.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.DuoSettingsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Mirrors the private constants: 2 processes may die, and the count stays armed for 2 minutes. */
    private val maxAttempts = 2
    private val crashWindowMs = 120_000L

    private fun register(settings: DuoSettings) {
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?
            ): Cursor? = MatrixCursor(DuoPrefs.COLUMNS).apply {
                addRow(DuoSettingsProvider.rowFor(settings, 1L))
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(
                uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
            ): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY, provider)
    }

    @Test
    fun `once this process has taken the risk, the other bars may use Rive`() {
        assertTrue(DuoGuard.shouldAllowRive(maxAttempts, attemptedThisProcess = true, attemptAgeMs = 0L))
    }

    @Test
    fun `a count below the limit is allowed`() {
        assertTrue(DuoGuard.shouldAllowRive(0, attemptedThisProcess = false, attemptAgeMs = 0L))
        assertTrue(DuoGuard.shouldAllowRive(1, attemptedThisProcess = false, attemptAgeMs = 0L))
    }

    @Test
    fun `a full, recent count is a crash loop and is refused`() {
        assertFalse(DuoGuard.shouldAllowRive(maxAttempts, attemptedThisProcess = false, attemptAgeMs = 1_000L))
        assertFalse(DuoGuard.shouldAllowRive(9, attemptedThisProcess = false, attemptAgeMs = crashWindowMs))
    }

    @Test
    fun `a full but stale count is allowed again`() {
        assertTrue(
            DuoGuard.shouldAllowRive(maxAttempts, attemptedThisProcess = false, attemptAgeMs = crashWindowMs + 1)
        )
    }

    @Test
    fun `canvas engine maps to icons only`() {
        register(DuoSettings(enabled = true, engineMode = DuoPrefs.ENGINE_CANVAS, useRive = false))
        assertEquals(DuoGuard.ICONS_ONLY, DuoGuard(context).stage())
    }

    @Test
    fun `auto engine maps to rive`() {
        register(DuoSettings(enabled = true, engineMode = DuoPrefs.ENGINE_AUTO, useRive = true))
        assertEquals(DuoGuard.RIVE, DuoGuard(context).stage())
    }

    @Test
    fun `rive engine maps to rive`() {
        register(DuoSettings(enabled = true, engineMode = DuoPrefs.ENGINE_RIVE, useRive = true))
        assertEquals(DuoGuard.RIVE, DuoGuard(context).stage())
    }
}
