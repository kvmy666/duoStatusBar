package io.github.kvmy666.duostatusbar.hook

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kvmy666.duostatusbar.L

private const val SIMPLE_HEADER = "com.oplus.systemui.separate.OplusQSSimpleHeader"
private const val SPLIT_HEADER =
    "com.oplus.systemui.qs.impl.OplusSplitShadeHeaderControllerExImpl"
private const val SIMPLE_SOURCE = "OplusQSSimpleHeader"
private const val SPLIT_SOURCE = "OplusSplitShade"

/**
 * OxygenOS/OnePlus shade hooks.
 *
 * On the measured CPH2747 the visible QS icon cluster is not the one `ShadeHeaderController` exposes:
 * `OplusQSSimpleHeader` finds it in `onInit()` as `qsStatusIconsContainer`, and
 * `OplusSplitShadeHeaderControllerExImpl.init(ViewGroup)` finds the split header's copy as
 * `qsStatusIcon` with the battery beside it as `batteryIcon`. Both are handed to
 * [DuoIconHost.attachShadeCluster] directly; `DuoHook.hookShadeHeader` stays in place as the fallback
 * for every other ROM.
 *
 * Everything is reflective and fail-silent: a class, method, field or getter missing on a particular
 * build logs once and skips that hook. Nothing here throws into SystemUI.
 */
internal class OnePlusShadeHooks(
    private val lp: XC_LoadPackage.LoadPackageParam,
    private val host: () -> DuoIconHost?
) {
    private val logOnce = LogOnce()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Installs both vendor hooks; each is independently optional. */
    fun install() {
        hookSimpleHeader()
        hookSplitHeader()
    }

    private fun hookSimpleHeader() {
        L.guard("OnePlus shade simple header") {
            val cls = findClass(SIMPLE_HEADER) ?: return@guard
            try {
                XposedHelpers.findAndHookMethod(cls, "onInit", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        L.guard("OnePlus shade simple header onInit") {
                            val owner = param.thisObject ?: return@guard
                            // Measured: field `qsStatusIconsContainer`, getter `getStatusIconsContainer`.
                            val cluster = read(
                                owner,
                                "qsStatusIconsContainer",
                                "getStatusIconsContainer"
                            ) as? ViewGroup
                            if (cluster == null) {
                                logOnce.once("missing:$SIMPLE_HEADER:cluster") {
                                    L.w("$SIMPLE_SOURCE: qsStatusIconsContainer missing - header left untouched")
                                }
                                return@guard
                            }
                            attach(cluster, null, SIMPLE_SOURCE)
                        }
                    }
                })
                L.i("$SIMPLE_SOURCE hook installed: $SIMPLE_HEADER.onInit()")
            } catch (t: Throwable) {
                L.w("$SIMPLE_SOURCE.onInit not hooked: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun hookSplitHeader() {
        L.guard("OnePlus shade split header") {
            val cls = findClass(SPLIT_HEADER) ?: return@guard
            try {
                XposedHelpers.findAndHookMethod(
                    cls, "init", ViewGroup::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            L.guard("OnePlus shade split header init") {
                                val owner = param.thisObject ?: return@guard
                                val cluster = read(owner, "qsStatusIcon") as? ViewGroup
                                if (cluster == null) {
                                    logOnce.once("missing:$SPLIT_HEADER:cluster") {
                                        L.w("$SPLIT_SOURCE: qsStatusIcon missing - split header left untouched")
                                    }
                                    return@guard
                                }
                                // The split controller keeps the battery beside the cluster, so it is
                                // handed over explicitly instead of searched for inside the cluster.
                                val battery = read(owner, "batteryIcon") as? View
                                attach(cluster, battery, SPLIT_SOURCE)
                            }
                        }
                    }
                )
                L.i("$SPLIT_SOURCE hook installed: $SPLIT_HEADER.init(ViewGroup)")
            } catch (t: Throwable) {
                L.w("$SPLIT_SOURCE.init not hooked: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * Hands a resolved cluster to the host, on the main thread. The vendor callback can run on a
     * worker, and touching the SystemUI view tree from there is not safe, so the attach is posted.
     * A missing host is logged once and left to the fallback, on either path.
     */
    private fun attach(cluster: ViewGroup, battery: View?, source: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                L.guard("$source attach (posted)") { attachOnMainThread(cluster, battery, source) }
            }
            return
        }
        attachOnMainThread(cluster, battery, source)
    }

    private fun attachOnMainThread(cluster: ViewGroup, battery: View?, source: String) {
        val target = host()
        if (target == null) {
            logOnce.once("host:$source") {
                L.i("$source: host not ready - waiting for the layout fallback")
            }
            return
        }
        target.attachShadeCluster(cluster, battery, source)
    }

    /** Resolves a vendor class, or logs once and returns null when this build does not have it. */
    private fun findClass(name: String): Class<*>? = try {
        XposedHelpers.findClass(name, lp.classLoader)
    } catch (t: Throwable) {
        logOnce.once("missing:$name") {
            L.i("$name not present (${t.javaClass.simpleName}) - skipping OnePlus shade hook")
        }
        null
    }

    /**
     * Reads [field] or, when the field is absent or null, the getter [getter] (by default derived from
     * [field]). Every lookup is optional; a missing member returns null instead of throwing.
     */
    private fun read(owner: Any, field: String, getter: String? = null): Any? {
        val direct = try {
            XposedHelpers.getObjectField(owner, field)
        } catch (t: Throwable) {
            logReadFailure(owner, field, t)
            null
        }
        if (direct != null) return direct
        val member = getter ?: getterFor(field)
        return try {
            XposedHelpers.callMethod(owner, member)
        } catch (t: Throwable) {
            logReadFailure(owner, member, t)
            null
        }
    }

    /** Logs the member that could not be read once, without values, and never lets reflection throw. */
    private fun logReadFailure(owner: Any, member: String, t: Throwable) {
        val ownerName = owner.javaClass.name
        logOnce.once("read:$ownerName:$member") {
            L.w("shade read $ownerName.$member failed (${t.javaClass.simpleName})")
        }
    }
}

/** `qsStatusIcon` -> `getQsStatusIcon`; pinned by a unit test because the OEM spelling is unknown. */
internal fun getterFor(field: String): String = "get" + field.replaceFirstChar { it.uppercaseChar() }
