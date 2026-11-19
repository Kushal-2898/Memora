package app.memora

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import app.memora.data.local.DefaultOnboardingPreferences
import app.memora.di.DefaultAppContainer
import app.memora.presentation.navigation.MemoraNavHost
import app.memora.presentation.onboarding.OnboardingScreen
import app.memora.presentation.onboarding.components.CategorySelectionGrid
import app.memora.presentation.onboarding.components.InteractiveScanSimulator
import app.memora.presentation.onboarding.components.NotificationPreviewCard
import app.memora.ui.theme.MemoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MemoraApplication::class, qualifiers = "w411dp-h914dp-xxhdpi")
class OnboardingFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: MemoraApplication
    private lateinit var appContainer: DefaultAppContainer
    private lateinit var preferences: DefaultOnboardingPreferences

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        val context: Context = app
        context.getSharedPreferences("memora_user_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        appContainer = app.container as DefaultAppContainer
        runBlocking(Dispatchers.IO) {
            appContainer.database.clearAllTables()
        }
        preferences = DefaultOnboardingPreferences(context)
    }

    @Test
    fun firstRun_showsOnboardingScreen_withScreen1Content() {
        assertFalse(preferences.hasCompletedOnboarding())

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Onboarding screen is active
        composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_app_title").assertIsDisplayed()

        // Screen 1 copy and progress
        composeTestRule.onNodeWithText("Never miss", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_indicator_dots").assertIsDisplayed()

        // Screen 1 Hero Logo
        composeTestRule.onNodeWithContentDescription("Memora Logo").assertIsDisplayed()

        // Screen 1 actions: Next and Skip present, Back NOT present
        composeTestRule.onNodeWithTag("onboarding_btn_next").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_skip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_back").assertDoesNotExist()

        // Bottom nav is hidden during onboarding
        composeTestRule.onNodeWithTag("main_navigation_bar").assertDoesNotExist()
    }

    @Test
    fun returningUser_bypassesOnboarding_directlyToHome() {
        preferences.setOnboardingCompleted(true)
        preferences.setAuth("uid_123", "user@test.com", "Test User", null)
        assertTrue(preferences.hasCompletedOnboarding())

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Onboarding is not displayed
        composeTestRule.onNodeWithTag("onboarding_screen").assertDoesNotExist()
        // Main navigation bar is displayed (only shown when not on Onboarding)
        composeTestRule.onNodeWithTag("main_navigation_bar").assertIsDisplayed()
        // Home screen is displayed
        composeTestRule.onNodeWithTag("home_empty_state").assertIsDisplayed()
    }

    @Test
    fun signedOutUser_showsOnboarding_andSkipNavigatesToAccountScreen() {
        preferences.setOnboardingCompleted(false)
        preferences.clearAuth()

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Onboarding is displayed
        composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        // Tap Skip on Screen 1 to jump straight to Account screen
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_google_signin").assertIsDisplayed()
    }

    @Test
    fun nextButton_advancesThroughAllScreensInSequence() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onboardingPreferences = preferences,
                    authRepository = appContainer.authRepository,
                )
            }
        }

        // Screen 1
        composeTestRule.onNodeWithText("Never miss", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_back").assertDoesNotExist()

        // Advance to Screen 2
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Scan product labels").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_skip").assertIsDisplayed()

        // Advance to Screen 3
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Set reminders").assertIsDisplayed()

        // Advance to Screen 4
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Track everything").assertIsDisplayed()

        // Advance to Screen 5
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Stay on top").assertIsDisplayed()

        // Advance to Screen 6
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You’re all set!", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").assertIsDisplayed()

        // Advance to Screen 7 (Account Creation)
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_google_signin").assertIsDisplayed()

        // Should not have completed yet
        assertFalse(completed)

        // Tap Continue with Google on Screen 7 to complete
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun categorySelectionGrid_togglesCategoriesDirectly() {
        var selected = setOf("groceries", "medicines")
        composeTestRule.setContent {
            MemoraTheme {
                CategorySelectionGrid(
                    selectedCategories = selected,
                    onToggleCategory = { id ->
                        selected = if (selected.contains(id)) selected - id else selected + id
                        preferences.setSelectedCategories(selected)
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("category_chip_warranties").performClick()
        composeTestRule.waitForIdle()

        assertTrue(preferences.getSelectedCategories().contains("warranties"))
    }

    @Test
    fun scanSimulator_runsSimulatedLaserScan() {
        composeTestRule.setContent {
            MemoraTheme {
                InteractiveScanSimulator()
            }
        }

        composeTestRule.onNodeWithTag("scan_simulator_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_simulator_result_chip").assertIsDisplayed()
    }

    @Test
    fun notificationPreviewCard_togglesDone() {
        composeTestRule.setContent {
            MemoraTheme {
                NotificationPreviewCard()
            }
        }

        composeTestRule.onNodeWithTag("notification_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_preview_mark_done_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("✓ Organic Rolled Oats marked completed!").assertIsDisplayed()
    }

    @Test
    fun backButton_navigatesToPreviousScreen() {
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        // Move to Screen 2
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Scan product labels").assertIsDisplayed()

        // Navigate back via system gesture / back handler
        androidx.test.espresso.Espresso.pressBack()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Should be back on Screen 1
        composeTestRule.onNodeWithText("Never miss", substring = true).assertIsDisplayed()
    }

    @Test
    fun skipButton_fromScreen1_navigatesToAccountScreen() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onboardingPreferences = preferences,
                    authRepository = appContainer.authRepository,
                )
            }
        }

        assertFalse(completed)
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Navigates directly to Screen 7 (Account Creation)
        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()
        assertFalse(completed)

        // Tapping Google Sign-in completes onboarding
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun skipFromLaterScreen_navigatesToAccountScreen() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onboardingPreferences = preferences,
                    authRepository = appContainer.authRepository,
                )
            }
        }

        // Advance to Screen 3
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Set reminders").assertIsDisplayed()

        // Tap skip
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Screen 7 is shown
        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()
        assertFalse(completed)
    }

    @Test
    fun fullNavHostFlow_completingViaGetStarted_persistsStateAndShowsHome() {
        assertFalse(preferences.hasCompletedOnboarding())

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Step through Screens 1 to 6
        for (i in 0 until 5) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.mainClock.advanceTimeBy(600)
            composeTestRule.waitForIdle()
        }

        // On Screen 6, advance to Screen 7
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // On Screen 7, tap Continue with Google to complete
        composeTestRule.onNodeWithTag("btn_google_signin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Preferences updated
        assertTrue(preferences.hasCompletedOnboarding())
        // Main navigation bar displayed
        composeTestRule.onNodeWithTag("main_navigation_bar").assertIsDisplayed()
        // Home screen displayed
        composeTestRule.onNodeWithTag("home_empty_state").assertIsDisplayed()
    }

    @Test
    fun fullNavHostFlow_completingViaSkip_persistsStateAndShowsHome() {
        assertFalse(preferences.hasCompletedOnboarding())

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Tap skip on Screen 1 -> navigates to Screen 7
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Screen 7 is displayed
        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()

        // Tap Continue with Google
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Preferences updated
        assertTrue(preferences.hasCompletedOnboarding())
        // Main navigation bar displayed
        composeTestRule.onNodeWithTag("main_navigation_bar").assertIsDisplayed()
        // Home screen displayed
        composeTestRule.onNodeWithTag("home_empty_state").assertIsDisplayed()
    }

    @Test
    fun rapidTapsOnGetStarted_onlyInvokesOnCompleteOnce() {
        var completeCount = 0
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completeCount++ },
                    authRepository = appContainer.authRepository,
                )
            }
        }

        // Step to Screen 6
        for (i in 0 until 5) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.mainClock.advanceTimeBy(600)
            composeTestRule.waitForIdle()
        }

        // Advance to Screen 7
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Tap Continue with Google rapidly on Screen 7
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, completeCount)
    }

    @Test
    fun screen7_skipIsRemoved_andGoogleSignInMandatory() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onboardingPreferences = preferences,
                    authRepository = appContainer.authRepository,
                )
            }
        }

        // Step through to Screen 6
        for (i in 0 until 5) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.mainClock.advanceTimeBy(600)
            composeTestRule.waitForIdle()
        }

        // Advance to Screen 7
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Verify Skip and unauthenticated Continue are NOT present on Screen 7
        composeTestRule.onNodeWithTag("onboarding_btn_skip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("onboarding_btn_continue").assertDoesNotExist()

        // Authenticate with Google
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.waitForIdle()

        assertTrue(completed)
        assertTrue(preferences.hasCompletedOnboarding())
        assertNotNull(preferences.getAuthUid())
    }

    @Test
    fun screen7_googleSignIn_updatesAuthStateAndCompletes() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onboardingPreferences = preferences,
                    authRepository = appContainer.authRepository,
                )
            }
        }

        // Step through to Screen 6
        for (i in 0 until 5) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.mainClock.advanceTimeBy(600)
            composeTestRule.waitForIdle()
        }

        // Advance to Screen 7
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Tap Continue with Google
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.waitForIdle()

        assertTrue(completed)
        assertTrue(preferences.hasCompletedOnboarding())
        assertNotNull(preferences.getAuthUid())
    }

    @Test
    fun screen6_maybeLater_navigatesToScreen7_andCompletesToHome() {
        var completed = false
        var addFirstMemoryCalled = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onAddFirstItem = { addFirstMemoryCalled = true },
                    onboardingPreferences = preferences,
                    authRepository = appContainer.authRepository,
                )
            }
        }

        // Step through to Screen 6
        for (i in 0 until 5) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.mainClock.advanceTimeBy(600)
            composeTestRule.waitForIdle()
        }

        // Tap "Maybe later" on Screen 6
        composeTestRule.onNodeWithTag("onboarding_btn_maybe_later").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // Verify we are now on Screen 7 ("Create your account" title displayed)
        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()

        // Tap Continue with Google on Screen 7
        composeTestRule.onNodeWithTag("btn_google_signin").performClick()
        composeTestRule.waitForIdle()

        // Verify completed to Home (not addFirstMemory)
        assertTrue(completed)
        assertFalse(addFirstMemoryCalled)
        assertTrue(preferences.hasCompletedOnboarding())
    }

    @Test
    fun screen5_nextButton_requestsNotificationPermission() {
        var notificationPermissionRequested = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = {},
                    onboardingPreferences = preferences,
                    onRequestNotificationPermission = { notificationPermissionRequested = true },
                )
            }
        }

        // Advance through Screens 1 -> 2 -> 3 -> 4 -> 5
        for (i in 0 until 4) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.mainClock.advanceTimeBy(600)
            composeTestRule.waitForIdle()
        }

        // Now on Screen 5 ("Stay on top")
        composeTestRule.onNodeWithText("Stay on top").assertIsDisplayed()
        assertFalse(notificationPermissionRequested)

        // Tap Next on Screen 5 -> should trigger notification permission request
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        assertTrue(notificationPermissionRequested)
    }
}
