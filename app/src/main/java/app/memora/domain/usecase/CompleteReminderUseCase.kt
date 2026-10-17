package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.scheduler.ClockProvider

class CompleteReminderUseCase(
    private val repository: ReminderRepository,
    private val clockProvider: ClockProvider
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
        }
        return repository.completeReminder(id, clockProvider.now())
    }
}
