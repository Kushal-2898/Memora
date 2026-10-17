package app.memora.domain.usecase

import app.memora.domain.repository.EntitlementRepository

class CanSaveMemoryUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    suspend operator fun invoke(): Boolean {
        return entitlementRepository.canSave()
    }
}
