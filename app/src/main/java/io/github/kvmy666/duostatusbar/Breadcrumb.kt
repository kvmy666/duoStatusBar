package io.github.kvmy666.duostatusbar

import android.content.Context

/** A breadcrumb sink, injectable so the ring and the once-per-step rule are testable off Android. */
internal interface BreadcrumbSink {
    fun putGlobal(value: String)
    fun writeFile(lines: List<String>)
    fun readFile(): List<String>
}

/**
 * RED STUB: the persisted last-step breadcrumb lands with the process-gate fix. The API is final so
 * the failing tests can report their assertions before the implementation exists.
 */
internal object Breadcrumb {

    fun bind(context: Context) {
    }

    fun step(name: String) {
    }

    fun last(context: Context): String? = null

    internal fun installSinkForTest(sink: BreadcrumbSink?) {
    }

    internal fun resetForTest() {
    }
}
