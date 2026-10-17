package com.waytun.app.core.log

import android.content.Context
import java.io.File

/** Journals in `files/logs`, Xray's debug output in `files/xray-debug.log` - the same places 1.3.2 used. */
fun AppLog.init(context: Context) {
    if (store == null) {
        store = FileLogStore(File(context.filesDir, "logs"), mapOf(AppLog.XRAY_DEBUG to File(context.filesDir, AppLog.XRAY_DEBUG)))
    }
}
