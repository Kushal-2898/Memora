package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.scheduler.SystemClockProvider
import app.memora.platform.notification.NotificationScheduler
import java.time.LocalTime
import java.time.ZoneId

/**
 * Snoozes a reminder by updating its reminderStartDate by +1 day (or specified daysToSnooze)
 * and re-arming the exact alarm via NotificationScheduler.
 */
class SnoozeReminderUseCase(
    private val reminderRepository: ReminderRepository,
    private val memoryRepository: MemoryRepository,
    private val notificationScheduler: NotificationScheduler,
    private val clockProvider: ClockProvider = SystemClockProvider(),
) {
    suspend operator fun invoke(
        reminderId: String,
        daysToSnooze: Long = 1L
    ): Result<Reminder> {
        if (reminderId.isBlank()) {
            return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
        }
        val reminder = reminderRepository.getReminder(reminderId)
            ?: return Result.failure(MemoraError.NotFound("Reminder not found: $reminderId"))

        val memory = memoryRepository.getMemory(reminder.memoryId)
            ?: return Result.failure(MemoraError.NotFound("Memory not found for ID: ${reminder.memoryId}"))

        val zone = runCatching { ZoneId.of(memory.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val today = clockProvider.today(zone)

        val baseDate = if (reminder.reminderStartDate.isAfter(today)) reminder.reminderStartDate else today
        val newStartDate = baseDate.plusDays(daysToSnooze)

        val updatedReminder = reminder.copy(
            reminderStartDate = newStartDate,
            completedAt = null,
            updatedAt = clockProvider.now(),
        )

        val updateResult = reminderRepository.updateReminder(updatedReminder)
        if (updateResult.isFailure) {
            return Result.failure(updateResult.exceptionOrNull() ?: Exception("Failed to update reminder"))
        }

        notificationScheduler.scheduleReminder(
            memoryId = memory.id,
            reminderId = updatedReminder.id,
            title = memory.title,
            reminderStartDate = updatedReminder.reminderStartDate,
            eventDate = memory.eventDate,
            timeZoneId = memory.timeZoneId,
            reminderTime = updatedReminder.reminderTime ?: LocalTime.of(9, 0),
        )

        return Result.success(updatedReminder)
    }
}
