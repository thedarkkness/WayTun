package com.waytun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.waytun.app.ui.settings.SettingsScreen
import com.waytun.app.ui.theme.WayTunTheme
import com.waytun.app.ui.tunnels.TunnelDetailScreen
import com.waytun.app.ui.tunnels.TunnelListScreen
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{tunnelId}"
private const val ROUTE_SETTINGS = "settings"

// Navigation-Compose's built-in default is a 700ms fade, which reads as sluggish next to the
// ~120ms spring used by Material3 menus/dialogs elsewhere in the app. Match that speed here.
private const val SCREEN_TRANSITION_MS = 120

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WayTunTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_LIST,
                    enterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS)) },
                    exitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_MS)) },
                    popExitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_MS)) }
                ) {
                    composable(ROUTE_LIST) {
                        TunnelListScreen(
                            onTunnelClick = { tunnelId -> navController.navigate("detail/$tunnelId") },
                            onSettingsClick = { navController.navigate(ROUTE_SETTINGS) }
                        )
                    }
                    composable(ROUTE_DETAIL) {
                        TunnelDetailScreen(onBack = { navController.popBackStack() })
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
