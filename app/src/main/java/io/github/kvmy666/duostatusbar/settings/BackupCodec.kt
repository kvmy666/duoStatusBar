package io.github.kvmy666.duostatusbar.settings

import org.json.JSONObject

/**
 * Pure (Android-free) codec for the Duo settings backup/import format.
 *
 * The format is one versioned JSON object:
 *   { "schema": "duo-settings/1", "version": 1, "exportedAt": <ms>, "values": { key: typed value } }
 *
 * Only boolean/int/long/String values are supported; everything else is rejected so a corrupt or
 * foreign file cannot silently overwrite a setting with something the app cannot read back.
 */
object BackupCodec {

    const val SCHEMA = "duo-settings/1"
    const val schema = SCHEMA
    const val VERSION = 1
    const val version = VERSION

    const val BAD_JSON = "BAD_JSON"
    const val UNSUPPORTED_SCHEMA = "UNSUPPORTED_SCHEMA"
    const val UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION"
    const val EMPTY = "EMPTY"
    const val BAD_VALUE = "BAD_VALUE"

    enum class ErrorCode {
        BAD_JSON, UNSUPPORTED_SCHEMA, UNSUPPORTED_VERSION, EMPTY, BAD_VALUE
    }

    sealed class Result {
        abstract val values: Map<String, Any?>?
        abstract val warnings: List<String>
        abstract val error: String?

        val code: String? get() = error
        val errorCode: ErrorCode? get() = error?.let { runCatching { ErrorCode.valueOf(it) }.getOrNull() }
        val isSuccess: Boolean get() = error == null && values != null

        data class Success(
            override val values: Map<String, Any?>,
            override val warnings: List<String> = emptyList()
        ) : Result() {
            override val error: String? = null
        }

        data class Error(
            override val error: String,
            override val warnings: List<String> = emptyList()
        ) : Result() {
            override val values: Map<String, Any?>? = null
        }

    }

    fun encode(values: Map<String, Any?>): String {
        val root = JSONObject()
        root.put("schema", SCHEMA)
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        val vals = JSONObject()
        for ((key, value) in values) {
            when (value) {
                null -> vals.put(key, JSONObject.NULL)
                is Boolean -> vals.put(key, value)
                is Int -> vals.put(key, value)
                is Long -> vals.put(key, JSONObject().put("\$long", value))
                is String -> vals.put(key, value)
                else -> { /* skip unsupported types */ }
            }
        }
        root.put("values", vals)
        return root.toString()
    }

    fun decode(json: String): Result {
        if (json.isBlank()) return Result.Error(EMPTY)
        val root = try {
            JSONObject(json)
        } catch (_: Throwable) {
            return Result.Error(BAD_JSON)
        }
        val schemaVal = root.opt("schema")
        if (schemaVal !is String || schemaVal != SCHEMA) {
            return Result.Error(UNSUPPORTED_SCHEMA)
        }
        val versionVal = root.opt("version")
        if (versionVal !is Int || versionVal != VERSION) {
            return Result.Error(UNSUPPORTED_VERSION)
        }
        val valuesObj = root.optJSONObject("values")
            ?: return Result.Error(BAD_JSON)
        if (valuesObj.length() == 0) return Result.Error(EMPTY)

        val out = LinkedHashMap<String, Any?>()
        val keys = valuesObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val v = valuesObj.opt(key)
            val typed = when (v) {
                JSONObject.NULL -> null
                is Boolean -> v
                is Int -> v
                is Long -> v
                is String -> v
                is JSONObject -> {
                    if (v.length() != 1 || !v.has("\$long")) return Result.Error(BAD_VALUE)
                    val inner = v.opt("\$long")
                    if (inner !is Long && inner !is Int) return Result.Error(BAD_VALUE)
                    v.getLong("\$long")
                }
                else -> return Result.Error(BAD_VALUE)
            }
            out[key] = typed
        }
        return Result.Success(out)
    }

    fun sanitize(values: Map<String, Any?>): Map<String, Any?> {
        val allowed = DuoPrefs.PORTRAIT_COLUMNS.toSet() +
            DuoPrefs.LANDSCAPE_COLUMNS.toSet() +
            setOf("hide_stock_icons", "check_updates", "landscape_set")
        val excluded = setOf(
            "revision", "last_dump", "last_status", "status_history", "last_fallback",
            "root_allowed", "log_sent_at", "update_notified", "update_check_at"
        )
        val out = LinkedHashMap<String, Any?>()
        for ((key, value) in values) {
            if (key in excluded) continue
            if (key !in allowed) continue
            if (value != null && value !is Boolean && value !is Int && value !is Long && value !is String) continue
            out[key] = value
        }
        return out
    }
}
