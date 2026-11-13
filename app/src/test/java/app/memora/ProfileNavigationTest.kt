package app.memora

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.memora.di.DefaultAppContainer
import app.memora.presentation.navigation.MemoraNavHost
import app.memora.presentation.navigation.Screen
import app.memora.ui.theme.MemoraTheme
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
class ProfileNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: MemoraApplication
    private lateinit var appContainer: DefaultAppContainer

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        appContainer = app.container as DefaultAppContainer
        appContainer.onboardingPreferences.setOnboardingCompleted(true)
        appContainer.onboardingPreferences.setPromptedNotificationPermission(true)
        appContainer.onboardingPreferences.clearAuth()
    }

    @Test
    fun bottomNav_containsOnlyHomeAddHistory() {
        assertEquals(3, Screen.bottomNavItems.size)
        assertEquals(listOf(Screen.Home, Screen.Add, Screen.History), Screen.bottomNavItems)
        assertFalse(Screen.bottomNavItems.contains(Screen.Settings))
    }

    @Test
    fun homeHeader_displaysProfileAvatarButton() {
        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithTag("home_profile_avatar_button").assertIsDisplayed()
    }

    @Test
    fun tappingProfileAvatar_opensProfileBottomSheet_andNavigatesToSettings() {
        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 1. Verify Avatar is present on Home header
        composeTestRule.onNodeWithTag("home_profile_avatar_button").assertIsDisplayed()

        // 2. Click Avatar -> BottomSheet opens
        composeTestRule.onNodeWithTag("home_profile_avatar_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_sheet_account_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_sheet_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_sheet_google_signin_button").assertIsDisplayed()

        // 3. Click "Settings & Preferences" inside Profile Sheet
        composeTestRule.onNodeWithTag("profile_sheet_settings_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Verify we navigated to Settings screen with the back button displayed
        composeTestRule.onNodeWithTag("settings_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_account_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_btn_signin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default Reminder Time").assertIsDisplayed()

        // 5. Click Back -> Returns to Home
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_profile_avatar_button").assertIsDisplayed()
    }

    @Test
    fun postOnboarding_showsNotificationPermissionDialog() {
        // Reset permission prompt flag so dialog shows
        appContainer.onboardingPreferences.setPromptedNotificationPermission(false)

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.waitForIdle()

        // Dialog should be visible
        composeTestRule.onNodeWithTag("notification_permission_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Notifications").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_allow_notifications").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_dismiss_notifications").assertIsDisplayed()

        // Dismiss dialog
        composeTestRule.onNodeWithTag("btn_dismiss_notifications").performClick()
        composeTestRule.waitForIdle()

        // Dialog should now be dismissed and flag set to true
        composeTestRule.onNodeWithTag("notification_permission_dialog").assertDoesNotExist()
        assertTrue(appContainer.onboardingPreferences.hasPromptedNotificationPermission())
    }
}
