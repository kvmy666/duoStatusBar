package io.github.kvmy666.duostatusbar

import android.content.Context
import android.provider.Settings

/**
 * A breadcrumb sink. Injectable so the once-per-step rule and the eight-line ring can be tested in a
 * plain JVM, with no Android Settings.
 */
internal interface BreadcrumbSink {
    fun putGlobal(value: String)
}

/**
 * The persisted "last step the module completed".
 *
 * After a native death no in-process reporter survives, so the next report has to read the last step
 * from storage. One readable sink is written on every step **change** (never on a hot path):
 * `Settings.Global` key `duo_statusbar_breadcrumb`, readable by the app and by a shell without root.
 * The in-process ring keeps the last eight values, so a report assembled before the process dies can
 * show the final sequence, not only the last step.
 *
 * The second sink was a file in SystemUI's data dir, and it was measured undeliverable: the module
 * (running as SystemUI's uid) can write it, but neither root nor Shizuku can read it back. Root runs
 * as `u:r:ksu:s0` with SELinux enforcing and `ls /data/user_de/0/com.android.systemui` is denied
 * outright (see `hook/DuoGuard.kt`), so the reporter's `cat` never saw a ring. A file ring nobody can
 * read is not a breadcrumb, so it was dropped; `Settings.Global` is the only readable sink.
 *
 * The value is the in-process ring itself: up to eight `ts|process=<p>|pid=<pid>|step=<name>` lines,
 * newline-separated with the newest last, so a report that reads only `Settings.Global` still shows
 * the sequence that led to the last step. A failed write never stops the next step and nothing here
 * ever throws: a breadcrumb that kills the process it is meant to diagnose is useless.
 */
internal object Breadcrumb {

    const val KEY = "duo_statusbar_breadcrumb"

    /** How many steps the in-process ring keeps. Small on purpose: it is a crash sequence, not a log. */
    private const val MAX_LINES = 8

    private val lock = Any()
    private var lastStep: String? = null
    private val ring = ArrayDeque<String>(MAX_LINES)
    private var pending = false

    @Volatile
    private var sink: BreadcrumbSink? = null

    /** Binds the real sink. Safe to call more than once; the first real sink wins. */
    fun bind(context: Context) {
        try {
            if (sink != null) return
            val real = RealSink(context.applicationContext ?: context)
            synchronized(lock) {
                if (sink != null) return
                sink = real
            }
            flush()
        } catch (_: Throwable) {
        }
    }

    /** Records [name]; writes only when it differs from the last step. Never throws. */
    fun step(name: String) {
        try {
            synchronized(lock) {
                if (name == lastStep) return
                lastStep = name
                val value = "${System.currentTimeMillis()}|process=${ProcessIdentity.processName()}" +
                    "|pid=${ProcessIdentity.pid()}|step=$name"
                ring.addLast(value)
                while (ring.size > MAX_LINES) ring.removeFirst()
                pending = true
            }
            flush()
        } catch (_: Throwable) {
        }
    }

    /** The last breadcrumb written to `Settings.Global` (the multi-line ring), or null when unreadable. */
    fun last(context: Context): String? = try {
        Settings.Global.getString(context.contentResolver, KEY)
    } catch (_: Throwable) {
        null
    }

    private fun flush() {
        try {
            val target: BreadcrumbSink
            val value: String
            synchronized(lock) {
                target = sink ?: return
                if (!pending) return
                if (ring.isEmpty()) return
                value = ring.joinToString("\n")
            }
            try {
                target.putGlobal(value)
            } catch (_: Throwable) {
            }
            synchronized(lock) { pending = false }
        } catch (_: Throwable) {
        }
    }

    /** The in-process ring, for the JVM tests. The ring is otherwise private to this object. */
    internal fun linesForTest(): List<String> = synchronized(lock) { ring.toList() }

    internal fun installSinkForTest(sink: BreadcrumbSink?) {
        synchronized(lock) {
            this.sink = sink
            lastStep = null
            ring.clear()
            pending = false
        }
    }

    internal fun resetForTest() {
        synchronized(lock) {
            sink = null
            lastStep = null
            ring.clear()
            pending = false
        }
    }

    private class RealSink(private val context: Context) : BreadcrumbSink {

        override fun putGlobal(value: String) {
            try {
                Settings.Global.putString(context.contentResolver, KEY, value)
            } catch (_: Throwable) {
            }
        }
    }
}
