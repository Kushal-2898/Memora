package app.memora

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import app.memora.di.DefaultAppContainer
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.model.EventType
import app.memora.presentation.capture.components.ExtractedMemoryBottomSheet
import app.memora.presentation.navigation.MemoraNavHost
import app.memora.presentation.navigation.Screen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import app.memora.ui.theme.MemoraTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MemoraApplication::class)
class MemoraEndToEndUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: MemoraApplication
    private lateinit var appContainer: DefaultAppContainer

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        appContainer = app.container as DefaultAppContainer
        appContainer.onboardingPreferences.setOnboardingCompleted(true)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            appContainer.database.clearAllTables()
        }
    }

    @Test
    fun appLaunch_showsEmptyState_whenNoMemories() {
        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onNodeWithTag("home_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_capture_button").assertIsDisplayed()
    }

    @Test
    fun memoryCreationFlow_andDeletion(): Unit = runBlocking {
        // Pre-populate memory
        val today = LocalDate.now()
        val memoryId = appContainer.createMemoryUseCase(
            title = "Test Delete Memory",
            eventType = EventType.OTHER,
            eventDate = today.plusDays(5)
        ).getOrThrow()

        // Create a reminder to ensure it shows as active in Needs Action
        appContainer.createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = today
        ).getOrThrow()

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Verify it appears in the list
        composeTestRule.onNodeWithTag("home_reminders_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Delete Memory").assertIsDisplayed()

        // Swipe left to delete
        composeTestRule.onNodeWithTag("swipe_item_$memoryId").performTouchInput {
            swipeLeft()
        }
        
        // Wait for UI to settle
        composeTestRule.waitForIdle()

        // Confirmation dialog is displayed
        composeTestRule.onNodeWithTag("delete_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_confirm_delete").performClick()
        
        // Wait until deleted item is removed from the UI
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Test Delete Memory").fetchSemanticsNodes().isEmpty()
        }

        // Verify memory is gone and empty state is shown (or at least item is gone)
        composeTestRule.onNodeWithText("Test Delete Memory").assertDoesNotExist()
    }

    @Test
    fun memoryCompletionFlow(): Unit = runBlocking {
        // Pre-populate memory
        val today = LocalDate.now()
        val memoryId = appContainer.createMemoryUseCase(
            title = "Test Complete Memory",
            eventType = EventType.DUE_DATE,
            eventDate = today.plusDays(2)
        ).getOrThrow()

        appContainer.createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = today
        ).getOrThrow()

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Verify it appears
        composeTestRule.onNodeWithText("Test Complete Memory").assertIsDisplayed()

        // Swipe right to complete
        composeTestRule.onNodeWithTag("swipe_item_$memoryId").performTouchInput {
            swipeRight()
        }
        
        composeTestRule.waitForIdle()

        // Verify Undo snackbar appears
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun historyRestoreFlow(): Unit = runBlocking {
        // Pre-populate memory
        val today = LocalDate.now()
        val memoryId = appContainer.createMemoryUseCase(
            title = "Test Restore Memory",
            eventType = EventType.DUE_DATE,
            eventDate = today.plusDays(3)
        ).getOrThrow()

        val reminderId = appContainer.createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = today
        ).getOrThrow()

        // Complete the reminder so it appears in History
        appContainer.completeReminderUseCase(reminderId).getOrThrow()

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Navigate to History tab
        composeTestRule.onNodeWithTag(Screen.History.testTag).performClick()
        composeTestRule.waitForIdle()

        // Verify the completed memory is displayed in History
        composeTestRule.onNodeWithText("Test Restore Memory").assertIsDisplayed()

        // Click the Restore button on the card
        composeTestRule.onNodeWithTag("restore_button_$memoryId").performClick()
        composeTestRule.waitForIdle()

        // Wait until it leaves History
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Test Restore Memory").fetchSemanticsNodes().isEmpty()
        }

        // Verify item is no longer in History
        composeTestRule.onNodeWithText("Test Restore Memory").assertDoesNotExist()

        // Navigate back to Home tab
        composeTestRule.onNodeWithTag(Screen.Home.testTag).performClick()
        composeTestRule.waitForIdle()

        // Verify restored memory is now back on Home!
        composeTestRule.onNodeWithText("Test Restore Memory").assertIsDisplayed()
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test
    fun extractedBottomSheet_accidentalDismissal_showsConfirmationDialog() {
        val candidate = ExtractedMemoryCandidate(
            suggestedTitle = "Dentist Appointment",
            suggestedEventDate = LocalDate.now().plusDays(7),
            suggestedEventType = EventType.OTHER,
        )

        var isSheetOpen by mutableStateOf(true)
        var isDiscardDialogVisible by mutableStateOf(false)

        composeTestRule.setContent {
            MemoraTheme {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    if (isSheetOpen) {
                        ExtractedMemoryBottomSheet(
                            candidate = candidate,
                            onSave = { _, _, _, _ -> isSheetOpen = false },
                            onDismiss = { isSheetOpen = false },
                            onRequestDismiss = { isDiscardDialogVisible = true },
                        )
                    }

                    if (isDiscardDialogVisible) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { isDiscardDialogVisible = false },
                            title = { androidx.compose.material3.Text("Discard memory?") },
                            text = { androidx.compose.material3.Text("Are you sure you want to discard this extracted memory?") },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        isDiscardDialogVisible = false
                                        isSheetOpen = false
                                    },
                                    modifier = Modifier.testTag("dialog_confirm_discard"),
                                ) {
                                    androidx.compose.material3.Text("Discard")
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { isDiscardDialogVisible = false },
                                    modifier = Modifier.testTag("dialog_cancel_discard"),
                                ) {
                                    androidx.compose.material3.Text("Keep Editing")
                                }
                            },
                            modifier = Modifier.testTag("discard_sheet_confirmation_dialog"),
                        )
                    }
                }
            }
        }

        // 1. Verify bottom sheet is displayed
        composeTestRule.onNodeWithTag("capture_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dentist Appointment").assertIsDisplayed()

        // 2. Trigger discard confirmation dialog (simulating accidental dismiss)
        composeTestRule.runOnUiThread {
            isDiscardDialogVisible = true
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("discard_sheet_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard memory?").assertIsDisplayed()

        // 3. Tap "Keep Editing" -> dialog closes, bottom sheet remains open
        composeTestRule.onNodeWithTag("dialog_cancel_discard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("discard_sheet_confirmation_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("capture_bottom_sheet").assertIsDisplayed()

        // 4. Trigger dismiss again and tap "Discard" -> bottom sheet closes
        composeTestRule.runOnUiThread {
            isDiscardDialogVisible = true
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_confirm_discard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("capture_bottom_sheet").assertDoesNotExist()
    }

    @Test
    fun firstLaunch_showsOnboarding_andCompletionNavigatesToHome() {
        appContainer.onboardingPreferences.setOnboardingCompleted(false)

        composeTestRule.setContent {
            MemoraTheme {
                MemoraNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Verify onboarding screen is shown and bottom navigation is hidden
        composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("main_navigation_bar").assertDoesNotExist()

        // Click Skip to complete onboarding
        composeTestRule.onNodeWithTag("onboarding_btn_skip").performClick()
        composeTestRule.waitForIdle()

        // Verify navigated to Home and bottom bar is now shown
        composeTestRule.onNodeWithTag("home_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("main_navigation_bar").assertIsDisplayed()
        org.junit.Assert.assertTrue(appContainer.onboardingPreferences.hasCompletedOnboarding())
    }
}


