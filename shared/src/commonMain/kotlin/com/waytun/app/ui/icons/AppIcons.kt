package com.waytun.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The few Material icons the app needs beyond `material-icons-core` (the extended set is several
 * megabytes of dependency for a handful of glyphs), as the standard Material path data.
 */
object AppIcons {
    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z"
        )
    }

    /** Cell-signal bars: used for "check latency". */
    val Signal: ImageVector by lazy { icon("Signal", "M17,4h3v16h-3V4zM5,14h3v6H5V14zM11,9h3v11h-3V9z") }

    val Tv: ImageVector by lazy {
        icon(
            "Tv",
            "M21,3L3,3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17L3,17L3,5h18v12z"
        )
    }

    val Mail: ImageVector by lazy {
        icon(
            "Mail",
            "M20,4L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,6c0,-1.1 -0.9,-2 -2,-2zM20,8l-8,5 -8,-5L4,6l8,5 8,-5v2z"
        )
    }

    val Pin: ImageVector by lazy {
        icon(
            "Pin",
            "M16,9V4h1c0.55,0 1,-0.45 1,-1c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3c0,0.55 0.45,1 1,1h1v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1l1,-1v-7H19v-2C17.34,12 16,10.66 16,9z"
        )
    }

    /** Paper-plane "send": the glyph for a subscription's Telegram bot-link button. */
    val Send: ImageVector by lazy { icon("Send", "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
