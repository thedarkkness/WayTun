package com.waytun.app.data.subscription

import com.waytun.app.core.text.Base64
import com.waytun.app.golden.GoldenData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SubscriptionClientTest {

    private val device = mapOf(
        "User-Agent" to "WayTun/1.3.2 (Android 16; Test)",
        "x-hwid" to "61712ced10c00f96",
        "x-device-os" to "Android",
        "x-ver-os" to "16",
        "x-device-model" to "Test"
    )
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): SubscriptionClient {
        val http = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            install(HttpTimeout)
            followRedirects = true
            expectSuccess = false
        }
        return SubscriptionClient(http) { device }
    }

    private val links = "vless://0b6f2c4e-8d1a-4c3e-9f2b-7a5d6e8c1b20@a.example.com:443?security=none#A\n"

    @Test
    fun sendsTheDeviceHeadersAndReadsPanelMetadata() = runTest {
        val subject = client {
            respond(
                links,
                HttpStatusCode.OK,
                headersOf(
                    "profile-title" to listOf("base64:" + Base64.encodeToString("☁️ DarkVPN\nВторая строка".encodeToByteArray(), Base64.NO_WRAP)),
                    "subscription-userinfo" to listOf("upload=0; download=2040109465; total=37580963840; expire=1799000000"),
                    "profile-update-interval" to listOf("1"),
                    "support-url" to listOf("https://t.me/darkvpn_official_support"),
                    "bot-link" to listOf("https://t.me/first", "https://t.me/darkvpn_official_bot"),
                    "announce" to listOf("base64:" + Base64.encodeToString("ℹ️ Не забывайте".encodeToByteArray(), Base64.NO_WRAP)),
                )
            )
        }
        val response = subject.fetch("https://sub.example.com/s/feed/abc123")
        assertEquals(1, requests.size)
        val sent = requests.single().headers
        for ((name, value) in device) assertEquals(value, sent[name], name)
        assertEquals("*/*", sent["Accept"])

        assertEquals(links, response.body)
        assertEquals("☁️ DarkVPN", response.title)
        assertEquals("Вторая строка", response.description)
        assertEquals(SubscriptionUsage(0, 2040109465, 37580963840, 1799000000), response.usage)
        assertEquals(1, response.updateIntervalHours)
        assertEquals("https://t.me/darkvpn_official_support", response.supportUrl)
        // HttpURLConnection.getHeaderField returned the last value of a repeated header.
        assertEquals("https://t.me/darkvpn_official_bot", response.botLink)
        assertEquals("ℹ️ Не забывайте", response.announce)
    }

    @Test
    fun hwidCanBeWithheldAndUserAgentOverridden() = runTest {
        val subject = client { respond(links, HttpStatusCode.OK) }
        subject.fetch("https://sub.example.com/s/feed/x", FetchOptions(userAgent = "Custom/1", sendHwid = false))
        val sent = requests.single().headers
        assertEquals("Custom/1", sent["User-Agent"])
        for (name in listOf("x-hwid", "x-device-os", "x-ver-os", "x-device-model")) assertNull(sent[name], name)
    }

    @Test
    fun htmlAnswerIsRetriedAsAKnownClient() = runTest {
        val subject = client { request ->
            if (request.headers["User-Agent"]!!.startsWith("v2rayNG")) respond(links, HttpStatusCode.OK)
            else respond("<!DOCTYPE html><html>panel</html>", HttpStatusCode.OK)
        }
        val response = subject.fetch("https://sub.example.com/s/feed/x")
        assertEquals(links, response.body)
        assertEquals(2, requests.size)
        assertEquals("v2rayNG/1.10.5", requests[1].headers["User-Agent"])
    }

    @Test
    fun remnawaveJsonIsRequestedOnTheExplicitPath() = runTest {
        val json = """[{"remarks":"A","outbounds":[{"tag":"proxy","protocol":"trojan","settings":{"servers":[{"address":"a.example.com","port":443,"password":"pw"}]}}]}]"""
        val subject = client { request ->
            when (request.url.encodedPath) {
                "/api/sub/tok" -> respond(links, HttpStatusCode.OK, headersOf("profile-title", "Panel"))
                "/api/sub/tok/json" -> respond(json, HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val response = subject.fetch("https://sub.example.com/api/sub/tok?x=1")
        assertEquals(json, response.body)
        assertEquals("Panel", response.title)
        assertEquals("x=1", requests.last().url.encodedQuery)
    }

    @Test
    fun httpErrorsKeepTheirShortMessage() = runTest {
        val subject = client { respond("nope", HttpStatusCode.Forbidden) }
        val error = assertFailsWith<SubscriptionHttpException> { subject.fetch("https://sub.example.com/s/feed/x") }
        assertEquals("HTTP 403", error.message)
    }

    @Test
    fun networkErrorsNeverQuoteTheTokenizedUrl() = runTest {
        val url = "https://sub.example.com/api/sub/SecretToken123?key=abc"
        val subject = client { throw IllegalStateException("Connect timeout has expired [url=$url, connect_timeout=15000 ms]") }
        val error = assertFailsWith<SubscriptionNetworkException> { subject.fetch(url) }
        assertFalse(error.message!!.contains("SecretToken123"))
        assertTrue(error.message!!.contains("sub.example.com"))
    }

    @Test
    fun panelHeaderDecodingMatches132() {
        for ((raw, expected) in GoldenData.titles) assertEquals(expected, SubscriptionClient.decodeTitle(raw), raw)
        for ((raw, expected) in GoldenData.usages) assertEquals(expected, SubscriptionClient.parseUsage(raw)?.toString(), raw)
    }
}
