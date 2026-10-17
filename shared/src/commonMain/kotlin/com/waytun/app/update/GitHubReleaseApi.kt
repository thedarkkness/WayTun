package com.waytun.app.update

import com.waytun.app.core.json.JSONObject
import com.waytun.app.core.net.SharedHttp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CancellationException

data class ReleaseInfo(
    val version: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?
)

sealed interface ReleaseCheckResult {
    data class Success(val release: ReleaseInfo) : ReleaseCheckResult
    data object NoReleasesYet : ReleaseCheckResult
    data class Failure(val message: String) : ReleaseCheckResult
}

/** Unauthenticated client for the public GitHub Releases API (60 req/hour/IP limit). */
object GitHubReleaseApi {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/thedarkkness/WayTun/releases/latest"

    suspend fun fetchLatest(http: HttpClient = SharedHttp.client, userAgent: String = "WayTun-Android"): ReleaseCheckResult = try {
        val response = http.get(LATEST_RELEASE_URL) {
            timeout {
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
            headers["Accept"] = "application/vnd.github+json"
            headers["User-Agent"] = userAgent
        }
        when (val code = response.status.value) {
            404 -> ReleaseCheckResult.NoReleasesYet
            in 200..299 -> ReleaseCheckResult.Success(parseRelease(response.bodyAsBytes().decodeToString()))
            else -> ReleaseCheckResult.Failure("HTTP $code")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReleaseCheckResult.Failure(e.message ?: "IO error")
    }

    private fun parseRelease(body: String): ReleaseInfo {
        val json = JSONObject(body)
        val tag = json.getString("tag_name").removePrefix("v").removePrefix("V")
        val notes = json.optString("body", "")
        var apkUrl: String? = null
        var apkName: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    apkName = name
                    break
                }
            }
        }
        return ReleaseInfo(tag, notes, apkUrl, apkName)
    }

    /**
     * Compares two "X.Y.Z"-style version strings numerically, segment by segment.
     * Non-numeric or missing segments are treated as 0. Returns true if [remote] > [local].
     */
    fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
