package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.repository.MemoryRepository

class DeleteMemoryUseCase(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.failure(MemoraError.Validation("Memory ID cannot be blank."))
        }
        return repository.deleteMemory(id)
    }
}
