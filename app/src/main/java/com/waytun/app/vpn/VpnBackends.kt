package com.waytun.app.vpn

import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.core.parser.TunnelProtocol
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val STOP_TIMEOUT_MS = 5_000L

/** UI-process view of all engines (read-only mirrors, see vpn/remote). */
@Singleton
class VpnBackends @Inject constructor(
    @WireGuardBackend private val wireGuard: VpnBackend,
    @AmneziaBackend private val amnezia: VpnBackend,
    @XrayBackend private val xray: VpnBackend
) {
    fun backend(engine: Engine): VpnBackend = when (engine) {
        Engine.WIREGUARD -> wireGuard
        Engine.AMNEZIA -> amnezia
        Engine.XRAY -> xray
    }

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
