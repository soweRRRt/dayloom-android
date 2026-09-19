package com.sowerrrt.dayloom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomLogo
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.feature.habits.HabitsScreen
import com.sowerrrt.dayloom.feature.home.HomeScreen
import com.sowerrrt.dayloom.feature.lists.ListsScreen
import com.sowerrrt.dayloom.feature.planner.PlannerScreen
import com.sowerrrt.dayloom.feature.settings.SettingsScreen
import com.sowerrrt.dayloom.feature.vault.VaultScreen
import com.sowerrrt.dayloom.feature.wishlist.WishlistScreen

@Composable
fun DayloomApp(
    rootViewModel: RootViewModel,
    updateViewModel: UpdateViewModel,
) {
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val settings = rootState.settings
    DayloomTheme(
        themeMode = settings?.themeMode ?: ThemeMode.SYSTEM,
        accentPalette = settings?.accentPalette ?: AccentPalette.VIOLET,
    ) {
        Surface(Modifier.fillMaxSize()) {
            if (settings == null) {
                StartupScreen()
            } else {
                DayloomShell(settings, updateState, updateViewModel)
            }
        }
    }
}

@Composable
private fun StartupScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DayloomLogo(Modifier.size(72.dp))
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DayloomShell(
    settings: AppSettings,
    updateState: UpdateUiState,
    updateViewModel: UpdateViewModel,
) {
    val navController = rememberNavController()
    val startRoute = remember { settings.startDestination.route }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val upToDateMessage = stringResource(R.string.update_up_to_date)
    val unavailableMessage = stringResource(R.string.update_unavailable)
    var showVaultNotice by remember { mutableStateOf(false) }

    LaunchedEffect(updateState.feedback) {
        when (updateState.feedback) {
            UpdateFeedback.UP_TO_DATE -> snackbarHostState.showSnackbar(upToDateMessage)
            UpdateFeedback.UNAVAILABLE -> snackbarHostState.showSnackbar(unavailableMessage)
            null -> Unit
        }
        if (updateState.feedback != null) updateViewModel.consumeFeedback()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { outerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(outerPadding)) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    DayloomNavigationRail(currentRoute, navController)
                    DayloomNavHost(
                        navController = navController,
                        startRoute = startRoute,
                        updateViewModel = updateViewModel,
                        onVaultSetup = { showVaultNotice = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Scaffold(
                    bottomBar = { DayloomBottomBar(currentRoute, navController) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    DayloomNavHost(
                        navController = navController,
                        startRoute = startRoute,
                        updateViewModel = updateViewModel,
                        onVaultSetup = { showVaultNotice = true },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    updateState.available?.let { update ->
        val uriHandler = LocalUriHandler.current
        UpdateAvailableDialog(
            update = update,
            onOpenRelease = {
                uriHandler.openUri(update.releasePageUrl)
                updateViewModel.dismissAvailable()
            },
            onLater = updateViewModel::dismissAvailable,
        )
    }

    if (showVaultNotice) {
        AlertDialog(
            onDismissRequest = { showVaultNotice = false },
            title = { Text(stringResource(R.string.vault_notice_title)) },
            text = { Text(stringResource(R.string.vault_notice_body)) },
            confirmButton = {
                TextButton(onClick = { showVaultNotice = false }) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
}

@Composable
internal fun UpdateAvailableDialog(
    update: com.sowerrrt.dayloom.core.updates.UpdateInfo,
    onOpenRelease: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_available_title, update.version.toString())) },
        text = { Text(update.notes.ifBlank { stringResource(R.string.update_available_no_notes) }) },
        confirmButton = {
            TextButton(onClick = onOpenRelease) { Text(stringResource(R.string.update_open_release)) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
        },
    )
}

@Composable
private fun DayloomNavHost(
    navController: NavHostController,
    startRoute: String,
    updateViewModel: UpdateViewModel,
    onVaultSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = startRoute, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenHabits = { navController.navigateSingleTop(Routes.HABITS) },
                onOpenPlanner = { navController.navigateSingleTop(Routes.PLANNER) },
                onOpenLists = { navController.navigateSingleTop(Routes.LISTS) },
                onOpenWishlist = { navController.navigate(Routes.WISHLIST) },
            )
        }
        composable(Routes.HABITS) { HabitsScreen() }
        composable(Routes.PLANNER) { PlannerScreen() }
        composable(Routes.LISTS) { ListsScreen() }
        composable(Routes.MORE) {
            MoreScreen(
                onWishlist = { navController.navigate(Routes.WISHLIST) },
                onVault = { navController.navigate(Routes.VAULT) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.WISHLIST) { WishlistScreen() }
        composable(Routes.VAULT) { VaultScreen(onVaultSetup) }
        composable(Routes.SETTINGS) { SettingsScreen(updateViewModel::checkManually) }
    }
}

@Composable
private fun DayloomBottomBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBar {
        primaryDestinations().forEach { destination ->
            NavigationBarItem(
                selected = destination.matches(currentRoute),
                onClick = { navController.navigateSingleTop(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag("primary_nav_${destination.route}"),
            )
        }
    }
}

@Composable
private fun DayloomNavigationRail(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationRail {
        primaryDestinations().forEach { destination ->
            NavigationRailItem(
                selected = destination.matches(currentRoute),
                onClick = { navController.navigateSingleTop(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier.testTag("primary_nav_${destination.route}"),
            )
        }
    }
}

@Composable
private fun primaryDestinations(): List<NavItem> =
    listOf(
        NavItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Rounded.Home),
        NavItem(Routes.HABITS, stringResource(R.string.nav_habits), Icons.Rounded.AutoAwesome),
        NavItem(Routes.PLANNER, stringResource(R.string.nav_plan), Icons.Rounded.CalendarMonth),
        NavItem(Routes.LISTS, stringResource(R.string.nav_lists), Icons.Rounded.Checklist),
        NavItem(Routes.MORE, stringResource(R.string.nav_more), Icons.Rounded.MoreHoriz, Routes.moreRoutes),
    )

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val relatedRoutes: Set<String> = emptySet(),
) {
    fun matches(currentRoute: String?): Boolean = currentRoute == route || currentRoute in relatedRoutes
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val StartDestination.route: String
    get() =
        when (this) {
            StartDestination.HOME -> Routes.HOME
            StartDestination.HABITS -> Routes.HABITS
            StartDestination.PLANNER -> Routes.PLANNER
            StartDestination.LISTS -> Routes.LISTS
        }

private object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val PLANNER = "planner"
    const val LISTS = "lists"
    const val MORE = "more"
    const val WISHLIST = "wishlist"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    val moreRoutes = setOf(MORE, WISHLIST, VAULT, SETTINGS)
}

@Composable
private fun MoreScreen(
    onWishlist: () -> Unit,
    onVault: () -> Unit,
    onSettings: () -> Unit,
) {
    val items =
        listOf(
            Triple(stringResource(R.string.more_wishlist), Icons.Rounded.Savings, onWishlist),
            Triple(stringResource(R.string.more_vault), Icons.Rounded.Lock, onVault),
            Triple(stringResource(R.string.more_settings), Icons.Rounded.Palette, onSettings),
        )
    Column {
        DayloomTopBar(stringResource(R.string.more_title))
        Column(
            modifier = Modifier.padding(horizontal = DayloomSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
        ) {
            Text(
                stringResource(R.string.more_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            items.forEach { (label, icon, action) ->
                DayloomCard(Modifier.fillMaxWidth().clickable(onClick = action)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
