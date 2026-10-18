package pt.vcc.smartshopcart.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.feature.history.HistoryScreen
import pt.vcc.smartshopcart.feature.home.HomeScreen
import pt.vcc.smartshopcart.feature.settings.SettingsScreen
import pt.vcc.smartshopcart.feature.shoppinglist.ShoppingListsScreen
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: @Composable () -> Painter,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, HomeRoute::class, R.string.nav_home) { rememberVectorPainter(Icons.Filled.Home) },
    TopLevelDestination(ListsRoute, ListsRoute::class, R.string.nav_lists) {
        rememberVectorPainter(Icons.AutoMirrored.Filled.List)
    },
    TopLevelDestination(HistoryRoute, HistoryRoute::class, R.string.nav_history) {
        painterResource(R.drawable.ic_history)
    },
    TopLevelDestination(SettingsRoute, SettingsRoute::class, R.string.nav_settings) {
        rememberVectorPainter(Icons.Filled.Settings)
    },
)

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { top -> destination?.hasRoute(top.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { top ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(top.route) },
                            icon = { Icon(top.icon(), contentDescription = null) },
                            label = { Text(stringResource(top.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenCalculator = {},
                    onOpenLists = { navController.navigateTopLevel(ListsRoute) },
                )
            }
            composable<ListsRoute> { ShoppingListsScreen() }
            composable<HistoryRoute> { HistoryScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
