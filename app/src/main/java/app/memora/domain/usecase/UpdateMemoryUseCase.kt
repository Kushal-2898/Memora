package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class UpdateMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val reminderRepository: ReminderRepository,
) {
    suspend operator fun invoke(
        memory: Memory,
        newTitle: String,
        newEventType: app.memora.domain.model.EventType,
        newEventDate: java.time.LocalDate
    ): Result<Unit> {
        if (newTitle.isBlank()) return Result.failure(MemoraError.Validation("Title cannot be blank."))

        val daysDifference = ChronoUnit.DAYS.between(memory.eventDate, newEventDate)
        
        val updatedMemory = memory.copy(
            title = newTitle,
            eventType = newEventType,
            eventDate = newEventDate,
            updatedAt = Instant.now()
        )

        val memoryWithReminders = memoryRepository.getMemoryWithReminders(memory.id)
        val updatedReminders = memoryWithReminders?.reminders?.map { reminder ->
            val newReminderDate = reminder.reminderStartDate.plusDays(daysDifference)
            reminder.copy(
                reminderStartDate = newReminderDate,
                updatedAt = Instant.now()
            )
        }.orEmpty()

        return memoryRepository.updateMemoryWithReminders(updatedMemory, updatedReminders)
    }
}
