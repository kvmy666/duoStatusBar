package io.github.kvmy666.duostatusbar.settings

import kotlin.system.exitProcess

/**
 * Issue #4: the process Shizuku starts for us, running with shell/root privilege.
 *
 * It exists for one narrow reason: to run the `settings` command that hides the stock status-bar icons
 * through the secure `icon_blacklist`. It never touches anything else, and it lives only as long as the
 * settings app does (`daemon(false)` in [StockIconHider]).
 *
 * The class name is reached reflectively by the Shizuku server, so it is kept by `proguard-rules.pro`.
 */
class ShellService : IShellService.Stub() {

    /** Reserved; the Shizuku server calls this when the service is stopped. */
    override fun destroy() {
        exitProcess(0)
    }

    /** Runs [command] with shell/root privilege and returns its combined output. */
    override fun exec(command: String): String = try {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output
    } catch (t: Throwable) {
        "ERROR: ${t.javaClass.simpleName}: ${t.message}"
    }
}
