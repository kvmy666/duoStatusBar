package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState

/**
 * Why LSPosed never injected the module, as far as the app can prove it without guessing.
 *
 * The inputs are the two facts a never-loaded report can still obtain: whether the LSPosed manager
 * package is visible to the phone, and what LSPosed's own `modules_config.db` said about Duo (enabled
 * and the scope list), which the privileged capture reads when it can. When neither is readable the
 * answer stays [LoadCause.ENABLE_OR_SCOPE_UNKNOWN] instead of blaming the wrong step.
 */
internal enum class LoadCause {
    /** The module is not in the never-loaded state, or there is nothing to explain. */
    NOT_NEEDED,

    /** The LSPosed manager app is not installed (or not visible), so nothing can inject the module. */
    LSPOSED_MISSING,

    /** LSPosed is present and read Duo's row, but the module is switched off there. */
    NOT_ENABLED,

    /** Duo is enabled, but `com.android.systemui` is not in its scope list. */
    NOT_SCOPED,

    /** Enabled and scoped, yet the module never reported: the running System UI predates the change. */
    SCOPED_BUT_NOT_LOADED,

    /** The manager is present but its enabled/scope answer could not be read, so both are possible. */
    ENABLE_OR_SCOPE_UNKNOWN
}

/**
 * The diagnosis plus the two facts the report header states next to it. [reportCannotReadFrameworkLogs]
 * is true when this report had no root/Shizuku route, so it also has no LSPosed or framework logs to
 * back the conclusion. It is guidance only: it never triggers an automatic report.
 */
internal data class LoadDiagnosis(
    val cause: LoadCause,
    val lsposedInstalled: Boolean?,
    val reportCannotReadFrameworkLogs: Boolean
) {
    /** The `likely cause:` wording for the report, or null when there is nothing to explain. */
    fun reportCause(): String? = when (cause) {
        LoadCause.NOT_NEEDED -> null
        LoadCause.LSPOSED_MISSING ->
            "LSPosed not detected: its manager app is not installed or not visible."
        LoadCause.NOT_ENABLED -> "Duo Status Bar is not enabled in LSPosed."
        LoadCause.NOT_SCOPED -> "System UI is not in Duo Status Bar's LSPosed scope."
        LoadCause.SCOPED_BUT_NOT_LOADED ->
            "LSPosed has Duo enabled and System UI in scope, but the module did not load."
        LoadCause.ENABLE_OR_SCOPE_UNKNOWN ->
            "The module is not enabled in LSPosed, or System UI is not in its scope."
    }

    /** The `next step:` wording for the report, or null when there is nothing to fix. */
    fun reportStep(): String? = when (cause) {
        LoadCause.NOT_NEEDED -> null
        LoadCause.LSPOSED_MISSING -> "Install LSPosed (Zygisk) and reboot your phone."
        LoadCause.NOT_ENABLED -> "Enable Duo Status Bar in LSPosed, then restart System UI."
        LoadCause.NOT_SCOPED ->
            "Add System UI to Duo Status Bar's scope in LSPosed, then restart System UI."
        LoadCause.SCOPED_BUT_NOT_LOADED -> "Restart System UI or reboot your phone."
        LoadCause.ENABLE_OR_SCOPE_UNKNOWN ->
            "In LSPosed, enable Duo Status Bar and add System UI to its scope, then restart System UI."
    }
}

internal object ModuleLoadDiagnosis {

    const val LSPOSED_PACKAGE = "org.lsposed.manager"

    /** The capture's own section title; the report parser writes the same string. */
    const val SCOPE_SECTION = "LSPosed config (enabled / scope)"

    private const val ROUTE_SECTION = "capture route"

    /**
     * The privileged evidence a "root/Shizuku is missing" note is about. Breadcrumbs, crash records,
     * tombstones and the crash buffer are deliberately absent: they can be unreadable on a healthy
     * capture and must not claim the framework logs were unreadable.
     */
    private val FRAMEWORK_SECTIONS = setOf(
        "LSPosed/framework logs",
        "LSPosed log files",
        "LSPosed newest modules log",
        "framework manager",
        SCOPE_SECTION
    )

    private const val SYSTEM_UI = "com.android.systemui"

    /**
     * Pure rule, so every cause is unit-tested without a device. The order is deliberate: the state
     * gate first, then "no LSPosed at all", then the config query; anything unreadable falls through to
     * the combined hint rather than inventing "not enabled".
     */
    fun diagnose(
        state: ModuleState,
        lsposedInstalled: Boolean?,
        scope: RootLogs.ModuleScope?,
        reportCannotReadFrameworkLogs: Boolean
    ): LoadDiagnosis {
        val knownScope = knownScope(scope)
        val cause = when {
            state != ModuleState.NEVER_LOADED -> LoadCause.NOT_NEEDED
            lsposedInstalled == false -> LoadCause.LSPOSED_MISSING
            scope != null && scope.queried && scope.enabled == false -> LoadCause.NOT_ENABLED
            knownScope != null && !knownScope.contains(SYSTEM_UI, ignoreCase = true) ->
                LoadCause.NOT_SCOPED
            knownScope != null && knownScope.contains(SYSTEM_UI, ignoreCase = true) ->
                LoadCause.SCOPED_BUT_NOT_LOADED
            else -> LoadCause.ENABLE_OR_SCOPE_UNKNOWN
        }
        return LoadDiagnosis(cause, lsposedInstalled, reportCannotReadFrameworkLogs)
    }

    /**
     * Reads the two facts from the phone and an optional capture. Every Android call is guarded: a
     * PackageManager or Shizuku failure becomes "unknown", never an exception on the settings screen.
     */
    fun of(
        context: Context,
        health: ModuleHealth?,
        capture: RootCapture? = null,
        captureFailure: String? = null
    ): LoadDiagnosis {
        val section = capture?.sections?.firstOrNull {
            it.title == SCOPE_SECTION && it.state == SectionState.PRESENT
        }
        val scope = section?.let { runCatching { RootLogs.parseModuleScope(it.text) }.getOrNull() }
        val managerVisible = runCatching {
            context.packageManager.getApplicationInfo(LSPOSED_PACKAGE, 0)
        }.getOrNull()
        val lsposedInstalled = when {
            managerVisible != null -> true
            // A queried config proves LSPosed's data exists even if the manager package is hidden from
            // the app; never guess a second manager package id.
            scope?.queried == true -> true
            else -> false
        }
        val cannotRead = reportCannotReadFrameworkLogs(context, capture, captureFailure)
        val state = health?.state ?: return LoadDiagnosis(
            LoadCause.NOT_NEEDED,
            lsposedInstalled,
            cannotRead
        )
        return diagnose(state, lsposedInstalled, scope, cannotRead)
    }

    /** A readable scope value: present, non-blank, and not the parser's `unknown` placeholder. */
    private fun knownScope(scope: RootLogs.ModuleScope?): String? {
        if (scope == null || !scope.queried || scope.enabled != true) return null
        return scope.scope?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("unknown", ignoreCase = true)
        }
    }

    /**
     * Pure rule: the report cannot show LSPosed/framework logs when the capture route failed/was
     * skipped, or when a framework-evidence section (not a breadcrumb/crash/tombstone) is missing.
     * Unrelated unreadable sections must not claim the privileged framework logs were unreadable.
     */
    internal fun reportCannotReadFrameworkLogs(
        capture: RootCapture?,
        captureFailure: String?
    ): Boolean {
        if (!captureFailure.isNullOrBlank()) return true
        if (capture == null) return false
        // The capture's own `capture route` section carries the failure/skip reason in its text;
        // RootCapture.route is only the probe detail. Check the section state, both texts, then the
        // framework sections themselves.
        val route = capture.sections.firstOrNull { it.title == ROUTE_SECTION }
        if (route != null &&
            (route.state == SectionState.SKIPPED || route.state == SectionState.UNREADABLE)
        ) {
            return true
        }
        val routeText = route?.text ?: ""
        if (routeText.contains("captureFailure=") || routeText.contains("captureSkipped=")) {
            return true
        }
        if (capture.route.contains("captureFailure=") ||
            capture.route.contains("captureSkipped=")
        ) {
            return true
        }
        return capture.sections.any {
            it.title in FRAMEWORK_SECTIONS &&
                (it.state == SectionState.SKIPPED || it.state == SectionState.UNREADABLE)
        }
    }

    /**
     * The capability flag for [of]. A structured capture (or a recorded failure) uses the pure rule;
     * the health card, which has no capture yet, falls back to whether root/Shizuku is even granted.
     * The module's own log needs no privilege and is not covered by this flag.
     */
    private fun reportCannotReadFrameworkLogs(
        context: Context,
        capture: RootCapture?,
        captureFailure: String?
    ): Boolean {
        if (capture != null || !captureFailure.isNullOrBlank()) {
            return reportCannotReadFrameworkLogs(capture, captureFailure)
        }
        val root = runCatching { DuoPrefs.rootAllowed(context) }.getOrDefault(false)
        if (root) return false
        val shizuku = runCatching {
            StockIconHider.isShizukuRunning() && StockIconHider.isPermissionGranted()
        }.getOrDefault(false)
        return !shizuku
    }
}
