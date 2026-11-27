package io.github.kvmy666.duostatusbar.settings

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Android half of [SnapshotRefresh]: a module that never loaded is not asked (a report must not wait
 * for a process that cannot answer), while a loaded module gets one explicit request to System UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnapshotRefreshAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun clearStamps() {
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", 0L)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", 0L)
        DuoPrefs.writeStatus(context, "")
        DuoPrefs.writeDump(context, "")
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", 0L)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", 0L)
    }

    @Test
    fun `a never-loaded module is not asked and the wait is skipped`() {
        clearStamps()
        var sleeps = 0

        val out = SnapshotRefresh.refresh(
            context,
            now = { 0L },
            sleep = { sleeps++ }
        )

        assertFalse(out.refreshed)
        assertTrue("a never-loaded module must not be polled", sleeps == 0)
        assertTrue(
            "a never-loaded module must not receive the request",
            Shadows.shadowOf(app).broadcastIntents.none {
                it.action == DuoPrefs.ACTION_DIAGNOSTICS_REQUEST
            }
        )
    }

    // The automatic pulse's collector path: AutoReportManager.collectorRead wraps the same snapshot.
    @Test
    fun `the auto report collector reads the shared snapshot without a module`() {
        clearStamps()
        val failures = mutableListOf<String>()

        val snapshot = AutoReportManager.collectorRead("snapshot", failures) {
            SnapshotRefresh.refresh(context, now = { 0L }, sleep = { })
        }

        assertTrue(snapshot != null)
        assertFalse(snapshot!!.refreshed)
        assertTrue("a successful read must not be recorded as a failure: $failures", failures.isEmpty())
    }

    @Test
    fun `a loaded module is asked once on the System UI package`() {
        clearStamps()
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", 1L)
        var now = 0L

        val out = SnapshotRefresh.refresh(
            context,
            now = { now },
            sleep = { now += it },
            timeoutMs = 300L,
            pollMs = 100L
        )

        assertFalse(out.refreshed)
        val request = Shadows.shadowOf(app).broadcastIntents.firstOrNull {
            it.action == DuoPrefs.ACTION_DIAGNOSTICS_REQUEST
        }
        assertTrue("the diagnostics request was not sent", request != null)
        assertTrue(
            "the request must target System UI, not a hard-coded package",
            request!!.`package` == RomDetection.AOSP
        )
    }
}
