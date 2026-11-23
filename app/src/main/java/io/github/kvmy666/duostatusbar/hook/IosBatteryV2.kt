package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomResources
import java.util.Collections
import java.util.WeakHashMap

/**
 * R7 P2: the v2 battery owner.
 *
 * The status bar's battery is code-drawn on every mapped ROM (`.pm/R7-P0-map.md:13` correction 0.7),
 * so the resource map cannot produce the iOS battery. This object replaces the OEM icon drawable on
 * the stock battery `ImageView` in place and re-asserts it whenever the OEM binder pushes its own
 * drawable back:
 *
 *  - OxygenOS: `StatBatteryMeterView` (constructors + `onFinishInflate` find the icon view, which is
 *    named `battery_icon_view` and is not inflated in the constructor) and the `BatteryViewBinder`
 *    push methods, which re-set the OEM drawable after our update (`.pm/R7-P0-map.md:99-115`).
 *  - Samsung/Nothing/Motorola: the generic `com.android.systemui.battery.BatteryMeterView`, whose
 *    `mBatteryIconView` field is the leaf (`.pm/R7-P0-map.md:228`, `:244`, `:262`).
 *
 * Exactly one owner per icon: this object never installs a controller while inactive, so the stock
 * battery is untouched until [activate] and restored exactly on [deactivate]. Every hook is optional
 * and fail-silent; a missing class or method only increments a counter (AGENTS rule 5).
 */
internal object IosBatteryV2 : IosDiagnosticSource {

    private const val TAG = "ios v2 battery"

    /** Measured OxygenOS battery view (16.00.14 smali; both class names are in 16.99.12 dex). */
    private const val OPLUS_VIEW_CLASS =
        "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView"

    /** Measured OxygenOS binder that re-pushes the OEM icon drawable. */
    private const val OPLUS_BINDER_CLASS =
        "com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder"

    /** The AOSP-shaped battery view used by Samsung/Nothing/Motorola (and dormant elsewhere). */
    private const val AOSP_VIEW_CLASS = "com.android.systemui.battery.BatteryMeterView"

    /** OxygenOS icon leaf name; the numeric id drifts between builds, so it is resolved by name. */
    private const val OPLUS_ICON_NAME = "battery_icon_view"

    /** OxygenOS bolt leaves; hidden while our glyph draws the bolt (`A-oxygenos-names.md:370-404`). */
    private val OPLUS_BOLT_NAMES = arrayOf("battery_charge_icon", "battery_charge_reverse")

    /** OxygenOS binder pushes that overwrite the icon drawable after our update. */
    private val OPLUS_BINDER_METHODS = arrayOf(
        "bind\$updateBatteryContentView",
        "bind\$updateBatteryIconStyle",
        "bind\$hideBatteryIcon",
        "bind\$updateChargingView"
    )

    private const val LOW_THRESHOLD_RESOURCE = "config_lowBatteryWarningLevel"

    private val logOnce = LogOnce()

    @Volatile
    private var installedFlag = false

    /** True once at least one battery view class was resolved in this process. */
    val installed: Boolean get() = installedFlag

    private var installDone = false

    // The OxygenOS view class, used only to recognise a hooked instance in a binder argument.
    @Volatile
    private var oplusClass: Class<*>? = null

    private val stateLock = Any()

    @Volatile
    private var active = false

    @Volatile
    private var contextRef: Context? = null

    @Volatile
    private var romRef: RomAdapter? = null

    private var percentInside = false
    private var lowThreshold = IosMapping.FALLBACK_LOW_THRESHOLD
    private var boltIds = IntArray(0)

    /** R7 P4: the settings snapshot each new controller bakes its per-icon transform from. */
    private var settingsRef: ModuleSettings? = null

    /** R7 P4: the computed battery transform fallback when no settings snapshot was supplied. */
    private var transformRef: IosUserTransform = IosUserTransform.IDENTITY

    /** The bar colour last seen in `onDarkChanged`, so a later controller starts with it (M17). */
    @Volatile
    private var lastTint = 0

    private var batteryIconId = 0
    private var batteryIconPackage = ""

    /**
     * Every battery view the hooks have seen, even while inactive.
     *
     * Weak keys on purpose: the bar, keyguard and shade each own a view that the system may drop;
     * a dropped view needs no restore, and [activate] adopts whatever is still alive.
     */
    private val seen: MutableMap<View, Boolean> =
        Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    /** The active controller per view; empty while inactive (one owner per icon). */
    private val controllers: MutableMap<View, Controller> =
        Collections.synchronizedMap(WeakHashMap<View, Controller>())

    private var receiver: BroadcastReceiver? = null
    private var receiverContext: Context? = null

    // Diagnostics are approximate on purpose; races are acceptable and the hot path must not throw.
    private var installs = 0
    private var reasserts = 0
    private var failures = 0
    private var lastLevel = -1
    private var lastState = "NONE"

    private var lastReading: IosBatteryReading? = null
    private var lastSaver = false

    val isActive: Boolean get() = active

    // ---------------------------------------------------------------------------------- install

    /**
     * Installs the process-wide battery hooks once. Never throws: a missing class leaves the stock
     * battery alive and counts one failure, and the diagnostic source is registered either way.
     */
    @Synchronized
    fun install(classLoader: ClassLoader) {
        if (installDone) return
        installDone = true
        IosDiagnostics.register(this)
        try {
            val oplus = findClass(OPLUS_VIEW_CLASS, classLoader)
            val oplusHooked = if (oplus != null) {
                oplusClass = oplus
                hookOplusView(oplus)
            } else {
                countFailure()
                false
            }

            val binder = findClass(OPLUS_BINDER_CLASS, classLoader)
            if (binder != null) hookOplusBinder(binder, oplus) else countFailure()

            val aosp = findClass(AOSP_VIEW_CLASS, classLoader)
            val aospHooked = if (aosp != null) {
                hookAospView(aosp)
            } else {
                countFailure()
                false
            }

            // R7 P5: a resolved class is not a working hook. V2 may only be selected when at least
            // one constructor hook was actually installed; otherwise the host stays the owner.
            installedFlag = batteryHookInstalled(oplusHooked, aospHooked)
            if (!installedFlag) {
                logOnce.once("ios-battery-no-class") {
                    L.w("$TAG no battery view hook installed - battery stays stock")
                }
            } else {
                L.i("install step: ios-battery-v2")
            }
        } catch (t: Throwable) {
            installedFlag = false
            countFailure()
            logOnce.once("ios-battery-install-failed") {
                L.w("$TAG install failed: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun findClass(name: String, classLoader: ClassLoader): Class<*>? = try {
        XposedHelpers.findClass(name, classLoader)
    } catch (_: Throwable) {
        null
    }

    private fun hookOplusView(cls: Class<*>): Boolean {
        val capture = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                onBatteryViewHook(param)
            }
        }
        val constructors = hookSafely("oplus-constructors") {
            XposedBridge.hookAllConstructors(cls, capture)
        }
        // The icon view does not exist in the constructor: `onFinishInflate` is the retry point.
        hookSafely("oplus-onFinishInflate") { XposedBridge.hookAllMethods(cls, "onFinishInflate", capture) }
        hookSafely("oplus-onDarkChanged") {
            XposedBridge.hookAllMethods(cls, "onDarkChanged", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    onDark(param)
                }
            })
        }
        return constructors
    }

    private fun hookOplusBinder(cls: Class<*>, oplus: Class<*>?) {
        if (oplus == null) return
        for (name in OPLUS_BINDER_METHODS) {
            hookSafely("oplus-binder-$name") {
                XposedBridge.hookAllMethods(cls, name, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.args.firstOrNull { it != null && oplus.isInstance(it) }
                                as? View ?: return
                            reassertFor(view)
                        } catch (_: Throwable) {
                            countFailure()
                        }
                    }
                })
            }
        }
    }

    private fun hookAospView(cls: Class<*>): Boolean {
        val capture = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                onBatteryViewHook(param)
            }
        }
        val constructors = hookSafely("aosp-constructors") {
            XposedBridge.hookAllConstructors(cls, capture)
        }
        hookSafely("aosp-onAttachedToWindow") {
            XposedBridge.hookAllMethods(cls, "onAttachedToWindow", capture)
        }
        hookSafely("aosp-onConfigurationChanged") {
            XposedBridge.hookAllMethods(cls, "onConfigurationChanged", capture)
        }
        // Present on Samsung; absent on some builds, which `hookAllMethods` tolerates.
        hookSafely("aosp-onDarkChanged") {
            XposedBridge.hookAllMethods(cls, "onDarkChanged", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    onDark(param)
                }
            })
        }
        return constructors
    }

    private inline fun hookSafely(what: String, block: () -> Collection<*>): Boolean {
        return try {
            block().isNotEmpty()
        } catch (t: Throwable) {
            countFailure()
            logOnce.once("ios-battery-hook-$what") {
                L.w("$TAG hook $what failed: ${t.javaClass.simpleName}")
            }
            false
        }
    }

    /** A battery view appeared/changed: record it and install or repair, never throw into SystemUI. */
    private fun onBatteryViewHook(param: XC_MethodHook.MethodHookParam) {
        try {
            (param.thisObject as? View)?.let { controllerFor(it) }
        } catch (_: Throwable) {
            countFailure()
        }
    }

    private fun onDark(param: XC_MethodHook.MethodHookParam) {
        try {
            val color = BarTint.pickTint(param.args)
            if (color == 0) return
            lastTint = color
            (param.thisObject as? View)?.let { controllers[it]?.setTint(color) }
        } catch (_: Throwable) {
            countFailure()
        }
    }

    // --------------------------------------------------------------------------------- activate

    /**
     * iOS owns the battery on [rom]. Requires the hook and a v2 battery family; anything else
     * deactivates, so a stale activation can never leave a view replaced.
     *
     * [settings] carries the user's per-icon offset/scale (R7 P4) and takes precedence over
     * [transform], which is the caller-computed fallback; both default to identity.
     */
    @Synchronized
    fun activate(
        context: Context,
        rom: RomAdapter,
        percentInside: Boolean,
        lowThreshold: Int,
        settings: ModuleSettings? = null,
        transform: IosUserTransform = IosUserTransform.IDENTITY
    ) {
        if (!installedFlag || !IosV2Profile.batteryFamily(rom.id)) {
            deactivate()
            return
        }
        val app = context.applicationContext ?: context
        this.contextRef = app
        this.romRef = rom
        this.percentInside = percentInside
        this.lowThreshold = resolveLowThreshold(app, lowThreshold)
        this.settingsRef = settings
        this.transformRef = transform
        this.batteryIconId = 0
        this.batteryIconPackage = ""
        this.boltIds = resolveBoltIds(app, rom)
        this.lastTint = 0
        installs = 0
        reasserts = 0
        // `failures` is process-lifetime on purpose: a class/method that was absent at install time
        // must stay visible in the dump after the first activation resets the volume counters.
        // R7 P5: an in-place re-activation must not install from the previous session's reading if
        // the sticky read fails now. Existing controllers keep their last level via update(null, ...);
        // a new controller stays stock until a real reading arrives.
        lastReading = null
        lastSaver = false
        active = true
        registerReceiver(app)
        // R7 P5: the sticky read decides whether an install is allowed at all (a null reading keeps
        // the stock icon), so read first, install the recorded views, then push the level.
        refresh()
        installRecordedViews()
        refresh()
    }

    /**
     * The framework's own low-battery warning level, validated by [IosMapping.lowThreshold].
     *
     * An unreadable config falls back to the caller's value; both go through the same validator, so
     * an out-of-range value can never become a threshold.
     */
    private fun resolveLowThreshold(context: Context, caller: Int): Int {
        val raw = try {
            val id = context.resources.getIdentifier(LOW_THRESHOLD_RESOURCE, "integer", "android")
            if (id == 0) null else context.resources.getInteger(id)
        } catch (_: Throwable) {
            null
        }
        val resolved = IosMapping.lowThreshold(raw ?: caller)
        if (resolved.fallback) {
            logOnce.once("ios-battery-low-fallback") {
                L.i("$TAG low threshold unreadable - using ${resolved.value}")
            }
        }
        return resolved.value
    }

    private fun resolveBoltIds(context: Context, rom: RomAdapter): IntArray {
        if (rom.id != "oxygenos") return IntArray(0)
        val ids = ArrayList<Int>(OPLUS_BOLT_NAMES.size)
        for (name in OPLUS_BOLT_NAMES) {
            val id = RomResources.id(context, rom, name)
            if (id != 0) ids.add(id)
        }
        return ids.toIntArray()
    }

    private fun registerReceiver(context: Context) {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val next = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                refresh()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    next,
                    filter,
                    null,
                    Handler(Looper.getMainLooper()),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(next, filter, null, Handler(Looper.getMainLooper()))
            }
            receiver = next
            receiverContext = context
        } catch (_: Throwable) {
            countFailure()
            receiver = null
            receiverContext = null
        }
    }

    private fun installRecordedViews() {
        val views = synchronized(seen) { seen.keys.toList() }
        for (view in views) {
            controllerFor(view)
        }
    }

    /** Restores every live battery icon, unregisters the receiver and drops the controllers. */
    @Synchronized
    fun deactivate() {
        active = false
        unregisterReceiver()
        val live = synchronized(controllers) { controllers.values.toList() }
        controllers.clear()
        for (controller in live) {
            try {
                controller.restore()
            } catch (_: Throwable) {
                countFailure()
            }
        }
        contextRef = null
        romRef = null
        settingsRef = null
        transformRef = IosUserTransform.IDENTITY
        lastTint = 0
        batteryIconId = 0
        batteryIconPackage = ""
        boltIds = IntArray(0)
        // A fresh activation must read the sticky state again; a stale reading from the previous
        // session could otherwise install a glyph before the bar has a level.
        lastReading = null
        lastSaver = false
    }

    private fun unregisterReceiver() {
        val current = receiver
        val context = receiverContext
        receiver = null
        receiverContext = null
        if (current != null && context != null) {
            try {
                context.unregisterReceiver(current)
            } catch (_: Throwable) {
            }
        }
    }

    // ---------------------------------------------------------------------------------- refresh

    /** Re-reads the sticky battery broadcast and the saver state, then updates every controller. */
    fun refresh() {
        if (!active) return
        val context = contextRef ?: return
        val reading = readStickyBattery(context)
        val saver = try {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isPowerSaveMode ?: false
        } catch (_: Throwable) {
            false
        }
        if (reading == null) return
        lastReading = reading
        lastSaver = saver
        lastLevel = reading.level
        lastState = IosMapping.batteryState(
            reading.level, lowThreshold, saver, reading.plugged, reading.status
        ).name
        val live = synchronized(controllers) { controllers.values.toList() }
        for (controller in live) {
            try {
                controller.update(reading, saver)
            } catch (_: Throwable) {
                countFailure()
            }
        }
        // R7 P5: a view recorded while the sticky read was missing was deliberately left stock.
        // Now that a reading exists, the install it refused is retried.
        if (lastReading != null) installRecordedViews()
    }

    // ------------------------------------------------------------------------------ controllers

    /**
     * Records [view] and, while active, installs (or retries) its controller.
     *
     * Called from constructors and `onFinishInflate`; the first call often sees a view whose icon child
     * is not inflated yet, so a null image is not an error. The next callback retries.
     */
    private fun controllerFor(view: View): Controller? {
        synchronized(seen) { seen[view] = true }
        val state = synchronized(stateLock) {
            if (!active) return null
            val ctx = contextRef ?: return null
            val rom = romRef ?: return null
            ActiveState(
                context = ctx,
                rom = rom,
                bolts = boltIds,
                percentInside = percentInside,
                lowThreshold = lowThreshold,
                reading = lastReading,
                saver = lastSaver,
                tint = lastTint,
                settings = settingsRef,
                transform = transformRef
            )
        }
        // R7 P5: never install an invisible glyph. A missing sticky reading leaves the stock icon.
        if (state.reading == null) return null

        // One synchronized block for the check-and-create, so two hook callbacks cannot both install
        // a controller for the same view (the loser would overwrite the winner's original drawable).
        return synchronized(controllers) {
            val existing = controllers[view]
            if (existing != null) {
                // R7 P5: the constructor values are no longer the live settings; apply them on every
                // re-activation/apply so a scale/toggle change reaches an already-installed icon.
                existing.applySettings(
                    state.settings,
                    state.percentInside,
                    state.lowThreshold,
                    state.transform
                )
                // The OEM binder may have pushed its drawable on configuration/attach; repair in place.
                if (existing.reassert()) reasserts++
                return@synchronized existing
            }

            val image = imageFor(view, state.context, state.rom) ?: return@synchronized null
            val controller = Controller(
                view,
                image,
                state.bolts,
                state.context,
                state.percentInside,
                state.lowThreshold,
                state.settings,
                state.transform
            )
            // R7 P5: the first reading belongs to the install, exactly as the production path sees it;
            // installing an empty controller would refuse forever and the v2 battery would never draw.
            if (!controller.install(state.reading, state.saver)) {
                countFailure()
                return@synchronized null
            }
            installs++
            controllers[view] = controller
            if (state.tint != 0) controller.setTint(state.tint)
            controller
        }
    }

    /** One activation's immutable controller inputs, copied under [stateLock]. */
    private data class ActiveState(
        val context: Context,
        val rom: RomAdapter,
        val bolts: IntArray,
        val percentInside: Boolean,
        val lowThreshold: Int,
        val reading: IosBatteryReading?,
        val saver: Boolean,
        val tint: Int,
        val settings: ModuleSettings?,
        val transform: IosUserTransform
    )

    /**
     * The OEM binder pushed its own drawable: [controllerFor] repairs an existing controller in place
     * and counts a real repair, or installs on first sight (after the push, so ours is final).
     */
    private fun reassertFor(view: View) {
        controllerFor(view)
    }

    /** The OxygenOS icon leaf by name, or the AOSP `mBatteryIconView` / first ImageView child. */
    private fun imageFor(view: View, context: Context, rom: RomAdapter): ImageView? {
        val oplus = oplusClass?.isInstance(view) == true || view.javaClass.name == OPLUS_VIEW_CLASS
        if (oplus) {
            val id = oplusIconId(context, rom)
            if (id == 0) return null
            return try {
                view.findViewById<View>(id) as? ImageView
            } catch (_: Throwable) {
                null
            }
        }
        val field = try {
            XposedHelpers.getObjectField(view, "mBatteryIconView") as? ImageView
        } catch (_: Throwable) {
            null
        }
        return field ?: firstImageView(view)
    }

    private fun oplusIconId(context: Context, rom: RomAdapter): Int {
        val cached = batteryIconId
        if (cached != 0 && batteryIconPackage == rom.systemUiPackage) return cached
        val id = RomResources.id(context, rom, OPLUS_ICON_NAME)
        if (id != 0) {
            batteryIconId = id
            batteryIconPackage = rom.systemUiPackage
        }
        return id
    }

    private fun firstImageView(root: View): ImageView? {
        val group = root as? ViewGroup ?: return null
        val stack = ArrayDeque<View>()
        for (i in 0 until group.childCount) stack.add(group.getChildAt(i))
        while (stack.isNotEmpty()) {
            val view = stack.removeFirst()
            if (view is ImageView) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
            }
        }
        return null
    }

    private fun countFailure() {
        failures++
    }

    // -------------------------------------------------------------------------------- diagnostics

    override fun diagnosticLines(): List<String> {
        val views = synchronized(seen) { seen.size }
        return listOf(
            "ios v2 battery: active=$active installed=$installedFlag views=$views " +
                "installs=$installs reasserts=$reasserts failures=$failures " +
                "level=$lastLevel state=$lastState"
        )
    }

    // -------------------------------------------------------------------------------- controller

    /**
     * One battery icon replacement. Pure Android views and drawables, so it is exercised by
     * `IosBatteryV2Test` without Xposed: [view] is the battery container (bolt children live inside),
     * [image] is the icon leaf.
     */
    internal class Controller(
        private val view: View,
        private val image: ImageView,
        private val boltIds: IntArray = IntArray(0),
        private val context: Context,
        percentInside: Boolean,
        lowThreshold: Int,
        settings: ModuleSettings? = null,
        transform: IosUserTransform = IosUserTransform.IDENTITY
    ) {

        private var original: Drawable? = null
        private var ours: IosGlyphDrawable? = null
        private var visual: IosVisual? = null

        /**
         * The stock icon visibility captured at [install]. The measured OxygenOS
         * `bind$hideBatteryIcon` sets the ImageView visibility to 8 and clears its drawable
         * (`A-oxygenos-names.md:380,392`), so our glyph must be made visible and the captured value
         * restored on [restore].
         */
        private var originalVisibility = View.VISIBLE
        private var reading: IosBatteryReading? = null
        private var saver = false
        private var tint = BarTint.fgColor(context)
        private val boltVisibility = ArrayList<Pair<View, Int>>()
        private var installedFlag = false

        /** R7 P4/P5: live settings, mutable so a re-activation reaches an installed icon. */
        private var percentInside = percentInside
        private var lowThreshold = lowThreshold
        private var settings = settings
        private var fallbackTransform = transform

        /** The stock slot size captured at [install], the input to the live transform recompute. */
        private var stockWidth = 0
        private var stockHeight = 0

        /** R7 P4: the per-icon transform applied to every visual after [install]. */
        private var userTransform: IosUserTransform = transform

        /** The visual last pushed to the drawable (test seam). */
        val visualForTest: IosVisual? get() = visual

        /** True between [install] and [restore] (test seam). */
        val installedForTest: Boolean get() = installedFlag

        /**
         * Captures the stock drawable and size, swaps in our glyph and hides the OEM bolt(s).
         *
         * [initialReading]/[saver] are the first snapshot and are stored before any guard, because the
         * production path ([controllerFor]) constructs the controller and installs it with that state
         * instead of calling [update] first (R7 P5). Returns false (and changes nothing) when there is
         * no battery reading yet (keep the stock icon rather than install an invisible glyph) or the
         * stock size is unusable (C2).
         */
        fun install(initialReading: IosBatteryReading? = null, saver: Boolean = false): Boolean {
            if (installedFlag) return true
            if (initialReading != null) this.reading = initialReading
            this.saver = saver
            if (reading == null) return false
            val stock = image.drawable
            val width = stock?.intrinsicWidth?.takeIf { it > 0 } ?: image.width
            val height = stock?.intrinsicHeight?.takeIf { it > 0 } ?: image.height
            if (width <= 0 || height <= 0) return false

            // The transform depends on the glyph's actual inner box, so it is resolved here once the
            // stock slot is known (R7 P4); a null settings snapshot keeps the caller's fallback.
            stockWidth = width
            stockHeight = height
            userTransform = resolveTransform(width, height)
            val next = visualFor()
            val drawable = IosDrawables.create(
                IosIconKind.BATTERY,
                next,
                width,
                height,
                relativeSize = true,
                captureColorFilter = true
            )
            original = stock
            // `bind$hideBatteryIcon` can leave the leaf GONE with no drawable; capture the stock
            // visibility for restore, then make the icon visible for our glyph.
            originalVisibility = image.visibility
            if (image.visibility != View.VISIBLE) image.visibility = View.VISIBLE
            ours = drawable
            visual = next
            image.setImageDrawable(drawable)
            hideBolts()
            installedFlag = true
            return true
        }

        /**
         * R7 P5: adopt the current settings on an already-installed controller.
         *
         * `percentInside`/`lowThreshold`/the transform were constructor values in P2, so a settings
         * change after the icon was installed never reached it. Recompute from the stock size captured
         * at install and push a fresh visual; before install only the fields are updated.
         */
        fun applySettings(
            settings: ModuleSettings?,
            percentInside: Boolean,
            lowThreshold: Int,
            fallbackTransform: IosUserTransform
        ) {
            this.settings = settings
            this.percentInside = percentInside
            this.lowThreshold = lowThreshold
            this.fallbackTransform = fallbackTransform
            userTransform = if (stockWidth > 0 && stockHeight > 0) {
                resolveTransform(stockWidth, stockHeight)
            } else {
                fallbackTransform
            }
            if (!installedFlag) return
            pushVisual()
        }

        /** The system's resolved icon colour; a zero-alpha value is not a colour (M17). */
        fun setTint(color: Int) {
            if (color == 0 || Color.alpha(color) == 0) return
            if (color == tint) return
            tint = color
            if (!installedFlag) return
            pushVisual()
        }

        /** Pushes a new battery snapshot; a null [reading] keeps the last known level. */
        fun update(reading: IosBatteryReading?, saver: Boolean) {
            if (reading != null) this.reading = reading
            this.saver = saver
            if (!installedFlag) return
            pushVisual()
        }

        /**
         * Re-installs our drawable when the OEM binder replaced it, makes the icon visible again
         * (`bind$hideBatteryIcon` sets it to 8) and re-hides the OEM bolt that the
         * `bind$updateChargingView` push made visible again (R7 P5). True only on a real repair.
         */
        fun reassert(): Boolean {
            val drawable = ours ?: return false
            hideBolts()
            var repaired = false
            if (image.visibility != View.VISIBLE) {
                image.visibility = View.VISIBLE
                repaired = true
            }
            if (image.drawable !== drawable) {
                image.setImageDrawable(drawable)
                repaired = true
            }
            return repaired
        }

        /** Returns the exact original drawable/visibility and every bolt visibility; the other half of P4's path. */
        fun restore() {
            if (!installedFlag) return
            image.setImageDrawable(original)
            image.visibility = originalVisibility
            restoreBolts()
            original = null
            ours = null
            visual = null
            installedFlag = false
        }

        private fun visualFor(): IosVisual {
            val snapshot = reading
                ?: return IosVisual(
                    part = IosPart.BATTERY,
                    visible = false,
                    tint = tint,
                    percentInside = percentInside,
                    userTransform = userTransform
                )
            return IosMapping.battery(
                snapshot.level,
                lowThreshold,
                saver,
                snapshot.plugged,
                snapshot.status,
                tint,
                percentInside
            ).copy(userTransform = userTransform)
        }

        /** Recomputes the visual from the live fields and hands it to the installed drawable. */
        private fun pushVisual() {
            val next = visualFor()
            visual = next
            ours?.update(next)
        }

        /**
         * The user's per-icon transform for this battery glyph (R7 P4/P5). A settings snapshot wins
         * because it is the live source; otherwise the caller's fallback is used. Any failure falls
         * back to identity, never to a guessed offset (C11).
         */
        private fun resolveTransform(width: Int, height: Int): IosUserTransform {
            val live = settings ?: return fallbackTransform
            return try {
                val innerWidth = IosGeometry.Relative.sizeFor(
                    IosIconKind.BATTERY,
                    width,
                    height,
                    dualCell = false
                ).first
                live.iosTransform(IosIconKind.BATTERY, innerWidth.toFloat())
            } catch (_: Throwable) {
                IosUserTransform.IDENTITY
            }
        }

        /**
         * Hides every OEM bolt and records its original visibility once, so [reassert] is idempotent
         * and [restore] can never restore a GONE we wrote ourselves.
         */
        private fun hideBolts() {
            if (boltIds.isEmpty()) return
            for (id in boltIds) {
                val bolt = try {
                    view.findViewById<View>(id)
                } catch (_: Throwable) {
                    null
                }
                if (bolt == null) continue
                val recorded = boltVisibility.firstOrNull { it.first === bolt }
                if (recorded == null) {
                    boltVisibility.add(bolt to bolt.visibility)
                }
                if (bolt.visibility != View.GONE) bolt.visibility = View.GONE
            }
        }

        private fun restoreBolts() {
            for ((bolt, visibility) in boltVisibility) {
                try {
                    bolt.visibility = visibility
                } catch (_: Throwable) {
                }
            }
            boltVisibility.clear()
        }
    }
}

/**
 * R7 P5: the battery owner exists only when a constructor hook was really installed. A class that was
 * merely found is not enough; otherwise the V2 route could be picked with a battery that never binds.
 */
internal fun batteryHookInstalled(oplusConstructorHook: Boolean, aospConstructorHook: Boolean): Boolean =
    oplusConstructorHook || aospConstructorHook
