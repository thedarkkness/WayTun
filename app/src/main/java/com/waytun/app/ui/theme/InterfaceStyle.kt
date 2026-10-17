package com.waytun.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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
    private const val PREFS = "interface_style"
    private const val KEY = "style"

    private val _style = MutableStateFlow(InterfaceStyle.GLASS)
    val style: StateFlow<InterfaceStyle> = _style.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _style.value = p.getString(KEY, null)?.let { runCatching { InterfaceStyle.valueOf(it) }.getOrNull() }
            ?: defaultFor(context)
    }

    fun set(style: InterfaceStyle) {
        _style.value = style
        prefs?.edit()?.putString(KEY, style.name)?.apply()
    }

    /** Until the user picks one: glass from Android 15 onward where the device can afford it; plain on low-RAM phones and on Android 10-14. */
    private fun defaultFor(context: Context): InterfaceStyle {
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        return if (lowRam || Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) InterfaceStyle.PLAIN else InterfaceStyle.GLASS
    }
}
