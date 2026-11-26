package io.github.kvmy666.duostatusbar.hook.rom

/**
 * Canonical ROM family ids and the legacy alias -> canonical mapping (R15).
 *
 * The code adapters and the data profiles used to spell the same family two ways (`oneui` in the asset,
 * `samsung` in [RomDetection]; likewise originos/vivo, magicos/honor, myos/motorola, nothingos/nothing).
 * Two spellings meant the alias could never be matched against [RomDetection.FAMILY_IDS] or a marker, so
 * everything now resolves through [canonical].
 */
internal object RomFamilies {

    /** Old asset ids and the canonical family they name. */
    val ALIASES = mapOf(
        "oneui" to "samsung",
        "originos" to "vivo",
        "magicos" to "honor",
        "myos" to "motorola",
        "nothingos" to "nothing"
    )

    /** Trim and lowercase an id, then map a known alias to its canonical family. Unknown passes through. */
    fun canonical(id: String): String {
        val normalized = id.trim().lowercase()
        return ALIASES[normalized] ?: normalized
    }

    /** The one id per family the code and the shipped asset must use. */
    val CANONICAL_IDS = setOf(
        "oxygenos", "coloros", "samsung", "hyperos", "vivo", "honor", "motorola", "nothing", "aosp"
    )
}
