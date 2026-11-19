package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * A newer release, if one exists. [apkUrl] and [sha256] are what the download worker needs; both are
 * empty when the release carries no APK asset (the caller then only offers the release page).
 */
internal data class UpdateInfo(
    val version: String,
    val url: String,
    val apkUrl: String = "",
    val sha256: String = "",
    val prerelease: Boolean = false
)

/**
 * Finds the newest release, stable or pre-release.
 *
 * Two routes, in this order:
 *  1. the Cloudflare relay (`GET /releases`, configured as `TELEGRAM_RELAY_URL`), which caches GitHub's
 *     answer in KV so devices never touch GitHub's rate-limited API; and
 *  2. GitHub's own `/releases` list, as a fallback when the relay is unreachable.
 *
 * Version comparison is pre-release aware, so `1.4.0-beta.2` is offered over `1.4.0-beta.1` and a final
 * `1.4.0` over either. Everything is guarded: no network, no release, or any error means "no update",
 * never a crash, and parsing is pure so it is unit-tested.
 */
internal object UpdateChecker {

    const val RELEASES_PAGE = "https://github.com/kvmy666/duoStatusBar/releases"

    private const val GITHUB_API =
        "https://api.github.com/repos/kvmy666/duoStatusBar/releases?per_page=20"

    private val SEMVER = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.]+))?""")

    /** The newest release (stable or pre-release), or null when it cannot be read. */
    fun check(): UpdateInfo? = relayRelease() ?: githubRelease()

    // ------------------------------------------------------------------------------ sources

    /** Route 1: the relay's cached, normalized JSON. */
    private fun relayRelease(): UpdateInfo? {
        val base = BuildConfig.TELEGRAM_RELAY_URL.trim().trimEnd('/')
        if (base.isBlank()) return null
        return try {
            parseRelay(get("$base/releases"))
        } catch (t: Throwable) {
            L.w("relay update check: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /** Newest first, so `maxWith` picks the highest version across a release list. */
    private val NEWEST_FIRST = Comparator<UpdateInfo> { a, b ->
        when {
            isNewer(a.version, b.version) -> 1
            isNewer(b.version, a.version) -> -1
            else -> 0
        }
    }

    /** Route 2: GitHub's own release list, when the relay could not be used. */
    private fun githubRelease(): UpdateInfo? = try {
        parseGithubReleases(get(GITHUB_API))?.maxWithOrNull(NEWEST_FIRST)
    } catch (t: Throwable) {
        L.w("github update check: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "duoStatusBar")
        }
        try {
            if (connection.responseCode != 200) throw IllegalStateException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // ------------------------------------------------------------------------------ parsing

    /**
     * The relay's `{ ok, latest, releases }` body. Prefers `latest`; falls back to the highest of
     * `releases` so an older relay that omits `latest` still works.
     */
    fun parseRelay(body: String): UpdateInfo? {
        val root = JSONObject(body)
        root.optJSONObject("latest")?.let { if (it.has("version")) return fromRelay(it) }
        val releases = root.optJSONArray("releases") ?: return null
        return (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it)?.let(::fromRelay) }
            .maxWithOrNull(NEWEST_FIRST)
    }

    private fun fromRelay(o: JSONObject): UpdateInfo = UpdateInfo(
        version = o.optString("version"),
        url = o.optString("pageUrl").ifEmpty { RELEASES_PAGE },
        apkUrl = o.optString("apkUrl"),
        sha256 = o.optString("sha256").lowercase(),
        prerelease = o.optBoolean("prerelease", false)
    )

    /** GitHub's release array, newest-first as returned, mapped to [UpdateInfo]. Pure, unit-tested. */
    fun parseGithubReleases(body: String): List<UpdateInfo>? {
        val array = JSONArray(body)
        val out = ArrayList<UpdateInfo>(array.length())
        for (i in 0 until array.length()) {
            val r = array.optJSONObject(i) ?: continue
            if (r.optBoolean("draft", false)) continue
            val raw = r.optString("name").ifEmpty { r.optString("tag_name") }
            val version = parseVersion(raw) ?: continue
            out.add(
                UpdateInfo(
                    version = version,
                    url = r.optString("html_url").ifEmpty { RELEASES_PAGE },
                    apkUrl = apkAssetUrl(r),
                    sha256 = apkAssetSha(r),
                    prerelease = r.optBoolean("prerelease", false)
                )
            )
        }
        return out
    }

    private fun apkAssetUrl(release: JSONObject): String {
        val assets = release.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url")
            }
        }
        return ""
    }

    private fun apkAssetSha(release: JSONObject): String {
        val assets = release.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (!a.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val digest = a.optString("digest")
            Regex("""sha256:([0-9a-fA-F]{64})""").find(digest)?.let { return it.groupValues[1].lowercase() }
        }
        return ""
    }

    /** The first `x.y` / `x.y.z` / `x.y.z-pre` in [raw], or null. Pure, unit-tested. */
    fun parseVersion(raw: String): String? = SEMVER.find(raw)?.value

    /**
     * True when [remote] is a strictly higher version than [current]; pre-release aware.
     * Pure, unit-tested.
     */
    fun isNewer(remote: String, current: String): Boolean {
        val a = semver(remote) ?: return false
        val b = semver(current) ?: return false
        for (i in 0..2) if (a.nums[i] != b.nums[i]) return a.nums[i] > b.nums[i]
        // Same x.y.z: a release beats any pre-release, and pre-releases order by their suffix.
        if (a.pre.isEmpty() && b.pre.isNotEmpty()) return true
        if (a.pre.isNotEmpty() && b.pre.isEmpty()) return false
        return comparePre(a.pre, b.pre) > 0
    }

    private data class Semver(val nums: List<Int>, val pre: String)

    private fun semver(raw: String): Semver? {
        val m = SEMVER.find(raw) ?: return null
        return Semver(
            nums = listOf(
                m.groupValues[1].toIntOrNull() ?: 0,
                m.groupValues[2].toIntOrNull() ?: 0,
                m.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: 0
            ),
            pre = m.groupValues[4]
        )
    }

    /** Semver pre-release ordering: numeric identifiers sort before alphanumeric, absent is lower. */
    private fun comparePre(a: String, b: String): Int {
        if (a == b) return 0
        val xs = a.split('.')
        val ys = b.split('.')
        for (i in 0 until maxOf(xs.size, ys.size)) {
            val x = xs.getOrNull(i) ?: return -1
            val y = ys.getOrNull(i) ?: return 1
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            when {
                xn != null && yn != null -> if (xn != yn) return xn - yn
                xn != null -> return -1
                yn != null -> return 1
                x != y -> return if (x < y) -1 else 1
            }
        }
        return 0
    }
}
