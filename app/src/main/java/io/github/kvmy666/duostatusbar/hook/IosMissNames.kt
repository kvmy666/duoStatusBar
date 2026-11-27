package io.github.kvmy666.duostatusbar.hook

import java.util.concurrent.atomic.AtomicInteger

/**
 * R17C: the bounded table of stock resource ids that fell through the live iOS v2 map.
 *
 * The measured problem (`.pm/evidence/u1127/duo-report-20261126-225719/diagnostics-dump.txt:980`)
 * is that the map is published after the first icon loads, so the bar's own Wi-Fi/cell/type loads are
 * misses whose names never reached a report. Resolving a resource name is not free, so a miss is named
 * once per id, only in the non-OxygenOS logging path, and the table is capped.
 *
 * No locks: [claimed] hands out a slot, and a racing publisher may briefly expose a lower [size], so a
 * concurrent record can duplicate an entry or lose one count. It can never throw, resolve a name
 * for an already-tracked id, or run more than [max] name resolutions. Diagnostics being approximate is the
 * accepted tradeoff; correctness of the bar is not affected.
 */
internal class IosMissNames(private val max: Int = 64) {

    private val ids = IntArray(max)
    private val counts = IntArray(max)
    private val names = arrayOfNulls<String>(max)
    private val claimed = AtomicInteger(0)

    /** Number of published slots, read once per scan so a record never walks a slot being written. */
    @Volatile
    private var size = 0

    /**
     * Tracks [id] and returns its resolved [resolve] name only the first time it is seen; a repeat
     * increments the count and returns null. Never throws: a failed [resolve] stores a null name.
     */
    fun record(id: Int, resolve: () -> String?): String? {
        val published = size
        for (i in 0 until published) {
            if (ids[i] == id) {
                counts[i]++
                return null
            }
        }
        val slot = claimed.getAndIncrement()
        if (slot >= max) return null
        ids[slot] = id
        val name = try {
            resolve()
        } catch (t: Throwable) {
            null
        }
        names[slot] = name
        counts[slot] = 1
        // Best-effort publication. getAndIncrement hands out increasing slots, so the common order
        // already advances size; a late writer must never shrink it below a slot already visible.
        if (slot + 1 > size) size = slot + 1
        return name
    }

    /** Named entries sorted by count (descending) then name, capped at [limit]. */
    fun top(limit: Int): List<Pair<String, Int>> {
        if (limit <= 0) return emptyList()
        val published = size
        val out = ArrayList<Pair<String, Int>>(published)
        for (i in 0 until published) {
            val name = names[i]
            if (name.isNullOrBlank()) continue
            out.add(name to counts[i])
        }
        return out
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
    }
}

/** R17C: the one additive miss line; OxygenOS never gets it (its report is frozen). */
internal fun missTopLine(family: String, top: List<Pair<String, Int>>): String =
    "ios v2 miss top: family=$family " +
        "names=${top.joinToString(",") { it.first }} " +
        "counts=${top.joinToString(",") { it.second.toString() }}"
