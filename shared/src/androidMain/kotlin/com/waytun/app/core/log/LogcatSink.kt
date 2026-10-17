package com.waytun.app.core.log

import android.util.Log

/** Routes [PlatformLog] to logcat; installed by the Application in every process. */
object LogcatSink {
    fun install() {
        PlatformLog.sink = { level, tag, message, error ->
            when (level) {
                PlatformLog.Level.DEBUG -> Log.d(tag, message, error)
                PlatformLog.Level.INFO -> Log.i(tag, message, error)
                PlatformLog.Level.WARN -> Log.w(tag, message, error)
                PlatformLog.Level.ERROR -> Log.e(tag, message, error)
            }
        }
    }
}
