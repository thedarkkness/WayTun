package com.waytun.app.core.time

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Wall-clock time in epoch milliseconds (System.currentTimeMillis() on the JVM). */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
