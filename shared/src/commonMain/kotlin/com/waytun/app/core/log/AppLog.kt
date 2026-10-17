package com.waytun.app.core.log

data class LogFileInfo(val name: String, val bytes: Long, val modifiedMillis: Long)

/** Where the journals live: files on Android and desktop. */
interface LogStore {
    /** Appends [line]; when the file has grown past [maxBytes] it is first cut down to its newest [keepBytes]. */
    fun append(name: String, line: String, maxBytes: Long, keepBytes: Int)
    fun read(name: String, maxBytes: Int): String
    fun clear(name: String)
    fun info(name: String): LogFileInfo?
}

/**
 * A small on-device journal the user can read in Settings: what happened with subscriptions and connections.
 * It never stores addresses, keys or tokens - every message is scrubbed before it is written - and each file is
 * trimmed to its newest part when it grows, so it can't eat storage. Every app process (the UI and the VPN engines)
 * appends to the same files.
 */
object AppLog {

    const val SUBSCRIPTION = "subscription.log"
    const val CONNECTION = "connection.log"
    const val XRAY_DEBUG = "xray-debug.log"

    private const val MAX_BYTES = 200_000L
    private const val KEEP_BYTES = 100_000

    private val urlPattern = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    private val tokenPattern = Regex("[A-Za-z0-9_\\-]{24,}")

    /** Installed once per process by the platform; messages logged before that are dropped. */
    var store: LogStore? = null

    fun log(file: String, message: String) {
        val target = store ?: return
        runCatching { target.append(file, "${localTimestamp()}  ${scrub(message)}\n", MAX_BYTES, KEEP_BYTES) }
    }

    /** Known journals first, then Xray's own debug output when a debug build produced it. */
    fun files(): List<LogFileInfo> {
        val target = store ?: return emptyList()
        val known = listOf(SUBSCRIPTION, CONNECTION).map { target.info(it) ?: LogFileInfo(it, 0L, 0L) }
        val debug = target.info(XRAY_DEBUG)?.takeIf { it.modifiedMillis > 0L || it.bytes > 0L }
        return known + listOfNotNull(debug)
    }

    /** The newest part of a journal (it is shown whole only when small). */
    fun read(name: String, maxBytes: Int = 120_000): String = runCatching { store?.read(name, maxBytes) }.getOrNull().orEmpty()

    fun clear(name: String) {
        runCatching { store?.clear(name) }
    }

    fun scrub(message: String): String =
        message.replace(urlPattern, "<url>").replace(tokenPattern, "…").replace('\n', ' ').take(500)
}

/** "yyyy-MM-dd HH:mm:ss" in the device's time zone. */
internal expect fun localTimestamp(): String
