package io.github.kvmy666.duostatusbar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.DuoPreview
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import kotlinx.coroutines.delay

/**
 * The settings screen (FR-03/09/10/11/16/17).
 *
 * Every change is written to the app's own storage and then broadcast, which is what makes it reach the
 * status bar immediately: the module listens for that broadcast and re-reads (see
 * `hook/DuoHook.hookSettingsChanges`). The module cannot be written to directly, so this handshake is the
 * mechanism — the app owns the values, the module applies them.
 *
 * The diagnostics card shows what the module reported about itself: facts, not intentions. When it says
 * "no report yet", the cause is almost always scope or enablement in LSPosed, which is what the hint
 * underneath it is for.
 */
@Composable
fun DuoSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(DuoPrefs.read(context)) }
    var loop by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(DuoPrefs.status(context)) }
    var history by remember { mutableStateOf(DuoPrefs.statusHistory(context)) }

    // The module answers an instant after the broadcast; re-reading is simpler than a callback.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            status = DuoPrefs.status(context)
            history = DuoPrefs.statusHistory(context)
        }
    }

    fun update(new: DuoSettings) {
        settings = new
        DuoPrefs.write(context, new)
        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
                SettingSwitch(
                    label = stringResource(R.string.settings_master),
                    detail = stringResource(R.string.settings_master_detail),
                    checked = settings.enabled
                ) { update(settings.copy(enabled = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_renderer),
                    detail = stringResource(R.string.settings_renderer_detail),
                    checked = settings.useRive,
                    enabled = settings.enabled
                ) { update(settings.copy(useRive = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_percent),
                    detail = stringResource(R.string.settings_percent_detail),
                    checked = settings.showPercent,
                    enabled = settings.enabled
                ) { update(settings.copy(showPercent = it)) }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_preview), style = MaterialTheme.typography.titleMedium)
                DuoPreview(loop = loop)
                SettingSwitch(
                    label = stringResource(R.string.settings_loop),
                    detail = null,
                    checked = loop
                ) { loop = it }
                LabelledSlider(
                    label = "${stringResource(R.string.settings_size)} ${settings.sizePercent}%",
                    value = settings.sizePercent.toFloat(),
                    range = DuoPrefs.MIN_SIZE.toFloat()..DuoPrefs.MAX_SIZE.toFloat()
                ) { update(settings.copy(sizePercent = it.toInt())) }
                LabelledSlider(
                    label = "${stringResource(R.string.settings_offset)} ${settings.offsetX} dp",
                    value = settings.offsetX.toFloat(),
                    range = -DuoPrefs.MAX_OFFSET.toFloat()..DuoPrefs.MAX_OFFSET.toFloat()
                ) { update(settings.copy(offsetX = it.toInt())) }
            }
        }

        val emptyStatus = stringResource(R.string.settings_no_status)
        val autoExpand = remember { DuoActions.isAutoExpandInstalled(context) }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_gestures), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (autoExpand) {
                        stringResource(R.string.settings_gestures_note)
                    } else {
                        stringResource(R.string.settings_gestures_missing)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                ActionPicker(
                    label = stringResource(R.string.settings_tap),
                    selectedKey = settings.tapAction,
                    enabled = autoExpand && settings.enabled
                ) { update(settings.copy(tapAction = it)) }
                ActionPicker(
                    label = stringResource(R.string.settings_double_tap),
                    selectedKey = settings.doubleTapAction,
                    enabled = autoExpand && settings.enabled
                ) { update(settings.copy(doubleTapAction = it)) }
                ActionPicker(
                    label = stringResource(R.string.settings_long_press),
                    selectedKey = settings.longPressAction,
                    enabled = autoExpand && settings.enabled
                ) { update(settings.copy(longPressAction = it)) }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = status.ifEmpty { emptyStatus },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_gate_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                if (history.isNotEmpty()) {
                    Text(stringResource(R.string.settings_history), style = MaterialTheme.typography.labelLarge)
                    history.takeLast(3).forEach { entry ->
                        Text(
                            text = entry.substringAfter(' '),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(
                    onClick = {
                        // Reuses the settings broadcast: the module re-resolves the stage, re-applies the layout
                        // and reports again, which is exactly what "did it take effect?" means.
                        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_recheck)) }
                Button(
                    onClick = {
                        val report = buildString {
                            appendLine("Duo Status Bar diagnostics")
                            appendLine("settings: $settings")
                            appendLine("module: ").append(status.ifEmpty { "no report yet" })
                            if (history.isNotEmpty()) {
                                appendLine("history:")
                                history.forEach { appendLine("  $it") }
                            }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Share diagnostics"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_share)) }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_donate)) }
            }
        }
    }
}

/** FR-28: where the donate button goes. */
private const val DONATE_URL = "https://paypal.me/kroomfahd"

@Composable
private fun SettingSwitch(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Pick one of Auto Expand's actions for a gesture. A plain dropdown: no experimental API needed. */
@Composable
private fun ActionPicker(
    label: String,
    selectedKey: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(DuoActions.label(selectedKey))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DuoActions.ALL.forEach { (key, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            onSelect(key)
                        }
                    )
                }
            }
        }
    }
}
