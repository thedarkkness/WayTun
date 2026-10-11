package com.waytun.app.ui.tunnels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.R
import com.waytun.app.core.parser.ConfigErrorKind
import com.waytun.app.core.parser.ConfigFieldError
import com.waytun.app.core.parser.TunnelConfigParser
import com.waytun.app.core.parser.TunnelParseResult
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.vpn.VpnBackends
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.waytun.app.ui.components.prettyJson
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
    data class Message(val text: String, val isError: Boolean = false) : TunnelDetailEvent
    data object Saved : TunnelDetailEvent
    data object Deleted : TunnelDetailEvent
}

@HiltViewModel
class TunnelDetailViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tunnelRepository: TunnelRepository,
    private val backends: VpnBackends,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tunnelId: String = checkNotNull(savedStateHandle["tunnelId"])

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
        val name = state.name.ifBlank { context.getString(R.string.import_default_name) }
        when (val result = TunnelConfigParser.parse(state.configText)) {
            is TunnelParseResult.WireGuardSuccess -> persist(name, TunnelProtocol.WIREGUARD, state.configText)
            is TunnelParseResult.AmneziaSuccess -> persist(name, TunnelProtocol.AMNEZIA_WG, state.configText)
            is TunnelParseResult.SingBoxSuccess -> persist(name, result.link.protocol, state.configText)
            is TunnelParseResult.Invalid -> sendError(describeInvalid(result.error))
            is TunnelParseResult.SyntaxError ->
                sendError(context.getString(R.string.import_error_structure, result.detail))
        }
    }

    fun onDelete() {
        backends.engineHolding(tunnelId)?.let { sendDisconnect(context, it) }
        viewModelScope.launch {
            tunnelRepository.deleteTunnel(tunnelId)
                .onSuccess { events.send(TunnelDetailEvent.Deleted) }
                .onFailure { sendError(it.message ?: context.getString(R.string.vpn_error_unknown)) }
        }
    }

    private fun persist(name: String, protocol: TunnelProtocol, rawText: String) {
        viewModelScope.launch {
            tunnelRepository.updateTunnel(tunnelId, name, protocol, rawText)
                .onSuccess { events.send(TunnelDetailEvent.Saved) }
                .onFailure { sendError(it.message ?: context.getString(R.string.vpn_error_unknown)) }
        }
    }

    private fun sendError(text: String) {
        viewModelScope.launch { events.send(TunnelDetailEvent.Message(text, isError = true)) }
    }

    private fun sendMessage(text: String) {
        viewModelScope.launch { events.send(TunnelDetailEvent.Message(text)) }
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
