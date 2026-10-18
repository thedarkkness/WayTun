package com.waytun.app.data

import com.waytun.app.core.prefs.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small UI-only state that should survive restarts: which tunnel is selected on the main screen. */
class UiPrefs(private val prefs: KeyValueStore) {

    private val _selectedTunnelId = MutableStateFlow(prefs.getString(KEY_SELECTED))
    val selectedTunnelId: StateFlow<String?> = _selectedTunnelId.asStateFlow()

    fun select(id: String?) {
        _selectedTunnelId.value = id
        prefs.edit { if (id == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, id) }
    }

    companion object {
        /** The SharedPreferences file name on Android (kept from 1.3.2). */
        const val STORE_NAME = "ui_prefs"
        private const val KEY_SELECTED = "selected_tunnel"
    }
}
