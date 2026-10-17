package app.memora.domain.usecase

import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.repository.MemoryRepository

class GetMemoryUseCase(
    private val memoryRepository: MemoryRepository,
    private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase
) {
    suspend operator fun invoke(id: String): MemoryWithReminders? {
        val item = memoryRepository.getMemoryWithReminders(id) ?: return null
        val updatedReminders = item.reminders.map { reminder ->
            val computedStatus = calculateReminderStatusUseCase(item.memory, reminder)
            if (reminder.status != computedStatus) reminder.copy(status = computedStatus) else reminder
        }
        return item.copy(reminders = updatedReminders)
    }
}
