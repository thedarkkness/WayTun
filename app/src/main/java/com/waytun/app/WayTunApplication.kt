package com.waytun.app

import android.app.Application
import com.waytun.app.core.log.AppLog
import com.waytun.app.core.log.init
import com.waytun.app.core.obfuscation.ObfuscationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WayTunApplication : Application() {

    @Inject lateinit var obfuscationManager: ObfuscationManager

    override fun onCreate() {
        com.waytun.app.core.log.LogcatSink.install()
        super.onCreate()
        AppLog.init(this)
        com.waytun.app.ui.theme.InterfaceStyleStore.init(this)
        com.waytun.app.ui.theme.AppThemeStore.init(this)
        // Each VPN service runs in its own process and creates its own Application; only the UI
        // process owns the periodic obfuscation rotation.
        if (Application.getProcessName() == packageName) obfuscationManager.start()
    }
}
