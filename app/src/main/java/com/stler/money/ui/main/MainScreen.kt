package com.stler.money.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.stler.money.domain.model.Account
import com.stler.money.domain.model.Category
import com.stler.money.domain.model.Transaction
import com.stler.money.ui.accounts.AccountFormSheet
import com.stler.money.ui.accounts.AccountsScreen
import com.stler.money.ui.analytics.AnalyticsScreen
import com.stler.money.ui.categories.CategoriesScreen
import com.stler.money.ui.categories.CategoryFormSheet
import com.stler.money.ui.feedback.FeedbackScreen
import com.stler.money.ui.help.HelpScreen
import com.stler.money.ui.menu.MenuScreen
import com.stler.money.ui.navigation.Screen
import com.stler.money.ui.settings.SettingsScreen
import com.stler.money.ui.transaction.TransactionFormSheet
import com.stler.money.ui.transactions.FilterMenu
import com.stler.money.ui.transactions.TransactionFilterState
import com.stler.money.ui.transactions.TransactionsScreen
import com.stler.money.ui.transactions.TransactionsViewModel
import com.stler.money.ui.util.ErrorSnackbarEffect
import com.stler.money.ui.util.LocalSnackbarHostState

/** Full-screen overlays reachable from the Menu tab — was a `List<String>` of magic names. */
private enum class Overlay { Settings, Help, Feedback, About }

/**
 * Bottom-nav-only app shell — tech spec §8 "Navigation shape" / §12. Tab order
 * is Accounts, Transactions, Categories, Analytics, Menu; the start
 * destination is Transactions despite not being tab 1 (two independent
 * lists — see the spec note). There is no sidebar mode.
 */
@Composable
fun MainScreen(
    onSignOut: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var overlayStack by remember { mutableStateOf(emptyList<Overlay>()) }
    val currentOverlay = overlayStack.lastOrNull()
    fun pushOverlay(screen: Overlay) { overlayStack = overlayStack + screen }
    fun popOverlay() { overlayStack = overlayStack.dropLast(1) }

    var showTransactionForm by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var copyFromTransaction by remember { mutableStateOf<Transaction?>(null) }
    fun openCreateTransaction() { editingTransaction = null; copyFromTransaction = null; showTransactionForm = true }
    fun openEditTransaction(t: Transaction) { editingTransaction = t; copyFromTransaction = null; showTransactionForm = true }
    fun openCopyTransaction(t: Transaction) { editingTransaction = null; copyFromTransaction = t }

    var showAccountForm by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    fun openCreateAccount() { editingAccount = null; showAccountForm = true }
    fun openEditAccount(a: Account) { editingAccount = a; showAccountForm = true }

    var showCategoryForm by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    fun openCreateCategory() { editingCategory = null; showCategoryForm = true }
    fun openEditCategory(c: Category) { editingCategory = c; showCategoryForm = true }

    val navController = rememberNavController()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val authData by viewModel.authData.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTransactionsRoute = currentRoute == null || currentRoute.startsWith("transactions")

    // Filter sheet trigger lives in the top bar (a sibling of the NavHost), but the state
    // it reads/writes belongs to TransactionsScreen's own ViewModel. `hiltViewModel(entry)`
    // with the SAME NavBackStackEntry TransactionsScreen's own `hiltViewModel()` resolves to
    // (guaranteed while on that route — Navigation Compose scopes both to that entry's
    // ViewModelStore) returns the identical cached instance, not a second one.
    var showFilterMenu by remember { mutableStateOf(false) }
    val transactionsViewModel: TransactionsViewModel? =
        if (isTransactionsRoute && backStackEntry != null) hiltViewModel(backStackEntry!!) else null
    val filterState: TransactionFilterState
    val filterAccounts: List<Account>
    val filterCategories: List<Category>
    val filterSearch: String
    if (transactionsViewModel != null) {
        filterState = transactionsViewModel.filterState.collectAsStateWithLifecycle().value
        filterAccounts = transactionsViewModel.accounts.collectAsStateWithLifecycle().value
        filterCategories = transactionsViewModel.categories.collectAsStateWithLifecycle().value
        filterSearch = transactionsViewModel.search.collectAsStateWithLifecycle().value
    } else {
        filterState = TransactionFilterState()
        filterAccounts = emptyList()
        filterCategories = emptyList()
        filterSearch = ""
    }

    val screenTitle = when (currentRoute) {
        Screen.ACCOUNTS -> "Accounts"
        Screen.CATEGORIES -> "Categories"
        Screen.ANALYTICS -> "Analytics"
        Screen.MENU -> "Menu"
        else -> "Transactions"
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Row-tap-to-filter (Accounts/Categories → Transactions) needs a genuinely fresh
    // Transactions instance with the new accountId/categoryId args, not `navigateTo`'s
    // tab-switch behavior: restoreState=true there restores the *saved* Transactions
    // entry (old SavedStateHandle, no filter) instead of applying the new query args —
    // that's why tapping a row showed the unfiltered list. Popping the existing entry
    // (inclusive) discards its saved state so the push below creates a fresh one.
    fun navigateToTransactionsFiltered(route: String) {
        navController.navigate(route) {
            popUpTo(Screen.TRANSACTIONS) { inclusive = true }
            launchSingleTop = true
        }
    }

    // The bottom-nav Transactions icon must always land on the *unfiltered* list — without
    // this, tapping it after a filtered drill-down would hit the same restoreState=true bug
    // `navigateToTransactionsFiltered` above was written to avoid (the saved entry, filter
    // and all, would get restored instead of a fresh unfiltered one).
    fun navigateToTransactionsHome() = navigateToTransactionsFiltered("transactions")

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
    ErrorSnackbarEffect(viewModel)
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                MoneyTopAppBar(
                    title = screenTitle,
                    showBack = false,
                    onBack = {},
                    syncState = syncState,
                    onSyncClick = viewModel::triggerSync,
                    filterActive = filterState.isActive,
                    onFilterClick = if (isTransactionsRoute) { { showFilterMenu = true } } else null,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Outlined.CreditCard, contentDescription = "Accounts") },
                        selected = currentRoute == Screen.ACCOUNTS,
                        onClick = { navigateTo(Screen.ACCOUNTS) },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Outlined.Wallet, contentDescription = "Transactions") },
                        selected = currentRoute == null || currentRoute.startsWith("transactions"),
                        onClick = { navigateToTransactionsHome() },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Outlined.LocalOffer, contentDescription = "Categories") },
                        selected = currentRoute == Screen.CATEGORIES,
                        onClick = { navigateTo(Screen.CATEGORIES) },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Outlined.BarChart, contentDescription = "Analytics") },
                        selected = currentRoute == Screen.ANALYTICS,
                        onClick = { navigateTo(Screen.ANALYTICS) },
                    )
                    NavigationBarItem(
                        icon = {
                            if (authData.userAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = authData.userAvatarUrl,
                                    contentDescription = "Menu",
                                    modifier = Modifier.size(26.dp).clip(CircleShape),
                                )
                            } else {
                                Icon(Icons.Outlined.AccountCircle, contentDescription = "Menu")
                            }
                        },
                        selected = currentRoute == Screen.MENU,
                        onClick = { navigateTo(Screen.MENU) },
                    )
                }
            },
            floatingActionButton = {
                // Explicit colors — the default `containerColor` is `colorScheme.primaryContainer`,
                // which this app reassigned to a neutral gray for chip/segmented-button selection
                // highlights, so the FAB inherited gray instead of the brand orange. Same fix Tasks
                // Android just applied to its own FAB, same root cause.
                when {
                    currentRoute == null || currentRoute.startsWith("transactions") ->
                        FloatingActionButton(
                            onClick = { openCreateTransaction() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
                        }
                    currentRoute == Screen.ACCOUNTS ->
                        FloatingActionButton(
                            onClick = { openCreateAccount() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add account")
                        }
                    currentRoute == Screen.CATEGORIES ->
                        FloatingActionButton(
                            onClick = { openCreateCategory() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add category")
                        }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.TRANSACTIONS,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(
                    route = Screen.TRANSACTIONS,
                    arguments = listOf(
                        navArgument(Screen.ARG_ACCOUNT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(Screen.ARG_CATEGORY_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(Screen.ARG_DATE_FROM) { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument(Screen.ARG_DATE_TO) { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    TransactionsScreen(onTransactionClick = { openEditTransaction(it) })
                }
                composable(Screen.ACCOUNTS) {
                    AccountsScreen(
                        onAccountClick = { account: Account ->
                            navigateToTransactionsFiltered(Screen.transactionsFilteredByAccount(account.id))
                        },
                        onEditAccount = { openEditAccount(it) },
                    )
                }
                composable(Screen.CATEGORIES) {
                    CategoriesScreen(
                        onCategoryClick = { category: Category ->
                            navigateToTransactionsFiltered(Screen.transactionsFilteredByCategory(category.id))
                        },
                        onEditCategory = { openEditCategory(it) },
                    )
                }
                composable(Screen.ANALYTICS) {
                    AnalyticsScreen(
                        onCategoryDrillDown = { categoryId, dateFrom, dateTo ->
                            navigateToTransactionsFiltered(Screen.transactionsFilteredByCategoryAndRange(categoryId, dateFrom, dateTo))
                        },
                    )
                }
                composable(Screen.MENU) {
                    MenuScreen(
                        userName = authData.userName,
                        userEmail = authData.userEmail,
                        userAvatarUrl = authData.userAvatarUrl,
                        onSettings = { pushOverlay(Overlay.Settings) },
                        onHelp = { pushOverlay(Overlay.Help) },
                        onFeedback = { pushOverlay(Overlay.Feedback) },
                        onAbout = { pushOverlay(Overlay.About) },
                        onSignOut = onSignOut,
                    )
                }
            }
        }

        when (currentOverlay) {
            Overlay.Settings -> SettingsScreen(onNavigateBack = ::popOverlay)
            Overlay.Help -> HelpScreen(onNavigateBack = ::popOverlay)
            Overlay.Feedback -> FeedbackScreen(onNavigateBack = ::popOverlay)
            Overlay.About -> AboutScreen(onNavigateBack = ::popOverlay)
            null -> Unit
        }
    }

    if (showTransactionForm) {
        TransactionFormSheet(
            transaction = editingTransaction,
            copyFrom = copyFromTransaction,
            onDismiss = { showTransactionForm = false; editingTransaction = null; copyFromTransaction = null },
            onCopy = { openCopyTransaction(it) },
        )
    }

    if (showAccountForm) {
        AccountFormSheet(
            account = editingAccount,
            onDismiss = { showAccountForm = false; editingAccount = null },
        )
    }

    if (showCategoryForm) {
        CategoryFormSheet(
            category = editingCategory,
            onDismiss = { showCategoryForm = false; editingCategory = null },
        )
    }

    if (showFilterMenu && transactionsViewModel != null) {
        FilterMenu(
            filterState = filterState,
            search = filterSearch,
            accounts = filterAccounts,
            categories = filterCategories,
            onToggleType = transactionsViewModel::toggleType,
            onToggleDebt = transactionsViewModel::toggleDebtType,
            onToggleAccount = transactionsViewModel::toggleAccount,
            onToggleCategory = transactionsViewModel::toggleCategory,
            onDateFromChange = transactionsViewModel::setDateFrom,
            onDateToChange = transactionsViewModel::setDateTo,
            onAmountMinChange = transactionsViewModel::setAmountMin,
            onAmountMaxChange = transactionsViewModel::setAmountMax,
            onSearchChange = transactionsViewModel::setSearch,
            onClearAll = transactionsViewModel::clearFilter,
            onDismiss = { showFilterMenu = false },
        )
    }
    }
}
