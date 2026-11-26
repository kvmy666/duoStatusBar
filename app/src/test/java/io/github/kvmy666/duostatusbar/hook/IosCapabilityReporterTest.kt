package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.IosCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R6/D13/D14: the module's capability report uses the shared [IosCapability] wire format, maps the
 * live owner/support/route truthfully, and does not churn `Settings.Global` when nothing changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosCapabilityReporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun features(owner: BarOwner): FeatureSet = FeatureSet(
        owner = owner,
        renderer = if (owner == BarOwner.DUO) DuoRenderer.RIVE else DuoRenderer.NONE,
        duo = emptySet(),
        shared = SharedHook.entries.toSet()
    )

    @Before
    fun setUp() {
        IosAvailability.resetForTest()
        IosCapabilityReporter.resetForTest()
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, "")
    }

    @Test
    fun `the report uses the shared wire format and parses back`() {
        val capability = IosCapabilityReporter.capabilityFor(
            features = features(BarOwner.IOS),
            family = "oxygenos",
            route = IosRoute.V2,
            revision = 42L,
            supported = true,
            pid = 1234,
            at = 1_700_000_000_000L
        )

        assertEquals(
            "owner=ios family=oxygenos supported=true route=v2 rev=42 pid=1234 at=1700000000000",
            capability.format()
        )
        assertEquals(capability, IosCapability.parse(capability.format()))
    }

    @Test
    fun `owner supported and route follow the live owner and the route failure`() {
        val failed = IosCapabilityReporter.capabilityFor(
            features = features(BarOwner.DUO),
            family = "samsung",
            route = IosRoute.NONE,
            revision = 7L,
            supported = false,
            pid = 9,
            at = 10L
        )
        assertEquals(IosCapability.OWNER_DUO, failed.owner)
        assertFalse(failed.supported)
        assertEquals(IosCapability.ROUTE_NONE, failed.route)

        val active = IosCapabilityReporter.capabilityFor(
            features = features(BarOwner.IOS),
            family = "oxygenos",
            route = IosRoute.V2,
            revision = 8L,
            supported = true,
            pid = 9,
            at = 10L
        )
        assertEquals(IosCapability.OWNER_IOS, active.owner)
        assertTrue(active.supported)
        assertEquals(IosCapability.ROUTE_V2, active.route)
    }

    @Test
    fun `a route failure makes the default supported flag false`() {
        IosAvailability.routeFailed = true

        val capability = IosCapabilityReporter.capabilityFor(
            features = features(BarOwner.IOS),
            family = "oxygenos",
            route = IosRoute.NONE,
            revision = 3L,
            pid = 11,
            at = 12L
        )

        assertFalse(capability.supported)
    }

    @Test
    fun `the de-dupe key ignores the wall clock and changes with real state`() {
        val original = IosCapabilityReporter.capabilityFor(
            features = features(BarOwner.DUO),
            family = "aosp",
            route = IosRoute.NONE,
            revision = 1L,
            supported = false,
            pid = 5,
            at = 100L
        )
        val later = original.copy(at = 200L)

        assertEquals(IosCapabilityReporter.stateKey(original), IosCapabilityReporter.stateKey(later))
        assertFalse(IosCapabilityReporter.shouldWrite(IosCapabilityReporter.stateKey(original), later))

        assertTrue(IosCapabilityReporter.shouldWrite(IosCapabilityReporter.stateKey(original), original.copy(supported = true)))
        assertTrue(IosCapabilityReporter.shouldWrite(IosCapabilityReporter.stateKey(original), original.copy(route = "v2")))
        assertTrue(IosCapabilityReporter.shouldWrite(IosCapabilityReporter.stateKey(original), original.copy(revision = 2L)))
        assertTrue(IosCapabilityReporter.shouldWrite(null, original))
    }

    @Test
    fun `an unchanged live state writes Global once`() {
        val before = Settings.Global.getString(context.contentResolver, IosCapability.KEY)

        IosCapabilityReporter.report(context, features(BarOwner.DUO), "aosp", IosRoute.NONE, 3L)
        val first = Settings.Global.getString(context.contentResolver, IosCapability.KEY)
        assertNotEquals(before, first)
        val parsed = IosCapability.parse(first)!!
        assertEquals(IosCapability.OWNER_DUO, parsed.owner)
        assertEquals("aosp", parsed.family)
        assertEquals(IosCapability.ROUTE_NONE, parsed.route)
        assertEquals(3L, parsed.revision)
        assertTrue(parsed.supported)

        // Overwrite with a marker: a second identical report must be a no-op (the marker survives).
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, "marker")
        IosCapabilityReporter.report(context, features(BarOwner.DUO), "aosp", IosRoute.NONE, 3L)
        assertEquals("marker", Settings.Global.getString(context.contentResolver, IosCapability.KEY))
    }

    @Test
    fun `a fresh route failure does rewrite the report as unsupported`() {
        IosCapabilityReporter.report(context, features(BarOwner.IOS), "oxygenos", IosRoute.V2, 4L)
        assertTrue(IosCapability.parse(Settings.Global.getString(context.contentResolver, IosCapability.KEY))!!.supported)

        IosAvailability.routeFailed = true
        IosCapabilityReporter.report(context, features(BarOwner.IOS), "oxygenos", IosRoute.NONE, 4L)

        val parsed = IosCapability.parse(Settings.Global.getString(context.contentResolver, IosCapability.KEY))!!
        assertEquals(IosCapability.OWNER_IOS, parsed.owner)
        assertEquals(IosCapability.ROUTE_NONE, parsed.route)
        assertFalse(parsed.supported)
    }
}
