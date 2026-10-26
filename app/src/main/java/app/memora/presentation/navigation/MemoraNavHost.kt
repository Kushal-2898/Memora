package app.memora.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.memora.di.AppContainer
import app.memora.presentation.capture.CaptureScreen
import app.memora.presentation.scanner.ScannerScreen
import app.memora.presentation.history.HistoryRoute
import app.memora.presentation.history.HistoryViewModel
import app.memora.presentation.home.HomeRoute
import app.memora.presentation.home.HomeViewModel
import app.memora.presentation.settings.SettingsScreen

@Composable
fun MemoraNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sharedImageUri: String? = null,
    onSharedImageConsumed: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    androidx.compose.runtime.LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            app.memora.platform.logging.AppLog.recordScreen(currentRoute)
        }
    }

    androidx.compose.runtime.LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null && currentRoute != Screen.Scanner.route) {
            navController.navigate(Screen.Scanner.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    val isBottomBarVisible = currentRoute != null && currentRoute != Screen.Onboarding.route
    val startDestination = remember {
        if (appContainer.onboardingPreferences.hasCompletedOnboarding()) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomBarVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = app.memora.ui.theme.BorderLight,
                        )
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("main_navigation_bar"),
                        containerColor = app.memora.ui.theme.CardWhite,
                        contentColor = app.memora.ui.theme.PrimaryNavy,
                    ) {
                        Screen.bottomNavItems.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = stringResource(screen.titleRes),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(screen.titleRes),
                                        style = app.memora.ui.theme.MemoraTypography.label,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = app.memora.ui.theme.AccentIndigo,
                                    selectedTextColor = app.memora.ui.theme.AccentIndigo,
                                    indicatorColor = app.memora.ui.theme.AccentIndigo.copy(alpha = 0.12f),
                                    unselectedIconColor = app.memora.ui.theme.TextCaption,
                                    unselectedTextColor = app.memora.ui.theme.TextCaption,
                                ),
                                modifier = Modifier.testTag(screen.testTag),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.98f, animationSpec = tween(220)) },
            popEnterTransition = { fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)) },
            popExitTransition = { fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 0.98f, animationSpec = tween(220)) }
        ) {
            composable(Screen.Onboarding.route) {
                app.memora.presentation.onboarding.OnboardingScreen(
                    onComplete = {
                        appContainer.onboardingPreferences.setOnboardingCompleted(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) {
                                inclusive = true
                            }
                        }
                    },
                    onboardingPreferences = appContainer.onboardingPreferences,
                )
            }

            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        observeMemoriesUseCase = appContainer.observeMemoriesUseCase,
                        calculateReminderStatusUseCase = appContainer.calculateReminderStatusUseCase,
                        completeReminderUseCase = appContainer.completeReminderUseCase,
                        uncompleteReminderUseCase = appContainer.uncompleteReminderUseCase,
                        deleteMemoryUseCase = appContainer.deleteMemoryUseCase,
                        getMemoryUseCase = appContainer.getMemoryUseCase,
                        updateMemoryUseCase = appContainer.updateMemoryUseCase,
                        notificationScheduler = appContainer.notificationScheduler,
                        snoozeReminderUseCase = appContainer.snoozeReminderUseCase,
                    )
                )
                HomeRoute(
                    viewModel = homeViewModel,
                    onNavigateToScanner = {
                        navController.navigate(Screen.Scanner.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(
                route = Screen.Scanner.route,
                enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
            ) {
                val scannerViewModel: app.memora.presentation.capture.CaptureViewModel = viewModel(
                    factory = app.memora.presentation.capture.CaptureViewModel.Factory(
                        processCapturedImageUseCase = appContainer.processCapturedImageUseCase,
                        imageStorage = appContainer.imageStorage,
                        createMemoryUseCase = appContainer.createMemoryUseCase,
                        deleteMemoryUseCase = appContainer.deleteMemoryUseCase,
                        notificationScheduler = appContainer.notificationScheduler,
                        extractLocallyUseCase = appContainer.extractLocallyUseCase,
                        refineWithGeminiUseCase = appContainer.refineWithGeminiUseCase,
                        crashReporter = appContainer.crashReporter,
                        analyticsLogger = appContainer.analyticsLogger,
                        extractCapturedImageUseCase = appContainer.extractCapturedImageUseCase,
                        canScanUseCase = appContainer.canScanUseCase,
                    )
                )
                androidx.compose.runtime.LaunchedEffect(sharedImageUri) {
                    if (sharedImageUri != null) {
                        scannerViewModel.onImageSelected(sharedImageUri, app.memora.domain.model.CaptureSource.GALLERY)
                        onSharedImageConsumed()
                    }
                }

                ScannerScreen(
                    viewModel = scannerViewModel,
                    cameraManager = appContainer.cameraXManager,
                    imageStorage = appContainer.imageStorage,
                    onNavigateBack = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            // Fallback backward-compatible route for legacy "capture"
            composable(
                route = "capture",
                enterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                exitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
                popExitTransition = { slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) }
            ) {
                val captureViewModel: app.memora.presentation.capture.CaptureViewModel = viewModel(
                    factory = app.memora.presentation.capture.CaptureViewModel.Factory(
                        processCapturedImageUseCase = appContainer.processCapturedImageUseCase,
                        imageStorage = appContainer.imageStorage,
                        createMemoryUseCase = appContainer.createMemoryUseCase,
                        deleteMemoryUseCase = appContainer.deleteMemoryUseCase,
                        notificationScheduler = appContainer.notificationScheduler,
                        extractLocallyUseCase = appContainer.extractLocallyUseCase,
                        refineWithGeminiUseCase = appContainer.refineWithGeminiUseCase,
                        crashReporter = appContainer.crashReporter,
                        analyticsLogger = appContainer.analyticsLogger,
                        extractCapturedImageUseCase = appContainer.extractCapturedImageUseCase,
                    )
                )
                ScannerScreen(
                    viewModel = captureViewModel,
                    cameraManager = appContainer.cameraXManager,
                    imageStorage = appContainer.imageStorage,
                    onNavigateBack = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }


            composable(Screen.History.route) {
                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(
                        observeMemoriesUseCase = appContainer.observeMemoriesUseCase,
                        uncompleteReminderUseCase = appContainer.uncompleteReminderUseCase,
                        completeReminderUseCase = appContainer.completeReminderUseCase,
                        getMemoryUseCase = appContainer.getMemoryUseCase,
                        notificationScheduler = appContainer.notificationScheduler,
                    )
                )
                HistoryRoute(viewModel = historyViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onClearAllData = {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            appContainer.database.clearAllTables()
                            app.memora.platform.logging.AppLog.clear()
                        }
                    }
                )
            }
        }
    }
}
