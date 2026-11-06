package io.github.kvmy666.duostatusbar.hook

import android.os.FileObserver
import io.github.kvmy666.duostatusbar.L
import java.io.File

/**
 * Cross-UI screenshot source: watches the public screenshot folders instead of one vendor's executor.
 *
 * This is deliberately a fallback, not the only source. It is the shared path for ROMs that do not call
 * `com.android.systemui.screenshot.TakeScreenshotExecutorImpl` (notably OxygenOS' external
 * `com.oplus.screenshot` process). It has no vendor class names, so it survives ROM changes.
 */
internal class ScreenshotWatcher(private val onScreenshot: (String) -> Unit) {
    private val observers = mutableListOf<FileObserver>()
    private var lastEventAt = 0L

    fun start() {
        stop()
        val roots = listOf(
            "/storage/emulated/0/Pictures/Screenshots",
            "/storage/emulated/0/DCIM/Screenshots"
        ).distinct()
        for (path in roots) {
            val dir = File(path)
            if (!dir.isDirectory) {
                L.i("screenshot watcher skip missing dir=$path")
                continue
            }
            try {
                val observer = object : FileObserver(
                    dir.absolutePath,
                    CREATE or CLOSE_WRITE or MOVED_TO
                ) {
                    override fun onEvent(event: Int, name: String?) {
                        val file = name ?: return
                        if (!file.endsWith(".png", true) &&
                            !file.endsWith(".jpg", true) &&
                            !file.endsWith(".jpeg", true) &&
                            !file.endsWith(".webp", true)
                        ) {
                            return
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastEventAt < 1_500L) return
                        lastEventAt = now
                        L.i("screenshot file -> $path")
                        onScreenshot("file:$path")
                    }
                }
                observer.startWatching()
                observers.add(observer)
                L.i("screenshot watcher -> $path")
            } catch (t: Throwable) {
                L.w("screenshot watcher failed dir=$path: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        if (observers.isEmpty()) L.w("screenshot watcher: no readable screenshot directory")
    }

    fun stop() {
        for (observer in observers) {
            runCatching { observer.stopWatching() }
        }
        observers.clear()
    }
}