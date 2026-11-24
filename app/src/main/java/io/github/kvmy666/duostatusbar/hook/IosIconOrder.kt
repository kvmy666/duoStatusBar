package io.github.kvmy666.duostatusbar.hook

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * R3 A3: the iOS icon order (`mobile -> wifi -> battery`) applied to the live status bar.
 *
 * The stock OxygenOS/HyperOS order is `wifi -> mobile -> battery`. The system lays out a
 * `StatusIconContainer` by child index and addresses each child through `StatusBarIconList.mSlots`
 * plus the earlier-holder count, so moving only the child views would desynchronise the slot list
 * from the views and the next icon refresh would put them back. This object therefore keeps the two
 * in step:
 *
 *  1. capture every `StatusBarIconList.mSlots` instance and snapshot its stock order,
 *  2. on [activate], move the same Wi-Fi view after the last `mobile` child of every registered
 *     `StatusIconContainer`, then permute `mSlots` in place (one main-thread call),
 *  3. on [restore], move Wi-Fi before the first `mobile` child, then put the captured slot order back
 *     (keeping any slot the system created after the snapshot).
 *
 * The order is owned by the process, not by a surface: tearing down the QS header while iOS stays on
 * must not restore the stock order. Only [restore] (leaving iOS, or the module being turned off) does.
 *
 * Every step is optional and guarded. If the slot list or container class cannot be resolved, the
 * stock order is left untouched and the reason is logged once. Nothing here guesses a class or an id:
 * the names are the measured ones from the device's SystemUI.
 *
 * A container is seen by a constructor, the existing `addView` hook, or any `onLayout`. `onLayout`
 * only registers/adopts and posts at most one coalesced main-thread pass; a live child is never
 * removed or reparented from a layout callback (AGENTS-V2 3.3 #14). [activate], [onIconAdded] and
 * [restore] stay synchronous on the main thread: they move every container before they permute or
 * restore the slots, and never post a move. Only an off-main [activate]/[restore] is posted.
 */
internal object IosIconOrder {

    private const val WIFI = "wifi"
    private const val MOBILE = "mobile"

    /** Slot-list classes, measured on OxygenOS 16/17 and HyperOS 3. */
    private val SLOT_LIST_CLASSES = listOf(
        "com.android.systemui.statusbar.phone.ui.StatusBarIconList",
        "com.android.systemui.statusbar.phone.StatusBarIconList"
    )

    /** The one container every measured home/keyguard/QS/shade surface uses. */
    private val CONTAINER_CLASSES = listOf(
        "com.android.systemui.statusbar.phone.StatusIconContainer",
        "com.android.systemui.statusbar.views.MiuiStatusIconContainer"
    )

    /** The last-resort guard against a slot index pointing at a non-icon child. */
    private val ICON_MANAGER_CLASS = "com.android.systemui.statusbar.phone.IconManager"
    private const val ICON_MANAGER_METHOD = "onSetIcon"

    private val installed = AtomicBoolean(false)
    private val active = AtomicBoolean(false)

    /** Re-entrancy guard: our own `addView` is seen by the DuoHook icon hook. */
    private val applying = AtomicBoolean(false)

    private val logOnce = LogOnce()

    /** Every hook this session installed, so [release] can unhook all of them (C). */
    private val unhooks = ArrayList<XC_MethodHook.Unhook>()

    /** Every live container seen by a constructor, `onLayout` or the existing `addView` hook. */
    private val containers: MutableSet<ViewGroup> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))

    /**
     * Containers whose first `onLayout` has been adopted. A missing entry means the constructor or
     * the `addView` hook saw the container but no layout has run yet; an entry means every later
     * layout is a single lookup and returns without scheduling.
     */
    private val layoutAdopted: MutableSet<ViewGroup> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))

    /**
     * Containers with a layout-triggered pass already posted. Repeated layouts coalesce into that
     * one queued pass instead of moving a live child from inside `onLayout`.
     */
    private val pending: MutableSet<ViewGroup> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))

    /** Diagnostics: child move passes that actually moved Wi-Fi (session total). */
    private val moveCount = AtomicInteger(0)

    /** Diagnostics: `movePass` passes that ran (session total). */
    private val passCount = AtomicInteger(0)

    /** REL210: measured slot-list/container classes and whether they resolved. */
    private val classFacts: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())

    /** REL210: "ok:…/missing:…" for the order hook classes. */
    internal val hookClassesForDiagnostics: String
        get() = synchronized(classFacts) { classFacts.joinToString(",") }.ifBlank { "none" }

    private fun noteClass(name: String, resolved: Boolean) {
        classFacts.add((if (resolved) "ok:" else "missing:") + name)
    }


    /**
     * Test-only stand-in for a registered container, so the pure slot-list tests can exercise
     * `activate()` without an Android view tree. Production never sets this.
     */
    private var syntheticSurfaceForTest = false

    private val slotLists = ArrayList<SlotListSnapshot>()

    /** Reset on every activation, so one log line describes the state iOS asked for. */
    private var loggedActive = false

    private data class SlotListSnapshot(
        val list: MutableList<Any>,
        val stock: List<Any>
    )

    /** True while iOS owns the bar. Read only; [activate]/[restore] are the writers. */
    val isActive: Boolean get() = active.get()

    /**
     * Diagnostics: `"slot-list"` only when at least one captured list is actually permuted away from
     * its stock snapshot; `"stock"` otherwise.
     */
    internal val source: String
        get() {
            val permuted = active.get() && synchronized(slotLists) {
                slotLists.any { !sameOrder(it.list, it.stock) }
            }
            return if (permuted) "slot-list" else "stock"
        }

    /** Diagnostics: how many layout passes actually moved Wi-Fi this session. */
    internal val moves: Int get() = moveCount.get()

    /** Diagnostics: how many move passes ran this session. */
    internal val passes: Int get() = passCount.get()

    /**
     * Hooks the measured `StatusBarIconList` and `StatusIconContainer` classes once per process.
     *
     * Constructors capture new instances; a method hook on an instance method captures lists and
     * containers that already existed when the module was injected late. Both are optional: a class
     * that is absent is skipped, and [activate] then leaves the stock order alone.
     */
    fun install(classLoader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) return
        try {
            var listClasses = 0
            for (name in SLOT_LIST_CLASSES) {
                val cls = findClass(name, classLoader)
                noteClass(name.substringAfterLast('.'), cls != null)
                if (cls == null) continue
                listClasses++
                val capture = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        captureSlotList(param.thisObject)
                    }
                }
                hookSafely { XposedBridge.hookAllConstructors(cls, capture) }
                // Also catches a list that was built before the module was injected. The method's
                // body is never changed; this only reads the instance it was called on.
                hookSafely { XposedBridge.hookAllMethods(cls, "getViewIndex", capture) }
            }
            var containerClasses = 0
            for (name in CONTAINER_CLASSES) {
                val cls = findClass(name, classLoader)
                noteClass(name.substringAfterLast('.'), cls != null)
                if (cls == null) continue
                containerClasses++
                // Constructor: register only, never schedule.
                val seen = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? ViewGroup)?.let { onContainerSeen(it) }
                    }
                }
                // Existing containers are adopted on their FIRST layout pass; every later layout is
                // a weak-set lookup and returns without work.
                val laidOut = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? ViewGroup)?.let { onContainerLayout(it) }
                    }
                }
                hookSafely { XposedBridge.hookAllConstructors(cls, seen) }
                hookSafely { XposedBridge.hookAllMethods(cls, "onLayout", laidOut) }
            }
            // Last-resort guard (bug B): if the slot list and the children ever disagree, the next
            // IconManager.onSetIcon must not cast the Wi-Fi composite to StatusBarIconView.
            val iconManager = findClass(ICON_MANAGER_CLASS, classLoader)
            noteClass("IconManager", iconManager != null)
            if (iconManager != null) {
                val guard = object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        guardIconManagerCall(param)
                    }
                }
                hookSafely { XposedBridge.hookAllMethods(iconManager, ICON_MANAGER_METHOD, guard) }
            }
            if (listClasses == 0) {
                logOnce.once("ios-order-no-slot-class") {
                    L.w("iOS order: StatusBarIconList class not found - order stays stock")
                }
            }
            if (containerClasses == 0) {
                logOnce.once("ios-order-no-container-class") {
                    L.w("iOS order: StatusIconContainer class not found - order stays stock")
                }
            }
        } catch (t: Throwable) {
            logOnce.once("ios-order-install-failed") {
                L.w("iOS order: hook install failed (${t.javaClass.simpleName}: ${t.message})")
            }
        }
    }

    /**
     * iOS owns the bar: move the Wi-Fi child on every registered container first, then permute the
     * captured slot lists, in one main-thread call. Idempotent; safe to call on every settings
     * apply/attach. Moving and permuting across two frames is what let `IconManager.onSetIcon` see a
     * permuted index whose child was the OPLUS Wi-Fi composite (bug B).
     */
    fun activate() {
        if (!isMainThread()) {
            postToMain { activateOnMain() }
            return
        }
        activateOnMain()
    }

    private fun activateOnMain() {
        val wasActive = active.getAndSet(true)
        if (!wasActive) {
            loggedActive = false
            moveCount.set(0)
            passCount.set(0)
        }
        if (synchronized(slotLists) { slotLists.isEmpty() }) {
            // Without the slot list, moving the children alone would desynchronise the indices.
            logOnce.once("ios-order-no-slots") {
                L.w("iOS order: status icon slots not captured - order stays stock")
            }
            return
        }
        val needsPermute = slotPermutationNeeded()
        if (needsPermute) {
            val moved = moveAllForActivate()
            if (!moved.safe) {
                // Never leave a permuted slot list over stock children. Children were moved first, so
                // put them back before restoring the (still stock) slot order.
                moveAll(afterMobile = false)
                restoreSlotLists()
                if (!moved.surface) {
                    logOnce.once("ios-order-refused") {
                        L.w("iOS order: refused (no movable surface) - stock order")
                    }
                } else {
                    logOnce.once("ios-order-refused-failed") {
                        L.w("iOS order: refused (move failed) - stock order")
                    }
                }
                return
            }
            applySlotPermutation()
            logActiveIfNeeded()
        } else {
            // A repeated settings apply on an unchanged, already-adopted bar must move nothing; the
            // first activation still moves because the views may not have been moved yet. The one
            // exception is a container the ROM re-ordered without an addView: recover exactly those.
            if (!wasActive) moveAll(afterMobile = true) else moveMisordered()
            logActiveIfNeeded()
        }
    }

    /**
     * iOS no longer owns the bar: move Wi-Fi before the first mobile child, then put the captured
     * stock slot order back. Idempotent; a no-op when iOS was never active.
     */
    fun restore() {
        if (!isMainThread()) {
            postToMain { restoreOnMain() }
            return
        }
        restoreOnMain()
    }

    private fun restoreOnMain() {
        if (!active.getAndSet(false)) return
        // Move first, then restore the slots (the exact inverse of activateOnMain).
        moveAll(afterMobile = false)
        restoreSlotLists()
        loggedActive = false
        // Only one log per leave event; restore() returns above when there was nothing to restore.
        L.i("iOS order restored")
    }

    /**
     * Unhooks every class-level order hook and drops the captured state (C).
     *
     * Called by the iOS teardown after [restore]. A second call is safe and does nothing.
     */
    fun release() {
        if (!isMainThread()) {
            postToMain { releaseOnMain() }
            return
        }
        releaseOnMain()
    }

    @Synchronized
    private fun releaseOnMain() {
        // Stop new capture/registration before touching any state, then restore what this session
        // changed. A caller may release without a prior restore; never leave the stock order permuted.
        installed.set(false)
        restoreOnMain()
        active.set(false)
        applying.set(false)
        loggedActive = false
        syntheticSurfaceForTest = false
        synchronized(containers) { containers.clear() }
        synchronized(layoutAdopted) { layoutAdopted.clear() }
        synchronized(pending) { pending.clear() }
        synchronized(slotLists) { slotLists.clear() }
        moveCount.set(0)
        passCount.set(0)
        val snapshot = synchronized(unhooks) { unhooks.toList().also { unhooks.clear() } }
        for (unhook in snapshot) {
            try {
                unhook.unhook()
            } catch (t: Throwable) {
                IosV2Log.failureOnce("order.releaseUnhook", t)
            }
        }
    }

    /**
     * Called from the existing `StatusIconContainer.addView` hook for every child the ROM adds.
     * Registers the container and, while iOS owns the bar, moves it synchronously in this callback.
     */
    fun onIconAdded(container: ViewGroup) {
        // A6: iOS code does nothing while iOS is off. The Duo-only process keeps this a single
        // boolean read; the hooks are only installed from an iOS activation path.
        if (!installed.get()) return
        synchronized(containers) { containers.add(container) }
        // Our own remove/add during a reorder must not queue another pass.
        if (applying.get()) return
        if (!active.get() || synchronized(slotLists) { slotLists.isEmpty() }) return
        if (slotPermutationNeeded()) {
            // The first activation was refused with no surface (or the ROM re-stocked the slots);
            // this container is the movable surface that lets the whole swap run atomically.
            activateOnMain()
        } else {
            movePass(container, afterMobile = true)
            logActiveIfNeeded()
        }
    }

    /** Constructor path: remember the container for activate(); never moves. */
    fun onContainerSeen(container: ViewGroup) {
        if (!installed.get()) return
        synchronized(containers) { containers.add(container) }
    }

    /**
     * `StatusIconContainer.onLayout` path: register/adopt only. A live child is never removed or
     * reparented from a layout callback (AGENTS-V2 3.3 #14); when a pass is needed it is posted to
     * the main thread, coalesced per container, and the move runs after layout has returned.
     */
    fun onContainerLayout(container: ViewGroup) {
        if (!installed.get()) return
        if (layoutAdopted.add(container)) {
            synchronized(containers) { containers.add(container) }
        }
        if (applying.get()) return
        if (!active.get() || synchronized(slotLists) { slotLists.isEmpty() }) return
        if (!slotPermutationNeeded() && !needsMove(container, afterMobile = true)) return
        scheduleLayoutPass(container)
    }

    /**
     * Queues at most one main-thread pass per container. The runnable re-checks the state after
     * layout: a first activation and an already-permuted misordered bar both converge on the same
     * atomic path, and repeated layouts join the pass that is already pending.
     */
    private fun scheduleLayoutPass(container: ViewGroup) {
        if (!pending.add(container)) return
        val posted = try {
            container.post {
                synchronized(pending) { pending.remove(container) }
                if (!installed.get() || !active.get()) return@post
                if (synchronized(slotLists) { slotLists.isEmpty() }) return@post
                if (slotPermutationNeeded()) {
                    activateOnMain()
                } else if (needsMove(container, afterMobile = true)) {
                    movePass(container, afterMobile = true)
                    logActiveIfNeeded()
                }
            }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("order.postLayoutPass", t)
            false
        }
        if (!posted) synchronized(pending) { pending.remove(container) }
    }

    // ---- main-thread handoff ----

    /**
     * True on SystemUI's main thread. A unit-test JVM has no looper, and running in place there is
     * exactly what the pure tests need.
     */
    private fun isMainThread(): Boolean = try {
        val main = Looper.getMainLooper()
        main == null || Looper.myLooper() == main
    } catch (_: Throwable) {
        true
    }

    /** Off-main callers queue one main-thread pass; every SystemUI path is already main. */
    private fun postToMain(block: () -> Unit) {
        try {
            val main = Looper.getMainLooper()
            if (main == null) {
                block()
                return
            }
            Handler(main).post(block)
        } catch (_: Throwable) {
            block()
        }
    }

    // ---- pure rules (unit-tested without Android) ----

    /**
     * An activation schedules a move pass only when it is the first activation this session (the
     * views may still be in stock order) or when it actually changed the captured slot order.
     */
    internal fun shouldScheduleOnActivate(wasActive: Boolean, changed: Boolean): Boolean =
        !wasActive || changed

    /**
     * `wifi` moves immediately after the last `mobile`, every other slot keeps its relative order.
     * Missing `wifi` or `mobile` leaves the list unchanged; applying it twice changes nothing.
     */
    fun permutedSlots(names: List<String>): List<String> =
        permutedEntries(names) { it as String? }.map { it as String }

    /**
     * The same rule over the live slot objects: `wifi` moves immediately after the last entry whose
     * resolved name is `mobile`, every other entry (vowifi/volte/nosim/battery) keeps its order.
     * The input objects are moved, never copied. Entries whose name cannot be resolved are opaque:
     * a list containing none of the known names is returned untouched.
     */
    private fun permutedEntries(entries: List<Any>, nameOf: (Any) -> String?): List<Any> {
        val wifiIndex = entries.indexOfFirst { nameOf(it).isToken(WIFI) }
        if (wifiIndex < 0) return entries
        if (entries.none { nameOf(it).isToken(MOBILE) }) return entries
        val out = entries.toMutableList()
        val wifi = out.removeAt(wifiIndex)
        val lastMobile = out.indexOfLast { nameOf(it).isToken(MOBILE) }
        if (lastMobile < 0) return entries
        out.add(lastMobile + 1, wifi)
        return out
    }

    /**
     * Resolves the slot name carried by one `StatusBarIconList.mSlots` entry.
     *
     * Measured shape (OxygenOS `StatusBarIconList$Slot`): a `getName()` method and an `mName` field.
     * The lookup order is String, public `getName()`, then a declared-or-inherited `name`/`mName`
     * field. Null means the entry is not a slot we understand.
     */
    private fun slotNameOf(entry: Any?): String? {
        if (entry == null) return null
        if (entry is String) return entry
        try {
            val method = entry.javaClass.getMethod("getName")
            if (method.parameterCount == 0 && method.returnType == String::class.java) {
                try {
                    method.isAccessible = true
                } catch (t: Throwable) {
                    // A public method on a public class does not need this; keep going either way.
                    IosV2Log.failureOnce("order.slotNameAccess", t)
                }
                (method.invoke(entry) as? String)?.let { return it }
            }
        } catch (t: Throwable) {
            // No public getName(); fall through to the field.
            IosV2Log.branchOnce("order.slotNameMethod", "no getName - field fallback")
        }
        for (fieldName in arrayOf("name", "mName")) {
            (readField(entry, fieldName) as? String)?.let { return it }
        }
        return null
    }

    private fun readField(instance: Any, name: String): Any? {
        var cls: Class<*>? = instance.javaClass
        while (cls != null && cls != Any::class.java) {
            val field = try {
                cls.getDeclaredField(name)
            } catch (t: Throwable) {
                IosV2Log.branchOnce("order.fieldMiss.$name", "field absent in this class")
                null
            }
            if (field != null) {
                return try {
                    field.isAccessible = true
                    field.get(instance)
                } catch (t: Throwable) {
                    IosV2Log.failureOnce("order.readField.$name", t)
                    null
                }
            }
            cls = cls.superclass
        }
        return null
    }

    /**
     * The insertion index for Wi-Fi in `mobile -> wifi`: after the last `mobile` in the list once
     * Wi-Fi is removed (the index `addView` sees after the same removal). Null when there is no
     * mobile child, in which case Wi-Fi is left alone.
     */
    fun movedIndex(childSlots: List<String?>): Int? {
        val others = childSlots.filterNot { it.isToken(WIFI) }
        val lastMobile = others.indexOfLast { it.isToken(MOBILE) }
        return if (lastMobile >= 0) lastMobile + 1 else null
    }

    /**
     * The insertion index for Wi-Fi in the stock `wifi -> mobile`: before the first `mobile` in the
     * list once Wi-Fi is removed. Null when there is no mobile child.
     */
    fun restoredIndex(childSlots: List<String?>): Int? {
        val others = childSlots.filterNot { it.isToken(WIFI) }
        val firstMobile = others.indexOfFirst { it.isToken(MOBILE) }
        return if (firstMobile >= 0) firstMobile else null
    }

    // ---- slot list capture ----

    private fun captureSlotList(instance: Any?) {
        // C: a retained getViewIndex/constructor hook must stay inert after release.
        if (!installed.get()) return
        if (instance == null) return
        val list = readSlots(instance) ?: return
        val added = synchronized(slotLists) {
            if (slotLists.none { it.list === list }) {
                slotLists.add(SlotListSnapshot(list, list.toList()))
                true
            } else {
                false
            }
        }
        // A list captured after activation (late injection) must be moved and permuted in the same
        // main-thread pass, exactly like the first activation.
        if (added && active.get()) activate()
    }

    /**
     * `StatusBarIconList.mSlots`, the live ordered slot list; never a copy.
     *
     * The measured field is an `ArrayList<StatusBarIconList.Slot>` (reverse/smali/oneplus
     * `StatusBarIconList.smali:7`, filled with `new Slot(name)` in the constructor), so the entries
     * are objects, not Strings. Any mutable list whose entries resolve a slot name is accepted; a
     * list with an entry whose name cannot be resolved is rejected rather than half-handled.
     */
    private fun readSlots(instance: Any): MutableList<Any>? {
        var cls: Class<*>? = instance.javaClass
        while (cls != null && cls != Any::class.java) {
            val field = try {
                cls.getDeclaredField("mSlots")
            } catch (_: Throwable) {
                null
            }
            if (field != null) {
                return try {
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val raw = field.get(instance) as? MutableList<Any> ?: return null
                    if (raw.all { slotNameOf(it) != null }) {
                        raw
                    } else {
                        IosV2Log.branchOnce(
                            "order.readSlots",
                            "slot name unresolved - order stays stock"
                        )
                        null
                    }
                } catch (t: Throwable) {
                    IosV2Log.failureOnce("order.readSlots", t)
                    null
                }
            }
            cls = cls.superclass
        }
        return null
    }

    /** True when at least one captured list would actually change under the iOS rule. */
    private fun slotPermutationNeeded(): Boolean = synchronized(slotLists) {
        slotLists.any { snapshot ->
            !sameOrder(permutedEntries(snapshot.list) { slotNameOf(it) }, snapshot.list)
        }
    }

    private fun applySlotPermutation(): Boolean = synchronized(slotLists) {
        var changed = false
        for (snapshot in slotLists) {
            val target = permutedEntries(snapshot.list) { slotNameOf(it) }
            if (!sameOrder(target, snapshot.list)) {
                snapshot.list.clear()
                snapshot.list.addAll(target)
                changed = true
            }
        }
        return changed
    }

    private fun restoreSlotLists() {
        synchronized(slotLists) {
            for (snapshot in slotLists) {
                restoreMerged(snapshot.list, snapshot.stock)
            }
        }
    }

    /**
     * Puts the captured stock order back without deleting slots the system created after the
     * snapshot.
     *
     * `StatusBarIconList.findOrInsertSlot` inserts a new slot at index 0
     * (reverse/smali/oneplus/`ui/StatusBarIconList.smali`), so a naive `clear()` + `addAll(stock)`
     * would drop that slot together with its `mHolder` and shift every later `getViewIndex`. Instead
     * every stock entry that is still present by identity is moved to its stock position in order;
     * live entries unknown to the snapshot keep their relative order after the known ones. A stock
     * entry the system already removed is skipped and the rest of the list is left untouched.
     */
    private fun restoreMerged(live: MutableList<Any>, stock: List<Any>) {
        var pos = 0
        for (entry in stock) {
            // Everything before `pos` is already settled, so only search the unsettled tail.
            var found = pos
            while (found < live.size && live[found] !== entry) found++
            if (found >= live.size) continue
            if (found != pos) {
                live.removeAt(found)
                live.add(pos, entry)
            }
            pos++
        }
    }

    /** Identity comparison: the live slot objects must move, never be replaced by equals() copies. */
    private fun sameOrder(actual: List<Any>, expected: List<Any>): Boolean {
        if (actual.size != expected.size) return false
        for (i in actual.indices) {
            if (actual[i] !== expected[i]) return false
        }
        return true
    }

    // ---- view moves ----

    /** What one synchronous move pass achieved. */
    private enum class MoveResult {
        MOVED,
        ALREADY,
        NOT_APPLICABLE,
        FAILED,
        SKIPPED
    }

    /** Whether a move surface existed, and whether any container failed to move. */
    private class SurfaceResult(val surface: Boolean, val failed: Boolean) {
        val safe: Boolean get() = surface && !failed
    }

    private fun moveAll(afterMobile: Boolean) {
        val snapshot = synchronized(containers) { containers.toList() }
        for (container in snapshot) movePass(container, afterMobile)
    }

    /**
     * Moves every registered container synchronously. `surface` is true when at least one container
     * was aligned with the requested order (moved or already correct); `failed` is true when a
     * container that should be movable could not be moved.
     */
    private fun moveAllForActivate(): SurfaceResult {
        val snapshot = synchronized(containers) { containers.toList() }
        var surface = syntheticSurfaceForTest
        var failed = false
        for (container in snapshot) {
            when (movePass(container, afterMobile = true)) {
                MoveResult.MOVED, MoveResult.ALREADY -> surface = true
                MoveResult.FAILED -> failed = true
                MoveResult.NOT_APPLICABLE, MoveResult.SKIPPED -> Unit
            }
        }
        return SurfaceResult(surface, failed)
    }

    /**
     * Moves only the registered containers whose Wi-Fi child is not already at the iOS target index.
     * This is the repeat-activation recovery: a container can be re-ordered into stock order without
     * an `addView` and without another layout, leaving the views wrong even though the slot list is
     * unchanged. An already-correct container is not touched, so a normal apply adds no pass.
     */
    private fun moveMisordered() {
        val snapshot = synchronized(containers) { containers.toList() }
        for (container in snapshot) {
            if (needsMove(container, afterMobile = true)) movePass(container, afterMobile = true)
        }
    }

    /**
     * One idempotent, synchronous move pass for [container]. `afterMobile = true` is the iOS order,
     * `false` is the stock order. The same view instance is moved; visibility and drawable are
     * untouched. Never posts: the caller's callback is the frame boundary.
     */
    private fun movePass(container: ViewGroup, afterMobile: Boolean): MoveResult {
        if (!applying.compareAndSet(false, true)) return MoveResult.SKIPPED
        passCount.incrementAndGet()
        val animate = hasAnimationSetter(container)
        val previousAnimation = readAnimation(container)
        if (animate) setAnimation(container, false)
        try {
            val plan = wifiTarget(container, afterMobile) ?: return MoveResult.NOT_APPLICABLE
            val current = container.indexOfChild(plan.wifi)
            val target = plan.index.coerceIn(0, container.childCount - 1)
            if (current == target) return MoveResult.ALREADY
            moveCount.incrementAndGet()
            container.removeView(plan.wifi)
            try {
                container.addView(plan.wifi, plan.index.coerceIn(0, container.childCount))
            } catch (t: Throwable) {
                // A rejected addView must not orphan the Wi-Fi child: put it back where it was.
                if (container.indexOfChild(plan.wifi) < 0) {
                    runCatching { container.addView(plan.wifi, current.coerceIn(0, container.childCount)) }
                }
                throw t
            }
            return MoveResult.MOVED
        } catch (t: Throwable) {
            logOnce.once("ios-order-move-failed") {
                L.w("iOS order: move failed (${t.javaClass.simpleName}: ${t.message})")
            }
            return MoveResult.FAILED
        } finally {
            if (animate) setAnimation(container, previousAnimation)
            applying.set(false)
        }
    }

    /** The Wi-Fi child of [container] and the index it belongs at, or null when unresolvable. */
    private fun wifiTarget(container: ViewGroup, afterMobile: Boolean): WifiTarget? {
        val children = ArrayList<View>(container.childCount)
        for (i in 0 until container.childCount) children.add(container.getChildAt(i))
        val wifi = children.firstOrNull { StockIconKey.slotTagOf(it).isToken(WIFI) } ?: return null
        val slots = children.map { StockIconKey.slotTagOf(it) }
        val index = (if (afterMobile) movedIndex(slots) else restoredIndex(slots)) ?: return null
        return WifiTarget(wifi, index)
    }

    /**
     * Read-only form of [movePass]: true when the Wi-Fi child is not at its target index. Every read
     * is guarded, so an odd tree is reported as "no move" rather than throwing inside System UI.
     */
    private fun needsMove(container: ViewGroup, afterMobile: Boolean): Boolean {
        return try {
            val plan = wifiTarget(container, afterMobile) ?: return false
            val current = container.indexOfChild(plan.wifi)
            current != plan.index.coerceIn(0, container.childCount - 1)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("order.needsMove", t)
            false
        }
    }

    private class WifiTarget(val wifi: View, val index: Int)

    /**
     * Last-resort guard for bug B: `IconManager.onSetIcon(index)` casts `getChildAt(index)` to
     * `StatusBarIconView`. If a stale/permuted index points at the OPLUS Wi-Fi composite (or any
     * other non-icon child), restore the stock order and skip the cast instead of crashing SystemUI.
     */
    private fun guardIconManagerCall(param: XC_MethodHook.MethodHookParam) {
        if (!active.get()) return
        val index = param.args.firstOrNull() as? Int ?: return
        val group = param.thisObject as? ViewGroup ?: return
        val child = try {
            group.getChildAt(index)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("order.iconGuard.child", t)
            return
        } ?: return
        if (isIconManagerChildClass(child)) return
        logOnce.once("ios-order-icon-guard") {
            L.w(
                "iOS order: IconManager.onSetIcon child ${child.javaClass.simpleName} at $index " +
                    "is not a StatusBarIconView - restoring stock order"
            )
        }
        restore()
        param.result = null
    }

    /**
     * Cheap class-name predicate for the `IconManager` cast target. AOSP/OEM `StatusBarIconView`
     * subclasses (e.g. `SingleBindableStatusBarIconView`) pass by name; the OPLUS composite
     * (`OplusModernStatusBarWifiView`) and every other child do not. `StatIconView` is OxygenOS's
     * opaque icon and casts cleanly.
     */
    internal fun isIconManagerChildClass(simpleName: String?): Boolean =
        simpleName != null &&
            (simpleName.contains("StatusBarIconView") || simpleName == "StatIconView")

    /**
     * The type-chain form used by [guardIconManagerCall]: the child's class or any superclass must
     * be a `StatusBarIconView` (or `StatIconView`). A wrapper whose own name merely contains the
     * string is not enough unless its base class really is the cast target.
     */
    internal fun isIconManagerChildClass(type: Class<*>): Boolean {
        var cls: Class<*>? = type
        while (cls != null && cls != Any::class.java) {
            if (isIconManagerChildClass(cls.simpleName)) return true
            cls = cls.superclass
        }
        return false
    }

    internal fun isIconManagerChildClass(view: View): Boolean =
        isIconManagerChildClass(view.javaClass)

    private fun logActiveIfNeeded() {
        if (loggedActive) return
        loggedActive = true
        val surfaces = synchronized(containers) { containers.size }
        val slots = if (synchronized(slotLists) { slotLists.isEmpty() }) {
            "stock"
        } else synchronized(slotLists) {
            if (slotLists.any { !sameOrder(it.list, it.stock) }) "permuted" else "stock"
        }
        L.i("iOS order: mobile -> wifi (surfaces=$surfaces slots=$slots)")
    }

    // ---- animation suppression (OEM containers may animate an add/remove) ----

    private fun hasAnimationSetter(container: ViewGroup): Boolean =
        findAnimationSetter(container.javaClass) != null

    private fun setAnimation(container: ViewGroup, enabled: Boolean) {
        val method = findAnimationSetter(container.javaClass) ?: return
        try {
            method.isAccessible = true
            method.invoke(container, enabled)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("order.setAnimation", t)
        }
    }

    private fun findAnimationSetter(cls: Class<*>): java.lang.reflect.Method? {
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredMethod("setEnableAnimation", Boolean::class.javaPrimitiveType)
            } catch (t: Throwable) {
                IosV2Log.branchOnce(
                    "order.animationSetterMiss",
                    "no setEnableAnimation in ${current?.simpleName ?: "?"}"
                )
                current = current.superclass
            }
        }
        return null
    }

    private fun readAnimation(container: ViewGroup): Boolean {
        var cls: Class<*>? = container.javaClass
        while (cls != null && cls != Any::class.java) {
            val field = try {
                cls.getDeclaredField("mEnableAnimation")
            } catch (t: Throwable) {
                IosV2Log.branchOnce(
                    "order.animationFieldMiss.${cls?.simpleName ?: "?"}",
                    "no mEnableAnimation - animation kept"
                )
                null
            }
            if (field != null) {
                return try {
                    field.isAccessible = true
                    field.getBoolean(container)
                } catch (t: Throwable) {
                    IosV2Log.failureOnce("order.readAnimation", t)
                    true
                }
            }
            cls = cls.superclass
        }
        return true
    }

    // ---- reflection helpers ----

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? = try {
        XposedHelpers.findClass(name, classLoader)
    } catch (t: Throwable) {
        IosV2Log.branchOnce(
            "order.findClass.${name.substringAfterLast('.')}",
            "class missing - stock order kept"
        )
        null
    }

    private inline fun hookSafely(block: () -> Collection<XC_MethodHook.Unhook>) {
        try {
            val added = block()
            synchronized(unhooks) { unhooks.addAll(added) }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("order.hook", t)
        }
    }

    private fun String?.isToken(token: String): Boolean =
        this != null && equals(token, ignoreCase = true)

    // ---- test seams ----

    /**
     * Seeds a live slot list without an Android `StatusBarIconList`. Returns the list so a test can
     * assert the in-place permutation and the exact restore.
     */
    internal fun seedSlotListForTest(names: List<String>): MutableList<String> {
        @Suppress("UNCHECKED_CAST")
        return seedSlotEntriesForTest(ArrayList(names)) as MutableList<String>
    }

    /**
     * Seeds the production shape: a live list of slot objects (the `StatusBarIconList.Slot` stand-in
     * in tests). Returns the list so a test can assert the in-place permutation and the exact restore.
     */
    internal fun seedSlotEntriesForTest(entries: List<Any>): MutableList<Any> {
        val live = ArrayList(entries)
        synchronized(slotLists) {
            slotLists.clear()
            slotLists.add(SlotListSnapshot(live, live.toList()))
        }
        return live
    }

    /** The production restore merge over an arbitrary live list, for the merge/idempotence tests. */
    internal fun restoreSlotsForTest(live: MutableList<Any>, stock: List<Any>) =
        restoreMerged(live, stock)

    /** The generic permutation seam: the production rule over arbitrary slot-name resolvers. */
    internal fun permutedEntriesForTest(
        entries: List<Any>,
        nameOf: (Any) -> String?
    ): List<Any> = permutedEntries(entries, nameOf)

    /** Exposes the production `mSlots` reader for a fake list holder; no Android class required. */
    internal fun readSlotsForTest(instance: Any): MutableList<Any>? = readSlots(instance)

    /** Marks the hook installer as done without touching Xposed, for the Robolectric view tests. */
    internal fun markInstalledForTest() {
        installed.set(true)
    }

    /** Whether [install] has run and [release] has not cleared it (test seam). */
    internal fun isInstalledForTest(): Boolean = installed.get()

    /** How many slot lists are currently captured (test seam). */
    internal fun slotListCountForTest(): Int = synchronized(slotLists) { slotLists.size }

    /** How many containers are currently registered (test seam). */
    internal fun containerCountForTest(): Int = synchronized(containers) { containers.size }

    /** How many class-level unhooks are still held (test seam). */
    internal fun unhookCountForTest(): Int = synchronized(unhooks) { unhooks.size }

    /** Runs the production slot-list capture for a fake holder; no Android class required. */
    internal fun captureSlotListForTest(instance: Any?) = captureSlotList(instance)

    /** Kept for older tests: every move pass is synchronous now, so there is nothing to flush. */
    internal fun flushForTest() = Unit

    /**
     * Test-only movable surface for the pure tests: `activate()` now refuses to permute slots when no
     * container could be moved, and a JVM test has no view tree. Never used by the module.
     */
    internal fun markMovableSurfaceForTest() {
        syntheticSurfaceForTest = true
    }

    /** Production constructor sighting, for the Robolectric tests. */
    internal fun onContainerSeenForTest(container: ViewGroup) = onContainerSeen(container)

    /** Production first-layout adoption path, for the Robolectric tests. */
    internal fun onContainerLayoutForTest(container: ViewGroup) = onContainerLayout(container)

    /** Move passes that actually moved Wi-Fi (test seam). */
    internal fun movesForTest(): Int = moves

    /** Move passes that ran (test seam). */
    internal fun passesForTest(): Int = passes

    /** Zeroes only the diagnostics counters, leaving containers and slots in place (test seam). */
    internal fun resetCountersForTest() {
        moveCount.set(0)
        passCount.set(0)
    }

    /** Clears all process-wide state between tests; never used by the module. */
    internal fun clearForTest() {
        active.set(false)
        applying.set(false)
        installed.set(false)
        loggedActive = false
        syntheticSurfaceForTest = false
        synchronized(containers) { containers.clear() }
        synchronized(layoutAdopted) { layoutAdopted.clear() }
        synchronized(pending) { pending.clear() }
        synchronized(slotLists) { slotLists.clear() }
        synchronized(unhooks) { unhooks.clear() }
        moveCount.set(0)
        passCount.set(0)
    }
}
