package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomResources

/**
 * Resolves the status-bar icon strip and the container the element is injected into from the view tree.
 *
 * Stateless: it only reads the tree and the ROM adapter. All attach/hide/overlay state stays in
 * [DuoIconHost]; this only answers "which view" questions.
 */
internal class ContainerFinder(
    private val context: Context,
    private val rom: RomAdapter,
    private val logOnce: LogOnce
) {

    /**
     * The keyguard's status bar itself - `com.android.systemui.statusbar.phone.KeyguardStatusBarView`,
     * read out of the device's SystemUI (`reverse/SystemUI-device.apk`, `layout/keyguard_status_bar.xml`),
     * not guessed. Walking up from the strip rather than looking the id up keeps it working on a ROM
     * that spells the id differently, and gives the right height to centre the element in.
     */
    fun findBar(strip: View): View? {
        var view: View? = strip
        while (view != null) {
            val name = view.javaClass.simpleName
            if (name.contains("KeyguardStatusBar") || name.contains("ShadeHeader")) return view
            view = view.parent as? View
        }
        return null
    }

    /**
     * Finds the strip the element goes into.
     *
     * First the ROM adapter's ids are probed in order. When none resolves — an unmeasured ROM such as
     * ColorOS 14/16 or One UI — the strip is found by what it must *contain*: every ROM's icon strip holds
     * the battery and/or the status-icons container, so walking up from those finds the parent without
     * knowing its id. That fallback is what lets a device we have never seen attach instead of doing
     * nothing, and the diagnostic dump records exactly which path was taken.
     */
    fun findStatusIconsHost(root: View): ViewGroup? {
        logOnce.once("rom") {
            L.i("ROM adapter: ${rom.id} (${rom.label}) - ${rom.notes}")
        }
        for (name in rom.containerIds) {
            val id = RomResources.id(context, rom, name)
            if (id == 0) continue
            val found = root.findViewById<View>(id)
            L.d("container $name -> ${found?.javaClass?.simpleName ?: "null"}")
            if (found is ViewGroup) {
                // A GONE strip is a legacy leftover, not the real one. Android 17 AOSP keeps a
                // `system_icons` LinearLayout that is GONE forever while the icons are drawn by Compose,
                // so injecting there produced a 0x0 element and nothing appeared (Pixel 9 report).
                if (found.visibility == View.GONE) {
                    L.i("container $name is GONE - skipping it (likely a legacy/Compose strip)")
                    continue
                }
                return found
            }
        }
        val anchor = stripAroundAnchors(root)
        if (anchor != null) {
            L.i("container id not found (tried ${rom.containerIds}) - using the strip around " +
                    "battery/status icons: ${anchor.javaClass.simpleName}")
            return anchor
        }
        // Final, additive fallback for a ROM that renames its ids: find the strip by the roles it must
        // hold (a battery view and a status-icons container), not by a name we guessed. Purely additive:
        // this runs only after the adapter ids and the anchor walk have both failed, so a ROM that
        // resolved before behaves exactly as before.
        val role = roleScan(root)
        if (role != null) {
            L.i("strip resolved by role: ${role.javaClass.simpleName} (adapter ids unresolved)")
            return role
        }
        L.w("no container id resolved (tried ${rom.containerIds}) - status bar left untouched")
        return null
    }

    /**
     * Finds the strip from what it must *contain*, so an unseen ROM with renamed ids still attaches.
     *
     * Every status-bar icon strip holds the battery and/or the status-icons container; the lowest common
     * ancestor of those two that is a real, non-GONE layout (and not the status-bar window itself) is the
     * strip. GONE seeds and GONE candidates are skipped, since both are legacy/Compose leftovers. A
     * class-name match is used only after the adapter's id is tried, so a measured ROM keeps its exact
     * behaviour. Returns null when the tree does not look like a status bar, leaving it untouched.
     */
    fun roleScan(root: View): ViewGroup? {
        val icons = findStatusIconsByRole(root)
        val battery = findBatteryByRole(root)
        // A GONE view is a legacy/Compose leftover, so it must not seed the walk either.
        val seed = listOfNotNull(icons, battery).firstOrNull { !skipGone(it) } ?: return null
        var candidate: ViewGroup? = seed.parent as? ViewGroup
        while (candidate != null && candidate !== root) {
            if (!skipGone(candidate) &&
                (icons == null || isAncestor(candidate, icons)) &&
                (battery == null || isAncestor(candidate, battery))
            ) {
                return candidate
            }
            candidate = candidate.parent as? ViewGroup
        }
        return null
    }

    /** The status-icons container by the adapter id, else by the class name every ROM keeps. */
    private fun findStatusIconsByRole(root: View): View? {
        for (name in listOf("statusIcons", "status_icons")) {
            val id = RomResources.id(context, rom, name)
            if (id != 0) findByIdNotGone(root, id)?.let { return it }
        }
        return findByNameNotGone(root, "StatusIconContainer")
    }

    /**
     * The battery view by the adapter id, else by class name. Deliberately matches battery *views*
     * (`BatteryMeter`/`BatteryIcon`/`BatteryView`), never a `BatteryContainer`, so the strip's parent
     * container is not mistaken for the battery itself.
     */
    private fun findBatteryByRole(root: View): View? {
        val id = RomResources.id(context, rom, rom.batteryId)
        if (id != 0) findByIdNotGone(root, id)?.let { return it }
        return findByNameNotGone(root, "BatteryMeter")
            ?: findByNameNotGone(root, "BatteryIcon")
            ?: findByNameNotGone(root, "BatteryView")
    }

    /** True when [view] is absent or GONE, so it must not seed or win the role walk. */
    private fun skipGone(view: View?): Boolean = view == null || view.visibility == View.GONE

    /** The first [id] match unless that match is a GONE leftover. */
    private fun findByIdNotGone(root: View, id: Int): View? =
        root.findViewById<View>(id)?.takeUnless { skipGone(it) }

    /** Like [findByName], but never descends into or returns a GONE subtree. */
    private fun findByNameNotGone(view: View, needle: String): View? {
        if (skipGone(view)) return null
        if (view.javaClass.simpleName.contains(needle)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findByNameNotGone(view.getChildAt(i), needle)?.let { return it }
            }
        }
        return null
    }

    /** True when [ancestor] is [view] or one of its parents. */
    private fun isAncestor(ancestor: View, view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    /**
     * The last-resort strip: the direct parent of the battery view, or of the status-icons container. Its
     * parent is what the ROM put the strip's children in, whatever the ROM calls that container.
     */
    fun stripAroundAnchors(root: View): ViewGroup? {
        for (anchorName in listOf(rom.batteryId, "statusIcons", "status_icons", "system_icons")) {
            val id = RomResources.id(context, rom, anchorName)
            if (id == 0) continue
            val anchor = root.findViewById<View>(id) ?: continue
            val parent = anchor.parent as? ViewGroup ?: continue
            // Never hand back the status-bar window itself: injecting there would fight the bar's layout.
            if (parent === root) continue
            // A GONE parent is a legacy/Compose leftover, not a strip we can draw in.
            if (parent.visibility == View.GONE) continue
            return parent
        }
        return null
    }

    /**
     * Where the element view should be added.
     *
     * The strip itself when it is a plain Android layout. When it is a custom container — HyperOS 3's
     * `MiuiStatusBatteryContainer` is the measured one — it lays out only its own children and left the
     * injected view at 0×0, so the icons disappeared and nothing replaced them. In that case the element
     * goes into the nearest plain `FrameLayout` ancestor (the bar's own icon layer) and is anchored over
     * the battery by [anchorOnBattery]; the strip is still what gets hidden, by [hideStrip].
     */
    fun chooseElementParent(strip: ViewGroup, barRoot: View): ViewGroup {
        if (isStandardLayout(strip)) return strip
        var view: View? = strip.parent as? View
        while (view != null) {
            if (view.javaClass.name == "android.widget.FrameLayout" && view is ViewGroup) return view
            view = view.parent as? View
        }
        return (barRoot as? ViewGroup)?.takeIf { it !== strip } ?: strip
    }

    /** A layout whose measurement the module trusts to honour a child's requested size. */
    fun isStandardLayout(view: ViewGroup): Boolean = when (view.javaClass.name) {
        "android.widget.FrameLayout",
        "android.widget.LinearLayout",
        "android.widget.RelativeLayout",
        "android.widget.GridLayout" -> true
        else -> false
    }

    /**
     * The Compose view that draws the Wi-Fi/cellular/battery cluster, on a status bar with no View strip
     * (Android 17 AOSP). Only the end-side area is searched, so the centre clock's Compose view is never
     * mistaken for the icon cluster. The outer `ComposeView` is preferred over the inner
     * `AndroidComposeView`: hiding the outer one hides everything it draws.
     */
    fun findComposeIconView(root: View): View? {
        for (name in listOf("status_bar_end_side_content", "status_bar_end_side_container")) {
            val id = RomResources.id(context, rom, name)
            if (id == 0) continue
            val area = root.findViewById<View>(id) ?: continue
            findComposeDescendant(area)?.let { return it }
        }
        return null
    }

    /** First `*ComposeView` in the subtree (top-down), or null. */
    fun findComposeDescendant(view: View): View? {
        if (view.javaClass.name.endsWith("ComposeView")) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeDescendant(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** The battery view inside [strip], the anchor for the overlay placement. */
    fun findBattery(strip: ViewGroup): View? {
        val id = RomResources.id(context, rom, rom.batteryId)
        return if (id != 0) strip.findViewById(id) else null
    }

    /**
     * Samsung One UI 8 (Android 16) draws the status-bar icon cluster with a custom `CombinedStatusView`
     * inside the new "IndicatorGarden" layout and leaves the AOSP `system_icons` in the tree as a dead
     * stub (its Wi-Fi/cellular/battery children measure 0x0). Injecting into that stub drew a 0x0 element
     * and hid nothing — the SM-S948N report. Detecting the combined view is how the host knows to anchor
     * on the real cluster instead.
     */
    fun findCombinedStatusView(root: View): View? = findByName(root, "CombinedStatusView")

    private fun findByName(view: View, needle: String): View? {
        if (view.javaClass.simpleName.contains(needle)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findByName(view.getChildAt(i), needle)?.let { return it }
        }
        return null
    }

    /**
     * True when [strip] holds no laid-out stock icon — a legacy stub, not a real strip. Checked at attach,
     * before anything is hidden, so a working ROM (whose icons are still measured) never matches.
     */
    fun isStubStrip(strip: ViewGroup): Boolean {
        val battery = findBattery(strip)
        val iconsId = RomResources.id(context, rom, "statusIcons")
        val icons = if (iconsId != 0) strip.findViewById<View>(iconsId) else null
        val batteryEmpty = battery == null || battery.width == 0
        val iconsEmpty = icons == null || icons.width == 0
        return batteryEmpty && iconsEmpty
    }

    /**
     * A frame the locked ring can be centered in.
     *
     * It has to span the status bar, so the center is the screen's center (where a punch-hole sits), and
     * it has to be the bar rather than a full-screen window, so the ring stays in the status-bar band.
     * The icon strip and the end-side cluster are skipped: those are the views that move when the clock
     * gets wider. The nearest ancestor that qualifies wins; a full-screen frame is only a last resort.
     */
    fun findLockFrame(strip: View, barRoot: View): FrameLayout? {
        val rootWidth = barRoot.width
        val rootHeight = barRoot.height
        if (rootWidth <= 0 || rootHeight <= 0) return null
        val windowIsScreen = rootHeight > rootWidth
        var view: View? = strip.parent as? View
        var screenSized: FrameLayout? = null
        while (view != null) {
            if (view is FrameLayout) {
                val spansWidth = view.width >= rootWidth * 9 / 10
                if (spansWidth) {
                    val asTallAsWindow = view.height >= rootHeight * 9 / 10
                    if (windowIsScreen && asTallAsWindow) {
                        if (screenSized == null) screenSized = view
                    } else {
                        return view
                    }
                }
            }
            if (view === barRoot) break
            view = view.parent as? View
        }
        return screenSized
    }

    /**
     * The bar's own plain `FrameLayout` layer to draw in for the One UI 8 overlay, skipping the tiny stub
     * frames that sit inside the (invisible) old end-side content and would clip the element. Falls back to
     * the status-bar window's first `FrameLayout` child when no frame is wide enough yet.
     */
    fun findOverlayContainer(strip: ViewGroup, barRoot: View): ViewGroup {
        val minWidth = barRoot.width / 2
        var view: View? = strip
        while (view != null) {
            if (view.javaClass.name == "android.widget.FrameLayout" && view is ViewGroup &&
                (minWidth <= 0 || view.width >= minWidth)
            ) {
                return view
            }
            view = view.parent as? View
        }
        if (barRoot is ViewGroup) {
            for (i in 0 until barRoot.childCount) {
                val child = barRoot.getChildAt(i)
                if (child is ViewGroup && child.javaClass.name == "android.widget.FrameLayout") return child
            }
        }
        return strip.parent as? ViewGroup ?: strip
    }

    /**
     * The shade/QS header's icon cluster.
     *
     * Selection order was measured on the CPH2747 (2026-10-27): the real cluster is
     * `quick_qs_status_icons`, a 386 px `ConstraintLayout` once the shade expands. It is chosen even
     * while it is still 0x0, because the header is built before it is laid out and
     * [DuoIconHost.attachShadeHeader] retries on the next layout pass. The old order picked the inner
     * `icons` wrapper (0x0) because it was found through `statusIcons`, so Duo was drawn into an
     * invisible view and the stock battery stayed on screen. `shade_header_system_icons` and
     * `parent(statusIcons)` remain fallbacks for ROMs that do not have the QS cluster.
     *
     * [id] is injectable so the measured tree can be pinned in a unit test; production resolves every
     * id against SystemUI's resources through [RomResources].
     */
    fun findShadeIconsArea(header: View): ViewGroup? =
        findShadeIconsArea(header) { name -> RomResources.id(context, rom, name) }

    internal fun findShadeIconsArea(header: View, id: (String) -> Int): ViewGroup? {
        // Probes only: a profile's shade ids are tried first, but only when the instance is really on
        // screen. The ungated order below stays as the fallback so a 0x0 header - built before the
        // shade is laid out - behaves exactly as it did before.
        for (name in rom.shadeContainerIds) {
            val candidateId = id(name)
            val candidate = if (candidateId != 0) header.findViewById<View>(candidateId) else null
            val usable = candidate is ViewGroup && candidate.width > 0 &&
                candidate.isShown && candidate.isAttachedToWindow
            ShadeProbe.logOnce(
                "shadeProbe:shadeContainerId:$name",
                "candidate $name id=$candidateId -> ${ShadeProbe.describe(candidate)} usable=$usable"
            )
            if (usable) return candidate as ViewGroup
        }
        val quickId = id("quick_qs_status_icons")
        val quick = if (quickId != 0) header.findViewById<View>(quickId) else null
        ShadeProbe.logOnce(
            "shadeProbe:quick_qs_status_icons",
            "candidate quick_qs_status_icons id=$quickId -> ${ShadeProbe.describe(quick)} (preferred)"
        )
        val systemIconsId = id("shade_header_system_icons")
        val systemIcons = if (systemIconsId != 0) header.findViewById<View>(systemIconsId) else null
        ShadeProbe.logOnce(
            "shadeProbe:shade_header_system_icons",
            "candidate shade_header_system_icons id=$systemIconsId -> ${ShadeProbe.describe(systemIcons)}"
        )
        // Last fallback: the parent of the icon container the controller binds to.
        val icons = id("statusIcons")
        val container = if (icons != 0) header.findViewById<View>(icons) else null
        val parent = container?.parent as? ViewGroup
        ShadeProbe.logOnce(
            "shadeProbe:parent(statusIcons)",
            "candidate parent(statusIcons) id=$icons statusIcons=${ShadeProbe.describe(container)} " +
                "-> ${ShadeProbe.describe(parent)}"
        )
        val chosen = (quick as? ViewGroup) ?: (systemIcons as? ViewGroup) ?: parent
        ShadeProbe.logOnce("shadeProbe:chosen:${ShadeProbe.describe(chosen)}", "chosen ${ShadeProbe.describe(chosen)}")
        return chosen
    }

    /**
     * The *laid-out* QS cluster inside the shade window.
     *
     * The id is not unique on the measured CPH2747 (2026-10-27): one `quick_qs_status_icons` lives in
     * `split_shade_status_bar` and stays 0x0/GONE, while the one the user sees is under
     * `qs_frame`/`header_container` ([816,153][1202,254]). A plain `findViewById` returns whichever
     * comes first in the tree — the dead duplicate — which is why the retry kept re-checking that same
     * 0x0 view. This walks the whole window depth-first and accepts only an instance with a real width
     * that is both shown and attached to the window - `visibility == VISIBLE` alone also matches a dead
     * duplicate whose ancestors are hidden.
     *
     * Returns null while the shade is collapsed; the caller keeps its bounded retry.
     */
    fun findVisibleQuickQsCluster(root: View): View? =
        findVisibleQuickQsCluster(root) { name -> RomResources.id(context, rom, name) }

    internal fun findVisibleQuickQsCluster(root: View, id: (String) -> Int): View? {
        val quickId = id("quick_qs_status_icons")
        if (quickId == 0) return null
        val candidates = ArrayList<View>(2)
        collectLaidOut(root, quickId, candidates)
        if (candidates.isEmpty()) return null
        // The id is duplicated, so rank by the container the real header lives in rather than by the
        // order the tree happens to hold them in. `header_container` is the QS header; `qs_frame` is
        // its ancestor; the largest area is the last guess before falling back to tree order.
        val headerId = id("header_container")
        val qsFrameId = id("qs_frame")
        var reason = "header_container"
        var chosen = if (headerId != 0) candidates.firstOrNull { hasAncestorId(it, headerId) } else null
        if (chosen == null) {
            reason = "qs_frame"
            chosen = if (qsFrameId != 0) candidates.firstOrNull { hasAncestorId(it, qsFrameId) } else null
        }
        if (chosen == null) {
            reason = "area"
            chosen = candidates.maxByOrNull { it.width * it.height }
        }
        val picked = chosen ?: return null
        // Re-check the winner: a collected view can detach between the walk and the choice.
        if (!picked.isShown || !picked.isAttachedToWindow || picked.width <= 0) return null
        ShadeProbe.logOnce(
            "shadeProbe:quickQs:chosen:${System.identityHashCode(picked)}",
            "shadeProbe quick_qs chosen by $reason area=${picked.width * picked.height}: " +
                ShadeProbe.describe(picked)
        )
        for (rejected in candidates) {
            if (rejected === picked) continue
            ShadeProbe.logOnce(
                "shadeProbe:quickQs:rejected:${System.identityHashCode(rejected)}",
                "shadeProbe quick_qs rejected (chosen by $reason) area=${rejected.width * rejected.height}: " +
                    ShadeProbe.describe(rejected)
            )
        }
        return picked
    }

    /** Depth-first collect of every [id] instance that has been laid out and is really on screen. */
    private fun collectLaidOut(view: View, id: Int, into: MutableList<View>) {
        if (view.id == id && view.width > 0 && view.isShown && view.isAttachedToWindow) {
            into.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectLaidOut(view.getChildAt(i), id, into)
        }
    }

    /**
     * The *laid-out* shade area for every profile, not just the QS cluster.
     *
     * The QS cluster is only the measured OxygenOS spelling; Samsung/Xiaomi/vivo/Motorola/Nothing/Honor
     * keep their own containers in [RomAdapter.shadeContainerIds]. Every profile id except the
     * status-bar clusters (`system_icons`, `statusIcons`) is probed across the whole window, plus the
     * QS cluster so the measured OxygenOS path is unchanged. A candidate must have a real width, be
     * shown and be attached - `visibility == VISIBLE` alone also matches a dead duplicate whose
     * ancestors are hidden.
     *
     * Ranking: an instance under `header_container` wins, then one under `qs_frame`, then the largest
     * area, then tree order. Returns null while the shade is collapsed; the caller keeps its retry.
     */
    fun findVisibleShadeArea(root: View): ViewGroup? =
        findVisibleShadeArea(root) { name -> RomResources.id(context, rom, name) }

    internal fun findVisibleShadeArea(root: View, id: (String) -> Int): ViewGroup? {
        val names = LinkedHashSet<String>()
        for (name in rom.shadeContainerIds) {
            if (name == "system_icons" || name == "statusIcons") continue
            names.add(name)
        }
        names.add("quick_qs_status_icons")
        val candidates = ArrayList<ViewGroup>(4)
        for (name in names) {
            val candidateId = id(name)
            if (candidateId == 0) {
                ShadeProbe.logOnce(
                    "shadeProbe:shadeArea:collect:$name",
                    "shadeArea candidate $name id=0 -> none"
                )
                continue
            }
            val matches = ArrayList<View>(2)
            collectLaidOut(root, candidateId, matches)
            for (match in matches) if (match is ViewGroup) candidates.add(match)
            ShadeProbe.logOnce(
                "shadeProbe:shadeArea:collect:$name",
                "shadeArea candidate $name id=$candidateId -> ${matches.size} laid out"
            )
        }
        if (candidates.isEmpty()) return null
        val headerId = id("header_container")
        val qsFrameId = id("qs_frame")
        var chosen: ViewGroup? = null
        var chosenRank = -1
        var chosenArea = -1
        var reason = "area"
        for (candidate in candidates) {
            val rank = when {
                headerId != 0 && hasAncestorId(candidate, headerId) -> 2
                qsFrameId != 0 && hasAncestorId(candidate, qsFrameId) -> 1
                else -> 0
            }
            val area = candidate.width * candidate.height
            if (chosen == null || rank > chosenRank || (rank == chosenRank && area > chosenArea)) {
                chosen = candidate
                chosenRank = rank
                chosenArea = area
                reason = when (rank) {
                    2 -> "header_container"
                    1 -> "qs_frame"
                    else -> "area"
                }
            }
        }
        val winner = chosen ?: return null
        // Re-check the winner: a collected view can detach between the walk and the choice.
        if (!winner.isShown || !winner.isAttachedToWindow || winner.width <= 0) return null
        // Return the raw cluster. The host picks the slot name from these original dimensions and only
        // then normalizes to a wider ancestor; normalizing here would hide the QS/shade distinction.
        ShadeProbe.logOnce(
            "shadeProbe:shadeArea:chosen:${System.identityHashCode(winner)}",
            "shadeArea chosen by $reason area=$chosenArea: ${ShadeProbe.describe(winner)}"
        )
        for (rejected in candidates) {
            if (rejected === winner) continue
            ShadeProbe.logOnce(
                "shadeProbe:shadeArea:rejected:${System.identityHashCode(rejected)}",
                "shadeArea rejected (chosen by $reason) area=${rejected.width * rejected.height}: " +
                    ShadeProbe.describe(rejected)
            )
        }
        return winner
    }

    /**
     * The view Duo should draw in for a shade cluster.
     *
     * The measured CPH2747 (2026-10-27) collapses `quick_qs_status_icons` to 9x63 inside a wide
     * `header`. Drawing there squeezed the ring and left Wi-Fi outside it. The cluster's immediate
     * parent is also 9x63; the wider `header` is a grandparent, so a single-parent normalization is
     * not enough. Walk up until a shown, attached layout is wider than the original cluster and draw
     * there. The battery does not have to be inside that host: the measured shade header was
     * normalized before the battery was resolvable. If no wider ancestor exists, keep the cluster.
     */
    fun usableShadeHost(cluster: ViewGroup): ViewGroup {
        var ancestor = cluster.parent as? ViewGroup
        var custom: ViewGroup? = null
        while (ancestor != null) {
            if (ancestor.isShown && ancestor.isAttachedToWindow && ancestor.width > cluster.width) {
                // The measured QS chain has a custom `OplusQuickStatusBarHeader` between the cluster
                // and the plain `header`; adding a foreign child to the custom view yields 0 width.
                // Prefer the first standard layout ancestor and keep the custom one only as fallback.
                if (isStandardLayout(ancestor)) return ancestor
                if (custom == null) custom = ancestor
            }
            ancestor = ancestor.parent as? ViewGroup
        }
        return custom ?: cluster
    }

    /** True when one of [view]'s ancestors carries [id]. */
    internal fun hasAncestorId(view: View, id: Int): Boolean {
        var current = view.parent as? View
        while (current != null) {
            if (current.id == id) return true
            current = current.parent as? View
        }
        return false
    }

    /** True when [view] is the measured QS icon cluster (`quick_qs_status_icons`). */
    fun isQuickQsCluster(view: View): Boolean {
        val id = RomResources.id(context, rom, "quick_qs_status_icons")
        return id != 0 && view.id == id
    }

    /** The stock `StatusIconContainer` inside the QS cluster, by id or by class name. */
    fun findShadeStatusIcons(area: ViewGroup): View? {
        for (name in listOf("statusIcons", "status_icons")) {
            val id = RomResources.id(context, rom, name)
            if (id != 0) area.findViewById<View>(id)?.let { return it }
        }
        return findByName(area, "StatusIconContainer")
    }

    /** The stock battery inside the QS cluster, so the element can be anchored at its end. */
    fun findShadeBattery(area: ViewGroup): View? =
        findShadeBattery(area) { name -> RomResources.id(context, rom, name) }

    internal fun findShadeBattery(area: ViewGroup, id: (String) -> Int): View? {
        val names = LinkedHashSet<String>()
        if (rom.shadeBatteryId.isNotBlank()) names.add(rom.shadeBatteryId)
        names.addAll(listOf("batteryRemainingIcon", "battery_icon_view", rom.batteryId, "battery"))
        for (name in names) {
            val batteryId = id(name)
            if (batteryId == 0) continue
            val found = area.findViewById<View>(batteryId) ?: continue
            // The id can be duplicated by a detached/GONE copy; only the instance really on screen is
            // the anchor, so keep probing instead of returning the dead duplicate.
            if (found.isShown && found.isAttachedToWindow) return found
            ShadeProbe.logOnce(
                "shadeProbe:shadeBattery:rejected:${System.identityHashCode(found)}",
                "shadeBattery rejected $name: ${ShadeProbe.describe(found)}"
            )
        }
        return findUsableByName(area, "BatteryMeter")
            ?: findUsableByName(area, "BatteryIcon")
            ?: findUsableByName(area, "BatteryView")
    }

    /** First descendant (top-down) whose class name matches [needle] and that is really on screen. */
    private fun findUsableByName(view: View, needle: String): View? {
        if (!view.isShown || !view.isAttachedToWindow) return null
        if (view.javaClass.simpleName.contains(needle)) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findUsableByName(view.getChildAt(i), needle)?.let { return it }
            }
        }
        return null
    }
}
