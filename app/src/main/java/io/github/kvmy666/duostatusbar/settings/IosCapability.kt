package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings
import java.util.Locale

/**
 * The module's report of whether the iOS bar can own this device right now (R6/D13/D14).
 *
 * The module writes one `Settings.Global` string, which a normal app can read without a permission:
 *
 *     owner=<none|duo|ios> family=<id> supported=<true|false> route=<none|v2> rev=<long> pid=<int> at=<epoch-ms>
 *
 * This is derived state, not a setting: there is no extra settings column and the app never writes it.
 * A report is trusted only for the module session that produced it ([read]): a missing, stale or
 * malformed value is [UNKNOWN] and must not be reported as "unsupported" or "off".
 */
internal data class IosCapability(
    /** The real [FeatureSet] owner on this device: [OWNER_NONE], [OWNER_DUO] or [OWNER_IOS]. */
    val owner: String,
    /** The code id of the detected UI family (oxygenos, samsung, …); blank when unknown. */
    val family: String,
    /** iOS is deliverable on this device now: family + battery owner + engine installed. */
    val supported: Boolean,
    /** The live route: [ROUTE_NONE] while iOS is off, [ROUTE_V2] while it draws. */
    val route: String,
    /** The module's settings revision when the report was written. */
    val revision: Long,
    /** The System UI pid that wrote the report. */
    val pid: Int,
    /** When the report was written (wall clock ms). */
    val at: Long
) {
    /** The canonical encoding the module writes; [parse] accepts it back. */
    fun format(): String = buildString {
        append("owner=").append(owner)
        append(" family=").append(family)
        append(" supported=").append(if (supported) "true" else "false")
        append(" route=").append(route)
        append(" rev=").append(revision)
        append(" pid=").append(pid)
        append(" at=").append(at)
    }

    companion object {
        /** The `Settings.Global` key the module writes and this app reads. */
        const val KEY = "duo_statusbar_ios_capability"

        const val OWNER_NONE = "none"
        const val OWNER_DUO = "duo"
        const val OWNER_IOS = "ios"

        const val ROUTE_NONE = "none"
        const val ROUTE_V2 = "v2"

        /** The fields the module always writes; a report missing one of them is truncated. */
        private val REQUIRED = setOf("owner", "family", "supported", "route", "at")

        private val OWNERS = setOf(OWNER_NONE, OWNER_DUO, OWNER_IOS)
        private val ROUTES = setOf(ROUTE_NONE, ROUTE_V2)

        /**
         * Tolerant parser: unknown keys are ignored, whitespace separates fields, and anything that
         * cannot be read as the contract (missing required key, unknown owner/route, non-numeric
         * revision/pid/at) is malformed and returns null.
         */
        fun parse(raw: String?): IosCapability? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val fields = LinkedHashMap<String, String>()
            for (token in text.split(Regex("\\s+"))) {
                val separator = token.indexOf('=')
                if (separator <= 0) return null
                fields[token.substring(0, separator)] = token.substring(separator + 1)
            }
            if (!fields.keys.containsAll(REQUIRED)) return null
            val owner = fields.getValue("owner")
            if (owner !in OWNERS) return null
            val route = fields.getValue("route")
            if (route !in ROUTES) return null
            val supported = when (fields.getValue("supported")) {
                "true" -> true
                "false" -> false
                else -> return null
            }
            val revision = fields["rev"]?.let { it.toLongOrNull() ?: return null } ?: 0L
            val pid = fields["pid"]?.let { it.toIntOrNull() ?: return null } ?: 0
            val at = fields.getValue("at").toLongOrNull() ?: return null
            return IosCapability(
                owner = owner,
                family = fields.getValue("family"),
                supported = supported,
                route = route,
                revision = revision,
                pid = pid,
                at = at
            )
        }

        /**
         * Reads the report for the module session that stamped [loadedAt].
         *
         * A blank value, an unreadable/malformed value, or a report older than the current module load
         * is UNKNOWN (null): that is a different answer from "iOS unsupported" and must keep today's
         * behaviour (the switch usable, no health claim).
         */
        fun read(context: Context, loadedAt: Long): IosCapability? {
            if (loadedAt <= 0L) return null
            val raw = try {
                Settings.Global.getString(context.contentResolver, KEY)
            } catch (_: Throwable) {
                return null
            }
            val capability = parse(raw) ?: return null
            return if (capability.at >= loadedAt) capability else null
        }

        /**
         * The human UI name for a family code. Unknown codes are shown verbatim; a blank family means
         * the module could not identify the UI yet, so the copy says "this UI".
         */
        fun familyLabel(id: String?): String {
            val family = id?.trim().orEmpty()
            return when (family.lowercase(Locale.ROOT)) {
                "" -> "this UI"
                "oxygenos" -> "OxygenOS"
                "samsung" -> "One UI"
                "hyperos" -> "HyperOS"
                "nothing" -> "Nothing OS"
                "motorola" -> "MyOS"
                "vivo" -> "OriginOS"
                "aosp" -> "Android"
                "coloros" -> "ColorOS"
                "honor" -> "MagicOS"
                else -> family
            }
        }

        /** The value the iOS switch explanation formats into `settings_ios_unsupported_detail`. */
        fun availableHint(family: String?): String = familyLabel(family)
    }
}
