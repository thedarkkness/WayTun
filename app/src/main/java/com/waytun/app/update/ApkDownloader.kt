package com.waytun.app.update

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadProgress {
    data class InProgress(val percent: Int?) : DownloadProgress
    data class Done(val file: File) : DownloadProgress
    data class Failed(val message: String) : DownloadProgress
}

private const val BUFFER_SIZE = 256 * 1024

object ApkDownloader {

    /** Downloads [url] into the app cache dir, emitting progress; the file is overwritten each run. */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (DownloadProgress) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "WayTun-Android")
                // The APK is already compressed; asking the server not to gzip it again avoids
                // an extra decode pass on top of the raw transfer.
                setRequestProperty("Accept-Encoding", "identity")
                useCaches = false
                connectTimeout = 15_000
                // Time allowed between two successive reads, not the whole transfer - generous so
                // a slow (e.g. TV Wi-Fi) link isn't mistaken for a stall.
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) {
                onProgress(DownloadProgress.Failed("HTTP ${connection.responseCode}"))
                return
            }
            val totalBytes = connection.contentLengthLong
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(updatesDir, fileName)

            connection.inputStream.use { input ->
                BufferedOutputStream(outFile.outputStream(), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var readTotal = 0L
                    var lastReportedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (totalBytes > 0) {
                            val percent = ((readTotal * 100) / totalBytes).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(DownloadProgress.InProgress(percent))
                            }
                        } else {
                            onProgress(DownloadProgress.InProgress(null))
                        }
                    }
                    output.flush()
                }
            }
            onProgress(DownloadProgress.Done(outFile))
        } catch (e: IOException) {
            onProgress(DownloadProgress.Failed(e.message ?: "IO error"))
        } finally {
            connection?.disconnect()
        }
    }
}
