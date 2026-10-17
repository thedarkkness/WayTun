package com.waytun.app.ui.components

import android.os.Build
import android.view.ViewParent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal actual fun DialogBackdrop(glass: Boolean) {
    if (glass) BlurBehindDialogWindow() else DimOnlyDialogWindow()
}

/** Full-screen blur behind a dialog window (Android 12+ where cross-window blur is available). */
@Composable
private fun BlurBehindDialogWindow(radius: Int = 28) {
    val view = LocalView.current
    SideEffect {
        var parent: ViewParent? = view.parent
        var provider: DialogWindowProvider? = view as? DialogWindowProvider
        while (provider == null && parent != null) {
            provider = parent as? DialogWindowProvider
            parent = parent.parent
        }
        val window = provider?.window ?: return@SideEffect
        window.setDimAmount(0.28f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = radius }
        }
    }
}

/** Plain mode: the same dimmed backdrop, with no blur of the screen behind. */
@Composable
private fun DimOnlyDialogWindow() {
    val view = LocalView.current
    SideEffect {
        var parent: ViewParent? = view.parent
        var provider: DialogWindowProvider? = view as? DialogWindowProvider
        while (provider == null && parent != null) {
            provider = parent as? DialogWindowProvider
            parent = parent.parent
        }
        provider?.window?.setDimAmount(0.5f)
    }
}

