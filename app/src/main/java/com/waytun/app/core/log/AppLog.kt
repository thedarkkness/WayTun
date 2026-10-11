package com.waytun.app.core.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogFileInfo(val name: String, val bytes: Long, val modifiedMillis: Long)

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

    private var directory: File? = null
    private val lock = Any()
    private val urlPattern = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    private val tokenPattern = Regex("[A-Za-z0-9_\\-]{24,}")

    fun init(context: Context) {
        if (directory == null) directory = File(context.filesDir, "logs").also { it.mkdirs() }
    }

    fun log(file: String, message: String) {
        val dir = directory ?: return
        val line = "${stamp()}  ${scrub(message)}\n"
        synchronized(lock) {
            runCatching {
                val target = File(dir, file)
                if (target.length() > MAX_BYTES) trim(target)
                target.appendText(line)
            }
        }
    }

    /** Known journals first, then Xray's own debug output when a debug build produced it. */
    fun files(context: Context): List<LogFileInfo> {
        init(context)
        val dir = directory ?: return emptyList()
        val known = listOf(SUBSCRIPTION, CONNECTION).map { info(File(dir, it)) }
        val debug = File(context.filesDir, XRAY_DEBUG).takeIf { it.exists() }?.let { info(it) }
        return known + listOfNotNull(debug)
    }

    /** The newest part of a journal (it is shown whole only when small). */
    fun read(context: Context, name: String, maxBytes: Int = 120_000): String {
        val file = fileFor(context, name)
        if (!file.exists()) return ""
        return runCatching {
            file.inputStream().use { input ->
                val size = file.length()
                if (size > maxBytes) input.skip(size - maxBytes)
                String(input.readBytes(), Charsets.UTF_8).let { text ->
                    if (size > maxBytes) text.substringAfter('\n', text) else text
                }
            }
        }.getOrDefault("")
    }

    fun clear(context: Context, name: String) {
        synchronized(lock) { runCatching { fileFor(context, name).writeText("") } }
    }

    private fun fileFor(context: Context, name: String): File {
        init(context)
        return if (name == XRAY_DEBUG) File(context.filesDir, name) else File(directory!!, name)
    }

    private fun info(file: File) = LogFileInfo(file.name, if (file.exists()) file.length() else 0L, if (file.exists()) file.lastModified() else 0L)

    private fun trim(file: File) {
        val tail = file.inputStream().use { input ->
            input.skip(file.length() - KEEP_BYTES)
            String(input.readBytes(), Charsets.UTF_8)
        }
        file.writeText(tail.substringAfter('\n', tail))
    }

    private fun scrub(message: String): String =
        message.replace(urlPattern, "<url>").replace(tokenPattern, "…").replace('\n', ' ').take(500)

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
