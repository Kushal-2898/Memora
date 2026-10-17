package app.memora.domain.usecase

import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes all durable memories along with their reminders, applying live deterministic
 * status calculations across the observation stream.
 */
class ObserveMemoriesUseCase(
    private val memoryRepository: MemoryRepository,
    private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase
) {

    operator fun invoke(): Flow<List<MemoryWithReminders>> {
        return memoryRepository.observeMemories().map { list ->
            list.map { item ->
                val updatedReminders = item.reminders.map { reminder ->
                    val computedStatus = calculateReminderStatusUseCase(item.memory, reminder)
                    if (reminder.status != computedStatus) {
                        reminder.copy(status = computedStatus)
                    } else {
                        reminder
                    }
                }
                item.copy(reminders = updatedReminders)
            }
        }
    }
}
