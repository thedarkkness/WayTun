package com.waytun.app.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java

internal actual fun platformHttpClient(): HttpClient = HttpClient(Java) { commonConfig() }
