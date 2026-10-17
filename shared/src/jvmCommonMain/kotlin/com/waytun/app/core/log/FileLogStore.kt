package com.waytun.app.core.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journals as files in [directory]; [elsewhere] maps names that live outside it (Xray's debug log is written by the
 * core itself next to the app files). Appends are serialized within a process; the files are shared by processes.
 */
class FileLogStore(private val directory: File, private val elsewhere: Map<String, File> = emptyMap()) : LogStore {

    private val lock = Any()

    init {
        directory.mkdirs()
    }

    private fun fileFor(name: String): File = elsewhere[name] ?: File(directory, name)

    override fun append(name: String, line: String, maxBytes: Long, keepBytes: Int) {
        synchronized(lock) {
            val target = fileFor(name)
            if (target.length() > maxBytes) trim(target, keepBytes)
            target.appendText(line)
        }
    }

    override fun read(name: String, maxBytes: Int): String {
        val file = fileFor(name)
        if (!file.exists()) return ""
        return file.inputStream().use { input ->
            val size = file.length()
            if (size > maxBytes) input.skip(size - maxBytes)
            String(input.readBytes(), Charsets.UTF_8).let { text ->
                if (size > maxBytes) text.substringAfter('\n', text) else text
            }
        }
    }

    override fun clear(name: String) {
        synchronized(lock) { fileFor(name).writeText("") }
    }

    override fun info(name: String): LogFileInfo? {
        val file = fileFor(name)
        if (name in elsewhere && !file.exists()) return null
        return LogFileInfo(file.name, if (file.exists()) file.length() else 0L, if (file.exists()) file.lastModified() else 0L)
    }

    private fun trim(file: File, keepBytes: Int) {
        val tail = file.inputStream().use { input ->
            input.skip(file.length() - keepBytes)
            String(input.readBytes(), Charsets.UTF_8)
        }
        file.writeText(tail.substringAfter('\n', tail))
    }
}

internal actual fun localTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
