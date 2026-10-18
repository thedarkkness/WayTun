package com.waytun.app.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/** True on Android TV / leanback devices, where there's no touchscreen or camera, only a D-pad remote. */
fun isTelevision(context: Context): Boolean =
    context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
