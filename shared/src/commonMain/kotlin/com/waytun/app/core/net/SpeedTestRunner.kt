package com.waytun.app.core.net

data class SpeedTestResult(val megabitsPerSecond: Double, val firstByteMillis: Long, val bytes: Long)

/** A short download through whatever route the system currently uses (the tunnel, when it is up); null on failure. */
expect suspend fun runSpeedTest(): SpeedTestResult?
