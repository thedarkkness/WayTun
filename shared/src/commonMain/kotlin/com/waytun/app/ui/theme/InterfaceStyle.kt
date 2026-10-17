package com.waytun.app.ui.theme

import com.waytun.app.core.prefs.KeyValueStore
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the app is drawn. [GLASS] is the translucent look (blurred bars and dialogs, soft color glows); [PLAIN] is flat,
 * opaque surfaces with no blur and no extra layers - far cheaper to render, for weak devices.
 */
enum class InterfaceStyle { PLAIN, GLASS }

val LocalInterfaceStyle = staticCompositionLocalOf { InterfaceStyle.GLASS }

/** The user's choice, readable synchronously at startup so the first frame already has the right look. */
object InterfaceStyleStore {
    /** The SharedPreferences file name on Android (kept from 1.3.2). */
    const val STORE_NAME = "interface_style"
    private const val KEY = "style"

    private val _style = MutableStateFlow(InterfaceStyle.GLASS)
    val style: StateFlow<InterfaceStyle> = _style.asStateFlow()

    private var prefs: KeyValueStore? = null

    /** [default] applies until the user picks one; the platform knows what its devices can afford. */
    fun init(store: KeyValueStore, default: InterfaceStyle) {
        if (prefs != null) return
        prefs = store
        _style.value = store.getString(KEY)?.let { runCatching { InterfaceStyle.valueOf(it) }.getOrNull() } ?: default
    }

    fun set(style: InterfaceStyle) {
        _style.value = style
        prefs?.edit { putString(KEY, style.name) }
    }
}
