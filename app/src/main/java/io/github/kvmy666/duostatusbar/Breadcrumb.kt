package io.github.kvmy666.duostatusbar

import android.content.Context
import android.provider.Settings
import java.io.File

/**
 * A breadcrumb sink. Injectable so the once-per-step rule and the eight-line ring can be tested in a
 * plain JVM, with no Android Settings and no SystemUI data directory.
 */
internal interface BreadcrumbSink {
    fun putGlobal(value: String)
    fun writeFile(lines: List<String>)
    fun readFile(): List<String>
}

/**
 * The persisted "last step the module completed".
 *
 * After a native death no in-process reporter survives, so the next report has to read the last step
 * from storage. Two sinks are written on every step **change** (never on a hot path):
 *  - `Settings.Global` key `duo_statusbar_breadcrumb`, readable by the app without root;
 *  - a file in SystemUI's data dir, which the reporter reads with root/Shizuku and keeps as the last
 *    eight lines (a ring), so one report shows the final sequence, not only the last value.
 *
 * The value is `ts|process=<p>|pid=<pid>|step=<name>`. A failed sink never stops the other one, and
 * nothing here ever throws: a breadcrumb that kills the process it is meant to diagnose is useless.
 */
internal object Breadcrumb {

    const val KEY = "duo_statusbar_breadcrumb"
    const val FILE_NAME = "duo-breadcrumb.txt"

    /** How many steps the file keeps. Small on purpose: it is a crash sequence, not a log. */
    private const val MAX_LINES = 8

    private val lock = Any()
    private var lastStep: String? = null
    private val ring = ArrayDeque<String>(MAX_LINES)
    private var pending = false

    @Volatile
    private var sink: BreadcrumbSink? = null

    /** Binds the real sinks. Safe to call more than once; the first real sink wins. */
    fun bind(context: Context) {
        try {
            if (sink != null) return
            val real = RealSink(context.applicationContext ?: context)
            synchronized(lock) {
                if (sink != null) return
                // Keep the previous run's tail when this process has not written a step yet.
                if (ring.isEmpty()) {
                    try {
                        real.readFile().takeLast(MAX_LINES).forEach { ring.addLast(it) }
                    } catch (_: Throwable) {
                    }
                }
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

    /** The last breadcrumb written to `Settings.Global`, or null when there is none / unreadable. */
    fun last(context: Context): String? = try {
        Settings.Global.getString(context.contentResolver, KEY)
    } catch (_: Throwable) {
        null
    }

    private fun flush() {
        try {
            val target: BreadcrumbSink
            val latest: String
            val lines: List<String>
            synchronized(lock) {
                target = sink ?: return
                if (!pending) return
                latest = ring.lastOrNull() ?: return
                lines = ring.toList()
            }
            try {
                target.putGlobal(latest)
            } catch (_: Throwable) {
            }
            try {
                target.writeFile(lines)
            } catch (_: Throwable) {
            }
            synchronized(lock) { pending = false }
        } catch (_: Throwable) {
        }
    }

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

        override fun writeFile(lines: List<String>) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                file.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"))
            } catch (_: Throwable) {
            }
        }

        override fun readFile(): List<String> = try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
