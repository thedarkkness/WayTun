package com.waytun.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.waytun.app.core.parser.TunnelProtocol

/** Semantic colors that Material's scheme has no slot for: connection states, ping quality, protocols. */
data class WayTunColors(
    val connected: Color,
    val connecting: Color,
    val danger: Color,
    val pingGood: Color,
    val pingOk: Color,
    val pingBad: Color,
    val heroIdle: Brush,
    val heroActive: Brush,
    val heroBusy: Brush,
    val heroError: Brush
) {
    fun protocol(protocol: TunnelProtocol): Color = when (protocol) {
        TunnelProtocol.VLESS -> Indigo
        TunnelProtocol.VMESS -> Sky
        TunnelProtocol.TROJAN -> Rose
        TunnelProtocol.SHADOWSOCKS -> Mint
        TunnelProtocol.HYSTERIA2, TunnelProtocol.HYSTERIA -> Amber
        TunnelProtocol.TUIC -> Orange
        TunnelProtocol.WIREGUARD -> Lime
        TunnelProtocol.AMNEZIA_WG -> Cyan
    }

    fun ping(millis: Long?): Color = when {
        millis == null -> danger
        millis < 300 -> pingGood
        millis < 1000 -> pingOk
        else -> pingBad
    }
}

val LocalWayTunColors = staticCompositionLocalOf<WayTunColors> { error("WayTunTheme not applied") }

private val DarkExtras = WayTunColors(
    connected = Color(0xFF8CDBB4),
    connecting = Color(0xFFF2B04A),
    danger = Color(0xFFFF6B7A),
    pingGood = Color(0xFF8CDBB4),
    pingOk = Color(0xFFF2B04A),
    pingBad = Color(0xFFFF6B7A),
    heroIdle = Brush.linearGradient(listOf(Color(0xFF2B2B33), Color(0xFF121216))),
    heroActive = Brush.linearGradient(listOf(Color(0xFF1B6A50), Color(0xFF1B4A68))),
    heroBusy = Brush.linearGradient(listOf(Color(0xFF6A4A12), Color(0xFF2E2A6B))),
    heroError = Brush.linearGradient(listOf(Color(0xFF6E2230), Color(0xFF2A2048)))
)

private val LightExtras = WayTunColors(
    connected = Color(0xFF2FA46F),
    connecting = Color(0xFFE08A00),
    danger = Color(0xFFD93A52),
    pingGood = Color(0xFF2FA46F),
    pingOk = Color(0xFFE08A00),
    pingBad = Color(0xFFD93A52),
    heroIdle = Brush.linearGradient(listOf(Color(0xFFD9DEFF), Color(0xFFC3CCFA))),
    heroActive = Brush.linearGradient(listOf(Color(0xFF7DE2B5), Color(0xFF8CCBFF))),
    heroBusy = Brush.linearGradient(listOf(Color(0xFFFFD58A), Color(0xFFCBC3FF))),
    heroError = Brush.linearGradient(listOf(Color(0xFFFFB0BB), Color(0xFFD9C6FF)))
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA698FF),
    onPrimary = Color(0xFF17123F),
    primaryContainer = Color(0xFF221F4A),
    onPrimaryContainer = Color(0xFFE2DEFF),
    secondary = Cyan,
    onSecondary = Color(0xFF00313D),
    secondaryContainer = Color(0xFF0F2A33),
    onSecondaryContainer = Color(0xFFC2EEFF),
    tertiary = Rose,
    onTertiary = Color(0xFF4A0A27),
    tertiaryContainer = Color(0xFF3F1A2C),
    onTertiaryContainer = Color(0xFFFFD9E5),
    background = Color.Black,
    onBackground = Color(0xFFF4F4F8),
    surface = Color.Black,
    onSurface = Color(0xFFF4F4F8),
    surfaceVariant = Color(0xFF1A1A20),
    onSurfaceVariant = Color(0xFFC4C4D0),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C10),
    surfaceContainer = Color(0xFF111116),
    surfaceContainerHigh = Color(0xFF1B1B22),
    surfaceContainerHighest = Color(0xFF24242D),
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF26262E),
    error = Coral,
    onError = Color(0xFF4A0A14),
    errorContainer = Color(0xFF5A1624),
    onErrorContainer = Color(0xFFFFDADF)
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DEFF),
    onPrimaryContainer = Color(0xFF1B1366),
    secondary = Color(0xFF007C99),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2EEFF),
    onSecondaryContainer = Color(0xFF00313D),
    tertiary = Color(0xFFB8336A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E5),
    onTertiaryContainer = Color(0xFF4A0A27),
    background = Daybreak,
    onBackground = DaybreakOnSurface,
    surface = Daybreak,
    onSurface = DaybreakOnSurface,
    surfaceVariant = DaybreakSurfaceHigh,
    onSurfaceVariant = DaybreakOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DaybreakSurface,
    surfaceContainer = DaybreakSurface,
    surfaceContainerHigh = DaybreakSurfaceHigh,
    surfaceContainerHighest = DaybreakSurfaceHighest,
    outline = DaybreakOutline,
    outlineVariant = Color(0xFFD3D7F0),
    error = Color(0xFFD93A52),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF4A0A14)
)

/**
 * Fixed brand palette (no Android 12+ wallpaper "dynamic color": it made every screen the same
 * tint of whatever the wallpaper was, and left no room for colors that carry meaning).
 */
@Composable
fun WayTunTheme(
    darkTheme: Boolean = AppThemeStore.mode.collectAsState().value == AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val interfaceStyle by InterfaceStyleStore.style.collectAsState()
    CompositionLocalProvider(
        LocalWayTunColors provides if (darkTheme) DarkExtras else LightExtras,
        LocalInterfaceStyle provides interfaceStyle
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

object WayTunTheme {
    val colors: WayTunColors
        @Composable get() = LocalWayTunColors.current
}
