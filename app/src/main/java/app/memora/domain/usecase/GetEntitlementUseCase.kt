package app.memora.domain.usecase

import app.memora.domain.model.Entitlement
import app.memora.domain.repository.EntitlementRepository

class GetEntitlementUseCase(
    private val entitlementRepository: EntitlementRepository
) {
    suspend operator fun invoke(): Entitlement {
        return entitlementRepository.getEntitlement()
    }
}
