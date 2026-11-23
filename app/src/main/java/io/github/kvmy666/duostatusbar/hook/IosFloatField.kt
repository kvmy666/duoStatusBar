package io.github.kvmy666.duostatusbar.hook

import java.lang.reflect.Field
import java.util.HashMap

/**
 * R6 item 5: one cached `float` field reader.
 *
 * The OPLUS composite draw reads `transXForCoord` on every frame. Resolving the field through
 * reflection on each draw (`XposedHelpers.getFloatField`) is main-thread work in the draw path, so a
 * [FloatFieldReader] resolves the [Field] **once** (at session creation) and its [FloatFieldReader.read]
 * only calls `field.getFloat` — no map lookup, no allocation, no synchronization per read.
 *
 * Missing, non-float, or unreadable fields yield no reader / `0f`, exactly like the previous guarded
 * call: a composite without the measured field simply draws untranslated (A1 §UNVERIFIED 10).
 */
internal object IosFloatField {

    private val cache = HashMap<Pair<Class<*>, String>, Field?>()

    /** A resolved field reader. [read] never throws and never allocates. */
    class FloatFieldReader internal constructor(private val field: Field) {
        fun read(target: Any): Float = try {
            field.getFloat(target)
        } catch (_: Throwable) {
            0f
        }
    }

    /**
     * Resolves `name` on [owner] into a reusable reader, or null when it is absent or not a primitive
     * `float` (the caller then reads 0f, i.e. no translation).
     */
    fun reader(owner: Class<*>, name: String): FloatFieldReader? {
        if (name.isBlank()) return null
        val field = resolve(owner, name) ?: return null
        if (field.type != Float::class.javaPrimitiveType) return null
        return FloatFieldReader(field)
    }

    private fun resolve(clazz: Class<*>, name: String): Field? = synchronized(cache) {
        val key = clazz to name
        if (cache.containsKey(key)) return cache[key]
        var current: Class<*>? = clazz
        var found: Field? = null
        while (current != null) {
            try {
                found = current.getDeclaredField(name)
                break
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            } catch (_: Throwable) {
                break
            }
        }
        try {
            found?.isAccessible = true
        } catch (_: Throwable) {
        }
        cache[key] = found
        found
    }

    /** Test seam: drops every cached field. */
    internal fun clearForTest() {
        synchronized(cache) {
            cache.clear()
        }
    }
}
