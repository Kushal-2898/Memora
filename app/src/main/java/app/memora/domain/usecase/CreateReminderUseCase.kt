package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import java.time.LocalDate

/**
 * Creates and persists a [Reminder] configuration referencing an existing [app.memora.domain.model.Memory].
 */
class CreateReminderUseCase(
    private val reminderRepository: ReminderRepository,
    private val memoryRepository: MemoryRepository
) {

    suspend operator fun invoke(reminder: Reminder, knownEventDate: LocalDate? = null): Result<Unit> {
        if (reminder.id.isBlank()) {
            return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
        }
        if (reminder.memoryId.isBlank()) {
            return Result.failure(MemoraError.Validation("Memory ID cannot be blank."))
        }

        val eventDate = knownEventDate ?: memoryRepository.getMemory(reminder.memoryId)?.eventDate
        if (eventDate == null) {
            return Result.failure(MemoraError.NotFound("Associated Memory not found for ID: ${reminder.memoryId}"))
        }

        if (reminder.reminderStartDate.isAfter(eventDate)) {
            return Result.failure(
                MemoraError.Validation(
                    "Reminder start date (${reminder.reminderStartDate}) cannot be after event date ($eventDate)."
                )
            )
        }

        return reminderRepository.createReminder(reminder)
    }

    suspend operator fun invoke(
        memoryId: String,
        reminderStartDate: LocalDate,
        id: String = java.util.UUID.randomUUID().toString(),
        reminderTime: java.time.LocalTime? = null,
    ): Result<String> {
        val now = java.time.Instant.now()
        val reminder = Reminder(
            id = id,
            memoryId = memoryId,
            reminderStartDate = reminderStartDate,
            reminderTime = reminderTime,
            createdAt = now,
            updatedAt = now,
        )
        return invoke(reminder).map { id }
    }
}
