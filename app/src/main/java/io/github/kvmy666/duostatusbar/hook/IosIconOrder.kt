package io.github.kvmy666.duostatusbar.hook

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
 *  2. on [activate], permute `mSlots` in place and move the same Wi-Fi view after the last `mobile`
 *     child of every registered `StatusIconContainer`,
 *  3. on [restore], put the captured slot order back (keeping any slot the system created after the
 *     snapshot) and move Wi-Fi before the first `mobile` child.
 *
 * The order is owned by the process, not by a surface: tearing down the QS header while iOS stays on
 * must not restore the stock order. Only [restore] (leaving iOS, or the module being turned off) does.
 *
 * Every step is optional and guarded. If the slot list or container class cannot be resolved, the
 * stock order is left untouched and the reason is logged once. Nothing here guesses a class or an id:
 * the names are the measured ones from the device's SystemUI.
 *
 * A container is seen by a constructor, the existing `addView` hook, or its first `onLayout`. Only
 * that first layout may schedule anything; every later layout is a single weak-set lookup, so an
 * already-ordered container does no per-frame work. Moves are scheduled only from the `addView` hook,
 * a slot-list capture/permutation change, or [activate]/[restore].
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

    /** Diagnostics: child move passes that actually moved Wi-Fi (session total). */
    private val moveCount = AtomicInteger(0)

    /** Diagnostics: `applyTo` passes that ran (session total). */
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


    /** Containers scheduled for the next frame, so a move never runs inside a layout pass. */
    private val pending: MutableSet<ViewGroup> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))

    /** Containers with a post already queued, so repeated icon adds do not stack work. */
    private val posted: MutableSet<ViewGroup> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>()))

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
     * Diagnostics: `"slot-list"` only while the order is active and at least one slot list was
     * captured (only a captured list can be permuted); `"stock"` otherwise.
     */
    internal val source: String
        get() = if (active.get() && synchronized(slotLists) { slotLists.isNotEmpty() }) {
            "slot-list"
        } else {
            "stock"
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
                // a weak-set lookup and returns without scheduling. The move itself is posted, so
                // this never changes the tree inside the layout callback.
                val laidOut = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? ViewGroup)?.let { onContainerLayout(it) }
                    }
                }
                hookSafely { XposedBridge.hookAllConstructors(cls, seen) }
                hookSafely { XposedBridge.hookAllMethods(cls, "onLayout", laidOut) }
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
     * iOS owns the bar: permute the captured slot lists and move Wi-Fi after the last mobile child
     * of every registered container. Idempotent; safe to call on every settings apply/attach.
     */
    fun activate() {
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
        val changed = applySlotPermutation()
        // A repeated settings apply on an unchanged, already-adopted bar must schedule nothing; the
        // first activation still schedules because the views may not have been moved yet. The one
        // exception is a container the ROM re-ordered without an addView (no hook fired) and with no
        // later first layout: a read-only target check recovers exactly those containers.
        if (shouldScheduleOnActivate(wasActive, changed)) {
            scheduleAll()
        } else {
            scheduleMisordered()
        }
    }

    /**
     * iOS no longer owns the bar: put the captured stock slot order back and move Wi-Fi before the
     * first mobile child. Idempotent; a no-op when iOS was never active.
     */
    fun restore() {
        if (!active.getAndSet(false)) return
        restoreSlotLists()
        scheduleAll()
        loggedActive = false
        // Only one log per leave event; restore() returns above when there was nothing to restore.
        L.i("iOS order restored")
    }

    /**
     * Unhooks every class-level order hook and drops the captured state (C).
     *
     * Called by the iOS teardown after [restore]. A second call is safe and does nothing. A pending
     * restore move is flushed before its queue is cleared, otherwise the posted frame would be dropped
     * and the Wi-Fi child would stay in the iOS position.
     */
    @Synchronized
    fun release() {
        // Stop new capture/registration before touching any state, then restore what this session
        // changed. A caller may release without a prior restore; never leave the stock order permuted.
        installed.set(false)
        restore()
        active.set(false)
        applying.set(false)
        loggedActive = false
        val queued = synchronized(pending) { pending.toList() }
        for (container in queued) {
            pending.remove(container)
            posted.remove(container)
            applyTo(container)
        }
        synchronized(pending) { pending.clear() }
        synchronized(posted) { posted.clear() }
        synchronized(containers) { containers.clear() }
        synchronized(layoutAdopted) { layoutAdopted.clear() }
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
     * Registers the container and, while iOS owns the bar, schedules one idempotent move pass.
     */
    fun onIconAdded(container: ViewGroup) {
        // A6: iOS code does nothing while iOS is off. The Duo-only process keeps this a single
        // boolean read; the hooks are only installed from an iOS activation path.
        if (!installed.get()) return
        synchronized(containers) { containers.add(container) }
        // Our own remove/add during a reorder must not queue another pass.
        if (applying.get()) return
        if (!active.get() || synchronized(slotLists) { slotLists.isEmpty() }) return
        schedule(container)
    }

    /** Constructor path: remember the container for activate()/scheduleAll(); never schedules. */
    fun onContainerSeen(container: ViewGroup) {
        if (!installed.get()) return
        synchronized(containers) { containers.add(container) }
    }

    /**
     * `StatusIconContainer.onLayout` path: the FIRST layout of a container adopts it (register + at
     * most one schedule). Every later layout returns after one weak-set lookup: no post, no tree walk,
     * no slot read.
     */
    fun onContainerLayout(container: ViewGroup) {
        if (!installed.get()) return
        if (!layoutAdopted.add(container)) return
        synchronized(containers) { containers.add(container) }
        if (!active.get() || synchronized(slotLists) { slotLists.isEmpty() }) return
        schedule(container)
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
                if (active.get()) applySlotPermutationLocked()
                true
            } else {
                false
            }
        }
        // A list captured after activation (late injection) still gets the pending view moves.
        if (added && active.get()) scheduleAll()
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

    private fun applySlotPermutation(): Boolean = synchronized(slotLists) {
        applySlotPermutationLocked()
    }

    private fun applySlotPermutationLocked(): Boolean {
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

    private fun scheduleAll() {
        val snapshot = synchronized(containers) { containers.toList() }
        snapshot.forEach { schedule(it) }
    }

    /**
     * Queues only the registered containers whose Wi-Fi child is not already at the iOS target index.
     *
     * This is the repeat-activation recovery: a container can be re-ordered into stock order without
     * an `addView` (so no hook fires) and without another layout, leaving the views wrong even though
     * the slot list is unchanged. An already-correct container is not queued, so a normal settings
     * apply still adds no pass.
     */
    private fun scheduleMisordered() {
        val snapshot = synchronized(containers) { containers.toList() }
        snapshot.forEach { container ->
            if (needsMove(container, afterMobile = true)) schedule(container)
        }
    }

    /** Queue one move pass for the next frame; repeated calls coalesce per container. */
    private fun schedule(container: ViewGroup) {
        synchronized(pending) { pending.add(container) }
        if (!posted.add(container)) return
        val move = Runnable {
            posted.remove(container)
            if (pending.remove(container)) applyTo(container)
        }
        try {
            container.post(move)
        } catch (t: Throwable) {
            posted.remove(container)
            pending.remove(container)
            // A view with no handler (unit tests, a detached copy) is still safe to move directly.
            IosV2Log.branchOnce("order.schedule", "no handler - move applied directly")
            applyTo(container)
        }
    }

    /** One idempotent move pass for [container]; never called from inside a layout callback. */
    private fun applyTo(container: ViewGroup) {
        if (!applying.compareAndSet(false, true)) return
        passCount.incrementAndGet()
        val animate = hasAnimationSetter(container)
        val previousAnimation = readAnimation(container)
        if (animate) setAnimation(container, false)
        try {
            if (active.get()) {
                if (synchronized(slotLists) { slotLists.isEmpty() }) return
                moveWifi(container, afterMobile = true)
                logActiveIfNeeded()
            } else {
                moveWifi(container, afterMobile = false)
            }
        } catch (t: Throwable) {
            logOnce.once("ios-order-move-failed") {
                L.w("iOS order: move failed (${t.javaClass.simpleName}: ${t.message})")
            }
        } finally {
            if (animate) setAnimation(container, previousAnimation)
            applying.set(false)
        }
    }

    private fun moveWifi(container: ViewGroup, afterMobile: Boolean) {
        val plan = wifiTarget(container, afterMobile) ?: return
        val current = container.indexOfChild(plan.wifi)
        val clamp = plan.index.coerceIn(0, container.childCount - 1)
        if (current == clamp) return
        // Move the same instance: identity, visibility and drawable are untouched.
        moveCount.incrementAndGet()
        container.removeView(plan.wifi)
        container.addView(plan.wifi, plan.index.coerceIn(0, container.childCount))
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
     * Read-only form of [moveWifi]: true when the Wi-Fi child is not at its target index. Every read
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

    /** Runs every queued move pass synchronously, instead of waiting for a frame. */
    internal fun flushForTest() {
        val queued = synchronized(pending) { pending.toList() }
        for (container in queued) {
            pending.remove(container)
            posted.remove(container)
            applyTo(container)
        }
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
        synchronized(containers) { containers.clear() }
        synchronized(layoutAdopted) { layoutAdopted.clear() }
        synchronized(pending) { pending.clear() }
        synchronized(posted) { posted.clear() }
        synchronized(slotLists) { slotLists.clear() }
        synchronized(unhooks) { unhooks.clear() }
        moveCount.set(0)
        passCount.set(0)
    }
}
