package com.waytun.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.data.subscription.SubscriptionRepository
import com.waytun.app.data.subscription.SubscriptionSettings
import com.waytun.app.data.subscription.SubscriptionSettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionSettingsViewModel(
    private val id: String,
    private val repository: SubscriptionRepository
) : ViewModel() {

    val state: StateFlow<SubscriptionSettingsState?> = repository.observeSettings(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (SubscriptionSettings) -> SubscriptionSettings) {
        viewModelScope.launch {
            val current = repository.getSettings(id) ?: return@launch
            repository.updateSettings(id, transform(current))
        }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.rename(id, name) }
    }
}
