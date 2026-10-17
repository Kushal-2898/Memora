package app.memora.domain.usecase

import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository

class GetReminderUseCase(
    private val reminderRepository: ReminderRepository,
    private val memoryRepository: MemoryRepository,
    private val calculateReminderStatusUseCase: CalculateReminderStatusUseCase
) {
    suspend operator fun invoke(id: String): Reminder? {
        val reminder = reminderRepository.getReminder(id) ?: return null
        val memory = memoryRepository.getMemory(reminder.memoryId)
        val status = if (memory != null) calculateReminderStatusUseCase(memory, reminder) else reminder.status
        return if (reminder.status != status) reminder.copy(status = status) else reminder
    }
}
