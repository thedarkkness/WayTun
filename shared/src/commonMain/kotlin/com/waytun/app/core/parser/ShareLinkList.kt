package com.waytun.app.core.parser

import com.waytun.app.core.text.Base64

/**
 * Pulls every supported share link (vless://, vmess://, trojan://) out of free-form text: one
 * link per line as pasted from a panel/chat, or the base64-wrapped block that subscription
 * endpoints return.
 */
object ShareLinkList {

    fun extract(text: String): List<String> {
        if (JsonSubscription.looksLikeJson(text)) return JsonSubscription.entries(text)
        val direct = links(text)
        if (direct.isNotEmpty()) return direct
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty() || compact.contains("://")) return emptyList()
        return decodeBase64(compact)?.let(::links).orEmpty()
    }

    /** Lines that look like links of any scheme - used to report how many were skipped as unsupported. */
    fun countAllLinks(text: String): Int {
        if (JsonSubscription.looksLikeJson(text)) return JsonSubscription.countEntries(text)
        val body = if (text.contains("://")) text else decodeBase64(text.filterNot { it.isWhitespace() }) ?: text
        return body.lines().count { Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(it.trim()) }
    }

    private fun links(text: String): List<String> =
        text.lines().map { it.trim() }.filter { SingBoxLinkParser.canParse(it) }

    private fun decodeBase64(value: String): String? {
        val padded = value.padEnd(value.length + (4 - value.length % 4) % 4, '=')
        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            val decoded = runCatching { Base64.decode(padded, flags).decodeToString() }.getOrNull()
            if (decoded != null && decoded.contains("://")) return decoded
        }
        return null
    }
}
