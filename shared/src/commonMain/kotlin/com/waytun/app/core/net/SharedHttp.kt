package com.waytun.app.core.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

/** One HTTP client per process for subscriptions and update checks (OkHttp on Android, the JDK client on desktop). */
object SharedHttp {
    val client: HttpClient by lazy { platformHttpClient() }
}

internal expect fun platformHttpClient(): HttpClient

/** Behaviour every engine shares: per-request timeouts, redirects followed (never https -> http), no exceptions on 4xx/5xx. */
internal fun HttpClientConfig<*>.commonConfig() {
    install(HttpTimeout)
    followRedirects = true
    expectSuccess = false
}
