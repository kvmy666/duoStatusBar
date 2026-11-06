package io.github.kvmy666.duostatusbar.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * One stock status-bar icon the module found in the live bar (A4).
 *
 * This is the transport shape only: it carries no [android.view.View], just the stable [key] the
 * hiding setting stores, the human [label] and the [kind] name. Both sides of the settings channel
 * therefore agree on the list even though only System UI can read the view tree.
 */
internal data class StockIconInventoryItem(
    val key: String,
    val label: String,
    val kind: String
)

/**
 * The compact JSON the module publishes and the app lists.
 *
 * A CSV cannot carry a label such as `Battery, Wi-Fi and cellular` without quoting rules that both
 * sides would have to implement, so the payload is a JSON array of `{key,label,kind}` objects. The
 * encoder is canonical (trimmed, de-duplicated, bounded) and the decoder is deliberately forgiving:
 * a truncated or unknown payload becomes an empty list, never an exception in System UI or the app.
 */
internal object StockIconInventory {

    /** Enough for every icon any measured bar has shown; a runaway tree cannot bloat the broadcast. */
    const val MAX_ITEMS = 64
    const val MAX_KEY_LENGTH = 160
    const val MAX_LABEL_LENGTH = 96
    const val MAX_KIND_LENGTH = 24

    /** Encodes [items] as a compact JSON array, dropping blanks, duplicates and overlong fields. */
    fun encode(items: List<StockIconInventoryItem>): String {
        val array = JSONArray()
        val seen = HashSet<String>()
        for (item in items) {
            if (array.length() >= MAX_ITEMS) break
            val key = item.key.trim().take(MAX_KEY_LENGTH)
            if (key.isEmpty() || !seen.add(key)) continue
            val label = item.label.trim().take(MAX_LABEL_LENGTH).ifEmpty { key }
            val kind = item.kind.trim().uppercase().take(MAX_KIND_LENGTH)
            array.put(
                JSONObject()
                    .put("key", key)
                    .put("label", label)
                    .put("kind", kind)
            )
        }
        return array.toString()
    }

    /** Parses [raw], returning only valid entries; anything malformed yields an empty list. */
    fun decode(raw: String?): List<StockIconInventoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val out = ArrayList<StockIconInventoryItem>()
            val seen = HashSet<String>()
            val limit = minOf(array.length(), MAX_ITEMS)
            for (i in 0 until limit) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("key").trim().take(MAX_KEY_LENGTH)
                if (key.isEmpty() || !seen.add(key)) continue
                val label = obj.optString("label").trim().take(MAX_LABEL_LENGTH).ifEmpty { key }
                val kind = obj.optString("kind").trim().uppercase().take(MAX_KIND_LENGTH)
                out.add(StockIconInventoryItem(key = key, label = label, kind = kind))
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/**
 * The pure state rules behind the per-icon list (A4). No Compose, no Android: the screen renders what
 * these return, and the tests pin the three cases that matter — "All", one key, and a key the live
 * inventory no longer contains.
 */
internal object StockIconSelection {

    /** The keys currently selected in [settings.hiddenStockIcons]. */
    fun hiddenKeys(settings: DuoSettings): Set<String> =
        DuoPrefs.parseHiddenStockIcons(settings.hiddenStockIcons).toSet()

    /** True when the specific [key] is selected. The legacy "All" switch is a separate override. */
    fun isHidden(settings: DuoSettings, key: String): Boolean = key in hiddenKeys(settings)

    /**
     * Turns one inventory row on or off. A key that is not in [inventory] is a no-op: a stale UI can
     * never write a key the module no longer publishes (and therefore cannot hide the wrong view).
     */
    fun toggle(
        settings: DuoSettings,
        inventory: List<StockIconInventoryItem>,
        key: String,
        hidden: Boolean
    ): DuoSettings {
        if (inventory.none { it.key == key }) return settings
        val keys = hiddenKeys(settings)
        val next = if (hidden) keys + key else keys - key
        if (next == keys) return settings
        return settings.copy(hiddenStockIcons = DuoPrefs.joinHiddenStockIcons(next))
    }

    /** The "All" switch writes [DuoSettings.hideOtherIcons] and leaves the per-key selection alone. */
    fun withAll(settings: DuoSettings, all: Boolean): DuoSettings =
        if (settings.hideOtherIcons == all) settings else settings.copy(hideOtherIcons = all)
}
