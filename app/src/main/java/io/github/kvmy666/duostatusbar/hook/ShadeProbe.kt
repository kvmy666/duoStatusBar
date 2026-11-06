package io.github.kvmy666.duostatusbar.hook

import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L

/**
 * Temporary, read-only probe for the shade/QS header (Phase 1, RC-003).
 *
 * It only reads the view tree and logs; it never changes selection, visibility or layout. Every line
 * starts with [PREFIX] so a single log capture can be grepped. [ENABLED] follows the build type, so a
 * release keeps the retry timer quiet while a debug build still has the probe for a device test; the
 * whole file is removed once the header host is resolved.
 */
internal object ShadeProbe {

    /** Master switch for every probe line: on in debug builds, off in release. */
    val ENABLED: Boolean = BuildConfig.DEBUG

    private const val PREFIX = "DuoSB | shadeProbe"
    private const val MAX_TREE_NODES = 40

    private val once = LogOnce()

    fun log(message: String) {
        if (!ENABLED) return
        L.i("$PREFIX $message")
    }

    /** Logs [message] only the first time [key] is used. */
    fun logOnce(key: String, message: String) {
        if (!ENABLED) return
        once.once(key) { log(message) }
    }

    /**
     * One line for a view: class, id, bounds (window coordinates) and visibility. Deliberately no
     * text content, so no notification or carrier text can reach the log.
     */
    fun describe(view: View?): String {
        view ?: return "none"
        val location = IntArray(2)
        val bounds = try {
            view.getLocationInWindow(location)
            "[${location[0]},${location[1]}][${location[0] + view.width},${location[1] + view.height}]"
        } catch (_: Throwable) {
            "?"
        }
        return "${view.javaClass.name} id=${idName(view)} ${view.width}x${view.height} " +
            "bounds=$bounds vis=${view.visibility}"
    }

    /** A one-shot key for a view type, so a repeatedly inflated root logs once. */
    fun key(view: View): String = "${view.javaClass.name}#${idName(view)}"

    /**
     * Bounded dump (at most [MAX_TREE_NODES] nodes, depth-first): class, id, bounds and visibility only.
     * Never the whole window - enough to see where the icon cluster and the battery actually sit.
     */
    fun tree(name: String, root: View?) {
        if (!ENABLED) return
        val builder = StringBuilder("$PREFIX tree $name (max $MAX_TREE_NODES nodes)")
        var count = 0
        fun walk(view: View, depth: Int) {
            if (count >= MAX_TREE_NODES) return
            count++
            builder.append('\n').append("  ".repeat(depth)).append(describe(view))
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    if (count >= MAX_TREE_NODES) break
                    walk(view.getChildAt(i), depth + 1)
                }
            }
        }
        if (root == null) {
            builder.append("\n  (not captured)")
        } else {
            walk(root, 0)
        }
        if (root != null && count >= MAX_TREE_NODES) {
            builder.append("\n  (truncated at $MAX_TREE_NODES nodes)")
        }
        for (line in builder.lines()) L.i(line)
    }

    private fun idName(view: View): String {
        if (view.id == View.NO_ID) return "-"
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            "0x${view.id.toString(16)}"
        }
    }
}
