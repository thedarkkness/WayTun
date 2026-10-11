package com.waytun.app.vpn.xray

import android.content.Context
import android.net.TrafficStats
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.waytun.app.core.log.AppLog
import com.waytun.app.BuildConfig
import com.waytun.app.R
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.vpn.ActiveTunnel
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.TunnelStats
import com.waytun.app.vpn.VpnBackend
import com.waytun.app.xray.xraycore.Protector
import com.waytun.app.xray.xraycore.Xraycore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "XrayVpnBackend"
private const val CONNECT_TIMEOUT_MS = 20_000L

private class NotAuthorizedException : Exception("VPN permission not granted")

/**
 * The Xray-core engine (VLESS/VMess/Trojan/Shadowsocks/Hysteria2, incl. XHTTP). Same shape as the other
 * backends: it lives in its own `:xray` process, and the UI process only mirrors its state.
 *
 * The TUN is opened here with [VpnService.Builder] and the descriptor is handed to Xray's own `tun`
 * inbound; Xray's sockets are excluded from the VPN through [Protector] -> [VpnService.protect].
 */
@Singleton
class XrayVpnBackend @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VpnBackend {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _activeTunnelId = MutableStateFlow<String?>(null)
    override val activeTunnelId: StateFlow<String?> = _activeTunnelId.asStateFlow()

    @Volatile private var activeVpnService: VpnService? = null
    @Volatile private var tun: ParcelFileDescriptor? = null

    fun attachService(service: VpnService) {
        activeVpnService = service
    }

    fun detachService(service: VpnService) {
        if (activeVpnService === service) activeVpnService = null
    }

    override suspend fun start(tunnel: ActiveTunnel): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = TunnelState.Connecting
        val link = SingBoxLinkParser.parse(tunnel.rawConfigText)
        if (link == null) {
            failWith(context.getString(R.string.vpn_error_invalid_config))
            return@withContext Result.failure(IllegalStateException("not a valid share link"))
        }
        val service = activeVpnService
        if (service == null) {
            failWith(context.getString(R.string.vpn_error_unable_to_start))
            return@withContext Result.failure(IllegalStateException("VpnService not running"))
        }
        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                Log.d(TAG, "start: connecting '${tunnel.displayName}' (${link.protocol}) with Xray ${Xraycore.version()}")
                // Resolve the server's hostname once, up front, with the system resolver (see ServerAddressResolver).
                val resolved = ServerAddressResolver.resolve(link)
                val logFile = if (BuildConfig.DEBUG) java.io.File(context.filesDir, "xray-debug.log").also { it.delete() }.absolutePath else null
                val config = if (tunnel.rawConfigText.trimStart().startsWith("{")) {
                    // The panel's own full config: honour it.
                    XrayConfigBuilder.fromJson(tunnel.rawConfigText, resolved.server.takeIf { it != link.server }, link.server, tunnel.blockQuic, logFile)
                } else {
                    XrayConfigBuilder.build(resolved, tunnel.obfuscation, tunnel.blockQuic, logFile)
                }
                Xraycore.setProtector(object : Protector {
                    override fun protect(fd: Long): Boolean = service.protect(fd.toInt())
                })
                val fd = openTun(service)
                try {
                    Xraycore.start(config, fd.fd.toLong(), context.filesDir.absolutePath)
                } catch (e: Exception) {
                    runCatching { fd.close() }
                    tun = null
                    throw e
                }
            }
            _activeTunnelId.value = tunnel.id
            _state.value = TunnelState.Connected(System.currentTimeMillis())
            Log.d(TAG, "start: connected '${tunnel.displayName}'")
            AppLog.log(AppLog.CONNECTION, "connected '${tunnel.displayName}' (Xray)")
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "start: timed out", e)
            AppLog.log(AppLog.CONNECTION, "connection timed out")
            teardown()
            failWith(context.getString(R.string.vpn_error_unknown))
            Result.failure(e)
        } catch (e: NotAuthorizedException) {
            Log.w(TAG, "start: VPN permission missing or another VPN is active")
            AppLog.log(AppLog.CONNECTION, "no VPN permission, or another VPN is active")
            teardown()
            failWith(context.getString(R.string.vpn_error_not_authorized))
            // Retrying can't help until the user grants permission again.
            Result.failure(IllegalStateException(e))
        } catch (e: XrayConfigBuilder.Unsupported) {
            Log.w(TAG, "start: ${e.message}")
            AppLog.log(AppLog.CONNECTION, "unsupported config: ${e.message}")
            teardown()
            failWith(e.message ?: context.getString(R.string.vpn_error_invalid_config))
            // An IllegalStateException tells the session supervisor that retrying can't help.
            Result.failure(IllegalStateException(e.message, e))
        } catch (e: Exception) {
            Log.e(TAG, "start: failed", e)
            AppLog.log(AppLog.CONNECTION, "connection failed: ${e.javaClass.simpleName}: ${e.message}")
            teardown()
            failWith(e.message ?: context.getString(R.string.vpn_error_unknown))
            Result.failure(e)
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        if (Xraycore.isRunning() || tun != null) {
            _state.value = TunnelState.Disconnecting
            teardown()
            Log.d(TAG, "stop: disconnected")
            AppLog.log(AppLog.CONNECTION, "disconnected")
        }
        _activeTunnelId.value = null
        _state.value = TunnelState.Disconnected
        Result.success(Unit)
    }

    @Synchronized
    private fun teardown() {
        runCatching { Xraycore.stop() }.onFailure { Log.w(TAG, "stop failed", it) }
        // Xray only uses the descriptor; closing it is ours to do (otherwise the VPN icon stays up).
        tun?.let { runCatching { it.close() } }
        tun = null
    }

    private fun openTun(service: VpnService): ParcelFileDescriptor {
        val builder = service.Builder()
            .setSession("WayTun")
            .setMtu(XrayConfigBuilder.tunMtu)
            .addAddress("172.19.0.1", 30)
            .addAddress("fd00:7a79:7461::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            // Public resolvers, reached through the tunnel like any other traffic.
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
        // Our own traffic (subscriptions, speed test) deliberately goes through the tunnel too; Xray's own
        // sockets are protected one by one.
        // null = the user hasn't granted VPN permission to us (or another VPN app took it over).
        val fd = builder.establish() ?: throw NotAuthorizedException()
        tun = fd
        return fd
    }

    override fun stats(): TunnelStats? {
        if (_state.value !is TunnelState.Connected) return null
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        return TunnelStats(rxBytes = rx, txBytes = tx, latestHandshakeEpochMillis = null)
    }

    private fun failWith(message: String) {
        _activeTunnelId.value = null
        _state.value = TunnelState.Error(message)
    }
}
