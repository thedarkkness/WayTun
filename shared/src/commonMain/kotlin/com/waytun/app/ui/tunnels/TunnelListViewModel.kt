package com.waytun.app.ui.tunnels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.core.net.runSpeedTest
import com.waytun.app.core.time.nowMillis
import com.waytun.app.res.Res
import com.waytun.app.res.config_import_success
import com.waytun.app.res.import_bulk_partial
import com.waytun.app.res.import_default_name
import com.waytun.app.res.import_error_clipboard_empty
import com.waytun.app.res.import_error_file_read
import com.waytun.app.res.import_error_nothing_recognized
import com.waytun.app.res.pairing_error_generic
import com.waytun.app.res.pairing_send_failed
import com.waytun.app.res.pairing_send_success
import com.waytun.app.res.subscription_empty
import com.waytun.app.res.subscription_error
import com.waytun.app.res.subscription_import_success
import com.waytun.app.res.subscription_updated_ok
import com.waytun.app.res.subscription_updating
import com.waytun.app.res.subscription_url_invalid
import com.waytun.app.res.tunnel_list_empty
import com.waytun.app.res.value_not_set
import com.waytun.app.res.vpn_error_unknown
import com.waytun.app.res.vpn_permission_denied
import com.waytun.app.ui.AppContainer
import com.waytun.app.ui.ConfCheck
import com.waytun.app.ui.ConfSummary
import com.waytun.app.ui.UiText
import com.waytun.app.ui.resolve
import com.waytun.app.core.pairing.PairingTarget
import com.waytun.app.core.parser.ShareLinkList
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.core.parser.TunnelDescriptor
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.core.text.FlagName
import com.waytun.app.data.UiPrefs
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
import kotlinx.coroutines.Job
import com.waytun.app.ui.components.HudState
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
    data class Message(val text: UiText, val isError: Boolean = false) : TunnelListEvent
}

class TunnelListViewModel(private val container: AppContainer) : ViewModel() {

    private val tunnelRepository = container.tunnelRepository
    private val subscriptionRepository = container.subscriptionRepository
    private val backends = container.backends
    private val obfuscation = container.obfuscation
    private val pingManager = container.pingManager
    private val uiPrefs = container.uiPrefs

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
            val result = runSpeedTest()
            _speed.value = result?.let { SpeedUiState.Done(it.megabitsPerSecond, it.firstByteMillis) } ?: SpeedUiState.Failed
        }
    }

    fun onPowerClick() {
        val hero = uiState.value.hero
        val holder = backends.all.firstOrNull { (_, b) -> b.state.value !is TunnelState.Disconnected }
        if (holder != null) {
            backends.stop(holder.first)
            return
        }
        uiState.value.tunnels.firstOrNull { it.isSelected }?.let { connect(it.id) }
        if (hero.title == null) sendMessage(UiText.of(Res.string.tunnel_list_empty))
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
        if (holder != null) backends.stop(holder) else connect(id)
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
            running.forEach { backends.stop(it) }
            if (running.isNotEmpty()) {
                backends.awaitStopped(running)
                delay(SAME_ENGINE_RESTART_GAP_MS)
            }
            uiPrefs.select(id)
            backends.start(engine, id, tunnel.name, settings, obfuscation.profileForConnect())
        }
    }

    // ---- selection / pings --------------------------------------------------------------------

    /** When each key was last asked to ping: repeated taps within [PING_COOLDOWN_MS] are ignored. */
    private val lastPingRequest = HashMap<String, Long>()

    fun pingAll(ids: Collection<String>? = null) {
        val now = nowMillis()
        val requested = ids ?: uiState.value.tunnels.map { it.id }
        // Called from the UI thread only.
        val targets = requested.filter { id ->
            val recent = now - (lastPingRequest[id] ?: 0L) < PING_COOLDOWN_MS
            val busy = id in pingingIds.value
            (!recent && !busy).also { allowed -> if (allowed) lastPingRequest[id] = now }
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
            sendError(UiText.of(Res.string.subscription_url_invalid))
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
        _hud.value = HudState.Loading(UiText.of(Res.string.subscription_updating))
        viewModelScope.launch {
            val result = subscriptionRepository.refresh(id, backends.activeTunnelIds())
            refreshingSubscriptions.value -= id
            // An update of a subscription that's already here is not an import: don't report "imported N".
            _hud.value = result.fold(
                onSuccess = { HudState.Success(UiText.of(Res.string.subscription_updated_ok)) },
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
        section.tunnels.forEach { tunnel -> backends.engineHolding(tunnel.id)?.let { backends.stop(it) } }
        viewModelScope.launch {
            subscriptionRepository.delete(id)
                .onFailure { sendError(errorText(it)) }
            pingManager.forget(section.tunnels.map { it.id })
        }
    }

    private fun syncMessage(result: SubscriptionSyncResult): UiText =
        if (result.skipped > 0) {
            UiText.of(Res.string.import_bulk_partial, result.imported, result.skipped)
        } else {
            UiText.of(Res.string.subscription_import_success)
        }

    private fun subscriptionError(error: Throwable): UiText =
        if (error is SubscriptionEmptyException) {
            UiText.of(Res.string.subscription_empty)
        } else {
            UiText.of(Res.string.subscription_error, error.message ?: error::class.simpleName.orEmpty())
        }

    // ---- importing ----------------------------------------------------------------------------

    /**
     * Entry point for free-form text (clipboard, QR): several share links (or a base64 block of
     * them) are imported straight away; a single config goes through the usual review dialog.
     */
    fun onTextImported(suggestedName: String, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) {
            sendError(UiText.of(Res.string.import_error_clipboard_empty))
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
                    imported == 0 -> UiText.of(Res.string.import_error_nothing_recognized)
                    skipped > 0 -> UiText.of(Res.string.import_bulk_partial, imported, skipped)
                    else -> UiText.of(Res.string.config_import_success)
                }
            )
        }
    }

    /** Parses the picked/pasted config and, if valid, stages it in [pendingImport] for review. */
    fun onConfigPicked(suggestedName: String, rawText: String) {
        val result = checkConfig(rawText, container.platform)
        viewModelScope.launch {
            when (result) {
                is ConfigCheck.Link -> _pendingImport.value = buildSingBoxPreview(suggestedName, result, rawText)
                is ConfigCheck.Conf -> when (val check = result.check) {
                    is ConfCheck.Valid -> _pendingImport.value = buildConfPreview(suggestedName, check, rawText)
                    else -> sendError(describeConfError(check)!!)
                }
            }
        }
    }

    fun onConfirmImport(name: String) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            val finalName = name.ifBlank { UiText.of(Res.string.import_default_name).resolve() }
            tunnelRepository.importTunnel(finalName, pending.protocol, pending.rawConfigText)
                .onSuccess { sendMessage(UiText.of(Res.string.config_import_success)) }
                .onFailure { sendError(errorText(it)) }
        }
    }

    fun onCancelImport() {
        _pendingImport.value = null
    }

    fun onDeleteTunnel(id: String) {
        backends.engineHolding(id)?.let { backends.stop(it) }
        viewModelScope.launch {
            tunnelRepository.deleteTunnel(id)
                .onFailure { sendError(errorText(it)) }
            pingManager.forget(listOf(id))
        }
    }

    fun onVpnPermissionDenied() {
        sendError(UiText.of(Res.string.vpn_permission_denied))
    }

    fun onImportFileReadFailed() {
        sendError(UiText.of(Res.string.import_error_file_read))
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
                sendError(UiText.of(Res.string.vpn_error_unknown))
                return@launch
            }
            val result = container.platform.sendToDevice(target, text)
            _isSendingTunnel.value = false
            result
                .onSuccess { sendMessage(UiText.of(Res.string.pairing_send_success)) }
                .onFailure { e ->
                    sendError(UiText.of(Res.string.pairing_send_failed, e.message ?: UiText.of(Res.string.pairing_error_generic)))
                }
        }
    }

    private fun errorText(e: Throwable): UiText = e.message?.let { UiText.Raw(it) } ?: UiText.of(Res.string.vpn_error_unknown)

    private fun sendMessage(text: UiText) {
        viewModelScope.launch { events.send(TunnelListEvent.Message(text)) }
    }

    private fun sendError(text: UiText) {
        viewModelScope.launch { events.send(TunnelListEvent.Message(text, isError = true)) }
    }

    private suspend fun buildConfPreview(suggestedName: String, check: ConfCheck.Valid, rawText: String): PendingImport {
        val summary: ConfSummary = check.summary
        val notSet = UiText.of(Res.string.value_not_set).resolve()
        return PendingImport(
            suggestedName = suggestedName.ifBlank { UiText.of(Res.string.import_default_name).resolve() },
            protocol = if (check.amnezia) TunnelProtocol.AMNEZIA_WG else TunnelProtocol.WIREGUARD,
            addresses = summary.addresses.joinToString(", ").ifBlank { notSet },
            dns = summary.dns.joinToString(", ").ifBlank { notSet },
            endpoint = summary.endpoint ?: notSet,
            allowedIps = summary.allowedIps.joinToString(", ").ifBlank { notSet },
            mtu = summary.mtu?.toString(),
            obfuscation = summary.obfuscation,
            rawConfigText = rawText
        )
    }

    private suspend fun buildSingBoxPreview(suggestedName: String, success: ConfigCheck.Link, rawText: String): PendingImport {
        val link = success.link
        val notSet = UiText.of(Res.string.value_not_set).resolve()
        return PendingImport(
            suggestedName = suggestedName.ifBlank { link.remark.ifBlank { UiText.of(Res.string.import_default_name).resolve() } },
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
