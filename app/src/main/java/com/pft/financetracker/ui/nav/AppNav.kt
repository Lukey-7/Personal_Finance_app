package com.pft.financetracker.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.screens.budgets.BudgetsScreen
import com.pft.financetracker.ui.screens.dashboard.DashboardScreen
import com.pft.financetracker.ui.screens.edit.EditTransactionScreen
import com.pft.financetracker.ui.screens.insights.InsightsScreen
import com.pft.financetracker.ui.screens.onboarding.OnboardingScreen
import com.pft.financetracker.ui.screens.review.ReviewScreen
import com.pft.financetracker.ui.screens.settings.SettingsScreen
import com.pft.financetracker.ui.screens.transactions.TransactionsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val INSIGHTS = "insights"
    const val BUDGETS = "budgets"
    const val SETTINGS = "settings"
    const val REVIEW = "review"
    const val EDIT = "edit?id={id}&reviewId={reviewId}"
    fun edit(id: Long? = null, reviewId: Long? = null) = "edit?id=${id ?: -1}&reviewId=${reviewId ?: -1}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.TRANSACTIONS, "Activity", Icons.AutoMirrored.Filled.List),
    Tab(Routes.INSIGHTS, "Insights", Icons.Filled.Insights),
    Tab(Routes.BUDGETS, "Budgets", Icons.Filled.Savings),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val onboarded by vm.onboarded.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (tab.route == Routes.TRANSACTIONS && reviewCount > 0) {
                                BadgedBox(badge = { Badge { Text(reviewCount.toString()) } }) { Icon(tab.icon, tab.label) }
                            } else Icon(tab.icon, tab.label)
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(vm) {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                }
            }
            composable(Routes.HOME) {
                DashboardScreen(
                    vm = vm,
                    onAdd = { nav.navigate(Routes.edit()) },
                    onEdit = { nav.navigate(Routes.edit(id = it)) },
                    onOpenReview = { nav.navigate(Routes.REVIEW) },
                    onOpenTransactions = { nav.navigate(Routes.TRANSACTIONS) },
                    onOpenInsights = { nav.navigate(Routes.INSIGHTS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    vm = vm,
                    onAdd = { nav.navigate(Routes.edit()) },
                    onEdit = { nav.navigate(Routes.edit(id = it)) },
                    onOpenReview = { nav.navigate(Routes.REVIEW) },
                )
            }
            composable(Routes.INSIGHTS) { InsightsScreen(vm) }
            composable(Routes.BUDGETS) { BudgetsScreen(vm) }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            composable(Routes.REVIEW) {
                ReviewScreen(vm, onEnter = { nav.navigate(Routes.edit(reviewId = it)) }, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("reviewId") { type = NavType.LongType; defaultValue = -1L },
                )
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                val reviewId = entry.arguments?.getLong("reviewId")?.takeIf { it >= 0 }
                EditTransactionScreen(vm, id = id, reviewId = reviewId, onBack = { nav.popBackStack() })
            }
        }
    }
}
