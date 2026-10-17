package com.waytun.app.ui.tunnels

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.R
import com.waytun.app.core.obfuscation.ObfuscationManager
import com.waytun.app.core.pairing.PairingCrypto
import com.waytun.app.core.pairing.PairingTarget
import com.waytun.app.core.parser.ConfigErrorKind
import com.waytun.app.core.parser.ConfigFieldError
import com.waytun.app.core.parser.ShareLinkList
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.core.parser.TunnelConfigParser
import com.waytun.app.core.parser.TunnelDescriptor
import com.waytun.app.core.parser.TunnelParseResult
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.core.text.FlagName
import com.waytun.app.data.UiPrefs
import com.waytun.app.core.net.SpeedTest
import com.waytun.app.data.ping.PingManager
import com.waytun.app.data.ping.PingResult
import com.waytun.app.data.subscription.SubscriptionEmptyException
import com.waytun.app.data.subscription.SubscriptionRepository
import com.waytun.app.data.subscription.SubscriptionSyncResult
import com.waytun.app.data.subscription.SubscriptionUsage
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.vpn.Engine
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackends
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import com.waytun.app.ui.components.HudState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONNECT_TIMEOUT_MS = 8_000
private const val SAME_ENGINE_RESTART_GAP_MS = 400L
const val SORT_BY_PING = 1
const val SORT_ALPHABET = 2
private const val HUD_SUCCESS_MS = 1_100L
private const val HUD_FAILURE_MS = 2_200L
private const val PING_COOLDOWN_MS = 1_500L

data class TunnelListItem(
    val id: String,
    /** Full stored name, flag included. */
    val name: String,
    val title: String,
    val flag: String?,
    val protocol: TunnelProtocol,
    val details: String,
    val isActive: Boolean,
    val isBusy: Boolean,
    val isSelected: Boolean,
    val ping: PingResult?,
    val isPinging: Boolean = false
)

data class SubscriptionHeader(
    val id: String,
    val name: String,
    val usage: SubscriptionUsage?,
    val updatedAtEpochMillis: Long,
    val updateIntervalHours: Int?,
    val announce: String?,
    val supportUrl: String?,
    val webPageUrl: String?,
    val collapsed: Boolean,
    val isRefreshing: Boolean,
    val description: String? = null,
    val announceUrl: String? = null,
    val supportEmail: String? = null,
    val pinned: Boolean = false,
    val sortMode: Int = 0,
    val autoUpdate: Boolean = true,
    val botLink: String? = null
)

/** A run of tunnels in the list: standalone ones ([subscription] == null) or one subscription's. */
data class TunnelSection(
    val subscription: SubscriptionHeader?,
    val tunnels: List<TunnelListItem>
)

enum class HeroPhase { IDLE, CONNECTING, CONNECTED, ERROR }

/** State of the "check speed" button on the connected hero card. */
sealed interface SpeedUiState {
    data object Idle : SpeedUiState
    data object Running : SpeedUiState
    data class Done(val megabitsPerSecond: Double, val firstByteMillis: Long) : SpeedUiState
    data object Failed : SpeedUiState
}

/** The big status card at the top: what's selected, whether it's up, and how long for. */
data class HeroState(
    val phase: HeroPhase = HeroPhase.IDLE,
    val title: String? = null,
    val flag: String? = null,
    val details: String? = null,
    val protocol: TunnelProtocol? = null,
    val sinceEpochMillis: Long? = null,
    val errorMessage: String? = null,
    val obfuscationOn: Boolean = false
)

data class TunnelListUiState(
    val tunnels: List<TunnelListItem> = emptyList(),
    val sections: List<TunnelSection> = emptyList(),
    val hero: HeroState = HeroState(),
    val hasPingsRunning: Boolean = false
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
    data class Message(val text: String, val isError: Boolean = false) : TunnelListEvent
}

@HiltViewModel
class TunnelListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tunnelRepository: TunnelRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val backends: VpnBackends,
    private val obfuscation: ObfuscationManager,
    private val pingManager: PingManager,
    private val uiPrefs: UiPrefs
) : ViewModel() {

    private val events = Channel<TunnelListEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TunnelListEvent> = events.receiveAsFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _isSendingTunnel = MutableStateFlow(false)
    val isSendingTunnel: StateFlow<Boolean> = _isSendingTunnel.asStateFlow()

    private val _isAddingSubscription = MutableStateFlow(false)
    val isAddingSubscription: StateFlow<Boolean> = _isAddingSubscription.asStateFlow()

    private val _speed = MutableStateFlow<SpeedUiState>(SpeedUiState.Idle)
    val speed: StateFlow<SpeedUiState> = _speed.asStateFlow()

    private val refreshingSubscriptions = MutableStateFlow<Set<String>>(emptySet())

    /** The centered progress/result panel shown while a subscription is being updated by hand. */
    private val _hud = MutableStateFlow<HudState?>(null)
    val hud: StateFlow<HudState?> = _hud.asStateFlow()
    private var hudJob: Job? = null
    private val pingingIds = MutableStateFlow<Set<String>>(emptySet())

    /** A plain-http:// subscription URL waiting for the user to accept the insecure-connection warning. */
    private val _pendingInsecureSubscription = MutableStateFlow<String?>(null)
    val pendingInsecureSubscription: StateFlow<String?> = _pendingInsecureSubscription.asStateFlow()

    private data class EngineSnapshot(val engine: Engine, val activeId: String?, val state: TunnelState)

    private val engineSnapshots: Flow<List<EngineSnapshot>> = combine(
        Engine.entries.map { engine ->
            val backend = backends.backend(engine)
            combine(backend.activeTunnelId, backend.state) { id, state -> EngineSnapshot(engine, id, state) }
        }
    ) { it.toList() }

    private val catalog = combine(
        tunnelRepository.observeTunnels(),
        subscriptionRepository.observeSubscriptions(),
        refreshingSubscriptions
    ) { tunnels, subscriptions, refreshing -> Triple(tunnels, subscriptions, refreshing) }

    val uiState: StateFlow<TunnelListUiState> = combine(
        catalog,
        engineSnapshots,
        uiPrefs.selectedTunnelId,
        pingManager.results,
        combine(obfuscation.settings, pingingIds) { settings, pinging -> settings to pinging }
    ) { (tunnels, subscriptions, refreshing), snapshots, selectedPref, pings, (obfuscationSettings, pinging) ->
        val busySnapshot = snapshots.firstOrNull { it.state !is TunnelState.Disconnected }
        val selectedId = selectedPref?.takeIf { id -> tunnels.any { it.id == id } }
            ?: busySnapshot?.activeId?.takeIf { id -> tunnels.any { it.id == id } }
            ?: tunnels.firstOrNull()?.id

        val items = tunnels.map { summary ->
            val holder = snapshots.firstOrNull { it.activeId == summary.id && it.state !is TunnelState.Disconnected }
            val flagName = FlagName.parse(summary.name)
            TunnelListItem(
                id = summary.id,
                name = summary.name,
                title = flagName.title,
                flag = flagName.flag,
                protocol = summary.protocol,
                details = summary.details?.ifBlank { null } ?: summary.protocol.displayName(),
                isActive = holder != null,
                isBusy = holder != null && (holder.state is TunnelState.Connecting || holder.state is TunnelState.Disconnecting),
                isSelected = summary.id == selectedId,
                ping = pings[summary.id],
                isPinging = summary.id in pinging
            ) to summary
        }

        val standalone = items.filter { (_, s) -> s.subscriptionId == null }.map { it.first }
        val subscriptionSections = subscriptions.map { subscription ->
            val keys = items.filter { (_, s) -> s.subscriptionId == subscription.id }.map { it.first }
            TunnelSection(
                subscription = SubscriptionHeader(
                    id = subscription.id,
                    name = subscription.name,
                    usage = subscription.usage,
                    updatedAtEpochMillis = subscription.updatedAtEpochMillis,
                    updateIntervalHours = subscription.updateIntervalHours,
                    announce = subscription.announce,
                    supportUrl = subscription.supportUrl,
                    webPageUrl = subscription.webPageUrl,
                    collapsed = subscription.collapsed,
                    isRefreshing = subscription.id in refreshing,
                    description = subscription.description,
                    announceUrl = subscription.announceUrl,
                    supportEmail = subscription.supportEmail,
                    pinned = subscription.settings.pinned,
                    sortMode = subscription.settings.sortMode,
                    autoUpdate = subscription.settings.autoUpdate,
                    botLink = subscription.botLink
                ),
                tunnels = when (subscription.settings.sortMode) {
                    SORT_BY_PING -> keys.sortedBy { it.ping?.millis ?: Long.MAX_VALUE }
                    SORT_ALPHABET -> keys.sortedBy { it.title.lowercase() }
                    else -> keys
                }
            )
        }
        // Pinned subscriptions float above everything else, like Happ's "Закрепить".
        val sections = subscriptionSections.filter { it.subscription?.pinned == true } +
            listOfNotNull(TunnelSection(null, standalone).takeIf { standalone.isNotEmpty() }) +
            subscriptionSections.filter { it.subscription?.pinned != true }

        val heroItem = items.map { it.first }.firstOrNull { it.id == (busySnapshot?.activeId ?: selectedId) }
        val hero = when {
            busySnapshot != null -> HeroState(
                phase = when (busySnapshot.state) {
                    is TunnelState.Connected -> HeroPhase.CONNECTED
                    is TunnelState.Error -> HeroPhase.ERROR
                    else -> HeroPhase.CONNECTING
                },
                title = (heroItem ?: items.map { it.first }.firstOrNull { it.id == selectedId })?.title,
                flag = (heroItem ?: items.map { it.first }.firstOrNull { it.id == selectedId })?.flag,
                details = heroItem?.details,
                protocol = heroItem?.protocol,
                sinceEpochMillis = (busySnapshot.state as? TunnelState.Connected)?.sinceEpochMillis,
                errorMessage = (busySnapshot.state as? TunnelState.Error)?.message,
                obfuscationOn = obfuscationSettings.enabled
            )
            else -> HeroState(
                phase = HeroPhase.IDLE,
                title = heroItem?.title,
                flag = heroItem?.flag,
                details = heroItem?.details,
                protocol = heroItem?.protocol,
                obfuscationOn = obfuscationSettings.enabled
            )
        }
        TunnelListUiState(items.map { it.first }, sections, hero, hasPingsRunning = pinging.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunnelListUiState())

    init {
        // A result belongs to one connection: forget it as soon as that connection is gone.
        viewModelScope.launch {
            uiState.map { it.hero.phase }.distinctUntilChanged().collect { phase ->
                if (phase != HeroPhase.CONNECTED) _speed.value = SpeedUiState.Idle
            }
        }
        viewModelScope.launch { tunnelRepository.backfillDetails() }
        // Like Happ: quietly bring stale subscriptions up to date whenever the app is opened.
        viewModelScope.launch {
            subscriptionRepository.refreshStale(backends.activeTunnelIds(), onOpen = true)
            // While the app stays open, hourly auto-update keeps happening without a restart.
            while (true) {
                kotlinx.coroutines.delay(10 * 60_000L)
                subscriptionRepository.refreshStale(backends.activeTunnelIds())
            }
        }
        // Ping whatever appears in the list (new imports, refreshed subscriptions).
        // Subscriptions that turned "ping on open" off are skipped for the first batch only.
        viewModelScope.launch {
            val skipOnOpen = subscriptionRepository.idsWithoutPingOnOpen()
            var first = true
            tunnelRepository.observeTunnels().map { list -> list }.distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }.collect { list ->
                val ids = list.filter { !first || it.subscriptionId !in skipOnOpen }.map { it.id }.toSet()
                first = false
                pingManager.measure(ids)
            }
        }
    }

    // ---- connecting ---------------------------------------------------------------------------

    /** Power button on the hero card: disconnect if something is up, otherwise connect the selected tunnel. */
    fun onSpeedTest() {
        if (_speed.value == SpeedUiState.Running) return
        viewModelScope.launch {
            _speed.value = SpeedUiState.Running
            val result = SpeedTest.run()
            _speed.value = result?.let { SpeedUiState.Done(it.megabitsPerSecond, it.firstByteMillis) } ?: SpeedUiState.Failed
        }
    }

    fun onPowerClick() {
        val hero = uiState.value.hero
        val holder = backends.all.firstOrNull { (_, b) -> b.state.value !is TunnelState.Disconnected }
        if (holder != null) {
            sendDisconnect(context, holder.first)
            return
        }
        uiState.value.tunnels.firstOrNull { it.isSelected }?.let { connect(it.id) }
        if (hero.title == null) sendMessage(context.getString(R.string.tunnel_list_empty))
    }

    /** Row tap: select it; if something else is connected, switch over to it immediately (like Happ). */
    fun onSelectTunnel(id: String) {
        uiPrefs.select(id)
        val running = backends.activeTunnelIds()
        if (running.isNotEmpty() && id !in running) connect(id)
    }

    /** Toggle used after the system VPN-consent dialog returns. */
    fun onToggleTunnel(id: String) {
        val holder = backends.engineHolding(id)
        if (holder != null) sendDisconnect(context, holder) else connect(id)
    }

    fun isConnectedOrBusy(id: String): Boolean = backends.engineHolding(id) != null

    private fun connect(id: String) {
        val tunnel = uiState.value.tunnels.firstOrNull { it.id == id } ?: return
        uiPrefs.select(id)
        viewModelScope.launch {
            val settings = obfuscation.settings.value
            val engine = backends.runtimeEngine(tunnel.protocol, settings)
            // Each engine embeds its own Go runtime and its own VPN service; bring every running one
            // fully down first (this includes this engine if it holds another tunnel) so that two
            // runtimes never overlap and a stopping service can't swallow the new connect request.
            val running = backends.all.filter { (_, b) -> b.state.value !is TunnelState.Disconnected }.map { it.first }
            running.forEach { sendDisconnect(context, it) }
            if (running.isNotEmpty()) {
                backends.awaitStopped(running)
                delay(SAME_ENGINE_RESTART_GAP_MS)
            }
            uiPrefs.select(id)
            startConnect(context, engine, id, tunnel.name, settings, obfuscation.profileForConnect())
        }
    }

    // ---- selection / pings --------------------------------------------------------------------

    /** When each key was last asked to ping: repeated taps within [PING_COOLDOWN_MS] are ignored. */
    private val lastPingRequest = HashMap<String, Long>()

    fun pingAll(ids: Collection<String>? = null) {
        val now = System.currentTimeMillis()
        val requested = ids ?: uiState.value.tunnels.map { it.id }
        val targets = synchronized(lastPingRequest) {
            requested.filter { id ->
                val recent = now - (lastPingRequest[id] ?: 0L) < PING_COOLDOWN_MS
                val busy = id in pingingIds.value
                (!recent && !busy).also { allowed -> if (allowed) lastPingRequest[id] = now }
            }
        }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            pingingIds.value += targets
            try {
                pingManager.measure(targets, force = true)
            } finally {
                pingingIds.value -= targets.toSet()
            }
        }
    }

    // ---- subscriptions ------------------------------------------------------------------------

    fun onAddSubscription(url: String, insecureConfirmed: Boolean = false) {
        val trimmed = url.trim()
        if (!SubscriptionRepository.isSubscriptionUrl(trimmed)) {
            sendError(context.getString(R.string.subscription_url_invalid))
            return
        }
        // Unencrypted subscriptions expose every connection key in them to anyone on the path,
        // so they're only fetched after an explicit yes.
        if (!insecureConfirmed && trimmed.startsWith("http://", ignoreCase = true)) {
            _pendingInsecureSubscription.value = trimmed
            return
        }
        if (_isAddingSubscription.value) return
        _isAddingSubscription.value = true
        viewModelScope.launch {
            subscriptionRepository.add(trimmed)
                .onSuccess { sendMessage(syncMessage(it)) }
                .onFailure { sendError(subscriptionError(it)) }
            _isAddingSubscription.value = false
        }
    }

    fun onConfirmInsecureSubscription() {
        val url = _pendingInsecureSubscription.value ?: return
        _pendingInsecureSubscription.value = null
        onAddSubscription(url, insecureConfirmed = true)
    }

    fun onDismissInsecureSubscription() {
        _pendingInsecureSubscription.value = null
    }

    fun onRefreshSubscription(id: String) {
        if (id in refreshingSubscriptions.value) return
        refreshingSubscriptions.value += id
        hudJob?.cancel()
        _hud.value = HudState.Loading(context.getString(R.string.subscription_updating))
        viewModelScope.launch {
            val result = subscriptionRepository.refresh(id, backends.activeTunnelIds())
            refreshingSubscriptions.value -= id
            // An update of a subscription that's already here is not an import: don't report "imported N".
            _hud.value = result.fold(
                onSuccess = { HudState.Success(context.getString(R.string.subscription_updated_ok)) },
                onFailure = { HudState.Failure(subscriptionError(it)) }
            )
            hudJob = launch {
                delay(if (result.isSuccess) HUD_SUCCESS_MS else HUD_FAILURE_MS)
                _hud.value = null
            }
        }
    }

    fun onToggleSubscriptionCollapsed(id: String) {
        val current = uiState.value.sections.firstOrNull { it.subscription?.id == id }?.subscription?.collapsed ?: return
        viewModelScope.launch { subscriptionRepository.setCollapsed(id, !current) }
    }

    fun onTogglePinned(id: String) {
        val pinned = uiState.value.sections.firstOrNull { it.subscription?.id == id }?.subscription?.pinned ?: return
        viewModelScope.launch { subscriptionRepository.setPinned(id, !pinned) }
    }

    fun onRenameSubscription(id: String, name: String) {
        viewModelScope.launch { subscriptionRepository.rename(id, name) }
    }

    suspend fun subscriptionUrl(id: String): String? = subscriptionRepository.getUrl(id).getOrNull()

    fun onDeleteSubscription(id: String) {
        val section = uiState.value.sections.firstOrNull { it.subscription?.id == id } ?: return
        section.tunnels.forEach { tunnel -> backends.engineHolding(tunnel.id)?.let { sendDisconnect(context, it) } }
        viewModelScope.launch {
            subscriptionRepository.delete(id)
                .onFailure { sendError(it.message ?: context.getString(R.string.vpn_error_unknown)) }
            pingManager.forget(section.tunnels.map { it.id })
        }
    }

    private fun syncMessage(result: SubscriptionSyncResult): String =
        if (result.skipped > 0) {
            context.getString(R.string.import_bulk_partial, result.imported, result.skipped)
        } else {
            context.getString(R.string.subscription_import_success)
        }

    private fun subscriptionError(error: Throwable): String =
        if (error is SubscriptionEmptyException) {
            context.getString(R.string.subscription_empty)
        } else {
            context.getString(R.string.subscription_error, error.message ?: error.javaClass.simpleName)
        }

    // ---- importing ----------------------------------------------------------------------------

    /**
     * Entry point for free-form text (clipboard, QR): several share links (or a base64 block of
     * them) are imported straight away; a single config goes through the usual review dialog.
     */
    fun onTextImported(suggestedName: String, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) {
            sendError(context.getString(R.string.import_error_clipboard_empty))
            return
        }
        if (SubscriptionRepository.isSubscriptionUrl(text)) {
            onAddSubscription(text)
            return
        }
        val links = ShareLinkList.extract(text)
        if (links.size > 1) {
            importLinks(links, totalLinks = ShareLinkList.countAllLinks(text))
        } else {
            onConfigPicked(suggestedName, links.singleOrNull() ?: text)
        }
    }

    private fun importLinks(links: List<String>, totalLinks: Int) {
        viewModelScope.launch {
            var imported = 0
            for (link in links) {
                val parsed = SingBoxLinkParser.parse(link) ?: continue
                tunnelRepository.importTunnel(parsed.remark, parsed.protocol, link).onSuccess { imported++ }
            }
            val skipped = (totalLinks - imported).coerceAtLeast(0)
            sendMessage(
                when {
                    imported == 0 -> context.getString(R.string.import_error_nothing_recognized)
                    skipped > 0 -> context.getString(R.string.import_bulk_partial, imported, skipped)
                    else -> context.getString(R.string.config_import_success)
                }
            )
        }
    }

    /** Parses the picked/pasted config and, if valid, stages it in [pendingImport] for review. */
    fun onConfigPicked(suggestedName: String, rawText: String) {
        when (val result = TunnelConfigParser.parse(rawText)) {
            is TunnelParseResult.WireGuardSuccess -> _pendingImport.value = buildWireGuardPreview(suggestedName, result, rawText)
            is TunnelParseResult.AmneziaSuccess -> _pendingImport.value = buildAmneziaPreview(suggestedName, result, rawText)
            is TunnelParseResult.SingBoxSuccess -> _pendingImport.value = buildSingBoxPreview(suggestedName, result, rawText)
            is TunnelParseResult.Invalid -> sendError(describeInvalid(result.error))
            is TunnelParseResult.SyntaxError ->
                sendError(context.getString(R.string.import_error_structure, result.detail))
        }
    }

    fun onConfirmImport(name: String) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            val finalName = name.ifBlank { context.getString(R.string.import_default_name) }
            tunnelRepository.importTunnel(finalName, pending.protocol, pending.rawConfigText)
                .onSuccess { sendMessage(context.getString(R.string.config_import_success)) }
                .onFailure { sendError(it.message ?: context.getString(R.string.vpn_error_unknown)) }
        }
    }

    fun onCancelImport() {
        _pendingImport.value = null
    }

    fun onDeleteTunnel(id: String) {
        backends.engineHolding(id)?.let { sendDisconnect(context, it) }
        viewModelScope.launch {
            tunnelRepository.deleteTunnel(id)
                .onFailure { sendError(it.message ?: context.getString(R.string.vpn_error_unknown)) }
            pingManager.forget(listOf(id))
        }
    }

    fun onVpnPermissionDenied() {
        sendError(context.getString(R.string.vpn_permission_denied))
    }

    fun onImportFileReadFailed() {
        sendError(context.getString(R.string.import_error_file_read))
    }

    /** Sends [tunnelId]'s decrypted config to a device that displayed a "receive via QR" code. */
    fun sendTunnelOverNetwork(tunnelId: String, target: PairingTarget) {
        sendOverNetwork(target) { tunnelRepository.getDecryptedConfigText(tunnelId).getOrNull() }
    }

    /** Sends a subscription's URL (not its keys): the receiving device downloads it itself, like Happ's TV import. */
    fun sendSubscriptionOverNetwork(subscriptionId: String, target: PairingTarget) {
        sendOverNetwork(target) { subscriptionRepository.getUrl(subscriptionId).getOrNull() }
    }

    private fun sendOverNetwork(target: PairingTarget, payload: suspend () -> String?) {
        viewModelScope.launch {
            _isSendingTunnel.value = true
            val text = payload()
            if (text == null) {
                _isSendingTunnel.value = false
                sendError(context.getString(R.string.vpn_error_unknown))
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
                        val (iv, ciphertext) = PairingCrypto.encrypt(target.keyBytes, text.toByteArray(Charsets.UTF_8))
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
                    sendError(
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

    private fun sendMessage(text: String) {
        viewModelScope.launch { events.send(TunnelListEvent.Message(text)) }
    }

    private fun sendError(text: String) {
        viewModelScope.launch { events.send(TunnelListEvent.Message(text, isError = true)) }
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

        return PendingImport(
            suggestedName = suggestedName.ifBlank { context.getString(R.string.import_default_name) },
            protocol = TunnelProtocol.AMNEZIA_WG,
            addresses = addresses,
            dns = dns,
            endpoint = endpoint,
            allowedIps = allowedIps,
            mtu = mtu,
            obfuscation = obfuscationParts.joinToString(", ").ifBlank { null },
            rawConfigText = rawText
        )
    }

    private fun buildSingBoxPreview(
        suggestedName: String,
        success: TunnelParseResult.SingBoxSuccess,
        rawText: String
    ): PendingImport {
        val link = success.link
        val notSet = context.getString(R.string.value_not_set)
        return PendingImport(
            suggestedName = suggestedName.ifBlank { link.remark.ifBlank { context.getString(R.string.import_default_name) } },
            protocol = link.protocol,
            addresses = notSet,
            dns = notSet,
            endpoint = "${link.server}:${link.port}",
            allowedIps = notSet,
            mtu = null,
            obfuscation = TunnelDescriptor.describe(link),
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

internal fun TunnelProtocol.displayName(): String = when (this) {
    TunnelProtocol.WIREGUARD -> "WireGuard"
    TunnelProtocol.AMNEZIA_WG -> "AmneziaWG"
    TunnelProtocol.VLESS -> "VLESS"
    TunnelProtocol.VMESS -> "VMess"
    TunnelProtocol.TROJAN -> "Trojan"
    TunnelProtocol.SHADOWSOCKS -> "Shadowsocks"
    TunnelProtocol.HYSTERIA2 -> "Hysteria2"
    TunnelProtocol.HYSTERIA -> "Hysteria"
    TunnelProtocol.TUIC -> "TUIC"
}
