package com.waytun.app.ui.theme

import com.waytun.app.core.prefs.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark palette, chosen by the user - the app does not follow the system theme. */
enum class AppThemeMode { LIGHT, DARK }

/** The user's choice, readable synchronously at startup so the first frame already has the right look. */
object AppThemeStore {
    /** The SharedPreferences file name on Android (kept from 1.3.2). */
    const val STORE_NAME = "app_theme"
    private const val KEY = "mode"

    private val _mode = MutableStateFlow(AppThemeMode.LIGHT)
    val mode: StateFlow<AppThemeMode> = _mode.asStateFlow()

    private var prefs: KeyValueStore? = null

    fun init(store: KeyValueStore) {
        if (prefs != null) return
        prefs = store
        _mode.value = store.getString(KEY)?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() } ?: AppThemeMode.LIGHT
    }

    fun set(mode: AppThemeMode) {
        _mode.value = mode
        prefs?.edit { putString(KEY, mode.name) }
    }
}
