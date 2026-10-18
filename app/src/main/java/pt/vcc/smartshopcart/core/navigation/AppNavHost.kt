package pt.vcc.smartshopcart.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.toRoute
import pt.vcc.smartshopcart.feature.reports.ReportDetailKind
import pt.vcc.smartshopcart.feature.reports.ReportDetailScreen
import pt.vcc.smartshopcart.feature.reports.ReportsHomeScreen
import pt.vcc.smartshopcart.feature.reports.ReportsViewModel
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
import pt.vcc.smartshopcart.feature.calculator.CalculatorScreen
import pt.vcc.smartshopcart.feature.expenses.catalog.ExpenseCatalogScreen
import pt.vcc.smartshopcart.feature.expenses.editor.ExpenseEditorScreen
import pt.vcc.smartshopcart.feature.expenses.history.ExpenseDetailScreen
import pt.vcc.smartshopcart.feature.expenses.history.ExpenseHistoryScreen
import pt.vcc.smartshopcart.feature.expenses.home.ExpensesHomeScreen
import pt.vcc.smartshopcart.feature.history.HistoryDetailScreen
import pt.vcc.smartshopcart.feature.history.HistoryScreen
import pt.vcc.smartshopcart.feature.home.HomeScreen
import pt.vcc.smartshopcart.feature.product.ItemEditorScreen
import pt.vcc.smartshopcart.feature.scanner.ScannerScreen
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.feature.session.PlannedSessionScreen
import pt.vcc.smartshopcart.feature.settings.AboutScreen
import pt.vcc.smartshopcart.feature.settings.SettingsScreen
import pt.vcc.smartshopcart.feature.shoppinglist.ListDetailScreen
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
    TopLevelDestination(ReportsRoute, ReportsRoute::class, R.string.nav_reports) {
        painterResource(R.drawable.ic_reports)
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
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenSession = navController::navigateToSession,
                    onOpenLists = { navController.navigateTopLevel(ListsRoute) },
                    onOpenExpenses = { navController.navigate(ExpensesHomeRoute) },
                    onOpenReports = { navController.navigateTopLevel(ReportsRoute) },
                )
            }
            composable<ReportsRoute> {
                ReportsHomeScreen(
                    onOpenDetail = { kind -> navController.navigate(ReportDetailRoute(kind.name)) },
                    viewModel = hiltViewModel(),
                )
            }
            composable<ReportDetailRoute> { entry ->
                val parent = remember(entry) { navController.getBackStackEntry<ReportsRoute>() }
                ReportDetailScreen(
                    kind = ReportDetailKind.valueOf(entry.toRoute<ReportDetailRoute>().kind),
                    onBack = { navController.popBackStack() },
                    viewModel = hiltViewModel<ReportsViewModel>(parent),
                )
            }
            composable<ListsRoute> {
                ShoppingListsScreen(onOpenList = { listId -> navController.navigate(ListDetailRoute(listId)) })
            }
            composable<ListDetailRoute> {
                ListDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenList = { listId ->
                        navController.navigate(ListDetailRoute(listId)) {
                            popUpTo<ListDetailRoute> { inclusive = true }
                        }
                    },
                    onOpenSession = navController::navigateToSession,
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(onOpenPurchase = { sessionId -> navController.navigate(HistoryDetailRoute(sessionId)) })
            }
            composable<HistoryDetailRoute> { HistoryDetailScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onOpenAbout = { navController.navigate(AboutRoute) }) }
            composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
            composable<CalculatorRoute> {
                CalculatorScreen(
                    onBack = { navController.popBackStack() },
                    onScan = { sessionId -> navController.navigate(ScannerRoute(sessionId)) },
                    onEditItem = { itemId -> navController.navigate(ItemEditRoute(itemId)) },
                    onSessionClosed = navController::closeSession,
                )
            }
            composable<PlannedSessionRoute> {
                PlannedSessionScreen(
                    onBack = { navController.popBackStack() },
                    onScan = { sessionId, listItemId -> navController.navigate(ScannerRoute(sessionId, listItemId)) },
                    onEditItem = { itemId -> navController.navigate(ItemEditRoute(itemId)) },
                    onSessionClosed = navController::closeSession,
                )
            }
            composable<ItemEditRoute> { ItemEditorScreen(onBack = { navController.popBackStack() }) }
            composable<ScannerRoute> {
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    onAdded = { navController.popBackStack() },
                )
            }
            composable<ExpenseCatalogRoute> { ExpenseCatalogScreen(onBack = { navController.popBackStack() }) }
            composable<ExpensesHomeRoute> {
                ExpensesHomeScreen(
                    onBack = { navController.popBackStack() },
                    onNewExpense = { categoryId, productId ->
                        navController.navigate(ExpenseEditorRoute(categoryId = categoryId, productId = productId))
                    },
                    onResumeDraft = { navController.navigate(ExpenseEditorRoute(resumeDraft = true)) },
                    onOpenHistory = { navController.navigate(ExpenseHistoryRoute) },
                    onOpenCatalog = { navController.navigate(ExpenseCatalogRoute) },
                )
            }
            composable<ExpenseEditorRoute> {
                ExpenseEditorScreen(
                    onBack = { navController.popBackStack() },
                    onFinished = navController::closeExpenseEditor,
                )
            }
            composable<ExpenseHistoryRoute> {
                ExpenseHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExpense = { navController.navigate(ExpenseDetailRoute(it)) },
                    onEditExpense = { navController.navigate(ExpenseEditorRoute(expenseId = it)) },
                    onRepeatExpense = { navController.navigate(ExpenseEditorRoute(repeatFromId = it)) },
                )
            }
            composable<ExpenseDetailRoute> {
                ExpenseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(ExpenseEditorRoute(expenseId = it)) },
                    onRepeat = { navController.navigate(ExpenseEditorRoute(repeatFromId = it)) },
                )
            }
        }
    }
}

private fun NavHostController.navigateToSession(sessionId: String, mode: SessionMode) {
    when (mode) {
        SessionMode.CALCULATOR -> navigate(CalculatorRoute(sessionId))
        SessionMode.PLANNED -> navigate(PlannedSessionRoute(sessionId))
    }
}

/** Após concluir/cancelar volta ao Início, removendo a sessão (e a lista de origem) da pilha. */
private fun NavHostController.closeSession() {
    navigate(HomeRoute) {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}

/** Após guardar/cancelar uma despesa volta ao ecrã anterior ao editor. */
private fun NavHostController.closeExpenseEditor() {
    popBackStack<ExpenseEditorRoute>(inclusive = true)
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
