package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** A newer release, if one exists. */
internal data class UpdateInfo(val version: String, val url: String)

/**
 * Checks GitHub for a newer release.
 *
 * There is no server of our own, so "is there an update?" is read straight from the repo's latest
 * release: `tag_name`/`name` carries the version (`v1.1.0`, or `6-1.1.0` when the tag is prefixed with
 * the version code), and `html_url` is where the user should go. Everything is guarded — no network, no
 * release, or any error simply means "no update", never a crash.
 */
internal object UpdateChecker {

    const val RELEASES_PAGE = "https://github.com/kvmy666/duoStatusBar/releases/latest"
    private const val API = "https://api.github.com/repos/kvmy666/duoStatusBar/releases/latest"

    private val SEMVER = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

    /** The latest release, or null when it cannot be read or there is none. */
    fun check(): UpdateInfo? = try {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "duoStatusBar")
        }
        try {
            if (connection.responseCode != 200) {
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                // Prefer the human name (`v1.1.0`); the tag may be `6-1.1.0`, so pull the semver out.
                val raw = json.optString("name").ifEmpty { json.optString("tag_name") }
                val version = parseVersion(raw)
                if (version == null) {
                    L.w("update check: no version in '$raw'")
                    null
                } else {
                    UpdateInfo(version, json.optString("html_url").ifEmpty { RELEASES_PAGE })
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (t: Throwable) {
        L.w("update check: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    /** A newer release than the installed one, or null. */
    fun updateAvailable(): UpdateInfo? {
        val info = check() ?: return null
        return if (isNewer(info.version, BuildConfig.VERSION_NAME)) info else null
    }

    /** The first `x.y` / `x.y.z` in [raw], or null. Pure, so the parsing is unit-tested. */
    fun parseVersion(raw: String): String? = SEMVER.find(raw)?.value

    /** True when [remote] is a strictly higher version than [current]. Pure, unit-tested. */
    fun isNewer(remote: String, current: String): Boolean {
        val a = parseVersion(remote)?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
        val b = parseVersion(current)?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
