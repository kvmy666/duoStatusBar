package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The one-shot failure/branch report for the iOS v2 native path (REL210).
 *
 * Every guarded hook used to swallow its exception silently, so a report from an unknown phone could
 * not name the class or reason behind a stock-looking bar. This gate logs the FIRST occurrence of each
 * site only (a per-frame or per-layout failure must not flood the log), counts every occurrence for
 * the compact dump block, and keeps the first reason for the same block.
 *
 * Thread-safe by construction (a concurrent key set plus atomics) and never throws: logging must not
 * be the reason SystemUI dies (FR-21). [resetForTest] is the seam a unit test uses to start clean.
 */
internal object IosV2Log {

    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val failureCounter = AtomicInteger(0)
    private val retryCounter = AtomicInteger(0)
    private val firstLock = Any()

    @Volatile
    private var firstFailure: String? = null

    /** Unexpected failures seen across the audited sites (process lifetime). */
    val failures: Int get() = failureCounter.get()

    /** Expected boot retries seen across the audited sites (process lifetime). */
    val retries: Int get() = retryCounter.get()

    /** The first failure/retry reason as one ASCII token, or "none". */
    val first: String
        get() = synchronized(firstLock) { firstFailure } ?: "none"

    /** A guarded failure: count it, remember the first reason, and log the site once. */
    fun failureOnce(site: String, t: Throwable?) {
        try {
            failureCounter.incrementAndGet()
            val key = "failure:$site"
            if (seen.contains(key)) return
            val reason = describe(t)
            noteFirst("failure $site $reason")
            once(key) { L.w("ios v2 $site failed: $reason") }
        } catch (_: Throwable) {
            // A diagnostic must never be the reason the bar dies.
        }
    }

    /** A guarded failure without a throwable (a missing class/id or a refused branch). */
    fun failureOnce(site: String, reason: String) {
        try {
            failureCounter.incrementAndGet()
            val key = "failure:$site"
            if (seen.contains(key)) return
            val text = token(reason)
            noteFirst("failure $site $text")
            once(key) { L.w("ios v2 $site failed: $text") }
        } catch (_: Throwable) {
        }
    }

    /** An expected boot retry (no reading yet, a 0x0 slot): counted, logged once per site. */
    fun retry(site: String, reason: String) {
        try {
            retryCounter.incrementAndGet()
            val key = "retry:$site"
            if (seen.contains(key)) return
            val text = token(reason)
            noteFirst("retry $site $text")
            once(key) { L.i("ios v2 $site retry: $text") }
        } catch (_: Throwable) {
        }
    }

    /** A deliberate fallback decision ("stock kept", "index fallback", …), logged once per branch. */
    fun branchOnce(site: String, branch: String) {
        try {
            val key = "branch:$site:$branch"
            if (seen.contains(key)) return
            once(key) { L.i("ios v2 $site: ${token(branch)}") }
        } catch (_: Throwable) {
        }
    }

    /** Runs [block] the first time [key] is used; returns true when it ran. Never throws. */
    fun once(key: String, block: () -> Unit): Boolean = try {
        if (!seen.add(key)) {
            false
        } else {
            block()
            true
        }
    } catch (_: Throwable) {
        false
    }

    /** Test seam: clears the once gate, the counters and the remembered first reason. */
    internal fun resetForTest() {
        try {
            seen.clear()
            failureCounter.set(0)
            retryCounter.set(0)
            synchronized(firstLock) { firstFailure = null }
        } catch (_: Throwable) {
        }
    }

    private fun noteFirst(reason: String) {
        if (firstFailure != null) return
        synchronized(firstLock) {
            if (firstFailure == null) {
                firstFailure = reason
                    .replace(' ', '_')
                    .replace('\n', '_')
                    .replace('\r', '_')
                    .take(120)
            }
        }
    }

    private fun describe(t: Throwable?): String {
        val cls = t?.javaClass?.simpleName ?: "Throwable"
        val message = t?.message
        return if (message.isNullOrBlank()) cls else "$cls: $message"
    }

    /** One field token: no newlines, bounded, and cheap enough for a repeated guarded call. */
    private fun token(text: String): String {
        if (text.length <= 120 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) return text
        return text.replace('\n', '_').replace('\r', '_').take(120)
    }
}
