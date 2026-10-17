package com.waytun.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.waytun.app.core.prefs.SharedPreferencesStore

/** Both stores over the SharedPreferences files 1.3.2 used, so the user's choices carry over. */
fun initThemeStores(context: Context) {
    InterfaceStyleStore.init(SharedPreferencesStore(context, InterfaceStyleStore.STORE_NAME), defaultInterfaceStyle(context))
    AppThemeStore.init(SharedPreferencesStore(context, AppThemeStore.STORE_NAME))
}

/** Until the user picks one: glass from Android 15 onward where the device can afford it; plain on low-RAM phones and on Android 10-14. */
private fun defaultInterfaceStyle(context: Context): InterfaceStyle {
    val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    return if (lowRam || Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) InterfaceStyle.PLAIN else InterfaceStyle.GLASS
}
