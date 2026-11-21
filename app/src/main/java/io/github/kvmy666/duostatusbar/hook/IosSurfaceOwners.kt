package io.github.kvmy666.duostatusbar.hook

import android.view.View
import java.util.IdentityHashMap

/**
 * Process-wide ownership of one iOS drawing surface (rule 7: one property, one owner).
 *
 * A surface is identified by its name (`"main bar"`, `"shade header"`, ...) **and** by the strip view
 * instance the host writes into. The strip map is identity-based on purpose: a re-inflated strip is a
 * different view even when it carries the same resource id, so a dead host cannot keep blocking the
 * replacement the new tree needs. Both claims must be free (or already held by the same token) before a
 * host may touch the tree.
 *
 * The token is the host instance. A host releases its claim in teardown and on every failed attach;
 * [ownerOf] is the evidence for the "refused, already owned" log line.
 */
internal object IosSurfaceOwners {

    private class Owner(val surface: String, val token: Any)

    /** Surface names are equality keys (they come from literals and constants, not per-call objects). */
    private val surfaceOwners = HashMap<String, Owner>()

    /** Strip identity, so a re-inflated tree is a new surface the replacement may claim. */
    private val stripOwners = IdentityHashMap<View, Owner>()

    /**
     * Claims [surface] and [strip] for [token]. Idempotent for the same token; refused with false when
     * any part already belongs to a different token.
     */
    @Synchronized
    fun claim(surface: String, strip: View, token: Any): Boolean {
        val bySurface = surfaceOwners[surface]
        if (bySurface != null && bySurface.token !== token) return false
        val byStrip = stripOwners[strip]
        if (byStrip != null && byStrip.token !== token) return false
        val owner = bySurface ?: Owner(surface, token)
        surfaceOwners[surface] = owner
        stripOwners[strip] = owner
        return true
    }

    /** Releases [surface] for [token] and every strip it holds for that surface. Idempotent. */
    @Synchronized
    fun release(surface: String, token: Any) {
        val owner = surfaceOwners[surface] ?: return
        if (owner.token !== token) return
        surfaceOwners.remove(surface)
        val entries = stripOwners.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            if (entry.value.token === token && entry.value.surface == surface) entries.remove()
        }
    }

    /** The surface currently owning [strip], or null when the strip is free. */
    @Synchronized
    fun ownerOf(strip: View): String? = stripOwners[strip]?.surface

    /** The surface currently owning the named [surface], or null when it is free. */
    @Synchronized
    fun ownerOfSurface(surface: String): String? = surfaceOwners[surface]?.surface

    @Synchronized
    fun clearForTest() {
        surfaceOwners.clear()
        stripOwners.clear()
    }
}
