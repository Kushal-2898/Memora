package app.memora.domain.usecase

import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.scheduler.ClockProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministically computes the single authoritative [ReminderStatus].
 *
 * CRITICAL BUSINESS RULES & TIMELINE LIFECYCLE:
 * The reminder start date determines WHEN REMINDING BECOMES ACTIVE.
 * The event date determines when the item is DUE_TODAY or OVERDUE.
 *
 * 1. If completedAt != null -> COMPLETED (remains completed permanently)
 * 2. Else if today > eventDate -> OVERDUE
 * 3. Else if today == eventDate -> DUE_TODAY
 * 4. Else if today >= reminderStartDate -> REMINDER_ACTIVE
 * 5. Else -> UPCOMING (today < reminderStartDate)
 *
 * Evaluated relative to today's date in the memory's explicit timezone.
 */
class CalculateReminderStatusUseCase(
    private val clockProvider: ClockProvider
) {

    operator fun invoke(
        eventDate: LocalDate,
        reminderStartDate: LocalDate,
        completedAt: Instant?,
        timeZoneId: String,
    ): ReminderStatus {
        if (completedAt != null) {
            return ReminderStatus.COMPLETED
        }

        val zoneId = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val today = clockProvider.today(zoneId)

        return when {
            today.isAfter(eventDate) -> ReminderStatus.OVERDUE
            today.isEqual(eventDate) -> ReminderStatus.DUE_TODAY
            !today.isBefore(reminderStartDate) -> ReminderStatus.REMINDER_ACTIVE
            else -> ReminderStatus.UPCOMING
        }
    }

    operator fun invoke(memory: Memory, reminder: Reminder): ReminderStatus {
        return invoke(
            eventDate = memory.eventDate,
            reminderStartDate = reminder.reminderStartDate,
            completedAt = reminder.completedAt,
            timeZoneId = memory.timeZoneId,
        )
    }

    operator fun invoke(item: MemoryWithReminders): ReminderStatus {
        val activeReminder = item.activeReminder
        if (activeReminder == null) {
            val zoneId = runCatching { ZoneId.of(item.memory.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            val today = clockProvider.today(zoneId)
            return when {
                today.isAfter(item.memory.eventDate) -> ReminderStatus.OVERDUE
                today.isEqual(item.memory.eventDate) -> ReminderStatus.DUE_TODAY
                else -> ReminderStatus.UPCOMING
            }
        }
        return invoke(item.memory, activeReminder)
    }
}
