package com.pft.financetracker.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.screens.budgets.BudgetsScreen
import com.pft.financetracker.ui.screens.dashboard.DashboardScreen
import com.pft.financetracker.ui.screens.drilldown.DrillDownScreen
import com.pft.financetracker.ui.screens.edit.EditTransactionScreen
import com.pft.financetracker.ui.screens.insights.InsightsScreen
import com.pft.financetracker.ui.screens.onboarding.OnboardingScreen
import com.pft.financetracker.ui.screens.review.ReviewScreen
import com.pft.financetracker.ui.screens.settings.SettingsScreen
import com.pft.financetracker.ui.screens.smslog.SmsLogScreen
import com.pft.financetracker.ui.screens.split.NewSplitScreen
import com.pft.financetracker.ui.screens.split.SplitDetailScreen
import com.pft.financetracker.ui.screens.split.SplitHomeScreen
import com.pft.financetracker.ui.screens.transactions.TransactionsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val SPLIT = "split"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val BUDGETS = "budgets"
    const val REVIEW = "review"
    const val SMS_LOG = "smslog?runId={runId}"
    fun smsLog(runId: Long? = null) = "smslog?runId=${runId ?: -1}"
    const val EDIT = "edit?id={id}&reviewId={reviewId}"
    fun edit(id: Long? = null, reviewId: Long? = null) = "edit?id=${id ?: -1}&reviewId=${reviewId ?: -1}"
    const val DRILL = "drill/{bucket}?category={category}"
    fun drill(bucket: InsightsEngine.Bucket, category: Category? = null) = "drill/${bucket.name}?category=${category?.name ?: ""}"
    const val NEW_SPLIT = "split/new"
    const val SPLIT_DETAIL = "split/{id}"
    fun splitDetail(id: Long) = "split/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.TRANSACTIONS, "Activity", Icons.AutoMirrored.Filled.List),
    Tab(Routes.SPLIT, "Split", Icons.Filled.CallSplit),
    Tab(Routes.INSIGHTS, "Insights", Icons.Filled.Insights),
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // A floating white pill rather than a full-width bar, as in the reference.
            if (showBar) Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                NavigationBar(
                    modifier = Modifier.clip(CircleShape).height(64.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
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
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
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
                    onOpenBudgets = { nav.navigate(Routes.BUDGETS) },
                    onOpenSmsLog = { nav.navigate(Routes.smsLog(it)) },
                    onDrill = { bucket, cat -> nav.navigate(Routes.drill(bucket, cat)) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    vm = vm,
                    onAdd = { nav.navigate(Routes.edit()) },
                    onEdit = { nav.navigate(Routes.edit(id = it)) },
                    onOpenReview = { nav.navigate(Routes.REVIEW) },
                    onOpenSmsLog = { nav.navigate(Routes.smsLog()) },
                )
            }
            composable(Routes.SPLIT) {
                SplitHomeScreen(vm, onNew = { nav.navigate(Routes.NEW_SPLIT) }, onOpen = { nav.navigate(Routes.splitDetail(it)) })
            }
            composable(Routes.NEW_SPLIT) {
                NewSplitScreen(vm, onBack = { nav.popBackStack() }, onSaved = { id -> nav.navigate(Routes.splitDetail(id)) { popUpTo(Routes.SPLIT) } })
            }
            composable(Routes.SPLIT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                SplitDetailScreen(vm, entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(Routes.edit(id = it)) })
            }
            composable(Routes.INSIGHTS) { InsightsScreen(vm, onOpenBudgets = { nav.navigate(Routes.BUDGETS) }) }
            composable(Routes.BUDGETS) { BudgetsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, onOpenSmsLog = { nav.navigate(Routes.smsLog()) }) }
            composable(Routes.REVIEW) {
                ReviewScreen(vm, onEnter = { nav.navigate(Routes.edit(reviewId = it)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.SMS_LOG, arguments = listOf(navArgument("runId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                SmsLogScreen(vm, runId = entry.arguments?.getLong("runId")?.takeIf { it > 0 }, onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(Routes.edit(id = it)) }, onOpenReview = { nav.navigate(Routes.REVIEW) })
            }
            composable(
                Routes.DRILL,
                arguments = listOf(navArgument("bucket") { type = NavType.StringType }, navArgument("category") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val bucket = runCatching { InsightsEngine.Bucket.valueOf(entry.arguments?.getString("bucket") ?: "") }.getOrDefault(InsightsEngine.Bucket.ALL)
                val cat = entry.arguments?.getString("category")?.takeIf { it.isNotEmpty() }?.let { Category.fromName(it) }
                DrillDownScreen(vm, bucket, cat, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(id = it)) })
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
                EditTransactionScreen(vm, id, reviewId) { nav.popBackStack() }
            }
        }
    }
}
