package io.github.kvmy666.duostatusbar.hook

import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.settings.StockIconInventory
import io.github.kvmy666.duostatusbar.settings.StockIconInventoryItem

/**
 * What a stock status-bar view is, for the per-icon hiding list (A4).
 *
 * [IGNORED] is deliberately broad: a Duo view, a layout/container, a status dot or a signal view
 * nested inside a composite icon is never an entry of its own. That matters because the inventory is
 * built from the live view tree, and a container such as HyperOS's `MiuiStatusBatteryContainer` or a
 * `mobile_signal` child must never be offered as an icon the user can hide.
 */
internal enum class StockIconKind {
    BATTERY,
    WIFI,
    CELLULAR,
    COMBINED,
    OTHER,
    IGNORED;

    /** True for every kind Duo replaces; those are hidden even when the user selected nothing. */
    val replaced: Boolean
        get() = this == BATTERY || this == WIFI || this == CELLULAR || this == COMBINED
}

/** One stock icon found in the live bar. [key] is what `hiddenStockIcons` stores. */
internal data class StockIcon(
    val key: String,
    val label: String,
    val kind: StockIconKind,
    val view: View
) {
    val replaced: Boolean get() = kind.replaced
}

/**
 * The stable identity and the read-only inventory of the stock status icons (A4).
 *
 * A key is resolved in the measured order — slot, then resource id, then class — and a duplicate of
 * the same base key gets a `#n` occurrence suffix (`id:mobile_combo`, `id:mobile_combo#2`). The suffix
 * is assigned by a depth-first walk in child order, which is also the order [inventory] reports, so
 * the settings list and the hiding pass always agree on which SIM is which.
 *
 * This object only reads the tree. It never changes a view, logs, or touches the settings; the hider
 * owns hiding and restoration. Everything is guarded: a ROM with no readable slot, no resolvable id
 * or an unexpected class still yields a usable key/label instead of throwing inside System UI.
 */
internal object StockIconKey {

    /** Children of the OOS composites: part of the icon, never separate entries. */
    private val INNER_IDS = setOf(
        "mobile_signal",
        "mobile_volte",
        "mobile_combo_real",
        "wifi_signal",
        "wifi_activity",
        "mobile_activity",
        "mobile_type"
    )

    /** Slots that describe an inner part or the status dot rather than a hideable icon. */
    private val INNER_SLOTS = INNER_IDS + setOf("status_bar_dot", "privacy", "privacy_dot")

    /** Class/id tokens that identify a status icon when no slot is readable. */
    private val ICON_HINTS = listOf(
        "statusbaricon", "statusicon", "status_bar_icon", "combinedstatus",
        "clock", "alarm", "volume", "ringer", "silent", "vibrate", "bluetooth",
        "dnd", "zen", "headset", "hotspot", "location", "microphone", "mic",
        "camera", "rotation", "airplane", "vowifi", "volte", "signal"
    )

    /** Fixed, plain-language labels; unknown tokens fall back to a prettified id/class name. */
    private val LABELS = mapOf(
        "battery" to "Battery",
        "wifi" to "Wi-Fi",
        "wifi_combo" to "Wi-Fi",
        "wifi_signal" to "Wi-Fi",
        "mobile" to "Cellular",
        "mobile_combo" to "Cellular",
        "mobile_roaming" to "Cellular (roaming)",
        "mobile_signal" to "Cellular signal",
        "clock" to "Clock",
        "alarm" to "Alarm",
        "alarm_clock" to "Alarm",
        "volume" to "Sound",
        "ringer" to "Sound",
        "silent" to "Silent",
        "vibrate" to "Vibrate",
        "bluetooth" to "Bluetooth",
        "dnd" to "Do Not Disturb",
        "zen" to "Do Not Disturb",
        "location" to "Location",
        "mic" to "Microphone",
        "microphone" to "Microphone",
        "hotspot" to "Hotspot",
        "rotation_lock" to "Rotation lock",
        "airplane" to "Airplane mode",
        "vowifi" to "Wi-Fi calling",
        "volte" to "VoLTE",
        "combinedstatusview" to "Battery, Wi-Fi and cellular"
    )

    /**
     * The base key, without an occurrence suffix: `slot:` first, then `id:`, then `class:`.
     * Slot and id are lower-cased so a ROM that spells `Battery` and `battery` cannot produce two
     * settings entries for the same icon.
     */
    fun baseKey(view: View): String {
        val slot = slotOf(view)?.trim()?.lowercase()
        if (!slot.isNullOrEmpty()) return "slot:$slot"
        val id = idName(view)
        if (id != null) return "id:${id.lowercase()}"
        return "class:${className(view)}"
    }

    /**
     * The stable key with a 1-based occurrence. The first occurrence keeps the plain base key; the
     * second becomes `#2`, and so on. Occurrence is assigned by [inventory]/the hiding walk, never
     * invented here, so the stored key means the same view in both places.
     */
    fun keyOf(view: View, occurrence: Int = 1): String = key(baseKey(view), occurrence)

    /** Applies the occurrence suffix to an already-computed [base] key. */
    fun key(base: String, occurrence: Int): String =
        if (occurrence > 1) "$base#$occurrence" else base

    /**
     * The kind of [view]. [IGNORED] covers Duo's own views, containers, the status dot and the inner
     * signal parts; [OTHER] is a real icon Duo does not replace (clock, alarm, sound, Bluetooth…).
     */
    fun kindOf(view: View): StockIconKind {
        if (isIgnored(view)) return StockIconKind.IGNORED
        val slot = slotOf(view)?.trim()?.lowercase()
        if (!slot.isNullOrEmpty()) return kindOfToken(slot)
        val id = idName(view)?.lowercase()
        val cls = className(view)
        // A container named "…BatteryContainer" is a host, not the battery itself; walk into it.
        if (view is ViewGroup && isContainerName(cls)) return StockIconKind.IGNORED
        return kindOfToken(id) .takeIf { it != StockIconKind.IGNORED }
            ?: kindOfToken(cls)
    }

    /** True when this view must never appear in the list or be hidden as an icon. */
    fun isIgnored(view: View): Boolean = try {
        isDuoOwned(view) || isStatusDot(view) || isInnerSignal(view)
    } catch (_: Throwable) {
        true
    }

    /** A human label for [view], with the same fallback chain as the key. */
    fun labelOf(view: View): String {
        val token = slotOf(view)?.trim()?.lowercase()
            ?: idName(view)?.lowercase()
            ?: className(view)
        return labelForToken(token)
    }

    /** A human label for a stored key (`slot:wifi`, `id:mobile_combo#2`, …). */
    fun labelFor(key: String): String {
        val token = key.substringAfter(':', key).substringBefore('#').lowercase()
        return labelForToken(token)
    }

    /**
     * Read-only inventory of the hideable icons under [root], in depth-first child order.
     * Containers are walked; an icon is a leaf and its inner signal/dot children are not listed.
     * The list holds the live views, so callers can hide exactly what they were shown.
     */
    fun inventory(root: View): List<StockIcon> {
        val out = ArrayList<StockIcon>()
        val counts = HashMap<String, Int>()
        try {
            walk(root, isRoot = true, out = out, counts = counts)
        } catch (_: Throwable) {
            // A half-built tree must not break the settings screen; return what was collected.
        }
        return out
    }

    /**
     * The inventory as the compact JSON the app lists: a `{key,label,kind}` object per icon, with no
     * view references. It uses exactly the entries [inventory] returns, so a key in the settings list
     * names the same view the hiding pass will hide.
     */
    fun inventorySummary(root: View): String =
        StockIconInventory.encode(
            inventory(root).map { StockIconInventoryItem(it.key, it.label, it.kind.name) }
        )

    /** The inventory entry for [view] under [root], or null when it is ignored or not an icon. */
    fun find(root: View, view: View): StockIcon? =
        inventory(root).firstOrNull { it.view === view }

    private fun walk(root: View, isRoot: Boolean, out: MutableList<StockIcon>, counts: MutableMap<String, Int>) {
        if (!isRoot) {
            if (isIgnored(root)) return
            val kind = kindOf(root)
            if (kind != StockIconKind.IGNORED) {
                val base = baseKey(root)
                val occurrence = (counts[base] ?: 0) + 1
                counts[base] = occurrence
                out.add(StockIcon(key(base, occurrence), labelOf(root), kind, root))
                return
            }
            if (root !is ViewGroup) return
        }
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            walk(root.getChildAt(i), isRoot = false, out = out, counts = counts)
        }
    }

    private fun kindOfToken(token: String?): StockIconKind {
        if (token.isNullOrBlank()) return StockIconKind.IGNORED
        return when {
            token.contains("battery") -> StockIconKind.BATTERY
            token.contains("combinedstatus") -> StockIconKind.COMBINED
            token.contains("wifi") -> StockIconKind.WIFI
            token.contains("mobile") || token.contains("cellular") || token.contains("signal") ->
                StockIconKind.CELLULAR
            ICON_HINTS.any { token.contains(it) } -> StockIconKind.OTHER
            else -> StockIconKind.IGNORED
        }
    }

    private fun labelForToken(token: String?): String {
        val clean = token?.trim()?.lowercase().orEmpty()
        if (clean.isEmpty()) return "Status icon"
        LABELS[clean]?.let { return it }
        return when {
            clean.contains("battery") -> "Battery"
            clean.contains("wifi") -> "Wi-Fi"
            clean.contains("mobile") || clean.contains("cellular") || clean.contains("signal") -> "Cellular"
            clean.contains("clock") -> "Clock"
            clean.contains("alarm") -> "Alarm"
            clean.contains("volume") || clean.contains("ringer") -> "Sound"
            clean.contains("bluetooth") -> "Bluetooth"
            clean.contains("combinedstatus") -> "Battery, Wi-Fi and cellular"
            else -> prettify(clean)
        }
    }

    /** `mobile_combo` → `Mobile Combo`; an unknown vendor token still reads as words, not a key. */
    private fun prettify(token: String): String {
        val words = token.split('_', '.', '-', ':', '#')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }
        return words.ifBlank { "Status icon" }
    }

    /** A container's name says so explicitly on every measured ROM; never hide the host itself. */
    private fun isContainerName(cls: String): Boolean =
        cls.contains("container") || cls.contains("layout") || cls.contains("group") ||
            cls.contains("iconarea") || cls.contains("strip") || cls.endsWith("icons")

    private fun isDuoOwned(view: View): Boolean =
        // Only Duo's own views (DuoRiveView, DuoCanvasView) are ignored by name. Matching the whole
        // module package would also ignore a stock icon view that a test double, a vendor subclass or
        // any future helper in this package happens to declare, hiding the wrong view.
        view.javaClass.simpleName.startsWith("Duo", ignoreCase = true)

    private fun isStatusDot(view: View): Boolean {
        val id = idName(view)?.lowercase()
        if (id != null && (id == "status_bar_dot" || id.contains("privacy_dot") || id.contains("privacy_chip"))) {
            return true
        }
        val slot = slotOf(view)?.trim()?.lowercase()
        if (slot != null && (slot == "status_bar_dot" || slot.startsWith("privacy"))) return true
        val cls = className(view)
        return cls.contains("statusbardot") || cls.contains("privacychip")
    }

    private fun isInnerSignal(view: View): Boolean {
        val id = idName(view)?.lowercase()
        if (id != null && id in INNER_IDS) return true
        val slot = slotOf(view)?.trim()?.lowercase()
        return slot != null && slot in INNER_SLOTS
    }

    /** `StatusBarIconView.getSlot()`, or the OEM `getSlotTag()`; null when neither exists. */
    private fun slotOf(view: View): String? = try {
        view.javaClass.getMethod("getSlot").invoke(view) as? String
    } catch (_: Throwable) {
        try {
            view.javaClass.getMethod("getSlotTag").invoke(view) as? String
        } catch (_: Throwable) {
            null
        }
    }

    /** The resource entry name, or null when the id is missing or belongs to another package. */
    private fun idName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            null
        }
    }

    private fun className(view: View): String =
        view.javaClass.simpleName.ifBlank { view.javaClass.name }.lowercase().ifBlank { "view" }
}
