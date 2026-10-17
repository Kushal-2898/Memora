package app.memora.domain.gateway

import kotlinx.coroutines.flow.Flow

/**
 * Represents an abstracted purchase transaction/receipt.
 */
data class PurchaseRecord(
    val productId: String,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean
)

/**
 * Platform-independent billing abstraction.
 * Represents Memora-level billing operations without depending on 
 * Google Play Billing SDK classes or Android UI elements.
 */
interface BillingGateway {
    val purchases: Flow<List<PurchaseRecord>>
    
    suspend fun queryAvailableProducts(): Result<List<String>>
    suspend fun initiatePurchase(productId: String): Result<Unit>
    suspend fun restorePurchases(): Result<Unit>
    suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit>
}
