package app.memora.data.repository

import app.memora.domain.model.Entitlement
import app.memora.domain.model.EntitlementStatus
import app.memora.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Development-only implementation of EntitlementRepository.
 * Automatically grants PREMIUM access so the developer can use the app without
 * setting up Google Play Billing or a backend.
 * 
 * In the future, this will be replaced by an implementation that actually
 * verifies purchase tokens and backend state.
 */
class DefaultEntitlementRepository : EntitlementRepository {

    private val devEntitlement = Entitlement(
        status = EntitlementStatus.PREMIUM,
        canScan = true,
        canSave = true,
        productId = "dev_premium_tier",
        source = "development_override"
    )

    override fun observeEntitlement(): Flow<Entitlement> {
        return flowOf(devEntitlement)
    }

    override suspend fun getEntitlement(): Entitlement {
        return devEntitlement
    }

    override suspend fun canScan(): Boolean {
        return devEntitlement.canScan
    }

    override suspend fun canSave(): Boolean {
        return devEntitlement.canSave
    }
}
