package app.memora.data.gateway

import app.memora.domain.gateway.BillingGateway
import app.memora.domain.gateway.PurchaseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Development-only implementation of BillingGateway.
 * Does not depend on Google Play Billing.
 */
class DefaultBillingGateway : BillingGateway {
    
    override val purchases: Flow<List<PurchaseRecord>> = flowOf(
        listOf(
            PurchaseRecord(
                productId = "dev_premium_tier",
                purchaseToken = "dev_token_123",
                isAcknowledged = true,
                isAutoRenewing = true
            )
        )
    )

    override suspend fun queryAvailableProducts(): Result<List<String>> {
        return Result.success(listOf("dev_premium_tier"))
    }

    override suspend fun initiatePurchase(productId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun restorePurchases(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
        return Result.success(Unit)
    }
}
