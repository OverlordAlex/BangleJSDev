package com.itsabugnotafeature.scrolltimesync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.itsabugnotafeature.scrolltimesync.ui.dashboard.DashboardScreen
import com.itsabugnotafeature.scrolltimesync.ui.detail.DetailScreen
import com.itsabugnotafeature.scrolltimesync.ui.settings.SettingsScreen
import com.itsabugnotafeature.scrolltimesync.ui.settings.SyncHistoryScreen
import com.itsabugnotafeature.scrolltimesync.ui.weekly.WeeklySummaryScreen
import kotlinx.serialization.Serializable

@Serializable
object DashboardRoute

@Serializable
data class DetailRoute(val dataType: String)

@Serializable
object SettingsRoute

@Serializable
object WeeklySummaryRoute

@Serializable
object SyncHistoryRoute

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = DashboardRoute) {
        composable<DashboardRoute> {
            DashboardScreen(
                onNavigateToDetail = { dataType ->
                    navController.navigate(DetailRoute(dataType))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                },
                onNavigateToWeeklySummary = {
                    navController.navigate(WeeklySummaryRoute)
                },
            )
        }
        composable<DetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DetailRoute>()
            DetailScreen(
                dataType = route.dataType,
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSyncHistory = {
                    navController.navigate(SyncHistoryRoute)
                },
            )
        }
        composable<SyncHistoryRoute> {
            SyncHistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<WeeklySummaryRoute> {
            WeeklySummaryScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
