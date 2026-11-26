package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.ProcessIdentity
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

    fun report(
        ctx: Context,
        stage: Int,
        settings: ModuleSettings?,
        /** The mode that owns the bar; a safe default keeps older callers/tests source-compatible. */
        owner: BarOwner = BarOwner.NONE
    ) {
        L.guard("DuoHook status report") {
            val element = duo()
            val status = hookStatusText(
                owner = owner.tag,
                stage = stage,
                renderer = element?.rendererName ?: "none",
                attached = element != null,
                settings = settings,
                riveAttempts = DuoGuard(ctx).attempts(),
                riveBind = RiveBindReport.reason()
            )
            // L already prefixes process/pid; drop the status's own copy so the log line has one
            // identity while the app-facing status keeps it.
            L.i("status -> app: " + status.removePrefix(hookStatusPrefix(owner.tag)))
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
 * The identity every app-facing status starts with. One shape for the status text and for the other
 * status writes (load heartbeat, attach failure), so no report can claim a state without saying which
 * process and owner produced it.
 */
internal fun hookStatusPrefix(owner: String): String =
    "process=${ProcessIdentity.processName()} pid=${ProcessIdentity.pid()} · owner=$owner · "

/**
 * The app-visible status line: process identity first, then the owner and the facts the diagnostics
 * screen already showed. Deterministic once the process identity is pinned, so the exact shape is
 * tested without a Context.
 */
internal fun hookStatusText(
    owner: String,
    stage: Int,
    renderer: String,
    attached: Boolean,
    settings: ModuleSettings?,
    riveAttempts: Int,
    /** B3: the Rive bind reason code; appended only when Rive failed. */
    riveBind: String? = null
): String = buildString {
    append(hookStatusPrefix(owner))
    append("stage=").append(stage)
    append(" · renderer=").append(renderer)
    append(" · attached=").append(attached)
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
    append(" · riveAttempts=").append(riveAttempts)
    if (riveBind != null) append(" · riveBind=").append(riveBind)
}
