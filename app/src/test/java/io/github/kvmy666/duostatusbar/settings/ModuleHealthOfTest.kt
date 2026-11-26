package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ModuleHealthCheck.of reads both orientations so an unsaved landscape copy (which falls back to
 * portrait) cannot make a running module look switched off. The pure decide() rule is tested elsewhere;
 * this covers of()'s both-orientation OR and its order against the "never loaded" states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleHealthOfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearCapability() {
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, "")
    }

    /** Writes the two stamps the check reads, plus the status that keeps OK from becoming NOT_REPORTING. */
    private fun stampFresh(now: Long) {
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", now)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", now)
        DuoPrefs.writeStatus(context, "loaded")
    }

    /** The module's capability report, written the way the module half will write it. */
    private fun report(
        owner: String,
        supported: Boolean,
        at: Long,
        route: String = if (owner == IosCapability.OWNER_IOS) {
            IosCapability.ROUTE_V2
        } else {
            IosCapability.ROUTE_NONE
        }
    ) {
        val value = IosCapability(
            owner = owner,
            family = "oxygenos",
            supported = supported,
            route = route,
            revision = 1L,
            pid = 7,
            at = at
        )
        Settings.Global.putString(context.contentResolver, IosCapability.KEY, value.format())
    }

    @Test
    fun `one enabled orientation is not reported disabled`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = true), DuoOrientation.LANDSCAPE)

        assertNotEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `unsaved landscape falls back to the portrait switch`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(context, DuoSettings(enabled = true), DuoOrientation.PORTRAIT)

        assertNotEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `both orientations off is disabled`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.LANDSCAPE)

        assertEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `never loaded stays never loaded when off`() {
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_last_load", 0L)
        Settings.Global.putLong(context.contentResolver, "duo_statusbar_heartbeat", 0L)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(enabled = false), DuoOrientation.LANDSCAPE)

        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `a fresh ios owner report is ios active`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.LANDSCAPE
        )
        report(IosCapability.OWNER_IOS, supported = true, at = now)

        assertEquals(ModuleState.IOS_ACTIVE, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `a fresh ios owner without the live route is not ios active`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.LANDSCAPE
        )
        // The activation-refusal/fallback window: the owner tag survived but no v2 route is live.
        report(
            IosCapability.OWNER_IOS,
            supported = true,
            at = now,
            route = IosCapability.ROUTE_NONE
        )

        val state = ModuleHealthCheck.of(context).state

        assertNotEquals(ModuleState.IOS_ACTIVE, state)
        assertEquals(ModuleState.OK, state)
    }

    @Test
    fun `a refused ios activation with the owner tag is unsupported`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.LANDSCAPE
        )
        report(
            IosCapability.OWNER_IOS,
            supported = false,
            at = now,
            route = IosCapability.ROUTE_NONE
        )

        assertEquals(ModuleState.IOS_UNSUPPORTED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `ios mode in landscape is read too`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = false),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.LANDSCAPE
        )
        report(IosCapability.OWNER_DUO, supported = false, at = now)

        assertEquals(ModuleState.IOS_UNSUPPORTED, ModuleHealthCheck.of(context).state)
    }

    @Test
    fun `ios mode with a stale report is not ios active and not disabled`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.LANDSCAPE
        )
        // Written before the module's current load stamp: a previous session, so UNKNOWN.
        report(IosCapability.OWNER_DUO, supported = false, at = now - 1L)

        val state = ModuleHealthCheck.of(context).state

        assertNotEquals(ModuleState.IOS_ACTIVE, state)
        assertNotEquals(ModuleState.IOS_UNSUPPORTED, state)
        assertNotEquals(ModuleState.DISABLED, state)
        assertEquals(ModuleState.OK, state)
    }

    @Test
    fun `ios mode with no report is not disabled`() {
        val now = System.currentTimeMillis()
        stampFresh(now)
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, iosMode = true),
            DuoOrientation.LANDSCAPE
        )

        assertNotEquals(ModuleState.DISABLED, ModuleHealthCheck.of(context).state)
    }
}
