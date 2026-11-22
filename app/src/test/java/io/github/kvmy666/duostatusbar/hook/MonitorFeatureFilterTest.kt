package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3A A6: the state monitor registers only the receivers its active features need. The lists are exact,
 * sorted and deterministic, so the runtime can diff them when settings change live.
 */
class MonitorFeatureFilterTest {

    private val shared = SharedHook.entries.toSet()

    private fun features(vararg duo: DuoFeature, owner: BarOwner = BarOwner.DUO): FeatureSet =
        FeatureSet(owner, if (owner == BarOwner.DUO) DuoRenderer.RIVE else DuoRenderer.NONE, duo.toSet(), shared)

    private fun actions(vararg duo: DuoFeature): List<String> = monitorActions(features(*duo))

    private val battery = "android.intent.action.BATTERY_CHANGED"
    private val powerSave = "android.os.action.POWER_SAVE_MODE_CHANGED"
    private val airplane = "android.intent.action.AIRPLANE_MODE"
    private val interruptionFilter = "android.app.action.INTERRUPTION_FILTER_CHANGED"
    private val ringerMode = "android.media.RINGER_MODE_CHANGED"
    private val screenOff = "android.intent.action.SCREEN_OFF"
    private val screenOn = "android.intent.action.SCREEN_ON"
    private val userPresent = "android.intent.action.USER_PRESENT"
    private val phoneState = "android.intent.action.PHONE_STATE"
    private val configuration = "android.intent.action.CONFIGURATION_CHANGED"
    private val timeChanged = "android.intent.action.TIME_SET"
    private val timezoneChanged = "android.intent.action.TIMEZONE_CHANGED"
    private val localeChanged = "android.intent.action.LOCALE_CHANGED"
    private val timeTick = "android.intent.action.TIME_TICK"
    private val nextAlarm = "android.app.action.NEXT_ALARM_CLOCK_CHANGED"
    private val wifiRssi = "android.net.wifi.RSSI_CHANGED"
    private val wifiState = "android.net.wifi.WIFI_STATE_CHANGED"
    private val connectivity = "android.net.conn.CONNECTIVITY_CHANGE"
    private val serviceState = "android.intent.action.SERVICE_STATE"
    private val anyDataState = "android.intent.action.ANY_DATA_STATE"

    private val baseline = listOf(
        battery,
        powerSave,
        screenOff,
        screenOn,
        configuration,
        timeChanged,
        timezoneChanged,
        localeChanged
    ).sorted()

    private fun expected(vararg extra: String): List<String> = (baseline + extra).sorted()

    @Test
    fun `a bare Duo bar registers only the baseline receivers`() {
        assertEquals(baseline, actions(DuoFeature.RING))
        assertFalse(actions(DuoFeature.RING).contains(timeTick))
    }

    @Test
    fun `time tick follows the clock or burn-in feature`() {
        assertEquals(expected(timeTick), actions(DuoFeature.RING, DuoFeature.CLOCK_FONT))
        assertEquals(expected(timeTick), actions(DuoFeature.RING, DuoFeature.BURN_IN))
        assertEquals(expected(timeTick), actions(DuoFeature.RING, DuoFeature.CLOCK_FONT, DuoFeature.BURN_IN))
        assertFalse(actions(DuoFeature.RING).contains(timeTick))
    }

    @Test
    fun `download adds its broadcast only while download is on`() {
        assertFalse(actions(DuoFeature.RING).contains(ACTION_DOWNLOAD))
        assertEquals(expected(ACTION_DOWNLOAD), actions(DuoFeature.RING, DuoFeature.DOWNLOAD))
    }

    @Test
    fun `alarm adds the next-alarm receiver only while the alarm event is on`() {
        assertFalse(actions(DuoFeature.RING).contains(nextAlarm))
        assertEquals(expected(nextAlarm), actions(DuoFeature.RING, DuoFeature.EVENT_ALARM))
    }

    @Test
    fun `call adds phone state only while the call event is on`() {
        assertFalse(actions(DuoFeature.RING).contains(phoneState))
        assertEquals(expected(phoneState), actions(DuoFeature.RING, DuoFeature.EVENT_CALL))
    }

    @Test
    fun `unlock adds user present only while the unlock event is on`() {
        assertFalse(actions(DuoFeature.RING).contains(userPresent))
        assertEquals(expected(userPresent), actions(DuoFeature.RING, DuoFeature.EVENT_UNLOCK))
    }

    @Test
    fun `airplane adds its receiver only while airplane is on`() {
        assertFalse(actions(DuoFeature.RING).contains(airplane))
        assertEquals(expected(airplane), actions(DuoFeature.RING, DuoFeature.AIRPLANE))
    }

    @Test
    fun `DND adds the filter and ringer receivers only while DND is drawn`() {
        assertFalse(actions(DuoFeature.RING).contains(interruptionFilter))
        assertFalse(actions(DuoFeature.RING).contains(ringerMode))
        assertEquals(
            expected(interruptionFilter, ringerMode),
            actions(DuoFeature.RING, DuoFeature.DND)
        )
    }

    @Test
    fun `Wi-Fi receivers follow the Wi-Fi glyph and Wi-Fi dots features`() {
        val wifi = expected(wifiRssi, wifiState, connectivity)
        assertEquals(wifi, actions(DuoFeature.RING, DuoFeature.WIFI_GLYPH))
        val dots = actions(DuoFeature.RING, DuoFeature.WIFI_DOTS)
        assertTrue(dots.containsAll(listOf(wifiRssi, wifiState, connectivity)))
        assertFalse(actions(DuoFeature.RING).contains(wifiRssi))
        assertFalse(actions(DuoFeature.RING).contains(wifiState))
        assertFalse(actions(DuoFeature.RING).contains(connectivity))
    }

    @Test
    fun `cell receivers follow the cell dots feature`() {
        assertEquals(
            expected(connectivity, serviceState, anyDataState),
            actions(DuoFeature.RING, DuoFeature.CELL_DOTS)
        )
        assertFalse(actions(DuoFeature.RING).contains(serviceState))
        assertFalse(actions(DuoFeature.RING).contains(anyDataState))
    }

    @Test
    fun `Wi-Fi dots also keep the airplane receiver because the dots change with it`() {
        assertEquals(
            expected(airplane, wifiRssi, wifiState, connectivity),
            actions(DuoFeature.RING, DuoFeature.WIFI_DOTS)
        )
    }

    @Test
    fun `flash and screenshot events add no receiver`() {
        assertEquals(baseline, actions(DuoFeature.RING, DuoFeature.EVENT_FLASHLIGHT))
        assertEquals(baseline, actions(DuoFeature.RING, DuoFeature.EVENT_SCREENSHOT))
    }

    @Test
    fun `a fully active Duo bar registers the whole contract exactly once`() {
        val all = actions(*DuoFeature.entries.toTypedArray()).sorted()
        assertEquals(all.distinct(), all)
        assertTrue(all.containsAll(baseline))
        assertEquals(
            sortedSetOf(
                battery,
                powerSave,
                airplane,
                interruptionFilter,
                ringerMode,
                screenOff,
                screenOn,
                userPresent,
                phoneState,
                configuration,
                timeChanged,
                timezoneChanged,
                localeChanged,
                timeTick,
                nextAlarm,
                wifiRssi,
                wifiState,
                connectivity,
                serviceState,
                anyDataState,
                ACTION_DOWNLOAD
            ).toList(),
            all
        )
    }

    @Test
    fun `the default enabled settings register exactly the default actions`() {
        val featureSet = featureSetFor(
            ModuleSettings.DEFAULT.copy(enabled = true),
            adbOverride = null,
            iosDrawingReady = true,
            otherOrientationEnabled = false
        )
        // Defaults: clock font on, network glyph/dots on, DND middle, download on, all events on.
        assertEquals(
            sortedSetOf(
                battery,
                powerSave,
                airplane,
                interruptionFilter,
                ringerMode,
                screenOff,
                screenOn,
                userPresent,
                phoneState,
                configuration,
                timeChanged,
                timezoneChanged,
                localeChanged,
                timeTick,
                nextAlarm,
                wifiRssi,
                wifiState,
                connectivity,
                serviceState,
                anyDataState,
                ACTION_DOWNLOAD
            ).toList(),
            monitorActions(featureSet)
        )
    }

    @Test
    fun `iOS and none register no state-monitor receivers`() {
        for (owner in listOf(BarOwner.IOS, BarOwner.NONE)) {
            val featureSet = FeatureSet(owner, DuoRenderer.NONE, emptySet(), shared)
            assertTrue(monitorActions(featureSet).isEmpty())
            assertTrue(monitorActionsForRuntime(featureSet, clockSlotShowsClock = true).isEmpty())
        }
    }

    @Test
    fun `the clock middle slot keeps the minute tick without the system-font switch`() {
        val bare = features(DuoFeature.RING)
        val withoutClock = monitorActionsForRuntime(bare, clockSlotShowsClock = false)
        assertEquals(baseline, withoutClock)
        assertFalse(withoutClock.contains(timeTick))

        val withClock = monitorActionsForRuntime(bare, clockSlotShowsClock = true)
        assertEquals(expected(timeTick), withClock)
    }

    @Test
    fun `a feature-driven tick is not duplicated by the clock slot`() {
        val clockFont = features(DuoFeature.RING, DuoFeature.CLOCK_FONT)
        assertEquals(
            monitorActionsForRuntime(clockFont, clockSlotShowsClock = true),
            monitorActionsForRuntime(clockFont, clockSlotShowsClock = false)
        )
        assertEquals(expected(timeTick), monitorActionsForRuntime(clockFont, clockSlotShowsClock = true))
    }
}
