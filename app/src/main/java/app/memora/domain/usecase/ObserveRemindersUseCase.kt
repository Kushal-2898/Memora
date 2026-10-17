package app.memora.domain.usecase

import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes raw reminders from persistence.
 */
class ObserveRemindersUseCase(
    private val reminderRepository: ReminderRepository,
    private val memoryRepository: MemoryRepository,
    private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase
) {

    operator fun invoke(): Flow<List<Reminder>> {
        return reminderRepository.observeReminders().map { reminders ->
            reminders.map { reminder ->
                val memory = memoryRepository.getMemory(reminder.memoryId)
                if (memory != null) {
                    val computedStatus = calculateReminderStatusUseCase(memory, reminder)
                    if (reminder.status != computedStatus) reminder.copy(status = computedStatus) else reminder
                } else {
                    reminder
                }
            }
        }
    }
}
