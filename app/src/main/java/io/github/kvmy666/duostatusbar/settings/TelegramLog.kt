package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

/**
 * One-tap bug-report delivery.
 *
 * Preferred route: a Cloudflare Worker relay ([BuildConfig.TELEGRAM_RELAY_URL]) holds the Telegram bot
 * token as a server secret, so the APK carries nothing secret; the app POSTs the report there and the
 * relay forwards it to the developer's chat. Fallback route: a directory bot token compiled from `.env`
 * ([BuildConfig.TELEGRAM_BOT_TOKEN]), used only for local/private builds. With neither configured the
 * caller falls back to the system share sheet.
 *
 * Everything is best-effort: no config, no network, or a rejection returns a non-SENT result. Never throws.
 */
internal object TelegramLog {

    /** What happened when a send was attempted. */
    enum class Result { SENT, RATE_LIMITED, FAILED }

    data class SendResult(val result: Result, val ticket: String? = null)

    private const val API = "https://api.telegram.org"

    /**
     * Hidden cooldown between successful sends. Repeated taps (or a stuck finger) cannot spam the bot,
     * and it survives an app restart because the last-sent time is persisted.
     */
    internal const val COOLDOWN_MS = 60_000L

    /** Whether a relay URL or a bot token was compiled in. False means the share sheet is used. */
    fun configured(): Boolean =
        BuildConfig.TELEGRAM_RELAY_URL.isNotBlank() || BuildConfig.TELEGRAM_BOT_TOKEN.isNotBlank()

    /**
     * True while [now] is inside the cooldown that started at [lastSentAt]. Pure, so the "the button
     * cannot spam" rule is pinned by a unit test rather than only exercised on a phone.
     */
    internal fun withinCooldown(lastSentAt: Long, now: Long): Boolean =
        (now - lastSentAt) in 0 until COOLDOWN_MS

    /** Uploads [file] and returns the sequential ticket assigned by the relay (if any). */
    fun sendWithTicket(context: Context, file: File, caption: String): SendResult {
        val relay = BuildConfig.TELEGRAM_RELAY_URL
        val token = BuildConfig.TELEGRAM_BOT_TOKEN
        val relayKey = BuildConfig.TELEGRAM_RELAY_KEY
        if (relay.isBlank() && token.isBlank()) return SendResult(Result.FAILED)

        val now = System.currentTimeMillis()
        if (withinCooldown(DuoPrefs.logSentAt(context), now)) {
            L.i("telegram: rate limited")
            return SendResult(Result.RATE_LIMITED)
        }

        val body = if (relay.isNotBlank() && relayKey.isNotBlank()) {
            // Preferred: the Worker holds the token, the app holds nothing secret. The Worker also
            // enforces its own per-IP cooldown, which a modified app cannot bypass. The relay is
            // secured: it rejects requests without a matching X-Duo-Key, so the key must be present.
            postMultipart(
                relay,
                file,
                caption,
                extraFields = emptyMap(),
                headers = mapOf("X-Duo-Key" to relayKey)
            )
        } else if (token.isNotBlank()) {
            val chatId = BuildConfig.TELEGRAM_CHAT_ID.ifBlank {
                discoverChatId(token) ?: return SendResult(Result.FAILED)
            }
            postMultipart(
                "$API/bot$token/sendDocument",
                file,
                caption,
                extraFields = mapOf("chat_id" to chatId)
            )
        } else {
            // Relay URL is configured but no key is baked in: do not send an unauthenticated
            // request. This is a configuration error, not a network failure.
            L.w("telegram: relay key missing; not sending unauthenticated request")
            null
        }
        if (body == null) return SendResult(Result.FAILED)
        DuoPrefs.writeLogSentAt(context, now)
        val ticket = try {
            JSONObject(body).optString("ticket").takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
        return SendResult(Result.SENT, ticket)
    }

    /** Uploads [file] to the developer's chat, honouring the hidden client-side cooldown. */
    fun send(context: Context, file: File, caption: String): Result =
        sendWithTicket(context, file, caption).result

    /** The chat id of the most recent message sent to the bot, or null. See the class note. */
    private fun discoverChatId(token: String): String? = try {
        val result = JSONObject(httpGet("$API/bot$token/getUpdates")).optJSONArray("result")
        var id: String? = null
        if (result != null) {
            for (i in 0 until result.length()) {
                val chat = result.getJSONObject(i).optJSONObject("message")?.optJSONObject("chat")
                if (chat != null && chat.has("id")) id = chat.getLong("id").toString()
            }
        }
        id
    } catch (t: Throwable) {
        L.w("telegram getUpdates: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    /**
     * POSTs the log as `multipart/form-data`. [extraFields] are added as form fields (the direct bot path
     * sends `chat_id`; the relay needs none). Returns whether the endpoint accepted it.
     */
    private fun postMultipart(
        url: String,
        file: File,
        caption: String,
        extraFields: Map<String, String>,
        headers: Map<String, String> = emptyMap()
    ): String? = try {
        val boundary = "----duo" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            for ((name, value) in headers) setRequestProperty(name, value)
        }
        conn.outputStream.use { out ->
            for ((name, value) in extraFields) field(out, boundary, name, value)
            if (caption.isNotBlank()) field(out, boundary, "caption", caption)
            filePart(out, boundary, "document", file)
            out.write("--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }
        if (code !in 200..299) {
            L.w("log upload HTTP $code: ${body?.take(200)}")
        } else {
            L.i("log delivered (${file.length()} bytes)")
        }
        conn.disconnect()
        if (code in 200..299) body else null
    } catch (t: Throwable) {
        L.w("telegram send: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    private fun field(out: OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        out.write(value.toByteArray())
        out.write("\r\n".toByteArray())
    }

    private fun filePart(out: OutputStream, boundary: String, name: String, file: File) {
        val contentType = if (file.name.endsWith(".zip", ignoreCase = true)) {
            "application/zip"
        } else {
            "text/plain"
        }
        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n".toByteArray()
        )
        out.write("Content-Type: $contentType\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.write("\r\n".toByteArray())
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
