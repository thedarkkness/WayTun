package com.waytun.app.data.subscription

import com.waytun.app.core.parser.JsonSubscription
import com.waytun.app.core.text.Base64
import com.waytun.app.core.text.UrlCoding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

/** Panel-provided usage info from the `subscription-userinfo` header. */
data class SubscriptionUsage(
    val uploadBytes: Long?,
    val downloadBytes: Long?,
    val totalBytes: Long?,
    val expireEpochSeconds: Long?
)

data class SubscriptionResponse(
    val body: String,
    val title: String?,
    val usage: SubscriptionUsage?,
    val updateIntervalHours: Int?,
    val announce: String?,
    val supportUrl: String?,
    val webPageUrl: String?,
    /** Happ's "description": `profile-description`, or the lines after the first in a multi-line `base64:` title. */
    val description: String? = null,
    val announceUrl: String? = null,
    val supportEmail: String? = null,
    /** Custom `bot-link` header: a dedicated bot/chat link, separate from `support-url`. */
    val botLink: String? = null
)

/** Per-subscription request options from its settings screen. */
data class FetchOptions(
    val userAgent: String? = null,
    val sendHwid: Boolean = true,
    val timeoutSeconds: Int = 15
)

/** A non-2xx answer from the panel; the message ("HTTP 404") is what the user sees. */
class SubscriptionHttpException(message: String) : Exception(message)

/**
 * Downloads a subscription the way Happ does: always sending the HWID/device headers panels use
 * for device counting and limits, and reading the panel's metadata headers back.
 *
 * [deviceHeaders] are the platform's User-Agent and `x-hwid`/`x-device-os`/`x-ver-os`/`x-device-model` values.
 */
class SubscriptionClient(
    private val http: HttpClient,
    private val deviceHeaders: () -> Map<String, String>
) {

    suspend fun fetch(url: String, options: FetchOptions = FetchOptions()): SubscriptionResponse = try {
        fetchInternal(url, options)
    } catch (e: SubscriptionHttpException) {
        throw e
    } catch (e: Exception) {
        // Some network errors quote the whole request URL, and a subscription URL carries the user's access token:
        // keep it out of messages that end up on screen.
        val message = e.message
        if (message != null && message.contains(url.substringBefore('?'))) throw SubscriptionNetworkException(redact(message, url), e) else throw e
    }

    private suspend fun fetchInternal(url: String, options: FetchOptions): SubscriptionResponse {
        var headers = deviceHeaders()
        if (!options.sendHwid) headers = headers - setOf("x-hwid", "x-device-os", "x-ver-os", "x-device-model")
        options.userAgent?.takeIf { it.isNotBlank() }?.let { headers = headers + ("User-Agent" to it) }
        val timeoutMs = options.timeoutSeconds.coerceIn(5, 15) * 1000
        var response = request(url, headers, timeoutMs)
        // Some panels answer unknown clients with their HTML subscription page instead of the
        // link list; retry once as a widely recognized client so those still work.
        if (response.body.looksLikeHtml()) {
            response = request(url, headers + ("User-Agent" to FALLBACK_USER_AGENT), timeoutMs)
        }
        // Panels that decide the format by User-Agent hand an unknown client plain links. Remnawave also serves the full
        // Xray JSON (the panel's routing and DNS included) on an explicit client-type path, so ask for that by address
        // instead of pretending to be another app.
        val bare = url.substringBefore('?')
        val query = url.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        if (!JsonSubscription.looksLikeJson(response.body) && EXPLICIT_FORMAT_PATH.containsMatchIn(pathOf(url))) {
            for (clientType in listOf("json", "v2ray-json")) {
                val explicit = runCatching { request(bare.trimEnd('/') + "/" + clientType + query, headers, timeoutMs) }.getOrNull()
                    ?.takeIf { JsonSubscription.looksLikeJson(it.body) && JsonSubscription.countEntries(it.body) > 0 }
                    ?: continue
                return explicit.copy(title = explicit.title ?: response.title, usage = explicit.usage ?: response.usage)
            }
        }
        return response
    }

    private suspend fun request(url: String, headers: Map<String, String>, timeoutMs: Int): SubscriptionResponse {
        val answer = http.get(url) {
            timeout {
                connectTimeoutMillis = timeoutMs.toLong()
                socketTimeoutMillis = timeoutMs.toLong()
            }
            this.headers["Accept"] = "*/*"
            headers.forEach { (name, value) -> this.headers[name] = value }
        }
        val code = answer.status.value
        if (code !in 200..299) throw SubscriptionHttpException("HTTP $code")
        val body = answer.bodyAsBytes().decodeToString()
        val meta = bodyMetadata(body)
        // Like HttpURLConnection.getHeaderField: the last value of a repeated header.
        fun header(name: String) = answer.headers.getAll(name)?.lastOrNull()?.trim()?.ifEmpty { null }
        // Headers win over the `#key: value` lines panels can put at the top of the body.
        val rawTitle = header("profile-title") ?: header("subscription-name") ?: meta["profile-title"]
            ?: header("content-disposition")?.let(::filenameOf)
        val titleText = rawTitle?.let(::decodeTitle)
        return SubscriptionResponse(
            body = body,
            title = titleText?.lineSequence()?.firstOrNull()?.trim()?.ifEmpty { null },
            usage = header("subscription-userinfo")?.let(::parseUsage),
            updateIntervalHours = (header("profile-update-interval") ?: meta["profile-update-interval"])?.toIntOrNull(),
            announce = (header("announce") ?: meta["announce"])?.let(::decodeTitle),
            supportUrl = header("support-url") ?: meta["support-url"],
            webPageUrl = header("profile-web-page-url") ?: meta["profile-web-page-url"] ?: header("homepage"),
            description = header("profile-description")?.let(::decodeTitle)
                ?: titleText?.lines()?.drop(1)?.joinToString("\n")?.trim()?.ifEmpty { null },
            announceUrl = header("announce-url") ?: meta["announce-url"],
            supportEmail = header("support-email"),
            botLink = header("bot-link") ?: meta["bot-link"]
        )
    }

    /** java.net.URL.getPath(): the part after the authority, without query or fragment. */
    private fun pathOf(url: String): String {
        val rest = url.substringAfter("://", url)
        val slash = rest.indexOf('/')
        return if (slash < 0) "" else rest.substring(slash).substringBefore('?').substringBefore('#')
    }

    private fun redact(message: String, url: String): String {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringAfterLast('@')
        return message.replace(url, host).replace(url.substringBefore('?'), host)
    }

    private fun String.looksLikeHtml(): Boolean {
        val head = trimStart().take(64).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    /** `#profile-title: ...` style metadata lines at the top of the body (comments for everything else). */
    private fun bodyMetadata(body: String): Map<String, String> {
        if (body.trimStart().startsWith("[") || body.trimStart().startsWith("{")) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (line in body.lineSequence().take(40)) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#")) continue
            val key = trimmed.removePrefix("#").substringBefore(':').trim().lowercase()
            val value = trimmed.substringAfter(':', "").trim()
            if (key in BODY_KEYS && value.isNotEmpty() && key !in result) result[key] = value
        }
        return result
    }

    private fun filenameOf(disposition: String): String? =
        Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)
            ?.let { runCatching { UrlCoding.decode(it) }.getOrDefault(it) }
            ?.removeSuffix(".txt")?.removeSuffix(".yaml")?.removeSuffix(".yml")?.trim()?.ifEmpty { null }

    companion object {
        private val BODY_KEYS = setOf(
            "announce", "profile-title", "support-url", "profile-web-page-url", "announce-url", "profile-update-interval", "bot-link"
        )
        private val EXPLICIT_FORMAT_PATH = Regex("^(/api)?(/sub)?/[^/.]+/?$")
        private const val FALLBACK_USER_AGENT = "v2rayNG/1.10.5"

        /** Text headers are either plain or `base64:<base64 of UTF-8 text>`. */
        internal fun decodeTitle(raw: String): String? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            if (!value.startsWith("base64:", ignoreCase = true)) return value
            return runCatching {
                Base64.decode(value.substringAfter(':'), Base64.DEFAULT).decodeToString().trim()
            }.getOrNull()?.ifEmpty { null }
        }

        /** `upload=123; download=456; total=789; expire=1700000000` - any field may be missing or 0. */
        internal fun parseUsage(raw: String): SubscriptionUsage? {
            val fields = raw.split(';').mapNotNull { part ->
                val key = part.substringBefore('=', "").trim().lowercase()
                val value = part.substringAfter('=', "").trim().toDoubleOrNull()?.toLong()
                if (key.isEmpty() || value == null) null else key to value
            }.toMap()
            if (fields.isEmpty()) return null
            return SubscriptionUsage(
                uploadBytes = fields["upload"],
                downloadBytes = fields["download"],
                totalBytes = fields["total"]?.takeIf { it > 0 },
                // Some panels send milliseconds.
                expireEpochSeconds = fields["expire"]?.takeIf { it > 0 }?.let { if (it > 32_000_000_000L) it / 1000 else it }
            )
        }
    }
}

/** A network failure whose original message quoted the subscription URL; the URL is replaced by its host. */
class SubscriptionNetworkException(message: String, cause: Throwable) : Exception(message, cause)
