package app.memora.domain.usecase

import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus

/**
 * Determines whether a reminder is currently active for notification delivery.
 *
 * Notification state is active when:
 * - The item is not completed, AND
 * - The current timeline has reached REMINDER_ACTIVE, DUE_TODAY, or OVERDUE.
 *
 * Notification state is inactive when:
 * - The item is UPCOMING (reminderStartDate has not arrived yet), OR
 * - The item is COMPLETED.
 */
class IsReminderNotificationActiveUseCase(
    private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase
) {

    operator fun invoke(memory: Memory, reminder: Reminder): Boolean {
        if (reminder.completedAt != null) {
            return false
        }
        val status = calculateReminderStatusUseCase(memory, reminder)
        return status == ReminderStatus.REMINDER_ACTIVE ||
                status == ReminderStatus.DUE_TODAY ||
                status == ReminderStatus.OVERDUE
    }
}
