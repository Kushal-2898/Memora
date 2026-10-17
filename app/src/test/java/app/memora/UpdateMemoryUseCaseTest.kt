package app.memora

import app.memora.domain.model.EventType
import app.memora.domain.model.MemoraError
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.usecase.UpdateMemoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class UpdateMemoryUseCaseTest {

    private class FakeMemoryRepository : MemoryRepository {
        var updatedMemory: Memory? = null
        var updatedReminders: List<Reminder>? = null
        var memoryWithRemindersToReturn: MemoryWithReminders? = null

        override fun observeMemories(): Flow<List<MemoryWithReminders>> = emptyFlow()
        override suspend fun getMemory(id: String): Memory? = updatedMemory
        override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = memoryWithRemindersToReturn
        override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> = Result.success(Unit)
        override suspend fun updateMemory(memory: Memory): Result<Unit> = Result.success(Unit)
        override suspend fun deleteMemory(id: String): Result<Unit> = Result.success(Unit)

        override suspend fun updateMemoryWithReminders(memory: Memory, reminders: List<Reminder>): Result<Unit> {
            updatedMemory = memory
            updatedReminders = reminders
            return Result.success(Unit)
        }
    }

    private class FakeReminderRepository : ReminderRepository {
        override fun observeReminders(): Flow<List<Reminder>> = emptyFlow()
        override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> = emptyFlow()
        override suspend fun getReminder(id: String): Reminder? = null
        override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> = emptyList()
        override suspend fun createReminder(reminder: Reminder): Result<Unit> = Result.success(Unit)
        override suspend fun updateReminder(reminder: Reminder): Result<Unit> = Result.success(Unit)
        override suspend fun completeReminder(id: String, completedAt: Instant): Result<Unit> = Result.success(Unit)
        override suspend fun deleteReminder(id: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `fails validation when title is blank`() = runTest {
        val memoryRepo = FakeMemoryRepository()
        val reminderRepo = FakeReminderRepository()
        val useCase = UpdateMemoryUseCase(memoryRepo, reminderRepo)

        val memory = Memory(
            id = "mem1",
            title = "Existing Title",
            eventType = EventType.EXPIRATION,
            eventDate = LocalDate.of(2026, 12, 1),
            timeZoneId = "UTC",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val result = useCase(memory, "", EventType.EXPIRATION, LocalDate.of(2026, 12, 1))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MemoraError.Validation)
    }

    @Test
    fun `atomically updates memory and adjusts reminder dates proportionally`() = runTest {
        val memoryRepo = FakeMemoryRepository()
        val reminderRepo = FakeReminderRepository()
        val useCase = UpdateMemoryUseCase(memoryRepo, reminderRepo)

        val initialEventDate = LocalDate.of(2026, 12, 1)
        val initialReminderDate = LocalDate.of(2026, 11, 24) // 7 days before

        val memory = Memory(
            id = "mem1",
            title = "Milk Carton",
            eventType = EventType.EXPIRATION,
            eventDate = initialEventDate,
            timeZoneId = "UTC",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val reminder = Reminder(
            id = "rem1",
            memoryId = "mem1",
            reminderStartDate = initialReminderDate,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        memoryRepo.memoryWithRemindersToReturn = MemoryWithReminders(memory, listOf(reminder))

        val newEventDate = LocalDate.of(2026, 12, 11) // +10 days
        val result = useCase(memory, "Milk Carton Fresh", EventType.BEST_BEFORE, newEventDate)

        assertTrue(result.isSuccess)
        assertEquals("Milk Carton Fresh", memoryRepo.updatedMemory?.title)
        assertEquals(EventType.BEST_BEFORE, memoryRepo.updatedMemory?.eventType)
        assertEquals(newEventDate, memoryRepo.updatedMemory?.eventDate)

        // Reminder should also be shifted +10 days: 2026-11-24 + 10 days = 2026-12-04
        val updatedReminder = memoryRepo.updatedReminders?.firstOrNull()
        assertEquals(LocalDate.of(2026, 12, 4), updatedReminder?.reminderStartDate)
    }
}
