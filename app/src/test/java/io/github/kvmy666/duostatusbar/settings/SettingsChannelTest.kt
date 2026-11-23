package io.github.kvmy666.duostatusbar.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.content.res.Configuration
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/**
 * The app ↔ module channel is the newest surface and the one with the least device coverage, so its contract
 * is pinned here: the settings must survive a round trip through the provider *as the module reads them*, and
 * the provider must expose exactly the columns the module asks for.
 *
 * This is the test that catches the failure mode I cannot see from either side alone: the app writes
 * `size_percent`, the module reads `size_percent`, and a typo in one of those strings would otherwise be
 * discovered by a user whose size slider does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsChannelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `settings survive a write and a read`() {
        val written = DuoSettings(
            enabled = true,
            useRive = false,
            showPercent = false,
            sizePercent = 120,
            offsetX = -12,
            splitIndicators = true,
            indicatorsOffsetX = 36,
            tapAction = "toggle_flashlight",
            doubleTapAction = "show_notifications",
            longPressAction = "take_screenshot",
            indicatorsTapAction = "show_quick_settings",
            indicatorsDoubleTapAction = "toggle_wifi",
            indicatorsLongPressAction = "toggle_power_saving",
            iconColor = "black",
            hideOtherIcons = false
        )
        DuoPrefs.write(context, written)
        // Writing useRive=false with the default AUTO engine normalises to an effective CANVAS engine.
        assertEquals(written.copy(engineMode = DuoPrefs.ENGINE_CANVAS), DuoPrefs.read(context))
    }

    @Test
    fun `older split settings inherit ring actions for the icons`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit()
            .clear()
            .putString(DuoPrefs.COL_TAP, "toggle_flashlight")
            .putString(DuoPrefs.COL_DOUBLE_TAP, "show_notifications")
            .putString(DuoPrefs.COL_LONG_PRESS, "take_screenshot")
            .apply()

        val read = DuoPrefs.read(context, DuoOrientation.PORTRAIT)

        assertEquals(read.tapAction, read.indicatorsTapAction)
        assertEquals(read.doubleTapAction, read.indicatorsDoubleTapAction)
        assertEquals(read.longPressAction, read.indicatorsLongPressAction)
    }

    @Test
    fun `unlocked ring and indicator offsets retain their two hundred dp cap`() {
        DuoPrefs.write(
            context,
            DuoSettings(sizePercent = 9_999, offsetX = 9_999, percentHeight = 9_999, indicatorsOffsetX = 9_999)
        )
        val read = DuoPrefs.read(context)
        assertEquals(DuoPrefs.MAX_SIZE, read.sizePercent)
        assertEquals(0, read.offsetX)
        assertEquals(DuoPrefs.MAX_PERCENT_HEIGHT, read.percentHeight)
        assertEquals(DuoPrefs.MAX_INDICATORS_OFFSET, read.indicatorsOffsetX)
    }

    @Test
    fun `screen locked ring offset is not artificially capped`() {
        DuoPrefs.write(
            context,
            DuoSettings(splitIndicators = true, lockRing = true, offsetX = 9_999)
        )

        assertEquals(9_999, DuoPrefs.read(context).offsetX)
    }

    @Test
    fun `normal ring offset clamps to the right-anchored range`() {
        DuoPrefs.write(
            context,
            DuoSettings(splitIndicators = true, lockRing = false, offsetX = 9_999)
        )
        assertEquals(0, DuoPrefs.read(context).offsetX)

        DuoPrefs.write(
            context,
            DuoSettings(splitIndicators = true, lockRing = false, offsetX = -9_999)
        )
        assertEquals(-DuoPrefs.MAX_UNLOCKED_RING_OFFSET, DuoPrefs.read(context).offsetX)
    }

    @Test
    fun `lockRing alone switches centered mode regardless of splitIndicators`() {
        DuoPrefs.write(
            context,
            DuoSettings(splitIndicators = false, lockRing = true, offsetX = 9_999)
        )
        assertEquals(9_999, DuoPrefs.read(context).offsetX)
    }

    @Test
    fun `the revision moves on every write so the module can notice changes`() {
        val first = DuoPrefs.write(context, DuoSettings(enabled = true))
        val second = DuoPrefs.write(context, DuoSettings(enabled = false))
        assertTrue("revision must increase", second > first)
    }

    @Test
    fun `the row the module reads has one value per column, in the declared order`() {
        val row = DuoSettingsProvider.rowFor(
            DuoSettings(
                enabled = true,
                useRive = false,
                showPercent = false,
                sizePercent = 130,
                offsetX = 7,
                tapAction = "toggle_flashlight",
                doubleTapAction = "no_action",
                longPressAction = "take_screenshot",
                indicatorsTapAction = "show_quick_settings",
                indicatorsDoubleTapAction = "toggle_wifi",
                indicatorsLongPressAction = "toggle_power_saving",
                percentHeight = 40,
                splitIndicators = true,
                indicatorsOffsetX = -18,
                downloadProgressEnabled = false
            ),
            revision = 42L
        )
        assertEquals(
            "a column added or reordered without the module being updated is the silent failure this guards",
            DuoPrefs.COLUMNS.size,
            row.size
        )
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_ENABLED)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_USE_RIVE)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_PERCENT)])
        assertEquals(130, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SIZE_PERCENT)])
        assertEquals(7, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_OFFSET_X)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_LIVE_APPLY)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_CLOCK_FONT)])
        assertEquals(42L, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_REVISION)])
        assertEquals("toggle_flashlight", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_TAP)])
        assertEquals("no_action", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DOUBLE_TAP)])
        assertEquals("take_screenshot", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_LONG_PRESS)])
        assertEquals("show_quick_settings", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_INDICATORS_TAP)])
        assertEquals("toggle_wifi", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_INDICATORS_DOUBLE_TAP)])
        assertEquals("toggle_power_saving", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_INDICATORS_LONG_PRESS)])
        assertEquals(1000, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_REVEAL_MS)])
        // FR-15b / FR-08b: the two newest columns must travel too, or the module would never see them.
        assertEquals("auto", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_ICON_COLOR)])
        // Default is now FR-08b (keep the other icons), so an untouched setting travels as 0.
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_HIDE_OTHER_ICONS)])
        // Both statuses may take the middle by default, so the legacy "hide both" column stays off.
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_NETWORK_ONLY)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_AIRPLANE)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_DND)])
        assertEquals(DuoPrefs.DND_MIDDLE, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DND_MODE)])
        // The dual-SIM line choice travels as a string, defaulting to the automatic data line.
        assertEquals("auto", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SIM_CHOICE)])
        assertEquals(40, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_PERCENT_HEIGHT)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SPLIT_INDICATORS)])
        assertEquals(-18, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_INDICATORS_OFFSET_X)])
        // Off unless the user asks: a missing choice keeps the cellular dots.
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_WIFI_DOTS)])
        assertEquals(DuoPrefs.DEFAULT_EDGE_PADDING, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_EDGE_PADDING)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_LOCK_RING)])
        // The download switch travels in both sets; burn-in and the per-icon list follow it.
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DOWNLOAD_PROGRESS)])
        assertEquals(
            0,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DOWNLOAD_PROGRESS)]
        )
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_BURN_IN)])
        assertEquals(
            0,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_BURN_IN)]
        )
        // Per-icon hiding is empty until the user picks icons; it travels in both sets too.
        assertEquals("", row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_HIDDEN_STOCK_ICONS)])
        assertEquals(
            "",
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDDEN_STOCK_ICONS)]
        )
        // The classic drawing is the default, in both orientations.
        assertEquals(DuoPrefs.DESIGN_OLD, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DESIGN_STYLE)])
        assertEquals(
            DuoPrefs.DESIGN_OLD,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DESIGN_STYLE)]
        )
        // One set published alone is copied into the landscape columns, so rotation does not go blank.
        assertEquals(130, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_SIZE_PERCENT)])
        assertEquals(7, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_OFFSET_X)])
    }

    @Test
    fun `the iOS columns are appended last without moving design style or hidden stock icons`() {
        // Append-only: existing installs read the old positions, so the new columns may never move.
        assertEquals(107, DuoPrefs.COLUMNS.size)
        assertEquals(54, DuoPrefs.PORTRAIT_COLUMNS.size)
        assertEquals(53, DuoPrefs.LANDSCAPE_COLUMNS.size)
        assertEquals(DuoPrefs.COL_HIDE_EXTRAS, DuoPrefs.PORTRAIT_COLUMNS.last())
        assertEquals(DuoPrefs.COL_IOS_PERCENT_INSIDE, DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 1])
        assertEquals(DuoPrefs.COL_SIM_DISPLAY, DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 2])
        assertEquals(
            DuoPrefs.COL_IOS_CELL_SCALE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 3]
        )
        assertEquals(
            DuoPrefs.COL_IOS_CELL_OFFSET,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 4]
        )
        assertEquals(
            DuoPrefs.COL_IOS_WIFI_SCALE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 5]
        )
        assertEquals(
            DuoPrefs.COL_IOS_WIFI_OFFSET,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 6]
        )
        assertEquals(
            DuoPrefs.COL_IOS_BATTERY_SCALE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 7]
        )
        assertEquals(
            DuoPrefs.COL_IOS_BATTERY_OFFSET,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 8]
        )
        assertEquals(
            DuoPrefs.COL_IOS_MODE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 9]
        )
        assertEquals(
            DuoPrefs.COL_DESIGN_STYLE,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 10]
        )
        assertEquals(
            DuoPrefs.COL_HIDDEN_STOCK_ICONS,
            DuoPrefs.PORTRAIT_COLUMNS[DuoPrefs.PORTRAIT_COLUMNS.lastIndex - 11]
        )
        assertArrayEquals(
            "the iOS tuning columns and the R3/R8 toggles stay in append-only order after COL_IOS_MODE",
            arrayOf(
                DuoPrefs.COL_IOS_MODE,
                DuoPrefs.COL_IOS_BATTERY_OFFSET,
                DuoPrefs.COL_IOS_BATTERY_SCALE,
                DuoPrefs.COL_IOS_WIFI_OFFSET,
                DuoPrefs.COL_IOS_WIFI_SCALE,
                DuoPrefs.COL_IOS_CELL_OFFSET,
                DuoPrefs.COL_IOS_CELL_SCALE,
                DuoPrefs.COL_SIM_DISPLAY,
                DuoPrefs.COL_IOS_PERCENT_INSIDE,
                DuoPrefs.COL_HIDE_EXTRAS
            ),
            DuoPrefs.PORTRAIT_COLUMNS.copyOfRange(
                DuoPrefs.PORTRAIT_COLUMNS.size - 10,
                DuoPrefs.PORTRAIT_COLUMNS.size
            )
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDE_EXTRAS,
            DuoPrefs.LANDSCAPE_COLUMNS.last()
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_PERCENT_INSIDE,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 1]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_SIM_DISPLAY,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 2]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_CELL_SCALE,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 3]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_CELL_OFFSET,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 4]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_WIFI_SCALE,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 5]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_WIFI_OFFSET,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 6]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_BATTERY_SCALE,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 7]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_BATTERY_OFFSET,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 8]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_MODE,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 9]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DESIGN_STYLE,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 10]
        )
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDDEN_STOCK_ICONS,
            DuoPrefs.LANDSCAPE_COLUMNS[DuoPrefs.LANDSCAPE_COLUMNS.lastIndex - 11]
        )
        assertEquals(107, DuoSettingsProvider.rowFor(DuoSettings(), 1L).size)

        val oldOrder = arrayOf(
            DuoPrefs.COL_ENABLED, DuoPrefs.COL_USE_RIVE, DuoPrefs.COL_SHOW_PERCENT,
            DuoPrefs.COL_SIZE_PERCENT, DuoPrefs.COL_OFFSET_X, DuoPrefs.COL_LIVE_APPLY,
            DuoPrefs.COL_CLOCK_FONT, DuoPrefs.COL_REVISION, DuoPrefs.COL_TAP,
            DuoPrefs.COL_DOUBLE_TAP, DuoPrefs.COL_LONG_PRESS, DuoPrefs.COL_REVEAL_MS,
            DuoPrefs.COL_ANIMATIONS, DuoPrefs.COL_ARRIVAL, DuoPrefs.COL_DEPARTURE,
            DuoPrefs.COL_CHARGING, DuoPrefs.COL_ICON_COLOR, DuoPrefs.COL_HIDE_OTHER_ICONS,
            DuoPrefs.COL_NETWORK_ONLY, DuoPrefs.COL_SIM_CHOICE, DuoPrefs.COL_PERCENT_HEIGHT,
            DuoPrefs.COL_SPLIT_INDICATORS, DuoPrefs.COL_INDICATORS_OFFSET_X, DuoPrefs.COL_WIFI_DOTS,
            DuoPrefs.COL_SHOW_AIRPLANE, DuoPrefs.COL_SHOW_DND, DuoPrefs.COL_EDGE_PADDING,
            DuoPrefs.COL_DND_MODE, DuoPrefs.COL_SHOW_CHARGING_BOLT, DuoPrefs.COL_LOCK_RING,
            DuoPrefs.COL_INDICATORS_TAP, DuoPrefs.COL_INDICATORS_DOUBLE_TAP,
            DuoPrefs.COL_INDICATORS_LONG_PRESS, DuoPrefs.COL_SHOW_IN_QS,
            DuoPrefs.COL_SHOW_IN_SHADE, DuoPrefs.COL_ENGINE_MODE, DuoPrefs.COL_MIDDLE_SLOT,
            DuoPrefs.COL_SHOW_WIFI_GLYPH, DuoPrefs.COL_SHOW_CELL_DOTS, DuoPrefs.COL_EVENT_MASK,
            DuoPrefs.COL_DOWNLOAD_PROGRESS
        )
        assertArrayEquals(
            "appending new columns must not move any column an existing install already reads",
            oldOrder,
            DuoPrefs.PORTRAIT_COLUMNS.copyOf(oldOrder.size)
        )
    }

    @Test
    fun `bridge row keeps the pre-R2 prefix and the R2 tail, then appends R3`() {
        val portrait = DuoSettings(
            enabled = true,
            sizePercent = 130,
            offsetX = -7,
            iosBatteryOffset = 11,
            iosBatteryScale = 120,
            iosWifiOffset = -9,
            iosWifiScale = 80,
            iosCellOffset = 5,
            iosCellScale = 140,
            simDisplay = DuoPrefs.SIM_DISPLAY_DUAL,
            iosPercentInside = true
        )
        val landscape = DuoSettings(
            enabled = false,
            sizePercent = 170,
            offsetX = -22,
            iosBatteryOffset = -12,
            iosBatteryScale = 130,
            iosWifiOffset = 6,
            iosWifiScale = 90,
            iosCellOffset = -4,
            iosCellScale = 110,
            simDisplay = DuoPrefs.SIM_DISPLAY_SINGLE,
            iosPercentInside = false,
            hideExtras = false
        )

        val standard = DuoSettingsProvider.rowFor(portrait, 42L, landscape)
        val bridge = DuoSettingsProvider.bridgeRowFor(portrait, 42L, landscape)

        assertEquals(107, bridge.size)
        assertEquals(
            89,
            DuoPrefs.LEGACY_PORTRAIT_COUNT + DuoPrefs.LEGACY_LANDSCAPE_COUNT
        )
        // 0..44 is the pre-R2 portrait prefix; 45..88 is the pre-R2 landscape prefix (standard 54..97).
        assertArrayEquals(standard.copyOfRange(0, 45), bridge.copyOfRange(0, 45))
        assertArrayEquals(standard.copyOfRange(54, 98), bridge.copyOfRange(45, 89))
        // 89..95 is the R2 portrait tail; 96..102 is the R2 landscape tail (standard 98..104). Together
        // these are the first 103 values, byte-layout-identical to what an R2 module reads and truncates.
        assertArrayEquals(standard.copyOfRange(45, 52), bridge.copyOfRange(89, 96))
        assertArrayEquals(standard.copyOfRange(98, 105), bridge.copyOfRange(96, 103))
        // Positional anchors: an insertion or reorder anywhere in the legacy prefixes moves one of
        // these names, even though the "first 103 unchanged" copies above would still match.
        assertEquals(DuoPrefs.COL_ENABLED, DuoPrefs.BRIDGE_COLUMNS[0])
        assertEquals(DuoPrefs.COL_IOS_MODE, DuoPrefs.BRIDGE_COLUMNS[44])
        // Index 45 is the first landscape legacy column, not the battery offset (that anchor is 89):
        // the requested [45] = COL_IOS_BATTERY_OFFSET cannot hold, since 45 starts LANDSCAPE_COLUMNS.
        assertEquals(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_ENABLED, DuoPrefs.BRIDGE_COLUMNS[45])
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_MODE,
            DuoPrefs.BRIDGE_COLUMNS[88]
        )
        assertEquals(DuoPrefs.COL_IOS_BATTERY_OFFSET, DuoPrefs.BRIDGE_COLUMNS[89])
        // 103/104 are the R7 prefix, unchanged: the R3 percent pair (portrait then landscape). 105/106
        // are the R8 hide pair. Every post-R2 generation is a portrait+landscape pair in append order.
        assertEquals(DuoPrefs.COL_IOS_PERCENT_INSIDE, DuoPrefs.BRIDGE_COLUMNS[103])
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_PERCENT_INSIDE,
            DuoPrefs.BRIDGE_COLUMNS[104]
        )
        assertEquals(DuoPrefs.COL_HIDE_EXTRAS, DuoPrefs.BRIDGE_COLUMNS[105])
        assertEquals(
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDE_EXTRAS,
            DuoPrefs.BRIDGE_COLUMNS[106]
        )
        assertEquals(1, bridge[103])
        assertEquals(0, bridge[104])
        assertEquals(1, bridge[105])
        assertEquals(0, bridge[106])
        assertArrayEquals(standard.copyOfRange(52, 53), bridge.copyOfRange(103, 104))
        assertArrayEquals(standard.copyOfRange(105, 106), bridge.copyOfRange(104, 105))
        assertArrayEquals(standard.copyOfRange(53, 54), bridge.copyOfRange(105, 106))
        assertArrayEquals(standard.copyOfRange(106, 107), bridge.copyOfRange(106, 107))
        // Distinctive values, so a portrait/landscape swap at either tail cannot pass.
        assertEquals(11, bridge[89])
        assertEquals(120, bridge[90])
        assertEquals(-12, bridge[96])
        assertEquals(130, bridge[97])
        assertEquals(DuoPrefs.SIM_DISPLAY_DUAL, bridge[95])
        assertEquals(DuoPrefs.SIM_DISPLAY_SINGLE, bridge[102])
    }

    @Test
    fun `bridge values map by name for a new 107-value push`() {
        val portrait = DuoSettings(
            enabled = true,
            sizePercent = 130,
            offsetX = -7,
            iosBatteryOffset = 11,
            iosBatteryScale = 120,
            iosWifiOffset = -9,
            iosWifiScale = 80,
            iosCellOffset = 5,
            iosCellScale = 140,
            simDisplay = DuoPrefs.SIM_DISPLAY_DUAL,
            iosPercentInside = true
        )
        val landscape = DuoSettings(
            enabled = false,
            sizePercent = 170,
            offsetX = -22,
            iosBatteryOffset = -12,
            iosBatteryScale = 130,
            iosWifiOffset = 6,
            iosWifiScale = 90,
            iosCellOffset = -4,
            iosCellScale = 110,
            simDisplay = DuoPrefs.SIM_DISPLAY_SINGLE,
            iosPercentInside = false,
            hideExtras = false
        )

        val bridge = DuoSettingsProvider.bridgeRowFor(portrait, 42L, landscape)
        val row = DuoSettingsClient.bridgeValuesToRow(bridge.toList())
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row) }
        cursor.moveToFirst()

        val inPortrait = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(11, inPortrait.iosBatteryOffset)
        assertEquals(120, inPortrait.iosBatteryScale)
        assertEquals(-9, inPortrait.iosWifiOffset)
        assertEquals(80, inPortrait.iosWifiScale)
        assertEquals(5, inPortrait.iosCellOffset)
        assertEquals(140, inPortrait.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_DUAL, inPortrait.simDisplay)
        assertEquals(true, inPortrait.iosPercentInside)
        assertEquals(true, inPortrait.hideExtras)
        assertEquals(130, inPortrait.sizePercent)
        assertEquals(-7, inPortrait.offsetX)
        assertEquals(42L, inPortrait.revision)

        val inLandscape = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(-12, inLandscape.iosBatteryOffset)
        assertEquals(130, inLandscape.iosBatteryScale)
        assertEquals(6, inLandscape.iosWifiOffset)
        assertEquals(90, inLandscape.iosWifiScale)
        assertEquals(-4, inLandscape.iosCellOffset)
        assertEquals(110, inLandscape.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_SINGLE, inLandscape.simDisplay)
        assertEquals(false, inLandscape.iosPercentInside)
        assertEquals(false, inLandscape.hideExtras)
        assertEquals(170, inLandscape.sizePercent)
        assertEquals(-22, inLandscape.offsetX)
        assertEquals(42L, inLandscape.revision)

        // An older module may still push the pre-R2 89 values; the new tail must read as defaults and
        // the old fields must stay intact.
        val full = DuoSettingsProvider.rowFor(portrait, 42L, landscape)
        val legacy = full.copyOfRange(0, 45).toList() + full.copyOfRange(54, 98).toList()
        assertEquals(89, legacy.size)
        val legacyCursor = MatrixCursor(DuoPrefs.COLUMNS).apply {
            addRow(DuoSettingsClient.bridgeValuesToRow(legacy))
        }
        legacyCursor.moveToFirst()

        val oldPortrait =
            DuoSettingsClient.fromCursor(legacyCursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(0, oldPortrait.iosBatteryOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, oldPortrait.iosBatteryScale)
        assertEquals(0, oldPortrait.iosWifiOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, oldPortrait.iosWifiScale)
        assertEquals(0, oldPortrait.iosCellOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, oldPortrait.iosCellScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_AUTO, oldPortrait.simDisplay)
        assertEquals(false, oldPortrait.iosPercentInside)
        assertEquals(true, oldPortrait.hideExtras)
        assertEquals(130, oldPortrait.sizePercent)
        assertEquals(-7, oldPortrait.offsetX)
        assertEquals(42L, oldPortrait.revision)

        val oldLandscape =
            DuoSettingsClient.fromCursor(legacyCursor, Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(0, oldLandscape.iosBatteryOffset)
        assertEquals(DuoPrefs.DEFAULT_ICON_SCALE, oldLandscape.iosBatteryScale)
        assertEquals(DuoPrefs.SIM_DISPLAY_AUTO, oldLandscape.simDisplay)
        assertEquals(false, oldLandscape.iosPercentInside)
        assertEquals(true, oldLandscape.hideExtras)
        assertEquals(170, oldLandscape.sizePercent)
        assertEquals(-22, oldLandscape.offsetX)
        assertEquals(42L, oldLandscape.revision)
    }

    @Test
    fun `download progress still round trips after burn-in is appended`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(
            context,
            DuoSettings(downloadProgressEnabled = false),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(downloadProgressEnabled = true),
            DuoOrientation.LANDSCAPE
        )
        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertFalse(portrait.downloadProgressEnabled)
        assertTrue(landscape.downloadProgressEnabled)

        val row = DuoSettingsProvider.rowFor(portrait, revision = 1L, landscape)
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DOWNLOAD_PROGRESS)])
        assertEquals(
            1,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_DOWNLOAD_PROGRESS)]
        )
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        assertFalse(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
                .downloadProgressEnabled
        )
        assertTrue(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE)
                .downloadProgressEnabled
        )
    }

    @Test
    fun `burn-in round trips through prefs, the provider row, and the module client`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(context, DuoSettings(burnInEnabled = true), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(burnInEnabled = false), DuoOrientation.LANDSCAPE)

        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertTrue(portrait.burnInEnabled)
        assertFalse(landscape.burnInEnabled)

        val row = DuoSettingsProvider.rowFor(portrait, revision = 1L, landscape)
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_BURN_IN)])
        assertEquals(
            0,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_BURN_IN)]
        )

        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        assertTrue(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT).burnInEnabled
        )
        assertFalse(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE).burnInEnabled
        )
    }

    @Test
    fun `ios mode round trips through shared preferences`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(
            context,
            DuoSettings(iosMode = true),
            DuoOrientation.PORTRAIT
        )
        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        assertTrue(portrait.iosMode)

        DuoPrefs.write(
            context,
            DuoSettings(iosMode = false),
            DuoOrientation.LANDSCAPE
        )
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertFalse(landscape.iosMode)
    }

    @Test
    fun `percentage inside round trips and defaults off in an older row`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(context, DuoSettings(iosPercentInside = true), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(iosPercentInside = false), DuoOrientation.LANDSCAPE)

        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertTrue(portrait.iosPercentInside)
        assertFalse(landscape.iosPercentInside)

        val row = DuoSettingsProvider.rowFor(portrait, revision = 1L, landscape)
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_IOS_PERCENT_INSIDE)])
        assertEquals(
            0,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_PERCENT_INSIDE)]
        )
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        assertTrue(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT).iosPercentInside
        )
        assertFalse(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE).iosPercentInside
        )

        // An older app publishes neither the portrait column nor its landscape copy: off is today's battery.
        val dropped = listOf(
            DuoPrefs.COL_IOS_PERCENT_INSIDE,
            DuoPrefs.LAND_PREFIX + DuoPrefs.COL_IOS_PERCENT_INSIDE
        )
        val columns = DuoPrefs.COLUMNS.filter { it !in dropped }.toTypedArray()
        val values = DuoSettingsProvider.rowFor(DuoSettings(iosPercentInside = true), 1L).toMutableList()
        dropped.map { DuoPrefs.COLUMNS.indexOf(it) }.sortedDescending().forEach { values.removeAt(it) }
        val oldCursor = MatrixCursor(columns).apply { addRow(values) }
        oldCursor.moveToFirst()
        assertFalse(
            DuoSettingsClient.fromCursor(oldCursor, Configuration.ORIENTATION_PORTRAIT).iosPercentInside
        )
        assertFalse(
            DuoSettingsClient.fromCursor(oldCursor, Configuration.ORIENTATION_LANDSCAPE).iosPercentInside
        )
    }

    @Test
    fun `hidden stock icons round trip through prefs, the provider row, and the module client`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(
            context,
            DuoSettings(
                hideOtherIcons = true,
                hiddenStockIcons = "wifi, battery,alarm,battery"
            ),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(hideOtherIcons = false, hiddenStockIcons = "clock"),
            DuoOrientation.LANDSCAPE
        )

        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertEquals("alarm,battery,wifi", portrait.hiddenStockIcons)
        assertEquals("clock", landscape.hiddenStockIcons)

        val row = DuoSettingsProvider.rowFor(portrait, revision = 1L, landscape)
        assertEquals(
            "alarm,battery,wifi",
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_HIDDEN_STOCK_ICONS)]
        )
        assertEquals(
            "clock",
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDDEN_STOCK_ICONS)]
        )
        // The legacy "hide all" switch is still its own value, not implied by an icon list.
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_HIDE_OTHER_ICONS)])
        assertEquals(
            0,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_HIDE_OTHER_ICONS)]
        )

        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        val clientPortrait =
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        val clientLandscape =
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE)
        assertEquals("alarm,battery,wifi", clientPortrait.hiddenStockIcons)
        assertEquals("clock", clientLandscape.hiddenStockIcons)
        assertTrue(clientPortrait.hideOtherIcons)
        assertFalse(clientLandscape.hideOtherIcons)
    }

    @Test
    fun `a row without design style keeps the classic drawing`() {
        val columns = DuoPrefs.PORTRAIT_COLUMNS
            .filter { it != DuoPrefs.COL_DESIGN_STYLE }
            .toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(designStyle = DuoPrefs.DESIGN_NEW), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_DESIGN_STYLE))
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        cursor.moveToFirst()

        assertEquals(
            DuoPrefs.DESIGN_OLD,
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT).designStyle
        )
    }

    @Test
    fun `a row without burn-in keeps the original fixed seat`() {
        val columns = DuoPrefs.PORTRAIT_COLUMNS
            .filter { it != DuoPrefs.COL_BURN_IN }
            .toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(burnInEnabled = true), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_BURN_IN))
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        cursor.moveToFirst()

        assertFalse(
            DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT).burnInEnabled
        )
    }

    @Test
    fun `airplane and do not disturb travel through the channel on their own`() {
        DuoPrefs.write(context, DuoSettings(showAirplane = true, dndMode = DuoPrefs.DND_OFF))
        val read = DuoPrefs.read(context)
        assertEquals(true, read.showAirplane)
        assertEquals(DuoPrefs.DND_OFF, read.dndMode)
        val row = DuoSettingsProvider.rowFor(read, revision = 1L)
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_AIRPLANE)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_DND)])
        assertEquals(DuoPrefs.DND_OFF, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DND_MODE)])
        // One of them is still allowed, so an older module must not treat this as "hide both".
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_NETWORK_ONLY)])
    }

    @Test
    fun `an old network-only choice keeps both statuses out until the user picks again`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DuoPrefs.COL_NETWORK_ONLY, true)
            .apply()
        val read = DuoPrefs.read(context)
        assertEquals(false, read.showAirplane)
        assertEquals(DuoPrefs.DND_OFF, read.dndMode)
    }

    @Test
    fun `edge spacing travels through the channel and can be zero`() {
        DuoPrefs.write(context, DuoSettings(edgePadding = 0))
        assertEquals(0, DuoPrefs.read(context).edgePadding)
        DuoPrefs.write(context, DuoSettings(edgePadding = 9_999))
        assertEquals(DuoPrefs.MAX_EDGE_PADDING, DuoPrefs.read(context).edgePadding)
        val row = DuoSettingsProvider.rowFor(DuoSettings(edgePadding = 25), revision = 1L)
        assertEquals(25, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_EDGE_PADDING)])
        assertEquals(25, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_EDGE_PADDING)])
    }

    @Test
    fun `locking the ring travels through the channel and defaults off`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(context, DuoSettings(lockRing = true, splitIndicators = true), DuoOrientation.PORTRAIT)
        DuoPrefs.write(context, DuoSettings(lockRing = false, sizePercent = 110), DuoOrientation.LANDSCAPE)
        assertEquals(true, DuoPrefs.read(context, DuoOrientation.PORTRAIT).lockRing)
        assertEquals(false, DuoPrefs.read(context, DuoOrientation.LANDSCAPE).lockRing)
        val row = DuoSettingsProvider.rowFor(
            DuoPrefs.read(context, DuoOrientation.PORTRAIT),
            revision = 1L,
            DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        )
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_LOCK_RING)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_LOCK_RING)])
        val columns = DuoPrefs.PORTRAIT_COLUMNS.filter { it != DuoPrefs.COL_LOCK_RING }.toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(lockRing = true), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_LOCK_RING))
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        cursor.use {
            it.moveToFirst()
            val read = DuoSettingsClient.fromCursor(it, Configuration.ORIENTATION_PORTRAIT)
            assertEquals(false, read.lockRing)
        }
    }

    @Test
    fun `a row without edge spacing keeps the full slot`() {
        val columns = DuoPrefs.PORTRAIT_COLUMNS.filter { it != DuoPrefs.COL_EDGE_PADDING }.toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(sizePercent = 100), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_EDGE_PADDING))
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        cursor.moveToFirst()
        val read = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(DuoPrefs.DEFAULT_EDGE_PADDING, read.edgePadding)
    }

    @Test
    fun `do not disturb on the signal dots travels through the channel`() {
        DuoPrefs.write(context, DuoSettings(dndMode = DuoPrefs.DND_DOTS))
        assertEquals(DuoPrefs.DND_DOTS, DuoPrefs.read(context).dndMode)
        val row = DuoSettingsProvider.rowFor(DuoPrefs.read(context), revision = 1L)
        assertEquals(DuoPrefs.DND_DOTS, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_DND_MODE)])
        // Dots do not take the middle, so an older module must not also draw the moon there.
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_DND)])
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_NETWORK_ONLY)])
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        val read = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(DuoPrefs.DND_DOTS, read.dndMode)
        assertEquals(false, read.showDnd)
    }

    @Test
    fun `a row without the do not disturb mode keeps the older switch`() {
        val columns = DuoPrefs.PORTRAIT_COLUMNS.filter { it != DuoPrefs.COL_DND_MODE }.toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(dndMode = DuoPrefs.DND_MIDDLE), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_DND_MODE))
        val on = MatrixCursor(columns).apply { addRow(values) }
        on.moveToFirst()
        assertEquals(DuoPrefs.DND_MIDDLE, DuoSettingsClient.fromCursor(on, Configuration.ORIENTATION_PORTRAIT).dndMode)

        val offValues = values.toMutableList()
        offValues[columns.indexOf(DuoPrefs.COL_SHOW_DND)] = 0
        val off = MatrixCursor(columns).apply { addRow(offValues) }
        off.moveToFirst()
        assertEquals(DuoPrefs.DND_OFF, DuoSettingsClient.fromCursor(off, Configuration.ORIENTATION_PORTRAIT).dndMode)
    }

    @Test
    fun `the wifi-dots toggle travels through the channel`() {
        DuoPrefs.write(context, DuoSettings(wifiDots = true))
        assertEquals(true, DuoPrefs.read(context).wifiDots)
        val row = DuoSettingsProvider.rowFor(DuoPrefs.read(context), revision = 1L)
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_WIFI_DOTS)])
    }

    @Test
    fun `the QS and shade surface toggles travel through the channel`() {
        DuoPrefs.write(context, DuoSettings(showInQs = false, showInShade = true))
        val read = DuoPrefs.read(context)
        assertEquals(false, read.showInQs)
        assertEquals(true, read.showInShade)
        val row = DuoSettingsProvider.rowFor(read, revision = 1L)
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_IN_QS)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_IN_SHADE)])
        assertEquals(
            0,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.LAND_PREFIX + DuoPrefs.COL_SHOW_IN_QS)]
        )
    }

    @Test
    fun `a row without the surface toggles keeps both surfaces on`() {
        val columns = DuoPrefs.PORTRAIT_COLUMNS
            .filter { it != DuoPrefs.COL_SHOW_IN_QS && it != DuoPrefs.COL_SHOW_IN_SHADE }
            .toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_SHOW_IN_SHADE))
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_SHOW_IN_QS))
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        cursor.moveToFirst()
        val read = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(true, read.showInQs)
        assertEquals(true, read.showInShade)
    }

    @Test
    fun `an arrival duration is snapped to one the Rive file can actually play`() {
        // The file holds one timeline at five speeds. Anything between two of them has to become one of
        // them, or the state machine would simply never fire and the element would never arrive.
        for (choice in DuoPrefs.REVEAL_CHOICES) {
            assertEquals(choice, DuoPrefs.nearestReveal(choice))
            assertEquals("just under $choice", choice, DuoPrefs.nearestReveal(choice - 1))
        }
        assertEquals(500, DuoPrefs.nearestReveal(0))
        assertEquals(1500, DuoPrefs.nearestReveal(99_999))
        // 624 is nearer 500 than 750; 626 tips the other way. The boundary is the midpoint.
        assertEquals(500, DuoPrefs.nearestReveal(624))
        assertEquals(750, DuoPrefs.nearestReveal(626))
    }

    @Test
    fun `a provider that cannot be reached is not mistaken for the user switching the module off`() {
        // The ColorOS/realme boot failure: the provider does not answer. It has to be read as
        // "unreachable" (so the module retries), never as the default `enabled=false` (which would pin the
        // module to stage 0 and make it look switched off). A provider that returns no cursor is exactly
        // what a dead one does on device.
        val dead = object : ContentProvider() {
            override fun onCreate() = true
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
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY, dead)

        val result = DuoSettingsClient.read(context)
        assertFalse("an unreachable provider must not look like an explicit 'off'", result.enabled)
        assertTrue("the failure has to be distinguishable to the stage logic", DuoSettingsClient.providerUnreachable)
    }

    @Test
    fun `status history keeps the newest reports and ignores repeats`() {
        DuoPrefs.writeStatus(context, "stage=1 renderer=Canvas")
        DuoPrefs.writeStatus(context, "stage=1 renderer=Canvas")   // repeat: ignored
        DuoPrefs.writeStatus(context, "stage=2 renderer=Rive")
        assertEquals("stage=2 renderer=Rive", DuoPrefs.status(context))
        val history = DuoPrefs.statusHistory(context)
        assertEquals("the repeat must not be stored twice", 2, history.size)
        assertTrue(history.last().endsWith("stage=2 renderer=Rive"))
    }

    @Test
    fun `portrait and landscape settings are stored apart`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(
            context,
            DuoSettings(enabled = true, sizePercent = 80, offsetX = -10, iconColor = "black"),
            DuoOrientation.PORTRAIT
        )
        DuoPrefs.write(
            context,
            DuoSettings(enabled = false, sizePercent = 160, offsetX = -30, iconColor = "white", wifiDots = true),
            DuoOrientation.LANDSCAPE
        )
        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertEquals(80, portrait.sizePercent)
        assertEquals(-10, portrait.offsetX)
        assertEquals(true, portrait.enabled)
        assertEquals("black", portrait.iconColor)
        assertEquals(false, portrait.wifiDots)
        assertEquals(160, landscape.sizePercent)
        assertEquals(-30, landscape.offsetX)
        assertEquals(false, landscape.enabled)
        assertEquals("white", landscape.iconColor)
        assertEquals(true, landscape.wifiDots)
    }

    @Test
    fun `landscape starts as a copy of portrait until it is edited`() {
        context.getSharedPreferences("duo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        DuoPrefs.write(context, DuoSettings(sizePercent = 140, offsetX = -12, showPercent = false), DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        assertEquals(140, landscape.sizePercent)
        assertEquals(-12, landscape.offsetX)
        assertEquals(false, landscape.showPercent)
    }

    @Test
    fun `the module reads the set that matches the status bar orientation`() {
        val portrait = DuoSettings(
            enabled = true,
            sizePercent = 80,
            offsetX = 4,
            tapAction = "toggle_flashlight",
            percentHeight = 20
        )
        val landscape = DuoSettings(
            enabled = false,
            sizePercent = 170,
            offsetX = -22,
            tapAction = "take_screenshot",
            percentHeight = 90,
            splitIndicators = true,
            wifiDots = true,
            dndMode = DuoPrefs.DND_DOTS
        )
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply {
            addRow(DuoSettingsProvider.rowFor(portrait, 7L, landscape).toList())
        }
        cursor.moveToFirst()
        val inPortrait = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        val inLandscape = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(80, inPortrait.sizePercent)
        assertEquals(4, inPortrait.offsetX)
        assertEquals(true, inPortrait.enabled)
        assertEquals("toggle_flashlight", inPortrait.tapAction)
        assertEquals(20, inPortrait.percentHeight)
        assertEquals(7L, inPortrait.revision)
        assertEquals(170, inLandscape.sizePercent)
        assertEquals(-22, inLandscape.offsetX)
        assertEquals(false, inLandscape.enabled)
        assertEquals("take_screenshot", inLandscape.tapAction)
        assertEquals(90, inLandscape.percentHeight)
        assertEquals(true, inLandscape.splitIndicators)
        assertEquals(true, inLandscape.wifiDots)
        assertEquals(DuoPrefs.DND_DOTS, inLandscape.dndMode)
        assertEquals(DuoPrefs.DND_MIDDLE, inPortrait.dndMode)
        assertEquals(7L, inLandscape.revision)
    }

    @Test
    fun `a row without landscape columns keeps the portrait set in landscape`() {
        val full = DuoSettingsProvider.rowFor(DuoSettings(sizePercent = 111, offsetX = 6), 3L)
        val cursor = MatrixCursor(DuoPrefs.PORTRAIT_COLUMNS).apply {
            addRow(full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toList())
        }
        cursor.moveToFirst()
        val inLandscape = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(111, inLandscape.sizePercent)
        assertEquals(6, inLandscape.offsetX)
        assertEquals(3L, inLandscape.revision)
    }

    @Test
    fun `engineMode round trips for auto rive and canvas`() {
        for (mode in listOf(DuoPrefs.ENGINE_AUTO, DuoPrefs.ENGINE_RIVE, DuoPrefs.ENGINE_CANVAS)) {
            val written = DuoSettings(enabled = true, engineMode = mode, useRive = mode != DuoPrefs.ENGINE_CANVAS)
            DuoPrefs.write(context, written)
            assertEquals(written, DuoPrefs.read(context))
        }
    }

    @Test
    fun `legacy useRive false maps to canvas`() {
        val written = DuoSettings(enabled = true, useRive = false)
        DuoPrefs.write(context, written)
        val read = DuoPrefs.read(context)
        assertEquals(DuoPrefs.ENGINE_CANVAS, read.engineMode)
        assertFalse(read.useRive)
    }

    @Test
    fun `provider row includes engine_mode`() {
        val row = DuoSettingsProvider.rowFor(DuoSettings(engineMode = DuoPrefs.ENGINE_RIVE), revision = 7L)
        assertEquals(
            DuoPrefs.ENGINE_RIVE,
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_ENGINE_MODE)]
        )
    }

    @Test
    fun `normalizeEngine accepts auto rive canvas and defaults unknown to auto`() {
        assertEquals(DuoPrefs.ENGINE_AUTO, DuoPrefs.normalizeEngine(DuoPrefs.ENGINE_AUTO))
        assertEquals(DuoPrefs.ENGINE_RIVE, DuoPrefs.normalizeEngine(DuoPrefs.ENGINE_RIVE))
        assertEquals(DuoPrefs.ENGINE_CANVAS, DuoPrefs.normalizeEngine(DuoPrefs.ENGINE_CANVAS))
        assertEquals(DuoPrefs.ENGINE_AUTO, DuoPrefs.normalizeEngine("nope"))
        assertEquals(DuoPrefs.ENGINE_AUTO, DuoPrefs.normalizeEngine(null))
    }

    @Test
    fun `middle slot percent round trips and travels through the channel`() {
        DuoPrefs.write(context, DuoSettings(middleSlot = DuoPrefs.MIDDLE_SLOT_PERCENT))
        assertEquals(DuoPrefs.MIDDLE_SLOT_PERCENT, DuoPrefs.read(context).middleSlot)
        val row = DuoSettingsProvider.rowFor(DuoPrefs.read(context), revision = 1L)
        assertEquals(
            "percent",
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_MIDDLE_SLOT)]
        )
    }



    @Test
    fun `middle slot clock round trips and travels through the channel`() {
        DuoPrefs.write(context, DuoSettings(middleSlot = DuoPrefs.MIDDLE_SLOT_CLOCK))
        assertEquals(DuoPrefs.MIDDLE_SLOT_CLOCK, DuoPrefs.read(context).middleSlot)
        val row = DuoSettingsProvider.rowFor(DuoPrefs.read(context), revision = 1L)
        assertEquals(
            "clock",
            row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_MIDDLE_SLOT)]
        )
    }



    @Test
    fun `event mask round trips alarm disabled and screenshot enabled`() {
        val mask = DuoPrefs.EVENT_ALL and DuoPrefs.EVENT_ALARM.inv()
        DuoPrefs.write(context, DuoSettings(eventMask = mask))
        assertEquals(mask, DuoPrefs.read(context).eventMask)
        val row = DuoSettingsProvider.rowFor(DuoPrefs.read(context), revision = 1L)
        assertEquals(mask, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_EVENT_MASK)])
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        val fromRow = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(mask, fromRow.eventMask)
        assertFalse(DuoPrefs.eventEnabled(fromRow.eventMask, DuoPrefs.EVENT_ALARM))
        assertTrue(DuoPrefs.eventEnabled(fromRow.eventMask, DuoPrefs.EVENT_SCREENSHOT))
    }

    @Test
    fun `a row without the event mask defaults to all events`() {
        val columns = DuoPrefs.PORTRAIT_COLUMNS.filter { it != DuoPrefs.COL_EVENT_MASK }.toTypedArray()
        val full = DuoSettingsProvider.rowFor(DuoSettings(), 1L)
        val values = full.copyOfRange(0, DuoPrefs.PORTRAIT_COLUMNS.size).toMutableList()
        values.removeAt(DuoPrefs.PORTRAIT_COLUMNS.indexOf(DuoPrefs.COL_EVENT_MASK))
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        cursor.moveToFirst()
        val read = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(DuoPrefs.EVENT_ALL, read.eventMask)
    }

    @Test
    fun `wifi glyph and cell dots round trip through prefs and provider row`() {
        DuoPrefs.write(context, DuoSettings(showWifiGlyph = false, showCellDots = true))
        val read = DuoPrefs.read(context)
        assertEquals(false, read.showWifiGlyph)
        assertEquals(true, read.showCellDots)
        val row = DuoSettingsProvider.rowFor(read, revision = 1L)
        assertEquals(0, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_WIFI_GLYPH)])
        assertEquals(1, row[DuoPrefs.COLUMNS.indexOf(DuoPrefs.COL_SHOW_CELL_DOTS)])
        val cursor = MatrixCursor(DuoPrefs.COLUMNS).apply { addRow(row.toList()) }
        cursor.moveToFirst()
        val fromRow = DuoSettingsClient.fromCursor(cursor, Configuration.ORIENTATION_PORTRAIT)
        assertEquals(false, fromRow.showWifiGlyph)
        assertEquals(true, fromRow.showCellDots)
    }

}
