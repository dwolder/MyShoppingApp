package com.myshoppinglist.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myshoppinglist.data.remote.SupabaseSyncService
import com.myshoppinglist.ui.screens.AuthScreen
import com.myshoppinglist.ui.screens.ListDetailScreen
import com.myshoppinglist.ui.screens.MyListsScreen
import com.myshoppinglist.ui.screens.SettingsScreen
import com.myshoppinglist.ui.screens.StoreSearchScreen

object Routes {
    const val MY_LISTS = "my_lists"
    const val LIST_DETAIL = "list_detail/{listId}"
    const val STORE_SEARCH = "store_search/{listId}"
    const val SETTINGS = "settings"
    const val AUTH = "auth"

    fun listDetail(listId: String) = "list_detail/$listId"
    fun storeSearch(listId: String) = "store_search/$listId"
}

@Composable
fun AppNavigation(syncService: SupabaseSyncService? = null) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MY_LISTS) {
        composable(Routes.MY_LISTS) {
            MyListsScreen(
                onListClick = { listId -> navController.navigate(Routes.listDetail(listId)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.LIST_DETAIL,
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) {
            ListDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStoreSearch = { listId ->
                    navController.navigate(Routes.storeSearch(listId))
                }
            )
        }

        composable(
            route = Routes.STORE_SEARCH,
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) {
            StoreSearchScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            if (syncService != null) {
                SettingsScreen(
                    syncService = syncService,
                    isLoggedIn = syncService.isAuthenticated,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAuth = { navController.navigate(Routes.AUTH) },
                    onSignOut = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.AUTH) {
            if (syncService != null) {
                AuthScreen(
                    syncService = syncService,
                    onNavigateBack = { navController.popBackStack() },
                    onAuthSuccess = {
                        navController.popBackStack(Routes.MY_LISTS, inclusive = false)
                    }
                )
            }
        }
    }
}
