package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tells the app what the module is actually doing, so diagnostics shows facts, not intentions.
 *
 * One instance lives on the single [DuoHook] for the process, which keeps the one-shot diagnostic
 * dump per-process.
 */
internal class HookReporter(
    private val statusBarRoot: () -> View?,
    private val duo: () -> DuoElement?,
    /** Other windows the module draws into (shade/keyguard window, shade header), for the dump. */
    private val extraRoots: () -> List<Pair<String, View?>> = { emptyList() }
) {

    /** The debug diagnostic dump is written to the log once per process, not on every settings change. */
    private val diagnosticsLogged = AtomicBoolean(false)

    /**
     * The shade header tree as it was last laid out (Issue #1). Snapshotted while the shade is open,
     * because by the time the app asks for a report the shade has closed and the bounds are gone.
     */
    @Volatile
    private var extrasSnapshot: String = ""

    /** Stores a rendered tree snapshot (see [Diag.treeText]) for the next diagnostic dump. */
    fun captureExtras(snapshot: String) {
        extrasSnapshot = snapshot
    }

    fun report(ctx: Context, stage: Int, settings: ModuleSettings?) {
        L.guard("DuoHook status report") {
            val element = duo()
            val renderer = element?.rendererName ?: "none"
            val status = buildString {
                append("stage=").append(stage)
                append(" · renderer=").append(renderer)
                append(" · attached=").append(element != null)
                settings?.let {
                    append(" · size=").append(it.sizePercent).append('%')
                    append(" · offset=").append(it.offsetX).append("dp")
                    append(" · split=").append(it.splitIndicators)
                    append(" · lock=").append(it.lockRing)
                    append(" · icons=").append(it.indicatorsOffsetX).append("dp")
                    append(" · edge=").append(it.edgePadding).append('%')
                    append(" · percent=").append(it.showPercent)
                    append(" · rev=").append(it.revision)
                }
                append(" · riveAttempts=").append(DuoGuard(ctx).attempts())
            }
            L.i("status -> app: $status")
            DuoSettingsClient.report(ctx, status)
            reportDiagnostics(ctx, stage, settings, element)
        }
    }

    /**
     * Sends the full diagnostic dump to the app (so its "Save status to a file" button ships it) and
     * writes it to the log once per process. Always on — not debug-only — so any release user can pull a
     * complete bug report without a special build.
     *
     * [iosLines] defaults to the attached surfaces' self-diagnosis (R3 A5.3); passing an explicit list
     * keeps the reporter testable and the existing callers source-compatible.
     */
    fun reportDiagnostics(
        ctx: Context,
        stage: Int,
        settings: ModuleSettings?,
        element: DuoElement?,
        iosLines: List<String> = IosDiagnostics.lines()
    ) {
        val tree = Diag.collect(
            ctx,
            statusBarRoot(),
            stage,
            settings,
            element,
            extraRoots(),
            extrasSnapshot,
            iosLines
        )
        // The module's own log rides along, so the report carries the module's lines even on a device
        // where the app cannot run `su` to read LSPosed's log file. Read before logging the tree, so the
        // dump does not log itself into the buffer.
        val moduleLog = L.recentText()
        val dump = tree +
            "\n=== module log (from the module, no root needed) ===\n" +
            moduleLog + (if (moduleLog.isBlank()) "" else "\n")
        DuoSettingsClient.reportDump(ctx, dump)
        if (diagnosticsLogged.compareAndSet(false, true)) {
            L.i("--- diagnostic dump (debug build) ---")
            Diag.log(tree)
            L.i("--- end diagnostic dump ---")
        }
    }
}

/**
 * The app-visible status line: process identity first, then the owner and the facts the diagnostics
 * screen already showed. Pure so the exact shape is pinned without a Context.
 *
 * RED STUB: returns an empty line so the status-identity test fails before the fix.
 */
internal fun hookStatusText(
    process: String,
    pid: Int,
    owner: String,
    stage: Int,
    renderer: String,
    attached: Boolean,
    settings: ModuleSettings?,
    riveAttempts: Int
): String = ""
