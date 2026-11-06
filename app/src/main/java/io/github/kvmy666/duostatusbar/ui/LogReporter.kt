package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.hook.rom.RomProfiles
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealthCheck
import io.github.kvmy666.duostatusbar.settings.TelegramLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** FR-28: where the support button goes. */
internal const val DONATE_URL = "https://paypal.me/kroomfahd"

/** Where a fallback report goes: the developer's Telegram, and the issue tracker. */
internal const val TELEGRAM_CHAT_URL = "https://t.me/kvmy1"
internal const val GITHUB_NEW_ISSUE = "https://github.com/kvmy666/duoStatusBar/issues/new"
internal val TELEGRAM_PACKAGES = listOf(
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.telegram.messenger.beta"
)

/**
 * Saves the diagnostics file and hands it to Telegram, so one press gets the log on its way. When
 * Telegram is not installed the chat is opened instead, and the file can be attached from
 * "Save status to a file".
 */
internal fun sendLogOnTelegram(context: Context, report: String) {
    val file = writeDiagnostics(context, report)
    // Preferred: the bot uploads it straight to the developer's chat, no user step.
    if (file != null &&
        TelegramLog.send(context, file, "Duo Status Bar fallback report") == TelegramLog.Result.SENT
    ) return
    // Bot not configured or offline: hand the file to Telegram if installed, else open the chat.
    val uri = file?.let { fileUri(context, it) }
    val telegram = TELEGRAM_PACKAGES.firstOrNull { pkg ->
        runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
    }
    if (uri != null && telegram != null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Duo Status Bar log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(telegram)
        }
        if (runCatching { context.startActivity(send) }.isSuccess) return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHAT_URL))) }
        .onFailure { L.w("telegram open: ${it.message}") }
}

/** Opens the developer's Telegram chat, for a user who cannot yet produce a module report. */
internal fun openTelegramChat(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHAT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { L.w("telegram chat open: ${it.message}") }
}

/** Opens the LSPosed manager, where the module is enabled and System UI is added to its scope. */
internal fun openLsposed(context: Context): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
        ?: return false
    return runCatching {
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

/** Opens a new GitHub issue with the device and status pre-filled; the report file can be attached there. */
internal fun openGitHubIssue(context: Context, fallback: String, status: String) {
    val body = buildString {
        appendLine("Duo Status Bar fallback report")
        appendLine()
        appendLine("fallback: $fallback")
        appendLine("status: $status")
        appendLine(
            "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        )
        append("Please attach the diagnostics file shared from the app's About section.")
    }
    val url = "$GITHUB_NEW_ISSUE?body=${Uri.encode(body)}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { L.w("github open: ${it.message}") }
}

/** The report the About buttons send — one text, shared or written to a file. */
internal fun buildDiagnostics(
    context: Context,
    settings: DuoSettings,
    status: String,
    history: List<String>,
    dump: String,
    moduleLoadAt: Long
): String =
    buildString {
        appendLine("Duo Status Bar diagnostics")
        appendLine("settings: $settings")
        appendLine(
            "module load: " + if (moduleLoadAt <= 0L) {
                "never (LSPosed has not injected the module into System UI)"
            } else {
                formatTimestamp(moduleLoadAt)
            }
        )
        appendLine("module: ").append(status.ifEmpty { "no report yet" })
        if (history.isNotEmpty()) {
            appendLine("history:")
            history.forEach { appendLine("  $it") }
        }
        // Always present, so even a report with no module log says exactly which build, device and
        // module state it came from - the facts that fix a "nothing happens" report.
        appendLine()
        append(appEnvironment(context))
        // The debug build's full dump (build identity, id probes, view tree, readers). Empty in release.
        if (dump.isNotEmpty()) {
            appendLine()
            appendLine(dump)
        }
    }

/**
 * Facts the app can always read, even when the module never loaded and there is no root. This is what
 * turns a ~1 KB "module load: never" report into something actionable: the app build, the device identity,
 * the ROM adapter and, above all, whether LSPosed actually has the module enabled and scoped.
 */
internal fun appEnvironment(context: Context): String = buildString {
    appendLine("=== app environment ===")
    appendLine("app: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, debug=${BuildConfig.DEBUG})")
    appendLine(
        "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.BRAND} " +
            "${android.os.Build.MODEL} / ${android.os.Build.DEVICE} / ${android.os.Build.PRODUCT}"
    )
    appendLine("android: SDK ${android.os.Build.VERSION.SDK_INT} RELEASE ${android.os.Build.VERSION.RELEASE}")
    val sysui = packageVersion(context, "com.android.systemui") ?: "not visible"
    appendLine("systemui: $sysui")
    val rom = runCatching {
        RomDetection.forThisRom(
            android.os.Build.MANUFACTURER.orEmpty(), android.os.Build.BRAND.orEmpty(),
            android.os.Build.PRODUCT.orEmpty(), android.os.Build.DISPLAY.orEmpty(), RomProfiles.load(context)
        )
    }.getOrNull()
    appendLine("rom adapter: ${rom?.id ?: "unknown"} (measured=${rom?.measured ?: false})")
    val lsposed = hasPackage(context, "org.lsposed.manager")
    appendLine("lsposed manager installed: $lsposed")
    append("module state: ${runCatching { ModuleHealthCheck.of(context).state }.getOrDefault("unknown")}")
}

private fun packageVersion(context: Context, pkg: String): String? = try {
    val info = context.packageManager.getPackageInfo(pkg, 0)
    "${info.versionName} (code ${@Suppress("DEPRECATION") info.versionCode})"
} catch (_: Throwable) {
    null
}

private fun hasPackage(context: Context, pkg: String): Boolean =
    runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess

/** The complete bug report: the user's description, the diagnostics, then the device capture. */
internal fun buildFullReport(
    context: Context,
    problem: String,
    settings: DuoSettings,
    status: String,
    history: List<String>,
    dump: String,
    moduleLoadAt: Long,
    logs: String
): String = buildString {
    val message = problem.trim()
    if (message.isNotEmpty()) {
        appendLine("user message:")
        appendLine(message)
        appendLine()
    }
    append(buildDiagnostics(context, settings, status, history, dump, moduleLoadAt))
    append("\n\n===== device capture =====\n")
    append(logs)
}

internal fun formatTimestamp(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

internal fun shareText(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(send, "Share diagnostics"))
}

/**
 * Hands the collected log to a share sheet, where the user picks Telegram (the developer, @kvmy1),
 * another app, or "Save to Files" — one button, "send it" or "keep it", as the user prefers.
 */
internal fun shareLog(context: Context, file: Uri) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, file)
        putExtra(Intent.EXTRA_TEXT, "Duo Status Bar log — @kvmy1")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(share, context.getString(R.string.settings_log_share_title))
    )
}

/**
 * FR-28: writes the report to the app's own diagnostics directory and returns a shareable Uri.
 *
 * A bug report needs the whole log, and a shared string gets truncated by chat apps. The file lives in
 * the app's external files dir and is handed out through a FileProvider, so nothing else is exposed.
 * Returns null (and the caller shares nothing) if the directory is unavailable.
 */
internal fun writeDiagnostics(context: Context, report: String): File? = try {
    val dir = context.getExternalFilesDir("diagnostics") ?: return null
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    File(dir, "duo-diagnostics-$stamp.txt").apply { writeText(report) }
} catch (t: Throwable) {
    L.w("diagnostics file: ${t.javaClass.simpleName}: ${t.message}")
    null
}

/** A shareable Uri for a written diagnostics [file], or null if the FileProvider cannot serve it. */
internal fun fileUri(context: Context, file: File): Uri? = try {
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
} catch (t: Throwable) {
    L.w("diagnostics uri: ${t.javaClass.simpleName}: ${t.message}")
    null
}

/**
 * T3b2a: builds a self-contained bug-report ZIP (manifest + diagnostics + module log + logcat) in the
 * app's diagnostics directory, prunes old reports and text dumps, and returns the file (or null on
 * failure). UI wiring is intentionally not part of this function.
 */
internal fun writeReportZip(
    context: Context,
    report: String,
    logs: String,
    status: String,
    dump: String
): File? = try {
    val dir = context.getExternalFilesDir("diagnostics") ?: return null
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val destination = File(dir, "duo-report-$stamp.zip")

    val rom = runCatching {
        RomDetection.forThisRom(
            android.os.Build.MANUFACTURER.orEmpty(), android.os.Build.BRAND.orEmpty(),
            android.os.Build.PRODUCT.orEmpty(), android.os.Build.DISPLAY.orEmpty(),
            RomProfiles.load(context)
        )
    }.getOrNull()

    val device = mapOf(
        "manufacturer" to android.os.Build.MANUFACTURER.orEmpty(),
        "brand" to android.os.Build.BRAND.orEmpty(),
        "model" to android.os.Build.MODEL.orEmpty(),
        "device" to android.os.Build.DEVICE.orEmpty(),
        "product" to android.os.Build.PRODUCT.orEmpty(),
        "sdk" to android.os.Build.VERSION.SDK_INT.toString(),
        "release" to android.os.Build.VERSION.RELEASE.orEmpty()
    )
    val adapter = mapOf(
        "id" to (rom?.id ?: "unknown"),
        "measured" to (rom?.measured ?: false).toString(),
        "shadeMeasured" to (rom?.shadeMeasured ?: false).toString(),
        "coverHook" to (rom?.coverHook ?: false).toString(),
        "panelExpansionKind" to (rom?.panelExpansionKind ?: "unknown"),
        "panelHookMeasured" to (rom?.panelHookMeasured ?: false).toString(),
        "panelOwnerAvailable" to (rom?.panelOwnerAvailable ?: false).toString()
    )

    val moduleLog = runCatching { L.recentText() }.getOrElse { status }

    val inputs = DuoReportInputs(
        generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
        appVersion = "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
        buildType = BuildConfig.BUILD_TYPE,
        device = device,
        adapter = adapter,
        diagnostics = report,
        moduleLog = moduleLog,
        logcat = logs,
        diagnosticsDump = dump.takeIf { it.isNotBlank() },
        screenshotPng = null
    )

    DuoReport.writeZip(destination, inputs)
    DuoReport.prune(dir, "duo-report-", 10)
    DuoReport.prune(dir, "duo-diagnostics-", 10)
    destination
} catch (t: Throwable) {
    L.w("report zip: ${t.javaClass.simpleName}: ${t.message}")
    null
}

/**
 * Shares a report ZIP through a chooser: MIME `application/zip`, captioned for the developer, with read
 * permission granted to the chosen app.
 */
internal fun shareReportZip(context: Context, file: Uri) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, file)
        putExtra(Intent.EXTRA_TEXT, "Duo Status Bar report — @kvmy1")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(share, context.getString(R.string.settings_log_share_title))
    )
}
