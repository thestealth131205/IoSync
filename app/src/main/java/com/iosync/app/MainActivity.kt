package com.iosync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iosync.app.ui.screens.DetailScreen
import com.iosync.app.ui.screens.HomeScreen
import com.iosync.app.ui.screens.SettingsScreen
import com.iosync.app.ui.theme.IoSyncTheme
import com.iosync.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IoSyncTheme {
                IoSyncNavGraph()
            }
        }
    }
}

@Composable
fun IoSyncNavGraph() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Route.Home.path
    ) {
        composable(Route.Home.path) {
            HomeScreen(
                uiState = uiState,
                onStateClick = { stateId ->
                    navController.navigate(Route.Detail.buildRoute(stateId))
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings.path)
                },
                onRefresh = viewModel::refresh,
                onToggleConnection = viewModel::toggleConnection
            )
        }
        composable(Route.Detail.path) { backStackEntry ->
            val stateId = backStackEntry.arguments?.getString("stateId") ?: return@composable
            DetailScreen(
                stateId = stateId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Detail : Route("detail/{stateId}") {
        fun buildRoute(stateId: String) = "detail/$stateId"
    }
    data object Settings : Route("settings")
}
