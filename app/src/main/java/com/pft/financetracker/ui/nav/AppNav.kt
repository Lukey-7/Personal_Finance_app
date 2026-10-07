package com.pft.financetracker.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.LocalNavAnimatedScope
import com.pft.financetracker.ui.components.LocalSharedTransitionScope
import com.pft.financetracker.ui.components.cappedScale
import com.pft.financetracker.ui.components.raised
import com.pft.financetracker.ui.model.QuickAddDraft
import com.pft.financetracker.ui.screens.budgets.BudgetsScreen
import com.pft.financetracker.ui.screens.dashboard.DashboardScreen
import com.pft.financetracker.ui.screens.detail.TransactionDetailScreen
import com.pft.financetracker.ui.screens.drilldown.DrillDownScreen
import com.pft.financetracker.ui.screens.edit.EditTransactionScreen
import com.pft.financetracker.ui.screens.edit.Prefill
import com.pft.financetracker.ui.screens.importer.ImportScreen
import com.pft.financetracker.ui.screens.insights.InsightsScreen
import com.pft.financetracker.ui.screens.onboarding.OnboardingScreen
import com.pft.financetracker.ui.screens.quickadd.QuickAddSheet
import com.pft.financetracker.ui.screens.review.ReviewScreen
import com.pft.financetracker.ui.screens.settings.SettingsScreen
import com.pft.financetracker.ui.screens.settings.SettingsSectionScreen
import com.pft.financetracker.ui.screens.smslog.SmsLogScreen
import com.pft.financetracker.ui.screens.split.NewSplitScreen
import com.pft.financetracker.ui.screens.split.SplitDetailScreen
import com.pft.financetracker.ui.screens.split.SplitHomeScreen
import com.pft.financetracker.ui.screens.tools.ToolsScreen
import com.pft.financetracker.ui.screens.transactions.TransactionsScreen
import com.pft.financetracker.ui.theme.LocalReducedMotion
import com.pft.financetracker.ui.theme.surfaces

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val SPLIT = "split"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    fun settingsSection(section: String) = "settings/$section"
    const val BUDGETS = "budgets"
    const val REVIEW = "review"
    const val SMS_LOG = "smslog?runId={runId}"
    fun smsLog(runId: Long? = null) = "smslog?runId=${runId ?: -1}"
    const val TXN = "txn/{id}"
    fun txn(id: Long) = "txn/$id"
    const val EDIT = "edit?id={id}&reviewId={reviewId}&amount={amount}&category={category}&note={note}&cash={cash}"
    fun edit(id: Long? = null, reviewId: Long? = null) = "edit?id=${id ?: -1}&reviewId=${reviewId ?: -1}"
    /** The full editor, filled in from the quick-add sheet. */
    fun edit(d: QuickAddDraft) = "edit?id=-1&reviewId=-1&amount=${Uri.encode(d.amount)}&category=${d.category?.name ?: ""}&note=${Uri.encode(d.note)}&cash=${d.cash}"
    const val DRILL = "drill/{bucket}?category={category}"
    fun drill(bucket: InsightsEngine.Bucket, category: Category? = null) = "drill/${bucket.name}?category=${category?.name ?: ""}"
    const val NEW_SPLIT = "split/new"
    const val IMPORT = "import"
    const val SPLIT_DETAIL = "split/{id}"
    fun splitDetail(id: Long) = "split/$id"
    const val TOOLS = "tools"
    const val RECURRING = "recurring"
    const val BILLS = "bills"
    const val CARDS = "cards"
    const val GOALS = "goals"
    const val TAX = "tax"
    const val NET_WORTH = "networth"
    const val ASK = "ask"
}

/** Outline glyph normally; the filled one marks the selected tab, a second cue besides colour. */
private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.TRANSACTIONS, "Activity", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(Routes.SPLIT, "Split", Icons.AutoMirrored.Outlined.CallSplit, Icons.AutoMirrored.Filled.CallSplit),
    Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

private val tabRoutes = tabs.map { it.route }.toSet()

/*
 * Screen transitions. Pushing a screen slides it in a little from the end over a fade; going back reverses it, and
 * follows the finger with predictive back. Switching tabs only cross-fades. Springs throughout; nothing moves when
 * animations are off.
 */
private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun isTabSwitch(s: Scope) = s.initialState.destination.route in tabRoutes && s.targetState.destination.route in tabRoutes

private fun enter(reduced: Boolean): Scope.() -> EnterTransition = {
    when {
        reduced -> EnterTransition.None
        isTabSwitch(this) -> fadeIn(spring(stiffness = 900f))
        else -> slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 6 } + fadeIn(spring(stiffness = 700f))
    }
}

private fun exit(reduced: Boolean): Scope.() -> ExitTransition = {
    when {
        reduced -> ExitTransition.None
        isTabSwitch(this) -> fadeOut(spring(stiffness = 900f))
        else -> fadeOut(spring(stiffness = 700f))
    }
}

private fun popEnter(reduced: Boolean): Scope.() -> EnterTransition = {
    if (reduced) EnterTransition.None else fadeIn(spring(stiffness = 700f))
}

private fun popExit(reduced: Boolean): Scope.() -> ExitTransition = {
    if (reduced) ExitTransition.None
    else slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 5 } +
        scaleOut(spring(stiffness = 500f), targetScale = 0.96f) + fadeOut(spring(stiffness = 700f))
}

/**
 * The app: five tabs on a floating pill, every other screen pushed over them. [pendingRoute] is a screen asked for from
 * outside (the quick-add sheet's "More details"); it is opened once and handed back through [onRouteHandled].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(vm: AppViewModel = viewModel(), pendingRoute: String? = null, onRouteHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val onboarded by vm.onboarded.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabRoutes
    val reduced = LocalReducedMotion.current
    var quickAdd by remember { mutableStateOf(false) }
    val openAdd = { quickAdd = true }
    val openTxn: (Long) -> Unit = { nav.navigate(Routes.txn(it)) }

    LaunchedEffect(pendingRoute, backStack != null) {
        if (pendingRoute != null && backStack != null) { nav.navigate(pendingRoute); onRouteHandled() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (showBar) NavPill(currentRoute, reviewCount) { route -> switchTab(nav, route) } },
    ) { padding ->
        // The NavHost fills the whole window: tab screens draw beneath the floating pill and pad their own ends by
        // LocalBottomBarPadding, instead of being clipped at a hard edge above it.
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalBottomBarPadding provides padding.calculateBottomPadding(),
                LocalSharedTransitionScope provides this,
            ) {
                NavHost(
                    navController = nav,
                    startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING,
                    enterTransition = enter(reduced),
                    exitTransition = exit(reduced),
                    popEnterTransition = popEnter(reduced),
                    popExitTransition = popExit(reduced),
                ) {
                    screen(Routes.ONBOARDING) {
                        OnboardingScreen(vm) {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        }
                    }
                    screen(Routes.HOME) {
                        DashboardScreen(
                            vm = vm,
                            onAdd = openAdd,
                            onEdit = openTxn,
                            onOpenReview = { nav.navigate(Routes.REVIEW) },
                            onOpenTransactions = { switchTab(nav, Routes.TRANSACTIONS) },
                            onOpenBudgets = { nav.navigate(Routes.BUDGETS) },
                            onOpenSmsLog = { nav.navigate(Routes.smsLog(it)) },
                            onDrill = { bucket, cat -> nav.navigate(Routes.drill(bucket, cat)) },
                            onOpenSplit = { nav.navigate(Routes.splitDetail(it)) },
                            onOpenTools = { nav.navigate(Routes.TOOLS) },
                        )
                    }
                    screen(Routes.TRANSACTIONS) {
                        TransactionsScreen(
                            vm = vm,
                            onAdd = openAdd,
                            onOpen = openTxn,
                            onOpenReview = { nav.navigate(Routes.REVIEW) },
                            onOpenSmsLog = { nav.navigate(Routes.smsLog()) },
                            onOpenImport = { nav.navigate(Routes.IMPORT) },
                            onSplit = { nav.navigate(Routes.NEW_SPLIT) },
                        )
                    }
                    screen(Routes.TXN, listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        TransactionDetailScreen(
                            vm, entry.arguments?.getLong("id") ?: -1L,
                            onEdit = { nav.navigate(Routes.edit(id = it)) },
                            onOpenSplit = { nav.navigate(Routes.splitDetail(it)) },
                            onOpenTransaction = openTxn,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    screen(Routes.SPLIT) {
                        SplitHomeScreen(vm, onNew = { nav.navigate(Routes.NEW_SPLIT) }, onOpen = { nav.navigate(Routes.splitDetail(it)) })
                    }
                    screen(Routes.NEW_SPLIT) {
                        NewSplitScreen(vm, onBack = { nav.popBackStack() }, onSaved = { id -> nav.navigate(Routes.splitDetail(id)) { popUpTo(Routes.SPLIT) } })
                    }
                    screen(Routes.SPLIT_DETAIL, listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        SplitDetailScreen(vm, entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() }, onOpenTransaction = openTxn)
                    }
                    screen(Routes.INSIGHTS) {
                        InsightsScreen(vm, onOpenBudgets = { nav.navigate(Routes.BUDGETS) }, onOpenTools = { nav.navigate(Routes.TOOLS) },
                            onDrill = { bucket, cat -> nav.navigate(Routes.drill(bucket, cat)) })
                    }
                    screen(Routes.TOOLS) { ToolsScreen(vm, onOpen = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
                    screen(Routes.ASK) { com.pft.financetracker.ui.screens.ask.AskScreen(vm, onOpenTransaction = openTxn) { nav.popBackStack() } }
                    screen(Routes.NET_WORTH) { com.pft.financetracker.ui.screens.networth.NetWorthScreen(vm) { nav.popBackStack() } }
                    screen(Routes.TAX) { com.pft.financetracker.ui.screens.tax.TaxScreen(vm) { nav.popBackStack() } }
                    screen(Routes.GOALS) { com.pft.financetracker.ui.screens.goals.GoalsScreen(vm) { nav.popBackStack() } }
                    screen(Routes.CARDS) { com.pft.financetracker.ui.screens.cards.CardsScreen(vm) { nav.popBackStack() } }
                    screen(Routes.BILLS) { com.pft.financetracker.ui.screens.bills.BillsScreen(vm, onOpenTransaction = openTxn) { nav.popBackStack() } }
                    screen(Routes.RECURRING) { com.pft.financetracker.ui.screens.recurring.RecurringScreen(vm, onOpenTransaction = openTxn) { nav.popBackStack() } }
                    screen(Routes.BUDGETS) { BudgetsScreen(vm, onBack = { nav.popBackStack() }) }
                    screen(Routes.SETTINGS) { SettingsScreen(vm, onOpenSection = { nav.navigate(Routes.settingsSection(it)) }) }
                    screen(Routes.SETTINGS_SECTION, listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                        SettingsSectionScreen(
                            vm, entry.arguments?.getString("section") ?: "",
                            onBack = { nav.popBackStack() },
                            onOpenSmsLog = { nav.navigate(Routes.smsLog()) },
                            onOpenImport = { nav.navigate(Routes.IMPORT) },
                        )
                    }
                    screen(Routes.IMPORT) {
                        ImportScreen(vm, onBack = { nav.popBackStack() }, onOpenSplits = { switchTab(nav, Routes.SPLIT) })
                    }
                    screen(Routes.REVIEW) {
                        ReviewScreen(vm, onEnter = { nav.navigate(Routes.edit(reviewId = it)) }, onBack = { nav.popBackStack() })
                    }
                    screen(Routes.SMS_LOG, listOf(navArgument("runId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                        SmsLogScreen(vm, runId = entry.arguments?.getLong("runId")?.takeIf { it > 0 }, onBack = { nav.popBackStack() }, onOpenTransaction = openTxn, onOpenReview = { nav.navigate(Routes.REVIEW) })
                    }
                    screen(
                        Routes.DRILL,
                        listOf(navArgument("bucket") { type = NavType.StringType }, navArgument("category") { type = NavType.StringType; defaultValue = "" }),
                    ) { entry ->
                        val bucket = runCatching { InsightsEngine.Bucket.valueOf(entry.arguments?.getString("bucket") ?: "") }.getOrDefault(InsightsEngine.Bucket.ALL)
                        val cat = entry.arguments?.getString("category")?.takeIf { it.isNotEmpty() }?.let { Category.fromName(it) }
                        DrillDownScreen(vm, bucket, cat, onBack = { nav.popBackStack() }, onEdit = openTxn)
                    }
                    screen(
                        Routes.EDIT,
                        listOf(
                            navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("reviewId") { type = NavType.LongType; defaultValue = -1L },
                            navArgument("amount") { type = NavType.StringType; defaultValue = "" },
                            navArgument("category") { type = NavType.StringType; defaultValue = "" },
                            navArgument("note") { type = NavType.StringType; defaultValue = "" },
                            navArgument("cash") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) { entry ->
                        val a = entry.arguments
                        val id = a?.getLong("id")?.takeIf { it >= 0 }
                        val reviewId = a?.getLong("reviewId")?.takeIf { it >= 0 }
                        val prefill = Prefill(
                            amount = a?.getString("amount").orEmpty(),
                            category = a?.getString("category")?.takeIf { it.isNotEmpty() }?.let { Category.fromName(it) },
                            note = a?.getString("note").orEmpty(),
                            cash = a?.getBoolean("cash") ?: false,
                        ).takeIf { it.amount.isNotEmpty() || it.category != null || it.note.isNotEmpty() || it.cash }
                        EditTransactionScreen(vm, id, reviewId, prefill, onOpenSplit = { nav.navigate(Routes.splitDetail(it)) }, onOpenTransaction = openTxn) { nav.popBackStack() }
                    }
                }
            }
        }
    }

    if (quickAdd) {
        val txns by vm.transactions.collectAsState()
        val cashCounted by vm.countCashAsSpend.collectAsState()
        QuickAddSheet(
            recent = txns,
            cashCounted = cashCounted,
            onSave = { vm.save(it); quickAdd = false },
            onMoreDetails = { d -> quickAdd = false; nav.navigate(Routes.edit(d)) },
            onDismiss = { quickAdd = false },
        )
    }
}

/** A destination that hands its animation scope down, so rows can share elements with the screen they open. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

private fun switchTab(nav: androidx.navigation.NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The floating nav pill: a raised surface over a fade into the page, so rows sliding under it never meet the system
 * buttons. Labels stay one line and stop growing at 1.3× (the pill grows by that much so icons are never clipped).
 */
@Composable
private fun NavPill(currentRoute: String?, reviewCount: Int, onSelect: (String) -> Unit) {
    val s = surfaces
    val page = MaterialTheme.colorScheme.background
    val fontScale = LocalDensity.current.fontScale
    val barHeight = 66.dp + (MaterialTheme.typography.labelSmall.lineHeight.value * (minOf(fontScale, 1.3f) - 1f)).coerceAtLeast(0f).dp
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to page.copy(alpha = 0f), 0.4f to page))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)
    ) {
        NavigationBar(
            modifier = Modifier
                .raised(CircleShape, 12.dp, s.shadow)
                .clip(CircleShape)
                .border(1.dp, s.hairline, CircleShape)
                .height(barHeight),
            containerColor = s.raised,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val glyph = if (selected) tab.selectedIcon else tab.icon
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = {
                        if (tab.route == Routes.TRANSACTIONS && reviewCount > 0) {
                            BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text(reviewCount.toString()) } }) {
                                Icon(glyph, "${tab.label}, $reviewCount to review")
                            }
                        } else Icon(glyph, tab.label)
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall.cappedScale(), maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = s.accentSoft,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
