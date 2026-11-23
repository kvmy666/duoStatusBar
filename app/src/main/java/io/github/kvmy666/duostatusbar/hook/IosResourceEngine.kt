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
import kotlin.math.roundToInt

/** Which iOS glyph a mapped stock resource becomes. */
internal sealed class IosResourceSlot {
    data class Wifi(val dots: Int) : IosResourceSlot()
    data class Cell(val dots: Int) : IosResourceSlot()
    data class Label(val text: String) : IosResourceSlot()
}

/** One resolved stock id: the slot it becomes, its stock intrinsic size (dp) and its package. */
internal data class ResolvedEntry(
    val id: Int,
    val slot: IosResourceSlot,
    val widthDp: Float,
    val heightDp: Float,
    val packageName: String,
    /**
     * R7 P4: the user's per-icon offset/scale, baked once per map build from [ModuleSettings].
     * [IosUserTransform.IDENTITY] is exactly the pre-setting pixels (rule 6).
     */
    val transform: IosUserTransform = IosUserTransform.IDENTITY
)

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

    /** `tint = WHITE`: the system's ImageView/colorFilter supplies the real colour (M17). */
    private const val WHITE = 0xFFFFFFFF.toInt()

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

    val isActive: Boolean get() = installedFlag && snapshot != null

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
     * Publishes a map for [rom]. No-op (and deactivates) unless the hook is installed and the family
     * is a v2 engine family. With [synchronous] the map is built inline on the boot path before the
     * first inflation; otherwise a single daemon thread builds it and a stale build is discarded.
     *
     * [settings] supplies each glyph's per-icon offset/scale (R7 P4). It is read at build time, so a
     * settings change rebuilds the map through the normal per-apply [activate]; null is identity.
     */
    fun activate(
        context: android.content.Context,
        rom: RomAdapter,
        settings: ModuleSettings? = null,
        synchronous: Boolean = false
    ) {
        var gen = 0L
        synchronized(stateLock) {
            if (!installedFlag || !IosV2Profile.engineFamily(rom.id)) {
                deactivate()
                return
            }
            gen = ++generation
            activeFamily = rom.id
            resetCountersLocked()
        }
        val work = {
            val built = buildMap(context, rom, settings)
            synchronized(stateLock) {
                if (gen == generation) snapshot = built
            }
        }
        if (synchronous) {
            work()
        } else {
            Thread({ work() }, "duo-ios-res").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Restores stock behaviour: the next load returns the stock drawable. */
    fun deactivate() {
        synchronized(stateLock) {
            generation++
            snapshot = null
            activeFamily = ""
        }
    }

    private fun resetCountersLocked() {
        calls = 0L
        hits = 0L
        misses = 0L
        badPackage = 0
        failures = 0
        maxMissNanos = 0L
    }

    // ------------------------------------------------------------------------------------ hook

    private fun onLoadDrawable(param: XC_MethodHook.MethodHookParam) {
        try {
            val snap = snapshot ?: return
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
                return
            }
            val wrapper = args[0] as? Resources ?: return
            val pkg = try {
                wrapper.getResourcePackageName(id)
            } catch (_: Throwable) {
                badPackage += 1
                return
            }
            if (!snap.accepts(index, pkg)) {
                badPackage += 1
                return
            }
            if (guard.get() == true) return
            guard.set(true)
            try {
                val density = args[3] as? Int ?: 0
                param.result = snap.newDrawable(index, wrapper, density, nightOf(wrapper))
                hits += 1L
            } catch (t: Throwable) {
                failures += 1
                logOnce.once("ios-resource-replacement") {
                    L.w("$TAG replacement failed: ${t.javaClass.simpleName}")
                }
            } finally {
                guard.set(false)
            }
        } catch (_: Throwable) {
            // Whole body guarded: any unexpected failure leaves the stock load path untouched.
        }
    }

    private fun nightOf(resources: Resources): Boolean = try {
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    } catch (_: Throwable) {
        false
    }

    // ----------------------------------------------------------------------------------- build

    /** Test seam: build a map without publishing or activating it. */
    internal fun buildForTest(
        context: android.content.Context,
        rom: RomAdapter,
        settings: ModuleSettings? = null
    ): Snapshot? = buildMap(context, rom, settings)

    private fun buildMap(
        context: android.content.Context,
        rom: RomAdapter,
        settings: ModuleSettings?
    ): Snapshot? {
        // The stock-intrinsic probes load through the same hook; on the building thread the guard
        // makes those loads return the stock drawable (other threads keep the published map).
        val wasGuarded = guard.get() == true
        guard.set(true)
        val startedAt = System.nanoTime()
        try {
            val resources = context.resources
            val densityDpi = resources.displayMetrics.densityDpi.let { if (it > 0) it else 160 }
            val specs = IosResourceNames.specs(rom.id)
            val seen = HashSet<Int>(specs.size * 2)
            val resolved = ArrayList<ResolvedEntry>(specs.size)
            var duplicates = 0
            var noIntrinsic = 0
            for (spec in specs) {
                val pkg = spec.packageName.ifBlank { rom.systemUiPackage }
                val id = if (spec.rawId != 0) {
                    spec.rawId
                } else {
                    try {
                        resources.getIdentifier(spec.name, "drawable", pkg)
                    } catch (_: Throwable) {
                        0
                    }
                }
                if (id == 0) continue
                if (!seen.add(id)) {
                    duplicates++
                    continue
                }
                val slot = slotOf(spec) ?: continue
                val stock = try {
                    resources.getDrawable(id, null)
                } catch (_: Throwable) {
                    null
                } ?: continue
                val widthPx = stock.intrinsicWidth
                val heightPx = stock.intrinsicHeight
                if (widthPx <= 0 || heightPx <= 0) {
                    // Never guess a size: an id with no intrinsic would draw in the wrong box.
                    noIntrinsic++
                    continue
                }
                resolved.add(
                    ResolvedEntry(
                        id = id,
                        slot = slot,
                        widthDp = widthPx * 160f / densityDpi,
                        heightDp = heightPx * 160f / densityDpi,
                        packageName = pkg,
                        transform = userTransformFor(settings, slot, widthPx, heightPx)
                    )
                )
            }
            val built = buildSnapshot(resolved, densityDpi)
            val buildMs = (System.nanoTime() - startedAt) / 1_000_000L
            L.i(
                "iOS v2 resource map: family=${rom.id} specs=${specs.size} " +
                    "resolved=${resolved.size} duplicates=$duplicates noIntrinsic=$noIntrinsic " +
                    "buildMs=$buildMs"
            )
            return built
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
            if (IosGeometry.DataLabel.supports(spec.label)) IosResourceSlot.Label(spec.label) else null
    }

    /**
     * The per-user transform for one entry, computed from the stock intrinsic size the map already
     * measured (R7 P4). The identity value is returned for a null settings snapshot or any failure,
     * so a broken provider row can never move a glyph (rule 6, C11).
     */
    private fun userTransformFor(
        settings: ModuleSettings?,
        slot: IosResourceSlot,
        widthPx: Int,
        heightPx: Int
    ): IosUserTransform {
        if (settings == null) return IosUserTransform.IDENTITY
        return try {
            val kind = drawableKind(slot)
            val innerWidth = IosGeometry.Relative.sizeFor(
                kind,
                widthPx,
                heightPx,
                dualCell = false
            ).first
            settings.iosTransform(kind, innerWidth.toFloat())
        } catch (_: Throwable) {
            IosUserTransform.IDENTITY
        }
    }

    // ------------------------------------------------------------------------------ diagnostics

    override fun diagnosticLines(): List<String> {
        val snap = snapshot
        return listOf(
            "$TAG: family=${activeFamily.ifEmpty { "none" }} active=${snap != null} " +
                "installed=$installedFlag calls=$calls hits=$hits misses=$misses " +
                "badPackage=$badPackage failures=$failures map=${snap?.size ?: 0} " +
                "maxMissNs=$maxMissNanos"
        )
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
        internal val baseDensity: Int
    ) {
        val size: Int get() = ids.size

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
            } catch (_: Throwable) {
                0
            }
            return if (dpi > 0) dpi else baseDensity.coerceAtLeast(1)
        }

        private fun stateFor(entry: ResolvedEntry, density: Int): Drawable.ConstantState {
            val dpi = if (density > 0) density else baseDensity.coerceAtLeast(1)
            val widthPx = (entry.widthDp * dpi / 160f).roundToInt().coerceAtLeast(1)
            val heightPx = (entry.heightDp * dpi / 160f).roundToInt().coerceAtLeast(1)
            return IosResourceEngine.CachedState(
                IosResourceEngine.drawableKind(entry.slot),
                IosResourceEngine.visualFor(entry.slot).copy(userTransform = entry.transform),
                widthPx,
                heightPx
            )
        }
    }

    private fun visualFor(slot: IosResourceSlot): IosVisual = when (slot) {
        is IosResourceSlot.Wifi -> IosVisual(
            part = IosPart.WIFI,
            tint = WHITE,
            wifiLevel = slot.dots.toFloat()
        )
        is IosResourceSlot.Cell -> IosVisual(
            part = IosPart.CELL,
            tint = WHITE,
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = slot.dots.toFloat()
        )
        is IosResourceSlot.Label -> IosVisual(
            part = IosPart.WIFI,
            tint = WHITE,
            wifiSlot = IosWifiSlot.DataType(slot.text)
        )
    }

    private fun drawableKind(slot: IosResourceSlot): IosIconKind = when (slot) {
        is IosResourceSlot.Wifi, is IosResourceSlot.Label -> IosIconKind.WIFI
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
            relativeSize = true,
            captureColorFilter = true
        )

        override fun getChangingConfigurations(): Int = 0
    }
}

/** Builds the immutable [IosResourceEngine.Snapshot] from resolved entries (also the test seam). */
internal fun buildSnapshot(
    entries: List<ResolvedEntry>,
    baseDensity: Int
): IosResourceEngine.Snapshot {
    val sorted = entries.sortedBy { it.id }
    val ids = IntArray(sorted.size) { sorted[it].id }
    return IosResourceEngine.Snapshot(ids, sorted.toTypedArray(), if (baseDensity > 0) baseDensity else 160)
}
