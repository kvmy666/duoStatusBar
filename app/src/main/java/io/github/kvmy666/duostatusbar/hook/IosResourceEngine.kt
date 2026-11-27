package io.github.kvmy666.duostatusbar.hook

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.LongSparseArray
import android.util.TypedValue
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import java.util.Arrays
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.roundToInt

/** Which iOS glyph a mapped stock resource becomes. */
internal sealed class IosResourceSlot {
    data class Wifi(val dots: Int) : IosResourceSlot()
    data class Cell(val dots: Int) : IosResourceSlot()
    data class Label(val text: String) : IosResourceSlot()

    /** Hide-extras leaf: a 0x0 gate while [IosExtras.hidden], the captured stock drawable otherwise. */
    object Hidden : IosResourceSlot()
}

/** One resolved stock id: the slot it becomes, its stock intrinsic size (dp) and its package. */
internal data class ResolvedEntry(
    val id: Int,
    val slot: IosResourceSlot,
    val widthDp: Float,
    val heightDp: Float,
    val packageName: String,
    /**
     * R7 P4/R9: the drawable transform. V2 always uses [IosUserTransform.IDENTITY] because
     * `IosSlotLayout` owns the per-icon scale by resizing the live stock leaf; HOST/preview keep
     * their own drawable transform. Identity is exactly the pre-setting pixels (rule 6).
     */
    val transform: IosUserTransform = IosUserTransform.IDENTITY,
    /**
     * R8 hide-extras: the captured stock `ConstantState` of a [IosResourceSlot.Hidden] leaf, and its
     * stock intrinsic px. Null/zero for every glyph entry, which never reads these fields.
     */
    val stockState: Drawable.ConstantState? = null,
    val stockWidthPx: Int = 0,
    val stockHeightPx: Int = 0
)

/**
 * R8 hide-extras: a [IosResourceSlot.Hidden] entry can only mint a gate when the stock
 * `ConstantState` was captured. Without it the gate would be 0x0 in both toggle states, so the
 * entry is left on the stock drawable instead (rule 6: the default must not change). Pure, so the
 * build rule is tested without a device resource table.
 */
internal fun hiddenCanRestore(stockState: Drawable.ConstantState?): Boolean = stockState != null

/** R4: why one resource spec was dropped from the map, exactly as it appears in the log line. */
internal object IosMapDropReason {
    const val NO_ID = "no_id"
    const val DUPLICATE = "duplicate"
    const val UNSUPPORTED_SLOT = "unsupported_slot"
    const val NO_DRAWABLE = "no_drawable"
    const val NO_INTRINSIC = "no_intrinsic"
    const val NO_STOCK_STATE = "no_stock_state"
}

/** R4: one dropped spec; [name] is the spec name or `0x<hex>` for a raw-id spec. */
internal data class IosMapDrop(val family: String, val name: String, val reason: String)

/** R4: the name a drop line uses; raw-id specs have a blank name, so their hex id is shown. */
internal fun iosMapDropName(spec: IosResourceSpec): String =
    spec.name.ifBlank { "0x" + Integer.toHexString(spec.rawId) }

/** R4: the exact per-drop log line (one per dropped spec, non-OxygenOS only). */
internal fun formatIosMapDrop(drop: IosMapDrop): String =
    "ios v2 map drop family=${drop.family} name=${drop.name} reason=${drop.reason}"

/**
 * R4: the per-spec drop lines are emitted for every family except OxygenOS, whose log is frozen (its
 * two `hiddenNoIntrinsic` passpoint drops must not add lines). Pure so the suppression is tested.
 */
internal fun iosMapDropsEnabled(family: String): Boolean = family != "oxygenos"

/**
 * R4: collects every dropped resource spec during one map build. Pure (no Android calls) so the
 * name/reason/format rule is unit-tested without a device resource table.
 */
internal class IosMapDrops(private val family: String) {

    private val dropped = ArrayList<IosMapDrop>()

    fun add(spec: IosResourceSpec, reason: String) {
        dropped.add(IosMapDrop(family, iosMapDropName(spec), reason))
    }

    /** All collected drops, regardless of whether the family logs them (diagnostics/tests). */
    fun entries(): List<IosMapDrop> = dropped.toList()

    /** The drops this family is allowed to log; OxygenOS gets none (its log is frozen). */
    fun logEntries(): List<IosMapDrop> =
        if (iosMapDropsEnabled(family)) dropped.toList() else emptyList()

    /** The formatted log lines for [logEntries]. */
    fun lines(): List<String> = logEntries().map(::formatIosMapDrop)
}

/**
 * R7 P1: replaces a small, measured set of status-bar drawables at load time by hooking
 * `android.content.res.ResourcesImpl.loadDrawable(Resources, TypedValue, int, int, Theme)`.
 *
 * This is the mechanism proven by Oxygen Customizer and mapped by the R7 P0 evidence: the system
 * loads each stock Wi-Fi/cell/data-type drawable itself, so replacing the load covers the bar,
 * keyguard and shade header at inflation time with no host, attach, overlay or layout work
 * (`.pm/R7-PLAN.md:9-26`, `.pm/R7-P0-map.md:81-97`).
 *
 * Safety rules from `.pm/R7-P0-map.md:458-475`:
 *  - the first check is a primitive binary search over a sorted `IntArray`; the miss path allocates
 *    nothing and logs nothing (every notification icon passes through this hook);
 *  - the id must also resolve to the expected package, because `0x7f…`/`0x0108…` ids collide;
 *  - replacements are immutable `ConstantState`s cached by `(id, density, night)`, never a shared
 *    `Drawable`;
 *  - a `ThreadLocal` guard stops our own drawable creation from recursing into the hook;
 *  - the whole body is try/catch and any failure leaves the stock path (AGENTS rule 5).
 */
internal object IosResourceEngine : IosDiagnosticSource {

    private const val TAG = "ios v2 resource"

    /** Bounded per-snapshot cache for densities other than the build density. */
    private const val MAX_CACHE = 256

    private val logOnce = LogOnce()
    private val guard = ThreadLocal<Boolean>()

    @Volatile
    private var installedFlag = false

    /** True only after the guarded hook was installed in this process. */
    val installed: Boolean get() = installedFlag

    private val stateLock = Any()

    /** The published immutable map; null while inactive. */
    @Volatile
    private var snapshot: Snapshot? = null

    @Volatile
    private var activeFamily: String = ""

    // R17C: the package a miss must belong to, and the bounded name table for a non-OxygenOS report.
    // The table survives a same-family re-activation that does NOT pass through deactivate (rapid
    // settings revisions must not throw away the boot misses; the Fold built the map three times);
    // deactivate clears it deliberately.
    @Volatile
    private var activePackage: String = ""

    @Volatile
    private var missFamily: String = ""

    @Volatile
    private var missNames: IosMissNames? = null

    private var generation: Long = 0L

    private var installDone = false

    // Diagnostics are approximate on purpose (`@Volatile` is not needed; races are acceptable),
    // and the hot path must not allocate: a primitive field increment is all a miss does.
    private var calls: Long = 0L
    private var hits: Long = 0L
    private var misses: Long = 0L
    private var badPackage: Int = 0
    private var failures: Int = 0
    private var maxMissNanos: Long = 0L

    // B2 evidence: calls that arrived before the map was published were previously invisible (the
    // null-snapshot path returned first). The atomic increment allocates nothing in the hot path.
    private val callsBeforeMap = AtomicLong(0L)

    @Volatile
    private var activatedAtMs: Long = 0L

    @Volatile
    private var mapReadyAtMs: Long = 0L

    @Volatile
    private var lastBuildMs: Long = 0L

    @Volatile
    private var lastCoverage: List<IosPieceCoverage> = emptyList()

    // Runtime misses per piece, in [IOS_PIECES] order (wifi, cell, label, hidden). Incremented only
    // when the id is in the map: a package rejection or a replacement failure. Unknown ids cannot be
    // attributed to a piece, so they stay in the global `misses` counter only.
    private val pieceMisses = AtomicLongArray(4)

    val isActive: Boolean get() = installedFlag && snapshot != null

    /** Entries in the live map (0 while inactive, building or after an empty build). */
    val mapSize: Int get() = snapshot?.size ?: 0

    /** R8 diagnostics: resolved hide-extras leaves in the live map (0 while inactive). */
    val hiddenEntries: Int get() = snapshot?.hiddenCount ?: 0

    /** REL210: how many `loadDrawable` calls the hook saw (diagnostics only). */
    internal val callsForDiagnostics: Long get() = calls

    /** REL210: how many loads the live map replaced (diagnostics only). */
    internal val hitsForDiagnostics: Long get() = hits

    /** REL210: the hooked class, or its absence, for the additive diag block. */
    internal val hookClassesForDiagnostics: String
        get() = if (installedFlag) "ok:ResourcesImpl" else "missing:ResourcesImpl"

    // B2 evidence getters (report-time only; the hot path increments the primitive counters above).
    internal val callsBeforeMapForDiagnostics: Long get() = callsBeforeMap.get()
    internal val activeFamilyForDiagnostics: String get() = activeFamily
    internal val coverageForDiagnostics: List<IosPieceCoverage> get() = lastCoverage
    internal val timingForDiagnostics: String
        get() = "activatedAt=$activatedAtMs mapReadyAt=$mapReadyAtMs buildMs=$lastBuildMs " +
            "callsBeforeMap=${callsBeforeMap.get()} calls=$calls hits=$hits misses=$misses"
    internal fun runtimeMissesForDiagnostics(): LongArray =
        LongArray(4) { pieceMisses.get(it) }

    /** Test seam: clear only the B2 evidence, leaving the install/gating state intact. */
    internal fun resetEvidenceForTest() {
        callsBeforeMap.set(0L)
        activatedAtMs = 0L
        mapReadyAtMs = 0L
        lastBuildMs = 0L
        lastCoverage = emptyList()
        for (i in 0 until 4) pieceMisses.set(i, 0L)
    }

    // ---------------------------------------------------------------------------------- install

    /**
     * Installs the one process-wide hook. Idempotent and never throws: a missing class/method leaves
     * [installed] false and the stock bar alive, with exactly one warning.
     */
    @Synchronized
    fun install(classLoader: ClassLoader) {
        if (installDone) return
        installDone = true
        // Register even when the hook is absent, so every dump states why nothing was replaced.
        IosDiagnostics.register(this)
        try {
            val cls = XposedHelpers.findClass("android.content.res.ResourcesImpl", classLoader)
            XposedHelpers.findAndHookMethod(
                cls,
                "loadDrawable",
                Resources::class.java,
                TypedValue::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Resources.Theme::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = onLoadDrawable(param)
                }
            )
            installedFlag = true
            L.i("install step: ios-resource-engine")
        } catch (t: Throwable) {
            installedFlag = false
            L.w("$TAG engine unavailable: ${t.javaClass.simpleName}")
        }
    }

    // --------------------------------------------------------------------------------- activate

    /**
     * Publishes a map for [rom]. No-op (and returns false) unless the hook is installed and the family
     * is a v2 engine family. With [synchronous] the map is built inline on the boot path before the
     * first inflation; otherwise a single daemon thread builds it and a stale build is discarded.
     *
     * @return true when a non-empty map is live now (`synchronous`), or when the previous map stays
     * live while an asynchronous rebuild runs. False means there is no map to draw and the caller
     * must keep the HOST route (R7 P5: a HOST->V2 switch may not tear the host down first).
     *
     * [settings] supplies each glyph's per-icon offset/scale (R7 P4). It is read at build time, so a
     * settings change rebuilds the map through the normal per-apply [activate]; null is identity.
     */
    fun activate(
        context: android.content.Context,
        rom: RomAdapter,
        settings: ModuleSettings? = null,
        synchronous: Boolean = false,
        onPublished: (() -> Unit)? = null
    ): Boolean {
        var gen = 0L
        var previousLive = false
        synchronized(stateLock) {
            if (!installedFlag || !IosV2Profile.engineFamily(rom.id)) {
                IosV2Log.branchOnce(
                    "engine.activate",
                    "installed=$installedFlag family=${rom.id} - HOST kept"
                )
                deactivate()
                return false
            }
            gen = ++generation
            activeFamily = rom.id
            activePackage = rom.systemUiPackage
            if (missFamily != rom.id) {
                // A different family is a different resource table: start the miss table fresh. A
                // same-family re-activation keeps the boot misses instead of resetting them.
                missNames = IosMissNames()
                missFamily = rom.id
            }
            resetCountersLocked()
            activatedAtMs = System.currentTimeMillis()
            mapReadyAtMs = 0L
            lastBuildMs = 0L
            previousLive = (snapshot?.size ?: 0) > 0
        }
        val work = {
            val result = buildMap(context, rom, settings)
            var published = false
            synchronized(stateLock) {
                // Never publish an empty/failed build over a live map: V2->V2 keeps drawing the old
                // snapshot until a successful rebuild replaces it.
                if (gen == generation && result != null && result.snapshot.size > 0) {
                    snapshot = result.snapshot
                    mapReadyAtMs = System.currentTimeMillis()
                    lastBuildMs = result.buildMs
                    lastCoverage = result.coverage
                    published = true
                } else {
                    IosV2Log.branchOnce(
                        "engine.publish",
                        "stale or empty build - previous map kept"
                    )
                }
            }
            if (published && result != null) {
                // One-shot evidence, once per published map: timing vs mapping and the per-piece
                // coverage that decides whether the ROM's stock names were actually resolved.
                L.i("ios v2 timing: ${timingForDiagnostics}")
                for (line in coverageReport(result.coverage, rom.id, LongArray(4))) {
                    L.i(line)
                }
            }
            if (published) {
                // The caller's reload owner runs after the map is live; a failure here must not undo
                // the publish (the stock bar already drew this map's drawables).
                try {
                    onPublished?.invoke()
                } catch (t: Throwable) {
                    IosV2Log.failureOnce("engine.onPublished", t)
                }
            }
        }
        if (synchronous) {
            work()
            return mapSize > 0
        }
        Thread({ work() }, "duo-ios-res").apply {
            isDaemon = true
            start()
        }
        return previousLive
    }

    /**
     * Restores stock behaviour: the next load returns the stock drawable.
     *
     * Known boundary: this stops future replacements only; it does not revert an
     * [IosGlyphDrawable] already installed on a live view. [IosSlotLayout.restoreAll] restores the
     * real LayoutParams for the views this module resized.
     */
    fun deactivate() {
        synchronized(stateLock) {
            generation++
            snapshot = null
            activeFamily = ""
            activePackage = ""
            missFamily = ""
            missNames = null
            mapReadyAtMs = 0L
            lastCoverage = emptyList()
        }
    }

    private fun resetCountersLocked() {
        calls = 0L
        hits = 0L
        misses = 0L
        badPackage = 0
        failures = 0
        maxMissNanos = 0L
        callsBeforeMap.set(0L)
        for (i in 0 until 4) pieceMisses.set(i, 0L)
    }

    // ------------------------------------------------------------------------------------ hook

    private fun onLoadDrawable(param: XC_MethodHook.MethodHookParam) {
        try {
            val snap = snapshot
            if (snap == null) {
                // Before the map is published the hook still sees the bar's loads; counting them here
                // is what tells a Samsung report whether V2 arrived too late for the first inflation.
                callsBeforeMap.incrementAndGet()
                return
            }
            val args = param.args
            if (args.size < 4) return
            val id = args[2] as? Int ?: return
            calls += 1L
            val started = if ((calls and 63L) == 0L) System.nanoTime() else 0L
            val index = snap.indexOf(id)
            if (index < 0) {
                misses += 1L
                if (started != 0L) {
                    val elapsed = System.nanoTime() - started
                    if (elapsed > maxMissNanos) maxMissNanos = elapsed
                }
                // R17C: name the miss only outside OxygenOS (its log is frozen). The package filter
                // runs on the miss path; the entry-name lookup runs once per new id in the bounded
                // table, so a notification icon never resolves a name more than once.
                if (activeFamily != "oxygenos") noteMiss(id, args[0])
                return
            }
            val wrapper = args[0] as? Resources ?: return
            val pkg = try {
                wrapper.getResourcePackageName(id)
            } catch (t: Throwable) {
                badPackage += 1
                pieceMisses.incrementAndGet(pieceIndexOf(snap.slotAt(index)))
                IosV2Log.failureOnce("engine.packageName", t)
                return
            }
            if (!snap.accepts(index, pkg)) {
                badPackage += 1
                pieceMisses.incrementAndGet(pieceIndexOf(snap.slotAt(index)))
                return
            }
            val result = replace(snap, index, wrapper, args[3] as? Int ?: 0, nightOf(wrapper))
            if (result != null) {
                param.result = result
                hits += 1L
            }
        } catch (t: Throwable) {
            // Whole body guarded: any unexpected failure leaves the stock load path untouched.
            IosV2Log.failureOnce("engine.loadDrawable", t)
        }
    }

    /**
     * R17C: a miss path only, outside OxygenOS. The package filter keeps app notification icons out
     * of the name table, and [IosMissNames] resolves an entry name only once per new id. Any failure
     * is silent.
     */
    private fun noteMiss(id: Int, arg: Any?) {
        try {
            val tracker = missNames ?: return
            val resources = arg as? Resources ?: return
            val pkg = try {
                resources.getResourcePackageName(id)
            } catch (t: Throwable) {
                return
            }
            val expected = activePackage
            if (expected.isNotEmpty() && pkg != expected) return
            val family = missFamily
            val name = tracker.record(id) {
                try {
                    resources.getResourceEntryName(id)
                } catch (t: Throwable) {
                    null
                }
            }
            if (!name.isNullOrBlank()) {
                logOnce.once("ios-miss:$family:$id") {
                    L.i("ios v2 miss family=$family res=$name")
                }
            }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("engine.missName", t)
        }
    }

    /** Piece index for the runtime miss counters, aligned with [IOS_PIECES] (allocation-free). */
    private fun pieceIndexOf(slot: IosResourceSlot): Int = when (slot) {
        is IosResourceSlot.Wifi -> 0
        is IosResourceSlot.Cell -> 1
        is IosResourceSlot.Label -> 2
        is IosResourceSlot.Hidden -> 3
    }

    /**
     * R7 P5 test seam: the same decision/replacement the hook body makes, without an Xposed method
     * parameter. [guarded] simulates our own drawable creation re-entering `loadDrawable`.
     */
    internal fun handleForTest(
        snapshotForTest: Snapshot,
        resources: Resources,
        id: Int,
        density: Int,
        night: Boolean,
        guarded: Boolean = false
    ): Drawable? {
        val index = snapshotForTest.indexOf(id)
        if (index < 0) return null
        val pkg = try {
            resources.getResourcePackageName(id)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("engine.handleForTest", t)
            return null
        }
        if (!snapshotForTest.accepts(index, pkg)) return null
        if (guarded) guard.set(true)
        return try {
            replace(snapshotForTest, index, resources, density, night)
        } finally {
            if (guarded) guard.set(false)
        }
    }

    /** The replacement half of the hook; a miss path never calls this. */
    private fun replace(
        snap: Snapshot,
        index: Int,
        wrapper: Resources,
        density: Int,
        night: Boolean
    ): Drawable? {
        if (guard.get() == true) return null
        guard.set(true)
        return try {
            snap.newDrawable(index, wrapper, density, night)
        } catch (t: Throwable) {
            failures += 1
            pieceMisses.incrementAndGet(pieceIndexOf(snap.slotAt(index)))
            logOnce.once("ios-resource-replacement") {
                L.w("$TAG replacement failed: ${t.javaClass.simpleName}")
            }
            null
        } finally {
            guard.set(false)
        }
    }

    private fun nightOf(resources: Resources): Boolean = try {
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    } catch (t: Throwable) {
        IosV2Log.failureOnce("engine.night", t)
        false
    }

    // ----------------------------------------------------------------------------------- build

    /** A built map plus the B2 evidence computed during the same pass (never re-resolved later). */
    private class BuildResult(
        val snapshot: Snapshot,
        val coverage: List<IosPieceCoverage>,
        val buildMs: Long
    )

    private fun buildMap(
        context: android.content.Context,
        rom: RomAdapter,
        settings: ModuleSettings?
    ): BuildResult? {
        // The stock-intrinsic probes load through the same hook; on the building thread the guard
        // makes those loads return the stock drawable (other threads keep the published map).
        val wasGuarded = guard.get() == true
        guard.set(true)
        val startedAt = System.nanoTime()
        try {
            val resources = context.resources
            val densityDpi = resources.displayMetrics.densityDpi.let { if (it > 0) it else 160 }
            // R7 P5: seed the glyph ink from the bar foreground. If the system never pushes a tint
            // list/colour filter (some ROM leaves do not), a hard-coded white would vanish on a light
            // bar. The system's own tint still overrides this through setTintList/captureColorFilter.
            val defaultTint = BarTint.fgColor(context)
            val specs = IosResourceNames.specs(rom.id)
            val seen = HashSet<Int>(specs.size * 2)
            val resolved = ArrayList<ResolvedEntry>(specs.size)
            // B2: which specs became entries, so per-piece coverage is exact and needs no second pass.
            val resolvedSpecs = HashSet<IosResourceSpec>(specs.size)
            val drops = IosMapDrops(rom.id)
            var duplicates = 0
            var noIntrinsic = 0
            var hiddenNoIntrinsic = 0
            var hiddenNoRestorable = 0
            for (spec in specs) {
                val pkg = spec.packageName.ifBlank { rom.systemUiPackage }
                // rawId is DORMANT (R13) Motorola data; kept generic so a future route flip needs no
                // engine change.
                val id = if (spec.rawId != 0) {
                    spec.rawId
                } else {
                    try {
                        resources.getIdentifier(spec.name, "drawable", pkg)
                    } catch (t: Throwable) {
                        IosV2Log.failureOnce("engine.identifier.${spec.name}", t)
                        0
                    }
                }
                if (id == 0) {
                    drops.add(spec, IosMapDropReason.NO_ID)
                    continue
                }
                if (!seen.add(id)) {
                    duplicates++
                    drops.add(spec, IosMapDropReason.DUPLICATE)
                    continue
                }
                val slot = slotOf(spec)
                if (slot == null) {
                    drops.add(spec, IosMapDropReason.UNSUPPORTED_SLOT)
                    continue
                }
                val isHidden = slot is IosResourceSlot.Hidden
                val stock = try {
                    resources.getDrawable(id, null)
                } catch (t: Throwable) {
                    IosV2Log.failureOnce("engine.getDrawable.${spec.name}", t)
                    null
                }
                if (stock == null) {
                    drops.add(spec, IosMapDropReason.NO_DRAWABLE)
                    continue
                }
                val widthPx = stock.intrinsicWidth
                val heightPx = stock.intrinsicHeight
                if (widthPx <= 0 || heightPx <= 0) {
                    // Never guess a size: an id with no intrinsic would draw in the wrong box. The
                    // measured h/r passpoint badges are legitimately empty `<vector/>`s.
                    if (isHidden) hiddenNoIntrinsic++ else noIntrinsic++
                    drops.add(spec, IosMapDropReason.NO_INTRINSIC)
                    IosV2Log.branchOnce(
                        "engine.noIntrinsic",
                        "id has no intrinsic size - entry skipped"
                    )
                    continue
                }
                val stockState = if (isHidden) stock.constantState else null
                if (isHidden && !hiddenCanRestore(stockState)) {
                    // A gate needs the stock state to put the badge back when the toggle is off. With
                    // no state it would be 0x0 in BOTH states, so the default (off) would change.
                    hiddenNoRestorable++
                    drops.add(spec, IosMapDropReason.NO_STOCK_STATE)
                    IosV2Log.branchOnce(
                        "engine.hiddenNoRestore",
                        "no stock state - hide-extras entry stays stock"
                    )
                    continue
                }
                resolved.add(
                    ResolvedEntry(
                        id = id,
                        slot = slot,
                        widthDp = widthPx * 160f / densityDpi,
                        heightDp = heightPx * 160f / densityDpi,
                        packageName = pkg,
                        // R9: the drawable stays identity. IosSlotLayout owns the per-icon scale by
                        // resizing the live stock leaf (`iosWifiScale`/`iosCellScale` were baked here
                        // before, so a slider tick never reached an already-installed view).
                        transform = IosUserTransform.IDENTITY,
                        stockState = stockState,
                        stockWidthPx = if (isHidden) widthPx else 0,
                        stockHeightPx = if (isHidden) heightPx else 0
                    )
                )
                resolvedSpecs.add(spec)
            }
            val built = buildSnapshot(resolved, densityDpi, defaultTint)
            val buildMs = (System.nanoTime() - startedAt) / 1_000_000L
            L.i(
                "iOS v2 resource map: family=${rom.id} specs=${specs.size} " +
                    "resolved=${resolved.size} duplicates=$duplicates noIntrinsic=$noIntrinsic " +
                    "hiddenNoIntrinsic=$hiddenNoIntrinsic hiddenNoRestorable=$hiddenNoRestorable " +
                    "buildMs=$buildMs"
            )
            // R4: name every dropped spec once per process, on the non-OxygenOS families only. The
            // collector itself (`logEntries`) owns the gate, so this loop can never log an OxygenOS
            // drop; the summary line above stays byte-identical for every family.
            for (drop in drops.logEntries()) {
                logOnce.once("ios-map-drop:${drop.family}:${drop.name}:${drop.reason}") {
                    L.i(formatIosMapDrop(drop))
                }
            }
            return BuildResult(built, computePieceCoverage(specs, resolvedSpecs), buildMs)
        } catch (t: Throwable) {
            logOnce.once("ios-resource-build") {
                L.w("$TAG map build failed: ${t.javaClass.simpleName}")
            }
            return null
        } finally {
            guard.set(wasGuarded)
        }
    }

    private fun slotOf(spec: IosResourceSpec): IosResourceSlot? = when (spec.kind) {
        IosResourceKind.WIFI -> IosResourceSlot.Wifi(spec.value)
        IosResourceKind.CELL -> IosResourceSlot.Cell(spec.value)
        IosResourceKind.LABEL ->
            if (IosGeometry.DataLabel.supports(spec.label)) {
                IosResourceSlot.Label(spec.label)
            } else {
                IosV2Log.branchOnce("engine.label.${spec.label}", "unsupported label - entry skipped")
                null
            }
        IosResourceKind.HIDDEN -> IosResourceSlot.Hidden
    }

    // ------------------------------------------------------------------------------ diagnostics

    override fun diagnosticLines(): List<String> {
        val snap = snapshot
        val base = "$TAG: family=${activeFamily.ifEmpty { "none" }} active=${snap != null} " +
            "installed=$installedFlag calls=$calls hits=$hits misses=$misses " +
            "badPackage=$badPackage failures=$failures map=${snap?.size ?: 0} " +
            "maxMissNs=$maxMissNanos"
        // R17C: the first line is byte-identical; the miss table adds one line only on a non-OxygenOS
        // family that actually recorded a name. An inactive/empty table is exactly one line.
        if (activeFamily.isEmpty() || activeFamily == "oxygenos") return listOf(base)
        val top = missNames?.top(8).orEmpty()
        return if (top.isEmpty()) listOf(base) else listOf(base, missTopLine(activeFamily, top))
    }

    /**
     * R17C: true when [id] is in the live map for [pkg] (or when no package was read). The reload
     * pass uses this to decide whether an icon's resource is the one the map already replaced.
     */
    internal fun isMappedForRefresh(id: Int, pkg: String?): Boolean {
        val snap = snapshot ?: return false
        val index = snap.indexOf(id)
        if (index < 0) return false
        return pkg == null || snap.accepts(index, pkg)
    }

    /** Coverage lines for a build/report; a piece with zero resolved (or a runtime miss) gets a miss line. */
    internal fun coverageReport(
        coverage: List<IosPieceCoverage>,
        family: String,
        misses: LongArray
    ): List<String> {
        if (coverage.isEmpty()) return emptyList()
        val out = ArrayList<String>(coverage.size)
        for (piece in coverage) {
            out.add(coverageLine(family, piece))
            val index = IOS_PIECES.indexOf(piece.piece)
            val runtimeMiss = index >= 0 && index < misses.size && misses[index] > 0L
            // R17C: a partial piece is named on non-OxygenOS families (Samsung 17/30 cell), while the
            // frozen OxygenOS report keeps its old zero-resolved/runtime-miss rule.
            val partial = iosMapDropsEnabled(family) && piece.resolved < piece.total
            if (piece.resolved == 0 || runtimeMiss || partial) {
                out.add(missLine(family, piece.piece, piece.tried))
            }
        }
        return out
    }

    // --------------------------------------------------------------------------------- snapshot

    /**
     * An immutable published map.
     *
     * [ids] is sorted for `Arrays.binarySearch`; [entries] is aligned with it. Entries carry their own
     * package because the Motorola table mixes framework `android` ids with SystemUI names, even
     * though the live v2 families are single-package.
     */
    internal class Snapshot(
        internal val ids: IntArray,
        internal val entries: Array<ResolvedEntry>,
        internal val baseDensity: Int,
        private val defaultTint: Int = 0xFFFFFFFF.toInt()
    ) {
        val size: Int get() = ids.size

        /** R8: resolved hide-extras leaves (each mints a gate drawable, never a [IosGlyphDrawable]). */
        val hiddenCount: Int = entries.count { it.slot is IosResourceSlot.Hidden }

        val packageName: String
            get() = entries.firstOrNull()?.packageName ?: IosResourceSpec.DEFAULT_PACKAGE

        private val cache = LongSparseArray<Drawable.ConstantState>()
        private val cacheLock = Any()

        /** Base-density states are created once and shared; [newDrawable] hands out fresh drawables. */
        private val prebuilt: Array<Drawable.ConstantState> =
            Array(entries.size) { stateFor(entries[it], baseDensity) }

        /** Allocation-free id lookup (`java.util.Arrays.binarySearch` on a primitive array). */
        fun indexOf(id: Int): Int = Arrays.binarySearch(ids, id)

        fun packageAt(index: Int): String = entries[index].packageName

        /** True when [pkg] is the package this id was measured in (R5 id-collision guard). */
        fun accepts(index: Int, pkg: String): Boolean = pkg == packageAt(index)

        fun slotAt(index: Int): IosResourceSlot = entries[index].slot

        internal fun kindAt(index: Int): IosIconKind =
            IosResourceEngine.drawableKind(entries[index].slot)

        /**
         * A fresh drawable for [index] at the requested density. The density argument comes from the
         * hooked `loadDrawable` call (0 means "use the Resources density"). Night only separates cache
         * entries; our geometry is colour-independent.
         */
        internal fun newDrawable(
            index: Int,
            resources: Resources,
            densityArg: Int,
            night: Boolean
        ): Drawable {
            val density = effectiveDensity(resources, densityArg)
            if (density == baseDensity) return prebuilt[index].newDrawable(resources, null)
            val key = IosResourceEngine.cacheKey(ids[index], density, night)
            val state = synchronized(cacheLock) {
                val cached = cache.get(key)
                if (cached != null) {
                    cached
                } else {
                    val created = stateFor(entries[index], density)
                    if (cache.size() >= IosResourceEngine.MAX_CACHE) cache.clear()
                    cache.put(key, created)
                    created
                }
            }
            return state.newDrawable(resources, null)
        }

        private fun effectiveDensity(resources: Resources, densityArg: Int): Int {
            if (densityArg > 0) return densityArg
            val dpi = try {
                resources.displayMetrics.densityDpi
            } catch (t: Throwable) {
                IosV2Log.failureOnce("engine.displayDensity", t)
                0
            }
            return if (dpi > 0) dpi else baseDensity.coerceAtLeast(1)
        }

        private fun stateFor(entry: ResolvedEntry, density: Int): Drawable.ConstantState {
            // A hide-extras leaf is a gate, not a glyph: 0x0 while hidden and the captured stock
            // drawable when the toggle is off. CachedState coerces to >=1px and would leave a gap.
            if (entry.slot is IosResourceSlot.Hidden) {
                // The captured stock px were measured at the map's base density; scale them for the
                // density actually requested so a 2x load restores a 2x stock drawable.
                val dpi = if (density > 0) density else baseDensity.coerceAtLeast(1)
                val widthPx = if (entry.stockWidthPx > 0) {
                    (entry.stockWidthPx * dpi / baseDensity.toFloat()).roundToInt().coerceAtLeast(1)
                } else {
                    entry.stockWidthPx
                }
                val heightPx = if (entry.stockHeightPx > 0) {
                    (entry.stockHeightPx * dpi / baseDensity.toFloat()).roundToInt().coerceAtLeast(1)
                } else {
                    entry.stockHeightPx
                }
                return IosExtras.gateState(entry.stockState, widthPx, heightPx)
            }
            val dpi = if (density > 0) density else baseDensity.coerceAtLeast(1)
            val widthPx = (entry.widthDp * dpi / 160f).roundToInt().coerceAtLeast(1)
            val heightPx = (entry.heightDp * dpi / 160f).roundToInt().coerceAtLeast(1)
            return IosResourceEngine.CachedState(
                IosResourceEngine.drawableKind(entry.slot),
                IosResourceEngine.visualFor(entry.slot, defaultTint).copy(userTransform = entry.transform),
                widthPx,
                heightPx
            )
        }
    }

    private fun visualFor(slot: IosResourceSlot, tint: Int): IosVisual = when (slot) {
        is IosResourceSlot.Wifi -> IosVisual(
            tint = tint,
            wifiLevel = slot.dots.toFloat()
        )
        is IosResourceSlot.Cell -> IosVisual(
            tint = tint,
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = slot.dots.toFloat()
        )
        is IosResourceSlot.Label -> IosVisual(
            tint = tint,
            wifiSlot = IosWifiSlot.DataType(slot.text)
        )
        // Never drawn: a HIDDEN entry's state is the gate, not a glyph.
        is IosResourceSlot.Hidden -> IosVisual(tint = tint, wifiLevel = 0f)
    }

    private fun drawableKind(slot: IosResourceSlot): IosIconKind = when (slot) {
        is IosResourceSlot.Wifi, is IosResourceSlot.Label, is IosResourceSlot.Hidden -> IosIconKind.WIFI
        is IosResourceSlot.Cell -> IosIconKind.CELL
    }

    private fun cacheKey(id: Int, density: Int, night: Boolean): Long =
        (id.toLong() shl 24) or (density.toLong() shl 1) or (if (night) 1L else 0L)

    /** A constant state that mints a fresh [IosGlyphDrawable] (a shared Drawable would be illegal). */
    private class CachedState(
        private val kind: IosIconKind,
        private val visual: IosVisual,
        private val widthPx: Int,
        private val heightPx: Int
    ) : Drawable.ConstantState() {
        override fun newDrawable(): Drawable = IosDrawables.create(
            kind,
            visual,
            widthPx,
            heightPx,
            // R9: the leaf is sized to the artboard by IosSlotLayout; a relative inset would shrink
            // the glyph inside the new box (and was the second half of the slider bug).
            relativeSize = false,
            captureColorFilter = true
        )

        override fun getChangingConfigurations(): Int = 0
    }
}

/** Builds the immutable [IosResourceEngine.Snapshot] from resolved entries (also the test seam). */
internal fun buildSnapshot(
    entries: List<ResolvedEntry>,
    baseDensity: Int,
    defaultTint: Int = 0xFFFFFFFF.toInt()
): IosResourceEngine.Snapshot {
    val sorted = entries.sortedBy { it.id }
    val ids = IntArray(sorted.size) { sorted[it].id }
    return IosResourceEngine.Snapshot(
        ids,
        sorted.toTypedArray(),
        if (baseDensity > 0) baseDensity else 160,
        defaultTint
    )
}
