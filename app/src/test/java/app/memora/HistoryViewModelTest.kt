package app.memora

import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import app.memora.domain.usecase.CompleteReminderUseCase
import app.memora.domain.usecase.GetMemoryUseCase
import app.memora.domain.usecase.ObserveMemoriesUseCase
import app.memora.domain.usecase.UncompleteReminderUseCase
import app.memora.platform.notification.NotificationScheduler
import app.memora.presentation.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var memoryRepository: FakeMemoryRepository
    private lateinit var reminderRepository: FakeReminderRepository
    private lateinit var observeMemoriesUseCase: ObserveMemoriesUseCase
    private lateinit var uncompleteReminderUseCase: UncompleteReminderUseCase
    private lateinit var completeReminderUseCase: CompleteReminderUseCase
    private lateinit var getMemoryUseCase: GetMemoryUseCase
    private lateinit var fakeNotificationScheduler: FakeNotificationScheduler

    private val now = Instant.parse("2026-09-23T10:00:00Z")
    private val today = LocalDate.of(2026, 9, 23)

    private val fixedClock = object : ClockProvider {
        override fun now(): Instant = now
        override fun today(zoneId: ZoneId): LocalDate = today
    }

    class FakeNotificationScheduler : NotificationScheduler {
        val scheduledReminders = mutableListOf<String>()
        val cancelledReminders = mutableListOf<String>()

        override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String): Result<Unit> {
            scheduledReminders.add(reminderId)
            return Result.success(Unit)
        }

        override suspend fun scheduleReminder(
            memoryId: String,
            reminderId: String,
            title: String,
            reminderStartDate: LocalDate,
            eventDate: LocalDate,
            timeZoneId: String,
            reminderTime: LocalTime
        ): Result<Unit> {
            scheduledReminders.add(reminderId)
            return Result.success(Unit)
        }

        override suspend fun cancelReminder(reminderId: String): Result<Unit> {
            cancelledReminders.add(reminderId)
            return Result.success(Unit)
        }

        override val areNotificationsEnabled: Boolean = true
    }

    class FakeMemoryRepository : MemoryRepository {
        val memories = mutableMapOf<String, Memory>()
        val reminders = mutableMapOf<String, Reminder>()
        private val flow = MutableStateFlow<List<MemoryWithReminders>>(emptyList())

        fun updateFlow() {
            flow.value = memories.values.map { mem ->
                MemoryWithReminders(mem, reminders.values.filter { it.memoryId == mem.id })
            }
        }

        override fun observeMemories(): Flow<List<MemoryWithReminders>> = flow
        override suspend fun getMemory(id: String): Memory? = memories[id]
        override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = memories[id]?.let {
            MemoryWithReminders(it, reminders.values.filter { r -> r.memoryId == id })
        }
        override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> {
            memories[memory.id] = memory
            if (initialReminder != null) reminders[initialReminder.id] = initialReminder
            updateFlow()
            return Result.success(Unit)
        }
        override suspend fun updateMemory(memory: Memory): Result<Unit> {
            memories[memory.id] = memory
            updateFlow()
            return Result.success(Unit)
        }
        override suspend fun deleteMemory(id: String): Result<Unit> {
            memories.remove(id)
            reminders.values.removeAll { it.memoryId == id }
            updateFlow()
            return Result.success(Unit)
        }
    }

    class FakeReminderRepository(
        private val memoryRepo: FakeMemoryRepository
    ) : ReminderRepository {
        val reminders get() = memoryRepo.reminders

        override fun observeReminders(): Flow<List<Reminder>> = flowOf(reminders.values.toList())
        override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> = flowOf(reminders.values.filter { it.memoryId == memoryId })
        override suspend fun getReminder(id: String): Reminder? = reminders[id]
        override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> = reminders.values.filter { it.memoryId == memoryId }
        override suspend fun createReminder(reminder: Reminder): Result<Unit> {
            reminders[reminder.id] = reminder
            memoryRepo.updateFlow()
            return Result.success(Unit)
        }
        override suspend fun updateReminder(reminder: Reminder): Result<Unit> {
            reminders[reminder.id] = reminder
            memoryRepo.updateFlow()
            return Result.success(Unit)
        }
        override suspend fun completeReminder(id: String, completedAt: Instant): Result<Unit> {
            val existing = reminders[id] ?: return Result.failure(RuntimeException("Not found"))
            reminders[id] = existing.copy(completedAt = completedAt, status = ReminderStatus.COMPLETED)
            memoryRepo.updateFlow()
            return Result.success(Unit)
        }
        override suspend fun deleteReminder(id: String): Result<Unit> {
            reminders.remove(id)
            memoryRepo.updateFlow()
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        memoryRepository = FakeMemoryRepository()
        reminderRepository = FakeReminderRepository(memoryRepository)

        val calculateReminderStatusUseCase = CalculateReminderStatusUseCase(fixedClock)
        observeMemoriesUseCase = ObserveMemoriesUseCase(memoryRepository, calculateReminderStatusUseCase)
        uncompleteReminderUseCase = UncompleteReminderUseCase(reminderRepository)
        completeReminderUseCase = CompleteReminderUseCase(reminderRepository, fixedClock)
        getMemoryUseCase = GetMemoryUseCase(memoryRepository, calculateReminderStatusUseCase)
        fakeNotificationScheduler = FakeNotificationScheduler()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun historyUiState_filtersAndDisplaysOnlyCompletedReminders() = testScope.runTest {
        // 1. Create an active memory
        val activeMemory = Memory(
            id = "mem-active",
            title = "Active Bill",
            eventType = EventType.DUE_DATE,
            eventDate = today.plusDays(3),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val activeReminder = Reminder(
            id = "rem-active",
            memoryId = "mem-active",
            reminderStartDate = today.plusDays(1),
            status = ReminderStatus.UPCOMING,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(activeMemory, activeReminder)

        // 2. Create a completed memory
        val completedMemory = Memory(
            id = "mem-completed",
            title = "Completed Warranty",
            eventType = EventType.WARRANTY_END,
            eventDate = today.plusDays(10),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val completedReminder = Reminder(
            id = "rem-completed",
            memoryId = "mem-completed",
            reminderStartDate = today.plusDays(2),
            status = ReminderStatus.COMPLETED,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(completedMemory, completedReminder)

        val viewModel = HistoryViewModel(
            observeMemoriesUseCase = observeMemoriesUseCase,
            uncompleteReminderUseCase = uncompleteReminderUseCase,
            completeReminderUseCase = completeReminderUseCase,
            getMemoryUseCase = getMemoryUseCase,
            notificationScheduler = fakeNotificationScheduler,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.isLoading && it.completedReminders.isNotEmpty() }
        assertEquals(1, state.completedReminders.size)
        assertEquals("mem-completed", state.completedReminders.first().memory.id)
        assertEquals(ReminderStatus.COMPLETED, state.completedReminders.first().effectiveStatus)
    }

    @Test
    fun restoreReminder_uncompletesReminderAndReschedulesNotification() = testScope.runTest {
        val completedMemory = Memory(
            id = "mem-1",
            title = "Expired Insurance",
            eventType = EventType.EXPIRATION,
            eventDate = today.plusDays(5),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val completedReminder = Reminder(
            id = "rem-1",
            memoryId = "mem-1",
            reminderStartDate = today.plusDays(2),
            status = ReminderStatus.COMPLETED,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(completedMemory, completedReminder)

        val viewModel = HistoryViewModel(
            observeMemoriesUseCase = observeMemoriesUseCase,
            uncompleteReminderUseCase = uncompleteReminderUseCase,
            completeReminderUseCase = completeReminderUseCase,
            getMemoryUseCase = getMemoryUseCase,
            notificationScheduler = fakeNotificationScheduler,
        )

        viewModel.restoreReminder("rem-1")
        advanceUntilIdle()

        // Verify reminder is restored in repository
        val restored = reminderRepository.getReminder("rem-1")
        assertNull("completedAt should be cleared", restored?.completedAt)

        // Verify notificationScheduler rescheduled it
        assertTrue("Notification should be rescheduled", fakeNotificationScheduler.scheduledReminders.contains("rem-1"))
    }

    @Test
    fun completeReminder_undoAction_completesReminderAndCancelsNotification() = testScope.runTest {
        val activeMemory = Memory(
            id = "mem-2",
            title = "Tax Filing",
            eventType = EventType.DUE_DATE,
            eventDate = today.plusDays(5),
            timeZoneId = "UTC",
            createdAt = now,
            updatedAt = now,
        )
        val activeReminder = Reminder(
            id = "rem-2",
            memoryId = "mem-2",
            reminderStartDate = today.plusDays(2),
            status = ReminderStatus.UPCOMING,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        memoryRepository.createMemory(activeMemory, activeReminder)

        val viewModel = HistoryViewModel(
            observeMemoriesUseCase = observeMemoriesUseCase,
            uncompleteReminderUseCase = uncompleteReminderUseCase,
            completeReminderUseCase = completeReminderUseCase,
            getMemoryUseCase = getMemoryUseCase,
            notificationScheduler = fakeNotificationScheduler,
        )

        // User undoes the restore -> re-completes the reminder
        viewModel.completeReminder("rem-2")
        advanceUntilIdle()

        val completed = reminderRepository.getReminder("rem-2")
        assertEquals(ReminderStatus.COMPLETED, completed?.status)
        assertTrue("Notification should be cancelled", fakeNotificationScheduler.cancelledReminders.contains("rem-2"))
    }
}
