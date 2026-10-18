package com.waytun.app.ui.tunnels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.res.Res
import com.waytun.app.res.import_default_name
import com.waytun.app.res.vpn_error_unknown
import com.waytun.app.ui.AppContainer
import com.waytun.app.ui.ConfCheck
import com.waytun.app.ui.UiText
import com.waytun.app.ui.components.prettyJson
import com.waytun.app.ui.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class TunnelDetailUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val protocol: TunnelProtocol? = null,
    val configText: String = "",
    /** For a JSON config: the pretty-printed lines, ready before the screen draws. */
    val jsonLines: List<String> = emptyList(),
    val loadFailed: Boolean = false
)

sealed interface TunnelDetailEvent {
    data class Message(val text: UiText, val isError: Boolean = false) : TunnelDetailEvent
    data object Saved : TunnelDetailEvent
    data object Deleted : TunnelDetailEvent
}

class TunnelDetailViewModel(
    private val tunnelId: String,
    private val container: AppContainer
) : ViewModel() {

    private val tunnelRepository = container.tunnelRepository
    private val backends = container.backends

    private val _uiState = MutableStateFlow(TunnelDetailUiState())
    val uiState: StateFlow<TunnelDetailUiState> = _uiState.asStateFlow()

    private val events = Channel<TunnelDetailEvent>(Channel.BUFFERED)
    val eventFlow: Flow<TunnelDetailEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            tunnelRepository.getTunnel(tunnelId)
                .onSuccess { details ->
                    val lines = if (details.rawConfigText.trimStart().startsWith("{")) {
                        withContext(Dispatchers.Default) { prettyJson(details.rawConfigText).lines() }
                    } else emptyList()
                    _uiState.value = TunnelDetailUiState(
                        isLoading = false,
                        name = details.name,
                        protocol = details.protocol,
                        configText = details.rawConfigText,
                        jsonLines = lines
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, loadFailed = true)
                }
        }
    }

    fun onNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun onConfigTextChanged(value: String) {
        _uiState.value = _uiState.value.copy(configText = value)
    }

    fun onSave() {
        val state = _uiState.value
        viewModelScope.launch {
            val name = state.name.ifBlank { UiText.of(Res.string.import_default_name).resolve() }
            when (val result = checkConfig(state.configText, container.platform)) {
                is ConfigCheck.Link -> persist(name, result.link.protocol, state.configText)
                is ConfigCheck.Conf -> when (val check = result.check) {
                    is ConfCheck.Valid ->
                        persist(name, if (check.amnezia) TunnelProtocol.AMNEZIA_WG else TunnelProtocol.WIREGUARD, state.configText)
                    else -> sendError(describeConfError(check)!!)
                }
            }
        }
    }

    fun onDelete() {
        backends.engineHolding(tunnelId)?.let { backends.stop(it) }
        viewModelScope.launch {
            tunnelRepository.deleteTunnel(tunnelId)
                .onSuccess { events.send(TunnelDetailEvent.Deleted) }
                .onFailure { sendError(errorText(it)) }
        }
    }

    private suspend fun persist(name: String, protocol: TunnelProtocol, rawText: String) {
        tunnelRepository.updateTunnel(tunnelId, name, protocol, rawText)
            .onSuccess { events.send(TunnelDetailEvent.Saved) }
            .onFailure { sendError(errorText(it)) }
    }

    private fun errorText(e: Throwable): UiText = e.message?.let { UiText.Raw(it) } ?: UiText.of(Res.string.vpn_error_unknown)

    private fun sendError(text: UiText) {
        viewModelScope.launch { events.send(TunnelDetailEvent.Message(text, isError = true)) }
    }
}
