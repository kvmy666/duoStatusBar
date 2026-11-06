package io.github.kvmy666.duostatusbar.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.rememberResumed
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.BackupCodec
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealthCheck
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.StageOverride
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import io.github.kvmy666.duostatusbar.settings.TelegramLog
import io.github.kvmy666.duostatusbar.settings.UpdateChecker
import io.github.kvmy666.duostatusbar.settings.UpdateDownloadWorker
import io.github.kvmy666.duostatusbar.settings.UpdateInfo
import io.github.kvmy666.duostatusbar.settings.UpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The settings screen (FR-03/09/10/11/16/17).
 *
 * Every change is written to the app's own storage and then broadcast, which is what makes it reach the
 * status bar immediately: the module listens for that broadcast and re-reads (see
 * `hook/DuoHook.hookSettingsChanges`). The app cannot write to the module directly, so this handshake is
 * the mechanism — the app owns the values, the module applies them.
 *
 * Portrait and landscape each have their own copy. The phone's current orientation is the copy on
 * screen and the copy the status bar uses; rotating swaps both.
 *
 * One exception is the size: resizing the Rive view while System UI is running is what used to take it
 * down, so a size change is only applied on the next start. That is why the size row carries a Restart
 * button and says so.
 *
 * [showCustomization] is false for the Settings route opened from the Home gear: that route is the
 * health/backup/about page, while the Customization tab owns the full set of look-and-feel sections.
 * Keeping the two routes distinct avoids showing the same controls twice.
 */
@Composable
fun DuoSettingsScreen(
    modifier: Modifier = Modifier,
    showCustomization: Boolean = true
) {
    val context = LocalContext.current
    val orientation = DuoOrientation.of(LocalConfiguration.current.orientation)
    var settings by remember(orientation) { mutableStateOf(DuoPrefs.read(context, orientation)) }
    var status by remember { mutableStateOf(DuoPrefs.status(context)) }
    var history by remember { mutableStateOf(DuoPrefs.statusHistory(context)) }
    var dump by remember { mutableStateOf(DuoPrefs.dump(context)) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var collecting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }
    var moduleLoadAt by remember { mutableStateOf(DuoPrefs.moduleLoadTime(context)) }
    var fallback by remember { mutableStateOf(DuoPrefs.fallback(context)) }
    // A leftover adb stage override pins the module to the simple drawing; the app can read it.
    var stageOverride by remember { mutableStateOf(StageOverride.read(context)) }
    var clearingOverride by remember { mutableStateOf(false) }
    var overrideFailed by remember { mutableStateOf(false) }
    // Whether the module is working, running old code, or unable to report (see ModuleHealthCheck).
    var moduleHealth by remember { mutableStateOf(ModuleHealthCheck.of(context)) }
    // Whether a report has an evidence source: root granted, or Shizuku granted. The module's own log
    // (moduleLoadAt > 0) is the third source and needs no privilege. Re-read on the poll below so the
    // Send button enables right after the user grants access.
    var accessGranted by remember {
        mutableStateOf(DuoPrefs.rootAllowed(context) || StockIconHider.isPermissionGranted())
    }
    var checkUpdates by remember { mutableStateOf(DuoPrefs.checkUpdates(context)) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var backupMessage by remember { mutableStateOf("") }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf("") }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Export: the system file picker, so the log can be saved anywhere (Downloads, Drive, …) and shown
    // to anyone — independent of Telegram or a live network.
    val exportLog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val text = pendingExport
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) R.string.settings_log_saved else R.string.settings_log_save_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = DuoPrefs.backupJson(context)
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("no stream")
                    }.isSuccess
                }
                val msg = context.getString(
                    if (ok) R.string.settings_backup_exported else R.string.settings_backup_failed
                )
                backupMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text == null) {
                    importError = BackupCodec.BAD_JSON
                    val msg = context.getString(R.string.settings_backup_failed)
                    backupMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val decoded = BackupCodec.decode(text)
                if (!decoded.isSuccess) {
                    importError = decoded.error ?: BackupCodec.BAD_JSON
                    val msg = context.getString(R.string.settings_backup_invalid)
                    backupMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                importError = ""
                pendingImportJson = text
            }
        }
    }
    /** Settings search: a row is shown when the query appears in its label or its detail. */
    fun matches(vararg text: String): Boolean =
        query.isBlank() || text.any { it.contains(query.trim(), ignoreCase = true) }

    val search = SearchGate { matches(*it) }

    // The module answers an instant after the broadcast; re-reading is simpler than a callback.
    // Pause the 1.5 s re-read while the tab is backgrounded: the values only change when the module
    // pushes them, so a hidden tab must not keep waking the binder/PackageManager.
    val resumed = rememberResumed()
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            delay(1500)
            status = DuoPrefs.status(context)
            history = DuoPrefs.statusHistory(context)
            dump = DuoPrefs.dump(context)
            moduleLoadAt = DuoPrefs.moduleLoadTime(context)
            fallback = DuoPrefs.fallback(context)
            stageOverride = StageOverride.read(context)
            moduleHealth = ModuleHealthCheck.of(context)
            accessGranted = DuoPrefs.rootAllowed(context) || StockIconHider.isPermissionGranted()
        }
    }

    fun update(new: DuoSettings) {
        val wasEnabled = settings.enabled
        settings = new
        DuoPrefs.write(context, new, orientation)
        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
        // Also publish on the provider-independent channel, so a ROM where System UI cannot see the
        // provider (One UI 8) still receives the change.
        SettingsBridge.push(context)
        // Issue #4: the Shizuku icon hiding only makes sense while the element is drawing. Turning the
        // master switch off puts the stock icons back rather than leaving a bare status bar. The other
        // orientation can still be on, and that blacklist is one value for the whole phone, so it stays
        // until both orientations are off.
        val other = if (orientation == DuoOrientation.LANDSCAPE) {
            DuoOrientation.PORTRAIT
        } else {
            DuoOrientation.LANDSCAPE
        }
        if (wasEnabled && !new.enabled && DuoPrefs.hideStockIcons(context) &&
            !DuoPrefs.read(context, other).enabled
        ) {
            StockIconHider.apply(context, false) { }
        }
    }

    val onUpdate: (DuoSettings) -> Unit = { update(it) }

    /**
     * Asks the module for a fresh diagnostic dump and returns it, so a report captures the bar as it is
     * right now (e.g. the shade open) rather than the one-shot boot dump. Falls back to the last stored
     * dump if the module is unreachable or does not answer in time.
     */
    suspend fun refreshedDump(): String = try {
        context.sendBroadcast(
            Intent(DuoPrefs.ACTION_DIAGNOSTICS_REQUEST).setPackage("com.android.systemui")
        )
        withContext(Dispatchers.IO) { Thread.sleep(900) }
        DuoPrefs.dump(context).ifEmpty { dump }
    } catch (t: Throwable) {
        dump
    }

    // One-tap bug report, shared by the About button and the module-health card: packs the description,
    // the module status and the logs, uploads it, and falls back to the share sheet.
    fun collectAndSend() {
        if (collecting) return
        collecting = true
        scope.launch {
            val logs = withContext(Dispatchers.IO) { RootLogs.collect(context) }
            collecting = false
            val message = problem.trim()
            // Only trust the stored module status/dump while the module is actually running; after it is
            // unscoped the persisted values are the *previous* run and would read as a healthy report.
            val alive = moduleHealth.state != ModuleState.NEVER_LOADED
            val dumpValue = if (alive) refreshedDump() else ""
            val report = buildFullReport(
                context, problem, settings,
                if (alive) status else "",
                if (alive) history else emptyList(),
                dumpValue,
                if (alive) moduleLoadAt else 0L,
                logs
            )
            // Prefer the self-contained report ZIP; fall back to the legacy text path when it cannot be
            // built (no diagnostics dir, IO failure, ...).
            val zip = withContext(Dispatchers.IO) {
                writeReportZip(context, report, logs, if (alive) status else "", dumpValue)
            }
            if (zip == null) {
                val file = writeDiagnostics(context, report)
                if (file == null) {
                    shareText(context, report)
                } else {
                    val caption = message.take(200).ifBlank { "Duo Status Bar log" }
                    val result = withContext(Dispatchers.IO) { TelegramLog.send(context, file, caption) }
                    when (result) {
                        TelegramLog.Result.SENT -> {
                            Toast.makeText(context, R.string.settings_log_sent, Toast.LENGTH_LONG).show()
                        }
                        TelegramLog.Result.RATE_LIMITED -> Toast.makeText(
                            context, R.string.settings_log_wait, Toast.LENGTH_LONG
                        ).show()
                        TelegramLog.Result.FAILED ->
                            fileUri(context, file)?.let { shareLog(context, it) } ?: shareText(context, report)
                    }
                }
            } else {
                val caption = message.take(200).ifBlank { "Duo Status Bar log" }
                val result = withContext(Dispatchers.IO) { TelegramLog.send(context, zip, caption) }
                when (result) {
                    TelegramLog.Result.SENT -> {
                        Toast.makeText(context, R.string.settings_log_sent, Toast.LENGTH_LONG).show()
                    }
                    TelegramLog.Result.RATE_LIMITED -> Toast.makeText(
                        context, R.string.settings_log_wait, Toast.LENGTH_LONG
                    ).show()
                    TelegramLog.Result.FAILED ->
                        fileUri(context, zip)?.let { shareReportZip(context, it) } ?: shareText(context, report)
                }
            }
        }
    }

    if (pendingImportJson != null) {
        val pending = pendingImportJson!!
        val count = BackupCodec.decode(pending).values?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text(stringResource(R.string.settings_backup_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_confirm_body) + " " + count) },
            confirmButton = {
                TextButton(onClick = {
                    val json = pendingImportJson
                    if (json != null) {
                        val result = DuoPrefs.restoreJson(context, json)
                        if (result.success) {
                            settings = DuoPrefs.read(context, orientation)
                            context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
                            SettingsBridge.push(context)
                            val msg = context.getString(R.string.settings_backup_imported)
                            backupMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        } else {
                            importError = result.error ?: BackupCodec.BAD_JSON
                            val msg = context.getString(
                                if (importError == BackupCodec.BAD_JSON ||
                                    importError == BackupCodec.BAD_VALUE ||
                                    importError == BackupCodec.EMPTY ||
                                    importError == BackupCodec.UNSUPPORTED_SCHEMA ||
                                    importError == BackupCodec.UNSUPPORTED_VERSION
                                ) R.string.settings_backup_invalid
                                else R.string.settings_backup_failed
                            )
                            backupMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        pendingImportJson = null
                    }
                }) {
                    Text(stringResource(R.string.settings_backup_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) {
                    Text(stringResource(R.string.settings_backup_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Issue #3: edge-to-edge draws under the system bars, so the content is inset away from the
            // status bar and navigation bar instead of being clipped by them.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(BRAND_WINE),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (showCustomization) {
            Text(
                text = stringResource(
                    if (orientation == DuoOrientation.LANDSCAPE) R.string.settings_orientation_landscape
                    else R.string.settings_orientation_portrait
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.settings_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Red health card: not loaded, running old code after an update, or loaded but unable to report
        // (the One UI 8 "Unknown authority" case). Recovery buttons plus a one-tap report.
        if (moduleHealth.state != ModuleState.OK && query.isBlank()) {
            ModuleHealthCard(
                state = moduleHealth.state,
                busy = collecting,
                onOpenLsposed = { openLsposed(context) },
                onRestart = { restartSystemUi(context) },
                onReboot = { scope.launch { withContext(Dispatchers.IO) { RootLogs.reboot() } } },
                onTelegram = { openTelegramChat(context) },
                onExport = {
                    if (!collecting) {
                        collecting = true
                        scope.launch {
                            val logs = withContext(Dispatchers.IO) { RootLogs.collect(context) }
                            collecting = false
                            val alive = moduleHealth.state != ModuleState.NEVER_LOADED
                            pendingExport =
                                buildFullReport(
                                    context, "", settings,
                                    if (alive) status else "",
                                    if (alive) history else emptyList(),
                                    if (alive) dump else "",
                                    if (alive) moduleLoadAt else 0L,
                                    logs
                                )
                            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            exportLog.launch("duo-log-$stamp.txt")
                        }
                    }
                },
                onReport = { collectAndSend() }
            )
        }

        // Fallback alert (see DuoPrefs.fallback): the module could not draw the animated element and is
        // using the simple drawing. Ask for the log where the developer watches — Telegram or GitHub.
        if (fallback.isNotEmpty() && query.isBlank()) {
            FallbackAlert(
                detail = fallback,
                busy = collecting,
                onTelegram = {
                    if (!collecting) {
                        collecting = true
                        scope.launch {
                            // Same full log as the About button: the module dump plus the device capture.
                            val logs = withContext(Dispatchers.IO) { RootLogs.collect(context) }
                            collecting = false
                            val alive = moduleHealth.state != ModuleState.NEVER_LOADED
                            sendLogOnTelegram(
                                context,
                                buildDiagnostics(
                                    context, settings,
                                    if (alive) status else "",
                                    if (alive) history else emptyList(),
                                    if (alive) dump else "",
                                    if (alive) moduleLoadAt else 0L
                                ) +
                                    "\n\n===== device capture =====\n" + logs
                            )
                        }
                    }
                },
                onGitHub = { openGitHubIssue(context, fallback, status) },
                onDismiss = {
                    DuoPrefs.writeFallback(context, "")
                    fallback = ""
                }
            )
        }

        // A leftover adb override is the other reason the simple drawing shows with no warning: the app's
        // own settings ask for Rive, but `Settings.Global` outranks them. Explain it and offer the clear.
        val override = stageOverride
        if (override != null && query.isBlank()) {
            StageOverrideAlert(
                value = override,
                busy = clearingOverride,
                command = StageOverride.clearCommand,
                failed = overrideFailed,
                onClear = {
                    if (!clearingOverride) {
                        clearingOverride = true
                        overrideFailed = false
                        scope.launch {
                            withContext(Dispatchers.IO) { RootLogs.clearStageOverride() }
                            clearingOverride = false
                            stageOverride = StageOverride.read(context)
                            if (stageOverride == null) {
                                // The module re-reads on a settings broadcast, but the override lived in a
                                // store it cached; a restart makes the change deterministic.
                                restartSystemUi(context)
                            } else {
                                overrideFailed = true
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.settings_override_failed, StageOverride.clearCommand
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        if (showCustomization) {
            CustomizeSection(
                settings = settings,
                onUpdate = onUpdate,
                matches = search,
                onRestart = { restartSystemUi(context) }
            )

            AnimationsSection(
                settings = settings,
                onUpdate = onUpdate,
                matches = search
            )

            AppearanceSection(
                settings = settings,
                onUpdate = onUpdate,
                matches = search
            )

            SurfacesSection(
                settings = settings,
                onUpdate = onUpdate,
                matches = search
            )

            IconsSection(
                settings = settings,
                onUpdate = onUpdate,
                matches = search
            )

            val autoExpand = remember { DuoActions.isAutoExpandInstalled(context) }
            ActionsSection(
                settings = settings,
                onUpdate = onUpdate,
                matches = search,
                autoExpand = autoExpand
            )

            LiveEventsSection(
                matches = search,
                settings = settings,
                onUpdate = onUpdate
            )
        }

        BackupSection(
            matches = search,
            message = backupMessage,
            onExport = {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                exportBackup.launch("duo-settings-$stamp.json")
            },
            onImport = { importBackup.launch(arrayOf("application/json")) }
        )

        AboutSection(
            matches = search,
            status = status,
            history = history,
            moduleLoadAt = moduleLoadAt,
            moduleAlive = moduleHealth.state != ModuleState.NEVER_LOADED,
            accessGranted = accessGranted,
            exporting = exporting,
            onExport = {
                exporting = true
                scope.launch {
                    val logs = withContext(Dispatchers.IO) { RootLogs.collect(context) }
                    exporting = false
                    val alive = moduleHealth.state != ModuleState.NEVER_LOADED
                    pendingExport = buildFullReport(
                        context, "", settings,
                        if (alive) status else "",
                        if (alive) history else emptyList(),
                        if (alive) refreshedDump() else "",
                        if (alive) moduleLoadAt else 0L,
                        logs
                    )
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportLog.launch("duo-log-$stamp.txt")
                }
            },
            checkUpdates = checkUpdates,
            onToggleUpdates = { on ->
                checkUpdates = on
                DuoPrefs.writeCheckUpdates(context, on)
                UpdateWorker.apply(context)
                if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            checkingUpdate = checkingUpdate,
            onCheckNow = {
                checkingUpdate = true
                updateMessage = ""
                updateInfo = null
                scope.launch {
                    val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
                    checkingUpdate = false
                    when {
                        info == null ->
                            updateMessage = context.getString(R.string.settings_update_failed)
                        UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME) -> {
                            updateInfo = info
                            updateMessage = context.getString(
                                R.string.settings_update_available, info.version
                            )
                        }
                        else -> updateMessage = context.getString(
                            R.string.settings_update_uptodate, BuildConfig.VERSION_NAME
                        )
                    }
                }
            },
            updateMessage = updateMessage,
            updateInfo = updateInfo,
            onDownload = {
                updateInfo?.let { info ->
                    UpdateDownloadWorker.enqueue(context, info.version, info.apkUrl, info.sha256)
                }
            }
        )
    }
}

/** Red Wine (FR-11): the app icon's background, reused for the header badge. */
private val BRAND_WINE = Color(0xFF7B1E3A)

/**
 * Asks the module to restart System UI so a size change takes effect.
 *
 * The module lives inside System UI, so killing its own process is the clean path — no root, no prompt.
 * The broadcast only reaches it when it is actually listening, though, and the old code assumed "the
 * module has loaded at least once" meant exactly that. It did not: a module that is injected but gated off
 * (the ColorOS/realme report: loaded, `stage=0`, no report) never registered the receiver, so the button
 * was silently dead. Now a fresh module load is the success signal: if System UI does not come back with a
 * newer load stamp, the app falls back to root, then to Shizuku — which is what makes the button work on a
 * ROM without `su` as long as Shizuku is running.
 */
internal fun restartSystemUi(context: Context) {
    val before = DuoPrefs.moduleLoadTime(context)
    context.sendBroadcast(Intent(DuoPrefs.ACTION_RESTART_SYSTEMUI))
    Thread {
        if (before > 0L && moduleReloadedSince(context, before)) return@Thread
        val ok = runCatching { RootLogs.restartSystemUi() }.getOrDefault(false)
        if (!ok && StockIconHider.isShizukuRunning() && StockIconHider.isPermissionGranted()) {
            StockIconHider.exec(
                context,
                "pkill -f com.android.systemui || killall com.android.systemui || " +
                    "kill -9 ${'$'}(pidof com.android.systemui)",
                {}
            )
        }
    }.start()
}

/**
 * Whether System UI came back with a newer module-load stamp within [timeoutMs] of the restart request.
 * The module stamps `Settings.Global` before it does anything else, so a newer value is direct proof that
 * the restart happened and the module (or at least LSPosed) came back.
 */
private fun moduleReloadedSince(context: Context, before: Long, timeoutMs: Long = 2_500L): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        if (DuoPrefs.moduleLoadTime(context) > before) return true
        if (System.currentTimeMillis() >= deadline) return false
        try {
            Thread.sleep(250)
        } catch (_: InterruptedException) {
            return DuoPrefs.moduleLoadTime(context) > before
        }
    }
}
