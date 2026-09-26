package io.github.kvmy666.duostatusbar.settings

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.kvmy666.duostatusbar.L
import rikka.shizuku.Shizuku

/**
 * Issue #4: the optional Shizuku path for hiding the stock status-bar icons.
 *
 * The LSPosed module already hides the icons by taking their views out of the status bar. On a ROM where
 * that leaves them behind, the same icons can be dropped at the SystemUI level by adding them to the
 * secure `icon_blacklist` setting. A normal app cannot write a secure setting, so the write is done by a
 * [ShellService] that Shizuku runs as shell (adb) or root.
 *
 * This is deliberately best-effort: with no Shizuku, no permission, or any failure on the way, every
 * entry point reports `false` and the module's own behaviour is untouched. Nothing here is required for
 * the LSPosed path, which is why a device without Shizuku behaves exactly as before.
 */
internal object StockIconHider {

    /** Ties the permission callback to this app; the UI listens only for this request code. */
    const val REQUEST_CODE = 0x5A17

    private const val SETTING = "icon_blacklist"
    private val main = Handler(Looper.getMainLooper())

    private var service: IShellService? = null
    private var binding = false

    /** The most recent request, so a call made while the service is still connecting is not lost. */
    private var waitingHide: Boolean? = null
    private var waitingDone: ((Boolean) -> Unit)? = null

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var listening = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            permissionCallback?.invoke(result == PackageManager.PERMISSION_GRANTED)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShellService.Stub.asInterface(binder)
            binding = false
            runWaiting()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
        }
    }

    /** Whether the Shizuku service is currently reachable. */
    fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** Whether the user has granted this app access in Shizuku. */
    fun isPermissionGranted(): Boolean = try {
        isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (isShizukuRunning()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            L.w("shizuku permission request: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Called by the UI to be told when the user answers the Shizuku permission dialog. */
    fun observePermissionResult(callback: (Boolean) -> Unit) {
        permissionCallback = callback
        if (listening) return
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            listening = true
        } catch (t: Throwable) {
            L.w("shizuku permission listener: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun stopObservingPermissionResult() {
        permissionCallback = null
        if (!listening) return
        listening = false
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
            L.w("shizuku permission listener remove: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Adds ([hide] = true) or removes ([hide] = false) this module's slots in the blacklist.
     * [done] is called on the main thread with whether the setting now holds the requested state.
     */
    fun apply(context: Context, hide: Boolean, done: (Boolean) -> Unit) {
        if (!isShizukuRunning() || !isPermissionGranted()) {
            done(false)
            return
        }
        waitingHide = hide
        waitingDone = done
        if (service != null) {
            runWaiting()
            return
        }
        if (binding) return
        binding = true
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShellService::class.java.name)
            )
                .daemon(false)
                .version(1)
                .processNameSuffix("duosb")
                .tag("duosb-shell")
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            binding = false
            L.w("shizuku user service bind: ${t.javaClass.simpleName}: ${t.message}")
            flush(false)
        }
    }

    private fun runWaiting() {
        val svc = service ?: return
        val hide = waitingHide ?: return
        val done = waitingDone
        waitingHide = null
        waitingDone = null
        // The command runs a shell process and several binder calls, so it must not run on the UI thread.
        Thread {
            val ok = writeBlacklist(svc, hide)
            main.post { done?.invoke(ok) }
        }.start()
    }

    private fun flush(ok: Boolean) {
        val done = waitingDone
        waitingHide = null
        waitingDone = null
        main.post { done?.invoke(ok) }
    }

    /**
     * Reads the current blacklist, adds or removes our slots, writes it back and reads it once more to
     * confirm. Reading back is the success signal: the `settings` command reports failures on stdout,
     * so its exit status alone would not be enough.
     */
    private fun writeBlacklist(svc: IShellService, hide: Boolean): Boolean = try {
        val before = svc.exec("settings get secure $SETTING")
        val current = IconBlacklist.parse(before).joinToString(",")
        val next = if (hide) IconBlacklist.withKeys(current) else IconBlacklist.withoutKeys(current)
        if (next.isEmpty()) {
            svc.exec("settings delete secure $SETTING")
        } else {
            svc.exec("settings put secure $SETTING '$next'")
        }
        val after = IconBlacklist.parse(svc.exec("settings get secure $SETTING"))
        val raw = after.joinToString(",")
        val ok = if (hide) IconBlacklist.hasKeys(raw) else IconBlacklist.KEYS.none { it in after }
        L.i("shizuku icon blacklist ${if (hide) "hide" else "show"}: '${before.trim()}' -> '$raw' (ok=$ok)")
        ok
    } catch (t: Throwable) {
        L.w("shizuku blacklist write: ${t.javaClass.simpleName}: ${t.message}")
        false
    }
}
