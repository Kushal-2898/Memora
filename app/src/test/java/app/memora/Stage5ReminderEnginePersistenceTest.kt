package app.memora

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.memora.data.local.MemoraDatabase
import app.memora.data.local.MemoryDao
import app.memora.data.local.ReminderDao
import app.memora.data.repository.DefaultMemoryRepository
import app.memora.data.repository.DefaultReminderRepository
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.scheduler.ClockProvider
import kotlinx.coroutines.asExecutor
import app.memora.platform.notification.NotificationScheduler
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import app.memora.domain.usecase.CompleteReminderUseCase
import app.memora.domain.usecase.UncompleteReminderUseCase
import app.memora.domain.usecase.CreateMemoryUseCase
import app.memora.domain.usecase.CreateReminderUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.GetMemoryUseCase
import app.memora.domain.usecase.ObserveMemoriesUseCase
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.platform.image.ImageStorage
import app.memora.presentation.capture.CaptureNavEvent
import app.memora.presentation.capture.CaptureViewModel
import app.memora.presentation.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stage 5 Verification Test Suite:
 * - saveMemory creates Memory + Reminder in Room
 * - reminderStartDate calculation (eventDate.minusDays(remindDaysBefore))
 * - all 5 status transitions (UPCOMING, REMINDER_ACTIVE, DUE_TODAY, OVERDUE, COMPLETED)
 * - completing an OVERDUE item transitions to COMPLETED
 * - deleting a memory cascades to its reminders
 * - temporary image deletion and navigation to Home
 * - HomeViewModel correctly groups into 5 status categories
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage5ReminderEnginePersistenceTest {

    private class FakeNotificationScheduler : NotificationScheduler {
        val cancelled = mutableListOf<String>()
        val scheduled = mutableListOf<String>()
        override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String): Result<Unit> { scheduled += reminderId; return Result.success(Unit) }
        override suspend fun scheduleReminder(memoryId: String, reminderId: String, title: String, reminderStartDate: LocalDate, eventDate: LocalDate, timeZoneId: String, reminderTime: java.time.LocalTime): Result<Unit> { scheduled += reminderId; return Result.success(Unit) }
        override suspend fun cancelReminder(reminderId: String): Result<Unit> { cancelled += reminderId; return Result.success(Unit) }
        override val areNotificationsEnabled: Boolean = true
    }


    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var database: MemoraDatabase
    private lateinit var memoryDao: MemoryDao
    private lateinit var reminderDao: ReminderDao
    private lateinit var memoryRepository: DefaultMemoryRepository
    private lateinit var reminderRepository: DefaultReminderRepository

    private lateinit var createMemoryUseCase: CreateMemoryUseCase
    private lateinit var createReminderUseCase: CreateReminderUseCase
    private lateinit var completeReminderUseCase: CompleteReminderUseCase
    private lateinit var deleteMemoryUseCase: DeleteMemoryUseCase
    private lateinit var observeMemoriesUseCase: ObserveMemoriesUseCase
    private lateinit var calculateUseCase: CalculateReminderStatusUseCase

    private lateinit var fakeImageStorage: TestFakeImageStorage
    private lateinit var processCapturedImageUseCase: ProcessCapturedImageUseCase

    private class TestFixedClockProvider(
        var currentDate: LocalDate,
        var currentInstant: Instant = Instant.parse("2026-09-05T12:00:00Z"),
    ) : ClockProvider {
        override fun now(): Instant = currentInstant
        override fun today(zoneId: ZoneId): LocalDate = currentDate
    }

    private class TestFakeImageStorage : ImageStorage {
        var deletedUri: String? = null
        var lastCreatedFile: File? = null

        override fun createCaptureFile(prefix: String): File {
            val file = File.createTempFile(prefix, ".jpg")
            lastCreatedFile = file
            return file
        }

        override suspend fun importFromUri(uriString: String, source: CaptureSource): Result<CapturedImage> {
            return Result.success(
                CapturedImage(
                    uri = uriString,
                    source = source,
                    capturedAt = Instant.now(),
                    mimeType = "image/jpeg",
                    fileSize = 1024L,
                )
            )
        }

        override fun createCapturedImageFromFile(file: File, source: CaptureSource): CapturedImage {
            return CapturedImage(
                uri = file.toURI().toString(),
                source = source,
                capturedAt = Instant.now(),
                mimeType = "image/jpeg",
                fileSize = file.length(),
            )
        }

        override fun deleteImage(uriString: String): Boolean {
            deletedUri = uriString
            return true
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MemoraDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(testDispatcher.asExecutor())
            .setQueryExecutor(testDispatcher.asExecutor())
            .build()
        memoryDao = database.memoryDao()
        reminderDao = database.reminderDao()

        memoryRepository = DefaultMemoryRepository(memoryDao, reminderDao, testDispatcher)
        reminderRepository = DefaultReminderRepository(reminderDao, memoryDao, testDispatcher)

        val clock = TestFixedClockProvider(LocalDate.of(2026, 9, 5))
        calculateUseCase = CalculateReminderStatusUseCase(clock)
        createMemoryUseCase = CreateMemoryUseCase(memoryRepository)
        createReminderUseCase = CreateReminderUseCase(reminderRepository, memoryRepository)
        completeReminderUseCase = CompleteReminderUseCase(reminderRepository, clock)
        deleteMemoryUseCase = DeleteMemoryUseCase(memoryRepository)
        observeMemoriesUseCase = ObserveMemoriesUseCase(memoryRepository, calculateUseCase)

        fakeImageStorage = TestFakeImageStorage()
        processCapturedImageUseCase = ProcessCapturedImageUseCase(fakeImageStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun `saveMemory creates Memory and Reminder in Room, deletes temporary image, and navigates to Home`() = runTest(testDispatcher) {
        val fakeNotificationScheduler = FakeNotificationScheduler()
        val viewModel = CaptureViewModel(
            processCapturedImageUseCase = processCapturedImageUseCase,
            imageStorage = fakeImageStorage,
            createMemoryUseCase = createMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            notificationScheduler = fakeNotificationScheduler,
            extractCapturedImageUseCase = app.memora.domain.usecase.ExtractCapturedImageUseCase(
                textExtractor = object : app.memora.platform.ocr.TextExtractor {
                    override suspend fun extractText(imageUri: String) = Result.success(app.memora.platform.ocr.ExtractedText(""))
                    override val isAvailable: Boolean = true
                },
                memoryExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor(),
            ),
            crashReporter = FakeCrashReporter(),
            analyticsLogger = FakeAnalyticsLogger(),
        )

        // 1. Simulate image capture
        val tempFile = File.createTempFile("capture_test", ".jpg")
        tempFile.writeText("fake image data")
        viewModel.onPhotoCaptured(tempFile)

        val activeImage = viewModel.activeTemporaryImage
        assertNotNull("Active temporary image should exist after capture", activeImage)
        val imageUri = activeImage?.uri

        // Collect navigation event
        var navEventReceived: CaptureNavEvent? = null
        backgroundScope.launch(testDispatcher) {
            viewModel.navigationEvents.collect { navEventReceived = it }
        }

        // 2. Save Memory via saveMemory
        val eventDate = LocalDate.of(2026, 9, 20)
        val remindDaysBefore = 3
        val saveJob = viewModel.saveMemory(
            title = "Car Insurance Renewal",
            eventType = EventType.EXPIRATION,
            eventDate = eventDate,
            remindDaysBefore = remindDaysBefore,
        )
        saveJob.join()
        advanceUntilIdle()

        // 3. Verify Memory and Reminder exist in Room
        val memories = memoryRepository.observeMemories().first { list ->
            list.isNotEmpty() && list.first().reminders.isNotEmpty()
        }
        assertEquals(1, memories.size)
        val savedItem = memories.first()
        assertEquals("Car Insurance Renewal", savedItem.memory.title)
        assertEquals(EventType.EXPIRATION, savedItem.memory.eventType)
        assertEquals(eventDate, savedItem.memory.eventDate)

        assertEquals(1, savedItem.reminders.size)
        val savedReminder = savedItem.reminders.first()
        val expectedReminderStartDate = eventDate.minusDays(remindDaysBefore.toLong())
        assertEquals(expectedReminderStartDate, savedReminder.reminderStartDate)
        assertEquals(LocalDate.of(2026, 9, 17), savedReminder.reminderStartDate)

        // 4. Verify temporary image was deleted via imageStorage
        assertEquals(imageUri, fakeImageStorage.deletedUri)

        // 5. Verify navigation event was emitted
        assertEquals(CaptureNavEvent.NavigateToHome, navEventReceived)

        tempFile.delete()
    }

    @Test
    fun `reminderStartDate calculation correctly computes eventDate minus remindDaysBefore`() = runTest(testDispatcher) {
        val eventDate = LocalDate.of(2026, 10, 15)

        val testCases = listOf(
            0 to LocalDate.of(2026, 10, 15),
            1 to LocalDate.of(2026, 10, 14),
            2 to LocalDate.of(2026, 10, 13),
            3 to LocalDate.of(2026, 10, 12),
            7 to LocalDate.of(2026, 10, 8),
            14 to LocalDate.of(2026, 10, 1),
        )

        for ((daysBefore, expectedStartDate) in testCases) {
            val memoryId = createMemoryUseCase(
                title = "Test for $daysBefore days",
                eventType = EventType.OTHER,
                eventDate = eventDate,
            ).getOrThrow()

            val reminderStartDate = eventDate.minusDays(daysBefore.toLong())
            assertEquals(expectedStartDate, reminderStartDate)

            val reminderId = createReminderUseCase(
                memoryId = memoryId,
                reminderStartDate = reminderStartDate,
            ).getOrThrow()

            val reminder = reminderRepository.getReminder(reminderId)
            assertNotNull(reminder)
            assertEquals(expectedStartDate, reminder?.reminderStartDate)
        }
    }

    @Test
    fun `all 5 status transitions are evaluated deterministically`() {
        val eventDate = LocalDate.of(2026, 9, 10)
        val reminderStartDate = LocalDate.of(2026, 9, 7)
        val fixedInstant = Instant.parse("2026-09-01T12:00:00Z")

        val clock = TestFixedClockProvider(LocalDate.of(2026, 9, 5), fixedInstant)
        val calculateUseCase = CalculateReminderStatusUseCase(clock)

        val memory = Memory(
            id = "mem-test-5",
            title = "Status Transition Test",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            timeZoneId = "UTC",
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        )
        val reminder = Reminder(
            id = "rem-test-5",
            memoryId = "mem-test-5",
            reminderStartDate = reminderStartDate,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
        )

        // 1. UPCOMING: today < reminderStartDate (Sep 6)
        clock.currentDate = LocalDate.of(2026, 9, 6)
        assertEquals(ReminderStatus.UPCOMING, calculateUseCase(memory, reminder))

        // 2. REMINDER_ACTIVE: reminderStartDate <= today < eventDate (Sep 7, Sep 8, Sep 9)
        clock.currentDate = LocalDate.of(2026, 9, 7)
        assertEquals(ReminderStatus.REMINDER_ACTIVE, calculateUseCase(memory, reminder))

        clock.currentDate = LocalDate.of(2026, 9, 9)
        assertEquals(ReminderStatus.REMINDER_ACTIVE, calculateUseCase(memory, reminder))

        // 3. DUE_TODAY: today == eventDate (Sep 10)
        clock.currentDate = LocalDate.of(2026, 9, 10)
        assertEquals(ReminderStatus.DUE_TODAY, calculateUseCase(memory, reminder))

        // 4. OVERDUE: today > eventDate (Sep 11)
        clock.currentDate = LocalDate.of(2026, 9, 11)
        assertEquals(ReminderStatus.OVERDUE, calculateUseCase(memory, reminder))

        // 5. COMPLETED: completedAt is not null
        val completedReminder = reminder.copy(completedAt = Instant.parse("2026-09-11T14:00:00Z"))
        assertEquals(ReminderStatus.COMPLETED, calculateUseCase(memory, completedReminder))
    }

    @Test
    fun `completing an OVERDUE item transitions it to COMPLETED`() = runTest(testDispatcher) {
        val eventDate = LocalDate.of(2026, 9, 1)
        val reminderStartDate = LocalDate.of(2026, 8, 25)
        val today = LocalDate.of(2026, 9, 5) // past eventDate -> OVERDUE

        val clock = TestFixedClockProvider(today)
        val calculateUseCase = CalculateReminderStatusUseCase(clock)

        val memoryId = createMemoryUseCase(
            title = "Overdue Bill",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
        ).getOrThrow()

        val reminderId = createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = reminderStartDate,
        ).getOrThrow()

        // Verify it is OVERDUE before completion
        var memoryWithReminders = memoryRepository.getMemoryWithReminders(memoryId)
        assertNotNull(memoryWithReminders)
        val overdueReminder = memoryWithReminders!!.reminders.first()
        val statusBefore = calculateUseCase(memoryWithReminders.memory, overdueReminder)
        assertEquals(ReminderStatus.OVERDUE, statusBefore)

        // Complete the reminder
        val completeResult = completeReminderUseCase(reminderId)
        assertTrue(completeResult.isSuccess)

        // Verify it is now COMPLETED
        memoryWithReminders = memoryRepository.getMemoryWithReminders(memoryId)
        assertNotNull(memoryWithReminders)
        val completedReminder = memoryWithReminders!!.reminders.first()
        val statusAfter = calculateUseCase(memoryWithReminders.memory, completedReminder)
        assertEquals(ReminderStatus.COMPLETED, statusAfter)
        assertNotNull(completedReminder.completedAt)
    }

    @Test
    fun `deleting a memory cascades and deletes all associated reminders in Room`() = runTest(testDispatcher) {
        val memoryId = createMemoryUseCase(
            title = "Memory with Reminders",
            eventType = EventType.OTHER,
            eventDate = LocalDate.of(2026, 10, 1),
        ).getOrThrow()

        val reminder1Id = createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = LocalDate.of(2026, 9, 25),
        ).getOrThrow()

        val reminder2Id = createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = LocalDate.of(2026, 9, 28),
        ).getOrThrow()

        assertEquals(2, reminderRepository.getRemindersForMemory(memoryId).size)

        // Delete parent memory
        val deleteResult = deleteMemoryUseCase(memoryId)
        assertTrue(deleteResult.isSuccess)

        // Verify parent memory is gone
        assertNull(memoryRepository.getMemory(memoryId))

        // Verify child reminders are cascade-deleted
        assertTrue(reminderRepository.getRemindersForMemory(memoryId).isEmpty())
        assertNull(reminderRepository.getReminder(reminder1Id))
        assertNull(reminderRepository.getReminder(reminder2Id))
    }

    @Test
    fun `HomeViewModel groups memories into 5 distinct statuses`() = runTest(testDispatcher) {
        val today = LocalDate.of(2026, 9, 10)
        val clock = TestFixedClockProvider(today)
        val calculateUseCase = CalculateReminderStatusUseCase(clock)

        // 1. Upcoming: reminderStartDate in the future (e.g. Sep 12, event Sep 15)
        val memUpcomingId = createMemoryUseCase(title = "Upcoming Item", eventType = EventType.OTHER, eventDate = LocalDate.of(2026, 9, 15)).getOrThrow()
        createReminderUseCase(memoryId = memUpcomingId, reminderStartDate = LocalDate.of(2026, 9, 12)).getOrThrow()

        // 2. Active: reminderStartDate in past/today, event in future (reminder Sep 8, event Sep 12)
        val memActiveId = createMemoryUseCase(title = "Active Item", eventType = EventType.OTHER, eventDate = LocalDate.of(2026, 9, 12)).getOrThrow()
        createReminderUseCase(memoryId = memActiveId, reminderStartDate = LocalDate.of(2026, 9, 8)).getOrThrow()

        // 3. Due Today: event today (Sep 10)
        val memDueId = createMemoryUseCase(title = "Due Today Item", eventType = EventType.DUE_DATE, eventDate = LocalDate.of(2026, 9, 10)).getOrThrow()
        createReminderUseCase(memoryId = memDueId, reminderStartDate = LocalDate.of(2026, 9, 5)).getOrThrow()

        // 4. Overdue: event in past (Sep 8)
        val memOverdueId = createMemoryUseCase(title = "Overdue Item", eventType = EventType.DUE_DATE, eventDate = LocalDate.of(2026, 9, 8)).getOrThrow()
        createReminderUseCase(memoryId = memOverdueId, reminderStartDate = LocalDate.of(2026, 9, 1)).getOrThrow()

        // 5. Completed: completeReminder called
        val memCompletedId = createMemoryUseCase(title = "Completed Item", eventType = EventType.OTHER, eventDate = LocalDate.of(2026, 9, 20)).getOrThrow()
        val remCompletedId = createReminderUseCase(memoryId = memCompletedId, reminderStartDate = LocalDate.of(2026, 9, 5)).getOrThrow()
        completeReminderUseCase(remCompletedId)

        val fakeNotificationScheduler = FakeNotificationScheduler()
        val homeViewModel = HomeViewModel(
            observeMemoriesUseCase = observeMemoriesUseCase,
            calculateReminderStatusUseCase = calculateUseCase,
            completeReminderUseCase = completeReminderUseCase,
            uncompleteReminderUseCase = UncompleteReminderUseCase(reminderRepository),
            deleteMemoryUseCase = deleteMemoryUseCase,
            getMemoryUseCase = GetMemoryUseCase(memoryRepository, calculateUseCase),
            notificationScheduler = fakeNotificationScheduler,
        )

        val uiState = homeViewModel.uiState.first { !it.isEmpty }

        assertEquals(1, uiState.upcomingReminders.size)
        assertEquals("Upcoming Item", uiState.upcomingReminders.first().memory.title)

        assertEquals(1, uiState.activeReminders.size)
        assertEquals("Active Item", uiState.activeReminders.first().memory.title)

        assertEquals(1, uiState.dueTodayReminders.size)
        assertEquals("Due Today Item", uiState.dueTodayReminders.first().memory.title)

        assertEquals(1, uiState.overdueReminders.size)
        assertEquals("Overdue Item", uiState.overdueReminders.first().memory.title)

        assertEquals(1, uiState.completedReminders.size)
        assertEquals("Completed Item", uiState.completedReminders.first().memory.title)
    }
    @Test
    fun `HomeViewModel completion cancels notification scheduling`() = runTest(testDispatcher) {
        val memoryId = createMemoryUseCase(
            title = "Cancel Notification",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 9, 20),
        ).getOrThrow()
        val reminderId = createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = LocalDate.of(2026, 9, 17),
        ).getOrThrow()
        val fakeNotificationScheduler = FakeNotificationScheduler()
        val homeViewModel = HomeViewModel(
            observeMemoriesUseCase = observeMemoriesUseCase,
            calculateReminderStatusUseCase = CalculateReminderStatusUseCase(TestFixedClockProvider(LocalDate.of(2026, 9, 10))),
            completeReminderUseCase = completeReminderUseCase,
            uncompleteReminderUseCase = UncompleteReminderUseCase(reminderRepository),
            deleteMemoryUseCase = deleteMemoryUseCase,
            getMemoryUseCase = GetMemoryUseCase(memoryRepository, calculateUseCase),
            notificationScheduler = fakeNotificationScheduler,
        )

        val result = completeReminderUseCase(reminderId)
        assertTrue("completeReminderUseCase failed: $result", result.isSuccess)
        homeViewModel.completeReminder(reminderId)
        advanceUntilIdle()

        val completedReminder = reminderRepository.getReminder(reminderId)
        assertNotNull("completedAt should not be null", completedReminder?.completedAt)
        assertEquals("Reminder status should be COMPLETED but was ${completedReminder?.status}", ReminderStatus.COMPLETED, completedReminder?.status)
        assertTrue("Notification should be cancelled", fakeNotificationScheduler.cancelled.contains(reminderId))
    }

    @Test
    fun `HomeViewModel undoCompleteReminder restores completed reminder and reschedules notification`() = runTest(testDispatcher) {
        val memoryId = createMemoryUseCase(
            title = "Test Undo Item",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 9, 20),
        ).getOrThrow()
        val reminderId = createReminderUseCase(
            memoryId = memoryId,
            reminderStartDate = LocalDate.of(2026, 9, 17),
        ).getOrThrow()
        val fakeNotificationScheduler = FakeNotificationScheduler()
        val homeViewModel = HomeViewModel(
            observeMemoriesUseCase = observeMemoriesUseCase,
            calculateReminderStatusUseCase = CalculateReminderStatusUseCase(TestFixedClockProvider(LocalDate.of(2026, 9, 10))),
            completeReminderUseCase = completeReminderUseCase,
            uncompleteReminderUseCase = UncompleteReminderUseCase(reminderRepository),
            deleteMemoryUseCase = deleteMemoryUseCase,
            getMemoryUseCase = GetMemoryUseCase(memoryRepository, calculateUseCase),
            notificationScheduler = fakeNotificationScheduler,
        )

        // Complete it first
        homeViewModel.completeReminder(reminderId)
        advanceUntilIdle()
        val completedReminder = reminderRepository.getReminder(reminderId)
        assertEquals(ReminderStatus.COMPLETED, completedReminder?.status)

        // Undo completion
        homeViewModel.undoCompleteReminder(reminderId)
        advanceUntilIdle()

        val restoredReminder = reminderRepository.getReminder(reminderId)
        assertNull("completedAt should be null after undo", restoredReminder?.completedAt)
        assertEquals(ReminderStatus.UPCOMING, restoredReminder?.status)
        assertTrue("Notification should be rescheduled", fakeNotificationScheduler.scheduled.contains(reminderId))
    }
}
