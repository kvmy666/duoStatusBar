package io.github.kvmy666.duostatusbar.settings

/**
 * The secure `icon_blacklist` value, as pure list maths (issue #4).
 *
 * AOSP SystemUI reads this comma-separated list of status-bar icon slots and drops the matching icons.
 * `null`, an empty string and the literal `null` all mean "nothing is hidden", which is what
 * `settings get` prints when the setting has never been written.
 *
 * Kept pure and separate so the merge/remove rules — the part that silently corrupts a user's blacklist
 * if it is wrong — are unit-tested rather than only exercised on a device.
 */
internal object IconBlacklist {

    /** The slots the Duo element replaces: the phone's own Wi-Fi, cellular and battery icons. */
    val KEYS = listOf("wifi", "mobile", "battery")

    fun parse(raw: String?): List<String> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.equals("null", ignoreCase = true)) return emptyList()
        return text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** [raw] with [keys] present, preserving any other entries the user or another app set. */
    fun withKeys(raw: String?, keys: List<String> = KEYS): String =
        (parse(raw) + keys).distinct().joinToString(",")

    /** [raw] with [keys] removed, leaving every other entry untouched. */
    fun withoutKeys(raw: String?, keys: List<String> = KEYS): String =
        parse(raw).filterNot { it in keys }.joinToString(",")

    fun hasKeys(raw: String?, keys: List<String> = KEYS): Boolean =
        parse(raw).containsAll(keys)
}
