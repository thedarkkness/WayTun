package com.waytun.app

import android.content.Context
import android.os.Bundle
import com.waytun.app.core.language.AppLanguage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.waytun.app.ui.logs.LogsScreen
import com.waytun.app.ui.settings.SettingsScreen
import com.waytun.app.ui.settings.SubscriptionSettingsScreen
import com.waytun.app.ui.theme.WayTunTheme
import com.waytun.app.ui.tunnels.TunnelDetailScreen
import com.waytun.app.ui.tunnels.TunnelListScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.runtime.CompositionLocalProvider
import com.waytun.app.ui.AndroidPlatformServices
import com.waytun.app.ui.tunnels.rememberAndroidTunnelListHost
import com.waytun.app.ui.AppContainer
import com.waytun.app.ui.LocalAppContainer

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{tunnelId}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LOGS = "logs"
private const val ROUTE_SUBSCRIPTION = "subscription/{subscriptionId}"

// Short horizontal slide + fade: the incoming screen travels only a third of the width and the
// outgoing one a sixth, so it reads as a gentle page turn rather than a full-width swipe.
// (Navigation-Compose's own 700ms fade default felt sluggish.)
private const val SLIDE_MS = 280
private const val FADE_IN_MS = 220
private const val FADE_OUT_MS = 160
private val slideSpec = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var container: AppContainer
    @Inject lateinit var platformServices: AndroidPlatformServices

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestSystemRefreshRate()
        platformServices.attach(this)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
            WayTunTheme {
                val navController = rememberNavController()
                // The strings re-resolve in place on a language change; a short dip-and-return of opacity makes the swap soft.
                val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].toLanguageTag()
                val fade = remember { androidx.compose.animation.core.Animatable(1f) }
                var firstLanguage by remember { mutableStateOf(true) }
                LaunchedEffect(language) {
                    if (firstLanguage) { firstLanguage = false; return@LaunchedEffect }
                    fade.snapTo(0.55f)
                    fade.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_LIST,
                    enterTransition = {
                        slideInHorizontally(slideSpec) { it / 3 } + fadeIn(tween(FADE_IN_MS))
                    },
                    exitTransition = {
                        slideOutHorizontally(slideSpec) { -it / 6 } + fadeOut(tween(FADE_OUT_MS))
                    },
                    popEnterTransition = {
                        slideInHorizontally(slideSpec) { -it / 6 } + fadeIn(tween(FADE_IN_MS))
                    },
                    popExitTransition = {
                        slideOutHorizontally(slideSpec) { it / 3 } + fadeOut(tween(FADE_OUT_MS))
                    }
                ) {
                    composable(ROUTE_LIST) {
                        TunnelListScreen(
                            onTunnelClick = { tunnelId -> navController.navigate("detail/$tunnelId") },
                            onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                            onSubscriptionSettings = { id -> navController.navigate("subscription/$id") },
                            host = rememberAndroidTunnelListHost()
                        )
                    }
                    composable(ROUTE_DETAIL) { entry ->
                        TunnelDetailScreen(
                            tunnelId = entry.arguments?.getString("tunnelId").orEmpty(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(ROUTE_SUBSCRIPTION) { entry ->
                        SubscriptionSettingsScreen(
                            subscriptionId = entry.arguments?.getString("subscriptionId").orEmpty(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenLogs = { navController.navigate(ROUTE_LOGS) }
                        )
                    }
                    composable(ROUTE_LOGS) {
                        LogsScreen(onBack = { navController.popBackStack() })
                    }
                }
                }
            }
            }
        }
    }

    override fun onDestroy() {
        platformServices.detach(this)
        super.onDestroy()
    }

    /**
     * Asks for the fastest mode the panel offers at the current resolution; the system still caps it by what the user
     * allowed in the display settings (60/90/120 Hz, adaptive), so the app runs at the device's chosen rate instead of
     * the 60 Hz some vendor builds default unlisted apps to.
     */
    private fun requestSystemRefreshRate() {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }
}
