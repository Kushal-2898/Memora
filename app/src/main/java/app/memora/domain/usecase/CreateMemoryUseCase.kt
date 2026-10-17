package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository

/**
 * Creates and persists a durable [Memory], validating title, date, and optional initial [Reminder].
 */
class CreateMemoryUseCase(
    private val memoryRepository: MemoryRepository
) {

    suspend operator fun invoke(memory: Memory, initialReminder: Reminder? = null): Result<Unit> {
        if (memory.id.isBlank()) {
            return Result.failure(MemoraError.Validation("Memory ID cannot be blank."))
        }
        if (memory.title.isBlank()) {
            return Result.failure(MemoraError.Validation("Memory title cannot be blank."))
        }

        if (initialReminder != null) {
            if (initialReminder.id.isBlank()) {
                return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
            }
            if (initialReminder.memoryId != memory.id) {
                return Result.failure(
                    MemoraError.Validation("Reminder memoryId (${initialReminder.memoryId}) does not match Memory ID (${memory.id}).")
                )
            }
            if (initialReminder.reminderStartDate.isAfter(memory.eventDate)) {
                return Result.failure(
                    MemoraError.Validation(
                        "Reminder start date (${initialReminder.reminderStartDate}) cannot be after event date (${memory.eventDate})."
                    )
                )
            }
        }

        return memoryRepository.createMemory(memory, initialReminder)
    }

    suspend operator fun invoke(
        title: String,
        eventType: app.memora.domain.model.EventType,
        eventDate: java.time.LocalDate,
        timeZoneId: String = java.time.ZoneId.systemDefault().id,
        id: String = java.util.UUID.randomUUID().toString(),
        description: String? = null,
        initialReminder: Reminder? = null,
    ): Result<String> {
        val now = java.time.Instant.now()
        val memory = Memory(
            id = id,
            title = title,
            description = description,
            eventType = eventType,
            eventDate = eventDate,
            timeZoneId = timeZoneId,
            createdAt = now,
            updatedAt = now,
        )
        return invoke(memory, initialReminder).map { id }
    }
}
