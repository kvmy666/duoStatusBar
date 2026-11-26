package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The new capability strings must exist in English and Arabic, and the detail copy must carry the UI
 * family label. Resource lookups only: the repo has no Compose UI test dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosStringsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val ids = listOf(
        R.string.settings_ios_unsupported,
        R.string.settings_ios_unsupported_detail,
        R.string.module_health_ios_title,
        R.string.module_health_ios_text,
        R.string.module_health_ios_unsupported_title,
        R.string.module_health_ios_unsupported_text,
        R.string.home_status_ios,
        R.string.home_status_ios_unsupported
    )

    @Test
    fun `english capability strings resolve and format the family`() {
        ids.forEach { id ->
            assertTrue("string resource $id is blank", context.getString(id).isNotBlank())
        }
        assertEquals(
            "iOS mode is not supported on OxygenOS yet.",
            context.getString(R.string.settings_ios_unsupported_detail, "OxygenOS")
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "ar")
    fun `arabic capability strings resolve`() {
        ids.forEach { id ->
            assertTrue("Arabic string resource $id is blank", context.getString(id).isNotBlank())
        }
        assertEquals("غير متاح على هذه الواجهة", context.getString(R.string.settings_ios_unsupported))
        assertEquals(
            "وضع iOS غير مدعوم على OxygenOS بعد.",
            context.getString(R.string.settings_ios_unsupported_detail, "OxygenOS")
        )
        assertEquals("وضع iOS مفعّل", context.getString(R.string.module_health_ios_title))
        assertEquals("وضع iOS غير متاح", context.getString(R.string.module_health_ios_unsupported_title))
        assertEquals("iOS مفعّل", context.getString(R.string.home_status_ios))
        assertEquals("iOS غير مدعوم", context.getString(R.string.home_status_ios_unsupported))
    }
}
