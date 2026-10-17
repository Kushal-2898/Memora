package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.repository.ReminderRepository

class DeleteReminderUseCase(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
        }
        return repository.deleteReminder(id)
    }
}
