package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.repository.ReminderRepository
import java.time.Instant

/**
 * Reverses the completed state of a reminder, clearing its completed timestamp
 * and restoring it to active tracking.
 */
class UncompleteReminderUseCase(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(id: String): Result<Reminder> {
        if (id.isBlank()) {
            return Result.failure(MemoraError.Validation("Reminder ID cannot be blank."))
        }
        val existing = repository.getReminder(id)
            ?: return Result.failure(MemoraError.NotFound("Reminder not found for uncomplete: $id"))

        val restored = existing.copy(
            completedAt = null,
            status = ReminderStatus.UPCOMING,
            updatedAt = Instant.now(),
        )
        return repository.updateReminder(restored).map { restored }
    }
}
