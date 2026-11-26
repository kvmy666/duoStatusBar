package io.github.kvmy666.duostatusbar

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.concurrent.atomic.AtomicInteger

/**
 * A side process must never overwrite the main process's stored status or dump: the writer guard has
 * to return before it touches the app's provider or sends the broadcast bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoSettingsClientGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val providerCalls = AtomicInteger()
    private val broadcasts = AtomicInteger()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            broadcasts.incrementAndGet()
        }
    }

    @Before
    fun setUp() {
        ProcessIdentity.resetForTest()
        L.clearRecentForTest()
        providerCalls.set(0)
        broadcasts.set(0)

        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
                providerCalls.incrementAndGet()
                // A provider that cannot answer forces the broadcast fallback, so both writer paths
                // would be visible if the guard failed.
                throw IllegalStateException("provider unavailable in this test")
            }
            override fun query(
                uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?
            ): Cursor? = null
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(
                uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
            ): Int = 0
        }
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY, provider)

        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(SettingsBridge.ACTION_STATUS_PUSH)
                addAction(SettingsBridge.ACTION_DUMP_PUSH)
                addAction(SettingsBridge.ACTION_FALLBACK_PUSH)
            },
            Context.RECEIVER_EXPORTED
        )
    }

    @After
    fun tearDown() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Throwable) {
        }
        ProcessIdentity.resetForTest()
        L.clearRecentForTest()
    }

    @Test
    fun `a side process refuses status dump and fallback writes`() {
        ProcessIdentity.forceForTest("com.android.systemui:edgelighting", 4242)

        DuoSettingsClient.report(context, "status-value")
        DuoSettingsClient.reportDump(context, "dump-value")
        DuoSettingsClient.reportFallback(context, "fallback-value")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("provider calls must be refused", 0, providerCalls.get())
        assertEquals("no broadcast may leave a side process", 0, broadcasts.get())
        val log = L.recentText()
        assertTrue(
            "refusal log missing: $log",
            log.contains("status write refused: side process=com.android.systemui:edgelighting pid=4242")
        )
    }

    @Test
    fun `a null-name main process writes after markMainProcess`() {
        ProcessIdentity.forceForTest(null, 4242)
        assertFalse(ProcessIdentity.isMainSystemUi())

        // MainHook installs on the package fallback even when the runtime cannot name the process,
        // so the writer gate must accept the same load once it has been marked.
        ProcessIdentity.markMainProcess()

        DuoSettingsClient.report(context, "status-value")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a marked main process must attempt the write", 1, providerCalls.get())
    }

    @Test
    fun `the main process still writes`() {
        ProcessIdentity.forceForTest("com.android.systemui", 4242)

        DuoSettingsClient.report(context, "status-value")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the main process must attempt the write", 1, providerCalls.get())
    }
}
