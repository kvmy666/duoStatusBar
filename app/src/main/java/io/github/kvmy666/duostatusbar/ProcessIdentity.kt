package io.github.kvmy666.duostatusbar

/**
 * The identity of the process the module is running in.
 *
 * RED STUB: the real implementation lands with the process-gate fix. It deliberately exposes the
 * final API only so the failing tests can compile and report their assertions.
 */
internal object ProcessIdentity {

    fun processName(): String = "unknown"

    fun pid(): Int = -1

    fun isMainSystemUi(): Boolean = true

    fun prefix(): String = ""

    internal fun forceForTest(name: String?, pid: Int) {
    }

    internal fun resetForTest() {
    }
}
