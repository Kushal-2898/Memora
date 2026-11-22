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
 * Snoozes a reminder by updating its reminder schedule.
 * If daysToSnooze > 0, postpones by N days.
 * If daysToSnooze == 0, performs an intra-day snooze (defaults to +3 hours,
 * rolling over to 9:00 AM tomorrow if after 8:00 PM to respect quiet hours).
 */
class SnoozeReminderUseCase(
    private val reminderRepository: ReminderRepository,
    private val memoryRepository: MemoryRepository,
    private val notificationScheduler: NotificationScheduler,
    private val clockProvider: ClockProvider = SystemClockProvider(),
) {
    suspend operator fun invoke(
        reminderId: String,
        daysToSnooze: Long = 0L,
        hoursToSnooze: Long = 3L,
    ): Result<Reminder> {
        if (reminderId.isBlank()) {
            return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
        }
        val reminder = reminderRepository.getReminder(reminderId)
            ?: return Result.failure(MemoraError.NotFound("Reminder not found: $reminderId"))

        val memory = memoryRepository.getMemory(reminder.memoryId)
            ?: return Result.failure(MemoraError.NotFound("Memory not found for ID: ${reminder.memoryId}"))

        val zone = runCatching { ZoneId.of(memory.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val now = clockProvider.now()
        val currentDateTime = now.atZone(zone)
        val today = currentDateTime.toLocalDate()

        val (newStartDate, newReminderTime) = if (daysToSnooze > 0L) {
            val baseDate = if (reminder.reminderStartDate.isAfter(today)) reminder.reminderStartDate else today
            baseDate.plusDays(daysToSnooze) to (reminder.reminderTime ?: LocalTime.of(9, 0))
        } else {
            // Intra-day snooze (+hoursToSnooze from current clock)
            val snoozeTarget = currentDateTime.plusHours(hoursToSnooze)
            val eveningCutoff = LocalTime.of(20, 0) // 8:00 PM cutoff to avoid late-night alerts
            if (snoozeTarget.toLocalDate().isAfter(today) || snoozeTarget.toLocalTime().isAfter(eveningCutoff)) {
                today.plusDays(1) to LocalTime.of(9, 0)
            } else {
                today to snoozeTarget.toLocalTime().withSecond(0).withNano(0)
            }
        }

        val updatedReminder = reminder.copy(
            reminderStartDate = newStartDate,
            reminderTime = newReminderTime,
            completedAt = null,
            updatedAt = now,
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
