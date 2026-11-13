package app.memora.domain.usecase

import app.memora.domain.model.MemoraError
import app.memora.domain.repository.MemoryRepository

class DeleteMemoryUseCase(
    private val repository: MemoryRepository,
    private val onRemoteDelete: (suspend (String) -> Unit)? = null,
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        if (id.isBlank()) {
            return Result.failure(MemoraError.Validation("Memory ID cannot be blank."))
        }
        val result = repository.deleteMemory(id)
        if (result.isSuccess) {
            runCatching { onRemoteDelete?.invoke(id) }
        }
        return result
    }
}
