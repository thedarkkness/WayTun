package com.waytun.app.vpn

import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.waytun.app.core.parser.TunnelProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val STOP_TIMEOUT_MS = 5_000L

/** Brings engines up and down; on Android each engine is a VPN service in its own process. */
interface VpnLauncher {
    fun start(engine: Engine, tunnelId: String, tunnelName: String, settings: ObfuscationSettings, profile: ObfuscationProfile?)
    fun stop(engine: Engine)
}

/**
 * The UI's view of all engines (on Android read-only mirrors of the service processes, see vpn/remote). An engine a
 * platform doesn't have yet is reported as permanently disconnected and refuses to start.
 */
class VpnBackends(
    private val backends: Map<Engine, VpnBackend>,
    private val launcher: VpnLauncher
) {
    private val unavailable = UnavailableBackend()

    fun backend(engine: Engine): VpnBackend = backends[engine] ?: unavailable

    fun isAvailable(engine: Engine): Boolean = engine in backends

    fun start(engine: Engine, tunnelId: String, tunnelName: String, settings: ObfuscationSettings, profile: ObfuscationProfile?) =
        launcher.start(engine, tunnelId, tunnelName, settings, profile)

    fun stop(engine: Engine) = launcher.stop(engine)

    val all: List<Pair<Engine, VpnBackend>> get() = Engine.entries.map { it to backend(it) }

    /**
     * Plain WireGuard tunnels run on the AmneziaWG engine when junk-packet obfuscation is on: with
     * the server-matched parameters left at their defaults it speaks ordinary WireGuard, and the
     * extra junk before the handshake is just dropped by a vanilla server.
     */
    fun runtimeEngine(protocol: TunnelProtocol, obfuscation: ObfuscationSettings): Engine =
        if (protocol == TunnelProtocol.WIREGUARD && obfuscation.enabled && obfuscation.wireguardJunk) {
            Engine.AMNEZIA
        } else {
            protocol.engine()
        }

    /** The engine currently holding [tunnelId] (connecting or connected), if any. */
    fun engineHolding(tunnelId: String): Engine? = all.firstOrNull { (_, backend) ->
        backend.activeTunnelId.value == tunnelId && backend.state.value !is TunnelState.Disconnected
    }?.first

    fun activeTunnelIds(): Set<String> = all
        .filter { (_, backend) -> backend.state.value !is TunnelState.Disconnected }
        .mapNotNull { (_, backend) -> backend.activeTunnelId.value }
        .toSet()

    /** Engines other than [keep] that are doing anything; each Go runtime must be fully down first. */
    fun busyEnginesExcept(keep: Engine): List<Engine> =
        all.filter { (engine, backend) -> engine != keep && backend.state.value !is TunnelState.Disconnected }.map { it.first }

    suspend fun awaitStopped(engines: List<Engine>) {
        engines.forEach { engine ->
            withTimeoutOrNull(STOP_TIMEOUT_MS) {
                backend(engine).state.first { it is TunnelState.Disconnected || it is TunnelState.Error }
            }
        }
    }
}

private class UnavailableBackend : VpnBackend {
    override val state: StateFlow<TunnelState> = MutableStateFlow(TunnelState.Disconnected)
    override val activeTunnelId: StateFlow<String?> = MutableStateFlow(null)
    override suspend fun start(tunnel: ActiveTunnel): Result<Unit> = Result.failure(UnsupportedOperationException("Not available on this platform yet"))
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override fun stats(): TunnelStats? = null
}
