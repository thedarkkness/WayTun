package com.waytun.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.waytun.app.ui.logs.LogsScreen
import com.waytun.app.ui.settings.SettingsScreen
import com.waytun.app.ui.settings.SubscriptionSettingsScreen
import com.waytun.app.ui.tunnels.TunnelDetailScreen
import com.waytun.app.ui.tunnels.TunnelListHost
import com.waytun.app.ui.tunnels.TunnelListScreen

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

private fun NavBackStackEntry.argument(name: String): String = savedStateHandle.get<String>(name).orEmpty()

/** The app's screens; [LocalAppContainer] must be provided around it. */
@Composable
fun WayTunNavHost(tunnelListHost: TunnelListHost) {
    val navController = rememberNavController()
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
                host = tunnelListHost
            )
        }
        composable(ROUTE_DETAIL) { entry ->
            TunnelDetailScreen(tunnelId = entry.argument("tunnelId"), onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SUBSCRIPTION) { entry ->
            SubscriptionSettingsScreen(subscriptionId = entry.argument("subscriptionId"), onBack = { navController.popBackStack() })
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
