package io.github.kvmy666.duostatusbar.hook

/**
 * Pure classifier for SystemUI's panel expansion type name (T4a).
 *
 * The ROM reports a `PanelType` whose `toString()` is one of `QS_PANEL`, `NOTIFICATION_PANEL`,
 * `SHADE_PANEL` (or an aggregate). This object maps that name to an owner without touching any
 * SystemUI state, so the mapping is unit-testable. Matching is on whole tokens only: a name that
 * merely contains one of those spellings as a substring of a larger identifier does not count.
 */
internal enum class ShadeOwner { NONE, QS, NOTIFICATION }

internal object ShadeExpansionClassifier {

    fun ownerOf(typeName: String?): ShadeOwner {
        if (typeName == null) return ShadeOwner.NONE
        val tokens = tokenize(typeName)
        return when {
            "QS_PANEL" in tokens -> ShadeOwner.QS
            "NOTIFICATION_PANEL" in tokens -> ShadeOwner.NOTIFICATION
            else -> ShadeOwner.NONE
        }
    }

    private fun tokenize(value: String): Set<String> {
        val tokens = mutableSetOf<String>()
        val current = StringBuilder()
        for (c in value) {
            if (c.isLetterOrDigit() || c == '_') {
                current.append(c)
            } else {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.setLength(0)
                }
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}