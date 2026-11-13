package app.memora

import app.memora.data.gateway.DefaultBillingGateway
import app.memora.data.local.OfflineProductCatalog
import app.memora.data.local.OnboardingPreferences
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.usecase.CreateMemoryUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.domain.usecase.RefineWithGeminiUseCase
import app.memora.platform.image.ImageStorage
import app.memora.platform.notification.NotificationScheduler
import app.memora.platform.ocr.BarcodeExtractor
import app.memora.platform.ocr.ExtractedText
import app.memora.platform.ocr.TextExtractor
import app.memora.presentation.capture.CaptureViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionAuditFixesTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaultBillingGateway restorePurchases fails when no real Play Store purchase exists`() = runTest {
        val gateway = DefaultBillingGateway()
        val result = gateway.restorePurchases()
        assertTrue("Dev stub must not spoof a free Pro purchase on restore", result.isFailure)
    }

    @Test
    fun `captureViewModel learns unknown barcode when user saves memory with edited title`() = runTest(testDispatcher) {
        val catalog = OfflineProductCatalog()
        val unknownIndianBarcode = "8909999888877"

        val textExtractor = object : TextExtractor {
            override suspend fun extractText(imageUri: String) =
                Result.success(ExtractedText("BEST BEFORE 15/12/2027"))
            override val isAvailable: Boolean = true
        }
        val barcodeExtractor = object : BarcodeExtractor {
            override suspend fun extractBarcode(imageUri: String) =
                Result.success(unknownIndianBarcode)
        }
        val extractLocallyUseCase = ExtractLocallyUseCase(
            textExtractor = textExtractor,
            barcodeExtractor = barcodeExtractor,
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = catalog,
        )

        val imageStorage = object : ImageStorage {
            override fun createCaptureFile(prefix: String): File = File.createTempFile(prefix, ".jpg")
            override suspend fun importFromUri(uriString: String, source: CaptureSource) =
                Result.success(CapturedImage(uriString, source, Instant.now(), "image/jpeg", 1024L))
            override fun createCapturedImageFromFile(file: File, source: CaptureSource) =
                CapturedImage(file.toURI().toString(), source, Instant.now(), "image/jpeg", 1024L)
            override fun deleteImage(uriString: String): Boolean = true
        }
        val memoryRepo = object : MemoryRepository {
            override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
            override suspend fun getMemory(id: String): Memory? = null
            override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = null
            override suspend fun createMemory(memory: Memory, initialReminder: Reminder?) = Result.success(Unit)
            override suspend fun updateMemory(memory: Memory) = Result.success(Unit)
            override suspend fun deleteMemory(id: String) = Result.success(Unit)
        }
        val scheduler = object : NotificationScheduler {
            override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String) = Result.success(Unit)
            override suspend fun scheduleReminder(
                memoryId: String,
                reminderId: String,
                title: String,
                reminderStartDate: LocalDate,
                eventDate: LocalDate,
                timeZoneId: String,
                reminderTime: LocalTime,
            ) = Result.success(Unit)
            override suspend fun cancelReminder(reminderId: String) = Result.success(Unit)
            override val areNotificationsEnabled: Boolean = true
        }

        val viewModel = CaptureViewModel(
            processCapturedImageUseCase = ProcessCapturedImageUseCase(imageStorage),
            imageStorage = imageStorage,
            createMemoryUseCase = CreateMemoryUseCase(memoryRepo),
            deleteMemoryUseCase = DeleteMemoryUseCase(memoryRepo),
            notificationScheduler = scheduler,
            extractLocallyUseCase = extractLocallyUseCase,
            refineWithGeminiUseCase = RefineWithGeminiUseCase(DeterministicMemoryExtractor()),
            crashReporter = FakeCrashReporter(),
            analyticsLogger = FakeAnalyticsLogger(),
        )

        val img = CapturedImage("file:///test.jpg", CaptureSource.CAMERA, Instant.now(), "image/jpeg", 1024L)
        viewModel.processLocalExtraction(img).join()

        // User edits title to "Haldiram's Bhujia Sev 400g" and saves
        viewModel.saveMemory(
            title = "Haldiram's Bhujia Sev 400g",
            eventType = EventType.BEST_BEFORE,
            eventDate = LocalDate.of(2027, 12, 15),
            remindDaysBefore = 7,
        ).join()

        // Next time the same barcode is looked up, OfflineProductCatalog knows it!
        val learned = catalog.lookup(unknownIndianBarcode)
        assertNotNull(learned)
        assertEquals("Haldiram's Bhujia Sev 400g", learned?.productName)
        assertEquals(EventType.BEST_BEFORE, learned?.category)
    }

    @Test
    fun `clearAllUserData cancels all active reminder alarms before wiping database`() = runTest {
        val cancelledIds = mutableListOf<String>()
        val activeReminders = listOf(
            Reminder(
                id = "rem-1",
                memoryId = "mem-1",
                reminderStartDate = LocalDate.of(2026, 11, 10),
                reminderTime = LocalTime.of(9, 0),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
            Reminder(
                id = "rem-2",
                memoryId = "mem-2",
                reminderStartDate = LocalDate.of(2026, 11, 12),
                reminderTime = LocalTime.of(9, 0),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
        val reminderRepo = object : ReminderRepository {
            override fun observeReminders(): Flow<List<Reminder>> = flowOf(activeReminders)
            override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> = flowOf(emptyList())
            override suspend fun getReminder(id: String): Reminder? = null
            override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> = emptyList()
            override suspend fun createReminder(reminder: Reminder) = Result.success(Unit)
            override suspend fun updateReminder(reminder: Reminder) = Result.success(Unit)
            override suspend fun completeReminder(id: String, completedAt: Instant) = Result.success(Unit)
            override suspend fun deleteReminder(id: String) = Result.success(Unit)
        }
        val scheduler = object : NotificationScheduler {
            override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String) = Result.success(Unit)
            override suspend fun scheduleReminder(
                memoryId: String,
                reminderId: String,
                title: String,
                reminderStartDate: LocalDate,
                eventDate: LocalDate,
                timeZoneId: String,
                reminderTime: LocalTime,
            ) = Result.success(Unit)
            override suspend fun cancelReminder(reminderId: String): Result<Unit> {
                cancelledIds.add(reminderId)
                return Result.success(Unit)
            }
            override val areNotificationsEnabled: Boolean = true
        }

        var dbCleared = false
        val clearUseCase = app.memora.domain.usecase.ClearAllUserDataUseCase(
            reminderRepository = reminderRepo,
            notificationScheduler = scheduler,
            onClearDatabaseAndFiles = { dbCleared = true },
        )

        clearUseCase().getOrThrow()

        assertEquals(listOf("rem-1", "rem-2"), cancelledIds)
        assertTrue(dbCleared)
    }

    @Test
    fun `deleteMemoryUseCase triggers remote cloud delete callback on success`() = runTest {
        val remoteDeletedIds = mutableListOf<String>()
        val memoryRepo = object : MemoryRepository {
            override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
            override suspend fun getMemory(id: String): Memory? = null
            override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = null
            override suspend fun createMemory(memory: Memory, initialReminder: Reminder?) = Result.success(Unit)
            override suspend fun updateMemory(memory: Memory) = Result.success(Unit)
            override suspend fun deleteMemory(id: String) = Result.success(Unit)
        }
        val deleteUseCase = DeleteMemoryUseCase(memoryRepo) { id ->
            remoteDeletedIds.add(id)
        }

        deleteUseCase("mem-cloud-99").getOrThrow()
        assertEquals(listOf("mem-cloud-99"), remoteDeletedIds)
    }

    @Test
    fun `createMemoryUseCase and updateMemoryUseCase truncate overlong titles and clamp snoozed reminderStartDate`() = runTest {
        var savedMemory: Memory? = null
        var updatedRemindersList: List<Reminder> = emptyList()
        val existingMemory = Memory(
            id = "mem-1",
            title = "Milk",
            eventType = EventType.EXPIRATION,
            eventDate = LocalDate.of(2026, 11, 20),
            timeZoneId = "UTC",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        // Suppose reminder was snoozed to 2026-11-19 (1 day before old eventDate)
        val snoozedReminder = Reminder(
            id = "rem-1",
            memoryId = "mem-1",
            reminderStartDate = LocalDate.of(2026, 11, 25), // abnormally past event date
            reminderTime = LocalTime.of(9, 0),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        val memoryRepo = object : MemoryRepository {
            override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
            override suspend fun getMemory(id: String): Memory? = existingMemory
            override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? =
                MemoryWithReminders(existingMemory, listOf(snoozedReminder))
            override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> {
                savedMemory = memory
                return Result.success(Unit)
            }
            override suspend fun updateMemory(memory: Memory) = Result.success(Unit)
            override suspend fun updateMemoryWithReminders(memory: Memory, reminders: List<Reminder>): Result<Unit> {
                savedMemory = memory
                updatedRemindersList = reminders
                return Result.success(Unit)
            }
            override suspend fun deleteMemory(id: String) = Result.success(Unit)
        }
        val reminderRepo = object : ReminderRepository {
            override fun observeReminders(): Flow<List<Reminder>> = flowOf(emptyList())
            override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> = flowOf(emptyList())
            override suspend fun getReminder(id: String): Reminder? = null
            override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> = emptyList()
            override suspend fun createReminder(reminder: Reminder) = Result.success(Unit)
            override suspend fun updateReminder(reminder: Reminder) = Result.success(Unit)
            override suspend fun completeReminder(id: String, completedAt: Instant) = Result.success(Unit)
            override suspend fun deleteReminder(id: String) = Result.success(Unit)
        }

        val overlongTitle = "A".repeat(300)
        val createUseCase = CreateMemoryUseCase(memoryRepo)
        createUseCase(
            title = overlongTitle,
            eventType = EventType.EXPIRATION,
            eventDate = LocalDate.of(2026, 11, 20),
        ).getOrThrow()
        assertEquals(120, savedMemory?.title?.length)

        val updateUseCase = app.memora.domain.usecase.UpdateMemoryUseCase(memoryRepo, reminderRepo)
        val newEventDate = LocalDate.of(2026, 11, 15)
        updateUseCase(existingMemory, overlongTitle, EventType.EXPIRATION, newEventDate).getOrThrow()
        assertEquals(120, savedMemory?.title?.length)
        assertTrue(
            "Updated reminderStartDate must never be after newEventDate",
            !updatedRemindersList.first().reminderStartDate.isAfter(newEventDate)
        )
    }

    @Test
    fun `memoraNotificationScheduler computeNextTriggerInstant advances past same-day time to tomorrow`() {
        val zone = java.time.ZoneId.of("UTC")
        // Current time is 2026-11-04 at 14:00 UTC (2:00 PM)
        val now = Instant.parse("2026-11-04T14:00:00Z")
        // Reminder start date is today (2026-11-04) at 09:00 AM (already 5 hours in the past)
        val triggerInstant = app.memora.platform.notification.MemoraNotificationScheduler.computeNextTriggerInstant(
            reminderStartDate = LocalDate.of(2026, 11, 4),
            reminderTime = LocalTime.of(9, 0),
            zone = zone,
            now = now,
        )
        // Must schedule for tomorrow (2026-11-05T09:00:00Z), not in the past
        assertEquals(Instant.parse("2026-11-05T09:00:00Z"), triggerInstant)
    }

    @Test
    fun `scanner retakeOrReplaceImage works from ExtractionReady and timeout error does not report crash`() = runTest(testDispatcher) {
        val textExtractor = object : TextExtractor {
            override suspend fun extractText(imageUri: String) =
                Result.success(ExtractedText("Amul Taaza\nEXP 15/12/2027"))
            override val isAvailable: Boolean = true
        }
        val imageStorage = object : ImageStorage {
            override fun createCaptureFile(prefix: String): File = File.createTempFile(prefix, ".jpg")
            override suspend fun importFromUri(uriString: String, source: CaptureSource) =
                Result.success(CapturedImage(uriString, source, Instant.now(), "image/jpeg"))
            override fun createCapturedImageFromFile(file: File, source: CaptureSource) =
                CapturedImage(file.toURI().toString(), source, Instant.now(), "image/jpeg")
            override fun deleteImage(uriString: String): Boolean = true
        }
        val memoryRepo = object : MemoryRepository {
            override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
            override suspend fun getMemory(id: String): Memory? = null
            override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = null
            override suspend fun createMemory(memory: Memory, initialReminder: Reminder?) = Result.success(Unit)
            override suspend fun updateMemory(memory: Memory) = Result.success(Unit)
            override suspend fun deleteMemory(id: String) = Result.success(Unit)
        }
        val scheduler = object : NotificationScheduler {
            override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String) = Result.success(Unit)
            override suspend fun scheduleReminder(
                memoryId: String,
                reminderId: String,
                title: String,
                reminderStartDate: LocalDate,
                eventDate: LocalDate,
                timeZoneId: String,
                reminderTime: LocalTime,
            ) = Result.success(Unit)
            override suspend fun cancelReminder(reminderId: String) = Result.success(Unit)
            override val areNotificationsEnabled: Boolean = true
        }
        val recordedExceptions = mutableListOf<Throwable>()
        val crashReporter = object : app.memora.domain.logging.CrashReporter {
            override fun recordException(throwable: Throwable) { recordedExceptions.add(throwable) }
            override fun log(message: String) {}
            override fun setUserId(userId: String) {}
            override fun setCustomKey(key: String, value: String) {}
        }
        val viewModel = CaptureViewModel(
            processCapturedImageUseCase = ProcessCapturedImageUseCase(imageStorage),
            imageStorage = imageStorage,
            createMemoryUseCase = CreateMemoryUseCase(memoryRepo),
            deleteMemoryUseCase = DeleteMemoryUseCase(memoryRepo),
            notificationScheduler = scheduler,
            extractLocallyUseCase = ExtractLocallyUseCase(textExtractor, DeterministicMemoryExtractor()),
            refineWithGeminiUseCase = RefineWithGeminiUseCase(DeterministicMemoryExtractor()),
            crashReporter = crashReporter,
            analyticsLogger = FakeAnalyticsLogger(),
        )

        val galleryImg = CapturedImage("file:///gallery.jpg", CaptureSource.GALLERY, Instant.now(), "image/jpeg")
        viewModel.processLocalExtraction(galleryImg).join()
        viewModel.skipGeminiAndProceed()
        assertTrue(viewModel.uiState.value is app.memora.presentation.capture.CaptureUiState.ExtractionReady)

        // Calling startScanner(true) while already in ExtractionReady (or importing shared image) must NOT clobber state
        viewModel.startScanner(true)
        assertTrue(
            "startScanner must not overwrite an active ExtractionReady/shared-image state",
            viewModel.uiState.value is app.memora.presentation.capture.CaptureUiState.ExtractionReady
        )

        // Calling retakeOrReplaceImage from ExtractionReady with GALLERY source must invoke onLaunchPicker
        var launchedPickerSource: CaptureSource? = null
        viewModel.retakeOrReplaceImage { src -> launchedPickerSource = src }
        assertEquals(CaptureSource.GALLERY, launchedPickerSource)

        // Calling onCaptureError with Timeout must NOT record a crash to Crashlytics and must allow manual entry
        viewModel.onCaptureError(app.memora.domain.model.MemoraError.Capture.Timeout("Scanner timed out."))
        assertTrue("Timeout must not pollute Crashlytics", recordedExceptions.isEmpty())
        val errState = viewModel.uiState.value as app.memora.presentation.capture.CaptureUiState.Error
        assertTrue("Timeout error must allow manual entry", errState.canEnterManually)
        assertEquals(app.memora.presentation.capture.CaptureUiState.CameraActive, errState.returnState)
    }

    @Test
    fun `snoozing DUE_TODAY or OVERDUE reminder moves status to UPCOMING until snooze date`() = runTest {
        val today = LocalDate.of(2026, 11, 4)
        val clock = object : app.memora.domain.scheduler.ClockProvider {
            override fun now(): Instant = today.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant()
            override fun today(zoneId: java.time.ZoneId): LocalDate = today
        }
        val statusUseCase = app.memora.domain.usecase.CalculateReminderStatusUseCase(clock)

        // Overdue item (eventDate was yesterday, snoozed until tomorrow)
        val statusAfterSnoozeOverdue = statusUseCase(
            eventDate = today.minusDays(1),
            reminderStartDate = today.plusDays(1),
            completedAt = null,
            timeZoneId = "UTC",
        )
        assertEquals(
            "Snoozed OVERDUE item must become UPCOMING until tomorrow",
            app.memora.domain.model.ReminderStatus.UPCOMING,
            statusAfterSnoozeOverdue
        )

        // Due today item (eventDate is today, snoozed until tomorrow)
        val statusAfterSnoozeDueToday = statusUseCase(
            eventDate = today,
            reminderStartDate = today.plusDays(1),
            completedAt = null,
            timeZoneId = "UTC",
        )
        assertEquals(
            "Snoozed DUE_TODAY item must become UPCOMING until tomorrow",
            app.memora.domain.model.ReminderStatus.UPCOMING,
            statusAfterSnoozeDueToday
        )
    }

    @Test
    fun `CaptureViewModel cancelCapture deletes active temp image, manual entry skips quota, and save error preserves ExtractionReady`() = runTest {
        val deletedUris = mutableListOf<String>()
        val imageStorage = object : ImageStorage {
            override fun createCaptureFile(prefix: String): File = File.createTempFile(prefix, ".jpg")
            override suspend fun importFromUri(uriString: String, source: CaptureSource) =
                Result.success(CapturedImage(uriString, source, Instant.now(), "image/jpeg"))
            override fun createCapturedImageFromFile(file: File, source: CaptureSource) =
                CapturedImage(file.toURI().toString(), source, Instant.now(), "image/jpeg")
            override fun deleteImage(uriString: String): Boolean {
                deletedUris.add(uriString)
                return true
            }
        }
        var shouldFailCreate = false
        val memoryRepo = object : MemoryRepository {
            override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
            override suspend fun getMemory(id: String): Memory? = null
            override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = null
            override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> =
                if (shouldFailCreate) Result.failure(IllegalStateException("DB error")) else Result.success(Unit)
            override suspend fun updateMemory(memory: Memory) = Result.success(Unit)
            override suspend fun deleteMemory(id: String) = Result.success(Unit)
        }
        val scheduler = object : NotificationScheduler {
            override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String) = Result.success(Unit)
            override suspend fun scheduleReminder(
                memoryId: String,
                reminderId: String,
                title: String,
                reminderStartDate: LocalDate,
                eventDate: LocalDate,
                timeZoneId: String,
                reminderTime: LocalTime,
            ) = Result.success(Unit)
            override suspend fun cancelReminder(reminderId: String) = Result.success(Unit)
            override val areNotificationsEnabled: Boolean = true
        }
        var scanCount = 0
        val prefs = object : OnboardingPreferences {
            override fun hasCompletedOnboarding() = true
            override fun setOnboardingCompleted(completed: Boolean) {}
            override fun getSelectedCategories() = emptySet<String>()
            override fun setSelectedCategories(categories: Set<String>) {}
            override fun getDefaultReminderTime(): LocalTime = LocalTime.of(9, 0)
            override fun setDefaultReminderTime(time: LocalTime) {}
            override fun getMonthlyScanCount(): Int = scanCount
            override fun incrementMonthlyScanCount(): Int = ++scanCount
            override fun getRemainingMonthlyScans(limit: Int): Int = (limit - scanCount).coerceAtLeast(0)
            override fun isProUser(): Boolean = false
            override fun setProUser(isPro: Boolean) {}
            override fun getCustomCategories() = emptySet<String>()
            override fun addCustomCategory(category: String) {}
            override fun getAuthUid(): String? = null
            override fun getAuthEmail(): String? = null
            override fun getAuthDisplayName(): String? = null
            override fun getAuthPhotoUrl(): String? = null
            override fun setAuth(uid: String, email: String?, displayName: String?, photoUrl: String?) {}
            override fun clearAuth() {}
            override fun hasPromptedNotificationPermission() = true
            override fun setPromptedNotificationPermission(prompted: Boolean) {}
        }
        val textExtractor = object : TextExtractor {
            override suspend fun extractText(imageUri: String) =
                Result.success(ExtractedText("Milk\nEXP 15/12/2027"))
            override val isAvailable: Boolean = true
        }
        val crashReporter = object : app.memora.domain.logging.CrashReporter {
            override fun recordException(throwable: Throwable) {}
            override fun log(message: String) {}
            override fun setUserId(userId: String) {}
            override fun setCustomKey(key: String, value: String) {}
        }
        val viewModel = CaptureViewModel(
            processCapturedImageUseCase = ProcessCapturedImageUseCase(imageStorage),
            imageStorage = imageStorage,
            createMemoryUseCase = CreateMemoryUseCase(memoryRepo),
            deleteMemoryUseCase = DeleteMemoryUseCase(memoryRepo),
            notificationScheduler = scheduler,
            extractLocallyUseCase = ExtractLocallyUseCase(textExtractor, DeterministicMemoryExtractor()),
            refineWithGeminiUseCase = RefineWithGeminiUseCase(DeterministicMemoryExtractor()),
            crashReporter = crashReporter,
            analyticsLogger = FakeAnalyticsLogger(),
            onboardingPreferences = prefs,
        )

        // 1. cancelCapture in ExtractionReady must delete temporary image
        val tempImg = CapturedImage("file:///captures/temp_scan.jpg", CaptureSource.CAMERA, Instant.now(), "image/jpeg")
        viewModel.processLocalExtraction(tempImg).join()
        viewModel.skipGeminiAndProceed()
        assertTrue(viewModel.uiState.value is app.memora.presentation.capture.CaptureUiState.ExtractionReady)
        viewModel.cancelCapture()
        assertTrue("cancelCapture must delete active temporary image", deletedUris.contains("file:///captures/temp_scan.jpg"))

        // 2. Manual entry save (uri == "") must NOT increment monthly scan count
        viewModel.startManualEntry(CapturedImage("", CaptureSource.CAMERA, Instant.now(), "image/jpeg"))
        viewModel.saveMemory(
            title = "Manual Item",
            eventType = EventType.EXPIRATION,
            eventDate = LocalDate.now().plusDays(10),
            remindDaysBefore = 1,
        ).join()
        assertEquals("Manual entry without image must not deduct scan quota", 0, scanCount)

        // 3. Save failure must preserve ExtractionReady as returnState
        val secondImg = CapturedImage("file:///captures/temp_scan_2.jpg", CaptureSource.CAMERA, Instant.now(), "image/jpeg")
        viewModel.processLocalExtraction(secondImg).join()
        viewModel.skipGeminiAndProceed()
        shouldFailCreate = true
        viewModel.saveMemory(
            title = "Milk",
            eventType = EventType.EXPIRATION,
            eventDate = LocalDate.now().plusDays(10),
            remindDaysBefore = 1,
        ).join()
        val saveErrState = viewModel.uiState.value as app.memora.presentation.capture.CaptureUiState.Error
        assertTrue(
            "Save failure returnState must preserve ExtractionReady so user does not lose their scan",
            saveErrState.returnState is app.memora.presentation.capture.CaptureUiState.ExtractionReady
        )

        // 4. Free user (isProUser=false) must be blocked from AI refinement and fall back to LOCAL_OCR
        val aiBlockedImg = CapturedImage("file:///captures/temp_scan_3.jpg", CaptureSource.CAMERA, Instant.now(), "image/jpeg")
        viewModel.processLocalExtraction(aiBlockedImg).join()
        viewModel.confirmLocalExtraction()?.join()
        val afterAiAttempt = viewModel.uiState.value as app.memora.presentation.capture.CaptureUiState.ExtractionReady
        assertEquals(
            "Free user confirmLocalExtraction must fall back to LOCAL_OCR without invoking Pro AI",
            app.memora.domain.extraction.ExtractionSource.LOCAL_OCR,
            afterAiAttempt.result.candidate.extractionSource
        )
    }
}
