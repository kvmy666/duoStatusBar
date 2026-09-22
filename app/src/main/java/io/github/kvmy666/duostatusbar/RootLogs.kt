package io.github.kvmy666.duostatusbar

/**
 * Root-assisted diagnostics for a module that is not (yet) running.
 *
 * The in-module dump only exists once LSPosed has injected the module into SystemUI. When it has not — the
 * exact case a "the module does nothing" report describes — there is nothing on the app side to read, so
 * this shells out to `su` and pulls the evidence that decides why:
 *
 *  - logcat filtered to `DuoSB` (works on ROMs that keep SystemUI's logcat),
 *  - LSPosed's own module log, which is where `L` writes when the ROM filters logcat and is the only place
 *    that records "MainHook loaded into com.android.systemui" — present or absent, it answers the question,
 *  - the build props and the installed module version, to pin the device/ROM and prove which APK is on.
 *
 * Every call is best-effort: a missing `su`, a denied prompt, or a ROM that keeps none of these files yields
 * a line saying so, never an exception.
 */
internal object RootLogs {

    /**
     * Runs one `su -c` script (a single root prompt) and returns everything it printed. The compound script
     * is deliberate: one prompt for the whole capture is far less annoying than one per command.
     */
    fun collect(): String {
        val script = """
            echo '=== logcat (DuoSB) ==='
            logcat -d -t 3000 -s DuoSB 2>&1
            echo '=== LSPosed newest modules log ==='
            LOG=$(ls -t /data/adb/lspd/log/modules_*.log 2>/dev/null | head -1)
            echo "file=${'$'}LOG"
            if [ -n "${'$'}LOG" ]; then
              echo '-- matching DuoSB --'
              grep -a -i 'DuoSB\|duostatusbar' "${'$'}LOG" | tail -n 400
              echo '-- tail --'
              tail -n 120 "${'$'}LOG"
            fi
            echo '=== LSPosed newest verbose log ==='
            VLOG=$(ls -t /data/adb/lspd/log/verbose_*.log 2>/dev/null | head -1)
            echo "file=${'$'}VLOG"
            if [ -n "${'$'}VLOG" ]; then
              echo '-- matching DuoSB --'
              grep -a -i 'duostatusbar\|DuoSB' "${'$'}VLOG" | tail -n 400
              echo '-- tail --'
              tail -n 150 "${'$'}VLOG"
            fi
            echo '=== LSPosed config (enabled / scope) ==='
            tr -c '[:print:]' '\n' < /data/adb/lspd/config/modules_config.db 2>/dev/null | grep -a -i -B3 -A8 duostatusbar
            echo '=== magisk / lsposed modules ==='
            ls -1 /data/adb/modules 2>/dev/null
            for m in /data/adb/modules/*/module.prop; do echo "--- ${'$'}m"; cat "${'$'}m" 2>/dev/null; done
            echo '=== installed module package ==='
            pm path io.github.kvmy666.duostatusbar 2>&1
            dumpsys package io.github.kvmy666.duostatusbar 2>/dev/null | head -n 40
            echo '=== build props ==='
            getprop ro.product.manufacturer
            getprop ro.product.brand
            getprop ro.product.model
            getprop ro.build.version.sdk
            getprop ro.build.version.release
            getprop ro.build.display.id
            echo '=== root framework ==='
            ls /data/adb 2>/dev/null
            ls /data/adb/lspd/log 2>/dev/null
        """.trimIndent()
        return runSu(script)
    }

    /**
     * Restarts SystemUI through root. Used only when the module is not running: the module's own path (kill
     * its process from inside SystemUI) needs no root, so this is the fallback that still works before
     * LSPosed has injected anything — and it is the root prompt users of other modules expect.
     */
    fun restartSystemUi(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f com.android.systemui")).waitFor()
        true
    } catch (t: Throwable) {
        L.w("root restart failed: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private fun runSu(script: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        process.waitFor()
        buildString {
            if (out.isNotBlank()) append(out)
            if (err.isNotBlank()) append("\n[stderr]\n").append(err)
            if (isBlank()) append("(su returned no output - root denied or no su binary)")
        }
    } catch (t: Throwable) {
        "root log collection failed: ${t.javaClass.simpleName}: ${t.message}"
    }
}
