package com.sowerrrt.dayloom

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sowerrrt.dayloom.core.designsystem.DayloomAnimatedBackground
import com.sowerrrt.dayloom.core.designsystem.DayloomButton
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomLogo
import com.sowerrrt.dayloom.core.designsystem.DayloomMotion
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.feature.habits.HabitsScreen
import com.sowerrrt.dayloom.feature.home.HomeScreen
import com.sowerrrt.dayloom.feature.lists.ListsScreen
import com.sowerrrt.dayloom.feature.planner.PlannerScreen
import com.sowerrrt.dayloom.feature.settings.SettingsScreen
import com.sowerrrt.dayloom.feature.vault.VaultScreen
import com.sowerrrt.dayloom.feature.wishlist.WishlistScreen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DayloomApp(
    rootViewModel: RootViewModel,
    updateViewModel: UpdateViewModel,
) {
    val rootState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val settings = rootState.settings
    LaunchedEffect(settings?.appLanguage) {
        settings?.appLanguage?.let(::applyAppLanguage)
    }
    DayloomTheme(
        themeMode = settings?.themeMode ?: ThemeMode.SYSTEM,
        accentPalette = settings?.accentPalette ?: AccentPalette.VIOLET,
    ) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                DayloomAnimatedBackground()
                if (settings == null) {
                    StartupScreen()
                } else if (!rootState.isAppUnlocked) {
                    AppLockScreen(rootState, rootViewModel)
                } else {
                    DayloomShell(settings, updateState, updateViewModel)
                }
            }
        }
    }
}

@Composable
private fun AppLockScreen(
    state: RootUiState,
    viewModel: RootViewModel,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val unlockSuccess by rememberUpdatedState(viewModel::authenticationSucceeded)
    val authFailed by rememberUpdatedState(viewModel::authenticationFailed)
    val authCancelled by rememberUpdatedState(viewModel::authenticationCancelled)
    val prompt =
        remember(activity) {
            activity?.let {
                BiometricPrompt(
                    it,
                    ContextCompat.getMainExecutor(it),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                            unlockSuccess()

                        override fun onAuthenticationFailed() = authFailed()

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (
                                errorCode == BiometricPrompt.ERROR_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                authCancelled()
                            } else {
                                authFailed()
                            }
                        }
                    },
                )
            }
        }
    val authTitle = stringResource(R.string.app_lock_auth_title)
    val authSubtitle = stringResource(R.string.app_lock_auth_subtitle)
    val authCancel = stringResource(R.string.app_lock_cancel)

    LaunchedEffect(Unit) { viewModel.requestAuthentication() }
    LaunchedEffect(state.authenticationRequest) {
        if (state.authenticationRequest == 0L) return@LaunchedEffect
        val authenticators = supportedAppAuthenticators()
        if (
            activity == null ||
            prompt == null ||
            BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            viewModel.authenticationUnavailable()
            return@LaunchedEffect
        }
        val builder =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(authTitle)
                .setSubtitle(authSubtitle)
                .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) builder.setNegativeButtonText(authCancel)
        prompt.authenticate(builder.build())
    }

    AppLockedContent(
        error = state.lockError,
        onUnlock = viewModel::requestAuthentication,
        onDisableUnavailableLock = viewModel::disableUnavailableLock,
    )
}

@Composable
internal fun AppLockedContent(
    error: AppLockError?,
    onUnlock: () -> Unit,
    onDisableUnavailableLock: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(DayloomSpacing.lg).testTag("app_locked"),
        contentAlignment = Alignment.Center,
    ) {
        DayloomCard(Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
            ) {
                DayloomLogo(Modifier.size(72.dp))
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.app_lock_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.app_lock_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Text(
                        stringResource(
                            if (it == AppLockError.UNAVAILABLE) {
                                R.string.app_lock_unavailable
                            } else {
                                R.string.app_lock_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("app_lock_error"),
                    )
                }
                DayloomButton(
                    text = stringResource(R.string.app_lock_unlock),
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth().testTag("unlock_app"),
                )
                if (error == AppLockError.UNAVAILABLE) {
                    OutlinedButton(
                        onClick = onDisableUnavailableLock,
                        modifier = Modifier.fillMaxWidth().testTag("disable_app_lock"),
                    ) {
                        Text(stringResource(R.string.app_lock_disable))
                    }
                }
                Text(
                    stringResource(R.string.app_lock_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun supportedAppAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
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
    val bottomDestinations = primaryDestinations(settings.bottomSections)
    val destinationOrder = bottomDestinations.map(NavItem::route)
    val selectedBottomIndex =
        bottomDestinations
            .indexOfFirst { it.matches(currentRoute ?: startRoute) }
            .coerceAtLeast(0)
    val indicatorHead = remember(destinationOrder) { Animatable(selectedBottomIndex.toFloat()) }
    val indicatorTail = remember(destinationOrder) { Animatable(selectedBottomIndex.toFloat()) }
    val upToDateMessage = stringResource(R.string.update_up_to_date)
    val unavailableMessage = stringResource(R.string.update_unavailable)

    LaunchedEffect(selectedBottomIndex, destinationOrder) {
        coroutineScope {
            launch {
                indicatorHead.animateTo(
                    targetValue = selectedBottomIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
                )
            }
            launch {
                indicatorTail.animateTo(
                    targetValue = selectedBottomIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 105f),
                )
            }
        }
    }

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
        containerColor = Color.Transparent,
    ) { outerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(outerPadding)) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    DayloomNavigationRail(currentRoute, navController, settings.bottomSections)
                    DayloomNavHost(
                        navController = navController,
                        startRoute = startRoute,
                        homeSections = settings.homeSections,
                        updateViewModel = updateViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        DayloomBottomBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            destinations = bottomDestinations,
                            indicatorHead = indicatorHead,
                            indicatorTail = indicatorTail,
                        )
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = Color.Transparent,
                ) { innerPadding ->
                    DayloomNavHost(
                        navController = navController,
                        startRoute = startRoute,
                        homeSections = settings.homeSections,
                        updateViewModel = updateViewModel,
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
}

@Composable
internal fun UpdateAvailableDialog(
    update: com.sowerrrt.dayloom.core.updates.UpdateInfo,
    onOpenRelease: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                text = stringResource(R.string.update_available_title, update.version.toString()),
                modifier = Modifier.testTag("update_dialog_title"),
            )
        },
        text = {
            Text(
                text = update.notes.ifBlank { stringResource(R.string.update_available_no_notes) },
                modifier = Modifier.testTag("update_dialog_notes"),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenRelease, modifier = Modifier.testTag("update_dialog_open")) {
                Text(stringResource(R.string.update_open_release))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("update_dialog_later")) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}

@Composable
private fun DayloomNavHost(
    navController: NavHostController,
    startRoute: String,
    homeSections: List<HomeSection>,
    updateViewModel: UpdateViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
        enterTransition = {
            fadeIn(tween(DayloomMotion.STANDARD_MILLIS, delayMillis = 40)) +
                slideInHorizontally(tween(DayloomMotion.EMPHASIZED_MILLIS)) { it / 12 } +
                scaleIn(tween(DayloomMotion.EMPHASIZED_MILLIS), initialScale = 0.992f)
        },
        exitTransition = {
            fadeOut(tween(DayloomMotion.QUICK_MILLIS)) +
                slideOutHorizontally(tween(DayloomMotion.STANDARD_MILLIS)) { -it / 28 }
        },
        popEnterTransition = {
            fadeIn(tween(DayloomMotion.STANDARD_MILLIS, delayMillis = 30)) +
                slideInHorizontally(tween(DayloomMotion.EMPHASIZED_MILLIS)) { -it / 12 }
        },
        popExitTransition = {
            fadeOut(tween(DayloomMotion.QUICK_MILLIS)) +
                slideOutHorizontally(tween(DayloomMotion.STANDARD_MILLIS)) { it / 20 } +
                scaleOut(tween(DayloomMotion.QUICK_MILLIS), targetScale = 0.992f)
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                sections = homeSections,
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
        composable(Routes.WISHLIST) { WishlistScreen(onBack = navController::popBackStack) }
        composable(Routes.VAULT) { VaultScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onCheckUpdates = updateViewModel::checkManually,
                onApplyLanguage = ::applyAppLanguage,
                onBack = navController::popBackStack,
            )
        }
    }
}

internal fun applyAppLanguage(language: AppLanguage) {
    val requested = LocaleListCompat.forLanguageTags(language.languageTags)
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != requested.toLanguageTags()) {
        AppCompatDelegate.setApplicationLocales(requested)
    }
}

internal val AppLanguage.languageTags: String
    get() =
        when (this) {
            AppLanguage.SYSTEM -> ""
            AppLanguage.RUSSIAN -> "ru"
            AppLanguage.ENGLISH -> "en"
        }

@Composable
private fun DayloomBottomBar(
    currentRoute: String?,
    navController: NavHostController,
    destinations: List<NavItem>,
    indicatorHead: Animatable<Float, *>,
    indicatorTail: Animatable<Float, *>,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp),
        ) {
            val indicatorColor = MaterialTheme.colorScheme.primaryContainer
            Canvas(Modifier.fillMaxSize()) {
                val itemWidth = size.width / destinations.size
                val headX = itemWidth * (indicatorHead.value + 0.5f)
                val tailX = itemWidth * (indicatorTail.value + 0.5f)
                val radius = 32.dp.toPx()
                val left = minOf(headX, tailX) - radius
                val right = maxOf(headX, tailX) + radius
                val stretch = ((right - left - radius * 2f) / itemWidth).coerceIn(0f, 1f)
                val verticalInset = 2.dp.toPx() * stretch
                val top = 12.dp.toPx() + verticalInset
                val height = 32.dp.toPx() - verticalInset * 2f
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, height),
                    cornerRadius = CornerRadius(height / 2f),
                )
            }
            Row(Modifier.fillMaxSize()) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.matches(currentRoute),
                        onClick = { navController.navigateSingleTop(destination.route) },
                        icon = { AnimatedNavigationIcon(destination.icon, destination.matches(currentRoute)) },
                        label = { Text(destination.label) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        modifier = Modifier.testTag("primary_nav_${destination.route}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayloomNavigationRail(
    currentRoute: String?,
    navController: NavHostController,
    sections: List<BottomSection>,
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        primaryDestinations(sections).forEach { destination ->
            NavigationRailItem(
                selected = destination.matches(currentRoute),
                onClick = { navController.navigateSingleTop(destination.route) },
                icon = { AnimatedNavigationIcon(destination.icon, destination.matches(currentRoute)) },
                label = { Text(destination.label) },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                modifier = Modifier.testTag("primary_nav_${destination.route}"),
            )
        }
    }
}

@Composable
private fun AnimatedNavigationIcon(
    icon: ImageVector,
    selected: Boolean,
) {
    val scale by
        animateFloatAsState(
            targetValue = if (selected) 1.12f else 1f,
            animationSpec = spring(stiffness = 440f, dampingRatio = 0.68f),
            label = "navigationIconScale",
        )
    Icon(
        icon,
        contentDescription = null,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
private fun primaryDestinations(sections: List<BottomSection>): List<NavItem> =
    sections.distinct().map { section ->
        when (section) {
            BottomSection.HOME -> NavItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Rounded.Home)
            BottomSection.HABITS ->
                NavItem(Routes.HABITS, stringResource(R.string.nav_habits), Icons.Rounded.AutoAwesome)
            BottomSection.PLANNER ->
                NavItem(Routes.PLANNER, stringResource(R.string.nav_plan), Icons.Rounded.CalendarMonth)
            BottomSection.LISTS -> NavItem(Routes.LISTS, stringResource(R.string.nav_lists), Icons.Rounded.Checklist)
            BottomSection.MORE ->
                NavItem(Routes.MORE, stringResource(R.string.nav_more), Icons.Rounded.MoreHoriz, Routes.moreRoutes)
        }
    }

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
            MoreDestination(
                title = stringResource(R.string.more_wishlist),
                description = stringResource(R.string.more_wishlist_description),
                icon = Icons.Rounded.Savings,
                testTag = "more_wishlist",
                onClick = onWishlist,
            ),
            MoreDestination(
                title = stringResource(R.string.more_vault),
                description = stringResource(R.string.more_vault_description),
                icon = Icons.Rounded.Lock,
                testTag = "more_vault",
                onClick = onVault,
            ),
            MoreDestination(
                title = stringResource(R.string.more_settings),
                description = stringResource(R.string.more_settings_description),
                icon = Icons.Rounded.Palette,
                testTag = "more_settings",
                onClick = onSettings,
            ),
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
            items.forEachIndexed { index, item -> MoreDestinationCard(item, index) }
        }
    }
}

@Composable
private fun MoreDestinationCard(
    item: MoreDestination,
    index: Int,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tween(DayloomMotion.STANDARD_MILLIS)) +
                slideInVertically(tween(DayloomMotion.EMPHASIZED_MILLIS)) { it / 4 },
        exit = fadeOut(tween(DayloomMotion.QUICK_MILLIS)) + slideOutVertically { it / 8 },
    ) {
        DayloomCard(
            modifier = Modifier.fillMaxWidth().testTag(item.testTag),
            onClick = item.onClick,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.md),
            ) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DayloomSpacing.xxs)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

private data class MoreDestination(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit,
)
