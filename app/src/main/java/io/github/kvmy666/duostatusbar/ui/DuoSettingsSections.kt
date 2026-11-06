package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.DuoRivePreview
import io.github.kvmy666.duostatusbar.DuoRiveStill
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoPart
import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.hook.rawResourceFor
import io.github.kvmy666.duostatusbar.hook.events.LiveEventType
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.StockIconInventory
import io.github.kvmy666.duostatusbar.settings.StockIconSelection
import io.github.kvmy666.duostatusbar.settings.UpdateInfo

// ------------------------------------------------------------------ Battery icon
@Composable
internal fun CustomizeSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate,
    onRestart: () -> Unit
) {
    val previewRaw = rawResourceFor(settings.designStyle)
    if (matches(
            stringResource(R.string.section_customize), stringResource(R.string.section_element),
            stringResource(R.string.settings_master),
            stringResource(R.string.settings_master_detail),             stringResource(R.string.settings_percent),
            stringResource(R.string.settings_percent_detail),
            stringResource(R.string.settings_percent_height),
            stringResource(R.string.settings_percent_height_detail),
            stringResource(R.string.settings_split),
            stringResource(R.string.settings_split_detail),
            stringResource(R.string.settings_lock_ring),
            stringResource(R.string.settings_lock_ring_detail),
            stringResource(R.string.settings_indicators_position),
            stringResource(R.string.settings_edge_padding),
            stringResource(R.string.settings_edge_padding_detail),
            stringResource(R.string.settings_size),
            stringResource(R.string.settings_position), stringResource(R.string.settings_live_apply),
            stringResource(R.string.settings_show_charging_bolt),
            stringResource(R.string.settings_show_charging_bolt_detail)
        )
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_customize))
            Text(
                text = stringResource(R.string.settings_customize_detail),
                style = MaterialTheme.typography.bodySmall
            )
            SettingSwitch(
                label = stringResource(R.string.settings_master),
                detail = stringResource(R.string.settings_master_detail),
                checked = settings.enabled,
                preview = {
                    // Off is the element *absent*: an empty chip, not a ring reading "0".
                    DuoSettingPreview(
                        off = demo(level = 0, showPercent = false),
                        on = demo(),
                        rawRes = previewRaw
                    )
                }
            ) { onUpdate(settings.copy(enabled = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_percent),
                detail = stringResource(R.string.settings_percent_detail),
                checked = settings.showPercent,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(off = demo(showPercent = false), on = demo(showPercent = true), rawRes = previewRaw)
                }
            ) { onUpdate(settings.copy(showPercent = it)) }

            SettingSwitch(
                label = stringResource(R.string.settings_show_charging_bolt),
                detail = stringResource(R.string.settings_show_charging_bolt_detail),
                checked = settings.showChargingBolt,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(
                        off = demo(charging = true, showCharging = false),
                        on = demo(charging = true, showCharging = true),
                        rawRes = previewRaw
                    )
                }
            ) { onUpdate(settings.copy(showChargingBolt = it)) }

            LabelledSlider(
                label = "${stringResource(R.string.settings_percent_height)} ${settings.percentHeight}%",
                value = settings.percentHeight.toFloat(),
                range = DuoPrefs.MIN_PERCENT_HEIGHT.toFloat()..DuoPrefs.MAX_PERCENT_HEIGHT.toFloat(),
                enabled = settings.enabled && settings.showPercent,
                preview = {
                    DuoSettingPreview(
                        off = demo(percentHeight = DuoPrefs.MIN_PERCENT_HEIGHT),
                        on = demo(percentHeight = DuoPrefs.MAX_PERCENT_HEIGHT),
                        rawRes = previewRaw
                    )
                }
            ) { onUpdate(settings.copy(percentHeight = it.toInt())) }
            Text(
                text = stringResource(R.string.settings_percent_height_detail),
                style = MaterialTheme.typography.bodySmall
            )

            SettingSwitch(
                label = stringResource(R.string.settings_split),
                detail = stringResource(R.string.settings_split_detail),
                checked = settings.splitIndicators,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(
                        off = demo(),
                        on = DuoPart.RING.apply(demo()),
                        rawRes = previewRaw
                    )
                }
            ) { onUpdate(settings.copy(splitIndicators = it)) }

            SizeAndPositionControls(
                settings = settings,
                onUpdate = onUpdate,
                onRestart = onRestart,
                includeEdgePadding = true
            )
        }
    }
}

@Composable
private fun SizeAndPositionControls(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    onRestart: () -> Unit,
    includeEdgePadding: Boolean
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1)
    val previewRaw = rawResourceFor(settings.designStyle)
    // The control speaks the logical 0..100 scale; storage and the module keep the physical size.
    val logicalSize = DuoPrefs.physicalToLogical(settings.sizePercent)
    LabelledSlider(
        label = "${stringResource(R.string.settings_size)} $logicalSize%",
        value = logicalSize.toFloat(),
        range = DuoPrefs.MIN_LOGICAL_SIZE.toFloat()..DuoPrefs.MAX_LOGICAL_SIZE.toFloat(),
        enabled = settings.enabled,
        preview = {
            DuoSettingPreview(
                off = demo(),
                on = demo(),
                scaleFrom = DuoPrefs.MIN_SIZE.toFloat() / DuoPrefs.MAX_SIZE,
                rawRes = previewRaw
            )
        }
    ) { onUpdate(settings.copy(sizePercent = DuoPrefs.logicalToPhysical(it.toInt()))) }
    Text(
        text = stringResource(R.string.settings_size_restart),
        style = MaterialTheme.typography.bodySmall
    )
    Button(
        onClick = onRestart,
        enabled = settings.enabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.settings_restart)) }

    SettingSwitch(
        label = stringResource(R.string.settings_live_apply),
        detail = stringResource(R.string.settings_live_apply_detail),
        checked = settings.liveApply,
        enabled = settings.enabled
    ) { onUpdate(settings.copy(liveApply = it)) }

    if (settings.splitIndicators) {
        SettingSwitch(
            label = stringResource(R.string.settings_lock_ring),
            detail = stringResource(R.string.settings_lock_ring_detail),
            checked = settings.lockRing,
            enabled = settings.enabled
        ) { locked ->
            onUpdate(
                settings.copy(
                    lockRing = locked,
                    offsetX = if (locked) {
                        settings.offsetX
                    } else {
                        settings.offsetX.coerceIn(
                            -DuoPrefs.MAX_UNLOCKED_RING_OFFSET,
                            DuoPrefs.MAX_UNLOCKED_RING_OFFSET
                        )
                    }
                )
            )
        }
    }
    PositionEditor(
        offsetDp = settings.offsetX,
        maxOffsetDp = if (settings.lockRing) {
            positionOffsetLimit(screenWidthDp, centered = true)
        } else {
            DuoPrefs.MAX_UNLOCKED_RING_OFFSET
        },
        enabled = settings.enabled,
        centered = settings.lockRing,
        label = if (settings.splitIndicators) stringResource(R.string.settings_ring_position) else null,
        hint = when {
            settings.splitIndicators && settings.lockRing ->
                stringResource(R.string.settings_ring_position_locked_hint)
            settings.splitIndicators -> stringResource(R.string.settings_ring_position_hint)
            else -> null
        },
        visual = if (settings.splitIndicators) {
            DuoPart.RING.apply(demo())
        } else {
            null
        },
        rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo
    ) { onUpdate(settings.copy(offsetX = it)) }
    if (settings.splitIndicators) {
        PositionEditor(
            offsetDp = settings.indicatorsOffsetX,
            maxOffsetDp = DuoPrefs.MAX_INDICATORS_OFFSET,
            enabled = settings.enabled,
            label = stringResource(R.string.settings_indicators_position),
            hint = stringResource(R.string.settings_indicators_position_hint),
            visual = DuoPart.INDICATORS.apply(demo()),
            rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo
        ) { onUpdate(settings.copy(indicatorsOffsetX = it)) }
    }

    if (includeEdgePadding) {
        LabelledSlider(
            label = "${stringResource(R.string.settings_edge_padding)} ${settings.edgePadding}%",
            value = settings.edgePadding.toFloat(),
            range = DuoPrefs.MIN_EDGE_PADDING.toFloat()..DuoPrefs.MAX_EDGE_PADDING.toFloat(),
            enabled = settings.enabled
        ) { onUpdate(settings.copy(edgePadding = it.toInt())) }
        Text(
            text = stringResource(R.string.settings_edge_padding_detail),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// -------------------------------------------------------------------- Animations
@Composable
internal fun AnimationsSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
    val previewRaw = rawResourceFor(settings.designStyle)
    if (matches(
            stringResource(R.string.section_animations), stringResource(R.string.settings_animations),
            stringResource(R.string.settings_anim_speed), stringResource(R.string.settings_anim_arrival),
            stringResource(R.string.settings_anim_departure)
        )
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_animations))
            SettingSwitch(
                label = stringResource(R.string.settings_animations),
                detail = stringResource(R.string.settings_animations_detail),
                checked = settings.animationsEnabled,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(animationsEnabled = it)) }

            // The arrival is one Rive timeline at five speeds, so this is a choice, not a number.
            val speedIndex = (DuoPrefs.REVEAL_CHOICES.size - 1 -
                    DuoPrefs.REVEAL_CHOICES.indexOf(settings.revealMs).coerceAtLeast(0))
            LabelledSlider(
                label = "${stringResource(R.string.settings_anim_speed)}: ${SPEED_LABELS[speedIndex]}",
                value = speedIndex.toFloat(),
                range = 0f..(DuoPrefs.REVEAL_CHOICES.size - 1).toFloat(),
                steps = DuoPrefs.REVEAL_CHOICES.size - 2,
                enabled = settings.enabled && settings.animationsEnabled
            ) { value ->
                val index = (DuoPrefs.REVEAL_CHOICES.size - 1 - value.toInt())
                    .coerceIn(0, DuoPrefs.REVEAL_CHOICES.size - 1)
                onUpdate(settings.copy(revealMs = DuoPrefs.REVEAL_CHOICES[index]))
            }

            SettingSwitch(
                label = stringResource(R.string.settings_anim_arrival),
                detail = stringResource(R.string.settings_anim_arrival_detail),
                checked = settings.arrivalEnabled,
                enabled = settings.enabled && settings.animationsEnabled,
                preview = {
                    DuoRivePreview(fireReveal = true, rawRes = previewRaw) { demo() }
                }
            ) { onUpdate(settings.copy(arrivalEnabled = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_anim_departure),
                detail = stringResource(R.string.settings_anim_departure_detail),
                checked = settings.departureEnabled,
                enabled = settings.enabled && settings.animationsEnabled,
                preview = {
                    DuoRivePreview(fireReveal = true, rawRes = previewRaw) { phase ->
                        if (phase < 0.5f) demo() else demo().copy(visible = false)
                    }
                }
            ) { onUpdate(settings.copy(departureEnabled = it)) }
        }
    }
}

// ------------------------------------------------------------------- Appearance
@Composable
internal fun AppearanceSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
    val previewRaw = rawResourceFor(settings.designStyle)
    if (matches(stringResource(R.string.section_look), stringResource(R.string.settings_icon_color),
            stringResource(R.string.settings_middle_slot),
            stringResource(R.string.settings_show_airplane),
            stringResource(R.string.settings_show_dnd),
            stringResource(R.string.settings_dnd_detail),
            stringResource(R.string.settings_dnd_off),
            stringResource(R.string.settings_dnd_middle),
            stringResource(R.string.settings_dnd_dots),
            stringResource(R.string.settings_wifi_dots),
            stringResource(R.string.settings_wifi_dots_detail),
            stringResource(R.string.settings_sim),
            stringResource(R.string.settings_burn_in),
            stringResource(R.string.settings_burn_in_detail))
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_look))
            OptionPicker(
                label = stringResource(R.string.settings_icon_color),
                options = listOf(
                    "auto" to stringResource(R.string.settings_icon_color_auto),
                    "black" to stringResource(R.string.settings_icon_color_black),
                    "white" to stringResource(R.string.settings_icon_color_white)
                ),
                selectedKey = settings.iconColor,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(iconColor = it)) }
            Text(
                text = stringResource(R.string.settings_middle_slot),
                style = MaterialTheme.typography.titleSmall
            )
            OptionPicker(
                label = stringResource(R.string.settings_middle_slot),
                options = listOf(
                    DuoPrefs.MIDDLE_SLOT_AUTO to stringResource(R.string.middle_slot_auto),
                    DuoPrefs.MIDDLE_SLOT_WIFI to stringResource(R.string.middle_slot_wifi),
                    DuoPrefs.MIDDLE_SLOT_CELL to stringResource(R.string.middle_slot_cell),
                    DuoPrefs.MIDDLE_SLOT_PERCENT to stringResource(R.string.middle_slot_percent),
                    DuoPrefs.MIDDLE_SLOT_CLOCK to stringResource(R.string.middle_slot_clock)
                ),
                selectedKey = settings.middleSlot,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(middleSlot = it)) }
            Text(
                text = stringResource(R.string.settings_middle_slot_detail),
                style = MaterialTheme.typography.bodySmall
            )
            SettingSwitch(
                label = stringResource(R.string.settings_show_airplane),
                detail = null,
                checked = settings.showAirplane,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(
                        off = demo(airplane = true, showAirplane = false),
                        on = demo(airplane = true, showAirplane = true),
                        rawRes = previewRaw
                    )
                }
            ) { onUpdate(settings.copy(showAirplane = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF101014)),
                    contentAlignment = Alignment.Center
                ) {
                    DuoRiveStill(
                        visual = dndPreview(settings.dndMode),
                        modifier = Modifier.fillMaxSize(),
                        rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OptionPicker(
                        label = stringResource(R.string.settings_show_dnd),
                        options = listOf(
                            DuoPrefs.DND_OFF to stringResource(R.string.settings_dnd_off),
                            DuoPrefs.DND_MIDDLE to stringResource(R.string.settings_dnd_middle),
                            DuoPrefs.DND_DOTS to stringResource(R.string.settings_dnd_dots)
                        ),
                        selectedKey = settings.dndMode,
                        enabled = settings.enabled
                    ) { onUpdate(settings.copy(dndMode = it)) }
                    Text(
                        text = stringResource(R.string.settings_dnd_detail),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            SettingSwitch(
                label = stringResource(R.string.settings_wifi_dots),
                detail = stringResource(R.string.settings_wifi_dots_detail),
                checked = settings.wifiDots,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(
                        off = demo(airplane = true, wifiLevel = 2, wifiOn = true),
                        on = demo(airplane = true, wifiLevel = 2, wifiOn = true, wifiDots = true),
                        rawRes = previewRaw
                    )
                }
            ) { onUpdate(settings.copy(wifiDots = it)) }
            OptionPicker(
                label = stringResource(R.string.settings_sim),
                options = listOf(
                    "auto" to stringResource(R.string.settings_sim_auto),
                    "sim1" to stringResource(R.string.settings_sim_1),
                    "sim2" to stringResource(R.string.settings_sim_2)
                ),
                selectedKey = settings.simChoice,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(simChoice = it)) }
            Text(
                text = stringResource(R.string.settings_sim_detail),
                style = MaterialTheme.typography.bodySmall
            )
            SettingSwitch(
                label = stringResource(R.string.settings_burn_in),
                detail = stringResource(R.string.settings_burn_in_detail),
                checked = settings.burnInEnabled,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(burnInEnabled = it)) }
        }
    }
}

// -------------------------------------------------------------- Surfaces
@Composable
internal fun SurfacesSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
    if (matches(
            stringResource(R.string.section_surfaces),
            stringResource(R.string.settings_show_in_qs),
            stringResource(R.string.settings_show_in_qs_detail),
            stringResource(R.string.settings_show_in_shade)
        )
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_surfaces))
            SettingSwitch(
                label = stringResource(R.string.settings_show_in_qs),
                detail = stringResource(R.string.settings_show_in_qs_detail),
                checked = settings.showInQs,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(showInQs = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_show_in_shade),
                detail = stringResource(R.string.settings_show_in_shade_detail),
                checked = settings.showInShade,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(showInShade = it)) }
        }
    }
}

// -------------------------------------------------------------- Status bar icons
// Issue #4: an optional Shizuku path for hiding the stock icons when the module's own
// view-hiding leaves them behind. Independent of the LSPosed module, so it is safe when unused.
@Composable
internal fun IconsSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
    val context = LocalContext.current
    // The module owns the live view tree, so the list arrives as JSON on the settings channel. Reading
    // it on composition (a prefs read plus a small decode) keeps the rows in step after every push.
    val inventory = StockIconInventory.decode(DuoPrefs.readStockIcons(context))
    if (matches(stringResource(R.string.section_icons), stringResource(R.string.settings_hide_icons),
            stringResource(R.string.settings_hide_icons_detail),
            stringResource(R.string.settings_hide_other_icons),
            stringResource(R.string.settings_stock_icons_detail),
            stringResource(R.string.settings_stock_icons_empty),
            stringResource(R.string.settings_show_wifi_glyph),
            stringResource(R.string.settings_show_wifi_glyph_detail),
            stringResource(R.string.settings_show_cell_dots),
            stringResource(R.string.settings_show_cell_dots_detail),
            *inventory.map { it.label }.toTypedArray())
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_icons))
            SettingSwitch(
                label = stringResource(R.string.settings_hide_other_icons),
                detail = stringResource(R.string.settings_hide_other_icons_detail),
                checked = settings.hideOtherIcons,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(hideOtherIcons = it)) }
            if (inventory.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_stock_icons_empty),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_stock_icons_detail),
                    style = MaterialTheme.typography.bodySmall
                )
                inventory.forEach { item ->
                    SettingSwitch(
                        label = item.label,
                        detail = null,
                        checked = StockIconSelection.isHidden(settings, item.key),
                        enabled = settings.enabled
                    ) { hidden ->
                        onUpdate(StockIconSelection.toggle(settings, inventory, item.key, hidden))
                    }
                }
            }
            SettingSwitch(
                label = stringResource(R.string.settings_show_wifi_glyph),
                detail = stringResource(R.string.settings_show_wifi_glyph_detail),
                checked = settings.showWifiGlyph,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(showWifiGlyph = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_show_cell_dots),
                detail = stringResource(R.string.settings_show_cell_dots_detail),
                checked = settings.showCellDots,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(showCellDots = it)) }
            IconHidingSetting(enabled = settings.enabled)
        }
    }
}

// ------------------------------------------------------------------ Tap actions
@Composable
internal fun ActionsSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate,
    autoExpand: Boolean
) {
    if (matches(stringResource(R.string.section_actions), stringResource(R.string.settings_tap),
            stringResource(R.string.settings_double_tap), stringResource(R.string.settings_long_press))
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_actions))
            Text(
                text = if (autoExpand) {
                    stringResource(R.string.settings_gestures_note)
                } else {
                    stringResource(R.string.settings_gestures_missing)
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (settings.splitIndicators) {
                Text(
                    text = stringResource(R.string.settings_ring_actions),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            ActionPicker(
                label = stringResource(R.string.settings_tap),
                selectedKey = settings.tapAction,
                enabled = autoExpand && settings.enabled
            ) { onUpdate(settings.copy(tapAction = it)) }
            ActionPicker(
                label = stringResource(R.string.settings_double_tap),
                selectedKey = settings.doubleTapAction,
                enabled = autoExpand && settings.enabled
            ) { onUpdate(settings.copy(doubleTapAction = it)) }
            ActionPicker(
                label = stringResource(R.string.settings_long_press),
                selectedKey = settings.longPressAction,
                enabled = autoExpand && settings.enabled
            ) { onUpdate(settings.copy(longPressAction = it)) }
            if (settings.splitIndicators) {
                Text(
                    text = stringResource(R.string.settings_indicator_actions),
                    style = MaterialTheme.typography.titleSmall
                )
                ActionPicker(
                    label = stringResource(R.string.settings_tap),
                    selectedKey = settings.indicatorsTapAction,
                    enabled = autoExpand && settings.enabled
                ) { onUpdate(settings.copy(indicatorsTapAction = it)) }
                ActionPicker(
                    label = stringResource(R.string.settings_double_tap),
                    selectedKey = settings.indicatorsDoubleTapAction,
                    enabled = autoExpand && settings.enabled
                ) { onUpdate(settings.copy(indicatorsDoubleTapAction = it)) }
                ActionPicker(
                    label = stringResource(R.string.settings_long_press),
                    selectedKey = settings.indicatorsLongPressAction,
                    enabled = autoExpand && settings.enabled
                ) { onUpdate(settings.copy(indicatorsLongPressAction = it)) }
            }
            Text(
                text = stringResource(R.string.settings_download_tap_note),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ------------------------------------------------------------------------ About
@Composable
internal fun AboutSection(
    matches: SearchGate,
    status: String,
    history: List<String>,
    moduleLoadAt: Long,
    moduleAlive: Boolean,
    accessGranted: Boolean,
    exporting: Boolean,
    onExport: () -> Unit,
    checkUpdates: Boolean,
    onToggleUpdates: (Boolean) -> Unit,
    checkingUpdate: Boolean,
    onCheckNow: () -> Unit,
    updateMessage: String,
    updateInfo: UpdateInfo?,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    if (matches(stringResource(R.string.section_about), stringResource(R.string.settings_status),
            stringResource(R.string.settings_export_log),
            stringResource(R.string.settings_check_updates), stringResource(R.string.settings_donate))
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_about))
            // The raw module status (stage/renderer/attached/…) travels in the report, never on screen —
            // it is developer jargon, not something a user should have to read.
            Text(
                text = when {
                    moduleAlive && moduleLoadAt > 0L ->
                        stringResource(R.string.settings_module_loaded, formatTimestamp(moduleLoadAt))
                    else -> stringResource(R.string.settings_module_never)
                },
                style = MaterialTheme.typography.bodySmall
            )
            // Optional elevated capture for the log: root (asked for explicitly) or Shizuku. With
            // neither, the report still carries the module's own log and the device facts.
            RootAccessSetting()
            // Export: save the same report anywhere with the system file picker, independent of
            // Telegram or a network. The escape hatch when the upload path is not available.
            OutlinedButton(
                onClick = onExport,
                enabled = !exporting && (moduleAlive || accessGranted),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (exporting) R.string.settings_collecting else R.string.settings_export_log
                    )
                )
            }
            // ----- Updates -----
            SettingSwitch(
                label = stringResource(R.string.settings_check_updates),
                detail = stringResource(R.string.settings_check_updates_detail),
                checked = checkUpdates
            ) { on -> onToggleUpdates(on) }
            OutlinedButton(
                onClick = onCheckNow,
                enabled = !checkingUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (checkingUpdate) R.string.settings_checking else R.string.settings_check_now
                    )
                )
            }
            if (updateMessage.isNotEmpty()) {
                Text(updateMessage, style = MaterialTheme.typography.bodySmall)
            }
            updateInfo?.let { info ->
                if (info.apkUrl.isNotBlank()) {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_update_download))
                    }
                } else {
                    Text(
                        stringResource(R.string.settings_update_no_apk),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_paypal),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_donate))
            }
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * FR-09: the fixed snapshot the setting demos morph from and to.
 *
 * One battery level for every demo, so the rows are comparable and the only thing that changes between
 * the two ends is the setting being demonstrated.
 */
private fun demo(
    level: Int = DEMO_LEVEL,
    charging: Boolean = false,
    showPercent: Boolean = true,
    airplane: Boolean = false,
    dnd: Boolean = false,
    percentHeight: Int = DuoPrefs.DEFAULT_PERCENT_HEIGHT,
    wifiLevel: Int = 3,
    wifiOn: Boolean = true,
    wifiDots: Boolean = false,
    showAirplane: Boolean = true,
    showDnd: Boolean = true,
    dndDots: Boolean = false,
    showCharging: Boolean = true,
    cellLevel: Int = 4,
    clockText: String = "10:50"
) = DuoMapping.visual(
    level = level,
    charging = charging,
    saver = false,
    showPercent = showPercent,
    wifiLevel = wifiLevel,
    cellLevel = cellLevel,
    airplane = airplane,
    dnd = dnd,
    wifiOn = wifiOn,
    percentHeight = percentHeight,
    wifiDots = wifiDots,
    showAirplane = showAirplane,
    showDnd = showDnd,
    showCharging = showCharging,
    dndDots = dndDots,
    clockText = clockText
)

/** The Do Not Disturb row, drawn as if that status is on, in the mode the user picked. */
private fun dndPreview(mode: String) = when (mode) {
    DuoPrefs.DND_DOTS -> demo(dnd = true, showDnd = false, dndDots = true, cellLevel = 2)
    DuoPrefs.DND_OFF -> demo(dnd = true, showDnd = false)
    else -> demo(dnd = true, showDnd = true)
}

// ------------------------------------------------------------------ Live events
@Composable
internal fun LiveEventsSection(
    matches: SearchGate,
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit
) {
    if (matches(
            stringResource(R.string.section_live_events),
            stringResource(R.string.settings_live_events_detail),
            stringResource(R.string.settings_event_alarm),
            stringResource(R.string.settings_event_alarm_detail),
            stringResource(R.string.settings_event_flashlight),
            stringResource(R.string.settings_event_flashlight_detail),
            stringResource(R.string.settings_event_screenshot),
            stringResource(R.string.settings_event_screenshot_detail),
            stringResource(R.string.settings_event_unlock),
            stringResource(R.string.settings_event_unlock_detail)
        )
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_live_events))
            Text(
                text = stringResource(R.string.settings_live_events_detail),
                style = MaterialTheme.typography.bodySmall
            )
            SettingSwitch(
                label = stringResource(R.string.settings_event_alarm),
                detail = stringResource(R.string.settings_event_alarm_detail),
                checked = DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_ALARM),
                enabled = settings.enabled,
                preview = { DuoEventRowPreview(DuoPrefs.EVENT_ALARM, settings.enabled, rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo, split = settings.splitIndicators) }
            ) { on ->
                onUpdate(settings.copy(eventMask = if (on) settings.eventMask or DuoPrefs.EVENT_ALARM else settings.eventMask and DuoPrefs.EVENT_ALARM.inv()))
            }
            SettingSwitch(
                label = stringResource(R.string.settings_event_flashlight),
                detail = stringResource(R.string.settings_event_flashlight_detail),
                checked = DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_FLASHLIGHT),
                enabled = settings.enabled,
                preview = { DuoEventRowPreview(DuoPrefs.EVENT_FLASHLIGHT, settings.enabled, rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo, split = settings.splitIndicators) }
            ) { on ->
                onUpdate(settings.copy(eventMask = if (on) settings.eventMask or DuoPrefs.EVENT_FLASHLIGHT else settings.eventMask and DuoPrefs.EVENT_FLASHLIGHT.inv()))
            }
            SettingSwitch(
                label = stringResource(R.string.settings_event_screenshot),
                detail = stringResource(R.string.settings_event_screenshot_detail),
                checked = DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_SCREENSHOT),
                enabled = settings.enabled,
                preview = { DuoEventRowPreview(DuoPrefs.EVENT_SCREENSHOT, settings.enabled, rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo, split = settings.splitIndicators) }
            ) { on ->
                onUpdate(settings.copy(eventMask = if (on) settings.eventMask or DuoPrefs.EVENT_SCREENSHOT else settings.eventMask and DuoPrefs.EVENT_SCREENSHOT.inv()))
            }
            SettingSwitch(
                label = stringResource(R.string.settings_event_unlock),
                detail = stringResource(R.string.settings_event_unlock_detail),
                checked = DuoPrefs.eventEnabled(settings.eventMask, DuoPrefs.EVENT_UNLOCK),
                enabled = settings.enabled,
                preview = { DuoEventRowPreview(DuoPrefs.EVENT_UNLOCK, settings.enabled, rawRes = if (settings.designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo, split = settings.splitIndicators) }
            ) { on ->
                onUpdate(settings.copy(eventMask = if (on) settings.eventMask or DuoPrefs.EVENT_UNLOCK else settings.eventMask and DuoPrefs.EVENT_UNLOCK.inv()))
            }
        }
    }
}

// ------------------------------------------------------------ Download progress
@Composable
internal fun DownloadSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
    val context = LocalContext.current
    var phdpInstalled by remember { mutableStateOf(isPhdpInstalled(context)) }
    // The user may install PHDP from the release page and come back; re-check on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        phdpInstalled = isPhdpInstalled(context)
    }
    if (matches(
            stringResource(R.string.section_download_progress),
            stringResource(R.string.settings_download_progress),
            stringResource(R.string.settings_download_progress_detail),
            stringResource(R.string.settings_download_progress_missing),
            stringResource(R.string.settings_download_latest),
            stringResource(R.string.settings_download_install_phdp),
            stringResource(R.string.settings_download_open_phdp),
            stringResource(R.string.settings_download_simulate),
            stringResource(R.string.settings_download_simulate_note),
            PHDP_PACKAGE
        )
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_download_progress))
            SettingSwitch(
                label = stringResource(R.string.settings_download_progress),
                detail = stringResource(R.string.settings_download_progress_detail),
                checked = settings.downloadProgressEnabled,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(downloadProgressEnabled = it)) }
            OutlinedButton(
                onClick = { openPhdpRelease(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_download_latest)) }
            if (phdpInstalled) {
                Button(
                    onClick = { openPhdp(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_download_open_phdp)) }
                Button(
                    onClick = { openPhdp(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_download_simulate)) }
                Text(
                    text = stringResource(R.string.settings_download_simulate_note),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Button(
                    onClick = { openPhdpRelease(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_download_install_phdp)) }
                Text(
                    text = stringResource(R.string.settings_download_progress_missing),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** PHDP owns the status-bar download simulation; Duo only consumes its published progress. */
private const val PHDP_PACKAGE = "eu.hxreborn.phdp"
private const val PHDP_RELEASE_URL =
    "https://github.com/hxreborn/punch-hole-download-progress/releases/latest"

private fun isPhdpInstalled(context: Context): Boolean =
    runCatching { context.packageManager.getApplicationInfo(PHDP_PACKAGE, 0) }.isSuccess

/** Opens PHDP itself, where its own test runs; Duo never injects a download broadcast. */
private fun openPhdp(context: Context): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage(PHDP_PACKAGE) ?: return false
    return runCatching {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

private fun openPhdpRelease(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PHDP_RELEASE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { L.w("phdp release open: ${it.message}") }
}

// ------------------------------------------------------------------------ Backup
@Composable
internal fun BackupSection(
    matches: SearchGate,
    message: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    if (matches(
            stringResource(R.string.section_backup),
            stringResource(R.string.settings_backup_detail),
            stringResource(R.string.settings_backup_export),
            stringResource(R.string.settings_backup_import)
        )
    ) GlassCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_backup))
            Text(
                text = stringResource(R.string.settings_backup_detail),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_backup_export))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_backup_import))
            }
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** FR-09: a level that shows a clear half-full ring rather than an empty or full one. */
private const val DEMO_LEVEL = 40

/** FR-25: the five arrival speeds, slowest first, matching [DuoPrefs.REVEAL_CHOICES] reversed. */
private val SPEED_LABELS = listOf("Slow", "Relaxed", "Normal", "Brisk", "Fast")
