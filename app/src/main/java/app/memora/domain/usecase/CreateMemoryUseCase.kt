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

        val sanitizedMemory = memory.copy(title = memory.title.trim().take(120))
        return memoryRepository.createMemory(sanitizedMemory, initialReminder)
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

    suspend fun saveAndSchedule(
        title: String,
        eventType: app.memora.domain.model.EventType,
        eventDate: java.time.LocalDate,
        remindDaysBefore: Int,
        reminderTime: java.time.LocalTime,
        customCategory: String? = null,
        notificationScheduler: app.memora.platform.notification.NotificationScheduler,
        timeZoneId: String = java.time.ZoneId.systemDefault().id,
    ): Result<String> {
        val now = java.time.Instant.now()
        val memoryId = java.util.UUID.randomUUID().toString()
        val reminder = Reminder(
            id = java.util.UUID.randomUUID().toString(),
            memoryId = memoryId,
            reminderStartDate = eventDate.minusDays(remindDaysBefore.coerceAtLeast(0).toLong()),
            reminderTime = reminderTime,
            createdAt = now,
            updatedAt = now,
        )
        return invoke(
            title = title,
            eventType = eventType,
            eventDate = eventDate,
            timeZoneId = timeZoneId,
            id = memoryId,
            description = customCategory?.takeIf { it.isNotBlank() },
            initialReminder = reminder,
        ).onSuccess {
            notificationScheduler.scheduleReminder(
                memoryId = memoryId,
                reminderId = reminder.id,
                title = title,
                reminderStartDate = reminder.reminderStartDate,
                eventDate = eventDate,
                timeZoneId = timeZoneId,
                reminderTime = reminderTime,
            )
        }
    }
}
