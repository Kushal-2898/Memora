package app.memora.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.memora.data.local.DefaultOnboardingPreferences
import app.memora.data.local.OnboardingPreferences
import app.memora.presentation.navigation.Screen
import app.memora.presentation.onboarding.screens.OnboardingAccountPage
import app.memora.presentation.onboarding.screens.OnboardingCompletionPage
import app.memora.presentation.onboarding.screens.OnboardingRemindersPage
import app.memora.presentation.onboarding.screens.OnboardingScanPage
import app.memora.presentation.onboarding.screens.OnboardingStayOnTopPage
import app.memora.presentation.onboarding.screens.OnboardingTrackPage
import app.memora.presentation.onboarding.screens.OnboardingWelcomePage
import app.memora.presentation.settings.PrivacyPolicyDialog
import kotlinx.coroutines.launch

/**
 * OnboardingScreen hosts the 6-screen onboarding experience:
 * 1. Screen 1: Welcome & Value Proposition (Organic waves, 3D Origami M)
 * 2. Screen 2: Scan Product Labels (Hero video player, 3 feature rows)
 * 3. Screen 3: Set Reminders (Green Oats pouch card, Expiry date, Advance reminders, Repeat)
 * 4. Screen 4: Track Everything (2×4 category grid with vector silhouette icons)
 * 5. Screen 5: Stay On Top (Hero lock-screen notification preview, 3 benefit rows)
 * 6. Screen 6: Completion ("You're all set!", 3D logo tile, 4 checklist rows)
 * 7. Screen 7: Account Creation ("Create your account", Google/Email auth, guest continuation)
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onboardingPreferences: OnboardingPreferences? = null,
    onNavigateToRoute: ((String) -> Unit)? = null,
    onAddFirstItem: (() -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    authRepository: app.memora.domain.repository.AuthRepository? = null,
    googleAuthClient: app.memora.platform.auth.GoogleAuthClient? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 7 })
    val coroutineScope = rememberCoroutineScope()
    var isCompleting by remember { mutableStateOf(false) }
    var wantsAddFirstItem by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showEmailAuthDialog by remember { mutableStateOf(false) }
    var isEmailAuthLoading by remember { mutableStateOf(false) }
    var emailAuthError by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen ${pagerState.currentPage + 1} viewed")
    }

    // Categories state
    var selectedCategories by remember {
        mutableStateOf(
            onboardingPreferences?.getSelectedCategories() ?: DefaultOnboardingPreferences.defaultCategories
        )
    }

    val handleComplete: (String?) -> Unit = { targetRoute ->
        if (!isCompleting) {
            isCompleting = true
            onboardingPreferences?.setSelectedCategories(selectedCategories)
            onboardingPreferences?.setOnboardingCompleted(true)
            if (wantsAddFirstItem && onAddFirstItem != null) {
                onAddFirstItem()
            } else if (targetRoute != null && onNavigateToRoute != null) {
                onNavigateToRoute(targetRoute)
            } else {
                onComplete()
            }
        }
    }

    // Intercept system back gestures when beyond Screen 1 to retreat one screen.
    BackHandler(enabled = pagerState.currentPage > 0) {
        if (!pagerState.isScrollInProgress) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            when (pageIndex) {
                0 -> {
                    // Screen 1: Welcome Page
                    OnboardingWelcomePage(
                        onGetStarted = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onSkip = {
                            wantsAddFirstItem = false
                            handleComplete(null)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                1 -> {
                    // Screen 2: Scan Product Labels (with live hero video)
                    OnboardingScanPage(
                        onNext = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        },
                        onSkip = {
                            wantsAddFirstItem = false
                            handleComplete(null)
                        },
                        isActive = pagerState.currentPage == 1,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                2 -> {
                    // Screen 3: Set Reminders
                    OnboardingRemindersPage(
                        onNext = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(3)
                            }
                        },
                        onSkip = {
                            wantsAddFirstItem = false
                            handleComplete(null)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                3 -> {
                    // Screen 4: Track Everything
                    OnboardingTrackPage(
                        onNext = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(4)
                            }
                        },
                        onSkip = {
                            wantsAddFirstItem = false
                            handleComplete(null)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                4 -> {
                    // Screen 5: Stay On Top
                    OnboardingStayOnTopPage(
                        onNext = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 5: Next clicked")
                            onRequestNotificationPermission?.invoke()
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(5)
                            }
                        },
                        onSkip = {
                            wantsAddFirstItem = false
                            handleComplete(null)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                5 -> {
                    // Screen 6: Completion ("You're all set!")
                    OnboardingCompletionPage(
                        onAddFirstItem = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 6: Add first memory clicked")
                            wantsAddFirstItem = true
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(6)
                            }
                        },
                        onMaybeLater = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 6: Maybe later clicked")
                            wantsAddFirstItem = false
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(6)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                6 -> {
                    // Screen 7: Account Creation ("Create your account")
                    OnboardingAccountPage(
                        onContinueWithGoogle = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 7: Continue with Google clicked")
                            coroutineScope.launch {
                                if (googleAuthClient != null && authRepository != null) {
                                    val result = googleAuthClient.signIn(context)
                                    val account = result.getOrNull()
                                    if (account != null) {
                                        val authResult = authRepository.signInWithGoogle(
                                            idToken = account.idToken,
                                            displayName = account.displayName,
                                            email = account.email,
                                            photoUrl = account.photoUrl,
                                        )
                                        if (authResult.isSuccess) {
                                            handleComplete(null)
                                        } else {
                                            val err = authResult.exceptionOrNull()?.localizedMessage ?: "Failed to sign in with Google"
                                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val ex = result.exceptionOrNull()
                                        if (ex !is kotlinx.coroutines.CancellationException) {
                                            val err = ex?.localizedMessage ?: "Google sign in failed"
                                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    authRepository?.signInWithGoogle(
                                        idToken = "google_auth_token",
                                        displayName = "Google User",
                                        email = "user@gmail.com",
                                        photoUrl = null,
                                    )
                                    handleComplete(null)
                                }
                            }
                        },
                        onContinueWithEmail = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 7: Continue with Email clicked")
                            emailAuthError = null
                            showEmailAuthDialog = true
                        },
                        onContinue = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 7: Continue clicked")
                            handleComplete(null)
                        },
                        onSkip = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 7: Skip clicked")
                            handleComplete(null)
                        },
                        onShowPrivacyPolicy = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 7: Privacy Policy link clicked")
                            showPrivacyPolicyDialog = true
                        },
                        onShowTerms = {
                            app.memora.platform.logging.AppLog.recordUserAction("Onboarding", "Screen 7: Terms link clicked")
                            showPrivacyPolicyDialog = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (showPrivacyPolicyDialog) {
            PrivacyPolicyDialog(
                onDismissRequest = { showPrivacyPolicyDialog = false }
            )
        }

        if (showEmailAuthDialog) {
            app.memora.presentation.common.EmailAuthDialog(
                onDismissRequest = {
                    showEmailAuthDialog = false
                    emailAuthError = null
                },
                isLoading = isEmailAuthLoading,
                errorMessage = emailAuthError,
                onSignIn = { email, password ->
                    isEmailAuthLoading = true
                    emailAuthError = null
                    coroutineScope.launch {
                        val result = authRepository?.signInWithEmailAndPassword(email, password)
                        isEmailAuthLoading = false
                        if (result != null && result.isSuccess) {
                            showEmailAuthDialog = false
                            handleComplete(null)
                        } else {
                            emailAuthError = result?.exceptionOrNull()?.localizedMessage
                                ?: "Sign-in failed. Please verify your email and password."
                        }
                    }
                },
                onSignUp = { email, password ->
                    isEmailAuthLoading = true
                    emailAuthError = null
                    coroutineScope.launch {
                        val result = authRepository?.signUpWithEmailAndPassword(email, password)
                        isEmailAuthLoading = false
                        if (result != null && result.isSuccess) {
                            showEmailAuthDialog = false
                            handleComplete(null)
                        } else {
                            emailAuthError = result?.exceptionOrNull()?.localizedMessage
                                ?: "Account creation failed. Please check your details."
                        }
                    }
                },
            )
        }
    }
}


