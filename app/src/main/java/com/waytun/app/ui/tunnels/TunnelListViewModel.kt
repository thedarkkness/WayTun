package com.waytun.app.ui.tunnels

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.R
import com.waytun.app.core.pairing.PairingCrypto
import com.waytun.app.core.pairing.PairingTarget
import com.waytun.app.core.parser.ConfigErrorKind
import com.waytun.app.core.parser.ConfigFieldError
import com.waytun.app.core.parser.TunnelConfigParser
import com.waytun.app.core.parser.TunnelParseResult
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.vpn.AmneziaBackend
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackend
import com.waytun.app.vpn.WireGuardBackend
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONNECT_TIMEOUT_MS = 8_000

data class TunnelListItem(
    val id: String,
    val name: String,
    val protocol: TunnelProtocol,
    val isActive: Boolean,
    val isBusy: Boolean
)

data class TunnelListUiState(
    val tunnels: List<TunnelListItem> = emptyList()
)

/** Parsed-but-not-yet-saved import, shown to the user for confirmation before it touches disk. */
data class PendingImport(
    val suggestedName: String,
    val protocol: TunnelProtocol,
    val addresses: String,
    val dns: String,
    val endpoint: String,
    val allowedIps: String,
    val mtu: String?,
    val obfuscation: String?,
    val rawConfigText: String
)

sealed interface TunnelListEvent {
    data class Message(val text: String) : TunnelListEvent
}

@HiltViewModel
class TunnelListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tunnelRepository: TunnelRepository,
    @param:WireGuardBackend private val wireGuardBackend: VpnBackend,
    @param:AmneziaBackend private val amneziaBackend: VpnBackend
) : ViewModel() {

    private val events = Channel<TunnelListEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TunnelListEvent> = events.receiveAsFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _isSendingTunnel = MutableStateFlow(false)
    val isSendingTunnel: StateFlow<Boolean> = _isSendingTunnel.asStateFlow()

    val uiState: StateFlow<TunnelListUiState> = combine(
        tunnelRepository.observeTunnels(),
        wireGuardBackend.activeTunnelId,
        wireGuardBackend.state,
        amneziaBackend.activeTunnelId,
        amneziaBackend.state
    ) { tunnels, wgActiveId, wgState, awgActiveId, awgState ->
        TunnelListUiState(
            tunnels = tunnels.map { summary ->
                val activeId = if (summary.protocol == TunnelProtocol.WIREGUARD) wgActiveId else awgActiveId
                val state = if (summary.protocol == TunnelProtocol.WIREGUARD) wgState else awgState
                val isThisOne = summary.id == activeId
                TunnelListItem(
                    id = summary.id,
                    name = summary.name,
                    protocol = summary.protocol,
                    isActive = isThisOne && state !is TunnelState.Disconnected,
                    isBusy = isThisOne &&
                        (state is TunnelState.Connecting || state is TunnelState.Disconnecting)
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunnelListUiState())

    /** Parses the picked/pasted config and, if valid, stages it in [pendingImport] for review. */
    fun onConfigPicked(suggestedName: String, rawText: String) {
        when (val result = TunnelConfigParser.parse(rawText)) {
            is TunnelParseResult.WireGuardSuccess -> _pendingImport.value = buildWireGuardPreview(suggestedName, result, rawText)
            is TunnelParseResult.AmneziaSuccess -> _pendingImport.value = buildAmneziaPreview(suggestedName, result, rawText)
            is TunnelParseResult.Invalid -> sendMessage(describeInvalid(result.error))
            is TunnelParseResult.SyntaxError ->
                sendMessage(context.getString(R.string.import_error_structure, result.detail))
        }
    }

    fun onConfirmImport(name: String) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            val finalName = name.ifBlank { context.getString(R.string.import_default_name) }
            tunnelRepository.importTunnel(finalName, pending.protocol, pending.rawConfigText)
                .onSuccess { sendMessage(context.getString(R.string.import_success, finalName)) }
                .onFailure { sendMessage(it.message ?: context.getString(R.string.vpn_error_unknown)) }
        }
    }

    fun onCancelImport() {
        _pendingImport.value = null
    }

    fun onToggleTunnel(id: String) {
        val tunnel = uiState.value.tunnels.firstOrNull { it.id == id } ?: return
        val backend = backendFor(tunnel.protocol)
        if (backend.activeTunnelId.value == id && backend.state.value !is TunnelState.Disconnected) {
            sendDisconnect(context, tunnel.protocol)
        } else {
            startConnect(context, tunnel.protocol, id, tunnel.name)
        }
    }

    fun onDeleteTunnel(id: String) {
        val tunnel = uiState.value.tunnels.firstOrNull { it.id == id }
        if (tunnel != null) {
            sendDisconnectIfActive(context, backendFor(tunnel.protocol), tunnel.protocol, id)
        }
        viewModelScope.launch {
            tunnelRepository.deleteTunnel(id)
                .onFailure { sendMessage(it.message ?: context.getString(R.string.vpn_error_unknown)) }
        }
    }

    fun onVpnPermissionDenied() {
        sendMessage(context.getString(R.string.vpn_permission_denied))
    }

    fun onImportFileReadFailed() {
        sendMessage(context.getString(R.string.import_error_file_read))
    }

    /** Sends [tunnelId]'s decrypted config to a device that displayed a "receive via QR" code. */
    fun sendTunnelOverNetwork(tunnelId: String, target: PairingTarget) {
        viewModelScope.launch {
            _isSendingTunnel.value = true
            val rawConfig = tunnelRepository.getDecryptedConfigText(tunnelId).getOrNull()
            if (rawConfig == null) {
                _isSendingTunnel.value = false
                sendMessage(context.getString(R.string.vpn_error_unknown))
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // If one of our own VPN tunnels is active, the default route goes through
                    // the tun interface, which has no path back to a peer on the local LAN.
                    // Bind this socket to the underlying Wi-Fi/Ethernet network explicitly so
                    // pairing still works while connected.
                    val lanNetwork = findLanNetwork()
                    Socket().use { socket ->
                        lanNetwork?.let { it.bindSocket(socket) }
                        socket.connect(InetSocketAddress(target.ip, target.port), CONNECT_TIMEOUT_MS)
                        socket.soTimeout = 15_000
                        val (iv, ciphertext) = PairingCrypto.encrypt(
                            target.keyBytes,
                            rawConfig.toByteArray(Charsets.UTF_8)
                        )
                        val output = DataOutputStream(socket.getOutputStream())
                        output.writeInt(iv.size + ciphertext.size)
                        output.write(iv)
                        output.write(ciphertext)
                        output.flush()
                    }
                }
            }
            _isSendingTunnel.value = false
            result
                .onSuccess { sendMessage(context.getString(R.string.pairing_send_success)) }
                .onFailure { e ->
                    sendMessage(
                        context.getString(
                            R.string.pairing_send_failed,
                            (e as? IOException)?.message ?: e.message ?: context.getString(R.string.pairing_error_generic)
                        )
                    )
                }
        }
    }

    /** The first non-VPN Wi-Fi or Ethernet network, used to route around an active VPN tunnel. */
    private fun findLanNetwork(): android.net.Network? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
    }

    private fun backendFor(protocol: TunnelProtocol): VpnBackend =
        if (protocol == TunnelProtocol.WIREGUARD) wireGuardBackend else amneziaBackend

    private fun sendMessage(text: String) {
        viewModelScope.launch { events.send(TunnelListEvent.Message(text)) }
    }

    private fun buildWireGuardPreview(
        suggestedName: String,
        success: TunnelParseResult.WireGuardSuccess,
        rawText: String
    ): PendingImport {
        val iface = success.config.getInterface()
        val notSet = context.getString(R.string.value_not_set)
        val addresses = iface.getAddresses().joinToString(", ") { it.toString() }.ifBlank { notSet }
        val dnsEntries = iface.getDnsServers().map { it.hostAddress } + iface.getDnsSearchDomains()
        val dns = dnsEntries.joinToString(", ").ifBlank { notSet }
        val mtu = if (iface.getMtu().isPresent) iface.getMtu().get().toString() else null
        val firstPeer = success.config.getPeers().firstOrNull()
        val endpoint = firstPeer?.getEndpoint()?.let { if (it.isPresent) it.get().toString() else null } ?: notSet
        val allowedIps = firstPeer?.getAllowedIps()?.joinToString(", ") { it.toString() }?.ifBlank { notSet } ?: notSet

        return PendingImport(
            suggestedName = suggestedName.ifBlank { context.getString(R.string.import_default_name) },
            protocol = TunnelProtocol.WIREGUARD,
            addresses = addresses,
            dns = dns,
            endpoint = endpoint,
            allowedIps = allowedIps,
            mtu = mtu,
            obfuscation = null,
            rawConfigText = rawText
        )
    }

    private fun buildAmneziaPreview(
        suggestedName: String,
        success: TunnelParseResult.AmneziaSuccess,
        rawText: String
    ): PendingImport {
        val iface = success.config.getInterface()
        val notSet = context.getString(R.string.value_not_set)
        val addresses = iface.getAddresses().joinToString(", ") { it.toString() }.ifBlank { notSet }
        val dnsEntries = iface.getDnsServers().map { it.hostAddress } + iface.getDnsSearchDomains()
        val dns = dnsEntries.joinToString(", ").ifBlank { notSet }
        val mtu = if (iface.getMtu().isPresent) iface.getMtu().get().toString() else null
        val firstPeer = success.config.getPeers().firstOrNull()
        val endpoint = firstPeer?.getEndpoint()?.let { if (it.isPresent) it.get().toString() else null } ?: notSet
        val allowedIps = firstPeer?.getAllowedIps()?.joinToString(", ") { it.toString() }?.ifBlank { notSet } ?: notSet

        val obfuscationParts = buildList {
            if (iface.getJunkPacketCount().isPresent) add("Jc=${iface.getJunkPacketCount().get()}")
            if (iface.getJunkPacketMinSize().isPresent) add("Jmin=${iface.getJunkPacketMinSize().get()}")
            if (iface.getJunkPacketMaxSize().isPresent) add("Jmax=${iface.getJunkPacketMaxSize().get()}")
            if (iface.getInitPacketJunkSize().isPresent) add("S1=${iface.getInitPacketJunkSize().get()}")
            if (iface.getResponsePacketJunkSize().isPresent) add("S2=${iface.getResponsePacketJunkSize().get()}")
            if (iface.getInitPacketMagicHeader().isPresent) add("H1")
            if (iface.getResponsePacketMagicHeader().isPresent) add("H2")
            if (iface.getUnderloadPacketMagicHeader().isPresent) add("H3")
            if (iface.getTransportPacketMagicHeader().isPresent) add("H4")
        }
        val obfuscation = obfuscationParts.joinToString(", ").ifBlank { null }

        return PendingImport(
            suggestedName = suggestedName.ifBlank { context.getString(R.string.import_default_name) },
            protocol = TunnelProtocol.AMNEZIA_WG,
            addresses = addresses,
            dns = dns,
            endpoint = endpoint,
            allowedIps = allowedIps,
            mtu = mtu,
            obfuscation = obfuscation,
            rawConfigText = rawText
        )
    }

    private fun describeInvalid(error: ConfigFieldError): String {
        val field = error.fieldName.ifBlank { context.getString(R.string.import_error_generic_field) }
        return when (error.kind) {
            ConfigErrorKind.MISSING -> context.getString(R.string.import_error_field_missing, field)
            ConfigErrorKind.INVALID -> context.getString(R.string.import_error_field_invalid, field)
            ConfigErrorKind.STRUCTURE -> context.getString(R.string.import_error_structure, field)
        }
    }
}
