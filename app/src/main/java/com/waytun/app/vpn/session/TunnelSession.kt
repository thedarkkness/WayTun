package com.waytun.app.vpn.session

import android.content.Context
import android.util.Log
import com.waytun.app.core.log.AppLog
import com.waytun.app.core.obfuscation.ObfuscationGenerator
import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.vpn.ActiveTunnel
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackend
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "TunnelSession"
private const val FIRST_PROBE_DELAY_MS = 8_000L
private const val PROBE_INTERVAL_MS = 15_000L
private const val PROBE_RETRY_INTERVAL_MS = 4_000L
private const val PROBE_FAILURES_TO_RESTART = 4
/** Data received while a probe was pending that proves the tunnel is alive (keepalives are a few KB at most). */
private const val CARRYING_DATA_BYTES = 100_000L
private const val MAX_BACKOFF_SECONDS = 60
private const val MAX_ATTEMPTS_BEFORE_FIRST_CONNECT = 4
private const val PROBE_RESTART_WINDOW_MS = 10 * 60_000L
private const val PROBE_RESTARTS_BEFORE_GIVING_UP_PROBING = 3

/**
 * The life of one "connect" request inside a VPN service: starts the tunnel and then keeps it
 * alive. If it fails, drops, or silently stops passing traffic, it restarts it with a freshly
 * randomized obfuscation profile (and, every few attempts, once without obfuscation in case that's
 * what's being blocked or breaking things), backing off between attempts and waiting for a network
 * when there is none.
 *
 * A single linear loop rather than state callbacks, so there's no race between "the backend says
 * disconnected" and "we disconnected it ourselves to restart".
 */
class TunnelSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val backend: VpnBackend,
    private val loadConfig: suspend (tunnelId: String) -> String?,
    private val listener: Listener
) {
    interface Listener {
        /** [attempt] is the reconnect attempt number, or null once connected normally. */
        fun onReconnectStatus(attempt: Int?)

        /** The session is over for good (user stop, hopeless failure): the service can stop itself. */
        fun onFinished()
    }

    data class Request(
        val tunnelId: String,
        val tunnelName: String,
        val settings: ObfuscationSettings,
        val initialProfile: ObfuscationProfile?
    )

    private enum class Reason { BackendLost, ProbeFailed, RotationDue }

    private val _reconnectingTunnelId = MutableStateFlow<String?>(null)

    /** Set while the session is (re)connecting after a failure, so the UI can show "connecting" instead of "off". */
    val reconnectingTunnelId: StateFlow<String?> = _reconnectingTunnelId.asStateFlow()

    private var job: Job? = null

    /** Starts (or switches to) [request]; a running session is replaced without stopping the backend. */
    fun begin(request: Request) {
        job?.cancel()
        job = scope.launch { run(request) }
    }

    /** User asked to disconnect: no reconnecting, stop the tunnel, end the session. */
    fun userStop() {
        job?.cancel()
        job = null
        _reconnectingTunnelId.value = null
        scope.launch {
            backend.stop()
            listener.onFinished()
        }
    }

    fun release() {
        job?.cancel()
        job = null
    }

    private suspend fun run(request: Request) {
        val settings = request.settings
        var attempt = 0
        var everConnected = false
        val probeRestarts = ArrayDeque<Long>()
        var probingEnabled = true
        probeEverPassed = false

        while (currentCoroutineContext().isActive) {
            val profile = profileFor(attempt, request)
            val raw = loadConfig(request.tunnelId)
            if (raw == null) {
                backend.stop()
                finish()
                return
            }
            Log.i(TAG, "start attempt=$attempt obfuscation=${profile?.summary() ?: "off"}")
            AppLog.log(AppLog.CONNECTION, "connecting '${request.tunnelName}' (attempt ${attempt + 1}, obfuscation ${if (profile != null) "on" else "off"})")
            val result = backend.start(ActiveTunnel(request.tunnelId, request.tunnelName, raw, profile, settings.blockQuic))

            val reason: Reason? = if (result.isSuccess) {
                everConnected = true
                attempt = 0
                _reconnectingTunnelId.value = null
                listener.onReconnectStatus(null)
                monitor(settings, probingEnabled)
            } else {
                val error = result.exceptionOrNull()
                // A config that doesn't even parse can't be fixed by trying again.
                if (error is IllegalStateException) {
                    finish()
                    return
                }
                null
            }

            if (!settings.autoReconnect || (!everConnected && attempt + 1 >= MAX_ATTEMPTS_BEFORE_FIRST_CONNECT)) {
                // Leave the backend in its Error state so the user sees why; nothing more to do.
                _reconnectingTunnelId.value = null
                listener.onReconnectStatus(null)
                finish()
                return
            }

            if (reason == Reason.ProbeFailed) {
                val now = System.currentTimeMillis()
                probeRestarts.addLast(now)
                while (probeRestarts.isNotEmpty() && now - probeRestarts.first() > PROBE_RESTART_WINDOW_MS) probeRestarts.removeFirst()
                if (!probeEverPassed && probeRestarts.size >= PROBE_RESTARTS_BEFORE_GIVING_UP_PROBING) {
                    // The tunnel connects fine but our probe targets never answered through it, not even once
                    // (e.g. a server that blocks them): stop second-guessing it instead of restarting forever.
                    // Once a probe has passed, later failures are real outages and keep triggering reconnects.
                    Log.w(TAG, "health probe keeps failing on a tunnel that connects; disabling it for this session")
                    AppLog.log(AppLog.CONNECTION, "health check keeps failing while connected; it is switched off for this session")
                    probingEnabled = false
                }
            }

            attempt++
            _reconnectingTunnelId.value = request.tunnelId
            listener.onReconnectStatus(attempt)
            Log.w(TAG, "reconnecting (attempt $attempt, reason=${reason ?: "start failed"})")
            AppLog.log(AppLog.CONNECTION, "reconnecting, attempt $attempt (${reason ?: "start failed"})")
            backend.stop()
            delay(backoffMillis(attempt))
            while (currentCoroutineContext().isActive && !TunnelHealthProbe.hasUnderlyingNetwork(context)) delay(2_000)
        }
    }

    /** Whether any health probe has succeeded during the current [run]. */
    @Volatile private var probeEverPassed = false

    private fun finish() {
        _reconnectingTunnelId.value = null
        listener.onFinished()
    }

    /** Fresh values per attempt; every 4th attempt goes out unobfuscated as a last-resort variant. */
    private fun profileFor(attempt: Int, request: Request): ObfuscationProfile? {
        if (!request.settings.enabled) return null
        if (attempt == 0) return request.initialProfile ?: ObfuscationGenerator.generate(request.settings)
        if (attempt % 4 == 0) return null
        return ObfuscationGenerator.generate(request.settings)
    }

    private fun backoffMillis(attempt: Int): Long {
        val seconds = minOf(MAX_BACKOFF_SECONDS, 1 shl minOf(attempt, 6))
        return TimeUnit.SECONDS.toMillis(seconds.toLong()) + Random.nextLong(0, 1_000)
    }

    /** Suspends while the tunnel is healthy; returns why it stopped being so. */
    private suspend fun monitor(settings: ObfuscationSettings, probingEnabled: Boolean): Reason = coroutineScope {
        val events = Channel<Reason>(Channel.CONFLATED)
        launch {
            backend.state.first { it is TunnelState.Error || it is TunnelState.Disconnected }
            events.trySend(Reason.BackendLost)
        }
        if (probingEnabled) {
            launch {
                delay(FIRST_PROBE_DELAY_MS)
                var failures = 0
                while (true) {
                    val rxBefore = backend.stats()?.rxBytes
                    val answered = TunnelHealthProbe.isHealthy()
                    val rxAfter = backend.stats()?.rxBytes
                    // A probe that is merely slow because the tunnel is busy (a video, a big download saturating
                    // the link) must not count as a failure - restarting a working tunnel is what made videos stall.
                    val carryingData = rxBefore != null && rxAfter != null && rxAfter - rxBefore >= CARRYING_DATA_BYTES
                    val healthy = answered || carryingData
                    if (answered) probeEverPassed = true
                    // No network at all isn't the tunnel's fault; it'll be re-probed when one is back.
                    failures = if (healthy || !TunnelHealthProbe.hasUnderlyingNetwork(context)) 0 else failures + 1
                    if (failures >= PROBE_FAILURES_TO_RESTART) {
                        events.trySend(Reason.ProbeFailed)
                        return@launch
                    }
                    delay(if (failures > 0) PROBE_RETRY_INTERVAL_MS else PROBE_INTERVAL_MS)
                }
            }
        }
        if (settings.enabled && settings.autoRotate && settings.applyRotationLive) {
            launch {
                delay(TimeUnit.MINUTES.toMillis(ObfuscationSettings.ROTATE_INTERVAL_MINUTES))
                events.trySend(Reason.RotationDue)
            }
        }
        val reason = events.receive()
        coroutineContext.cancelChildren()
        reason
    }
}
