package com.waytun.app.core.net

actual suspend fun runSpeedTest(): SpeedTestResult? =
    SpeedTest.run()?.let { SpeedTestResult(it.megabitsPerSecond, it.firstByteMillis, it.bytes) }
