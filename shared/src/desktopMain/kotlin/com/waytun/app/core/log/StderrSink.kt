package com.waytun.app.core.log

/** Routes [PlatformLog] to stderr (warnings and errors only unless [verbose]). */
object StderrSink {
    fun install(verbose: Boolean = false) {
        PlatformLog.sink = { level, tag, message, error ->
            if (verbose || level >= PlatformLog.Level.WARN) {
                System.err.println("${level.name.first()}/$tag: $message")
                error?.printStackTrace()
            }
        }
    }
}
