package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R6/D13/D14: the module -> app capability report. The app must be able to tell "iOS cannot run here"
 * from "no report yet", so parsing is tolerant but a truncated/unknown report is null (UNKNOWN), never
 * a false "unsupported", and a report from a previous module session is ignored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosCapabilityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val capability = IosCapability(
        owner = IosCapability.OWNER_IOS,
        family = "oxygenos",
        supported = true,
        route = IosCapability.ROUTE_V2,
        revision = 42L,
        pid = 1234,
        at = 1_700_000_000_000L
    )

    @Before
    fun clearReport() {
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, "")
    }

    // ---------------------------------------------------------------- wire format

    @Test
    fun `format writes the documented field order`() {
        assertEquals(
            "owner=ios family=oxygenos supported=true route=v2 rev=42 pid=1234 at=1700000000000",
            capability.format()
        )
    }

    @Test
    fun `parse round trips the encoded report`() {
        assertEquals(capability, IosCapability.parse(capability.format()))
    }

    @Test
    fun `unknown keys are ignored and order does not matter`() {
        val wire = "vendor=acme pid=1234 at=1700000000000 route=v2 family=oxygenos " +
            "supported=true rev=42 owner=ios extra=1"

        assertEquals(capability, IosCapability.parse(wire))
    }

    @Test
    fun `rev and pid are optional but the core fields are required`() {
        val minimal = IosCapability.parse("owner=duo family=aosp supported=false route=none at=5")

        assertEquals(
            IosCapability(IosCapability.OWNER_DUO, "aosp", false, IosCapability.ROUTE_NONE, 0L, 0, 5L),
            minimal
        )
    }

    @Test
    fun `malformed reports are null rather than a guess`() {
        val malformed = listOf(
            null,
            "",
            "   ",
            "not-a-field",
            "=x",
            // missing "at"
            "owner=ios family=aosp supported=true route=v2 rev=1 pid=2",
            // unknown enum values
            "owner=oem family=aosp supported=true route=v2 at=3",
            "owner=ios family=aosp supported=yes route=v2 at=3",
            "owner=ios family=aosp supported=true route=v1 at=3",
            // unparsable numbers
            "owner=ios family=aosp supported=true route=v2 rev=x at=3",
            "owner=ios family=aosp supported=true route=v2 pid=x at=3",
            "owner=ios family=aosp supported=true route=v2 at=x"
        )

        malformed.forEach { wire ->
            assertNull("must reject: $wire", IosCapability.parse(wire))
        }
    }

    // ------------------------------------------------------------------- reading

    @Test
    fun `read accepts a report from the current module session`() {
        write(capability)

        assertEquals(capability, IosCapability.read(context, loadedAt = 1_699_999_999_000L))
        assertEquals(capability, IosCapability.read(context, loadedAt = capability.at))
    }

    @Test
    fun `a report older than the module load is stale`() {
        write(capability)

        assertNull(IosCapability.read(context, loadedAt = capability.at + 1L))
    }

    @Test
    fun `no module session means no report`() {
        write(capability)

        assertNull(IosCapability.read(context, loadedAt = 0L))
    }

    @Test
    fun `blank and malformed stored reports are unknown`() {
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, "")
        assertNull(IosCapability.read(context, loadedAt = 1L))

        Settings.Global.putString(context.contentResolver, IosCapability.KEY, "owner=ios")
        assertNull(IosCapability.read(context, loadedAt = 1L))
    }

    // ------------------------------------------------------------------- labels

    @Test
    fun `family codes map to the user facing UI names`() {
        val expected = mapOf(
            "oxygenos" to "OxygenOS",
            "samsung" to "One UI",
            "hyperos" to "HyperOS",
            "nothing" to "Nothing OS",
            "motorola" to "MyOS",
            "vivo" to "OriginOS",
            "aosp" to "Android",
            "coloros" to "ColorOS",
            "honor" to "MagicOS"
        )

        expected.forEach { (code, label) ->
            assertEquals(code, label, IosCapability.familyLabel(code))
            assertEquals("$code must match case-insensitively", label, IosCapability.familyLabel(code.uppercase(java.util.Locale.ROOT)))
        }
    }

    @Test
    fun `an unknown family is shown verbatim and a missing one says this UI`() {
        assertEquals("calyxos", IosCapability.familyLabel("calyxos"))
        assertEquals("this UI", IosCapability.familyLabel(""))
        assertEquals("this UI", IosCapability.familyLabel("   "))
        assertEquals("this UI", IosCapability.familyLabel(null))
        assertEquals("OxygenOS", IosCapability.availableHint("oxygenos"))
        assertTrue(IosCapability.availableHint(null).isNotBlank())
    }

    private fun write(value: IosCapability) {
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, value.format())
    }
}
