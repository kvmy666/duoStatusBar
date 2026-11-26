package io.github.kvmy666.duostatusbar

import android.app.Application
import android.os.Process

/**
 * The identity of the process the module is running in.
 *
 * It is read once and cached: the process name and pid cannot change during a process's life, and
 * every log line asks for it. [Application.getProcessName] is API 28+ (the module's minSdk is 33);
 * it is still guarded because a ROM or a unit-test JVM can throw, and logging must never be the
 * reason a process dies.
 *
 * This object deliberately never calls [L]: [L] calls it to build its line prefix, so any logging here
 * would recurse. Tests may force the identity; [resetForTest] removes that and the real cache.
 */
internal object ProcessIdentity {

    private val lock = Any()

    private var nameResolved = false
    private var name: String? = null
    private var pidResolved = false
    private var pid = -1

    @Volatile
    private var forced = false

    @Volatile
    private var forcedName: String? = null

    @Volatile
    private var forcedPid: Int? = null

    /**
     * Set by [MainHook] on the class-loader load that actually installs. `MainHook` may have admitted
     * that load through the package fallback while the runtime cannot name the process, so the writer
     * gate must accept the same process instead of refusing every status/dump write.
     */
    @Volatile
    private var mainProcess = false

    /** The process name, or `unknown` when the runtime cannot answer. Never throws, never logs. */
    fun processName(): String {
        if (forced) return forcedName ?: "unknown"
        synchronized(lock) {
            if (!nameResolved) {
                name = try {
                    Application.getProcessName()
                } catch (_: Throwable) {
                    null
                }
                nameResolved = true
            }
        }
        return name ?: "unknown"
    }

    /** The pid, or `-1` when the runtime cannot answer. Never throws, never logs. */
    fun pid(): Int {
        if (forced) return forcedPid ?: -1
        synchronized(lock) {
            if (!pidResolved) {
                pid = try {
                    Process.myPid()
                } catch (_: Throwable) {
                    -1
                }
                pidResolved = true
            }
        }
        return pid
    }

    /**
     * True only in the main SystemUI process. The process name is the identity that survives the
     * HyperOS `packageName = system` report and the owner's `com.oplus.keyguard.*` class-loaders;
     * any `com.android.systemui:<suffix>` is a side process and is false.
     */
    fun isMainSystemUi(): Boolean = mainProcess || processName() == SystemUiProcess.PACKAGE

    /** Marks the process MainHook actually installed in as the main SystemUI process. */
    fun markMainProcess() {
        mainProcess = true
    }

    /** The prefix every module log line starts with. */
    fun prefix(): String = "process=${processName()} pid=${pid()} "

    internal fun forceForTest(name: String?, pid: Int) {
        forcedName = name
        forcedPid = pid
        forced = true
    }

    internal fun resetForTest() {
        forced = false
        forcedName = null
        forcedPid = null
        mainProcess = false
        synchronized(lock) {
            nameResolved = false
            name = null
            pidResolved = false
            pid = -1
        }
    }
}
