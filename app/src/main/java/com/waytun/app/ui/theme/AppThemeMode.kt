package com.waytun.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark palette, chosen by the user - the app does not follow the system theme. */
enum class AppThemeMode { LIGHT, DARK }

/** The user's choice, readable synchronously at startup so the first frame already has the right look. */
object AppThemeStore {
    private const val PREFS = "app_theme"
    private const val KEY = "mode"

    private val _mode = MutableStateFlow(AppThemeMode.LIGHT)
    val mode: StateFlow<AppThemeMode> = _mode.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _mode.value = p.getString(KEY, null)?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: AppThemeMode.LIGHT
    }

    fun set(mode: AppThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString(KEY, mode.name)?.apply()
    }
}
