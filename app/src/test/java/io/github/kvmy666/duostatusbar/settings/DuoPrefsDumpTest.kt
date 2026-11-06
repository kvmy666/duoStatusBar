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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoPrefsDumpTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a dump over 2MB is capped and gets the truncation marker`() {
        val input = "x".repeat(2 * 1024 * 1024 + 1000)
        DuoPrefs.writeDump(context, input)
        val stored = DuoPrefs.dump(context)
        val marker = "\n[truncated 2MB]"
        assertTrue("stored dump must not exceed 2MB plus the marker",
            stored.length <= 2 * 1024 * 1024 + marker.length)
        assertTrue("truncated dump must end with the marker", stored.endsWith(marker))
        val content = stored.removeSuffix(marker)
        assertTrue("content must be capped at 2MB", content.length <= 2 * 1024 * 1024)
    }

    @Test
    fun `a normal dump is stored unchanged`() {
        val input = "hello dump\nsecond line"
        DuoPrefs.writeDump(context, input)
        assertEquals(input, DuoPrefs.dump(context))
    }

    @Test
    fun `truncation does not split a UTF-16 surrogate pair`() {
        val cap = 2 * 1024 * 1024
        val input = "a".repeat(cap - 1) + "\uD83D\uDE00" + "b".repeat(1000)
        DuoPrefs.writeDump(context, input)
        val stored = DuoPrefs.dump(context)
        val marker = "\n[truncated 2MB]"
        assertTrue(stored.endsWith(marker))
        val content = stored.removeSuffix(marker)
        assertFalse("truncation must not leave a lone high surrogate",
            content.isNotEmpty() && Character.isHighSurrogate(content[content.length - 1]))
    }
}
