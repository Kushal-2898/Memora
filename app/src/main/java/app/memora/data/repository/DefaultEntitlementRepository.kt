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
class DefaultEntitlementRepository(
    private val isDebug: Boolean = app.memora.BuildConfig.DEBUG
) : EntitlementRepository {

    private val currentEntitlement = if (isDebug) {
        Entitlement(
            status = EntitlementStatus.PREMIUM,
            canScan = true,
            canSave = true,
            productId = "dev_premium_tier",
            source = "development_override"
        )
    } else {
        Entitlement(
            status = EntitlementStatus.FREE,
            canScan = true,
            canSave = true,
            productId = null,
            source = "production_default"
        )
    }

    override fun observeEntitlement(): Flow<Entitlement> {
        return flowOf(currentEntitlement)
    }

    override suspend fun getEntitlement(): Entitlement {
        return currentEntitlement
    }

    override suspend fun canScan(): Boolean {
        return currentEntitlement.canScan
    }

    override suspend fun canSave(): Boolean {
        return currentEntitlement.canSave
    }
}
