package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.Intent
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection

/**
 * One bounded ask of the running module for a *fresh* status and diagnostic dump.
 *
 * A report used to read the stored status/dump and hope: after an app update, the stored dump can be
 * from the previous build (field report: app 2.1.2, dump `version: 2.1.1` written the day before), and
 * the app then described the old run as if it were this one. [DuoPrefs.ACTION_DIAGNOSTICS_REQUEST] is
 * the existing on-demand route; this object makes every report path wait a bounded time for the write
 * it causes.
 *
 * Freshness has three proofs so an older module still counts: a newer [DuoPrefs.dumpAt] stamp (new
 * builds), changed dump text (the old build rewrites the dump but does not know the stamp), or a changed
 * status line. A module that never loaded is never asked, so a report on a phone without LSPosed does
 * not wait for the timeout.
 */
internal data class FreshSnapshot(
    val status: String,
    val dump: String,
    val dumpAt: Long,
    val refreshed: Boolean
)

internal object SnapshotRefresh {

    /** Poll granularity: often enough to notice the write, cheap enough to sleep most of the wait. */
    const val POLL_MS = 100L

    /** Total wait. The module writes both the status and the dump in the same broadcast turn. */
    const val TIMEOUT_MS = 2_800L

    /**
     * Whether asking can produce anything. A load or heartbeat stamp (even a stale one) or a stored
     * status means the module was injected at least once; a never-loaded install has none, and asking
     * would only make the report wait for a process that cannot answer.
     */
    fun shouldRequest(loadedAt: Long, heartbeatAt: Long, status: String): Boolean =
        loadedAt > 0L || heartbeatAt > 0L || status.isNotBlank()

    /**
     * Whether a report may read the snapshot's status/dump as evidence at all.
     *
     * A refreshed snapshot is live by definition. A stored one is only usable while the live health
     * says the module is not [ModuleState.NEVER_LOADED]: after an unscope and a System UI restart the
     * request times out, but the stored same-build status/dump from the dead run survive, and there is
     * no version label to catch them. Blanking those inputs is the one gate every report path shares.
     * An unknown state proves nothing, so it is not usable either.
     */
    fun evidenceUsable(snapshot: FreshSnapshot, state: ModuleState?): Boolean =
        snapshot.refreshed || (state != null && state != ModuleState.NEVER_LOADED)

    /** The stored values as a snapshot that does not claim to be fresh. */
    fun stored(context: Context): FreshSnapshot = try {
        FreshSnapshot(
            status = DuoPrefs.status(context),
            dump = DuoPrefs.dump(context),
            dumpAt = DuoPrefs.dumpAt(context),
            refreshed = false
        )
    } catch (_: Throwable) {
        FreshSnapshot("", "", 0L, false)
    }

    /**
     * Sends one request and polls until the module's write is visible or [timeoutMs] elapses.
     *
     * Pure enough for a JVM test: every clock, sleep and read is a lambda. It never throws and the
     * iteration cap is independent of the clock, so a stuck clock or a throwing sleep cannot hang a
     * report. With neither a new dump nor a changed status, the previous snapshot is returned unchanged
     * with `refreshed=false`.
     */
    fun poll(
        previous: FreshSnapshot,
        request: () -> Unit,
        readStatus: () -> String,
        readDump: () -> String,
        readDumpAt: () -> Long,
        now: () -> Long,
        sleep: (Long) -> Unit,
        timeoutMs: Long = TIMEOUT_MS,
        pollMs: Long = POLL_MS
    ): FreshSnapshot {
        runCatching { request() }
        val deadline = runCatching { now() }.getOrDefault(0L) + timeoutMs.coerceAtLeast(0L)
        val poll = pollMs.coerceAtLeast(0L)
        val attempts = if (poll <= 0L) 1 else (timeoutMs.coerceAtLeast(0L) / poll).toInt() + 2
        // The module writes the status first and the dump immediately after, so a changed status can be
        // seen one poll before the dump. Keep waiting for the dump; a status-only answer is still usable
        // evidence, but only as the fallback at the deadline.
        var statusOnly: FreshSnapshot? = null
        repeat(attempts) {
            val status = runCatching { readStatus() }.getOrNull()
            val dump = runCatching { readDump() }.getOrNull()
            val dumpAt = runCatching { readDumpAt() }.getOrDefault(0L)
            if (status != null && dump != null) {
                val dumpFresh = dumpAt > previous.dumpAt || dump != previous.dump
                if (dumpFresh) return FreshSnapshot(status, dump, dumpAt, refreshed = true)
                if (status != previous.status) {
                    statusOnly = FreshSnapshot(status, dump, dumpAt, refreshed = true)
                }
            }
            val time = runCatching { now() }.getOrDefault(deadline)
            if (time >= deadline) return statusOnly ?: previous
            runCatching { sleep(poll) }
        }
        return statusOnly ?: previous
    }

    /**
     * The Android half: broadcast the request to System UI, then [poll]. [alive] is exposed (and
     * defaults to the stamps) so a caller that already knows the module is gone does not ask at all.
     */
    fun refresh(
        context: Context,
        alive: Boolean = shouldRequest(
            runCatching { DuoPrefs.moduleLoadTime(context) }.getOrDefault(0L),
            runCatching { DuoPrefs.heartbeat(context) }.getOrDefault(0L),
            runCatching { DuoPrefs.status(context) }.getOrDefault("")
        ),
        now: () -> Long = { System.currentTimeMillis() },
        sleep: (Long) -> Unit = { ms -> Thread.sleep(ms) },
        timeoutMs: Long = TIMEOUT_MS,
        pollMs: Long = POLL_MS
    ): FreshSnapshot {
        val previous = stored(context)
        if (!alive) return previous
        return try {
            poll(
                previous = previous,
                request = {
                    context.sendBroadcast(
                        Intent(DuoPrefs.ACTION_DIAGNOSTICS_REQUEST)
                            .setPackage(RomDetection.AOSP)
                    )
                },
                readStatus = { DuoPrefs.status(context) },
                readDump = { DuoPrefs.dump(context) },
                readDumpAt = { DuoPrefs.dumpAt(context) },
                now = now,
                sleep = sleep,
                timeoutMs = timeoutMs,
                pollMs = pollMs
            )
        } catch (_: Throwable) {
            previous
        }
    }
}
