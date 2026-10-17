package com.waytun.app.core.log

/**
 * Developer diagnostics (logcat on Android, stderr on desktop). Each platform installs its [sink] at startup; until
 * then messages are dropped. Unlike [AppLog] this is not shown to the user and is not scrubbed, so never pass
 * addresses, keys or tokens to it.
 */
object PlatformLog {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    var sink: (level: Level, tag: String, message: String, error: Throwable?) -> Unit = { _, _, _, _ -> }

    fun d(tag: String, message: String) = sink(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = sink(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = sink(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = sink(Level.ERROR, tag, message, error)
}
