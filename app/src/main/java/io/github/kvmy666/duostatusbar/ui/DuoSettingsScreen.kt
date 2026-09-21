package io.github.kvmy666.duostatusbar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.core.content.FileProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kvmy666.duostatusbar.DuoPreview
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoElement
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    var query by remember { mutableStateOf("") }

    /** Settings search: a row is shown when the query appears in its label or its detail. */
    fun matches(vararg text: String): Boolean =
        query.isBlank() || text.any { it.contains(query.trim(), ignoreCase = true) }

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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.settings_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (matches(stringResource(R.string.settings_title), stringResource(R.string.settings_master),
                stringResource(R.string.settings_master_detail), stringResource(R.string.settings_renderer),
                stringResource(R.string.settings_renderer_detail), stringResource(R.string.settings_percent),
                stringResource(R.string.settings_percent_detail))
        ) Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
                SettingSwitch(
                    label = stringResource(R.string.settings_master),
                    detail = stringResource(R.string.settings_master_detail),
                    checked = settings.enabled,
                    preview = {
                        // Off is the element *absent*, so the demo shows an empty ring with no
                        // number rather than a ring reading "0".
                        DuoSettingPreview(off = demo(level = 0, showPercent = false), on = demo())
                    }
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
                    enabled = settings.enabled,
                    preview = {
                        DuoSettingPreview(
                            off = demo(showPercent = false),
                            on = demo(showPercent = true)
                        )
                    }
                ) { update(settings.copy(showPercent = it)) }
            }
        }

        if (matches(stringResource(R.string.settings_preview), stringResource(R.string.settings_loop),
                stringResource(R.string.settings_size), stringResource(R.string.settings_offset),
                stringResource(R.string.settings_reveal))
        ) Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_preview), style = MaterialTheme.typography.titleMedium)
                DuoPreview(loop = loop, revealMs = settings.revealMs)
                SettingSwitch(
                    label = stringResource(R.string.settings_loop),
                    detail = null,
                    checked = loop
                ) { loop = it }
                LabelledSlider(
                    label = "${stringResource(R.string.settings_size)} ${settings.sizePercent}%",
                    value = settings.sizePercent.toFloat(),
                    range = DuoPrefs.MIN_SIZE.toFloat()..DuoPrefs.MAX_SIZE.toFloat(),
                    preview = {
                        DuoSettingPreview(
                            off = demo(),
                            on = demo(),
                            scaleFrom = DuoPrefs.MIN_SIZE.toFloat() / DuoPrefs.MAX_SIZE
                        )
                    }
                ) { update(settings.copy(sizePercent = it.toInt())) }
                // FR-25: the arrival is one Rive timeline played at five speeds, so this picks an index
                // into the choices rather than a free millisecond value.
                LabelledSlider(
                    label = "${stringResource(R.string.settings_reveal)}: ${settings.revealMs} ms",
                    value = DuoPrefs.REVEAL_CHOICES.indexOf(settings.revealMs)
                        .coerceAtLeast(0).toFloat(),
                    range = 0f..(DuoPrefs.REVEAL_CHOICES.size - 1).toFloat(),
                    steps = DuoPrefs.REVEAL_CHOICES.size - 2
                ) { index ->
                    update(settings.copy(revealMs = DuoPrefs.REVEAL_CHOICES[index.toInt().coerceIn(0, DuoPrefs.REVEAL_CHOICES.size - 1)]))
                }
                Text(
                    text = stringResource(R.string.settings_reveal_detail),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.settings_offset)}: ${settings.offsetX} dp",
                    style = MaterialTheme.typography.bodyMedium
                )
                PositionEditor(settings.offsetX) { update(settings.copy(offsetX = it)) }
            }
        }

        val emptyStatus = stringResource(R.string.settings_no_status)
        val autoExpand = remember { DuoActions.isAutoExpandInstalled(context) }
        if (matches(stringResource(R.string.settings_gestures), stringResource(R.string.settings_tap),
                stringResource(R.string.settings_double_tap), stringResource(R.string.settings_long_press))
        ) Card {
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

        if (matches(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_share),
                stringResource(R.string.settings_save_file), stringResource(R.string.settings_donate))
        ) Card {
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
                        val file = writeDiagnostics(context, buildDiagnostics(settings, status, history))
                        if (file != null) {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, file)
                                putExtra(Intent.EXTRA_TEXT, "Duo Status Bar diagnostics")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Save diagnostics"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_save_file)) }
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

/** The report the diagnostics buttons send - one text, shared or written to a file. */
private fun buildDiagnostics(settings: DuoSettings, status: String, history: List<String>): String =
    buildString {
        appendLine("Duo Status Bar diagnostics")
        appendLine("settings: $settings")
        appendLine("module: ").append(status.ifEmpty { "no report yet" })
        if (history.isNotEmpty()) {
            appendLine("history:")
            history.forEach { appendLine("  $it") }
        }
    }

/**
 * FR-28: writes the report to the app's own diagnostics directory and returns a shareable Uri.
 *
 * A bug report needs the whole log, and a shared string gets truncated by chat apps. The file lives in
 * the app's external files dir and is handed out through a FileProvider, so nothing else is exposed.
 * Returns null (and the caller shares nothing) if the directory is unavailable.
 */
private fun writeDiagnostics(context: android.content.Context, report: String): Uri? = try {
    val dir = context.getExternalFilesDir("diagnostics")
    if (dir == null) {
        null
    } else {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "duo-diagnostics-$stamp.txt")
        file.writeText(report)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
} catch (t: Throwable) {
    L.w("diagnostics file: ${t.javaClass.simpleName}: ${t.message}")
    null
}

/**
 * FR-17: drag the element along a mock status bar instead of guessing a number.
 *
 * The strip hosts the **same Canvas element** the status bar uses (no native code, so it can never break the
 * settings app), driven by the same [DuoMapping], and writes the horizontal offset the module applies as
 * `translationX` — so what is dragged here is what the phone does, not a picture of it.
 */
@Composable
private fun PositionEditor(offsetDp: Int, onOffset: (Int) -> Unit) {
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(offsetDp.toFloat()) }
    // Keep in step when the value changes from elsewhere (a re-read, or another screen).
    LaunchedEffect(offsetDp) { drag = offsetDp.toFloat() }
    val limit = DuoPrefs.MAX_OFFSET.toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures { _, amount ->
                    val next = (drag + amount.x / density.density).coerceIn(-limit, limit)
                    drag = next
                    onOffset(next.toInt())
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = stringResource(R.string.settings_drag_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
        )
        AndroidView(
            modifier = Modifier
                .size(40.dp)
                .padding(end = 18.dp),
            factory = { ctx -> DuoCanvasView(ctx).also { it.start() } },
            update = { view ->
                (view as? DuoElement)?.render(
                    DuoMapping.visual(
                        level = 78,
                        charging = false,
                        saver = false,
                        showPercent = true,
                        wifiLevel = 3,
                        cellLevel = 4,
                        airplane = false
                    )
                )
                view.translationX = drag * density.density
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    preview: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        preview?.invoke()
        Column(Modifier.weight(1f).padding(start = if (preview == null) 0.dp else 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
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
    middleBlend: Float = 1f
) = DuoMapping.visual(
    level = level,
    charging = charging,
    saver = false,
    showPercent = showPercent,
    wifiLevel = 3,
    cellLevel = 4,
    airplane = airplane,
    dnd = dnd,
    middleBlend = middleBlend
)

/** FR-09: a level that shows a clear half-full ring rather than an empty or full one. */
private const val DEMO_LEVEL = 72

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    preview: (@Composable () -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            preview?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = if (preview == null) 0.dp else 12.dp)
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
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
