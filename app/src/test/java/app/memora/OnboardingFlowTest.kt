package app.memora

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MemoraApplication::class)
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
        composeTestRule.onNodeWithText("Never lose track of what matters.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_progress_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 of 4").assertIsDisplayed()

        // Screen 1 Loss Aversion Hero Card
        composeTestRule.onNodeWithTag("onboarding_loss_aversion_card").assertIsDisplayed()

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
    fun nextButton_advancesThroughAllScreensInSequence() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(
                    onComplete = { completed = true },
                    onboardingPreferences = preferences,
                )
            }
        }

        // Screen 1
        composeTestRule.onNodeWithText("Never lose track of what matters.").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 of 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_loss_aversion_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_back").assertDoesNotExist()

        // Advance to Screen 2
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Choose what you track.").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 of 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_category_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_skip").assertIsDisplayed()

        // Advance to Screen 3
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Smart visual capture.").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 of 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_simulator_container").assertIsDisplayed()

        // Advance to Screen 4
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Let Memora remind you.").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 of 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_preview_card").assertIsDisplayed()

        // Advance to Final Screen
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You're ready to begin.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("final_ready_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").assertIsDisplayed()

        // Educational progress and skip must NOT be displayed on final screen
        composeTestRule.onNodeWithTag("onboarding_progress_text").assertDoesNotExist()
        composeTestRule.onNodeWithTag("onboarding_btn_skip").assertDoesNotExist()

        // Should not have completed yet
        assertFalse(completed)

        // Tap Get started
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
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
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Choose what you track.").assertIsDisplayed()

        // Tap Back
        composeTestRule.onNodeWithTag("onboarding_btn_back").performClick()
        composeTestRule.waitForIdle()

        // Should be back on Screen 1
        composeTestRule.onNodeWithText("Never lose track of what matters.").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 of 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_back").assertDoesNotExist()
    }

    @Test
    fun skipButton_invokesOnCompleteImmediately() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(onComplete = { completed = true })
            }
        }

        assertFalse(completed)
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun skipFromLaterScreen_invokesOnCompleteImmediately() {
        var completed = false
        composeTestRule.setContent {
            MemoraTheme {
                OnboardingScreen(onComplete = { completed = true })
            }
        }

        // Advance to Screen 3
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smart visual capture.").assertIsDisplayed()

        // Tap skip
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.waitForIdle()
        assertTrue(completed)
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

        // Step through to final screen
        for (i in 0 until 4) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.waitForIdle()
        }

        composeTestRule.onNodeWithTag("onboarding_btn_get_started").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
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

        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
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
                OnboardingScreen(onComplete = { completeCount++ })
            }
        }

        // Step to final screen
        for (i in 0 until 4) {
            composeTestRule.onNodeWithTag("onboarding_btn_next").performClick()
            composeTestRule.waitForIdle()
        }

        // Tap Get started rapidly
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.onNodeWithTag("onboarding_btn_get_started").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, completeCount)
    }
}
