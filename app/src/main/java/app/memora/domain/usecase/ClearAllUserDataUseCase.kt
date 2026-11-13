package app.memora.domain.usecase

import app.memora.domain.repository.ReminderRepository
import app.memora.platform.notification.NotificationScheduler
import kotlinx.coroutines.flow.first

/**
 * Safely erases all user data (DPDP Act Section 12 / CCPA compliance):
 * 1. Cancels all scheduled AlarmManager PendingIntents so no ghost notifications fire.
 * 2. Purges Room SQLite tables, captured image files, and diagnostic logs.
 */
class ClearAllUserDataUseCase(
    private val reminderRepository: ReminderRepository,
    private val notificationScheduler: NotificationScheduler,
    private val onClearDatabaseAndFiles: suspend () -> Unit,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val activeReminders = runCatching { reminderRepository.observeReminders().first() }.getOrDefault(emptyList())
        for (reminder in activeReminders) {
            notificationScheduler.cancelReminder(reminder.id)
        }
        onClearDatabaseAndFiles()
    }
}
