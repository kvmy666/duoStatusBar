package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.DuoRiveStill
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The settings search gate shared by the section cards: a row is shown when the query appears in its
 * label or its detail. Built by `DuoSettingsScreen`'s local `matches` helper.
 */
internal fun interface SearchGate {
    operator fun invoke(vararg text: String): Boolean
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/**
 * The fallback alert: the module could not draw the animated element and is using the simple drawing.
 * It offers the two routes the developer actually reads — Telegram or GitHub — and can be dismissed once
 * the log has been sent.
 */
@Composable
internal fun FallbackAlert(
    detail: String,
    busy: Boolean,
    onTelegram: () -> Unit,
    onGitHub: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.fallback_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.fallback_message), style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onTelegram, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.settings_collecting else R.string.fallback_telegram))
            }
            OutlinedButton(onClick = onGitHub, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fallback_github))
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fallback_dismiss))
            }
        }
    }
}

/**
 * A leftover adb stage override (see `StageOverride`): the module is pinned to the simple drawing by a
 * `Settings.Global` value the app cannot write. This explains it and offers the root-assisted clear so
 * the user can get animations back without a computer.
 */
@Composable
internal fun StageOverrideAlert(
    value: Int,
    busy: Boolean,
    command: String,
    failed: Boolean,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.settings_override_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_override_message, value),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    if (failed) R.string.settings_override_failed else R.string.settings_override_command,
                    command
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onClear, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (busy) R.string.settings_override_clearing else R.string.settings_override_clear
                    )
                )
            }
        }
    }
}

/**
 * The module-health card (red): the module is not running, is running old code after an update, or is
 * loaded but cannot report. It offers the two ways to recover and a one-tap report, which is what a
 * "no report in the log" report needs to become actionable.
 */
@Composable
internal fun ModuleHealthCard(
    state: ModuleState,
    busy: Boolean,
    onOpenLsposed: () -> Unit,
    onRestart: () -> Unit,
    onReboot: () -> Unit,
    onTelegram: () -> Unit,
    onExport: () -> Unit,
    onReport: () -> Unit
) {
    val title = when (state) {
        ModuleState.NEEDS_RESTART -> R.string.module_health_restart_title
        ModuleState.NOT_REPORTING -> R.string.module_health_noreport_title
        else -> R.string.module_health_never_title
    }
    val text = when (state) {
        ModuleState.NEEDS_RESTART -> R.string.module_health_restart_text
        ModuleState.NOT_REPORTING -> R.string.module_health_noreport_text
        else -> R.string.module_health_never_text
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            // Not injected: the fix is in LSPosed (enable + System UI scope) and a restart, not a report -
            // the module has produced nothing to report. A user in this state can never send a useful dump,
            // so the report button is replaced by the two recovery steps and a way to reach support.
            if (state == ModuleState.NEVER_LOADED) {
                Text(stringResource(R.string.module_health_scope_hint), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenLsposed, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.module_health_open_lsposed))
                }
                OutlinedButton(onClick = onRestart, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_restart))
                }
                // Even without an injection there is an app-side report worth having (build, device, ROM,
                // module state), so a user stuck here can still save it and send it on.
                OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.module_health_export))
                }
            } else {
                Button(onClick = onRestart, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_restart))
                }
                OutlinedButton(onClick = onReboot, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_reboot))
                }
                OutlinedButton(onClick = onReport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (busy) R.string.settings_collecting else R.string.settings_report_bug))
                }
            }
            // Escalation, for when the automatic recovery does not work.
            Text(stringResource(R.string.module_health_contact_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onTelegram, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.module_health_contact))
            }
        }
    }
}

/**
 * FR-17: set the element's horizontal position on a status-bar-shaped preview.
 *
 * It is drawn to look like the real bar — a dark pill with the clock on the left and the element on
 * the rail — and the element is the **same Canvas element** the status bar falls back to, driven by
 * the same [DuoMapping]. Only the element (icon) can be dragged; the rail itself does not consume
 * touches, so vertical scrolling keeps working. The value is shown so it can be set exactly.
 */
@Composable
internal fun PositionEditor(
    offsetDp: Int,
    maxOffsetDp: Int,
    enabled: Boolean,
    centered: Boolean = false,
    label: String? = null,
    hint: String? = null,
    visual: DuoVisual? = null,
    rawRes: Int = R.raw.duo,
    onOffset: (Int) -> Unit
) {
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(offsetDp.toFloat()) }
    LaunchedEffect(offsetDp) { drag = offsetDp.toFloat() }
    val limit = maxOffsetDp.coerceAtLeast(1).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label ?: stringResource(R.string.settings_position), style = MaterialTheme.typography.bodyMedium)
        // Force LTR so Alignment.CenterEnd is always the physical right edge, matching native.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics {
                    if (enabled) {
                        val hi = if (centered) limit else 0f
                        progressBarRangeInfo = ProgressBarRangeInfo(drag, -limit..hi, 0)
                        setProgress { target ->
                            val next = target.coerceIn(-limit, hi).toInt()
                            drag = next.toFloat()
                            onOffset(next)
                            true
                        }
                    }
                }
        ) {
            val previewTravel = (maxWidth.value - 32f).coerceAtLeast(0f) / 2f
            // The pill is only the background. The content is deliberately not clipped: the element
            // must stay visible and draggable all the way to either end of the rail.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF101014))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "9:41",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 14.dp)
                )
                val previewOffset = positionPreviewOffset(drag, limit, previewTravel, centered)
                // Normal mode is right-anchored: 0 renders at the right edge, negatives move left.
                // Centered (locked) mode keeps the element on the status-bar centre.
                val alignment = if (centered) Alignment.Center else Alignment.CenterEnd
                val xShift = if (centered) previewOffset else previewOffset - previewTravel
                DuoRiveStill(
                    modifier = Modifier
                        .align(alignment)
                        .absoluteOffset(x = xShift.dp)
                        .size(32.dp)
                        .pointerInput(enabled, centered, limit, previewTravel) {
                            if (!enabled) return@pointerInput
                            detectHorizontalDragGestures { _, amount ->
                                val dragScale = limit / previewTravel.coerceAtLeast(1f)
                                val hi = if (centered) limit else 0f
                                val next = (drag + amount / density.density * dragScale)
                                    .coerceIn(-limit, hi)
                                drag = next
                                onOffset(next.toInt())
                            }
                        },
                    visual = visual ?: DuoMapping.visual(
                        level = 40,
                        charging = false,
                        saver = false,
                        showPercent = true,
                        wifiLevel = 3,
                        cellLevel = 4,
                        airplane = false
                    ),
                    rawRes = rawRes
                )
            }
        }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hint ?: stringResource(R.string.settings_position_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.settings_position_value, drag.toInt()), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    drag = 0f
                    onOffset(0)
                },
                enabled = enabled
            ) { Text(stringResource(R.string.settings_position_reset)) }
        }
    }
}

/** Maps a real screen offset onto the smaller status-bar preview.
 *
 * centered: 0 -> 0 (middle), ±limit -> ±travel (locked ring).
 * normal:   0 -> +travel (right edge), -limit -> -travel (left), positive values clamp at +travel.
 */
internal fun positionPreviewOffset(offsetDp: Float, limitDp: Float, previewTravelDp: Float, centered: Boolean = false): Float {
    if (limitDp <= 0f || previewTravelDp <= 0f) return 0f
    if (centered) {
        return offsetDp.coerceIn(-limitDp, limitDp) / limitDp * previewTravelDp
    }
    val clamped = offsetDp.coerceIn(-limitDp, 0f)
    return (1f + 2f * clamped / limitDp) * previewTravelDp
}

/** The locked ring travels from screen center to either edge; strip-anchored parts can cross it all. */
internal fun positionOffsetLimit(screenWidthDp: Int, centered: Boolean): Int {
    val width = screenWidthDp.coerceAtLeast(1)
    return if (centered) (width + 1) / 2 else width
}

/** Moves a position editor by one dp in [direction], without crossing its configured bounds. */
internal fun stepPositionOffset(offsetDp: Int, direction: Int, limitDp: Int): Int {
    val limit = limitDp.coerceAtLeast(1)
    return (offsetDp + direction.coerceIn(-1, 1)).coerceIn(-limit, limit)
}

@Composable
internal fun SettingSwitch(
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
        GlassSwitch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/**
 * Issue #4: the Shizuku icon-hiding control.
 *
 * Owns its own Shizuku state (running / permission / last result) so it never blocks the rest of the
 * screen. The permission callback re-reads the wish from [DuoPrefs] instead of closing over a value a
 * recomposition may have replaced.
 */
@Composable
internal fun IconHidingSetting(enabled: Boolean) {
    val context = LocalContext.current
    var wanted by remember { mutableStateOf(DuoPrefs.hideStockIcons(context)) }
    var running by remember { mutableStateOf(StockIconHider.isShizukuRunning()) }
    var granted by remember { mutableStateOf(StockIconHider.isPermissionGranted()) }
    var note by remember { mutableStateOf<String?>(null) }

    fun apply(hide: Boolean) {
        when {
            !running -> note = null
            !granted -> if (hide) StockIconHider.requestPermission() else {
                note = context.getString(R.string.settings_icons_need_permission)
            }
            else -> StockIconHider.apply(context, hide) { ok ->
                note = if (ok) null else context.getString(R.string.settings_icons_failed)
            }
        }
    }

    val onPermission: (Boolean) -> Unit = remember {
        { grantedNow ->
            granted = grantedNow
            if (grantedNow && DuoPrefs.hideStockIcons(context)) {
                StockIconHider.apply(context, true) { ok ->
                    note = if (ok) null else context.getString(R.string.settings_icons_failed)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        StockIconHider.observePermissionResult(onPermission)
        // Shizuku may have been started after the screen opened, so check once when the row appears.
        running = StockIconHider.isShizukuRunning()
        granted = StockIconHider.isPermissionGranted()
        onDispose { StockIconHider.stopObservingPermissionResult(onPermission) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingSwitch(
            label = stringResource(R.string.settings_hide_icons),
            detail = stringResource(R.string.settings_hide_icons_detail),
            checked = wanted,
            enabled = enabled
        ) { next ->
            wanted = next
            DuoPrefs.writeHideStockIcons(context, next)
            apply(next)
        }
        val status = when {
            !running -> stringResource(R.string.settings_icons_no_shizuku)
            !granted -> stringResource(R.string.settings_icons_need_permission)
            note != null -> note!!
            else -> stringResource(R.string.settings_icons_ready)
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (running && !granted) {
            OutlinedButton(onClick = { StockIconHider.requestPermission() }, enabled = enabled) {
                Text(stringResource(R.string.settings_icons_grant))
            }
        }
    }
}

@Composable
internal fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
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
        GlassSlider(value = value, onValueChange = onChange, range = range, steps = steps, enabled = enabled)
    }
}

/** Pick one of Auto Expand's actions for a gesture. */
@Composable
internal fun ActionPicker(
    label: String,
    selectedKey: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    OptionPicker(label, DuoActions.ALL, selectedKey, enabled, onSelect)
}

/** A plain labelled dropdown over a (key → label) list: no experimental API needed. */
@Composable
internal fun OptionPicker(
    label: String,
    options: List<Pair<String, String>>,
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
                Text(options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, name) ->
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

/**
 * The diagnostics privilege control (root / Shizuku).
 *
 * Root is optional and is only used to **read** the system log into a bug report the user explicitly
 * sends, so it is never requested until the user asks — no root prompt appears uninvited. Shizuku is the
 * alternative that also works with a non-rooted phone started over adb. Both are best-effort: with
 * neither, a report still carries the module's own log and the device facts.
 */
@Composable
internal fun RootAccessSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(DuoPrefs.rootAllowed(context)) }
    var running by remember { mutableStateOf(StockIconHider.isShizukuRunning()) }
    var granted by remember { mutableStateOf(StockIconHider.isPermissionGranted()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    val onPermission: (Boolean) -> Unit = remember {
        { grantedNow -> granted = grantedNow }
    }

    DisposableEffect(Unit) {
        StockIconHider.observePermissionResult(onPermission)
        // Re-read when the row appears: Shizuku may have been started after the screen opened.
        running = StockIconHider.isShizukuRunning()
        granted = StockIconHider.isPermissionGranted()
        onDispose { StockIconHider.stopObservingPermissionResult(onPermission) }
    }

    // If root was granted before, verify it still is. Revoking it in the root manager (KernelSU Next /
    // SukiSU) otherwise leaves the stored flag `true` and the guide below never appears.
    LaunchedEffect(Unit) {
        if (allowed) {
            val ok = withContext(Dispatchers.IO) { RootLogs.recheckRoot(context) }
            if (!ok) {
                allowed = false
                result = context.getString(R.string.settings_root_result_failed)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_root_detail),
            style = MaterialTheme.typography.bodySmall
        )
        val status = when {
            allowed -> stringResource(R.string.settings_root_granted)
            running && granted -> stringResource(R.string.settings_root_shizuku)
            running -> stringResource(R.string.settings_icons_need_permission)
            else -> stringResource(R.string.settings_root_denied)
        }
        val elevated = allowed || (running && granted)
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (elevated) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
        )
        // The outcome of the last attempt, so a tap that fails says why instead of looking dead.
        result?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!allowed) {
            // A screenshot of the root manager's per-app Superuser screen, with the terse steps below it,
            // so the one case that needs a manual toggle is shown rather than described.
            // The animated GIF of the root manager's per-app Superuser toggle. Compose's Image does not
            // animate a GIF, so it is hosted in an ImageView. ImageDecoder is used explicitly, because
            // `setImageResource` does not always hand back an animated drawable.
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = ctx.getString(R.string.settings_root_guide_image)
                        runCatching {
                            val src = android.graphics.ImageDecoder.createSource(
                                ctx.resources, R.drawable.guide_root_superuser
                            )
                            when (val d = android.graphics.ImageDecoder.decodeDrawable(src)) {
                                is android.graphics.drawable.AnimatedImageDrawable -> {
                                    d.repeatCount =
                                        android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                                    setImageDrawable(d)
                                    d.start()
                                }
                                else -> setImageDrawable(d)
                            }
                        }.onFailure {
                            setImageResource(R.drawable.guide_root_superuser)
                        }
                        // AnimatedImageDrawable only runs once it is attached and visible; start it again
                        // after the view is attached so the loop always begins.
                        post {
                            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
            Text(
                text = stringResource(R.string.settings_root_guide_steps),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.settings_root_guide_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            OutlinedButton(
                onClick = {
                    if (!busy) {
                        busy = true
                        result = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { RootLogs.requestRoot(context) }
                            allowed = ok
                            busy = false
                            result = context.getString(
                                if (ok) R.string.settings_root_result_ok
                                else R.string.settings_root_result_failed
                            )
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (busy) R.string.settings_root_checking else R.string.settings_root_allow
                    )
                )
            }
        }
        if (running && !granted) {
            OutlinedButton(
                onClick = { StockIconHider.requestPermission() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_icons_grant))
            }
        }
    }
}
