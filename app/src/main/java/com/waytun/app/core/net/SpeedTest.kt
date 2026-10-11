package com.waytun.app.core.net

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * A short download test through whatever the device is currently routed over (so, through the tunnel when
 * one is up): several parallel streams from Cloudflare's speed endpoint, because video CDNs also fetch in
 * parallel and a single TCP stream under-reports a good link. Not a precise benchmark, but enough to tell
 * a 40 Mbit/s server from a stalling one.
 */
object SpeedTest {

    data class Result(val megabitsPerSecond: Double, val firstByteMillis: Long, val bytes: Long)

    private const val STREAMS = 4
    private const val DURATION_MS = 7_000L
    // Several large static files on big CDNs: if one is blocked or slow from this network, the others still answer.
    private val URLS = listOf(
        "https://dl.google.com/android/repository/platform-tools-latest-linux.zip",
        "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip",
        "https://speed.cloudflare.com/__down?bytes=100000000",
        "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    )

    /** Null when no stream received anything (no connectivity, or the endpoint is blocked). */
    suspend fun run(): Result? = withContext(Dispatchers.IO) {
        val total = AtomicLong(0)
        val firstByte = AtomicLong(Long.MAX_VALUE)
        val start = System.nanoTime()
        val deadline = start + DURATION_MS * 1_000_000
        coroutineScope {
            List(STREAMS) { index ->
                async {
                    runCatching {
                        val connection = URL(URLS[index % URLS.size]).openConnection() as HttpURLConnection
                        connection.connectTimeout = 6_000
                        connection.readTimeout = 6_000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        try {
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (System.nanoTime() < deadline) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (total.getAndAdd(read.toLong()) == 0L) {
                                        firstByte.set((System.nanoTime() - start) / 1_000_000)
                                    }
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
            }.awaitAll()
        }
        val bytes = total.get()
        if (bytes <= 0) return@withContext null
        // Exclude the connection setup from the rate so a slow handshake doesn't hide a fast pipe.
        val seconds = ((System.nanoTime() - start) / 1e9 - firstByte.get() / 1000.0).coerceAtLeast(0.2)
        Result(bytes * 8 / seconds / 1e6, firstByte.get(), bytes)
    }
}
