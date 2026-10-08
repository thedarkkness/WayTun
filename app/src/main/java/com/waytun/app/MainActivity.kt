package com.waytun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.waytun.app.ui.theme.WayTunTheme
import com.waytun.app.ui.tunnels.TunnelDetailScreen
import com.waytun.app.ui.tunnels.TunnelListScreen
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_LIST = "list"
private const val ROUTE_DETAIL = "detail/{tunnelId}"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WayTunTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_LIST) {
                    composable(ROUTE_LIST) {
                        TunnelListScreen(
                            onTunnelClick = { tunnelId -> navController.navigate("detail/$tunnelId") }
                        )
                    }
                    composable(ROUTE_DETAIL) {
                        TunnelDetailScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
