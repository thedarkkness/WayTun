package com.waytun.app.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** True on Android TV / leanback devices, where there's no touchscreen or camera, only a D-pad remote. */
fun isTelevision(context: Context): Boolean =
    context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * A clearly visible ring around whichever element the remote's D-pad has focused. Put it *before*
 * the clickable/focusable modifiers of the element it should outline.
 */
fun Modifier.tvFocusBorder(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.hasFocus || it.isFocused }
        .then(if (focused) Modifier.border(3.dp, ring, shape) else Modifier)
}
