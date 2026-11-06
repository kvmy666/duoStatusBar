package io.github.kvmy666.duostatusbar.hook

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTimeHelperTest {

    @Test
    fun `24-hour format is hour and minutes with no am pm`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 50)
        }
        val text = formatClockTime(true, cal.time, Locale.US)
        assertEquals("10:50", text)
        assertTrue(text.contains(":"))
        assertFalse(text.contains("AM", ignoreCase = true))
        assertFalse(text.contains("PM", ignoreCase = true))
    }

    @Test
    fun `12-hour format is hour and minutes with no am pm`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 50)
        }
        val text = formatClockTime(false, cal.time, Locale.US)
        assertEquals("10:50", text)
        assertTrue(text.contains(":"))
        assertFalse(text.contains("AM", ignoreCase = true))
        assertFalse(text.contains("PM", ignoreCase = true))
    }

    @Test
    fun `long overload matches date overload`() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 7) }
        assertEquals(
            formatClockTime(true, cal.time, Locale.US),
            formatClockTime(true, cal.timeInMillis, Locale.US)
        )
    }
}
