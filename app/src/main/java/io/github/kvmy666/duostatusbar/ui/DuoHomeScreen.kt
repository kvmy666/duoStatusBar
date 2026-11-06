package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.DuoPreview
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealthCheck
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.SettingsBridge

/**
 * The quiet front door: status and a live ring.
 *
 * Custom and Settings live on their own tabs, so Home keeps no second route into either destination:
 * there is exactly one way to each place.
 */
@Composable
fun DuoHomeScreen() {
    val context = LocalContext.current
    val health = remember { ModuleHealthCheck.of(context).state }
    var settings by remember { mutableStateOf(DuoPrefs.read(context)) }
    var restartRequired by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = DuoPrefs.read(context)
    }

    fun persistCamera(on: Boolean) {
        settings = writeCameraMode(context, settings, on)
    }

    fun persistDesign(style: String) {
        if (style == settings.designStyle) return
        settings = writeRingDesign(context, settings, style)
        // The running Rive view already loaded its own drawing, so the switch is honest only after
        // System UI restarts; never pretend the live element changed.
        restartRequired = true
    }

    fun persistInvert(on: Boolean) {
        settings = writeInvertCenter(context, settings, on)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = DuoSpace.xl)
    ) {
        Spacer(Modifier.height(DuoSpace.xxl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DuoSpace.md)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = DuoColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            StatusPill(health)
        }

        Spacer(Modifier.height(DuoSpace.sm))
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = DuoColors.TextSecondary
        )

        Spacer(Modifier.height(DuoSpace.xxl))
        LivePreviewLabCard(
            designStyle = settings.designStyle,
            middleSlot = settings.middleSlot,
            onInvertChange = { persistInvert(it) }
        )

        Spacer(Modifier.height(DuoSpace.md))
        RingDesignCard(
            selected = settings.designStyle,
            enabled = settings.enabled,
            restartRequired = restartRequired,
            onSelect = { persistDesign(it) },
            onRestart = { restartSystemUi(context) }
        )

        Spacer(Modifier.height(DuoSpace.md))
        CameraCard(
            checked = settings.lockRing,
            enabled = settings.enabled
        ) { persistCamera(it) }

        Spacer(Modifier.height(120.dp))
    }
}

/**
 * Camera mode is independent of the selected design: it only writes [DuoSettings.lockRing] and never
 * moves the Default/Compact/Custom selection or the Custom snapshot.
 */
internal fun writeCameraMode(context: Context, settings: DuoSettings, on: Boolean): DuoSettings {
    val updated = settings.copy(lockRing = on)
    DuoPrefs.write(context, updated)
    context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
    SettingsBridge.push(context)
    return updated
}

/**
 * Ring design is its own state: it only writes [DuoSettings.designStyle]. The selected
 * Default/Compact/Custom preset and the Custom snapshot are left exactly where the user put them,
 * so this choice survives a later preset change.
 */
internal fun writeRingDesign(context: Context, settings: DuoSettings, style: String): DuoSettings {
    val updated = settings.copy(designStyle = DuoPrefs.normalizeDesignStyle(style))
    DuoPrefs.write(context, updated)
    context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
    SettingsBridge.push(context)
    return updated
}

/**
 * Invert center is the existing `middle_slot=percent` setting: no new column, no schema change.
 * On enable it writes `percent`; on disable it restores `auto` unless the slot already held a
 * non-percent value, which is preserved instead.
 */
internal fun writeInvertCenter(context: Context, settings: DuoSettings, on: Boolean): DuoSettings {
    val updated = settings.copy(middleSlot = invertCenterToMiddleSlot(on, settings.middleSlot))
    DuoPrefs.write(context, updated)
    context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
    SettingsBridge.push(context)
    return updated
}

/** Pure toggle -> middleSlot mapping; exercised by [InvertCenterTest]. */
internal fun invertCenterToMiddleSlot(on: Boolean, current: String): String =
    if (on) DuoPrefs.MIDDLE_SLOT_PERCENT
    else if (current == DuoPrefs.MIDDLE_SLOT_PERCENT) DuoPrefs.MIDDLE_SLOT_AUTO
    else DuoPrefs.normalizeMiddleSlot(current)

/**
 * The single Live Preview Controls card: the interactive Rive preview plus its real-model controls.
 * Invert center is the only control that writes module settings; the rest are preview-only.
 */
@Composable
private fun LivePreviewLabCard(
    designStyle: String,
    middleSlot: String,
    onInvertChange: (Boolean) -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(DuoRadius.surface),
        colors = CardDefaults.cardColors(containerColor = DuoColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(DuoSpace.xl),
            verticalArrangement = Arrangement.spacedBy(DuoSpace.sm)
        ) {
            Text(
                text = stringResource(R.string.home_live_preview),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.home_live_preview_detail),
                style = MaterialTheme.typography.bodySmall,
                color = DuoColors.TextSecondary
            )
            DuoPreview(
                pixelSize = 180,
                rawRes = if (designStyle == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo,
                middleSlot = middleSlot,
                onInvertChange = onInvertChange
            )
            Text(
                text = stringResource(R.string.home_live_preview_note),
                style = MaterialTheme.typography.bodySmall,
                color = DuoColors.TextTertiary
            )
        }
    }
}

@Composable
private fun RingDesignCard(
    selected: String,
    enabled: Boolean,
    restartRequired: Boolean,
    onSelect: (String) -> Unit,
    onRestart: () -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(DuoRadius.surface),
        colors = CardDefaults.cardColors(containerColor = DuoColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(DuoSpace.xl),
            verticalArrangement = Arrangement.spacedBy(DuoSpace.sm)
        ) {
            Text(
                text = stringResource(R.string.home_ring_design),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.home_ring_design_detail),
                style = MaterialTheme.typography.bodySmall,
                color = DuoColors.TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(DuoSpace.sm)
            ) {
                RingDesignOption(
                    label = stringResource(R.string.home_ring_design_classic),
                    selected = DuoPrefs.normalizeDesignStyle(selected) == DuoPrefs.DESIGN_OLD,
                    enabled = enabled,
                    demoRes = R.drawable.classic_demo,
                    modifier = Modifier.weight(1f)
                ) { onSelect(DuoPrefs.DESIGN_OLD) }
                RingDesignOption(
                    label = stringResource(R.string.home_ring_design_dual),
                    selected = DuoPrefs.normalizeDesignStyle(selected) == DuoPrefs.DESIGN_NEW,
                    enabled = enabled,
                    demoRes = R.drawable.design_dual,
                    modifier = Modifier.weight(1f)
                ) { onSelect(DuoPrefs.DESIGN_NEW) }
            }
            if (restartRequired) {
                Text(
                    text = stringResource(R.string.home_ring_design_restart),
                    style = MaterialTheme.typography.bodySmall,
                    color = DuoColors.WineMist
                )
                OutlinedButton(
                    onClick = onRestart,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_restart))
                }
            }
        }
    }
}

@Composable
private fun RingDesignOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    demoRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(DuoRadius.control)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) DuoColors.Wine.copy(alpha = 0.20f) else DuoColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) DuoColors.WineBright.copy(alpha = 0.72f) else DuoColors.Hairline,
                shape = shape
            )
            .alpha(if (enabled) 1f else 0.45f)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(DuoSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DuoSpace.xs)
    ) {
        DesignDemo(
            res = demoRes,
            modifier = Modifier.size(52.dp),
            contentDescription = label
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) DuoColors.WineMist else DuoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CameraCard(
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(DuoRadius.surface),
        colors = CardDefaults.cardColors(containerColor = DuoColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(DuoSpace.xl)) {
            SettingSwitch(
                label = stringResource(R.string.home_camera),
                detail = stringResource(R.string.home_camera_detail),
                checked = checked,
                enabled = enabled,
                onChange = onToggle
            )
        }
    }
}

@Composable
private fun StatusPill(state: ModuleState) {
    val (label, dot) = when (state) {
        ModuleState.OK -> R.string.home_status_running to DuoColors.Success
        ModuleState.NEVER_LOADED -> R.string.home_status_setup to DuoColors.WineMist
        ModuleState.NEEDS_RESTART -> R.string.home_status_restart to DuoColors.WineMist
        ModuleState.NOT_REPORTING -> R.string.home_status_no_report to DuoColors.Danger
    }
    val shape = RoundedCornerShape(DuoRadius.pill)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(DuoColors.Surface)
            .border(1.dp, DuoColors.Hairline, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = DuoColors.TextSecondary,
            maxLines = 1
        )
    }
}
