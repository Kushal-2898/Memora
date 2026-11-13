package app.memora.data.gateway

import app.memora.domain.gateway.BillingGateway
import app.memora.domain.gateway.PurchaseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Local fallback implementation of BillingGateway when Google Play Billing is not configured.
 * Does not spoof or grant fake Pro entitlements.
 */
class DefaultBillingGateway : BillingGateway {

    override val purchases: Flow<List<PurchaseRecord>> = flowOf(emptyList())

    override suspend fun queryAvailableProducts(): Result<List<String>> {
        return Result.success(emptyList())
    }

    override suspend fun initiatePurchase(productId: String): Result<Unit> {
        return Result.failure(IllegalStateException("Google Play Billing is not available on this build."))
    }

    override suspend fun restorePurchases(): Result<Unit> {
        return Result.failure(IllegalStateException("No active Google Play subscription found to restore."))
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
        return Result.failure(IllegalStateException("Cannot acknowledge purchase without active Play Billing connection."))
    }
}
