package io.github.kvmy666.duostatusbar.hook

/**
 * The dynamic registrations the runtime diffs, keyed by a stable tag (R3 A6).
 *
 * [PANEL_EXPANSION] is shared by both owners (R3 A4/A1/A6): it is installed for Duo and for iOS, where
 * it is the only registration the bar carries. The event hooks, the status-bar touch hook and the
 * main-bar layout listener stay Duo-only.
 *
 * The enum is the stable contract the runtime diffs and the tests pin. A registration whose feature is
 * off is not merely inert: it is removed, so no callback of it can run while the feature is off.
 */
internal enum class DuoRegistration(val tag: String) {
    FLASHLIGHT("flashlight"),
    SCREENSHOT("screenshot"),
    UNLOCK("unlock"),
    STATUS_TOUCH("status_touch"),
    PANEL_EXPANSION("panel_expansion"),
    STATUS_LAYOUT("status_layout")
}

/**
 * Exactly the runtime registrations [features] asks for.
 *
 * While no owner is live the set is empty: no hook, listener or touch handling survives. The
 * panel-expansion hook is shared, so any live owner gets it: its iOS branch only refreshes iOS state
 * (readings and attach memos) while its Duo branch stays gated on `features.duoRuntime` inside the
 * callback. Panel expansion is therefore the single registration an iOS bar carries.
 *
 * The main-bar layout listener, the event hooks and the touch hook are Duo-only and follow either the
 * owner or their own optional feature.
 */
internal fun duoRegistrationsFor(features: FeatureSet): Set<DuoRegistration> {
    if (features.owner == BarOwner.NONE) return emptySet()
    val out = LinkedHashSet<DuoRegistration>()
    // Shared owner-level trigger: installed for Duo and iOS (A4/A1/A6).
    out += DuoRegistration.PANEL_EXPANSION
    if (!features.duoRuntime) return out
    out += DuoRegistration.STATUS_LAYOUT
    if (features.has(DuoFeature.EVENT_FLASHLIGHT)) out += DuoRegistration.FLASHLIGHT
    if (features.has(DuoFeature.EVENT_SCREENSHOT)) out += DuoRegistration.SCREENSHOT
    if (features.has(DuoFeature.EVENT_UNLOCK)) out += DuoRegistration.UNLOCK
    if (features.has(DuoFeature.RING_GESTURES) ||
        features.has(DuoFeature.INDICATOR_GESTURES) ||
        features.has(DuoFeature.DOWNLOAD)
    ) {
        out += DuoRegistration.STATUS_TOUCH
    }
    return out
}

/** The owner work a real shade/QS panel transition performs. */
internal enum class PanelTransitionAction {
    /** Refresh iOS state only: reset attach memos, drop the shared reading snapshot, repaint. */
    IOS_REFRESH,

    /** Drive the Duo panel owner / main-bar visibility from the aggregate panel state. */
    DUO_PANEL_STATE
}

/**
 * The work a real (edge-triggered) panel transition runs for [features].
 *
 * iOS owns its own refresh and never touches the Duo host; Duo owns the panel/visibility state. NONE
 * does nothing, mirroring the empty registration set. Pure and allocation-light so the callbacks can
 * use it only when a panel/aggregate boolean actually changed.
 */
internal fun panelTransitionActions(features: FeatureSet): Set<PanelTransitionAction> = when (features.owner) {
    BarOwner.IOS -> setOf(PanelTransitionAction.IOS_REFRESH)
    BarOwner.DUO -> setOf(PanelTransitionAction.DUO_PANEL_STATE)
    BarOwner.NONE -> emptySet()
}

/** A process-level owner transition that a settings apply has to perform. */
internal enum class OwnerAction {
    STOP_DUO_MONITOR,
    TEARDOWN_DUO,
    ATTACH_DUO,
    TEARDOWN_IOS,
    ATTACH_IOS
}

/**
 * The owner actions needed to move from [current] to [next].
 *
 * The diff is what makes a live Duo -> iOS -> Duo switch leave nothing behind: entering a mode stops
 * the other mode first, and leaving a mode always tears it down. Switching between Duo renderers (or
 * refreshing Duo settings) produces no owner action at all.
 */
internal fun ownerActions(current: FeatureSet, next: FeatureSet): Set<OwnerAction> {
    val out = LinkedHashSet<OwnerAction>()
    if (!next.duoRuntime) {
        out += OwnerAction.STOP_DUO_MONITOR
        out += OwnerAction.TEARDOWN_DUO
    } else if (!current.duoRuntime) {
        out += OwnerAction.ATTACH_DUO
    }
    if (!next.iosRuntime) {
        out += OwnerAction.TEARDOWN_IOS
    } else if (!current.iosRuntime) {
        out += OwnerAction.ATTACH_IOS
    }
    return out
}

/** What [RegistrationSet.apply] changed, so the runtime can log one line per real diff. */
internal data class RegistrationDiff<K>(val added: Set<K>, val removed: Set<K>) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
}

/**
 * Tracks installed registrations and applies a desired set as a diff.
 *
 * [installer] returns a handle that removes exactly what it installed, or null when the ROM does not
 * expose that class/method. A null install is deliberately not recorded: the next apply retries it,
 * and nothing half-installed is ever claimed as live. [uninstaller] runs once per removal.
 *
 * Generic and Android-free so the registration matrix can prove install/uninstall counts without
 * Xposed.
 */
internal class RegistrationSet<K, H>(
    private val installer: (K) -> H?,
    private val uninstaller: (H) -> Unit
) {
    private val live = LinkedHashMap<K, H>()

    @Synchronized
    fun installed(): Set<K> = live.keys.toSet()

    @Synchronized
    fun apply(desired: Set<K>): RegistrationDiff<K> {
        val removed = LinkedHashSet<K>()
        for (key in live.keys.toList()) {
            if (key in desired) continue
            val handle = live.remove(key) ?: continue
            try {
                uninstaller(handle)
            } catch (_: Throwable) {
                // A failed unhook must not leave the bookkeeping claiming the registration is gone.
            }
            removed += key
        }
        val added = LinkedHashSet<K>()
        for (key in desired) {
            if (key in live) continue
            val handle = try {
                installer(key)
            } catch (_: Throwable) {
                null
            } ?: continue
            live[key] = handle
            added += key
        }
        return RegistrationDiff(added, removed)
    }

    @Synchronized
    fun clear(): Set<K> {
        val removed = live.keys.toSet()
        val handles = live.values.toList()
        live.clear()
        for (handle in handles) {
            try {
                uninstaller(handle)
            } catch (_: Throwable) {
            }
        }
        return removed
    }
}

// ---------------------------------------------------------------- QS/shade extra surfaces

/** The shade-header slot names, shared by the host and the surface tests. */
internal const val SURFACE_QS = "qs header"
internal const val SURFACE_SHADE = "shade header"
private val EXTRA_SURFACES = setOf(SURFACE_QS, SURFACE_SHADE)

/** Whether [name] is enabled by the user's current settings. Non-extra names are always allowed. */
internal fun surfaceEnabled(name: String, settings: ModuleSettings): Boolean = when (name) {
    SURFACE_QS -> settings.showInQs
    SURFACE_SHADE -> settings.showInShade
    else -> true
}

/** Every extra surface the settings currently ask for. */
internal fun desiredSurfaces(settings: ModuleSettings): Set<String> = buildSet {
    if (settings.showInQs) add(SURFACE_QS)
    if (settings.showInShade) add(SURFACE_SHADE)
}

/** Live slots that the new settings no longer want; dropping one restores its stock icons. */
internal fun surfacesToDrop(live: Collection<String>, settings: ModuleSettings): Set<String> =
    live.filter { it in EXTRA_SURFACES && it !in desiredSurfaces(settings) }.toSet()

/** Desired extra surfaces that are not live; the next attach/layout creates them. */
internal fun surfacesToCreate(live: Collection<String>, settings: ModuleSettings): Set<String> =
    desiredSurfaces(settings) - live.toSet()
