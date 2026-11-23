package app.memora.presentation.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.memora.di.AppContainer
import app.memora.presentation.capture.CaptureViewModel
import app.memora.presentation.common.EmailAuthDialog
import app.memora.presentation.common.NotificationPermissionDialog
import app.memora.presentation.history.HistoryRoute
import app.memora.presentation.history.HistoryViewModel
import app.memora.presentation.home.HomeRoute
import app.memora.presentation.home.HomeViewModel
import app.memora.presentation.home.components.AddChoiceBottomSheet
import app.memora.presentation.home.components.AddMemoryBottomSheet
import app.memora.presentation.profile.ProfileBottomSheet
import app.memora.presentation.scanner.ScannerScreen
import app.memora.presentation.settings.PrivacyPolicyDialog
import app.memora.presentation.settings.SettingsScreen
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MemoraNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sharedImageUri: String? = null,
    onSharedImageConsumed: () -> Unit = {},
    initialMemoryId: String? = null,
    onMemoryIdConsumed: () -> Unit = {},
    openScannerOnLaunch: Boolean = false,
    onOpenScannerConsumed: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentUser by appContainer.authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)

    var notificationRequestStartMs by remember { mutableStateOf(0L) }
    var openSettingsIfSuppressed by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val elapsedMs = android.os.SystemClock.elapsedRealtime() - notificationRequestStartMs
        appContainer.onboardingPreferences.setPromptedNotificationPermission(true)
        app.memora.platform.logging.AppLog.recordUserAction(
            "Permissions",
            "Notification Permission Result: granted=$isGranted (elapsed=${elapsedMs}ms)"
        )
        if (!isGranted && openSettingsIfSuppressed && elapsedMs in 0L..350L) {
            app.memora.platform.logging.AppLog.recordUserAction(
                "Permissions",
                "OS suppressed notification permission popup -> opening App Notification Settings"
            )
            runCatching {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
        openSettingsIfSuppressed = false
    }

    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showAddChoiceSheet by rememberSaveable { mutableStateOf(false) }
    var showManualEntrySheet by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showPolicyDialog by rememberSaveable { mutableStateOf(false) }
    var showProfileEmailDialog by rememberSaveable { mutableStateOf(false) }
    var emailAuthError by rememberSaveable { mutableStateOf<String?>(null) }
    var emailAuthLoading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialMemoryId) {
        if (!initialMemoryId.isNullOrBlank()) {
            navController.navigate("home") {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            onMemoryIdConsumed()
        }
    }

    LaunchedEffect(openScannerOnLaunch) {
        if (openScannerOnLaunch && appContainer.onboardingPreferences.hasCompletedOnboarding()) {
            navController.navigate(Screen.Scanner.route) {
                launchSingleTop = true
            }
            onOpenScannerConsumed()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            app.memora.platform.logging.AppLog.recordScreen(currentRoute)
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            appContainer.firestoreSyncManager.sync()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Home.route &&
            !appContainer.onboardingPreferences.hasPromptedNotificationPermission() &&
            appContainer.onboardingPreferences.hasCompletedOnboarding()
        ) {
            val isGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                app.memora.platform.logging.AppLog.recordUserAction(
                    "Permissions",
                    "Notification Permission Dialog shown on home"
                )
                showNotificationPermissionDialog = true
            } else {
                appContainer.onboardingPreferences.setPromptedNotificationPermission(true)
            }
        }
    }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null && currentRoute != Screen.Scanner.route) {
            navController.navigate(Screen.Scanner.route) {
                launchSingleTop = true
            }
        }
    }

    val isBottomBarVisible = currentRoute == Screen.Home.route || currentRoute == Screen.History.route
    val startDestination = remember {
        if (appContainer.onboardingPreferences.hasCompletedOnboarding()) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = app.memora.ui.theme.SurfaceCool,
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
                                selected = if (screen == Screen.Add) false else selected,
                                onClick = {
                                    if (screen == Screen.Add) {
                                        showAddChoiceSheet = true
                                    } else if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = currentRoute == Screen.Home.route || currentRoute == Screen.History.route
                                            }
                                            launchSingleTop = true
                                            restoreState = screen != Screen.Home
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
            enterTransition = { fadeIn(animationSpec = tween(140)) },
            exitTransition = { fadeOut(animationSpec = tween(100)) },
            popEnterTransition = { fadeIn(animationSpec = tween(140)) },
            popExitTransition = { fadeOut(animationSpec = tween(100)) },
        ) {
            composable(Screen.Onboarding.route) {
                app.memora.presentation.onboarding.OnboardingScreen(
                    onComplete = {
                        appContainer.onboardingPreferences.setOnboardingCompleted(true)
                        if (appContainer.onboardingPreferences.isScanNudgeEnabled()) {
                            app.memora.platform.notification.ScanNudgeScheduler.scheduleInitial(context, isVaultEmpty = true)
                        }
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) {
                                inclusive = true
                            }
                        }
                    },
                    onAddFirstItem = {
                        appContainer.onboardingPreferences.setOnboardingCompleted(true)
                        if (appContainer.onboardingPreferences.isScanNudgeEnabled()) {
                            app.memora.platform.notification.ScanNudgeScheduler.scheduleInitial(context, isVaultEmpty = true)
                        }
                        app.memora.platform.logging.AppLog.recordUserAction(
                            "Onboarding",
                            "Completed with Add First Item -> opening Add Choice sheet"
                        )
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) {
                                inclusive = true
                            }
                        }
                        showAddChoiceSheet = true
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!isGranted) {
                                appContainer.onboardingPreferences.setPromptedNotificationPermission(true)
                                openSettingsIfSuppressed = false
                                notificationRequestStartMs = android.os.SystemClock.elapsedRealtime()
                                app.memora.platform.logging.AppLog.recordUserAction(
                                    "Permissions",
                                    "Notification Permission Requested (source=onboarding_screen_5)"
                                )
                                runCatching {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    },
                    onboardingPreferences = appContainer.onboardingPreferences,
                    authRepository = appContainer.authRepository,
                    googleAuthClient = appContainer.googleAuthClient,
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
                    currentUser = currentUser,
                    onProfileClick = {
                        showProfileSheet = true
                    },
                    onNavigateToScanner = {
                        navController.navigate(Screen.Scanner.route) {
                            launchSingleTop = true
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
                val scannerViewModel: CaptureViewModel = viewModel(
                    factory = CaptureViewModel.Factory(
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
                        onboardingPreferences = appContainer.onboardingPreferences,
                        scanEvidenceDao = appContainer.database.scanEvidenceDao(),
                        onPushScanEvidence = { evidence ->
                            appContainer.firestoreSyncManager.pushScanEvidence(evidence)
                        },
                        onSyncScanQuota = {
                            appContainer.firestoreSyncManager.syncMonthlyScanQuota()
                        },
                    )
                )
                LaunchedEffect(sharedImageUri) {
                    if (sharedImageUri != null) {
                        scannerViewModel.onImageSelected(sharedImageUri, app.memora.domain.model.CaptureSource.GALLERY)
                        onSharedImageConsumed()
                    }
                }

                ScannerScreen(
                    viewModel = scannerViewModel,
                    cameraManager = appContainer.cameraXManager,
                    imageStorage = appContainer.imageStorage,
                    billingGateway = appContainer.billingGateway,
                    onNavigateBack = {
                        if (!navController.popBackStack(Screen.Scanner.route, inclusive = true)) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id)
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
                HistoryRoute(
                    viewModel = historyViewModel,
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

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onboardingPreferences = appContainer.onboardingPreferences,
                    billingGateway = appContainer.billingGateway,
                    authRepository = appContainer.authRepository,
                    googleAuthClient = appContainer.googleAuthClient,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onClearAllData = {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            app.memora.domain.usecase.ClearAllUserDataUseCase(
                                reminderRepository = appContainer.reminderRepository,
                                notificationScheduler = appContainer.notificationScheduler,
                                onClearDatabaseAndFiles = {
                                    appContainer.database.clearAllTables()
                                    appContainer.onboardingPreferences.clearAllUserData()
                                    runCatching {
                                        java.io.File(context.filesDir, "captures").deleteRecursively()
                                    }
                                    app.memora.platform.logging.AppLog.clear()
                                }
                            ).invoke()
                        }
                    },
                    onDeleteAccountCloudData = {
                        appContainer.firestoreSyncManager.deleteAllRemoteData()
                    }
                )
            }
        }
    }

    if (showNotificationPermissionDialog) {
        NotificationPermissionDialog(
            onAllow = {
                appContainer.onboardingPreferences.setPromptedNotificationPermission(true)
                showNotificationPermissionDialog = false
                app.memora.platform.logging.AppLog.recordUserAction(
                    "Permissions",
                    "Notification Permission Requested (source=home_dialog)"
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    openSettingsIfSuppressed = true
                    notificationRequestStartMs = android.os.SystemClock.elapsedRealtime()
                    runCatching {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
            onDismiss = {
                appContainer.onboardingPreferences.setPromptedNotificationPermission(true)
                showNotificationPermissionDialog = false
                app.memora.platform.logging.AppLog.recordUserAction(
                    "Permissions",
                    "Notification Permission Dialog dismissed (Maybe Later)"
                )
            },
        )
    }

    if (showProfileSheet) {
        ProfileBottomSheet(
            currentUser = currentUser,
            onDismiss = { showProfileSheet = false },
            onNavigateToSettings = {
                showProfileSheet = false
                navController.navigate(Screen.Settings.route) {
                    launchSingleTop = true
                }
            },
            onSignInWithGoogle = {
                coroutineScope.launch {
                    val result = appContainer.googleAuthClient.signIn(context)
                    val account = result.getOrNull()
                    if (account != null) {
                        val authResult = appContainer.authRepository.signInWithGoogle(
                            idToken = account.idToken,
                            displayName = account.displayName,
                            email = account.email,
                            photoUrl = account.photoUrl,
                        )
                        if (authResult.isSuccess) {
                            showProfileSheet = false
                            android.widget.Toast.makeText(context, "Signed in as ${account.email}", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val error = authResult.exceptionOrNull()?.localizedMessage ?: "Failed to sign in"
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val ex = result.exceptionOrNull()
                        if (ex !is kotlinx.coroutines.CancellationException) {
                            val error = ex?.localizedMessage ?: "Google sign in unavailable"
                            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onSignInWithEmail = {
                showProfileSheet = false
                emailAuthError = null
                showProfileEmailDialog = true
            },
            onSignOut = {
                showProfileSheet = false
                coroutineScope.launch {
                    appContainer.authRepository.signOut()
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
            onShowPrivacyPolicy = {
                showPolicyDialog = true
            },
        )
    }

    if (showProfileEmailDialog) {
        EmailAuthDialog(
            onDismissRequest = {
                showProfileEmailDialog = false
                emailAuthError = null
            },
            onSignIn = { email, password ->
                coroutineScope.launch {
                    emailAuthLoading = true
                    emailAuthError = null
                    val result = appContainer.authRepository.signInWithEmailAndPassword(email, password)
                    emailAuthLoading = false
                    if (result.isSuccess) {
                        showProfileEmailDialog = false
                    } else {
                        emailAuthError = result.exceptionOrNull()?.localizedMessage ?: "Sign in failed"
                    }
                }
            },
            onSignUp = { email, password ->
                coroutineScope.launch {
                    emailAuthLoading = true
                    emailAuthError = null
                    val result = appContainer.authRepository.signUpWithEmailAndPassword(email, password)
                    emailAuthLoading = false
                    if (result.isSuccess) {
                        showProfileEmailDialog = false
                    } else {
                        emailAuthError = result.exceptionOrNull()?.localizedMessage ?: "Registration failed"
                    }
                }
            },
            isLoading = emailAuthLoading,
            errorMessage = emailAuthError,
        )
    }

    if (showAddChoiceSheet) {
        AddChoiceBottomSheet(
            onScanClick = {
                showAddChoiceSheet = false
                navController.navigate(Screen.Scanner.route) {
                    launchSingleTop = true
                }
            },
            onManualClick = {
                showAddChoiceSheet = false
                showManualEntrySheet = true
            },
            onDismiss = {
                showAddChoiceSheet = false
            },
        )
    }

    if (showManualEntrySheet) {
        AddMemoryBottomSheet(
            onSave = { title, eventType, eventDate, remindDaysBefore, reminderTime, customCategory ->
                coroutineScope.launch {
                    val memoryId = java.util.UUID.randomUUID().toString()
                    val now = java.time.Instant.now()
                    val memory = app.memora.domain.model.Memory(
                        id = memoryId,
                        title = title,
                        description = customCategory?.takeIf { it.isNotBlank() },
                        eventType = eventType,
                        eventDate = eventDate,
                        timeZoneId = java.time.ZoneId.systemDefault().id,
                        createdAt = now,
                        updatedAt = now,
                    )
                    val reminder = app.memora.domain.model.Reminder(
                        id = java.util.UUID.randomUUID().toString(),
                        memoryId = memoryId,
                        reminderStartDate = eventDate.minusDays(remindDaysBefore.toLong()),
                        reminderTime = reminderTime,
                        createdAt = now,
                        updatedAt = now,
                    )
                    appContainer.createMemoryUseCase(memory, reminder).onSuccess {
                        appContainer.notificationScheduler.scheduleReminder(
                            memoryId = memoryId,
                            reminderId = reminder.id,
                            title = title,
                            reminderStartDate = reminder.reminderStartDate,
                            eventDate = eventDate,
                            timeZoneId = memory.timeZoneId,
                            reminderTime = reminderTime ?: appContainer.onboardingPreferences.getDefaultReminderTime(),
                        )
                    }
                    showManualEntrySheet = false
                }
            },
            onDismiss = {
                showManualEntrySheet = false
            },
            preferences = appContainer.onboardingPreferences,
        )
    }

    if (showPolicyDialog) {
        PrivacyPolicyDialog(
            onDismissRequest = { showPolicyDialog = false }
        )
    }
}
