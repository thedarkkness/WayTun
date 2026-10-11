package com.waytun.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small UI-only state that should survive restarts: which tunnel is selected on the main screen. */
@Singleton
class UiPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private val _selectedTunnelId = MutableStateFlow(prefs.getString(KEY_SELECTED, null))
    val selectedTunnelId: StateFlow<String?> = _selectedTunnelId.asStateFlow()

    fun select(id: String?) {
        _selectedTunnelId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    private companion object {
        const val KEY_SELECTED = "selected_tunnel"
    }
}
